package com.agente.atencion.service;

import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.NegocioConfigRepository;
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
    @Autowired private NegocioTools negocioTools;
    @Autowired private InmobiliariaTools inmobiliariaTools;

    // Solo el historial de conversaciones se cachea — el prompt se lee de BD en cada request
    private final Map<String, MessageWindowChatMemory> memoryCache = new ConcurrentHashMap<>();

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
                return buildDatosNegocio(cfg) + "\n\n" + contexto;
            })
            .orElse(tenant.getContexto());

        // ChatClient se construye en cada request con el prompt actualizado desde BD
        ChatClient client = chatClientBuilder.clone()
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
            .build();

        TenantContext.set(tenantId);
        try {
            return client.prompt()
                .user(mensaje)
                .tools("barberia-demo".equals(tenantId)
                    ? new Object[]{negocioTools} : new Object[]{negocioTools, inmobiliariaTools})
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, tenantId + "-" + sessionId))
                .call()
                .content();
        } finally {
            TenantContext.clear();
        }
    }

    private String buildDatosNegocio(NegocioConfig cfg) {
        StringBuilder sb = new StringBuilder("=== DATOS ACTUALES DEL NEGOCIO ===\n");
        if (cfg.getNombre()    != null) sb.append("Nombre: ")    .append(cfg.getNombre())    .append("\n");
        if (cfg.getDireccion() != null) sb.append("Dirección: ") .append(cfg.getDireccion()) .append("\n");
        if (cfg.getTelefono()  != null) sb.append("Teléfono: ")  .append(cfg.getTelefono())  .append("\n");
        if (cfg.getEmail()     != null) sb.append("Email: ")     .append(cfg.getEmail())     .append("\n");
        if (cfg.getHorario1()  != null) sb.append("Horario: ")   .append(cfg.getHorario1())  .append("\n");
        if (cfg.getHorario2()  != null && !cfg.getHorario2().isBlank()) sb.append("         ").append(cfg.getHorario2()).append("\n");
        if (cfg.getHorario3()  != null && !cfg.getHorario3().isBlank()) sb.append("         ").append(cfg.getHorario3()).append("\n");
        sb.append("=== REGLAS ABSOLUTAS ===\n");
        sb.append("- JAMÁS inventes, agregues ni modifiques nombres de barberos, servicios, precios, fechas u horarios. Usá ÚNICAMENTE lo que devuelven las herramientas.\n");
        sb.append("- Si una herramienta devuelve 2 barberos, mencionás exactamente esos 2. No agregues ni uno más.\n");
        sb.append("- Si una herramienta devuelve 0 resultados, decís que no hay disponibilidad. No inventes alternativas.\n");
        sb.append("- El nombre del negocio es exactamente: ").append(cfg.getNombre() != null ? cfg.getNombre() : "este negocio").append(". No uses variaciones ni apodos.\n");
        sb.append("===================================");
        return sb.toString();
    }
}
