package lab.sampleapp.orderplatform.payment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * List&lt;PaymentGateway&gt;를 주입받아 method()로 키를 만든 Map으로 재구성한다.
 * (plugin-discovery-system의 NotificationPluginRegistry와 같은 패턴 - Spring의
 * Map&lt;String,T&gt; 자동 주입은 빈 이름을 키로 쓰기 때문에, "우리가 정의한 키"로 찾으려면
 * 이렇게 List를 받아 직접 Map을 만들어야 한다.)
 */
@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentMethod, PaymentGateway> gatewaysByMethod;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        Map<PaymentMethod, PaymentGateway> byMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentGateway gateway : gateways) {
            PaymentGateway previous = byMethod.putIfAbsent(gateway.method(), gateway);
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate PaymentGateway for method " + gateway.method());
            }
        }
        this.gatewaysByMethod = Map.copyOf(byMethod);
    }

    public PaymentResult charge(PaymentMethod method, PaymentRequest request) {
        PaymentGateway gateway = gatewaysByMethod.get(method);
        if (gateway == null) {
            throw new IllegalArgumentException("no PaymentGateway registered for " + method);
        }
        return gateway.charge(request);
    }
}
