package com.agente.atencion.service;

import com.agente.atencion.dto.RegistroRequest;
import com.agente.atencion.dto.RegistroResponse;
import com.agente.atencion.entity.CodigoInvitacion;
import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.CodigoInvitacionRepository;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDateTime;

@Service
public class RegistroService {

    private final TenantRepository tenantRepo;
    private final NegocioConfigRepository configRepo;
    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder encoder;
    private final CodigoInvitacionRepository codigoRepo;

    public RegistroService(TenantRepository tenantRepo, NegocioConfigRepository configRepo,
                           UsuarioRepository usuarioRepo, PasswordEncoder encoder,
                           CodigoInvitacionRepository codigoRepo) {
        this.tenantRepo = tenantRepo;
        this.configRepo = configRepo;
        this.usuarioRepo = usuarioRepo;
        this.encoder = encoder;
        this.codigoRepo = codigoRepo;
    }

    @Transactional
    public RegistroResponse registrar(RegistroRequest req) {
        CodigoInvitacion codigoEntidad = resolverCodigo(req.codigoInvitacion());
        validarCampos(req);

        String slug = generarSlug(req.nombreNegocio());
        if (slug.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El nombre del negocio no genera un identificador válido. Usá letras o números.");

        if (tenantRepo.existsById(slug))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un negocio con ese nombre. Probá con uno diferente.");

        if (usuarioRepo.findByUsernameAndActivoTrue(req.username()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ese nombre de usuario ya está en uso.");

        Tenant tenant = new Tenant();
        tenant.setId(slug);
        tenant.setNombre(req.nombreNegocio());
        tenant.setActivo(true);
        tenant.setContexto("Sos el asistente virtual de " + req.nombreNegocio()
            + ". Ayudás a los clientes con información sobre servicios, precios y reservas de turnos."
            + " Siempre respondé de forma amable, breve y profesional.");
        tenantRepo.save(tenant);

        NegocioConfig config = new NegocioConfig();
        config.setTenantId(slug);
        config.setNombre(req.nombreNegocio());
        config.setTagline("Tu barbería online");
        config.setTimeZone("America/Montevideo");
        configRepo.save(config);

        Usuario admin = new Usuario();
        admin.setUsername(req.username());
        admin.setPassword(encoder.encode(req.password()));
        admin.setTenantId(slug);
        admin.setRol("ADMIN");
        admin.setActivo(true);
        usuarioRepo.save(admin);

        codigoEntidad.setUsado(true);
        codigoEntidad.setUsadoPorUsername(req.username());
        codigoEntidad.setUsadoAt(LocalDateTime.now());
        codigoRepo.save(codigoEntidad);

        return new RegistroResponse(slug, req.username(), "/" + slug + "/admin",
            "¡Bienvenido! Tu barbería está lista.");
    }

    private CodigoInvitacion resolverCodigo(String codigoStr) {
        if (codigoStr == null || codigoStr.isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Código de invitación requerido. Contactá al administrador para obtenerlo.");

        CodigoInvitacion c = codigoRepo.findById(codigoStr.trim().toUpperCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Código de invitación inválido. Contactá al administrador."));

        if (c.isUsado())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Este código ya fue utilizado. Solicitá uno nuevo al administrador.");

        return c;
    }

    private void validarCampos(RegistroRequest req) {
        if (req.nombreNegocio() == null || req.nombreNegocio().isBlank()
                || req.nombreNegocio().length() < 2 || req.nombreNegocio().length() > 60)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El nombre del negocio debe tener entre 2 y 60 caracteres.");

        if (req.adminNombre() == null || req.adminNombre().isBlank()
                || req.adminNombre().length() < 2 || req.adminNombre().length() > 60)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tu nombre debe tener entre 2 y 60 caracteres.");

        if (req.username() == null || req.username().isBlank()
                || req.username().length() < 3 || req.username().length() > 30
                || !req.username().matches("[a-zA-Z0-9._-]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El usuario debe tener 3-30 caracteres (letras, números, puntos, guiones, underscore).");

        if (req.password() == null || req.password().length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La contraseña debe tener al menos 8 caracteres.");
    }

    String generarSlug(String nombre) {
        String result = Normalizer.normalize(nombre, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .trim()
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-");
        return result.length() > 40 ? result.substring(0, 40) : result;
    }
}
