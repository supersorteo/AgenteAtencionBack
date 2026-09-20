package com.agente.atencion.service;

import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.tools.InmobiliariaTools;
import com.agente.atencion.tools.NegocioTools;
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

    @Autowired private ChatClient.Builder chatClientBuilder;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private NegocioTools negocioTools;
    @Autowired private InmobiliariaTools inmobiliariaTools;

    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    public String chat(String tenantId, String sessionId, String mensaje) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Negocio no encontrado: " + tenantId));

        ChatClient client = clientCache.computeIfAbsent(tenantId, id ->
            chatClientBuilder
                .defaultSystem(tenant.getContexto())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(
                    MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(10)
                        .build())
                    .build())
                .build()
        );

        TenantContext.set(tenantId);
        try {
            return client.prompt()
                .user(mensaje)
                .tools(negocioTools, inmobiliariaTools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, tenantId + "-" + sessionId))
                .call()
                .content();
        } finally {
            TenantContext.clear();
        }
    }
}
