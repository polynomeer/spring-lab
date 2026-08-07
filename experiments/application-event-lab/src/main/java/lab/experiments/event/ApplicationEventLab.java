package lab.experiments.event;

import java.time.Duration;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 대시보드의 event-multicast 시나리오 라이브 진입점 - docs/21-application-events.md 8번 절
 * "런타임 관찰"에 적어 둔 7가지 실험 중, JDI 브레이크포인트로 관찰 가능한 것들을 한 프로세스
 * 안에서 순서대로 재현한다.
 */
public final class ApplicationEventLab {

    private ApplicationEventLab() {
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EventLabConfig.class);
        EventLog eventLog = context.getBean(EventLog.class);
        OrderService orderService = context.getBean(OrderService.class);

        // 1) 홀수 id, 트랜잭션 없음 - 동기 순서 리스너(10,20) + condition 리스너는 건너뜀(1%2!=0)
        //    + async 리스너 + @TransactionalEventListener는 트랜잭션이 없어 조용히 버려진다.
        orderService.completeOrder(1);
        eventLog.awaitAtLeast(3, Duration.ofSeconds(2)); // sync-first, sync-second, async

        // 2) 짝수 id - condition 리스너가 실제로 실행되는 걸 보여준다.
        orderService.completeOrder(2);
        eventLog.awaitAtLeast(7, Duration.ofSeconds(2));

        // 3) 트랜잭션 커밋 - after-commit 리스너가 커밋 이후 실행된다.
        orderService.completeOrderInTransaction(3, false);

        // 4) 트랜잭션 롤백 - after-commit 리스너는 절대 실행되지 않는다.
        try {
            orderService.completeOrderInTransaction(4, true);
        } catch (IllegalStateException expected) {
            // 의도된 롤백 유발 예외 - AFTER_COMMIT이 실행되지 않는다는 걸 보여주기 위한 것.
        }

        // 5) 음수 id - FailureProneListener(order=1)가 예외를 던져 이후 순서의 리스너가
        //    전혀 호출되지 않는다(errorHandler가 없으면 multicastEvent의 for 루프가 그 자리에서
        //    멈춘다는 걸 실제로 관찰하기 위한 케이스).
        try {
            orderService.completeOrder(-1);
        } catch (IllegalStateException expected) {
            System.out.println("negative-order-id publish threw as expected, halting listener loop");
        }

        context.close();
    }
}
