package lab.experiments.tx;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Runnable driver for a tools/jdi-tracer session against the transaction
 * interceptor chain. See tools/jdi-tracer/specs/transaction-propagation-lab.txt
 * for the breakpoint spec and docs/14-transaction-propagation/transaction-propagation.md
 * for the session write-up.
 */
public class TransactionPropagationLab {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = TransactionPlaygroundLab.buildContext();
        OrderService orderService = context.getBean(OrderService.class);

        System.out.println("--- placeOrderInnerRequiresNew(false): REQUIRES_NEW suspends the outer tx, runs its own, then resumes ---");
        orderService.placeOrderInnerRequiresNew(false);

        System.out.println();
        System.out.println("--- placeOrderCatchingInnerRequiredFailure(true): swallowed at the call site, still surfaces at commit ---");
        try {
            orderService.placeOrderCatchingInnerRequiredFailure(true);
        } catch (Exception ex) {
            System.out.println("caught at the top: " + ex);
        }

        context.close();
    }
}
