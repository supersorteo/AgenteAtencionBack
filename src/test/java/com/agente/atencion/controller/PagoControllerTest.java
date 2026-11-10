package com.agente.atencion.controller;

import com.agente.atencion.entity.EstadoPago;
import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.exception.GlobalExceptionHandler;
import com.agente.atencion.service.PagoService;
import com.agente.atencion.service.pago.MercadoPagoService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PagoControllerTest {

    @Mock MercadoPagoService mercadoPagoService;
    @Mock PagoService pagoService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PagoController(mercadoPagoService, pagoService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private Pago orden(EstadoPago estado, String tenantId) {
        Pago p = new Pago();
        p.setReferencia("ref-1");
        p.setEstado(estado);
        p.setTenantId(tenantId);
        p.setTipoPlan(TipoCodigo.PLAN_3M);
        p.setMoneda("ARS");
        p.setMonto(new BigDecimal("60000.00"));
        p.setCodigoGenerado("3M-ABCDEFGHJKLM");
        return p;
    }

    @Test
    void crearOrden_devuelveReferenciaYUrlDeCheckout() throws Exception {
        when(mercadoPagoService.iniciarCheckout(TipoCodigo.PLAN_3M, "barber-x"))
            .thenReturn(new MercadoPagoService.CheckoutCreado("ref-1", "https://mp.test/c"));

        mvc.perform(post("/api/pagos/mercadopago/orden").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PLAN_3M\",\"tenantId\":\"barber-x\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.referencia").value("ref-1"))
            .andExpect(jsonPath("$.checkoutUrl").value("https://mp.test/c"));
    }

    @Test
    void crearOrden_tenantEnBlancoSeTrataComoAltaNueva() throws Exception {
        when(mercadoPagoService.iniciarCheckout(TipoCodigo.PLAN_1M, null))
            .thenReturn(new MercadoPagoService.CheckoutCreado("ref-2", "https://mp.test/c"));

        mvc.perform(post("/api/pagos/mercadopago/orden").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PLAN_1M\",\"tenantId\":\"  \"}"))
            .andExpect(status().isOk());

        verify(mercadoPagoService).iniciarCheckout(TipoCodigo.PLAN_1M, null);
    }

    @Test
    void crearOrden_sinPlanOPlanInvalido_400() throws Exception {
        mvc.perform(post("/api/pagos/mercadopago/orden").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pagos/mercadopago/orden").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PLAN_GRATIS\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(mercadoPagoService);
    }

    @Test
    void crearOrden_pasarelaDeshabilitada_503() throws Exception {
        when(mercadoPagoService.iniciarCheckout(TipoCodigo.PLAN_3M, null))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "no disponible"));

        mvc.perform(post("/api/pagos/mercadopago/orden").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"PLAN_3M\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void webhook_mapeaDataIdTipoYCabeceras() throws Exception {
        mvc.perform(post("/api/pagos/webhook/mercadopago")
                .param("data.id", "555").param("type", "payment")
                .header("x-signature", "ts=1,v1=abc").header("x-request-id", "req-1"))
            .andExpect(status().isOk());

        verify(mercadoPagoService).procesarWebhook("payment", "555", "ts=1,v1=abc", "req-1");
    }

    @Test
    void webhook_firmaInvalida_401() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma inválida."))
            .when(mercadoPagoService).procesarWebhook("payment", "555", "mala", null);

        mvc.perform(post("/api/pagos/webhook/mercadopago")
                .param("data.id", "555").param("type", "payment").header("x-signature", "mala"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void webhook_fallaDeMercadoPago_5xxParaQueReintente() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "x"))
            .when(mercadoPagoService).procesarWebhook("payment", "555", "s", "r");

        mvc.perform(post("/api/pagos/webhook/mercadopago")
                .param("data.id", "555").param("type", "payment")
                .header("x-signature", "s").header("x-request-id", "r"))
            .andExpect(status().isBadGateway());
    }

    @Test
    void estadoOrden_altaAprobada_exponeElCodigo() throws Exception {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(EstadoPago.APROBADO, null));

        mvc.perform(get("/api/pagos/orden/ref-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("APROBADO"))
            .andExpect(jsonPath("$.codigo").value("3M-ABCDEFGHJKLM"))
            .andExpect(jsonPath("$.plan").value("PLAN_3M"))
            .andExpect(jsonPath("$.moneda").value("ARS"));
    }

    @Test
    void estadoOrden_pendienteNoExponeElCodigo() throws Exception {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(EstadoPago.PENDIENTE, null));

        mvc.perform(get("/api/pagos/orden/ref-1"))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    void estadoOrden_renovacionNoExponeCodigo() throws Exception {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(EstadoPago.APROBADO, "barber-x"));

        mvc.perform(get("/api/pagos/orden/ref-1"))
            .andExpect(jsonPath("$.estado").value("APROBADO"))
            .andExpect(jsonPath("$.codigo").doesNotExist())
            .andExpect(jsonPath("$.tenantId").value("barber-x"));
    }

    @Test
    void estadoOrden_inexistente_404() throws Exception {
        when(pagoService.obtenerPorReferencia("nope"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de pago no encontrada."));

        mvc.perform(get("/api/pagos/orden/nope")).andExpect(status().isNotFound());
    }

    @Test
    void verificarRetorno_devuelveElEstadoActualizado() throws Exception {
        when(mercadoPagoService.verificarRetorno("ref-1", "555")).thenReturn(orden(EstadoPago.APROBADO, "barber-x"));

        mvc.perform(post("/api/pagos/mercadopago/orden/ref-1/verificar").param("paymentId", "555"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void verificarRetorno_sinPaymentId_400() throws Exception {
        mvc.perform(post("/api/pagos/mercadopago/orden/ref-1/verificar")).andExpect(status().isBadRequest());
    }
}
