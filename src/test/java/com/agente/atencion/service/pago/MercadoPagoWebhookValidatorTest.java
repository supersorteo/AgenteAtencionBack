package com.agente.atencion.service.pago;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoWebhookValidatorTest {

    private static final String SECRET = "clave-secreta-de-prueba";
    private static final String TS = "1742505638683";

    private final MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator();

    private static String hmac(String secret, String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private static String cabecera(String v1) {
        return "ts=" + TS + ",v1=" + v1;
    }

    @Test
    void firmaCorrecta_conDataIdYRequestId() throws Exception {
        String v1 = hmac(SECRET, "id:123456;request-id:req-1;ts:" + TS + ";");
        assertTrue(validator.esValida(cabecera(v1), "req-1", "123456", SECRET));
    }

    @Test
    void dataIdEnMayusculasSeNormalizaAMinusculas() throws Exception {
        String v1 = hmac(SECRET, "id:abc123;request-id:req-1;ts:" + TS + ";");
        assertTrue(validator.esValida(cabecera(v1), "req-1", "ABC123", SECRET));
    }

    @Test
    void sinRequestId_seOmiteDelManifest() throws Exception {
        String v1 = hmac(SECRET, "id:123456;ts:" + TS + ";");
        assertTrue(validator.esValida(cabecera(v1), null, "123456", SECRET));
        assertTrue(validator.esValida(cabecera(v1), "", "123456", SECRET));
    }

    @Test
    void sinDataIdNiRequestId_soloTs() throws Exception {
        String v1 = hmac(SECRET, "ts:" + TS + ";");
        assertTrue(validator.esValida(cabecera(v1), null, null, SECRET));
    }

    @Test
    void hexadecimalEnMayusculasEsEquivalente() throws Exception {
        String v1 = hmac(SECRET, "id:123456;request-id:req-1;ts:" + TS + ";").toUpperCase();
        assertTrue(validator.esValida(cabecera(v1), "req-1", "123456", SECRET));
    }

    @Test
    void secretoIncorrecto_noEsValida() throws Exception {
        String v1 = hmac("otro-secreto", "id:123456;request-id:req-1;ts:" + TS + ";");
        assertFalse(validator.esValida(cabecera(v1), "req-1", "123456", SECRET));
    }

    @Test
    void dataIdManipulado_noEsValida() throws Exception {
        String v1 = hmac(SECRET, "id:123456;request-id:req-1;ts:" + TS + ";");
        assertFalse(validator.esValida(cabecera(v1), "req-1", "999999", SECRET));
    }

    @Test
    void requestIdManipulado_noEsValida() throws Exception {
        String v1 = hmac(SECRET, "id:123456;request-id:req-1;ts:" + TS + ";");
        assertFalse(validator.esValida(cabecera(v1), "req-2", "123456", SECRET));
    }

    @Test
    void tsManipulado_noEsValida() throws Exception {
        String v1 = hmac(SECRET, "id:123456;request-id:req-1;ts:" + TS + ";");
        assertFalse(validator.esValida("ts=1742505638684,v1=" + v1, "req-1", "123456", SECRET));
    }

    @Test
    void cabecerasIncompletasOMalformadas_noSonValidas() {
        assertFalse(validator.esValida(null, "req-1", "123456", SECRET));
        assertFalse(validator.esValida("", "req-1", "123456", SECRET));
        assertFalse(validator.esValida("basura", "req-1", "123456", SECRET));
        assertFalse(validator.esValida("ts=" + TS, "req-1", "123456", SECRET));
        assertFalse(validator.esValida("v1=abc", "req-1", "123456", SECRET));
        assertFalse(validator.esValida("ts=,v1=", "req-1", "123456", SECRET));
    }

    @Test
    void sinSecretoConfigurado_nuncaEsValida() throws Exception {
        String v1 = hmac("x", "id:123456;request-id:req-1;ts:" + TS + ";");
        assertFalse(validator.esValida(cabecera(v1), "req-1", "123456", ""));
        assertFalse(validator.esValida(cabecera(v1), "req-1", "123456", null));
        assertFalse(validator.esValida(cabecera(v1), "req-1", "123456", "   "));
    }
}
