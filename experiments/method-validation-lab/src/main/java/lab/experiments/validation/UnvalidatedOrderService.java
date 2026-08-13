package lab.experiments.validation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

// OrderService와 완전히 같은 모양의 제약 애노테이션을 갖고 있지만, 구현 클래스에
// @Validated가 없다 - 대조군.
public interface UnvalidatedOrderService {

    void placeOrder(@NotBlank String customerId, @Min(1) int quantity);
}
