package com.agente.atencion.service;

import java.util.Optional;

public final class ImagenSegura {

    private ImagenSegura() {}

    // Decide por los primeros bytes, no por el tipo que declara el cliente. SVG queda fuera a propósito (puede llevar scripts).
    public static Optional<String> extension(byte[] b) {
        if (b == null || b.length < 12) return Optional.empty();
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Optional.of("jpg");
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return Optional.of("png");
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a') return Optional.of("gif");
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return Optional.of("webp");
        return Optional.empty();
    }
}
