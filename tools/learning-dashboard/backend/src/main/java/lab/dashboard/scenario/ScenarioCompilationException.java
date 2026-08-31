package lab.dashboard.scenario;

import java.util.List;

/**
 * 실행 시점(2단계 - 즉석 코드 작성 시나리오)에 컴파일이 실패했을 때 던진다 - 새 예외
 * 타입을 만든 이유는 별도 처리를 위해서가 아니라, 이미 있는
 * {@code ScenarioWebSocketController}의 범용 {@code @MessageExceptionHandler}가 이걸
 * 그대로 잡아 {@code {"type":"error"}} STOMP 프레임으로 중계하기 때문이다 - 새 기능을
 * 위한 새 배선을 만들지 않고, 이미 있는 일관된 에러 경로를 그대로 재사용한다.
 */
public class ScenarioCompilationException extends RuntimeException {

    public ScenarioCompilationException(String scenarioName, List<String> diagnostics) {
        super("시나리오 '" + scenarioName + "' 컴파일 실패:\n" + String.join("\n", diagnostics));
    }
}
