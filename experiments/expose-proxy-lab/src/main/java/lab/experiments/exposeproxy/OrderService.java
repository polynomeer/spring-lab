package lab.experiments.exposeproxy;

public interface OrderService {

    void placeOrder();

    void placeOrderViaAopContextSelfInvocation();
}
