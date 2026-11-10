package com.agente.atencion.service;

import com.agente.atencion.entity.CodigoInvitacion;
import com.agente.atencion.entity.EstadoPago;
import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.PlanPrecio;
import com.agente.atencion.entity.ProveedorPago;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.repository.CodigoInvitacionRepository;
import com.agente.atencion.repository.PagoRepository;
import com.agente.atencion.repository.PlanPrecioRepository;
import com.agente.atencion.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock PagoRepository pagoRepo;
    @Mock PlanPrecioRepository planPrecioRepo;
    @Mock TenantRepository tenantRepo;
    @Mock CodigoInvitacionRepository codigoRepo;
    @Mock SuscripcionService suscripcionService;
    @InjectMocks PagoService service;

    private PlanPrecio precios;

    @BeforeEach
    void setUp() {
        precios = new PlanPrecio();
        precios.setTipo(TipoCodigo.PLAN_3M);
        precios.setPrecioUsd(new BigDecimal("50.00"));
        precios.setPrecioArs(new BigDecimal("60000.00"));
        precios.setPrecioUyu(new BigDecimal("2000.00"));
    }

    private Tenant tenant(String id, boolean activo) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setActivo(activo);
        return t;
    }

    private Pago orden(String tenantId, EstadoPago estado) {
        Pago p = new Pago();
        p.setId(1L);
        p.setReferencia("ref-1");
        p.setTenantId(tenantId);
        p.setProveedor(ProveedorPago.MERCADOPAGO);
        p.setTipoPlan(TipoCodigo.PLAN_3M);
        p.setMonto(new BigDecimal("60000.00"));
        p.setMoneda("ARS");
        p.setEstado(estado);
        p.setCreadoAt(LocalDateTime.now());
        return p;
    }

    private void ordenPersistida(Pago p) {
        when(pagoRepo.bloquearPorReferencia("ref-1")).thenReturn(Optional.of(p));
        when(pagoRepo.save(any(Pago.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void crearOrden_mercadoPago_cobraPrecioArsCongelado() {
        when(planPrecioRepo.findById(TipoCodigo.PLAN_3M)).thenReturn(Optional.of(precios));
        when(pagoRepo.save(any(Pago.class))).thenAnswer(i -> i.getArgument(0));

        Pago p = service.crearOrden(ProveedorPago.MERCADOPAGO, TipoCodigo.PLAN_3M, null);

        assertEquals(new BigDecimal("60000.00"), p.getMonto());
        assertEquals("ARS", p.getMoneda());
        assertEquals(EstadoPago.PENDIENTE, p.getEstado());
        assertNotNull(p.getReferencia());
        assertNull(p.getTenantId());
    }

    @Test
    void crearOrden_paypal_cobraPrecioUsd() {
        when(planPrecioRepo.findById(TipoCodigo.PLAN_3M)).thenReturn(Optional.of(precios));
        when(pagoRepo.save(any(Pago.class))).thenAnswer(i -> i.getArgument(0));

        Pago p = service.crearOrden(ProveedorPago.PAYPAL, TipoCodigo.PLAN_3M, null);

        assertEquals(new BigDecimal("50.00"), p.getMonto());
        assertEquals("USD", p.getMoneda());
    }

    @Test
    void crearOrden_sinPrecioConfigurado_400() {
        precios.setPrecioArs(null);
        when(planPrecioRepo.findById(TipoCodigo.PLAN_3M)).thenReturn(Optional.of(precios));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.crearOrden(ProveedorPago.MERCADOPAGO, TipoCodigo.PLAN_3M, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(pagoRepo, never()).save(any());
    }

    @Test
    void crearOrden_renovacionDeLaDemo_400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.crearOrden(ProveedorPago.PAYPAL, TipoCodigo.PLAN_3M, SuscripcionService.DEMO_SLUG));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void crearOrden_tenantInexistente_404() {
        when(tenantRepo.findById("x")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.crearOrden(ProveedorPago.PAYPAL, TipoCodigo.PLAN_3M, "x"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void crearOrden_tenantDesactivado_403() {
        when(tenantRepo.findById("x")).thenReturn(Optional.of(tenant("x", false)));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.crearOrden(ProveedorPago.PAYPAL, TipoCodigo.PLAN_3M, "x"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void confirmar_altaNueva_generaCodigoDelPlan() {
        Pago p = orden(null, EstadoPago.PENDIENTE);
        ordenPersistida(p);
        when(pagoRepo.findByProveedorAndExternalId(ProveedorPago.MERCADOPAGO, "mp-9")).thenReturn(Optional.empty());
        when(codigoRepo.existsById(anyString())).thenReturn(false);

        Pago r = service.confirmarAprobado("ref-1", "mp-9", new BigDecimal("60000.00"), "ARS", "{}");

        assertEquals(EstadoPago.APROBADO, r.getEstado());
        assertTrue(r.getCodigoGenerado().startsWith("3M-"));
        assertEquals("mp-9", r.getExternalId());
        ArgumentCaptor<CodigoInvitacion> captor = ArgumentCaptor.forClass(CodigoInvitacion.class);
        verify(codigoRepo).save(captor.capture());
        assertEquals(TipoCodigo.PLAN_3M, captor.getValue().getTipo());
        verify(suscripcionService, never()).extender(anyString(), any(), any());
    }

    @Test
    void confirmar_renovacion_extiendeLaSuscripcion() {
        Pago p = orden("barber-x", EstadoPago.PENDIENTE);
        ordenPersistida(p);
        when(pagoRepo.findByProveedorAndExternalId(any(), anyString())).thenReturn(Optional.empty());

        Pago r = service.confirmarAprobado("ref-1", "mp-9", new BigDecimal("60000"), "ars", "{}");

        assertEquals(EstadoPago.APROBADO, r.getEstado());
        assertNull(r.getCodigoGenerado());
        verify(suscripcionService).extender("barber-x", TipoCodigo.PLAN_3M, "ARS");
        verify(codigoRepo, never()).save(any());
    }

    @Test
    void confirmar_esIdempotenteSiYaEstabaAprobado() {
        Pago p = orden("barber-x", EstadoPago.APROBADO);
        when(pagoRepo.bloquearPorReferencia("ref-1")).thenReturn(Optional.of(p));

        Pago r = service.confirmarAprobado("ref-1", "mp-9", new BigDecimal("60000.00"), "ARS", "{}");

        assertEquals(EstadoPago.APROBADO, r.getEstado());
        verify(suscripcionService, never()).extender(anyString(), any(), any());
        verify(pagoRepo, never()).save(any());
    }

    @Test
    void confirmar_conMontoDistinto_noAplicaNada() {
        Pago p = orden("barber-x", EstadoPago.PENDIENTE);
        ordenPersistida(p);
        when(pagoRepo.findByProveedorAndExternalId(any(), anyString())).thenReturn(Optional.empty());

        Pago r = service.confirmarAprobado("ref-1", "mp-9", new BigDecimal("100.00"), "ARS", "{}");

        assertEquals(EstadoPago.MONTO_INVALIDO, r.getEstado());
        verify(suscripcionService, never()).extender(anyString(), any(), any());
    }

    @Test
    void confirmar_conMonedaDistinta_noAplicaNada() {
        Pago p = orden("barber-x", EstadoPago.PENDIENTE);
        ordenPersistida(p);
        when(pagoRepo.findByProveedorAndExternalId(any(), anyString())).thenReturn(Optional.empty());

        Pago r = service.confirmarAprobado("ref-1", "mp-9", new BigDecimal("60000.00"), "USD", "{}");

        assertEquals(EstadoPago.MONTO_INVALIDO, r.getEstado());
        verify(suscripcionService, never()).extender(anyString(), any(), any());
    }

    @Test
    void confirmar_externalIdDeOtraOrden_409() {
        Pago p = orden("barber-x", EstadoPago.PENDIENTE);
        when(pagoRepo.bloquearPorReferencia("ref-1")).thenReturn(Optional.of(p));
        Pago otra = orden(null, EstadoPago.APROBADO);
        otra.setId(2L);
        when(pagoRepo.findByProveedorAndExternalId(ProveedorPago.MERCADOPAGO, "mp-9")).thenReturn(Optional.of(otra));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.confirmarAprobado("ref-1", "mp-9", new BigDecimal("60000.00"), "ARS", "{}"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(suscripcionService, never()).extender(anyString(), any(), any());
    }

    @Test
    void confirmar_unPagoRechazadoPuedeAprobarseDespues() {
        Pago p = orden("barber-x", EstadoPago.RECHAZADO);
        ordenPersistida(p);
        when(pagoRepo.findByProveedorAndExternalId(any(), anyString())).thenReturn(Optional.empty());

        Pago r = service.confirmarAprobado("ref-1", "mp-10", new BigDecimal("60000.00"), "ARS", "{}");

        assertEquals(EstadoPago.APROBADO, r.getEstado());
        verify(suscripcionService).extender(eq("barber-x"), eq(TipoCodigo.PLAN_3M), eq("ARS"));
    }

    @Test
    void confirmar_ordenInexistente_404() {
        when(pagoRepo.bloquearPorReferencia("nope")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.confirmarAprobado("nope", "mp-9", BigDecimal.ONE, "ARS", "{}"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void marcarRechazado_soloDesdePendiente() {
        Pago aprobado = orden("barber-x", EstadoPago.APROBADO);
        when(pagoRepo.bloquearPorReferencia("ref-1")).thenReturn(Optional.of(aprobado));

        assertEquals(EstadoPago.APROBADO, service.marcarRechazado("ref-1", "mp-1", "{}").getEstado());
        verify(pagoRepo, never()).save(any());
    }
}
