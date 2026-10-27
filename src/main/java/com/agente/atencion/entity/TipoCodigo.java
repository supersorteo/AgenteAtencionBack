package com.agente.atencion.entity;

public enum TipoCodigo {
    PLAN_1M(5, 1, "1M"),
    PLAN_3M(6, 3, "3M"),
    PLAN_6M(7, 6, "6M"),
    PLAN_12M(8, 12, "12M");

    public final int longitudAleatoria;
    public final int meses;
    public final String prefijo;

    TipoCodigo(int longitudAleatoria, int meses, String prefijo) {
        this.longitudAleatoria = longitudAleatoria;
        this.meses = meses;
        this.prefijo = prefijo;
    }
}
