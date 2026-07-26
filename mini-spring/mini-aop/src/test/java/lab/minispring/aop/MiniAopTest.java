package lab.minispring.aop;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniAopTest {

    @Test
    void singleInterceptorWrapsTheTargetCall() {
        CallCountInterceptor counter = new CallCountInterceptor();
        MiniProxyFactory factory = new MiniProxyFactory(new CalculatorImpl());
        factory.addInterceptor(counter);

        Calculator proxy = factory.getProxy();

        assertThat(proxy.add(2, 3)).isEqualTo(5);
        assertThat(counter.getCount("add")).isEqualTo(1);
        assertThat(counter.getCount("divide")).isEqualTo(0);
    }

    @Test
    void interceptorsRunInOnionOrder() {
        // 카탈로그(프로젝트 19)가 제시한 순서 그대로: Logging -> Authorization -> Timing ->
        // Target -> Timing -> Authorization -> Logging. addInterceptor()를 호출한 순서가
        // 바깥쪽부터 감싸는 순서가 된다 - ReflectiveMethodInvocation이 리스트 인덱스 0번부터
        // 차례로 타기 때문이다.
        List<String> trace = new ArrayList<>();
        MiniProxyFactory factory = new MiniProxyFactory(new CalculatorImpl());
        factory.addInterceptor(new LoggingInterceptor(trace));
        factory.addInterceptor(new AuthorizationInterceptor(() -> true, trace));
        factory.addInterceptor(new TimingInterceptor(trace));

        Calculator proxy = factory.getProxy();
        int result = proxy.add(1, 1);

        assertThat(result).isEqualTo(2);
        assertThat(trace).containsExactly(
                "LoggingInterceptor before",
                "AuthorizationInterceptor before",
                "TimingInterceptor before",
                "TimingInterceptor after",
                "AuthorizationInterceptor after",
                "LoggingInterceptor after");
    }

    @Test
    void authorizationFailureShortCircuitsBeforeReachingTheTarget() {
        List<String> trace = new ArrayList<>();
        CallCountInterceptor counter = new CallCountInterceptor();
        MiniProxyFactory factory = new MiniProxyFactory(new CalculatorImpl());
        factory.addInterceptor(new AuthorizationInterceptor(() -> false, trace));
        factory.addInterceptor(counter);

        Calculator proxy = factory.getProxy();

        assertThatThrownBy(() -> proxy.add(1, 1)).isInstanceOf(SecurityException.class);
        // "after"는 기록되지 않는다 - 인증 실패가 proceed() 자체를 막기 때문이다.
        assertThat(trace).containsExactly("AuthorizationInterceptor before");
        assertThat(counter.getCount("add")).isEqualTo(0);
    }

    @Test
    void exceptionTranslationInterceptorConvertsMatchingExceptions() {
        MiniProxyFactory factory = new MiniProxyFactory(new CalculatorImpl());
        factory.addInterceptor(new ExceptionTranslationInterceptor(
                ArithmeticException.class,
                cause -> new IllegalStateException("calculation failed", cause)));

        Calculator proxy = factory.getProxy();

        assertThatThrownBy(() -> proxy.divide(1, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("calculation failed")
                .hasCauseInstanceOf(ArithmeticException.class);
    }

    @Test
    void retryInterceptorRetriesUntilSuccess() {
        FlakyService target = new FlakyService(3);
        MiniProxyFactory factory = new MiniProxyFactory(target);
        factory.addInterceptor(new RetryInterceptor(5, IllegalStateException.class));

        Flaky proxy = factory.getProxy();

        assertThat(proxy.call()).isEqualTo("success after 3 attempts");
        assertThat(target.getAttempts()).isEqualTo(3);
    }

    @Test
    void retryInterceptorExhaustsAttemptsAndRethrowsTheLastFailure() {
        FlakyService target = new FlakyService(10);
        MiniProxyFactory factory = new MiniProxyFactory(target);
        factory.addInterceptor(new RetryInterceptor(3, IllegalStateException.class));

        Flaky proxy = factory.getProxy();

        assertThatThrownBy(proxy::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("not yet, attempt 3");
        assertThat(target.getAttempts()).isEqualTo(3);
    }

    @Test
    void timingInterceptorRecordsOneDurationPerCall() {
        TimingInterceptor timing = new TimingInterceptor(new ArrayList<>());
        MiniProxyFactory factory = new MiniProxyFactory(new CalculatorImpl());
        factory.addInterceptor(timing);
        Calculator proxy = factory.getProxy();

        proxy.add(1, 2);
        proxy.add(3, 4);

        assertThat(timing.getDurationsNanos()).hasSize(2);
        assertThat(timing.getDurationsNanos()).allMatch(duration -> duration >= 0);
    }
}
