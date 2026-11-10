package com.agente.atencion.security;

import com.agente.atencion.repository.UsuarioRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthCacheService {

    public record UsuarioAuth(boolean activo, LocalDateTime passwordChangedAt) {}

    private static final Duration TTL = Duration.ofSeconds(60);

    private final UsuarioRepository repo;
    private final Cache<String, Optional<UsuarioAuth>> cache;

    @Autowired
    public AuthCacheService(UsuarioRepository repo) {
        this(repo, Ticker.systemTicker());
    }

    AuthCacheService(UsuarioRepository repo, Ticker ticker) {
        this.repo = repo;
        this.cache = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(TTL)
            .ticker(ticker)
            .recordStats()
            .build();
    }

    // Respaldo: aunque se olvide un vaciado explícito, el dato envejece como máximo TTL segundos
    public Optional<UsuarioAuth> usuario(String username) {
        if (username == null) return Optional.empty();
        return cache.get(username, u -> repo.findByUsername(u)
            .map(x -> new UsuarioAuth(x.isActivo(), x.getPasswordChangedAt())));
    }

    public void evict(String username) {
        if (username == null) return;
        ejecutarTrasCommit(() -> cache.invalidate(username));
    }

    public void evictAll() {
        ejecutarTrasCommit(cache::invalidateAll);
    }

    private static void ejecutarTrasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
