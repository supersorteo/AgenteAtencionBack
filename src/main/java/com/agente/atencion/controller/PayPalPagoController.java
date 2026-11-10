package com.agente.atencion.controller;

import com.agente.atencion.service.pago.PayPalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pagos")
public class PayPalPagoController {

    private final PayPalService payPalService;

    public PayPalPagoController(PayPalService payPalService) {
        this.payPalService = payPalService;
    }

    @PostMapping("/paypal/orden")
    public PagoController.CheckoutResponse crearOrden(@Valid @RequestBody PagoController.CrearOrdenRequest request) {
        var checkout = payPalService.iniciarCheckout(request.plan(), PagoController.blankANull(request.tenantId()));
        return new PagoController.CheckoutResponse(checkout.referencia(), checkout.checkoutUrl());
    }

    @PostMapping("/paypal/orden/{referencia}/capturar")
    public PagoController.OrdenResponse capturar(@PathVariable String referencia, @RequestParam String orderId) {
        return PagoController.aRespuesta(payPalService.capturarRetorno(referencia, orderId));
    }

    @PostMapping("/webhook/paypal")
    public ResponseEntity<Void> webhook(
            @RequestBody String cuerpo,
            @RequestHeader(name = "paypal-transmission-id", required = false) String transmissionId,
            @RequestHeader(name = "paypal-transmission-time", required = false) String transmissionTime,
            @RequestHeader(name = "paypal-transmission-sig", required = false) String transmissionSig,
            @RequestHeader(name = "paypal-cert-url", required = false) String certUrl,
            @RequestHeader(name = "paypal-auth-algo", required = false) String authAlgo) {
        payPalService.procesarWebhook(cuerpo, new PayPalService.FirmaWebhook(
            transmissionId, transmissionTime, transmissionSig, certUrl, authAlgo));
        return ResponseEntity.ok().build();
    }
}
