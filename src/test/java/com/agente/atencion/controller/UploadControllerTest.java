package com.agente.atencion.controller;

import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest {

    private CloudinaryService cloudinary;
    private MockMvc mvc;
    private Authentication admin;

    @BeforeEach
    void setUp() throws Exception {
        cloudinary = mock(CloudinaryService.class);
        when(cloudinary.isConfigured()).thenReturn(true);
        when(cloudinary.upload(any(), eq("t1"))).thenReturn("https://cdn/img.png");
        mvc = MockMvcBuilders.standaloneSetup(new UploadController(cloudinary)).build();
        admin = new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("a", "ADMIN", "t1", null), null, List.of());
    }

    private static byte[] png() {
        return Arrays.copyOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 64);
    }

    @Test
    void unaImagenRealSeSube() throws Exception {
        mvc.perform(multipart("/api/v1/upload/t1").file(new MockMultipartFile("file", "x.png", "image/png", png())).principal(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://cdn/img.png"));
    }

    @Test
    void unHtmlDisfrazadoDeImagenSeRechazaYNoLlegaAlAlmacenamiento() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        mvc.perform(multipart("/api/v1/upload/t1").file(new MockMultipartFile("file", "x.png", "image/png", html)).principal(admin))
            .andExpect(status().isBadRequest());

        verify(cloudinary, never()).upload(any(), any());
    }

    @Test
    void despuesDeLimiteHorarioResponde429() throws Exception {
        for (int i = 0; i < 60; i++) {
            mvc.perform(multipart("/api/v1/upload/t1").file(new MockMultipartFile("file", "x.png", "image/png", png())).principal(admin))
                .andExpect(status().isOk());
        }

        mvc.perform(multipart("/api/v1/upload/t1").file(new MockMultipartFile("file", "x.png", "image/png", png())).principal(admin))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void otroNegocioRecibe403() throws Exception {
        mvc.perform(multipart("/api/v1/upload/otro").file(new MockMultipartFile("file", "x.png", "image/png", png())).principal(admin))
            .andExpect(status().isForbidden());
    }
}
