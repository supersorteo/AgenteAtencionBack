package com.agente.atencion.service;

import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.tools.InmobiliariaTools;
import com.agente.atencion.tools.NegocioTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgenteService {

    private static final Logger log = LoggerFactory.getLogger(AgenteService.class);

    @Autowired private ChatClient.Builder chatClientBuilder;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private NegocioConfigRepository negocioConfigRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private NegocioTools negocioTools;
    @Autowired private InmobiliariaTools inmobiliariaTools;

    // Solo el historial de conversaciones se cachea — el prompt se lee de BD en cada request
    private final Map<String, MessageWindowChatMemory> memoryCache = new ConcurrentHashMap<>();
    // Sesiones donde el agente ya invitó al usuario a confirmar la reserva
    private final Map<String, Boolean> awaitingReserva = new ConcurrentHashMap<>();

    public String chat(String tenantId, String sessionId, String mensaje) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Negocio no encontrado: " + tenantId));

        log.info("[AgenteService] chat tenantId={} sessionId={} mensajeLen={}", tenantId, sessionId, mensaje.length());

        MessageWindowChatMemory memory = memoryCache.computeIfAbsent(tenantId, id ->
            MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(40)
                .build()
        );

        // Nombre dinámico reemplaza el hardcodeado en el contexto del tenant
        String systemPrompt = negocioConfigRepository.findById(tenantId)
            .map(cfg -> {
                String contexto = tenant.getContexto();
                if (cfg.getNombre() != null && tenant.getNombre() != null
                        && !cfg.getNombre().isBlank() && !tenant.getNombre().isBlank()) {
                    contexto = contexto.replace(tenant.getNombre(), cfg.getNombre());
                }
                return buildDatosNegocio(cfg, tenantId) + "\n\n" + contexto;
            })
            .orElse(tenant.getContexto());

        // ChatClient se construye en cada request con el prompt actualizado desde BD
        ChatClient client = chatClientBuilder.clone()
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
            .build();

        String sessionKey = tenantId + "-" + sessionId;
        boolean eraEsperandoConfirmacion = awaitingReserva.getOrDefault(sessionKey, false);
        boolean usuarioAfirma = esAfirmativo(mensaje);

        // Si el usuario menciona reserva explícitamente, activar el flag para este y próximos ciclos
        if (tieneIntentReserva(mensaje)) {
            awaitingReserva.put(sessionKey, true);
            eraEsperandoConfirmacion = awaitingReserva.get(sessionKey);
        }

        TenantContext.set(tenantId);
        String respuesta;
        try {
            respuesta = client.prompt()
                .user(mensaje)
                .tools("barberia-demo".equals(tenantId)
                    ? new Object[]{negocioTools} : new Object[]{negocioTools, inmobiliariaTools})
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionKey))
                .call()
                .content();
        } finally {
            TenantContext.clear();
        }

        if (respuesta == null) respuesta = "";
        log.info("[AgenteService] respuesta sesion={} awaiting={} preview='{}'",
            sessionKey, eraEsperandoConfirmacion,
            respuesta.length() > 160 ? respuesta.substring(0, 160) + "..." : respuesta);

        if (respuesta.contains("[ABRIR_MODAL_RESERVA]")) {
            awaitingReserva.remove(sessionKey);
        } else if (eraEsperandoConfirmacion && usuarioAfirma) {
            log.info("[AgenteService] Inyectando [ABRIR_MODAL_RESERVA] sesion={}", sessionKey);
            respuesta = respuesta.trim() + " [ABRIR_MODAL_RESERVA]";
            awaitingReserva.remove(sessionKey);
        } else {
            // Detectar invitación del agente — lista amplia para cubrir variantes del modelo
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
            if (agenteInvita) awaitingReserva.put(sessionKey, true);
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
