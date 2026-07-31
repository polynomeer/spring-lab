package lab.minispring.event;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import lab.minispring.transaction.JdbcMiniTransactionManager;
import lab.minispring.transaction.MiniTransactionSynchronization;

// 실제 SimpleApplicationEventMulticaster에 대응한다. 카탈로그의 발전 과제 다섯 가지를
// 모두 다룬다: 동기/비동기 실행, 리스너 순서(order), 예외 정책, 상위 타입 매칭, 커밋 후 실행.
public final class MiniEventMulticaster {

    // 실제 SimpleApplicationEventMulticaster의 errorHandler와 대응 - 기본값은 그대로 던지는
    // 것이다(errorHandler가 없으면 doInvokeListener의 예외가 그대로 전파되는 것과 동일하게
    // 검증했다, docs/14-transaction-propagation 참고 문서의 자매 주제인 이벤트 쪽 실측).
    public interface ErrorPolicy {
        void handle(RuntimeException ex);
    }

    private record Registration<E>(
            Class<E> eventType, MiniEventListener<E> listener, int order, boolean async, boolean afterCommit) {
    }

    private final List<Registration<?>> registrations = new CopyOnWriteArrayList<>();
    private final Executor asyncExecutor;
    private final JdbcMiniTransactionManager transactionManager;
    private ErrorPolicy errorPolicy = ex -> {
        throw ex;
    };

    public MiniEventMulticaster(Executor asyncExecutor, JdbcMiniTransactionManager transactionManager) {
        this.asyncExecutor = asyncExecutor;
        this.transactionManager = transactionManager;
    }

    public void setErrorPolicy(ErrorPolicy errorPolicy) {
        this.errorPolicy = errorPolicy;
    }

    public <E> void addListener(Class<E> eventType, MiniEventListener<E> listener, int order) {
        addListener(eventType, listener, order, false, false);
    }

    public <E> void addListener(
            Class<E> eventType, MiniEventListener<E> listener, int order, boolean async, boolean afterCommit) {
        registrations.add(new Registration<>(eventType, listener, order, async, afterCommit));
    }

    // 정확히 일치하는 타입만 찾는 Map<Class<?>, List<...>> 조회(카탈로그 원본 스켈레톤)
    // 대신, 등록된 타입이 실제 이벤트 타입의 상위 타입/인터페이스인지 전부 검사한다 - 실제
    // ApplicationListener가 제네릭 타입 계층을 따라 상위 이벤트에도 반응하는 것과 같다.
    public void publish(Object event) {
        registrations.stream()
                .filter(registration -> registration.eventType().isAssignableFrom(event.getClass()))
                .sorted(Comparator.comparingInt(Registration::order))
                .forEach(registration -> dispatch(registration, event));
    }

    @SuppressWarnings("unchecked")
    private void dispatch(Registration<?> registration, Object event) {
        Runnable invocation = () -> ((MiniEventListener<Object>) registration.listener()).onEvent(event);

        if (registration.afterCommit()) {
            // 실제 @TransactionalEventListener의 fallbackExecution=false 기본값과 동일하게,
            // 트랜잭션이 아예 없으면 조용히 버린다(검증된 사실 - TransactionalEventListener
            // 소스의 fallbackExecution() 기본값 주석 참고).
            if (transactionManager == null || !transactionManager.isTransactionActive()) {
                return;
            }
            transactionManager.registerSynchronization(new MiniTransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runProtected(invocation);
                }
            });
            return;
        }

        if (registration.async()) {
            asyncExecutor.execute(() -> runProtected(invocation));
        } else {
            runProtected(invocation);
        }
    }

    private void runProtected(Runnable invocation) {
        try {
            invocation.run();
        } catch (RuntimeException ex) {
            errorPolicy.handle(ex);
        }
    }
}
