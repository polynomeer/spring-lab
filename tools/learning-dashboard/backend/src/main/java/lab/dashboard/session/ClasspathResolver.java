package lab.dashboard.session;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "이 모듈의 런타임 클래스패스가 뭐지?"를 매 jdi-tracer 세션마다 손으로 throwaway init
 * script를 만들어 조회해 온 패턴을, 루트 {@code build.gradle.kts}에 영구히 등록해 둔
 * {@code printRuntimeClasspath} 태스크를 셸아웃으로 호출하는 방식으로 자동화한다.
 * 한 번 조회한 결과는 프로세스 생존 기간 동안 캐시한다 - 대상 모듈은 이미 빌드돼 있고
 * 바뀌지 않는다고 가정한다(개발 중 재컴파일이 필요하면 백엔드를 재시작한다).
 */
@Component
public class ClasspathResolver {

    private final Path repoRoot;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ClasspathResolver() {
        this(Path.of(System.getProperty("user.dir")));
    }

    ClasspathResolver(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public String resolve(String gradleModulePath) {
        return cache.computeIfAbsent(gradleModulePath, this::runGradleTask);
    }

    private String runGradleTask(String gradleModulePath) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    repoRoot.resolve("gradlew").toString(), "-q",
                    gradleModulePath + ":printRuntimeClasspath");
            builder.directory(repoRoot.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // -q로 조용히 시키면 태스크가 println한 클래스패스 한 줄만 남는다 - 혹시
                // 섞여 들어올 다른 출력에 대비해 마지막 비어있지 않은 줄을 취한다.
                output = reader.lines().filter(line -> !line.isBlank()).reduce((first, last) -> last).orElse("");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "gradlew " + gradleModulePath + ":printRuntimeClasspath failed (exit " + exitCode + "): " + output);
            }
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("failed to resolve classpath for " + gradleModulePath, e);
        }
    }
}
