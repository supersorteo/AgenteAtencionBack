package com.agente.atencion.service.pago;

import com.agente.atencion.config.PayPalProperties;
import com.agente.atencion.entity.EstadoPago;
import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.ProveedorPago;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.service.PagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayPalServiceTest {

    private static final BigDecimal USD_50 = new BigDecimal("50.00");
    private static final PayPalService.FirmaWebhook FIRMA =
        new PayPalService.FirmaWebhook("tid", "time", "sig", "cert", "algo");
    private static final String EVENTO_COMPLETADO = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":"
        + "{\"id\":\"CAP1\",\"supplementary_data\":{\"related_ids\":{\"order_id\":\"ORD12345\"}}}}";

    @Mock PayPalClient client;
    @Mock PagoService pagoService;

    private PayPalService service;

    private PayPalProperties props(boolean enabled, String clientId, String secret, String webhookId) {
        return new PayPalProperties(enabled, clientId, secret, webhookId, "https://api-m.sandbox.paypal.com",
            "https://app.test/pago/resultado", "https://app.test/pago/resultado");
    }

    @BeforeEach
    void setUp() {
        service = new PayPalService(props(true, "cid", "secret", "WH-1"), client, pagoService);
    }

    private Pago orden(ProveedorPago proveedor, String externalId) {
        Pago p = new Pago();
        p.setReferencia("ref-1");
        p.setProveedor(proveedor);
        p.setExternalId(externalId);
        p.setEstado(EstadoPago.PENDIENTE);
        p.setMonto(USD_50);
        p.setMoneda("USD");
        p.setTipoPlan(TipoCodigo.PLAN_3M);
        return p;
    }

    private PayPalClient.OrdenPayPal paypal(String estadoOrden, String estadoCaptura, String customId) {
        var captura = estadoCaptura == null ? null
            : new PayPalClient.Captura("CAP1", estadoCaptura, new PayPalClient.Monto("USD", USD_50), customId);
        var pagos = captura == null ? null : new PayPalClient.Pagos(List.of(captura));
        return new PayPalClient.OrdenPayPal("ORD12345", estadoOrden,
            List.of(new PayPalClient.UnidadCompra("ref-1", customId, pagos)), List.of());
    }

    private void firmaValida() {
        when(client.verificarFirma(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(true);
    }

    @Test
    void iniciarCheckout_creaLaOrdenConUrlsDeRetornoYGuardaElIdDePayPal() {
        Pago pago = orden(ProveedorPago.PAYPAL, null);
        when(pagoService.crearOrden(ProveedorPago.PAYPAL, TipoCodigo.PLAN_3M, "barber-x")).thenReturn(pago);
        when(client.crearOrden(pago, "Corteo - Suscripción 3 meses",
            "https://app.test/pago/resultado?ref=ref-1",
            "https://app.test/pago/resultado?ref=ref-1&cancelado=1"))
            .thenReturn(new PayPalClient.OrdenCreada("ORD12345", "https://paypal.test/pay"));

        var checkout = service.iniciarCheckout(TipoCodigo.PLAN_3M, "barber-x");

        assertEquals("ref-1", checkout.referencia());
        assertEquals("https://paypal.test/pay", checkout.checkoutUrl());
        verify(pagoService).asociarExternalId("ref-1", "ORD12345");
    }

    @Test
    void iniciarCheckout_deshabilitadoOSinCredenciales_503SinCrearOrden() {
        service = new PayPalService(props(false, "cid", "secret", "WH-1"), client, pagoService);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows(ResponseStatusException.class, () -> service.iniciarCheckout(TipoCodigo.PLAN_3M, null)).getStatusCode());

        service = new PayPalService(props(true, "", "secret", "WH-1"), client, pagoService);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows(ResponseStatusException.class, () -> service.iniciarCheckout(TipoCodigo.PLAN_3M, null)).getStatusCode());

        verifyNoInteractions(client, pagoService);
    }

    @Test
    void capturarRetorno_ordenAprobada_capturaYConfirmaConMontoDePayPal() {
        Pago orden = orden(ProveedorPago.PAYPAL, "ORD12345");
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden);
        when(client.obtenerOrden("ORD12345")).thenReturn(paypal("APPROVED", null, "ref-1"));
        when(client.capturar("ORD12345")).thenReturn(paypal("COMPLETED", "COMPLETED", "ref-1"));
        Pago confirmada = orden(ProveedorPago.PAYPAL, "ORD12345");
        confirmada.setEstado(EstadoPago.APROBADO);
        when(pagoService.confirmarAprobado(eq("ref-1"), eq("ORD12345"), eq(USD_50), eq("USD"), anyString()))
            .thenReturn(confirmada);

        assertEquals(EstadoPago.APROBADO, service.capturarRetorno("ref-1", "ORD12345").getEstado());
    }

    @Test
    void capturarRetorno_ordenYaCompletada_noCapturaDeNuevo() {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.PAYPAL, "ORD12345"));
        when(client.obtenerOrden("ORD12345")).thenReturn(paypal("COMPLETED", "COMPLETED", "ref-1"));

        service.capturarRetorno("ref-1", "ORD12345");

        verify(client, never()).capturar(anyString());
        verify(pagoService).confirmarAprobado(eq("ref-1"), eq("ORD12345"), eq(USD_50), eq("USD"), anyString());
    }

    @Test
    void capturarRetorno_clienteNoLlegoAAprobar_noCapturaNiConfirma() {
        Pago orden = orden(ProveedorPago.PAYPAL, "ORD12345");
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden);
        when(client.obtenerOrden("ORD12345")).thenReturn(paypal("PAYER_ACTION_REQUIRED", null, "ref-1"));

        assertSame(orden, service.capturarRetorno("ref-1", "ORD12345"));

        verify(client, never()).capturar(anyString());
        verify(pagoService, never()).confirmarAprobado(any(), any(), any(), any(), any());
    }

    @Test
    void capturarRetorno_conOrderIdDeOtraOrden_400SinTocarPayPal() {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.PAYPAL, "ORD12345"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.capturarRetorno("ref-1", "ORD-DE-OTRO"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void capturarRetorno_ordenDeMercadoPago_400() {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.MERCADOPAGO, "ORD12345"));

        assertEquals(HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class, () -> service.capturarRetorno("ref-1", "ORD12345")).getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void capturarRetorno_conCustomIdDeOtraReferencia_400SinConfirmar() {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.PAYPAL, "ORD12345"));
        when(client.obtenerOrden("ORD12345")).thenReturn(paypal("COMPLETED", "COMPLETED", "ref-de-otro"));

        assertEquals(HttpStatus.BAD_REQUEST,
            assertThrows(ResponseStatusException.class, () -> service.capturarRetorno("ref-1", "ORD12345")).getStatusCode());
        verify(pagoService, never()).confirmarAprobado(any(), any(), any(), any(), any());
    }

    @Test
    void capturarRetorno_paypalRechazaLaCaptura422_marcaRechazado() {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.PAYPAL, "ORD12345"));
        when(client.obtenerOrden("ORD12345")).thenReturn(paypal("APPROVED", null, "ref-1"));
        when(client.capturar("ORD12345")).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "x"));

        service.capturarRetorno("ref-1", "ORD12345");

        verify(pagoService).marcarRechazado(eq("ref-1"), eq("ORD12345"), anyString());
    }

    @Test
    void capturarRetorno_fallaDeRedDePayPal_propagaSinMarcarRechazado() {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.PAYPAL, "ORD12345"));
        when(client.obtenerOrden("ORD12345")).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "x"));

        assertEquals(HttpStatus.BAD_GATEWAY,
            assertThrows(ResponseStatusException.class, () -> service.capturarRetorno("ref-1", "ORD12345")).getStatusCode());
        verify(pagoService, never()).marcarRechazado(any(), any(), any());
    }

    @Test
    void capturarRetorno_capturaDeclinada_marcaRechazado() {
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.PAYPAL, "ORD12345"));
        when(client.obtenerOrden("ORD12345")).thenReturn(paypal("COMPLETED", "DECLINED", "ref-1"));

        service.capturarRetorno("ref-1", "ORD12345");

        verify(pagoService).marcarRechazado(eq("ref-1"), eq("ORD12345"), anyString());
        verify(pagoService, never()).confirmarAprobado(any(), any(), any(), any(), any());
    }

    @Test
    void webhook_deshabilitadoOSinWebhookId_503() {
        service = new PayPalService(props(false, "cid", "secret", "WH-1"), client, pagoService);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows(ResponseStatusException.class, () -> service.procesarWebhook(EVENTO_COMPLETADO, FIRMA)).getStatusCode());

        service = new PayPalService(props(true, "cid", "secret", ""), client, pagoService);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
            assertThrows(ResponseStatusException.class, () -> service.procesarWebhook(EVENTO_COMPLETADO, FIRMA)).getStatusCode());

        verifyNoInteractions(client, pagoService);
    }

    @Test
    void webhook_firmaInvalida_401() {
        when(client.verificarFirma(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(false);

        assertEquals(HttpStatus.UNAUTHORIZED,
            assertThrows(ResponseStatusException.class, () -> service.procesarWebhook(EVENTO_COMPLETADO, FIRMA)).getStatusCode());
        verifyNoInteractions(pagoService);
    }

    @Test
    void webhook_usaElWebhookIdConfiguradoNoUnoDeLaPeticion() {
        firmaValida();
        when(pagoService.buscarPorExternalId(ProveedorPago.PAYPAL, "ORD12345")).thenReturn(Optional.empty());

        service.procesarWebhook(EVENTO_COMPLETADO, FIRMA);

        verify(client).verificarFirma("tid", "time", "sig", "cert", "algo", "WH-1", EVENTO_COMPLETADO);
    }

    @Test
    void webhook_eventoDeOtroTipo_seIgnora() {
        firmaValida();

        assertDoesNotThrow(() -> service.procesarWebhook("{\"event_type\":\"CHECKOUT.ORDER.APPROVED\"}", FIRMA));

        verifyNoInteractions(pagoService);
        verify(client, never()).obtenerOrden(anyString());
    }

    @Test
    void webhook_capturaCompletadaDeUnaOrdenConocida_confirmaConLosDatosDeLaApi() {
        firmaValida();
        Pago orden = orden(ProveedorPago.PAYPAL, "ORD12345");
        when(pagoService.buscarPorExternalId(ProveedorPago.PAYPAL, "ORD12345")).thenReturn(Optional.of(orden));
        when(client.obtenerOrden("ORD12345")).thenReturn(paypal("COMPLETED", "COMPLETED", "ref-1"));

        service.procesarWebhook(EVENTO_COMPLETADO, FIRMA);

        verify(pagoService).confirmarAprobado(eq("ref-1"), eq("ORD12345"), eq(USD_50), eq("USD"), anyString());
    }

    @Test
    void webhook_ordenDesconocida_seIgnoraSinConsultarAPayPal() {
        firmaValida();
        when(pagoService.buscarPorExternalId(ProveedorPago.PAYPAL, "ORD12345")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.procesarWebhook(EVENTO_COMPLETADO, FIRMA));

        verify(client, never()).obtenerOrden(anyString());
    }

    @Test
    void webhook_eventoSinOrderId_seIgnora() {
        firmaValida();

        assertDoesNotThrow(() -> service.procesarWebhook(
            "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\",\"resource\":{\"id\":\"CAP1\"}}", FIRMA));

        verifyNoInteractions(pagoService);
    }
}
