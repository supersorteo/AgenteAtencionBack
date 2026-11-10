package com.agente.atencion.service.pago;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class MercadoPagoWebhookValidator {

    public boolean esValida(String xSignature, String xRequestId, String dataId, String secret) {
        if (secret == null || secret.isBlank() || xSignature == null || xSignature.isBlank()) return false;

        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            int igual = parte.indexOf('=');
            if (igual < 0) continue;
            String clave = parte.substring(0, igual).trim();
            String valor = parte.substring(igual + 1).trim();
            if ("ts".equals(clave)) ts = valor;
            if ("v1".equals(clave)) v1 = valor;
        }
        if (ts == null || ts.isBlank() || v1 == null || v1.isBlank()) return false;

        List<String> partes = new ArrayList<>();
        if (dataId != null && !dataId.isBlank()) partes.add("id:" + dataId.toLowerCase());
        if (xRequestId != null && !xRequestId.isBlank()) partes.add("request-id:" + xRequestId);
        partes.add("ts:" + ts);
        String manifest = String.join(";", partes) + ";";

        String calculado;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            calculado = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            return false;
        }
        return MessageDigest.isEqual(
            calculado.getBytes(StandardCharsets.UTF_8), v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
