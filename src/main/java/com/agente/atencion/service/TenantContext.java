package com.agente.atencion.service;

public class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    public static void set(String id) { CURRENT.set(id); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
