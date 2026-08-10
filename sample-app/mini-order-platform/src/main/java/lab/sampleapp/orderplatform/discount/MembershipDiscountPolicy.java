package lab.sampleapp.orderplatform.discount;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("membership")
public class MembershipDiscountPolicy implements DiscountPolicy {

    private static final long PERCENT_OFF = 10;

    @Override
    public long apply(long priceWon) {
        return priceWon - (priceWon * PERCENT_OFF / 100);
    }
}
