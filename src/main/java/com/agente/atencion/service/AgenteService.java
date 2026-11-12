package com.agente.atencion.service;

import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.tools.NegocioTools;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgenteService {

    private static final Logger log = LoggerFactory.getLogger(AgenteService.class);
    private static final class Sesion {
        final MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(40)
            .build();
        volatile boolean esperandoReserva;
    }

    @Autowired private ChatClient.Builder chatClientBuilder;
    @Autowired private TenantConfigService tenantConfigService;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private NegocioTools negocioTools;
    @Autowired private ChatGuard chatGuard;

    // Sesiones acotadas: 30 min sin uso o más de 20.000 simultáneas las descartan
    private final com.github.benmanes.caffeine.cache.Cache<String, Sesion> sesiones = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
        .maximumSize(20_000)
        .expireAfterAccess(java.time.Duration.ofMinutes(30))
        .build();

    public String chat(String tenantId, String sessionId, String mensaje) {
        return chat(tenantId, sessionId, mensaje, "desconocido");
    }

    public String chat(String tenantId, String sessionId, String mensaje, String cliente) {
        Tenant tenant = tenantConfigService.getTenant(tenantId);
        if (tenant == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado.");
        if (SuscripcionService.estado(tenant) != SuscripcionService.EstadoAcceso.ACTIVO) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Barbería desactivada");

        String sessionKey = tenantId + "-" + sessionId;

        log.info("[AgenteService] chat tenantId={} session={} mensajeLen={}", tenantId, sessionKey, mensaje.length());

        Sesion sesion = sesiones.get(sessionKey, k -> new Sesion());
        MessageWindowChatMemory memory = sesion.memory;

        // Contexto genérico si el tenant todavía no tiene uno configurado
        String contexto = tenant.getContexto();
        if (contexto == null || contexto.isBlank()) {
            contexto = "Sos el asistente virtual de " + tenant.getNombre()
                + ". Ayudás a los clientes con información sobre servicios, precios y reservas de turnos.";
        }

        final String contextoFinal = contexto;
        NegocioConfig negocioConfig = tenantConfigService.getNegocioConfig(tenantId);
        String systemPrompt;
        if (negocioConfig != null) {
            String ctx = (negocioConfig.getNombre() != null && !negocioConfig.getNombre().isBlank()
                    && tenant.getNombre() != null && !tenant.getNombre().isBlank())
                ? contextoFinal.replace(tenant.getNombre(), negocioConfig.getNombre())
                : contextoFinal;
            systemPrompt = buildDatosNegocio(negocioConfig, tenantId) + "\n\n" + ctx;
        } else {
            systemPrompt = contextoFinal;
        }

        ChatClient client = chatClientBuilder.clone()
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
            .build();

        boolean eraEsperandoConfirmacion = sesion.esperandoReserva;
        boolean usuarioAfirma = esAfirmativo(mensaje);

        if (tieneIntentReserva(mensaje)) {
            sesion.esperandoReserva = true;
            eraEsperandoConfirmacion = true;
        }

        String respuesta = chatGuard.ejecutar(tenantId, cliente, () -> {
            TenantContext.set(tenantId);
            try {
                return client.prompt()
                    .user(mensaje)
                    .tools(negocioTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionKey))
                    .call()
                    .content();
            } finally {
                TenantContext.clear();
            }
        });

        if (respuesta == null) respuesta = "";
        log.info("[AgenteService] respuesta sesion={} awaiting={} preview='{}'",
            sessionKey, eraEsperandoConfirmacion,
            respuesta.length() > 160 ? respuesta.substring(0, 160) + "..." : respuesta);

        if (respuesta.contains("[ABRIR_MODAL_RESERVA]")) {
            sesion.esperandoReserva = false;
        } else if (eraEsperandoConfirmacion && usuarioAfirma) {
            log.info("[AgenteService] Inyectando [ABRIR_MODAL_RESERVA] sesion={}", sessionKey);
            respuesta = respuesta.trim() + " [ABRIR_MODAL_RESERVA]";
            sesion.esperandoReserva = false;
        } else {
            String norm = respuesta.toLowerCase()
                .replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u");
            boolean agenteInvita = norm.contains("avisame") || norm.contains("decime")
                || norm.contains("cuando quieras") || norm.contains("confirmas")
                || norm.contains("confirmame") || norm.contains("agendamos")
                || norm.contains("hacemos la reserva") || norm.contains("procedemos")
                || norm.contains("deseas reservar") || norm.contains("quieres reservar")
                || norm.contains("continuar con la reserva")
                || (norm.contains("reserva") && norm.contains("?"))
                || (norm.contains("turno") && norm.contains("?"));
            if (agenteInvita) sesion.esperandoReserva = true;
        }

        return respuesta;
    }

    private boolean tieneIntentReserva(String msg) {
        String clean = msg.trim().toLowerCase()
            .replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u");
        return clean.contains("reservar") || clean.contains("reserva") || clean.contains("turno")
            || clean.contains("agendar") || clean.contains("cita");
    }

    private boolean esAfirmativo(String msg) {
        String clean = msg.trim().toLowerCase()
            .replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u");
        return clean.matches("s[ii]!?|ok|dale|claro|bueno|adelante|perfecto|listo|vamos|"
            + "si quiero|quiero reservar?|reservar?|buenisimo|genial|por supuesto|obvio|va|"
            + "si por favor|si gracias|claro que si|de acuerdo|confirmo|confirmado");
    }

    private String buildDatosNegocio(NegocioConfig cfg, String tenantId) {
        StringBuilder sb = new StringBuilder("=== DATOS ACTUALES DEL NEGOCIO ===\n");
        if (cfg.getNombre()    != null) sb.append("Nombre: ")    .append(cfg.getNombre())    .append("\n");
        if (cfg.getDireccion() != null) sb.append("Dirección: ") .append(cfg.getDireccion()) .append("\n");
        if (cfg.getTelefono()  != null) sb.append("Teléfono: ")  .append(cfg.getTelefono())  .append("\n");
        if (cfg.getEmail()     != null) sb.append("Email: ")     .append(cfg.getEmail())     .append("\n");
        if (cfg.getHorario1()  != null) sb.append("Horario: ")   .append(cfg.getHorario1())  .append("\n");
        if (cfg.getHorario2()  != null && !cfg.getHorario2().isBlank()) sb.append("         ").append(cfg.getHorario2()).append("\n");
        if (cfg.getHorario3()  != null && !cfg.getHorario3().isBlank()) sb.append("         ").append(cfg.getHorario3()).append("\n");

        var barberos = barberoRepository.findByTenantIdAndActivoTrue(tenantId);
        if (!barberos.isEmpty()) {
            sb.append("=== BARBEROS (ÚNICOS REALES, NO INVENTES OTROS) ===\n");
            barberos.forEach(b -> sb.append("- ").append(b.getNombre()).append("\n"));
        }

        var servicios = servicioRepository.findByTenantIdAndActivoTrue(tenantId);
        if (!servicios.isEmpty()) {
            sb.append("=== SERVICIOS Y PRECIOS (ÚNICOS REALES, NO INVENTES OTROS) ===\n");
            servicios.forEach(s -> {
                sb.append("- ").append(s.getNombre()).append(": $").append(s.getPrecio().intValue());
                if (s.getDuracionMinutos() != null) sb.append(" (").append(s.getDuracionMinutos()).append(" min)");
                if (s.getDescripcion() != null && !s.getDescripcion().isBlank()) sb.append(" — ").append(s.getDescripcion());
                sb.append("\n");
            });
        }

        sb.append("=== REGLAS ABSOLUTAS ===\n");
        sb.append("- JAMÁS inventes barberos, servicios ni precios que no estén en las listas de arriba. Son los ÚNICOS que existen.\n");
        sb.append("- Si una herramienta devuelve 0 resultados, decís que no hay disponibilidad. No inventes alternativas.\n");
        sb.append("- El nombre del negocio es exactamente: ").append(cfg.getNombre() != null ? cfg.getNombre() : "este negocio").append(". No uses variaciones ni apodos.\n");
        sb.append("- RESERVAS: En cuanto el cliente mencione que quiere reservar, hacer un turno o pedir cita, respondé ÚNICAMENTE: '¡Claro! Para reservar necesitás completar un formulario rápido. ¿Te lo abro ahora?' NUNCA pidas nombre, teléfono, servicio, barbero ni fecha por el chat. Si el cliente confirma con 'si', 'dale', 'claro' o similar, incluí EXACTAMENTE el texto [ABRIR_MODAL_RESERVA] en tu respuesta. Si el cliente dice 'no', 'después' o similar, respondé normalmente y seguí siendo informativo.\n");
        sb.append("===================================");
        return sb.toString();
    }
}
