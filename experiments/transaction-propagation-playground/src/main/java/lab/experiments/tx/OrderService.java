package lab.experiments.tx;

public interface OrderService {

    void placeOrderInnerRequired(boolean innerFails);

    void placeOrderInnerRequiresNew(boolean innerFails);

    void placeOrderInnerNested(boolean innerFails);

    void placeOrderInnerNotSupported(boolean innerFails);

    void placeOrderCatchingInnerRequiredFailure(boolean innerFails);

    void placeOrderCatchingInnerRequiresNewFailure(boolean innerFails);

    void placeOrderCatchingInnerNestedFailure(boolean innerFails);

    void placeOrderRequiresNewSucceedsThenOuterFails();
}
