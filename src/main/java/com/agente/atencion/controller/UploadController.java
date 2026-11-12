package com.agente.atencion.controller;

import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.CloudinaryService;
import com.agente.atencion.service.ImagenSegura;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final String UPLOAD_DIR = "./uploads/";
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private static final int MAX_SUBIDAS_POR_HORA = 60;

    private final CloudinaryService cloudinaryService;
    private final com.github.benmanes.caffeine.cache.Cache<String, java.util.concurrent.atomic.AtomicInteger> subidasPorHora =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(java.time.Duration.ofHours(1)).build();

    public UploadController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<?> upload(@PathVariable String tenantId,
                                     @RequestParam("file") MultipartFile file,
                                     Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !tenantId.equals(u.tenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Archivo vacío"));
        }
        if (file.getSize() > MAX_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Máximo 5 MB por imagen"));
        }
        if (subidasPorHora.get(tenantId, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet() > MAX_SUBIDAS_POR_HORA) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Demasiadas imágenes subidas en la última hora. Probá más tarde."));
        }

        try {
            byte[] bytes = file.getBytes();
            String ext = ImagenSegura.extension(bytes).orElse(null);
            if (ext == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Solo se permiten imágenes JPG, PNG, GIF o WEBP"));
            }
            if (cloudinaryService.isConfigured()) {
                String url = cloudinaryService.upload(bytes, tenantId);
                return ResponseEntity.ok(Map.of("url", url));
            }
            return uploadLocal(bytes, tenantId, ext);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar el archivo"));
        }
    }

    private ResponseEntity<?> uploadLocal(byte[] bytes, String tenantId, String ext) throws IOException {
        String filename = tenantId + "_" + UUID.randomUUID() + "." + ext;
        Path dir = Path.of(UPLOAD_DIR);
        Files.createDirectories(dir);
        Files.write(dir.resolve(filename), bytes);
        return ResponseEntity.ok(Map.of("url", "/uploads/" + filename));
    }
}
