package com.agente.atencion.controller;

import com.agente.atencion.dto.TenantResumen;
import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.CodigoInvitacion;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RND = new SecureRandom();
    private static final String DEMO_SLUG = "barberia-demo";

    private final TenantRepository tenantRepo;
    private final UsuarioRepository usuarioRepo;
    private final CodigoInvitacionRepository codigoRepo;
    private final BarberoRepository barberoRepo;
    private final HorarioBarberoRepository horarioRepo;
    private final BloqueoHorarioRepository bloqueoRepo;
    private final TurnoRepository turnoRepo;
    private final ServicioRepository servicioRepo;
    private final GaleriaRepository galeriaRepo;
    private final NegocioConfigRepository negocioConfigRepo;
    private final VisitaRepository visitaRepo;
    private final PropiedadRepository propiedadRepo;

    public SuperAdminController(TenantRepository tenantRepo, UsuarioRepository usuarioRepo,
                                CodigoInvitacionRepository codigoRepo, BarberoRepository barberoRepo,
                                HorarioBarberoRepository horarioRepo, BloqueoHorarioRepository bloqueoRepo,
                                TurnoRepository turnoRepo, ServicioRepository servicioRepo,
                                GaleriaRepository galeriaRepo, NegocioConfigRepository negocioConfigRepo,
                                VisitaRepository visitaRepo, PropiedadRepository propiedadRepo) {
        this.tenantRepo = tenantRepo;
        this.usuarioRepo = usuarioRepo;
        this.codigoRepo = codigoRepo;
        this.barberoRepo = barberoRepo;
        this.horarioRepo = horarioRepo;
        this.bloqueoRepo = bloqueoRepo;
        this.turnoRepo = turnoRepo;
        this.servicioRepo = servicioRepo;
        this.galeriaRepo = galeriaRepo;
        this.negocioConfigRepo = negocioConfigRepo;
        this.visitaRepo = visitaRepo;
        this.propiedadRepo = propiedadRepo;
    }

    @GetMapping("/resumen")
    public Map<String, Object> getResumen() {
        // 1 query for tenants, 1 count query for admins, 1 count query for codes
        List<Tenant> tenants = tenantRepo.findAll().stream()
            .filter(t -> !DEMO_SLUG.equals(t.getId()) && !"system".equals(t.getId()))
            .toList();
        long total = tenants.size();
        long activos = tenants.stream().filter(Tenant::isActivo).count();
        long admins = usuarioRepo.countByRolAndActivoTrue("ADMIN");
        long codigosDisponibles = codigoRepo.countByUsado(false);
        return Map.of(
            "totalTenants", total,
            "tenantActivos", activos,
            "totalAdmins", admins,
            "codigosDisponibles", codigosDisponibles
        );
    }

    @GetMapping("/tenants")
    public List<TenantResumen> getTenants() {
        // 1 query for all tenants + 1 query for all admins (eliminates N+1)
        Map<String, String> adminByTenant = usuarioRepo.findByRolAndActivoTrue("ADMIN").stream()
            .collect(Collectors.toMap(
                u -> u.getTenantId(),
                u -> u.getUsername(),
                (a, b) -> a   // keep first if multiple admins per tenant
            ));

        return tenantRepo.findAll().stream()
            .filter(t -> !"system".equals(t.getId()))
            .map(t -> new TenantResumen(
                t.getId(),
                t.getNombre(),
                t.isActivo(),
                adminByTenant.getOrDefault(t.getId(), "—")
            ))
            .toList();
    }

    @PatchMapping("/tenants/{slug}/toggle")
    public ResponseEntity<?> toggleTenant(@PathVariable String slug) {
        if (DEMO_SLUG.equals(slug))
            return ResponseEntity.badRequest()
                .body(Map.of("error", "La barbería demo no se puede desactivar."));

        return tenantRepo.findById(slug)
            .map(t -> {
                t.setActivo(!t.isActivo());
                tenantRepo.save(t);
                return ResponseEntity.ok(Map.of("activo", t.isActivo(), "slug", slug));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/codigos/generar")
    public ResponseEntity<?> generarCodigos(@RequestBody Map<String, Integer> body) {
        int cantidad = body.getOrDefault("cantidad", 0);
        if (cantidad < 1 || cantidad > 50)
            return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe estar entre 1 y 50."));

        List<Map<String, Object>> generados = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            String code;
            do { code = generarCodigo(); } while (codigoRepo.existsById(code));
            CodigoInvitacion c = new CodigoInvitacion();
            c.setCodigo(code);
            c.setCreadoAt(LocalDateTime.now());
            codigoRepo.save(c);
            generados.add(Map.of("codigo", code, "creadoAt", c.getCreadoAt().toString()));
        }
        return ResponseEntity.ok(generados);
    }

    @GetMapping("/codigos")
    public List<Map<String, Object>> getCodigos() {
        return codigoRepo.findAllByOrderByCreadoAtDesc().stream()
            .map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("codigo", c.getCodigo());
                m.put("usado", c.isUsado());
                m.put("usadoPorUsername", c.getUsadoPorUsername());
                m.put("usadoAt", c.getUsadoAt() != null ? c.getUsadoAt().toString() : null);
                m.put("creadoAt", c.getCreadoAt().toString());
                return m;
            })
            .toList();
    }

    @DeleteMapping("/tenants/{slug}")
    @Transactional
    public ResponseEntity<?> eliminarTenant(@PathVariable String slug) {
        if (DEMO_SLUG.equals(slug))
            return ResponseEntity.badRequest()
                .body(Map.of("error", "La barbería demo no se puede eliminar."));

        if (!tenantRepo.existsById(slug))
            return ResponseEntity.notFound().build();

        // HorarioBarbero no tiene tenantId; se elimina via barberoIds
        List<Long> barberoIds = barberoRepo.findByTenantId(slug)
            .stream().map(Barbero::getId).toList();
        if (!barberoIds.isEmpty()) horarioRepo.deleteByBarberoIdIn(barberoIds);

        // Resto en orden: hijos primero, tenant al final
        bloqueoRepo.deleteByTenantId(slug);
        turnoRepo.deleteByTenantId(slug);
        visitaRepo.deleteByTenantId(slug);
        propiedadRepo.deleteByTenantId(slug);
        barberoRepo.deleteAll(barberoRepo.findByTenantId(slug));
        servicioRepo.deleteByTenantId(slug);
        galeriaRepo.deleteByTenantId(slug);
        usuarioRepo.deleteByTenantId(slug);
        negocioConfigRepo.deleteById(slug);
        tenantRepo.deleteById(slug);

        return ResponseEntity.ok(Map.of("eliminado", slug));
    }

    private String generarCodigo() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
        return sb.toString();
    }
}
