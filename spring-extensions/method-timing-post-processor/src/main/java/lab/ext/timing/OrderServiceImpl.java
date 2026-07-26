package lab.ext.timing;

import org.springframework.stereotype.Component;

@Component
public class OrderServiceImpl implements OrderService {

    @Override
    public void placeOrder() {
        // 측정 대상 작업.
    }

    @Override
    public String cachedLookup() {
        return "cached";
    }

    @Override
    public void checkout() {
        // this.placeOrder()는 프록시가 아니라 이 인스턴스로 직접 가므로, 외부에서
        // proxy.placeOrder()를 부르는 것과 달리 측정되지 않는다(11주차 self-invocation 참고).
        placeOrder();
    }
}
