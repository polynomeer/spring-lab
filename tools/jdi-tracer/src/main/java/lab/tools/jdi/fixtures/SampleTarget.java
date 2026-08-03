package lab.tools.jdi.fixtures;

// TracerServerTest 전용 최소 대상 프로그램 - 실제 Spring 컨텍스트 없이, 지역 변수가 있는
// 메서드를 정확히 두 번 호출한다는 결정론적 시나리오만 재현한다. 무거운 experiments 모듈에
// 의존하지 않도록, tools/jdi-tracer 자기 자신의 컴파일 결과물 안에 둔다.
public final class SampleTarget {

    public static void main(String[] args) {
        greet("Alice");
        greet("Bob");
    }

    static void greet(String name) {
        String message = "Hello, " + name;
        System.out.println(message);
    }
}
