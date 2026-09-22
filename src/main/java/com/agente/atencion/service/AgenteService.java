package com.agente.atencion.service;

import com.agente.atencion.entity.Tenant;
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

        // ChatClient se construye en cada request con el prompt actualizado desde BD
        ChatClient client = chatClientBuilder.clone()
            .defaultSystem(tenant.getContexto())
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
}
