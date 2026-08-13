package lab.experiments.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class OrderRequest {

    @NotBlank
    private final String customerId;

    @Positive
    private final int quantity;

    // 기본 그룹(Default)에는 속하지 않는다 - ExpressGroup이 명시적으로 요청될 때만 검사된다.
    @NotBlank(groups = ExpressGroup.class)
    private final String courier;

    public OrderRequest(String customerId, int quantity, String courier) {
        this.customerId = customerId;
        this.quantity = quantity;
        this.courier = courier;
    }

    public String getCustomerId() {
        return customerId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getCourier() {
        return courier;
    }
}
