package lab.sampleapp.orderplatform.discount;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * PaymentGatewayRegistry(List -&gt; Map)와는 다른 축의 "복수 빈 선택" 방식을 보여준다:
 * 여러 후보가 있을 때 컬렉션으로 전부 받는 대신, 생성자 파라미터에 @Qualifier로 어떤 빈을
 * 쓸지 명시적으로 고정한다. 회원 등급별로 다른 정책을 쓰고 싶다면 이 서비스 자체를
 * 여러 개로 나누거나 등급->정책 Map으로 바꿔야 하는데, 그건 지금 단계에서는 다루지 않는다.
 */
@Component
public class PricingService {

    private final DiscountPolicy discountPolicy;

    public PricingService(@Qualifier("membership") DiscountPolicy discountPolicy) {
        this.discountPolicy = discountPolicy;
    }

    public long finalPriceWon(long priceWon) {
        return discountPolicy.apply(priceWon);
    }
}
