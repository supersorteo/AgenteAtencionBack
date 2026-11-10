package com.agente.atencion.controller;

import com.agente.atencion.entity.EstadoPago;
import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.service.PagoService;
import com.agente.atencion.service.pago.MercadoPagoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    record CrearOrdenRequest(@NotNull TipoCodigo plan, @Size(max = 100) String tenantId) {}

    record CheckoutResponse(String referencia, String checkoutUrl) {}

    record OrdenResponse(String referencia, EstadoPago estado, TipoCodigo plan, String moneda,
                         BigDecimal monto, String codigo, String tenantId) {}

    private final MercadoPagoService mercadoPagoService;
    private final PagoService pagoService;

    public PagoController(MercadoPagoService mercadoPagoService, PagoService pagoService) {
        this.mercadoPagoService = mercadoPagoService;
        this.pagoService = pagoService;
    }

    @PostMapping("/mercadopago/orden")
    public CheckoutResponse crearOrdenMercadoPago(@Valid @RequestBody CrearOrdenRequest request) {
        var checkout = mercadoPagoService.iniciarCheckout(request.plan(), blankANull(request.tenantId()));
        return new CheckoutResponse(checkout.referencia(), checkout.checkoutUrl());
    }

    @PostMapping("/mercadopago/orden/{referencia}/verificar")
    public OrdenResponse verificarRetornoMercadoPago(@PathVariable String referencia,
                                                      @RequestParam String paymentId) {
        return aRespuesta(mercadoPagoService.verificarRetorno(referencia, paymentId));
    }

    @GetMapping("/orden/{referencia}")
    public OrdenResponse estadoOrden(@PathVariable String referencia) {
        return aRespuesta(pagoService.obtenerPorReferencia(referencia));
    }

    @PostMapping("/webhook/mercadopago")
    public ResponseEntity<Void> webhookMercadoPago(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId) {
        mercadoPagoService.procesarWebhook(type, dataId, xSignature, xRequestId);
        return ResponseEntity.ok().build();
    }

    static OrdenResponse aRespuesta(Pago pago) {
        boolean exponerCodigo = pago.getEstado() == EstadoPago.APROBADO && pago.getTenantId() == null;
        return new OrdenResponse(pago.getReferencia(), pago.getEstado(), pago.getTipoPlan(),
            pago.getMoneda(), pago.getMonto(), exponerCodigo ? pago.getCodigoGenerado() : null, pago.getTenantId());
    }

    static String blankANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
