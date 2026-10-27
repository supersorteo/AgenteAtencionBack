package com.agente.atencion.entity;

public enum TipoCodigo {
    PLAN_1M(1, "1M"),
    PLAN_3M(3, "3M"),
    PLAN_6M(6, "6M"),
    PLAN_12M(12, "12M");

    public final int meses;
    public final String prefijo;

    TipoCodigo(int meses, String prefijo) {
        this.meses = meses;
        this.prefijo = prefijo;
    }
}
