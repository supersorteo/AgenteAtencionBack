package com.agente.atencion.controller;

import com.agente.atencion.entity.EstadoPago;
import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.exception.GlobalExceptionHandler;
import com.agente.atencion.service.pago.PayPalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PayPalPagoControllerTest {

    @Mock PayPalService payPalService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PayPalPagoController(payPalService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private Pago orden(EstadoPago estado, String tenantId) {
        Pago p = new Pago();
        p.setReferencia("ref-1");
        p.setEstado(estado);
        p.setTenantId(tenantId);
        p.setTipoPlan(TipoCodigo.PLAN_3M);
        p.setMoneda("USD");
        p.setMonto(new BigDecimal("50.00"));
        p.setCodigoGenerado("3M-ABCDEFGHJKLM");
        return p;
    }

    @Test
    void crearOrden_devuelveReferenciaYUrlDeAprobacion() throws Exception {
        when(payPalService.iniciarCheckout(TipoCodigo.PLAN_3M, "barber-x"))
            .thenReturn(new PayPalService.CheckoutCreado("ref-1", "https://paypal.test/pay"));

        mvc.perform(post("/api/pagos/paypal/orden").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PLAN_3M\",\"tenantId\":\"barber-x\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.referencia").value("ref-1"))
            .andExpect(jsonPath("$.checkoutUrl").value("https://paypal.test/pay"));
    }

    @Test
    void crearOrden_sinPlan_400() throws Exception {
        mvc.perform(post("/api/pagos/paypal/orden").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(payPalService);
    }

    @Test
    void crearOrden_pasarelaDeshabilitada_503() throws Exception {
        when(payPalService.iniciarCheckout(TipoCodigo.PLAN_1M, null))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "no disponible"));

        mvc.perform(post("/api/pagos/paypal/orden").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PLAN_1M\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void capturar_altaAprobada_exponeElCodigo() throws Exception {
        when(payPalService.capturarRetorno("ref-1", "ORD12345")).thenReturn(orden(EstadoPago.APROBADO, null));

        mvc.perform(post("/api/pagos/paypal/orden/ref-1/capturar").param("orderId", "ORD12345"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("APROBADO"))
            .andExpect(jsonPath("$.codigo").value("3M-ABCDEFGHJKLM"))
            .andExpect(jsonPath("$.moneda").value("USD"));
    }

    @Test
    void capturar_renovacion_noExponeCodigoPeroSiElTenant() throws Exception {
        when(payPalService.capturarRetorno("ref-1", "ORD12345")).thenReturn(orden(EstadoPago.APROBADO, "barber-x"));

        mvc.perform(post("/api/pagos/paypal/orden/ref-1/capturar").param("orderId", "ORD12345"))
            .andExpect(jsonPath("$.codigo").doesNotExist())
            .andExpect(jsonPath("$.tenantId").value("barber-x"));
    }

    @Test
    void capturar_sinOrderId_400() throws Exception {
        mvc.perform(post("/api/pagos/paypal/orden/ref-1/capturar")).andExpect(status().isBadRequest());
    }

    @Test
    void capturar_ordenDeOtroPago_400() throws Exception {
        when(payPalService.capturarRetorno("ref-1", "OTRA"))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "no corresponde"));

        mvc.perform(post("/api/pagos/paypal/orden/ref-1/capturar").param("orderId", "OTRA"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_pasaElCuerpoOriginalYLasCabecerasDeFirma() throws Exception {
        String cuerpo = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}";

        mvc.perform(post("/api/pagos/webhook/paypal").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("paypal-transmission-id", "tid").header("paypal-transmission-time", "time")
                .header("paypal-transmission-sig", "sig").header("paypal-cert-url", "cert")
                .header("paypal-auth-algo", "algo"))
            .andExpect(status().isOk());

        verify(payPalService).procesarWebhook(cuerpo, new PayPalService.FirmaWebhook("tid", "time", "sig", "cert", "algo"));
    }

    @Test
    void webhook_firmaInvalida_401() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma inválida."))
            .when(payPalService).procesarWebhook(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/api/pagos/webhook/paypal").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void webhook_sinCuerpo_400() throws Exception {
        mvc.perform(post("/api/pagos/webhook/paypal").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(payPalService);
    }
}
