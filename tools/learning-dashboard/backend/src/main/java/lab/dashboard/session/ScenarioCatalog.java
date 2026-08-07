package lab.dashboard.session;

import lab.dashboard.interpret.AutoProxyInterpreter;
import lab.dashboard.interpret.BeanLifecycleInterpreter;
import lab.dashboard.interpret.DispatcherFlowInterpreter;
import lab.dashboard.interpret.EventMulticastInterpreter;
import lab.dashboard.interpret.ExceptionResolutionInterpreter;
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
 * semantic 해석기) 조회 표.
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
        register(repoRoot, "dispatcher-flow", ":experiments:dispatcher-servlet-trace",
                "lab.experiments.mvc.DispatcherServletTraceLab",
                "tools/jdi-tracer/specs/dispatcher-servlet-trace.txt",
                DispatcherFlowInterpreter::new);
        register(repoRoot, "event-multicast", ":experiments:application-event-lab",
                "lab.experiments.event.ApplicationEventLab",
                "tools/jdi-tracer/specs/application-event-lab.txt",
                EventMulticastInterpreter::new);
        register(repoRoot, "mvc-exception-priority", ":experiments:mvc-exception-pipeline",
                "lab.experiments.mvcerror.ExceptionPipelineLab",
                "tools/jdi-tracer/specs/mvc-exception-pipeline.txt",
                ExceptionResolutionInterpreter::new);
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
