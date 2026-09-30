package com.agente.atencion.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

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

    public boolean isConfigured() {
        return cloudinary != null;
    }

    @SuppressWarnings("unchecked")
    public String upload(byte[] bytes, String tenantId) throws IOException {
        Map<String, Object> result = cloudinary.uploader().upload(
            bytes,
            ObjectUtils.asMap("folder", tenantId)
        );
        return (String) result.get("secure_url");
    }

    @SuppressWarnings("unchecked")
    public void deleteTenantFolder(String tenantId) {
        if (cloudinary == null) return;
        try {
            cloudinary.api().deleteResourcesByPrefix(tenantId + "/", ObjectUtils.emptyMap());
            cloudinary.api().deleteFolder(tenantId, ObjectUtils.emptyMap());
        } catch (Exception ignored) {
            // folder puede no existir si nunca se subieron imágenes
        }
    }
}
