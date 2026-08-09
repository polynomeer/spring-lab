package lab.dashboard.conditionreport;

import lab.dashboard.session.ClasspathResolver;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * condition-report 시나리오의 실행기 - {@link lab.dashboard.session.ScenarioSession}과 별개다.
 * 이 시나리오는 스텝/재생 개념이 없다({@code ConditionEvaluationReport}는 {@code refresh()}가
 * 끝나는 순간 이미 완성된 데이터라 "단계"가 없다 - docs/plan/03-learning-dashboard-design.md
 * 6.5절 참고) - 그래서 JDI도, TracerServer도, "지금 실행 중인 세션 1개"라는 상태도 없다.
 * 매 실행은 짧게 살고, 끝나고, 결과 하나만 남긴다.
 */
@Component
public class ConditionReportRunner {

    private static final String GRADLE_MODULE_PATH = ":experiments:auto-configuration-lab";
    private static final String MAIN_CLASS = "lab.experiments.autoconfig.ConditionReportLab";
    private static final long TIMEOUT_SECONDS = 30;

    private final ClasspathResolver classpathResolver;
    private final ApplicationEventPublisher eventPublisher;

    public ConditionReportRunner(ClasspathResolver classpathResolver, ApplicationEventPublisher eventPublisher) {
        this.classpathResolver = classpathResolver;
        this.eventPublisher = eventPublisher;
    }

    /** WebSocket 핸들러 스레드를 막지 않도록 별도 스레드에서 실행한다 - gradlew 셸아웃(첫 호출)과 컨텍스트 refresh 모두 수백 ms~수 초가 걸릴 수 있다. */
    public void run(Map<String, String> propertyOverrides) {
        Thread worker = new Thread(() -> runBlocking(propertyOverrides));
        worker.setDaemon(true);
        worker.start();
    }

    private void runBlocking(Map<String, String> propertyOverrides) {
        try {
            String classpath = classpathResolver.resolve(GRADLE_MODULE_PATH);
            String javaBin = System.getProperty("java.home") + "/bin/java";

            List<String> command = new ArrayList<>(List.of(javaBin, "-cp", classpath, MAIN_CLASS));
            propertyOverrides.forEach((key, value) -> command.add(key + "=" + value));

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            Process process = builder.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().filter(line -> !line.isBlank()).reduce((first, last) -> last).orElse("");
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                eventPublisher.publishEvent(new ConditionReportReceived(propertyOverrides, null,
                        "ConditionReportLab timed out after " + TIMEOUT_SECONDS + "s"));
                return;
            }

            if (process.exitValue() != 0 || output.isBlank()) {
                eventPublisher.publishEvent(new ConditionReportReceived(propertyOverrides, null,
                        "ConditionReportLab exited " + process.exitValue() + " with no usable output"));
                return;
            }

            eventPublisher.publishEvent(new ConditionReportReceived(propertyOverrides, output, null));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            eventPublisher.publishEvent(new ConditionReportReceived(propertyOverrides, null, e.getMessage()));
        }
    }
}
