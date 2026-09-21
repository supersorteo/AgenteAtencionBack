package com.agente.atencion.controller;

import com.agente.atencion.security.UsuarioAutenticado;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final String UPLOAD_DIR = "./uploads/";
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Value("${cloudinary.cloud-name:}") private String cloudName;
    @Value("${cloudinary.api-key:}")    private String apiKey;
    @Value("${cloudinary.api-secret:}") private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    void init() {
        if (!cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank()) {
            cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
            ));
        }
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
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo se permiten imágenes"));
        }

        try {
            if (cloudinary != null) {
                return uploadCloudinary(file, tenantId);
            }
            return uploadLocal(file, tenantId, contentType);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar el archivo"));
        }
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<?> uploadCloudinary(MultipartFile file, String tenantId) throws IOException {
        Map<String, Object> result = cloudinary.uploader().upload(
            file.getBytes(),
            ObjectUtils.asMap("folder", tenantId)
        );
        return ResponseEntity.ok(Map.of("url", result.get("secure_url")));
    }

    private ResponseEntity<?> uploadLocal(MultipartFile file, String tenantId, String contentType) throws IOException {
        String ext = contentType.substring(contentType.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9]", "");
        String filename = tenantId + "_" + UUID.randomUUID() + "." + ext;
        Path dir = Path.of(UPLOAD_DIR);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return ResponseEntity.ok(Map.of("url", "/uploads/" + filename));
    }
}
