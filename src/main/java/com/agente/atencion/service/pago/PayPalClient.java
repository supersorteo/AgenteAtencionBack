package com.agente.atencion.service.pago;

import com.agente.atencion.config.PayPalProperties;
import com.agente.atencion.entity.Pago;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);
    private static final Pattern ID_ORDEN = Pattern.compile("^[A-Za-z0-9-]{5,64}$");
    private static final Pattern VALOR_SEGURO = Pattern.compile("^[A-Za-z0-9+/=_:.\\-?&%~]{1,2048}$");

    private final RestClient http;
    private final PayPalProperties props;
    private String accessToken;
    private Instant tokenVenceEn = Instant.EPOCH;

    @Autowired
    public PayPalClient(PayPalProperties props) {
        this(builderConTimeouts(), props);
    }

    PayPalClient(RestClient.Builder builder, PayPalProperties props) {
        this.props = props;
        this.http = builder.baseUrl(props.baseUrl()).build();
    }

    // Propio de este cliente: no se expone como bean RestClient.Builder para no alterar el de Spring AI (Groq)
    private static RestClient.Builder builderConTimeouts() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(fabrica);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenRespuesta(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Link(String href, String rel) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Monto(@JsonProperty("currency_code") String currencyCode, BigDecimal value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Captura(String id, String status, Monto amount, @JsonProperty("custom_id") String customId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagos(List<Captura> captures) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UnidadCompra(
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("custom_id") String customId,
        Pagos payments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrdenPayPal(
        String id,
        String status,
        @JsonProperty("purchase_units") List<UnidadCompra> purchaseUnits,
        List<Link> links) {

        public Captura primeraCaptura() {
            if (purchaseUnits == null || purchaseUnits.isEmpty()) return null;
            Pagos pagos = purchaseUnits.get(0).payments();
            if (pagos == null || pagos.captures() == null || pagos.captures().isEmpty()) return null;
            return pagos.captures().get(0);
        }

        public String customId() {
            if (purchaseUnits == null || purchaseUnits.isEmpty()) return null;
            return purchaseUnits.get(0).customId();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VerificacionRespuesta(@JsonProperty("verification_status") String verificationStatus) {}

    public record OrdenCreada(String id, String urlDeAprobacion) {}

    public OrdenCreada crearOrden(Pago pago, String descripcion, String returnUrl, String cancelUrl) {
        Map<String, Object> experiencia = new LinkedHashMap<>();
        experiencia.put("brand_name", "Corteo");
        experiencia.put("user_action", "PAY_NOW");
        experiencia.put("shipping_preference", "NO_SHIPPING");
        experiencia.put("return_url", returnUrl);
        experiencia.put("cancel_url", cancelUrl);

        Map<String, Object> unidad = new LinkedHashMap<>();
        unidad.put("reference_id", pago.getReferencia());
        unidad.put("custom_id", pago.getReferencia());
        unidad.put("description", descripcion);
        unidad.put("amount", Map.of(
            "currency_code", pago.getMoneda(),
            "value", pago.getMonto().setScale(2, RoundingMode.HALF_UP).toPlainString()));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("intent", "CAPTURE");
        cuerpo.put("purchase_units", List.of(unidad));
        cuerpo.put("payment_source", Map.of("paypal", Map.of("experience_context", experiencia)));

        OrdenPayPal orden = ejecutar("crear la orden", () -> http.post()
            .uri("/v2/checkout/orders")
            .header("Authorization", "Bearer " + token())
            .header("PayPal-Request-Id", pago.getReferencia())
            .contentType(MediaType.APPLICATION_JSON)
            .body(cuerpo)
            .retrieve()
            .body(OrdenPayPal.class));

        String url = orden == null || orden.links() == null ? null : orden.links().stream()
            .filter(l -> "payer-action".equals(l.rel()) || "approve".equals(l.rel()))
            .map(Link::href)
            .findFirst().orElse(null);
        if (orden == null || orden.id() == null || url == null || !url.startsWith("https://")) {
            log.error("PayPal no devolvió una orden utilizable para la referencia {}", pago.getReferencia());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con PayPal.");
        }
        return new OrdenCreada(orden.id(), url);
    }

    public OrdenPayPal obtenerOrden(String orderId) {
        validarIdOrden(orderId);
        OrdenPayPal orden = ejecutar("consultar la orden", () -> http.get()
            .uri("/v2/checkout/orders/{id}", orderId)
            .header("Authorization", "Bearer " + token())
            .retrieve()
            .body(OrdenPayPal.class));
        return exigir(orden);
    }

    public OrdenPayPal capturar(String orderId) {
        validarIdOrden(orderId);
        OrdenPayPal orden = ejecutar("capturar la orden", () -> http.post()
            .uri("/v2/checkout/orders/{id}/capture", orderId)
            .header("Authorization", "Bearer " + token())
            .header("PayPal-Request-Id", "cap-" + orderId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .retrieve()
            .body(OrdenPayPal.class));
        return exigir(orden);
    }

    public boolean verificarFirma(String transmissionId, String transmissionTime, String transmissionSig,
                                  String certUrl, String authAlgo, String webhookId, String cuerpoOriginal) {
        for (String valor : new String[] {transmissionId, transmissionTime, transmissionSig, certUrl, authAlgo, webhookId}) {
            if (valor == null || !VALOR_SEGURO.matcher(valor).matches()) return false;
        }
        if (!certificadoDePayPal(certUrl) || !esObjetoJsonUnico(cuerpoOriginal)) return false;

        String cuerpo = "{\"auth_algo\":\"" + authAlgo + "\",\"cert_url\":\"" + certUrl
            + "\",\"transmission_id\":\"" + transmissionId + "\",\"transmission_sig\":\"" + transmissionSig
            + "\",\"transmission_time\":\"" + transmissionTime + "\",\"webhook_id\":\"" + webhookId
            + "\",\"webhook_event\":" + cuerpoOriginal.trim() + "}";

        VerificacionRespuesta respuesta = ejecutar("verificar el webhook", () -> http.post()
            .uri("/v1/notifications/verify-webhook-signature")
            .header("Authorization", "Bearer " + token())
            .contentType(MediaType.APPLICATION_JSON)
            .body(cuerpo)
            .retrieve()
            .body(VerificacionRespuesta.class));
        return respuesta != null && "SUCCESS".equals(respuesta.verificationStatus());
    }

    static boolean certificadoDePayPal(String certUrl) {
        try {
            URI uri = URI.create(certUrl);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getHost().endsWith(".paypal.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // El evento se incrusta tal cual en la verificación: debe ser exactamente un objeto JSON, sin texto extra
    static boolean esObjetoJsonUnico(String texto) {
        if (texto == null) return false;
        String t = texto.trim();
        if (t.length() < 2 || t.charAt(0) != '{' || t.charAt(t.length() - 1) != '}') return false;
        int profundidad = 0;
        boolean enCadena = false;
        boolean escape = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (enCadena) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') enCadena = false;
                continue;
            }
            if (c == '"') {
                enCadena = true;
            } else if (c == '{' || c == '[') {
                profundidad++;
            } else if (c == '}' || c == ']') {
                profundidad--;
                if (profundidad < 0 || (profundidad == 0 && i != t.length() - 1)) return false;
            }
        }
        return profundidad == 0 && !enCadena;
    }

    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(tokenVenceEn)) return accessToken;
        TokenRespuesta respuesta = ejecutar("autenticarse", () -> http.post()
            .uri("/v1/oauth2/token")
            .headers(h -> h.setBasicAuth(props.clientId(), props.clientSecret()))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=client_credentials")
            .retrieve()
            .body(TokenRespuesta.class));
        if (respuesta == null || respuesta.accessToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comunicar con PayPal.");
        }
        long segundos = respuesta.expiresIn() == null ? 300 : respuesta.expiresIn();
        accessToken = respuesta.accessToken();
        tokenVenceEn = Instant.now().plusSeconds(Math.max(30, segundos - 60));
        return accessToken;
    }

    private static void validarIdOrden(String orderId) {
        if (orderId == null || !ID_ORDEN.matcher(orderId).matches()) {
            throw new IllegalArgumentException("Identificador de orden inválido.");
        }
    }

    private static OrdenPayPal exigir(OrdenPayPal orden) {
        if (orden == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal no devolvió la orden.");
        return orden;
    }

    private <T> T ejecutar(String accion, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            int estado = e.getStatusCode().value();
            String cuerpo = e.getResponseBodyAsString();
            log.error("PayPal rechazó la solicitud para {}: HTTP {} {}", accion, estado,
                cuerpo.length() > 300 ? cuerpo.substring(0, 300) : cuerpo);
            if (estado == 422) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "PayPal no pudo procesar el pago.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comunicar con PayPal.");
        } catch (RestClientException e) {
            log.error("Error de red al {} con PayPal", accion, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comunicar con PayPal.");
        }
    }
}
