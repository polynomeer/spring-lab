package lab.dashboard.session;

import lab.dashboard.interpret.AutoProxyInterpreter;
import lab.dashboard.interpret.BeanLifecycleInterpreter;
import lab.dashboard.interpret.ScenarioInterpreter;
import lab.dashboard.interpret.TransactionPropagationInterpreter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 시나리오 이름 → (Gradle 모듈 경로, 대상 main 클래스, 브레이크포인트 스펙 파일,
 * semantic 해석기) 조회 표. 아직 라이브 Lab이 없는 DispatcherServlet 흐름(4단계로 미룸,
 * docs/plan/03-learning-dashboard-design.md 6.4절)은 여기 등록하지 않는다 - 실행할 수
 * 없는 시나리오를 카탈로그에 올려 두는 것보다, 실제로 돌아가는 것만 올려 두는 편이 낫다.
 */
@Component
public class ScenarioCatalog {

    private record Entry(
            String gradleModulePath, String mainClass, Path specFile, Supplier<ScenarioInterpreter> interpreterFactory) {
    }

    private final ClasspathResolver classpathResolver;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    @Autowired
    public ScenarioCatalog(ClasspathResolver classpathResolver) {
        this(Path.of(System.getProperty("user.dir")), classpathResolver);
    }

    ScenarioCatalog(Path repoRoot, ClasspathResolver classpathResolver) {
        this.classpathResolver = classpathResolver;

        register(repoRoot, "bean-lifecycle", ":experiments:circular-dependency-lab",
                "lab.experiments.circular.CircularDependencyLab",
                "tools/jdi-tracer/specs/circular-dependency-lab.txt",
                BeanLifecycleInterpreter::new);
        register(repoRoot, "aop-proxy", ":spring-extensions:method-timing-post-processor",
                "lab.ext.timing.AutoProxyCreationLab",
                "tools/jdi-tracer/specs/auto-proxy-creation-lab.txt",
                AutoProxyInterpreter::new);
        register(repoRoot, "tx-propagation", ":experiments:transaction-propagation-playground",
                "lab.experiments.tx.TransactionPropagationLab",
                "tools/jdi-tracer/specs/transaction-propagation-lab.txt",
                TransactionPropagationInterpreter::new);
    }

    private void register(Path repoRoot, String name, String gradleModulePath, String mainClass,
                           String specFileRelativePath, Supplier<ScenarioInterpreter> interpreterFactory) {
        entries.put(name, new Entry(gradleModulePath, mainClass, repoRoot.resolve(specFileRelativePath), interpreterFactory));
    }

    public List<String> scenarioNames() {
        return List.copyOf(entries.keySet());
    }

    /** 클래스패스를 (필요하면 gradlew를 셸아웃해서) 해석해 실행 가능한 정의로 돌려준다. */
    public Optional<ScenarioDefinition> resolve(String name) {
        Entry entry = entries.get(name);
        if (entry == null) {
            return Optional.empty();
        }
        String classpath = classpathResolver.resolve(entry.gradleModulePath());
        return Optional.of(new ScenarioDefinition(
                name, classpath, entry.mainClass(), entry.specFile().toString(), entry.interpreterFactory()));
    }
}
