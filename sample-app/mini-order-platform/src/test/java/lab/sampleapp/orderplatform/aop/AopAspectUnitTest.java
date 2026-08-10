package lab.sampleapp.orderplatform.aop;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 각 어드바이스를 실제 애플리케이션 컨텍스트 없이, {@link AspectJProxyFactory}로 대상 하나에만
 * 어드바이스를 걸어 격리 검증한다 - 실제 Spring AOP 프록시 메커니즘(모의 객체가 아니라)을 그대로
 * 쓰되, 나머지 어드바이스가 끼어들지 않는 최소 구성으로 각자의 경계 조건까지 확인하려는 의도다.
 * 여러 어드바이스가 실제로 같이 쌓였을 때의 동작은 PaymentProcessingServiceIntegrationTest가 맡는다.
 */
class AopAspectUnitTest {

    static class TimedTarget {
        @Timed
        String hello() {
            return "hi";
        }
    }

    @Test
    void timingAspectRecordsElapsedTimeAroundTheCall() {
        TimingLog log = new TimingLog();
        AspectJProxyFactory factory = new AspectJProxyFactory(new TimedTarget());
        factory.addAspect(new TimingAspect(log));
        TimedTarget proxy = factory.getProxy();

        String result = proxy.hello();

        assertThat(result).isEqualTo("hi");
        assertThat(log.entries()).hasSize(1);
        assertThat(log.entries().get(0).elapsedNanos()).isGreaterThanOrEqualTo(0);
    }

    static class RoleGuardedTarget {
        @RequiresRole(Role.ADMIN)
        String adminOnly() {
            return "admin-secret";
        }
    }

    @Test
    void authorizationAspectAllowsMatchingRoleAndDeniesOthers() {
        CurrentActor currentActor = new CurrentActor();
        AspectJProxyFactory factory = new AspectJProxyFactory(new RoleGuardedTarget());
        factory.addAspect(new AuthorizationAspect(currentActor));
        RoleGuardedTarget proxy = factory.getProxy();

        currentActor.set(new CurrentActor.Actor("admin-1", Role.ADMIN));
        assertThat(proxy.adminOnly()).isEqualTo("admin-secret");

        currentActor.set(new CurrentActor.Actor("cust-1", Role.CUSTOMER));
        assertThatThrownBy(proxy::adminOnly).isInstanceOf(AccessDeniedException.class);
    }

    static class AuditedTarget {
        @Audited(action = "do-thing")
        String succeed() {
            return "done";
        }

        @Audited(action = "do-thing")
        String fail() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void auditAspectRecordsBothSuccessAndFailureExactlyOnce() {
        AuditLog log = new AuditLog();
        CurrentActor currentActor = new CurrentActor();
        currentActor.set(new CurrentActor.Actor("actor-1", Role.CUSTOMER));
        AspectJProxyFactory factory = new AspectJProxyFactory(new AuditedTarget());
        factory.addAspect(new AuditAspect(log, currentActor));
        AuditedTarget proxy = factory.getProxy();

        proxy.succeed();
        assertThatThrownBy(proxy::fail).isInstanceOf(IllegalStateException.class);

        assertThat(log.entries()).hasSize(2);
        assertThat(log.entries().get(0).success()).isTrue();
        assertThat(log.entries().get(1).success()).isFalse();
        assertThat(log.entries().get(1).detail()).isEqualTo("IllegalStateException");
    }

    static class IdempotentTarget {
        final AtomicInteger calls = new AtomicInteger();

        @IdempotencyGuarded
        String process(String key) {
            calls.incrementAndGet();
            return "result-for-" + key + "-" + calls.get();
        }
    }

    @Test
    void idempotencyAspectSkipsReExecutionForARepeatedKey() {
        IdempotencyStore store = new IdempotencyStore();
        IdempotentTarget target = new IdempotentTarget();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new IdempotencyAspect(store));
        IdempotentTarget proxy = factory.getProxy();

        String first = proxy.process("key-1");
        String second = proxy.process("key-1");
        String differentKey = proxy.process("key-2");

        assertThat(second).isEqualTo(first);
        assertThat(differentKey).isNotEqualTo(first);
        assertThat(target.calls.get()).isEqualTo(2); // key-1 실제 실행 1회 + key-2 실제 실행 1회
    }

    static class RetryTarget {
        int callCount;
        final int failUntilAttempt;

        RetryTarget(int failUntilAttempt) {
            this.failUntilAttempt = failUntilAttempt;
        }

        @Retryable(maxAttempts = 3)
        String flaky() {
            callCount++;
            if (callCount < failUntilAttempt) {
                throw new TransientOperationException("still failing, attempt " + callCount);
            }
            return "succeeded-on-attempt-" + callCount;
        }

        @Retryable(maxAttempts = 3, retryFor = IllegalStateException.class)
        String failsWithNonRetryableException() {
            callCount++;
            throw new TransientOperationException("not the type this method retries for");
        }
    }

    @Test
    void retryAspectRetriesUntilSuccessWithinMaxAttempts() {
        RetryTarget target = new RetryTarget(3);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RetryAspect());
        RetryTarget proxy = factory.getProxy();

        String result = proxy.flaky();

        assertThat(result).isEqualTo("succeeded-on-attempt-3");
        assertThat(target.callCount).isEqualTo(3);
    }

    @Test
    void retryAspectGivesUpAfterMaxAttemptsAndRethrowsTheLastFailure() {
        RetryTarget target = new RetryTarget(10); // 절대 성공하지 못함
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RetryAspect());
        RetryTarget proxy = factory.getProxy();

        assertThatThrownBy(proxy::flaky).isInstanceOf(TransientOperationException.class);
        assertThat(target.callCount).isEqualTo(3); // maxAttempts만큼만 시도
    }

    @Test
    void retryAspectDoesNotRetryAnExceptionTypeItWasNotConfiguredFor() {
        RetryTarget target = new RetryTarget(10);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RetryAspect());
        RetryTarget proxy = factory.getProxy();

        assertThatThrownBy(proxy::failsWithNonRetryableException)
                .isInstanceOf(TransientOperationException.class);
        assertThat(target.callCount).isEqualTo(1); // retryFor()가 IllegalStateException이라 재시도 없이 즉시 전파
    }
}
