package com.agente.atencion.service.pago;

import com.agente.atencion.config.PayPalProperties;
import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.ProveedorPago;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.service.PagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@Service
public class PayPalService {

    private static final Logger log = LoggerFactory.getLogger(PayPalService.class);
    private static final Set<String> CAPTURA_FALLIDA = Set.of("DECLINED", "FAILED");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public record CheckoutCreado(String referencia, String checkoutUrl) {}

    public record FirmaWebhook(String transmissionId, String transmissionTime, String transmissionSig,
                               String certUrl, String authAlgo) {}

    private final PayPalProperties props;
    private final PayPalClient client;
    private final PagoService pagoService;

    public PayPalService(PayPalProperties props, PayPalClient client, PagoService pagoService) {
        this.props = props;
        this.client = client;
        this.pagoService = pagoService;
    }

    public CheckoutCreado iniciarCheckout(TipoCodigo plan, String tenantId) {
        exigirHabilitado();
        if (!props.credencialesCargadas()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los pagos con PayPal no están disponibles.");
        }
        Pago pago = pagoService.crearOrden(ProveedorPago.PAYPAL, plan, tenantId);
        String ref = URLEncoder.encode(pago.getReferencia(), StandardCharsets.UTF_8);
        PayPalClient.OrdenCreada orden = client.crearOrden(pago, titulo(plan),
            conParametros(props.successUrl(), "ref=" + ref),
            conParametros(props.cancelUrl(), "ref=" + ref, "cancelado=1"));
        pagoService.asociarExternalId(pago.getReferencia(), orden.id());
        return new CheckoutCreado(pago.getReferencia(), orden.urlDeAprobacion());
    }

    public Pago capturarRetorno(String referencia, String orderId) {
        exigirHabilitado();
        Pago orden = pagoService.obtenerPorReferencia(referencia);
        if (orden.getProveedor() != ProveedorPago.PAYPAL || orderId == null || !orderId.equals(orden.getExternalId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pago no corresponde a esta orden.");
        }
        try {
            PayPalClient.OrdenPayPal paypal = client.obtenerOrden(orderId);
            if ("APPROVED".equals(paypal.status())) paypal = client.capturar(orderId);
            return aplicar(orden, paypal);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.UNPROCESSABLE_ENTITY) throw e;
            return pagoService.marcarRechazado(referencia, orderId, "capture rechazado por PayPal (HTTP 422)");
        }
    }

    public void procesarWebhook(String cuerpo, FirmaWebhook firma) {
        exigirHabilitado();
        if (!props.webhookConfigurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook no configurado.");
        }
        if (!client.verificarFirma(firma.transmissionId(), firma.transmissionTime(), firma.transmissionSig(),
                firma.certUrl(), firma.authAlgo(), props.webhookId(), cuerpo)) {
            log.warn("Webhook de PayPal con firma inválida");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma inválida.");
        }

        JsonNode evento;
        try {
            evento = JSON.readTree(cuerpo);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento inválido.");
        }
        if (!"PAYMENT.CAPTURE.COMPLETED".equals(evento.path("event_type").asString(""))) return;

        String orderId = evento.path("resource").path("supplementary_data").path("related_ids")
            .path("order_id").asString("");
        if (orderId.isBlank()) return;

        Optional<Pago> orden = pagoService.buscarPorExternalId(ProveedorPago.PAYPAL, orderId);
        if (orden.isEmpty()) {
            log.warn("Webhook de PayPal para una orden sin pedido conocido: {}", orderId);
            return;
        }
        aplicar(orden.get(), client.obtenerOrden(orderId));
    }

    private Pago aplicar(Pago orden, PayPalClient.OrdenPayPal paypal) {
        String customId = paypal.customId();
        if (customId != null && !customId.equals(orden.getReferencia())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pago no corresponde a esta orden.");
        }
        PayPalClient.Captura captura = paypal.primeraCaptura();
        String resumen = "order_id=" + paypal.id() + " status=" + paypal.status()
            + (captura == null ? "" : " capture_id=" + captura.id() + " capture_status=" + captura.status()
                + " amount=" + (captura.amount() == null ? null : captura.amount().value())
                + " currency=" + (captura.amount() == null ? null : captura.amount().currencyCode()));

        boolean completada = "COMPLETED".equals(paypal.status()) && captura != null
            && "COMPLETED".equals(captura.status()) && captura.amount() != null;
        if (completada) {
            return pagoService.confirmarAprobado(orden.getReferencia(), paypal.id(),
                captura.amount().value(), captura.amount().currencyCode(), resumen);
        }
        boolean fallida = "VOIDED".equals(paypal.status())
            || (captura != null && captura.status() != null && CAPTURA_FALLIDA.contains(captura.status()));
        if (fallida) {
            return pagoService.marcarRechazado(orden.getReferencia(), paypal.id(), resumen);
        }
        return orden;
    }

    private void exigirHabilitado() {
        if (!props.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los pagos con PayPal no están disponibles.");
        }
    }

    private static String conParametros(String base, String... pares) {
        return base + (base.contains("?") ? "&" : "?") + String.join("&", pares);
    }

    private static String titulo(TipoCodigo plan) {
        return "Corteo - Suscripción " + plan.meses + (plan.meses == 1 ? " mes" : " meses");
    }
}
