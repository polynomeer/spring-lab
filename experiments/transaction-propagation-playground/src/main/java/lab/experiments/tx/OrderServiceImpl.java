package lab.experiments.tx;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

    private final LedgerRepository ledgerRepository;
    private final PaymentService paymentService;
    private final DataSource dataSource;

    public OrderServiceImpl(LedgerRepository ledgerRepository, PaymentService paymentService, DataSource dataSource) {
        this.ledgerRepository = ledgerRepository;
        this.paymentService = paymentService;
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void placeOrderInnerRequired(boolean innerFails) {
        placeOrder(() -> paymentService.payRequired(innerFails));
    }

    @Override
    @Transactional
    public void placeOrderInnerRequiresNew(boolean innerFails) {
        placeOrder(() -> paymentService.payRequiresNew(innerFails));
    }

    @Override
    @Transactional
    public void placeOrderInnerNested(boolean innerFails) {
        placeOrder(() -> paymentService.payNested(innerFails));
    }

    @Override
    @Transactional
    public void placeOrderInnerNotSupported(boolean innerFails) {
        placeOrder(() -> paymentService.payNotSupported(innerFails));
    }

    @Override
    @Transactional
    public void placeOrderCatchingInnerRequiredFailure(boolean innerFails) {
        placeOrderCatchingInnerFailure(() -> paymentService.payRequired(innerFails));
    }

    @Override
    @Transactional
    public void placeOrderCatchingInnerRequiresNewFailure(boolean innerFails) {
        placeOrderCatchingInnerFailure(() -> paymentService.payRequiresNew(innerFails));
    }

    @Override
    @Transactional
    public void placeOrderCatchingInnerNestedFailure(boolean innerFails) {
        placeOrderCatchingInnerFailure(() -> paymentService.payNested(innerFails));
    }

    @Override
    @Transactional
    public void placeOrderRequiresNewSucceedsThenOuterFails() {
        ledgerRepository.record("order");
        // REQUIRES_NEW는 자신만의 독립된 트랜잭션이라 여기서 이미 커밋된다 - 아래에서 outer가
        // 실패해도 이 결제는 되돌릴 수 없다(suspend/resume, 6번 절 참고).
        paymentService.payRequiresNew(false);
        throw new IllegalStateException("outer fails after payment already committed independently");
    }

    private void placeOrder(Runnable pay) {
        ledgerRepository.record("order");
        PropagationLog.capture("order", dataSource);
        pay.run();
        ledgerRepository.record("audit");
        PropagationLog.capture("audit", dataSource);
    }

    private void placeOrderCatchingInnerFailure(Runnable pay) {
        ledgerRepository.record("order");
        PropagationLog.capture("order", dataSource);
        try {
            pay.run();
        } catch (PaymentFailedException ignored) {
            // 삼킨다 - REQUIRED로 참여한 결제 실패가 이미 전체 트랜잭션을 rollback-only로
            // 표시했다면, 여기서 삼켜도 최종 commit() 시점에 UnexpectedRollbackException이
            // 대신 던져진다(13주차 문서, 11번 절 참고).
        }
        ledgerRepository.record("audit");
        PropagationLog.capture("audit", dataSource);
    }
}
