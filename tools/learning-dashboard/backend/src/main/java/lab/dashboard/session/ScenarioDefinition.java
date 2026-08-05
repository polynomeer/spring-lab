package lab.dashboard.session;

import lab.dashboard.interpret.ScenarioInterpreter;

import java.util.function.Supplier;

/**
 * 시나리오 하나를 실행하는 데 필요한 모든 것 - 이미 classpath까지 해석된 상태로
 * {@link ScenarioSession#start}에 넘겨준다. {@link ScenarioCatalog}가 시나리오 이름을 이
 * 정의로 바꾸는 조회 표를 갖고 있고, classpath 해석 자체는 {@link ClasspathResolver}가
 * 담당한다 - 이 레코드 자신은 "이미 다 준비된 실행 계획"만 표현한다.
 */
public record ScenarioDefinition(
        String name,
        String targetClasspath,
        String mainClass,
        String breakpointSpec,
        Supplier<ScenarioInterpreter> interpreterFactory) {
}
