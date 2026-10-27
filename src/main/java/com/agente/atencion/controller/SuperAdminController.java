package com.agente.atencion.controller;

import com.agente.atencion.dto.GenerarCodigosRequest;
import com.agente.atencion.dto.TenantResumen;
import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.CodigoInvitacion;
import com.agente.atencion.entity.PlanPrecio;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.repository.*;
import jakarta.validation.Valid;
import com.agente.atencion.service.CloudinaryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private final PlanPrecioRepository planPrecioRepo;
    private final BarberoRepository barberoRepo;
    private final HorarioBarberoRepository horarioRepo;
    private final BloqueoHorarioRepository bloqueoRepo;
    private final TurnoRepository turnoRepo;
    private final ServicioRepository servicioRepo;
    private final CategoriaServicioRepository categoriaServicioRepo;
    private final GaleriaRepository galeriaRepo;
    private final NegocioConfigRepository negocioConfigRepo;
    private final CloudinaryService cloudinaryService;
    private final PasswordEncoder encoder;

    public SuperAdminController(TenantRepository tenantRepo, UsuarioRepository usuarioRepo,
                                CodigoInvitacionRepository codigoRepo, PlanPrecioRepository planPrecioRepo,
                                BarberoRepository barberoRepo,
                                HorarioBarberoRepository horarioRepo, BloqueoHorarioRepository bloqueoRepo,
                                TurnoRepository turnoRepo, ServicioRepository servicioRepo,
                                CategoriaServicioRepository categoriaServicioRepo,
                                GaleriaRepository galeriaRepo, NegocioConfigRepository negocioConfigRepo,
                                CloudinaryService cloudinaryService, PasswordEncoder encoder) {
        this.tenantRepo = tenantRepo;
        this.usuarioRepo = usuarioRepo;
        this.codigoRepo = codigoRepo;
        this.planPrecioRepo = planPrecioRepo;
        this.barberoRepo = barberoRepo;
        this.horarioRepo = horarioRepo;
        this.bloqueoRepo = bloqueoRepo;
        this.turnoRepo = turnoRepo;
        this.servicioRepo = servicioRepo;
        this.categoriaServicioRepo = categoriaServicioRepo;
        this.galeriaRepo = galeriaRepo;
        this.negocioConfigRepo = negocioConfigRepo;
        this.cloudinaryService = cloudinaryService;
        this.encoder = encoder;
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
                adminByTenant.getOrDefault(t.getId(), "—"),
                t.getFechaRegistro(),
                t.getFechaVencimientoSuscripcion()
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
    public ResponseEntity<?> generarCodigos(@RequestBody @Valid GenerarCodigosRequest req) {
        List<Map<String, Object>> generados = new ArrayList<>();
        for (int i = 0; i < req.cantidad(); i++) {
            String code;
            do { code = generarCodigoPorTipo(req.tipo()); } while (codigoRepo.existsById(code));
            CodigoInvitacion c = new CodigoInvitacion();
            c.setCodigo(code);
            c.setTipo(req.tipo());
            c.setCreadoAt(LocalDateTime.now());
            codigoRepo.save(c);
            generados.add(Map.of(
                "codigo", code,
                "tipo", req.tipo().name(),
                "creadoAt", c.getCreadoAt().toString()
            ));
        }
        return ResponseEntity.ok(generados);
    }

    @GetMapping("/codigos")
    public List<Map<String, Object>> getCodigos() {
        return codigoRepo.findAllByOrderByCreadoAtDesc().stream()
            .map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("codigo", c.getCodigo());
                m.put("tipo", c.getTipo() != null ? c.getTipo().name() : null);
                m.put("usado", c.isUsado());
                m.put("usadoPorUsername", c.getUsadoPorUsername());
                m.put("usadoAt", c.getUsadoAt() != null ? c.getUsadoAt().toString() : null);
                m.put("fechaVencimiento", c.getFechaVencimiento() != null ? c.getFechaVencimiento().toString() : null);
                m.put("creadoAt", c.getCreadoAt().toString());
                return m;
            })
            .toList();
    }

    @GetMapping("/plan-precios")
    public List<PlanPrecio> getPlanPrecios() {
        return planPrecioRepo.findAll();
    }

    @PutMapping("/plan-precios/{tipo}")
    public ResponseEntity<?> updatePlanPrecio(
            @PathVariable TipoCodigo tipo,
            @RequestBody Map<String, BigDecimal> body) {
        PlanPrecio p = planPrecioRepo.findById(tipo)
            .orElseGet(() -> { PlanPrecio np = new PlanPrecio(); np.setTipo(tipo); return np; });
        if (body.containsKey("precioUsd")) p.setPrecioUsd(body.get("precioUsd"));
        if (body.containsKey("precioUyu")) p.setPrecioUyu(body.get("precioUyu"));
        if (body.containsKey("precioArs")) p.setPrecioArs(body.get("precioArs"));
        return ResponseEntity.ok(planPrecioRepo.save(p));
    }

    @DeleteMapping("/codigos/legacy")
    @Transactional
    public ResponseEntity<?> eliminarCodigosLegacy() {
        long eliminados = codigoRepo.deleteByTipoIsNull();
        return ResponseEntity.ok(Map.of("eliminados", eliminados));
    }

    @DeleteMapping("/tenants/{slug}")
    @Transactional
    public ResponseEntity<?> eliminarTenant(@PathVariable String slug) {
        if (DEMO_SLUG.equals(slug))
            return ResponseEntity.badRequest()
                .body(Map.of("error", "La barbería demo no se puede eliminar."));

        if (!tenantRepo.existsById(slug))
            return ResponseEntity.notFound().build();

        // Guardar username del admin antes de eliminar usuarios para limpiar su código
        String adminUsername = usuarioRepo.findByTenantIdAndActivoTrue(slug).stream()
            .filter(u -> "ADMIN".equals(u.getRol()))
            .map(u -> u.getUsername())
            .findFirst().orElse(null);

        // HorarioBarbero no tiene tenantId; se elimina via barberoIds
        List<Long> barberoIds = barberoRepo.findByTenantId(slug)
            .stream().map(Barbero::getId).toList();
        if (!barberoIds.isEmpty()) horarioRepo.deleteByBarberoIdIn(barberoIds);

        // Resto en orden: hijos primero, tenant al final
        bloqueoRepo.deleteByTenantId(slug);
        turnoRepo.deleteByTenantId(slug);
        barberoRepo.deleteAll(barberoRepo.findByTenantId(slug));
        servicioRepo.deleteByTenantId(slug);
        categoriaServicioRepo.deleteByTenantId(slug);
        galeriaRepo.deleteByTenantId(slug);
        usuarioRepo.deleteByTenantId(slug);
        negocioConfigRepo.deleteById(slug);
        tenantRepo.deleteById(slug);

        // Eliminar el código de invitación que usó este tenant
        if (adminUsername != null)
            codigoRepo.findByUsadoPorUsername(adminUsername).ifPresent(codigoRepo::delete);

        cloudinaryService.deleteTenantFolder(slug);

        return ResponseEntity.ok(Map.of("eliminado", slug));
    }

    @PatchMapping("/tenants/{slug}/reset-password")
    public ResponseEntity<?> resetPassword(
            @PathVariable String slug,
            @RequestBody(required = false) Map<String, String> body) {
        var adminOpt = usuarioRepo.findByTenantIdAndActivoTrue(slug).stream()
            .filter(u -> "ADMIN".equals(u.getRol())).findFirst();
        if (adminOpt.isEmpty()) return ResponseEntity.notFound().build();
        var admin = adminOpt.get();
        String customPass = body != null ? body.get("password") : null;
        String nuevaPass = (customPass != null && !customPass.isBlank())
            ? customPass
            : generarPasswordLegible();
        admin.setPassword(encoder.encode(nuevaPass));
        usuarioRepo.save(admin);
        return ResponseEntity.ok(Map.of(
            "username", admin.getUsername(),
            "password", nuevaPass
        ));
    }

    record ActualizarSuscripcionRequest(java.time.LocalDate fechaInicio, TipoCodigo tipoPlan) {}

    @PatchMapping("/tenants/{slug}/suscripcion")
    public ResponseEntity<?> actualizarSuscripcion(
            @PathVariable String slug,
            @RequestBody ActualizarSuscripcionRequest req) {
        if (req.fechaInicio() == null || req.tipoPlan() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "fechaInicio y tipoPlan son requeridos."));
        return tenantRepo.findById(slug)
            .map(t -> {
                t.setFechaRegistro(req.fechaInicio());
                java.time.LocalDate vencimiento = req.fechaInicio().plusMonths(req.tipoPlan().meses);
                t.setFechaVencimientoSuscripcion(vencimiento);
                tenantRepo.save(t);
                String adminUsername = usuarioRepo.findByTenantIdAndActivoTrue(slug).stream()
                    .filter(u -> "ADMIN".equals(u.getRol()))
                    .map(u -> u.getUsername())
                    .findFirst().orElse("—");
                return ResponseEntity.ok(new TenantResumen(
                    t.getId(), t.getNombre(), t.isActivo(), adminUsername,
                    t.getFechaRegistro(), t.getFechaVencimientoSuscripcion()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private String generarPasswordLegible() {
        // 4 letras mayúsculas + 4 dígitos + símbolo, mezcla aleatoria
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String digitos = "23456789";
        String simbolos = "@#$!";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(letras.charAt(RND.nextInt(letras.length())));
        for (int i = 0; i < 4; i++) sb.append(digitos.charAt(RND.nextInt(digitos.length())));
        sb.append(simbolos.charAt(RND.nextInt(simbolos.length())));
        // mezclar
        List<Character> chars = new ArrayList<>();
        for (char c : sb.toString().toCharArray()) chars.add(c);
        Collections.shuffle(chars, RND);
        StringBuilder result = new StringBuilder();
        for (char c : chars) result.append(c);
        return result.toString();
    }

    private String generarCodigoPorTipo(TipoCodigo tipo) {
        StringBuilder sb = new StringBuilder(tipo.prefijo).append("-");
        for (int i = 0; i < tipo.longitudAleatoria; i++)
            sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
        return sb.toString();
    }
}
