package com.agente.atencion.config;

import com.agente.atencion.service.pago.MercadoPagoClient;
import com.agente.atencion.service.pago.MercadoPagoWebhookValidator;
import com.agente.atencion.service.pago.PayPalClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PagosContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PagosConfig.class)
        .withBean(MercadoPagoClient.class)
        .withBean(MercadoPagoWebhookValidator.class)
        .withPropertyValues(
            "mercadopago.enabled=true",
            "mercadopago.base-url=https://api.mercadopago.com",
            "mercadopago.access-token=TOKEN",
            "mercadopago.webhook-secret=SECRETO",
            "mercadopago.success-url=https://app.test/pago/resultado",
            "mercadopago.failure-url=https://app.test/pago/resultado",
            "mercadopago.pending-url=https://app.test/pago/resultado",
            "pagos.backend-url=https://api.test");

    @Test
    void elContextoArrancaYBindeaLasPropiedadesSinNecesitarUnRestClientBuilder() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(MercadoPagoClient.class);
            assertThat(ctx).doesNotHaveBean(RestClient.Builder.class);

            MercadoPagoProperties props = ctx.getBean(MercadoPagoProperties.class);
            assertThat(props.enabled()).isTrue();
            assertThat(props.baseUrl()).isEqualTo("https://api.mercadopago.com");
            assertThat(props.credencialesCargadas()).isTrue();
            assertThat(props.webhookConfigurado()).isTrue();
            assertThat(ctx.getBean(PagosProperties.class).backendUrl()).isEqualTo("https://api.test");
        });
    }

    @Test
    void elClienteDePayPalArrancaYBindeaSusPropiedades() {
        new ApplicationContextRunner()
            .withUserConfiguration(PagosConfig.class)
            .withBean(PayPalClient.class)
            .withPropertyValues(
                "paypal.enabled=true",
                "paypal.client-id=CID",
                "paypal.client-secret=SECRET",
                "paypal.webhook-id=WH-1",
                "paypal.base-url=https://api-m.sandbox.paypal.com",
                "paypal.success-url=https://app.test/pago/resultado",
                "paypal.cancel-url=https://app.test/pago/resultado")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(PayPalClient.class);
                assertThat(ctx).doesNotHaveBean(RestClient.Builder.class);
                PayPalProperties props = ctx.getBean(PayPalProperties.class);
                assertThat(props.credencialesCargadas()).isTrue();
                assertThat(props.webhookConfigurado()).isTrue();
                assertThat(props.baseUrl()).isEqualTo("https://api-m.sandbox.paypal.com");
            });
    }

    @Test
    void sinCredencialesDePayPalElContextoIgualArranca() {
        new ApplicationContextRunner()
            .withUserConfiguration(PagosConfig.class)
            .withBean(PayPalClient.class)
            .withPropertyValues(
                "paypal.enabled=true",
                "paypal.client-id=",
                "paypal.client-secret=",
                "paypal.webhook-id=",
                "paypal.base-url=https://api-m.sandbox.paypal.com")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                PayPalProperties props = ctx.getBean(PayPalProperties.class);
                assertThat(props.credencialesCargadas()).isFalse();
                assertThat(props.webhookConfigurado()).isFalse();
            });
    }

    @Test
    void sinCredencialesElContextoIgualArranca() {
        new ApplicationContextRunner()
            .withUserConfiguration(PagosConfig.class)
            .withBean(MercadoPagoClient.class)
            .withPropertyValues(
                "mercadopago.enabled=true",
                "mercadopago.base-url=https://api.mercadopago.com",
                "mercadopago.access-token=",
                "mercadopago.webhook-secret=",
                "pagos.backend-url=")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                MercadoPagoProperties props = ctx.getBean(MercadoPagoProperties.class);
                assertThat(props.credencialesCargadas()).isFalse();
                assertThat(props.webhookConfigurado()).isFalse();
            });
    }
}
