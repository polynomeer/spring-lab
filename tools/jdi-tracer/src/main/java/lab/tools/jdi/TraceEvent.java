package lab.tools.jdi;

import java.util.List;

/**
 * 브레이크포인트 히트 하나를 순수 데이터로 표현한 것 - {@code Tracer#printHit()}이 콘솔에
 * 찍던 것과 같은 정보(위치, 스택, 지역 변수)를, 대시보드 설계 문서
 * (docs/plan/03-learning-dashboard-design.md 7번 절)의 "원본 이벤트" 모양 그대로 담는다.
 */
public record TraceEvent(
        int hitId,
        long timestampNanos,
        String thread,
        Location location,
        List<Frame> stack,
        List<Local> locals,
        boolean localsAvailable) {

    public record Location(String className, String methodName, int line) {
    }

    public record Frame(String className, String methodName) {
    }

    public record Local(String name, String type, String value) {
    }
}
