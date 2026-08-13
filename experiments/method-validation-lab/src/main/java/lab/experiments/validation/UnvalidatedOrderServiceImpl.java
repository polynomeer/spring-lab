package lab.experiments.validation;

public class UnvalidatedOrderServiceImpl implements UnvalidatedOrderService {

    private final InvocationCounter counter;

    public UnvalidatedOrderServiceImpl(InvocationCounter counter) {
        this.counter = counter;
    }

    @Override
    public void placeOrder(String customerId, int quantity) {
        counter.increment("unvalidated.placeOrder");
    }
}
