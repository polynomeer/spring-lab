package lab.dashboard.session;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lab.dashboard.interpret.ScenarioInterpreter;
import lab.dashboard.scenario.DynamicScenarioCompiler;
import lab.dashboard.scenario.InlineLabelInterpreter;
import lab.dashboard.scenario.InlineLabelParser;
import lab.dashboard.scenario.ScenarioCompilationException;
import lab.dashboard.scenario.ScenarioDefinitionEntity;
import lab.dashboard.scenario.ScenarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 시나리오 이름 → 실행 가능한 정의 조회 표. docs/plan/04-dynamic-scenario-design.md 이전에는
 * 여섯 개 시나리오가 이 클래스 안에 자바 코드로 하드코딩돼 있었다 - 이제는 {@link ScenarioRepository}
 * (DB)가 그 정의를 갖고, 이 클래스는 "DB 엔티티 + 클래스패스 해석(+ 필요하면 컴파일)"을
 * {@link ScenarioSession}이 바로 쓸 수 있는 {@link ScenarioDefinition}으로 조립하는 어댑터로
 * 남는다 - 하위(세션, 웹소켓 컨트롤러) 쪽 계약은 전혀 바뀌지 않는다.
 */
@Component
public class ScenarioCatalog {

    private final ScenarioRepository repository;
    private final ClasspathResolver classpathResolver;
    private final DynamicScenarioCompiler compiler;

    @Autowired
    public ScenarioCatalog(ScenarioRepository repository, ClasspathResolver classpathResolver,
                            DynamicScenarioCompiler compiler) {
        this.repository = repository;
        this.classpathResolver = classpathResolver;
        this.compiler = compiler;
    }

    public List<String> scenarioNames() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(ScenarioDefinitionEntity::getName)
                .toList();
    }

    /**
     * 클래스패스를 (필요하면 gradlew를 셸아웃해서) 해석하고, 2단계 시나리오(소스코드가
     * 있는)라면 매번 새로 컴파일까지 마친 실행 가능한 정의로 돌려준다. 컴파일이 실패하면
     * {@link ScenarioCompilationException}을 던진다 - 저장 시점에도 같은 검증을 거치므로
     * (ScenarioController) 정상적으로는 여기서 실패할 일이 없지만, 저장 이후 클래스패스
     * 구성이 바뀌는 등의 경우를 대비한 마지막 방어선이다.
     */
    public Optional<ScenarioDefinition> resolve(String name) {
        return repository.findByName(name).map(this::toDefinition);
    }

    private ScenarioDefinition toDefinition(ScenarioDefinitionEntity entity) {
        // 모듈을 여러 개 고른 시나리오(2단계)라면 각 모듈의 클래스패스를 이어붙인다 - 순서는
        // 엔티티에 저장된 순서(@OrderColumn) 그대로다.
        String classpath = entity.getGradleModulePaths().stream()
                .map(classpathResolver::resolve)
                .collect(Collectors.joining(File.pathSeparator));

        boolean hasSourceCode = entity.getSourceCode() != null && !entity.getSourceCode().isBlank();
        Supplier<ScenarioInterpreter> interpreterFactory = entity.getInterpreterKind().factory();
        if (hasSourceCode) {
            DynamicScenarioCompiler.CompileResult result =
                    compiler.compile(entity.getMainClass(), entity.getSourceCode(), classpath);
            if (!result.success()) {
                throw new ScenarioCompilationException(entity.getName(), result.diagnostics());
            }
            // 방금 컴파일한 사용자 코드가 클래스패스 상의 다른 클래스보다 항상 먼저
            // 발견되도록 맨 앞에 둔다. 이 임시 디렉터리는 자식 JVM이 살아있는 동안 계속
            // 필요하므로 여기서 지우지 않는다(OS 임시 디렉터리 정리에 맡긴다).
            classpath = result.outputDir() + File.pathSeparator + classpath;

            // 손으로 짠 해석기 없이도(interpreterKind는 사용자 시나리오에서 항상 NONE),
            // 소스 코드 안의 "// @dashboard-label: ..." 주석만으로 최소한의 semantic
            // 이벤트를 만든다(docs/plan/04-dynamic-scenario-design.md 7번 절). 라벨이
            // 하나도 없으면 굳이 새 해석기로 바꾸지 않고 기존 NONE(원본 로그만)을 그대로 둔다.
            Map<String, String> labels = InlineLabelParser.parseLabels(entity.getSourceCode());
            if (!labels.isEmpty()) {
                interpreterFactory = () -> new InlineLabelInterpreter(labels);
            }
        }

        return new ScenarioDefinition(
                entity.getName(), classpath, entity.getMainClass(), entity.getBreakpointSpec(),
                interpreterFactory, hasSourceCode);
    }
}
