package com.agente.atencion.service.pago;

import com.agente.atencion.config.MercadoPagoProperties;
import com.agente.atencion.config.PagosProperties;
import com.agente.atencion.entity.Pago;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);
    private static final Pattern ID_PAGO = Pattern.compile("^\\d{1,20}$");
    static final String RUTA_WEBHOOK = "/api/pagos/webhook/mercadopago";

    private final RestClient http;
    private final MercadoPagoProperties props;
    private final PagosProperties pagosProps;

    @Autowired
    public MercadoPagoClient(MercadoPagoProperties props, PagosProperties pagosProps) {
        this(builderConTimeouts(), props, pagosProps);
    }

    MercadoPagoClient(RestClient.Builder builder, MercadoPagoProperties props, PagosProperties pagosProps) {
        this.props = props;
        this.pagosProps = pagosProps;
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
    record PreferenciaMp(
        String id,
        @JsonProperty("init_point") String initPoint,
        @JsonProperty("sandbox_init_point") String sandboxInitPoint) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PagoMp(
        Long id,
        String status,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("transaction_amount") BigDecimal transactionAmount,
        @JsonProperty("currency_id") String currencyId) {}

    public String crearPreferencia(Pago pago, String titulo) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", titulo);
        item.put("quantity", 1);
        item.put("unit_price", pago.getMonto());
        item.put("currency_id", pago.getMoneda());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("items", List.of(item));
        cuerpo.put("external_reference", pago.getReferencia());
        cuerpo.put("back_urls", Map.of(
            "success", props.successUrl(),
            "failure", props.failureUrl(),
            "pending", props.pendingUrl()));
        if (esHttps(props.successUrl())) cuerpo.put("auto_return", "approved");
        if (esHttps(pagosProps.backendUrl())) cuerpo.put("notification_url", pagosProps.backendUrl() + RUTA_WEBHOOK);

        PreferenciaMp respuesta = ejecutar("crear la preferencia", () -> http.post()
            .uri("/checkout/preferences")
            .header("Authorization", "Bearer " + props.accessToken())
            .body(cuerpo)
            .retrieve()
            .body(PreferenciaMp.class));

        String url = respuesta != null && respuesta.initPoint() != null
            ? respuesta.initPoint()
            : respuesta != null ? respuesta.sandboxInitPoint() : null;
        if (url == null || url.isBlank()) {
            log.error("Mercado Pago no devolvió una URL de checkout para la orden {}", pago.getReferencia());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con Mercado Pago.");
        }
        return url;
    }

    public PagoMp obtenerPago(String paymentId) {
        if (paymentId == null || !ID_PAGO.matcher(paymentId).matches()) {
            throw new IllegalArgumentException("Identificador de pago inválido.");
        }
        PagoMp pago = ejecutar("consultar el pago", () -> http.get()
            .uri("/v1/payments/{id}", paymentId)
            .header("Authorization", "Bearer " + props.accessToken())
            .retrieve()
            .body(PagoMp.class));
        if (pago == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mercado Pago no devolvió el pago.");
        }
        return pago;
    }

    private <T> T ejecutar(String accion, java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            log.error("Mercado Pago rechazó la solicitud para {}: HTTP {}", accion, e.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comunicar con Mercado Pago.");
        } catch (RestClientException e) {
            log.error("Error de red al {} con Mercado Pago", accion, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo comunicar con Mercado Pago.");
        }
    }

    private static boolean esHttps(String url) {
        return url != null && url.startsWith("https://");
    }
}
