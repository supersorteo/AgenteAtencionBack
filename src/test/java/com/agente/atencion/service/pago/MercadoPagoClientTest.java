package com.agente.atencion.service.pago;

import com.agente.atencion.config.MercadoPagoProperties;
import com.agente.atencion.config.PagosProperties;
import com.agente.atencion.entity.Pago;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoClientTest {

    private static final String BASE = "https://api.mercadopago.com";

    private MockRestServiceServer server;

    private MercadoPagoClient cliente(String successUrl, String backendUrl) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new MercadoPagoProperties(true, BASE, "TOKEN-DE-PRUEBA", "secreto",
            successUrl, "https://app.test/pago/resultado", "https://app.test/pago/resultado");
        return new MercadoPagoClient(builder, props, new PagosProperties(backendUrl));
    }

    private Pago pago() {
        Pago p = new Pago();
        p.setReferencia("ref-1");
        p.setMonto(new BigDecimal("60000.00"));
        p.setMoneda("ARS");
        return p;
    }

    @Test
    void crearPreferencia_enviaMontoMonedaReferenciaYUrlsYDevuelveElCheckout() {
        MercadoPagoClient client = cliente("https://app.test/pago/resultado", "https://api.test");
        server.expect(requestTo(BASE + "/checkout/preferences"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer TOKEN-DE-PRUEBA"))
            .andExpect(jsonPath("$.external_reference").value("ref-1"))
            .andExpect(jsonPath("$.items[0].title").value("Plan"))
            .andExpect(jsonPath("$.items[0].quantity").value(1))
            .andExpect(jsonPath("$.items[0].unit_price").value(60000.0))
            .andExpect(jsonPath("$.items[0].currency_id").value("ARS"))
            .andExpect(jsonPath("$.back_urls.success").value("https://app.test/pago/resultado"))
            .andExpect(jsonPath("$.back_urls.failure").value("https://app.test/pago/resultado"))
            .andExpect(jsonPath("$.back_urls.pending").value("https://app.test/pago/resultado"))
            .andExpect(jsonPath("$.auto_return").value("approved"))
            .andExpect(jsonPath("$.notification_url").value("https://api.test/api/pagos/webhook/mercadopago"))
            .andRespond(withSuccess("{\"id\":\"123\",\"init_point\":\"https://mp.test/checkout\",\"otro\":1}",
                MediaType.APPLICATION_JSON));

        assertEquals("https://mp.test/checkout", client.crearPreferencia(pago(), "Plan"));
        server.verify();
    }

    @Test
    void crearPreferencia_conUrlsLocales_omiteAutoReturnYNotificationUrl() {
        MercadoPagoClient client = cliente("http://localhost:4200/pago/resultado", "http://localhost:8080");
        server.expect(requestTo(BASE + "/checkout/preferences"))
            .andExpect(jsonPath("$.auto_return").doesNotExist())
            .andExpect(jsonPath("$.notification_url").doesNotExist())
            .andRespond(withSuccess("{\"id\":\"1\",\"init_point\":\"https://mp.test/c\"}", MediaType.APPLICATION_JSON));

        assertEquals("https://mp.test/c", client.crearPreferencia(pago(), "Plan"));
    }

    @Test
    void crearPreferencia_sinInitPoint_usaSandboxInitPoint() {
        MercadoPagoClient client = cliente("https://app.test/r", "https://api.test");
        server.expect(requestTo(BASE + "/checkout/preferences"))
            .andRespond(withSuccess("{\"id\":\"1\",\"sandbox_init_point\":\"https://sandbox.test/c\"}",
                MediaType.APPLICATION_JSON));

        assertEquals("https://sandbox.test/c", client.crearPreferencia(pago(), "Plan"));
    }

    @Test
    void crearPreferencia_sinNingunaUrl_502() {
        MercadoPagoClient client = cliente("https://app.test/r", "https://api.test");
        server.expect(requestTo(BASE + "/checkout/preferences"))
            .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> client.crearPreferencia(pago(), "Plan"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void crearPreferencia_errorDeMercadoPago_502SinExponerElDetalle() {
        MercadoPagoClient client = cliente("https://app.test/r", "https://api.test");
        server.expect(requestTo(BASE + "/checkout/preferences"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"invalid access token\"}")
                .contentType(MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> client.crearPreferencia(pago(), "Plan"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals("No se pudo comunicar con Mercado Pago.", ex.getReason());
    }

    @Test
    void obtenerPago_leeLosCamposYIgnoraLosDesconocidos() {
        MercadoPagoClient client = cliente("https://app.test/r", "https://api.test");
        server.expect(requestTo(BASE + "/v1/payments/123456"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer TOKEN-DE-PRUEBA"))
            .andRespond(withSuccess("{\"id\":123456,\"status\":\"approved\",\"external_reference\":\"ref-1\","
                + "\"transaction_amount\":60000.0,\"currency_id\":\"ARS\",\"payer\":{\"email\":\"x@y.z\"}}",
                MediaType.APPLICATION_JSON));

        MercadoPagoClient.PagoMp pago = client.obtenerPago("123456");

        assertEquals(123456L, pago.id());
        assertEquals("approved", pago.status());
        assertEquals("ref-1", pago.externalReference());
        assertEquals(0, new BigDecimal("60000").compareTo(pago.transactionAmount()));
        assertEquals("ARS", pago.currencyId());
    }

    @Test
    void obtenerPago_conIdentificadorInvalido_noLlamaALaApi() {
        MercadoPagoClient client = cliente("https://app.test/r", "https://api.test");

        assertThrows(IllegalArgumentException.class, () -> client.obtenerPago("12ab"));
        assertThrows(IllegalArgumentException.class, () -> client.obtenerPago("../v1/users/me"));
        assertThrows(IllegalArgumentException.class, () -> client.obtenerPago(""));
        assertThrows(IllegalArgumentException.class, () -> client.obtenerPago(null));
        server.verify();
    }

    @Test
    void obtenerPago_errorDeMercadoPago_502() {
        MercadoPagoClient client = cliente("https://app.test/r", "https://api.test");
        server.expect(requestTo(BASE + "/v1/payments/99"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> client.obtenerPago("99"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }
}
