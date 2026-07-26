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
}
