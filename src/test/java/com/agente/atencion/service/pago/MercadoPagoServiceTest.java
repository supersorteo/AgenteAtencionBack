package com.agente.atencion.service.pago;

import com.agente.atencion.config.MercadoPagoProperties;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoServiceTest {

    private static final String SECRETO = "secreto";
    private static final BigDecimal MONTO = new BigDecimal("60000.00");

    @Mock MercadoPagoClient client;
    @Mock MercadoPagoWebhookValidator validator;
    @Mock PagoService pagoService;

    private MercadoPagoService service;

    private MercadoPagoProperties props(boolean enabled, String token, String secreto) {
        return new MercadoPagoProperties(enabled, "https://api.mercadopago.com", token, secreto,
            "https://app.test/r", "https://app.test/r", "https://app.test/r");
    }

    @BeforeEach
    void setUp() {
        service = new MercadoPagoService(props(true, "TOKEN", SECRETO), client, validator, pagoService);
    }

    private Pago orden(ProveedorPago proveedor, EstadoPago estado) {
        Pago p = new Pago();
        p.setReferencia("ref-1");
        p.setProveedor(proveedor);
        p.setEstado(estado);
        p.setMonto(MONTO);
        p.setMoneda("ARS");
        p.setTipoPlan(TipoCodigo.PLAN_3M);
        return p;
    }

    private MercadoPagoClient.PagoMp pagoMp(String status, String referencia) {
        return new MercadoPagoClient.PagoMp(555L, status, referencia, MONTO, "ARS");
    }

    private void webhookValido() {
        when(validator.esValida("sig", "req", "555", SECRETO)).thenReturn(true);
    }

    @Test
    void iniciarCheckout_creaLaOrdenYDevuelveLaUrl() {
        Pago pago = orden(ProveedorPago.MERCADOPAGO, EstadoPago.PENDIENTE);
        when(pagoService.crearOrden(ProveedorPago.MERCADOPAGO, TipoCodigo.PLAN_3M, "barber-x")).thenReturn(pago);
        when(client.crearPreferencia(pago, "Corteo - Suscripción 3 meses")).thenReturn("https://mp.test/checkout");

        var checkout = service.iniciarCheckout(TipoCodigo.PLAN_3M, "barber-x");

        assertEquals("ref-1", checkout.referencia());
        assertEquals("https://mp.test/checkout", checkout.checkoutUrl());
    }

    @Test
    void iniciarCheckout_tituloEnSingularParaUnMes() {
        Pago pago = orden(ProveedorPago.MERCADOPAGO, EstadoPago.PENDIENTE);
        when(pagoService.crearOrden(ProveedorPago.MERCADOPAGO, TipoCodigo.PLAN_1M, null)).thenReturn(pago);
        when(client.crearPreferencia(pago, "Corteo - Suscripción 1 mes")).thenReturn("https://mp.test/c");

        assertEquals("https://mp.test/c", service.iniciarCheckout(TipoCodigo.PLAN_1M, null).checkoutUrl());
    }

    @Test
    void iniciarCheckout_deshabilitado_503SinCrearOrden() {
        service = new MercadoPagoService(props(false, "TOKEN", SECRETO), client, validator, pagoService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.iniciarCheckout(TipoCodigo.PLAN_3M, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verifyNoInteractions(pagoService, client);
    }

    @Test
    void iniciarCheckout_sinAccessToken_503SinCrearOrden() {
        service = new MercadoPagoService(props(true, "", SECRETO), client, validator, pagoService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.iniciarCheckout(TipoCodigo.PLAN_3M, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verifyNoInteractions(pagoService, client);
    }

    @Test
    void webhook_sinSecretoConfigurado_503() {
        service = new MercadoPagoService(props(true, "TOKEN", ""), client, validator, pagoService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.procesarWebhook("payment", "555", "sig", "req"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verifyNoInteractions(client, pagoService);
    }

    @Test
    void webhook_conFirmaInvalida_401SinConsultarNada() {
        when(validator.esValida("sig", "req", "555", SECRETO)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.procesarWebhook("payment", "555", "sig", "req"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(client, pagoService);
    }

    @Test
    void webhook_deOtroTipo_seIgnoraSinConsultarElPago() {
        webhookValido();

        assertDoesNotThrow(() -> service.procesarWebhook("merchant_order", "555", "sig", "req"));

        verifyNoInteractions(client, pagoService);
    }

    @Test
    void webhook_pagoAprobado_confirmaConMontoYMonedaDeLaApi() {
        webhookValido();
        when(client.obtenerPago("555")).thenReturn(pagoMp("approved", "ref-1"));
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.MERCADOPAGO, EstadoPago.PENDIENTE));

        service.procesarWebhook("payment", "555", "sig", "req");

        verify(pagoService).confirmarAprobado(
            org.mockito.ArgumentMatchers.eq("ref-1"), org.mockito.ArgumentMatchers.eq("555"),
            org.mockito.ArgumentMatchers.eq(MONTO), org.mockito.ArgumentMatchers.eq("ARS"), anyString());
    }

    @Test
    void webhook_pagoRechazadoOCancelado_marcaRechazado() {
        webhookValido();
        when(client.obtenerPago("555")).thenReturn(pagoMp("rejected", "ref-1"));
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.MERCADOPAGO, EstadoPago.PENDIENTE));

        service.procesarWebhook("payment", "555", "sig", "req");

        verify(pagoService).marcarRechazado(org.mockito.ArgumentMatchers.eq("ref-1"),
            org.mockito.ArgumentMatchers.eq("555"), anyString());
        verify(pagoService, never()).confirmarAprobado(any(), any(), any(), any(), any());
    }

    @Test
    void webhook_pagoPendiente_noCambiaNada() {
        webhookValido();
        when(client.obtenerPago("555")).thenReturn(pagoMp("pending", "ref-1"));
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.MERCADOPAGO, EstadoPago.PENDIENTE));

        service.procesarWebhook("payment", "555", "sig", "req");

        verify(pagoService, never()).confirmarAprobado(any(), any(), any(), any(), any());
        verify(pagoService, never()).marcarRechazado(any(), any(), any());
    }

    @Test
    void webhook_pagoSinReferencia_seIgnora() {
        webhookValido();
        when(client.obtenerPago("555")).thenReturn(pagoMp("approved", null));

        assertDoesNotThrow(() -> service.procesarWebhook("payment", "555", "sig", "req"));

        verifyNoInteractions(pagoService);
    }

    @Test
    void webhook_ordenDeOtraPasarela_noSeConfirma() {
        webhookValido();
        when(client.obtenerPago("555")).thenReturn(pagoMp("approved", "ref-1"));
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden(ProveedorPago.PAYPAL, EstadoPago.PENDIENTE));

        service.procesarWebhook("payment", "555", "sig", "req");

        verify(pagoService, never()).confirmarAprobado(any(), any(), any(), any(), any());
    }

    @Test
    void webhook_ordenDesconocida_seResponde200ParaQueNoReintente() {
        webhookValido();
        when(client.obtenerPago("555")).thenReturn(pagoMp("approved", "ref-x"));
        when(pagoService.obtenerPorReferencia("ref-x"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de pago no encontrada."));

        assertDoesNotThrow(() -> service.procesarWebhook("payment", "555", "sig", "req"));
    }

    @Test
    void webhook_fallaLaApiDeMercadoPago_propagaParaQueReintente() {
        webhookValido();
        when(client.obtenerPago("555"))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comunicar con Mercado Pago."));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.procesarWebhook("payment", "555", "sig", "req"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void webhook_sinDataId_400() {
        when(validator.esValida("sig", "req", null, SECRETO)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.procesarWebhook("payment", null, "sig", "req"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void verificarRetorno_confirmaConLosDatosDeLaApiNoDelCliente() {
        when(client.obtenerPago("555")).thenReturn(pagoMp("approved", "ref-1"));
        Pago orden = orden(ProveedorPago.MERCADOPAGO, EstadoPago.PENDIENTE);
        Pago confirmada = orden(ProveedorPago.MERCADOPAGO, EstadoPago.APROBADO);
        when(pagoService.obtenerPorReferencia("ref-1")).thenReturn(orden);
        when(pagoService.confirmarAprobado(anyString(), anyString(), any(), anyString(), anyString())).thenReturn(confirmada);

        Pago r = service.verificarRetorno("ref-1", "555");

        assertEquals(EstadoPago.APROBADO, r.getEstado());
    }

    @Test
    void verificarRetorno_conPagoDeOtraOrden_400SinConfirmar() {
        when(client.obtenerPago("555")).thenReturn(pagoMp("approved", "ref-de-otro"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.verificarRetorno("ref-1", "555"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(pagoService);
    }

    @Test
    void verificarRetorno_deshabilitado_503() {
        service = new MercadoPagoService(props(false, "TOKEN", SECRETO), client, validator, pagoService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.verificarRetorno("ref-1", "555"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verifyNoInteractions(client);
    }
}
