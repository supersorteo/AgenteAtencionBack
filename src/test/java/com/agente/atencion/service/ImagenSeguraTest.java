package com.agente.atencion.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagenSeguraTest {

    private static byte[] con(byte[] cabecera) {
        return Arrays.copyOf(cabecera, 64);
    }

    @Test
    void reconocePorLosPrimerosBytes() {
        assertEquals("jpg", ImagenSegura.extension(con(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).orElseThrow());
        assertEquals("png", ImagenSegura.extension(con(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).orElseThrow());
        assertEquals("gif", ImagenSegura.extension(con("GIF89a".getBytes(StandardCharsets.US_ASCII))).orElseThrow());
        assertEquals("webp", ImagenSegura.extension(con("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).orElseThrow());
    }

    @Test
    void rechazaHtmlSvgYEjecutablesAunqueDigan32ImagePng() {
        assertTrue(ImagenSegura.extension(con("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty());
        assertTrue(ImagenSegura.extension(con("<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes(StandardCharsets.UTF_8))).isEmpty());
        assertTrue(ImagenSegura.extension(con("MZ\u0090\0".getBytes(StandardCharsets.ISO_8859_1))).isEmpty());
    }

    @Test
    void rechazaVacioNuloOMuyCorto() {
        assertTrue(ImagenSegura.extension(null).isEmpty());
        assertTrue(ImagenSegura.extension(new byte[0]).isEmpty());
        assertTrue(ImagenSegura.extension(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}).isEmpty());
    }
}
