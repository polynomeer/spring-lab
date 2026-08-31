package lab.tools.jdi.fixtures;

// docs/plan/04-dynamic-scenario-design.md 5번 절(실행 타임아웃) 검증 전용 - 절대 스스로
// 끝나지 않는다(즉석 코드 작성 시나리오의 무한루프 실수를 흉내낸다). tick()에 브레이크포인트를
// 걸면 루프가 도는 동안 반복해서 히트가 발생하는 걸 관찰할 수 있다.
public final class InfiniteLoopTarget {

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            tick();
            Thread.sleep(20);
        }
    }

    private static void tick() {
    }
}
