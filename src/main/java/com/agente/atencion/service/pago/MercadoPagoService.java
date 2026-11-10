package com.agente.atencion.service.pago;

import com.agente.atencion.config.MercadoPagoProperties;
import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.ProveedorPago;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.service.PagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

    public record CheckoutCreado(String referencia, String checkoutUrl) {}

    private final MercadoPagoProperties props;
    private final MercadoPagoClient client;
    private final MercadoPagoWebhookValidator validator;
    private final PagoService pagoService;

    public MercadoPagoService(MercadoPagoProperties props, MercadoPagoClient client,
                              MercadoPagoWebhookValidator validator, PagoService pagoService) {
        this.props = props;
        this.client = client;
        this.validator = validator;
        this.pagoService = pagoService;
    }

    public CheckoutCreado iniciarCheckout(TipoCodigo plan, String tenantId) {
        exigirHabilitado();
        if (!props.credencialesCargadas()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los pagos con Mercado Pago no están disponibles.");
        }
        Pago pago = pagoService.crearOrden(ProveedorPago.MERCADOPAGO, plan, tenantId);
        String url = client.crearPreferencia(pago, titulo(plan));
        return new CheckoutCreado(pago.getReferencia(), url);
    }

    public void procesarWebhook(String type, String dataId, String xSignature, String xRequestId) {
        exigirHabilitado();
        if (!props.webhookConfigurado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook no configurado.");
        }
        if (!validator.esValida(xSignature, xRequestId, dataId, props.webhookSecret())) {
            log.warn("Webhook de Mercado Pago con firma inválida");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma inválida.");
        }
        if (!"payment".equals(type)) return;
        if (dataId == null || dataId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta data.id.");
        }
        try {
            aplicar(client.obtenerPago(dataId));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) throw e;
            log.warn("Webhook de Mercado Pago para un pago sin orden conocida: {}", dataId);
        }
    }

    public Pago verificarRetorno(String referencia, String paymentId) {
        exigirHabilitado();
        MercadoPagoClient.PagoMp pagoMp = client.obtenerPago(paymentId);
        if (!referencia.equals(pagoMp.externalReference())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pago no corresponde a esta orden.");
        }
        return aplicar(pagoMp);
    }

    private Pago aplicar(MercadoPagoClient.PagoMp pagoMp) {
        String referencia = pagoMp.externalReference();
        if (referencia == null || referencia.isBlank()) return null;

        Pago orden = pagoService.obtenerPorReferencia(referencia);
        if (orden.getProveedor() != ProveedorPago.MERCADOPAGO) return orden;

        String idPago = String.valueOf(pagoMp.id());
        String resumen = "payment_id=" + idPago + " status=" + pagoMp.status()
            + " amount=" + pagoMp.transactionAmount() + " currency=" + pagoMp.currencyId();
        String estado = pagoMp.status() == null ? "" : pagoMp.status();
        return switch (estado) {
            case "approved" -> pagoService.confirmarAprobado(
                referencia, idPago, pagoMp.transactionAmount(), pagoMp.currencyId(), resumen);
            case "rejected", "cancelled" -> pagoService.marcarRechazado(referencia, idPago, resumen);
            default -> orden;
        };
    }

    private void exigirHabilitado() {
        if (!props.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los pagos con Mercado Pago no están disponibles.");
        }
    }

    private static String titulo(TipoCodigo plan) {
        return "Corteo - Suscripción " + plan.meses + (plan.meses == 1 ? " mes" : " meses");
    }
}
