package lab.sampleapp.orderplatform.payment;

import lab.sampleapp.orderplatform.plugin.SelfDescribingPlugin;

public interface PaymentGateway extends SelfDescribingPlugin {

    PaymentMethod method();

    PaymentResult charge(PaymentRequest request);

    @Override
    default String pluginKind() {
        return "payment-gateway";
    }

    @Override
    default String pluginKey() {
        return method().name();
    }
}
