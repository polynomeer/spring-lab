package lab.experiments.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

public interface OrderService {

    void placeOrder(@NotBlank String customerId, @Min(1) int quantity);

    void placeValidatedOrder(@Valid OrderRequest request);

    // 메서드 레벨 @Validated는 클래스 레벨 기본값(Default 그룹)을 이 메서드에 한해서만
    // ExpressGroup으로 덮어쓴다 - MethodValidationAdapter#determineValidationGroups가
    // 메서드를 먼저 찾아보고, 없을 때만 클래스로 폴백한다.
    @Validated(ExpressGroup.class)
    void placeExpressOrder(@Valid OrderRequest request);

    @NotNull
    String findCustomerName(@NotBlank String customerId);

    void refreshViaSelfInvocation(String customerId, int quantity);
}
