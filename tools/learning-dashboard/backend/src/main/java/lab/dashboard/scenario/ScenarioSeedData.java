package lab.dashboard.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 예전 {@code ScenarioCatalog}(자바 코드) 안에 하드코딩돼 있던 여섯 개 시나리오 정의를
 * 그대로 옮겨 온 것 - {@link ScenarioSeeder}(실제 앱 기동 시 DB에 채워 넣음)와
 * {@code ScenarioCatalogTest}(리포지토리 슬라이스 테스트)가 이 하나의 목록을 공유해서,
 * "여섯 개 시나리오가 무엇인가"에 대한 정의가 두 곳에서 어긋나지 않게 한다.
 *
 * <p>브레이크포인트 스펙은 여전히 {@code tools/jdi-tracer/specs/*.txt} 파일에 있다 - 다른
 * 곳(문서, jdi-tracer 자체 사용법)에서도 참조되므로 파일 자체는 남겨 두고, 시드 시점에
 * 그 내용을 읽어 DB 컬럼으로 복사해 넣는다.
 */
public final class ScenarioSeedData {

    private ScenarioSeedData() {
    }

    public static List<ScenarioDefinitionEntity> defaults(Path repoRoot) {
        return List.of(
                entry(repoRoot, "bean-lifecycle", "빈 생명주기 + 순환 참조",
                        "3단계 캐시가 조기 참조를 노출하는 시점과, AOP 프록시 대상 빈의 순환 참조가 실제로 풀리는 과정.",
                        "experiments:circular-dependency-lab",
                        "lab.experiments.circular.CircularDependencyLab",
                        "tools/jdi-tracer/specs/circular-dependency-lab.txt",
                        InterpreterKind.BEAN_LIFECYCLE),
                entry(repoRoot, "aop-proxy", "AOP 자동 프록시 생성",
                        "advisor 빈이 재귀적으로 인스턴스화되는 순간과 JDK/CGLIB 프록시 선택 경로.",
                        "spring-extensions:method-timing-post-processor",
                        "lab.ext.timing.AutoProxyCreationLab",
                        "tools/jdi-tracer/specs/auto-proxy-creation-lab.txt",
                        InterpreterKind.AUTO_PROXY),
                entry(repoRoot, "tx-propagation", "트랜잭션 전파",
                        "REQUIRES_NEW의 suspend/resume, 그리고 참여자 실패가 커밋 시점의 "
                                + "UnexpectedRollbackException으로 이어지는 경로.",
                        "experiments:transaction-propagation-playground",
                        "lab.experiments.tx.TransactionPropagationLab",
                        "tools/jdi-tracer/specs/transaction-propagation-lab.txt",
                        InterpreterKind.TX_PROPAGATION),
                entry(repoRoot, "dispatcher-flow", "DispatcherServlet 요청 흐름",
                        "임베디드 Tomcat에 실제 HTTP 요청을 쏴서, doDispatch → HandlerMapping → "
                                + "Interceptor → Controller(→ 예외 시 ExceptionResolver) 순서로 "
                                + "파이프라인이 채워지는 걸 지켜본다.",
                        "experiments:dispatcher-servlet-trace",
                        "lab.experiments.mvc.DispatcherServletTraceLab",
                        "tools/jdi-tracer/specs/dispatcher-servlet-trace.txt",
                        InterpreterKind.DISPATCHER_FLOW),
                entry(repoRoot, "event-multicast", "애플리케이션 이벤트 멀티캐스트",
                        "동기 순서 리스너, condition 리스너, @Async 리스너(진짜 다른 스레드), 그리고 "
                                + "@TransactionalEventListener가 커밋 후에만 실행되는 것과 리스너 예외가 "
                                + "이후 리스너를 전부 막는 것까지.",
                        "experiments:application-event-lab",
                        "lab.experiments.event.ApplicationEventLab",
                        "tools/jdi-tracer/specs/application-event-lab.txt",
                        InterpreterKind.EVENT_MULTICAST),
                entry(repoRoot, "mvc-exception-priority", "MVC 예외 처리 우선순위",
                        "컨트롤러 로컬 @ExceptionHandler가 @ControllerAdvice보다 항상 먼저 이기는 것, "
                                + "두 advice가 겹치면 @Order가 정하는 것, 그리고 세 리졸버(ExceptionHandler "
                                + "→ ResponseStatus → Default)가 어디서 멈추는지.",
                        "experiments:mvc-exception-pipeline",
                        "lab.experiments.mvcerror.ExceptionPipelineLab",
                        "tools/jdi-tracer/specs/mvc-exception-pipeline.txt",
                        InterpreterKind.EXCEPTION_RESOLUTION));
    }

    /** {@code user.dir}이 저장소 루트가 아닐 수 있는 모든 실행 맥락(Gradle run/test/IDE)에서
     * 안전하게 저장소 루트를 찾는다 - {@code settings.gradle.kts}가 있는 조상 디렉터리까지
     * 위로 올라간다. */
    public static Path findRepoRoot(Path start) {
        Path dir = start.toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate repo root (settings.gradle.kts) starting from " + start);
    }

    private static ScenarioDefinitionEntity entry(Path repoRoot, String name, String title, String description,
                                                    String gradleModulePath, String mainClass,
                                                    String specFileRelativePath, InterpreterKind interpreterKind) {
        String breakpointSpec = readSpecFile(repoRoot.resolve(specFileRelativePath));
        return new ScenarioDefinitionEntity(name, title, description, List.of(gradleModulePath), mainClass,
                breakpointSpec, interpreterKind);
    }

    private static String readSpecFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read breakpoint spec file: " + path, e);
        }
    }
}
