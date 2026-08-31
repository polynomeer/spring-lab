package lab.dashboard.session;

import lab.dashboard.interpret.ScenarioInterpreter;

import java.util.function.Supplier;

/**
 * 시나리오 하나를 실행하는 데 필요한 모든 것 - 이미 classpath까지 해석된 상태로
 * {@link ScenarioSession#start}에 넘겨준다. {@link ScenarioCatalog}가 시나리오 이름을 이
 * 정의로 바꾸는 조회 표를 갖고 있고, classpath 해석 자체는 {@link ClasspathResolver}가
 * 담당한다 - 이 레코드 자신은 "이미 다 준비된 실행 계획"만 표현한다.
 *
 * <p>{@code dynamicallyCompiled}는 이 실행이 방금 컴파일된, 한 번도 실행해 본 적 없는
 * 사용자 코드인지를 나타낸다(docs/plan/04-dynamic-scenario-design.md 5번 절) -
 * {@link ScenarioSession}이 실행 타임아웃을 걸지 말지 이 값으로 결정한다. 기존 6개
 * 카탈로그 시나리오(dispatcher-flow처럼 사용자 상호작용을 무기한 기다리는 것도 포함)는
 * 이미 검증된 코드라 타임아웃 대상이 아니다.
 */
public record ScenarioDefinition(
        String name,
        String targetClasspath,
        String mainClass,
        String breakpointSpec,
        Supplier<ScenarioInterpreter> interpreterFactory,
        boolean dynamicallyCompiled) {
}
