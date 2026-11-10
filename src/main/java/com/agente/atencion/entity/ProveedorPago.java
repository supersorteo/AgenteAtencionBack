package com.agente.atencion.entity;

public enum ProveedorPago {
    MERCADOPAGO("ARS"),
    PAYPAL("USD");

    public final String moneda;

    ProveedorPago(String moneda) {
        this.moneda = moneda;
    }
}
