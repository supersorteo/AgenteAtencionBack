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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PagoService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LONGITUD_CODIGO = 12;
    private static final SecureRandom RND = new SecureRandom();
    private static final Set<EstadoPago> CONFIRMABLES = Set.of(EstadoPago.PENDIENTE, EstadoPago.RECHAZADO);

    private final PagoRepository pagoRepo;
    private final PlanPrecioRepository planPrecioRepo;
    private final TenantRepository tenantRepo;
    private final CodigoInvitacionRepository codigoRepo;
    private final SuscripcionService suscripcionService;

    public PagoService(PagoRepository pagoRepo, PlanPrecioRepository planPrecioRepo, TenantRepository tenantRepo,
                       CodigoInvitacionRepository codigoRepo, SuscripcionService suscripcionService) {
        this.pagoRepo = pagoRepo;
        this.planPrecioRepo = planPrecioRepo;
        this.tenantRepo = tenantRepo;
        this.codigoRepo = codigoRepo;
        this.suscripcionService = suscripcionService;
    }

    @Transactional
    public Pago crearOrden(ProveedorPago proveedor, TipoCodigo plan, String tenantId) {
        if (tenantId != null) validarTenantRenovable(tenantId);

        PlanPrecio precio = planPrecioRepo.findById(plan)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El plan no existe."));
        BigDecimal monto = "ARS".equals(proveedor.moneda) ? precio.getPrecioArs() : precio.getPrecioUsd();
        if (monto == null || monto.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El plan no tiene precio configurado para " + proveedor.moneda + ".");
        }

        Pago pago = new Pago();
        pago.setReferencia(UUID.randomUUID().toString());
        pago.setTenantId(tenantId);
        pago.setProveedor(proveedor);
        pago.setTipoPlan(plan);
        pago.setMonto(monto);
        pago.setMoneda(proveedor.moneda);
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setCreadoAt(LocalDateTime.now());
        return pagoRepo.save(pago);
    }

    @Transactional
    public Pago confirmarAprobado(String referencia, String externalId, BigDecimal montoPagado,
                                  String monedaPagada, String payload) {
        Pago pago = pagoRepo.bloquearPorReferencia(referencia)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de pago no encontrada."));

        if (!CONFIRMABLES.contains(pago.getEstado())) return pago;

        pagoRepo.findByProveedorAndExternalId(pago.getProveedor(), externalId)
            .filter(otro -> !otro.getId().equals(pago.getId()))
            .ifPresent(otro -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese pago ya está asociado a otra orden.");
            });

        pago.setExternalId(externalId);
        pago.setPayload(payload);
        boolean coincide = montoPagado != null && monedaPagada != null
            && pago.getMonto().compareTo(montoPagado) == 0
            && pago.getMoneda().equalsIgnoreCase(monedaPagada);
        if (!coincide) {
            pago.setEstado(EstadoPago.MONTO_INVALIDO);
            return pagoRepo.save(pago);
        }

        if (pago.getTenantId() != null) {
            suscripcionService.extender(pago.getTenantId(), pago.getTipoPlan(), pago.getMoneda());
        } else {
            pago.setCodigoGenerado(generarCodigo(pago.getTipoPlan()));
        }
        pago.setEstado(EstadoPago.APROBADO);
        pago.setConfirmadoAt(LocalDateTime.now());
        return pagoRepo.save(pago);
    }

    @Transactional
    public Pago marcarRechazado(String referencia, String externalId, String payload) {
        Pago pago = pagoRepo.bloquearPorReferencia(referencia)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de pago no encontrada."));
        if (pago.getEstado() != EstadoPago.PENDIENTE) return pago;
        pago.setEstado(EstadoPago.RECHAZADO);
        pago.setPayload(payload);
        if (externalId != null
                && pagoRepo.findByProveedorAndExternalId(pago.getProveedor(), externalId).isEmpty()) {
            pago.setExternalId(externalId);
        }
        return pagoRepo.save(pago);
    }

    @Transactional
    public Pago asociarExternalId(String referencia, String externalId) {
        Pago pago = pagoRepo.bloquearPorReferencia(referencia)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de pago no encontrada."));
        pago.setExternalId(externalId);
        return pagoRepo.save(pago);
    }

    @Transactional(readOnly = true)
    public Optional<Pago> buscarPorExternalId(ProveedorPago proveedor, String externalId) {
        return pagoRepo.findByProveedorAndExternalId(proveedor, externalId);
    }

    @Transactional(readOnly = true)
    public Pago obtenerPorReferencia(String referencia) {
        return pagoRepo.findByReferencia(referencia)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de pago no encontrada."));
    }

    private void validarTenantRenovable(String tenantId) {
        if (SuscripcionService.DEMO_SLUG.equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La barbería demo no se renueva.");
        }
        Tenant tenant = tenantRepo.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado."));
        if (!tenant.isActivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Barbería desactivada");
        }
    }

    private String generarCodigo(TipoCodigo plan) {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder(plan.prefijo).append("-");
            for (int i = 0; i < LONGITUD_CODIGO; i++) sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
            codigo = sb.toString();
        } while (codigoRepo.existsById(codigo));
        CodigoInvitacion c = new CodigoInvitacion();
        c.setCodigo(codigo);
        c.setTipo(plan);
        c.setCreadoAt(LocalDateTime.now());
        codigoRepo.save(c);
        return codigo;
    }
}
