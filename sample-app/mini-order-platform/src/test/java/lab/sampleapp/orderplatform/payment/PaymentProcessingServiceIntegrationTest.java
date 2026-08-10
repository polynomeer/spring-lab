package lab.sampleapp.orderplatform.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lab.sampleapp.orderplatform.OrderPlatformConfig;
import lab.sampleapp.orderplatform.aop.AccessDeniedException;
import lab.sampleapp.orderplatform.aop.AuditLog;
import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.aop.TimingLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 AOP가 실제 @EnableAspectJAutoProxy 기반 컨텍스트 안에서, 컴포넌트 스캔으로 등록된
 * PaymentProcessingService 위에 여러 어드바이스가 실제로 함께 쌓여 동작하는지 검증한다.
 * 개별 어드바이스 자체의 경계 조건은 AopAspectUnitTest가 이미 격리해서 다뤘으므로, 여기서는
 * "여러 어드바이스가 겹쳤을 때"만 관심사로 삼는다.
 */
class PaymentProcessingServiceIntegrationTest {

    private AnnotationConfigApplicationContext context;

    private AnnotationConfigApplicationContext buildContext() {
        context = new AnnotationConfigApplicationContext();
        context.register(OrderPlatformConfig.class);
        context.refresh();
        return context;
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void repeatedProcessPaymentWithSameIdempotencyKeyDoesNotChargeTwice() {
        AnnotationConfigApplicationContext ctx = buildContext();
        PaymentProcessingService service = ctx.getBean(PaymentProcessingService.class);
        ctx.getBean(CurrentActor.class).set(new CurrentActor.Actor("cust-1", Role.CUSTOMER));

        // CardPaymentGateway의 트랜잭션 ID는 System.nanoTime()을 포함하므로, 두 번째 호출이
        // 실제로 게이트웨이를 다시 호출했다면 첫 호출과 다른 ID가 나올 수밖에 없다.
        PaymentResult first = service.processPayment("idem-key-1", PaymentMethod.CARD,
                new PaymentRequest("cust-1", 10_000));
        PaymentResult second = service.processPayment("idem-key-1", PaymentMethod.CARD,
                new PaymentRequest("cust-1", 10_000));

        assertThat(second.transactionId()).isEqualTo(first.transactionId());

        AuditLog auditLog = ctx.getBean(AuditLog.class);
        assertThat(auditLog.entries()).hasSize(2); // Audit은 Idempotency보다 바깥쪽이라 매 호출마다 기록됨
        assertThat(auditLog.entries()).allMatch(AuditLog.Entry::success);

        TimingLog timingLog = ctx.getBean(TimingLog.class);
        assertThat(timingLog.entries()).hasSize(2); // Timing은 가장 바깥쪽이라 캐시 히트여도 매번 측정됨
    }

    @Test
    void refundRequiresAdminRoleAndDeniedAttemptIsStillAudited() {
        AnnotationConfigApplicationContext ctx = buildContext();
        PaymentProcessingService service = ctx.getBean(PaymentProcessingService.class);
        CurrentActor currentActor = ctx.getBean(CurrentActor.class);
        AuditLog auditLog = ctx.getBean(AuditLog.class);

        currentActor.set(new CurrentActor.Actor("cust-1", Role.CUSTOMER));
        assertThatThrownBy(() -> service.refund(new PaymentRequest("cust-1", 5_000)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(auditLog.entries()).hasSize(1);
        assertThat(auditLog.entries().get(0).success()).isFalse();
        assertThat(auditLog.entries().get(0).action()).isEqualTo("refund");

        currentActor.set(new CurrentActor.Actor("admin-1", Role.ADMIN));
        PaymentResult result = service.refund(new PaymentRequest("cust-1", 5_000));

        assertThat(result.success()).isTrue();
        assertThat(auditLog.entries()).hasSize(2);
        assertThat(auditLog.entries().get(1).success()).isTrue();
    }

    @Test
    void pingProviderRecoversFromTransientFailuresViaRetry() {
        AnnotationConfigApplicationContext ctx = buildContext();
        PaymentProcessingService service = ctx.getBean(PaymentProcessingService.class);
        PaymentGatewayClient client = ctx.getBean(PaymentGatewayClient.class);

        client.failNextPings(2); // maxAttempts=3이므로 정확히 3번째 시도에서 성공해야 함

        service.pingProvider(); // 예외 없이 통과해야 retry가 실제로 동작한 것

        TimingLog timingLog = ctx.getBean(TimingLog.class);
        assertThat(timingLog.entries()).hasSize(1); // Timing은 재시도 전체를 감싸므로 호출당 1건만 기록
    }

    @Test
    void pingProviderGivesUpWhenFailuresExceedMaxAttempts() {
        AnnotationConfigApplicationContext ctx = buildContext();
        PaymentProcessingService service = ctx.getBean(PaymentProcessingService.class);
        PaymentGatewayClient client = ctx.getBean(PaymentGatewayClient.class);

        client.failNextPings(3); // maxAttempts=3번을 모두 소진하고도 실패

        assertThatThrownBy(service::pingProvider)
                .isInstanceOf(lab.sampleapp.orderplatform.aop.TransientOperationException.class);
    }
}
