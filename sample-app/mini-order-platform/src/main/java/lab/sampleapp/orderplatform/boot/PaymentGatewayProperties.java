package lab.sampleapp.orderplatform.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("order-platform.payment")
public class PaymentGatewayProperties {

    /** false면 PaymentGatewayAutoConfiguration이 PaymentGatewayClient를 아예 등록하지 않는다. */
    private boolean enabled = true;

    private int connectTimeoutSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }
}
