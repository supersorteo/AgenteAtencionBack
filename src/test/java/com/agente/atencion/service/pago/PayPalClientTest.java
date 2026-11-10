package com.agente.atencion.service.pago;

import com.agente.atencion.config.PayPalProperties;
import com.agente.atencion.entity.Pago;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayPalClientTest {

    private static final String BASE = "https://api-m.sandbox.paypal.com";
    private static final String CERT = "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-360caa42-fca2a594";
    private static final String EVENTO = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{\"id\":\"CAP1\"}}";

    private MockRestServiceServer server;

    private PayPalClient cliente() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new PayPalProperties(true, "cid", "csecret", "WH-1", BASE,
            "https://app.test/pago/resultado", "https://app.test/pago/resultado");
        return new PayPalClient(builder, props);
    }

    private void esperarToken() {
        String basic = "Basic " + Base64.getEncoder().encodeToString("cid:csecret".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE + "/v1/oauth2/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", basic))
            .andExpect(content().string("grant_type=client_credentials"))
            .andRespond(withSuccess("{\"access_token\":\"TOK\",\"expires_in\":32400,\"token_type\":\"Bearer\"}",
                MediaType.APPLICATION_JSON));
    }

    private Pago pago() {
        Pago p = new Pago();
        p.setReferencia("ref-1");
        p.setMonto(new BigDecimal("50"));
        p.setMoneda("USD");
        return p;
    }

    private static String ordenCompletada(String estadoCaptura) {
        return "{\"id\":\"ORD12345\",\"status\":\"COMPLETED\",\"purchase_units\":[{\"reference_id\":\"ref-1\","
            + "\"custom_id\":\"ref-1\",\"payments\":{\"captures\":[{\"id\":\"CAP1\",\"status\":\"" + estadoCaptura + "\","
            + "\"amount\":{\"currency_code\":\"USD\",\"value\":\"50.00\"},\"custom_id\":\"ref-1\",\"extra\":1}]}}]}";
    }

    @Test
    void crearOrden_enviaMontoConDosDecimalesReferenciaYUrlsYDevuelveElEnlaceDeAprobacion() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer TOK"))
            .andExpect(header("PayPal-Request-Id", "ref-1"))
            .andExpect(jsonPath("$.intent").value("CAPTURE"))
            .andExpect(jsonPath("$.purchase_units[0].reference_id").value("ref-1"))
            .andExpect(jsonPath("$.purchase_units[0].custom_id").value("ref-1"))
            .andExpect(jsonPath("$.purchase_units[0].description").value("Plan"))
            .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("USD"))
            .andExpect(jsonPath("$.purchase_units[0].amount.value").value("50.00"))
            .andExpect(jsonPath("$.payment_source.paypal.experience_context.user_action").value("PAY_NOW"))
            .andExpect(jsonPath("$.payment_source.paypal.experience_context.shipping_preference").value("NO_SHIPPING"))
            .andExpect(jsonPath("$.payment_source.paypal.experience_context.return_url").value("https://app.test/r?ref=ref-1"))
            .andExpect(jsonPath("$.payment_source.paypal.experience_context.cancel_url").value("https://app.test/c?ref=ref-1&cancelado=1"))
            .andRespond(withSuccess("{\"id\":\"ORD12345\",\"status\":\"PAYER_ACTION_REQUIRED\",\"links\":["
                + "{\"href\":\"https://api.sandbox.paypal.com/v2/checkout/orders/ORD12345\",\"rel\":\"self\",\"method\":\"GET\"},"
                + "{\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=ORD12345\",\"rel\":\"payer-action\",\"method\":\"GET\"}]}",
                MediaType.APPLICATION_JSON));

        PayPalClient.OrdenCreada orden = client.crearOrden(pago(), "Plan",
            "https://app.test/r?ref=ref-1", "https://app.test/c?ref=ref-1&cancelado=1");

        assertEquals("ORD12345", orden.id());
        assertEquals("https://www.sandbox.paypal.com/checkoutnow?token=ORD12345", orden.urlDeAprobacion());
        server.verify();
    }

    @Test
    void crearOrden_sinEnlaceDeAprobacion_502() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders"))
            .andRespond(withSuccess("{\"id\":\"ORD12345\",\"links\":[{\"href\":\"https://x\",\"rel\":\"self\"}]}",
                MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> client.crearOrden(pago(), "Plan", "https://a/r", "https://a/c"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void crearOrden_conEnlaceNoHttps_502() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders"))
            .andRespond(withSuccess("{\"id\":\"ORD12345\",\"links\":[{\"href\":\"http://phishing.test/x\",\"rel\":\"payer-action\"}]}",
                MediaType.APPLICATION_JSON));

        assertThrows(ResponseStatusException.class, () -> client.crearOrden(pago(), "Plan", "https://a/r", "https://a/c"));
    }

    @Test
    void elTokenSeReutilizaEntreLlamadas() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(ExpectedCount.twice(), requestTo(BASE + "/v2/checkout/orders/ORD12345"))
            .andExpect(header("Authorization", "Bearer TOK"))
            .andRespond(withSuccess("{\"id\":\"ORD12345\",\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        assertEquals("APPROVED", client.obtenerOrden("ORD12345").status());
        assertEquals("APPROVED", client.obtenerOrden("ORD12345").status());
        server.verify();
    }

    @Test
    void obtenerOrden_leeLaCapturaYIgnoraLosCamposDesconocidos() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORD12345"))
            .andRespond(withSuccess(ordenCompletada("COMPLETED"), MediaType.APPLICATION_JSON));

        PayPalClient.OrdenPayPal orden = client.obtenerOrden("ORD12345");

        assertEquals("COMPLETED", orden.status());
        assertEquals("ref-1", orden.customId());
        PayPalClient.Captura captura = orden.primeraCaptura();
        assertNotNull(captura);
        assertEquals("CAP1", captura.id());
        assertEquals("USD", captura.amount().currencyCode());
        assertEquals(0, new BigDecimal("50.00").compareTo(captura.amount().value()));
    }

    @Test
    void ordenSinCaptura_primeraCapturaEsNull() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORD12345"))
            .andRespond(withSuccess("{\"id\":\"ORD12345\",\"status\":\"APPROVED\",\"purchase_units\":[{\"custom_id\":\"ref-1\"}]}",
                MediaType.APPLICATION_JSON));

        assertEquals(null, client.obtenerOrden("ORD12345").primeraCaptura());
    }

    @Test
    void capturar_envieCuerpoVacioYClaveDeIdempotencia() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORD12345/capture"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer TOK"))
            .andExpect(header("PayPal-Request-Id", "cap-ORD12345"))
            .andExpect(content().string("{}"))
            .andRespond(withStatus(HttpStatus.CREATED).body(ordenCompletada("COMPLETED")).contentType(MediaType.APPLICATION_JSON));

        assertEquals("COMPLETED", client.capturar("ORD12345").primeraCaptura().status());
    }

    @Test
    void capturar_rechazoDePayPal422_seTraduceA422() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORD12345/capture"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":\"INSTRUMENT_DECLINED\"}]}")
                .contentType(MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> client.capturar("ORD12345"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    void capturar_errorDelServidorDePayPal_502SinExponerDetalle() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORD12345/capture"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("detalle interno"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> client.capturar("ORD12345"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertEquals("No se pudo comunicar con PayPal.", ex.getReason());
    }

    @Test
    void tokenRechazado_502() {
        PayPalClient client = cliente();
        server.expect(requestTo(BASE + "/v1/oauth2/token"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"invalid_client\"}")
                .contentType(MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> client.obtenerOrden("ORD12345"));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void identificadoresDeOrdenInvalidos_noLlamanALaApi() {
        PayPalClient client = cliente();

        assertThrows(IllegalArgumentException.class, () -> client.obtenerOrden("../v1/identity"));
        assertThrows(IllegalArgumentException.class, () -> client.capturar("a b"));
        assertThrows(IllegalArgumentException.class, () -> client.obtenerOrden(null));
        assertThrows(IllegalArgumentException.class, () -> client.obtenerOrden("x"));
        server.verify();
    }

    @Test
    void verificarFirma_exitosa_enviaElEventoTalCualYElWebhookIdPropio() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v1/notifications/verify-webhook-signature"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer TOK"))
            .andExpect(jsonPath("$.webhook_id").value("WH-1"))
            .andExpect(jsonPath("$.transmission_id").value("tid"))
            .andExpect(jsonPath("$.transmission_sig").value("abc+/="))
            .andExpect(jsonPath("$.cert_url").value(CERT))
            .andExpect(jsonPath("$.auth_algo").value("SHA256withRSA"))
            .andExpect(jsonPath("$.webhook_event.event_type").value("PAYMENT.CAPTURE.COMPLETED"))
            .andExpect(jsonPath("$.webhook_event.resource.id").value("CAP1"))
            .andRespond(withSuccess("{\"verification_status\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));

        assertTrue(client.verificarFirma("tid", "2026-11-10T15:00:00Z", "abc+/=", CERT, "SHA256withRSA", "WH-1", EVENTO));
        server.verify();
    }

    @Test
    void verificarFirma_rechazadaPorPayPal_devuelveFalse() {
        PayPalClient client = cliente();
        esperarToken();
        server.expect(requestTo(BASE + "/v1/notifications/verify-webhook-signature"))
            .andRespond(withSuccess("{\"verification_status\":\"FAILURE\"}", MediaType.APPLICATION_JSON));

        assertFalse(client.verificarFirma("tid", "2026-11-10T15:00:00Z", "abc+/=", CERT, "SHA256withRSA", "WH-1", EVENTO));
    }

    @Test
    void verificarFirma_conCertificadoDeOtroDominio_noLlamaALaApi() {
        PayPalClient client = cliente();

        assertFalse(client.verificarFirma("tid", "t", "sig", "https://evil.example.com/cert", "SHA256withRSA", "WH-1", EVENTO));
        assertFalse(client.verificarFirma("tid", "t", "sig", "http://api.sandbox.paypal.com/cert", "SHA256withRSA", "WH-1", EVENTO));
        assertFalse(client.verificarFirma("tid", "t", "sig", "https://paypal.com.evil.test/cert", "SHA256withRSA", "WH-1", EVENTO));
        server.verify();
    }

    @Test
    void verificarFirma_conEventoQueIntentaInyectarOtroWebhookId_noLlamaALaApi() {
        PayPalClient client = cliente();
        String inyeccion = "{}, \"webhook_id\":\"WH-DEL-ATACANTE\", \"x\":{}";

        assertFalse(client.verificarFirma("tid", "t", "sig", CERT, "SHA256withRSA", "WH-1", inyeccion));
        assertFalse(client.verificarFirma("tid", "t", "sig", CERT, "SHA256withRSA", "WH-1", EVENTO + " {\"a\":1}"));
        assertFalse(client.verificarFirma("tid", "t", "sig", CERT, "SHA256withRSA", "WH-1", "[1,2]"));
        assertFalse(client.verificarFirma("tid", "t", "sig", CERT, "SHA256withRSA", "WH-1", null));
        server.verify();
    }

    @Test
    void verificarFirma_conCabecerasConCaracteresDeInyeccion_noLlamaALaApi() {
        PayPalClient client = cliente();

        assertFalse(client.verificarFirma("tid\",\"webhook_id\":\"x", "t", "sig", CERT, "SHA256withRSA", "WH-1", EVENTO));
        assertFalse(client.verificarFirma("tid", "t", "sig\"", CERT, "SHA256withRSA", "WH-1", EVENTO));
        assertFalse(client.verificarFirma(null, "t", "sig", CERT, "SHA256withRSA", "WH-1", EVENTO));
        assertFalse(client.verificarFirma("tid", "t", "sig", CERT, "SHA256withRSA", "", EVENTO));
        server.verify();
    }

    @Test
    void esObjetoJsonUnico_distingueUnObjetoDeTextoConExtras() {
        assertTrue(PayPalClient.esObjetoJsonUnico("{}"));
        assertTrue(PayPalClient.esObjetoJsonUnico("  {\"a\":{\"b\":[1,2,{\"c\":\"}\"}]}}  "));
        assertTrue(PayPalClient.esObjetoJsonUnico("{\"a\":\"comilla \\\" y llave }\"}"));
        assertFalse(PayPalClient.esObjetoJsonUnico("{} {}"));
        assertFalse(PayPalClient.esObjetoJsonUnico("{},{}"));
        assertFalse(PayPalClient.esObjetoJsonUnico("{\"a\":1"));
        assertFalse(PayPalClient.esObjetoJsonUnico("{\"a\":\"sin cerrar}"));
        assertFalse(PayPalClient.esObjetoJsonUnico("}{"));
        assertFalse(PayPalClient.esObjetoJsonUnico(""));
        assertFalse(PayPalClient.esObjetoJsonUnico(null));
    }
}
