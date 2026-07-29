package lab.experiments.tx;

public interface PaymentService {

    void payRequired(boolean fail);

    void payRequiresNew(boolean fail);

    void payNested(boolean fail);

    void payNotSupported(boolean fail);
}
