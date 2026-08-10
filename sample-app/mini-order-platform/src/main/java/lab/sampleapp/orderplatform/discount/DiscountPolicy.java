package lab.sampleapp.orderplatform.discount;

public interface DiscountPolicy {

    long apply(long priceWon);
}
