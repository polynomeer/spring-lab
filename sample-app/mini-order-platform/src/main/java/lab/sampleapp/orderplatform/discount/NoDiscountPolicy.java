package lab.sampleapp.orderplatform.discount;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("none")
public class NoDiscountPolicy implements DiscountPolicy {

    @Override
    public long apply(long priceWon) {
        return priceWon;
    }
}
