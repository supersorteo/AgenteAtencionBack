package com.agente.atencion.security;

import org.springframework.security.core.Authentication;

public final class AuthUtils {

    private AuthUtils() {}

    public static boolean isAdmin(Authentication auth, String tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)) return false;
        return "ADMIN".equals(u.rol()) && tenantId.equals(u.tenantId());
    }
}
