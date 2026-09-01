package lab.dashboard.web;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import lab.dashboard.session.ClasspathResolver;
import lab.dashboard.scenario.DynamicScenarioCompiler;
import lab.dashboard.scenario.InterpreterKind;
import lab.dashboard.scenario.ScenarioDefinitionEntity;
import lab.dashboard.scenario.ScenarioDocExporter;
import lab.dashboard.scenario.ScenarioModuleLookup;
import lab.dashboard.scenario.ScenarioRepository;
import lab.dashboard.scenario.ScenarioRunEntity;
import lab.dashboard.scenario.ScenarioRunRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장된 시나리오 CRUD - docs/plan/04-dynamic-scenario-design.md 4.1절. 시나리오를 실제로
 * "실행"하는 것은 여전히 STOMP({@code /app/scenario/{name}/start}, {@link ScenarioWebSocketController})의
 * 몫이다 - 이 컨트롤러는 시나리오 정의 자체를 조회/저장/삭제하는 순수 REST 계층이고, DB
 * 엔티티({@link ScenarioDefinitionEntity})를 직접 노출하지 않고 항상 {@link ScenarioResponse}로
 * 감싸 돌려준다.
 *
 * <p>2단계(즉석 코드 작성) 시나리오는 저장 시점에 미리 한 번 컴파일해 본다 - 컴파일이
 * 안 되는 코드는 애초에 DB에 들어가지 못한다("실패한 컴파일은 저장되지 않는다"). 실행
 * 시점에도 {@link lab.dashboard.session.ScenarioCatalog}가 다시 컴파일하지만(자식 JVM에
 * 넘길 최신 산출물이 필요하므로), 저장 시점의 이 검증 덕분에 사용자는 "저장하고 목록에
 * 추가"를 누른 순간 바로 컴파일 에러를 볼 수 있다 - 탭을 열어 실행해 보기 전에.
 */
@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioRepository repository;
    private final ScenarioModuleLookup moduleLookup;
    private final ClasspathResolver classpathResolver;
    private final DynamicScenarioCompiler compiler;
    private final ScenarioRunRepository runRepository;
    private final ScenarioDocExporter docExporter;

    public ScenarioController(ScenarioRepository repository, ScenarioModuleLookup moduleLookup,
                               ClasspathResolver classpathResolver, DynamicScenarioCompiler compiler,
                               ScenarioRunRepository runRepository, ScenarioDocExporter docExporter) {
        this.repository = repository;
        this.moduleLookup = moduleLookup;
        this.classpathResolver = classpathResolver;
        this.compiler = compiler;
        this.runRepository = runRepository;
        this.docExporter = docExporter;
    }

    @GetMapping
    public List<ScenarioResponse> list() {
        return repository.findAllByOrderByIdAsc().stream().map(ScenarioResponse::from).toList();
    }

    @GetMapping("/modules")
    public List<String> modules() {
        return moduleLookup.listModulePaths();
    }

    @GetMapping("/compiler-status")
    public Map<String, Boolean> compilerStatus() {
        return Map.of("available", compiler.isAvailable());
    }

    /**
     * docs/plan/04-dynamic-scenario-design.md 7번 절 "시나리오 → 정식 문서 뼈대 export".
     * {@code runId}를 안 주면 이 시나리오의 가장 최근 완료된 실행을 8절(런타임 관찰) 표의
     * 근거로 쓴다 - 완료된 실행이 아예 없으면 8절은 TODO로 남는다.
     */
    @GetMapping(value = "/export", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String export(@RequestParam String name, @RequestParam(required = false) Long runId) {
        ScenarioDefinitionEntity scenario = repository.findByName(name)
                .orElseThrow(() -> new NoSuchElementException("no scenario named " + name));
        ScenarioRunEntity run = runId != null
                ? runRepository.findById(runId).orElse(null)
                : runRepository.findByScenarioNameOrderByStartedAtDesc(name).stream().findFirst().orElse(null);
        return docExporter.export(scenario, run);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioResponse create(@RequestBody ScenarioSaveRequest request) {
        validate(request);
        if (repository.existsByName(request.name())) {
            throw new DuplicateScenarioNameException(request.name());
        }
        compileIfSourceProvided(request);

        ScenarioDefinitionEntity entity = new ScenarioDefinitionEntity(
                request.name(), request.title(), request.description(), request.gradleModulePaths(),
                request.mainClass(), request.sourceCode(), request.breakpointSpec(), InterpreterKind.NONE);
        return ScenarioResponse.from(repository.save(entity));
    }

    @PutMapping("/{id}")
    public ScenarioResponse update(@PathVariable Long id, @RequestBody ScenarioSaveRequest request) {
        validate(request);
        ScenarioDefinitionEntity existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("no scenario with id " + id));
        repository.findByName(request.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateScenarioNameException(request.name());
                });
        compileIfSourceProvided(request);

        // id/interpreterKind는 건드리지 않고 나머지 필드만 그 자리에서 갱신한다 - 지우고
        // 다시 만들면 id가 바뀌어서(자동 증가), 이 id를 들고 있는 클라이언트 쪽 참조가 전부
        // 끊긴다(처음 이렇게 짰다가 실제로 겪은 버그 - 갱신 직후 그 id로 삭제를 호출하면
        // 이미 다른 id로 대체된 뒤라 아무것도 지워지지 않았다).
        existing.update(request.name(), request.title(), request.description(), request.gradleModulePaths(),
                request.mainClass(), request.sourceCode(), request.breakpointSpec());
        return ScenarioResponse.from(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }

    private void compileIfSourceProvided(ScenarioSaveRequest request) {
        if (request.sourceCode() == null || request.sourceCode().isBlank()) {
            return;
        }
        String classpath = request.gradleModulePaths().stream()
                .map(classpathResolver::resolve)
                .collect(Collectors.joining(File.pathSeparator));
        DynamicScenarioCompiler.CompileResult result =
                compiler.compile(request.mainClass(), request.sourceCode(), classpath);
        // 저장 시점 검증일 뿐, 이 산출물로 뭘 실행하지는 않는다 - 바로 치운다. 실제 실행에
        // 쓰일 산출물은 ScenarioCatalog가 실행 시점에 새로 컴파일한다.
        compiler.discard(result);
        if (!result.success()) {
            throw new CompilationFailedException(result.diagnostics());
        }
    }

    private void validate(ScenarioSaveRequest request) {
        requireNonBlank(request.name(), "name");
        requireNonBlank(request.title(), "title");
        requireNonBlank(request.mainClass(), "mainClass");
        requireNonBlank(request.breakpointSpec(), "breakpointSpec");
        if (request.gradleModulePaths() == null || request.gradleModulePaths().isEmpty()) {
            throw new IllegalArgumentException("gradleModulePaths must not be empty");
        }
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(CompilationFailedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<String> handleCompilationFailed(CompilationFailedException e) {
        return e.diagnostics();
    }

    @ExceptionHandler(DuplicateScenarioNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDuplicateName(DuplicateScenarioNameException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }

    private static final class DuplicateScenarioNameException extends RuntimeException {
        DuplicateScenarioNameException(String name) {
            super("a scenario named '" + name + "' already exists");
        }
    }

    private static final class CompilationFailedException extends RuntimeException {
        private final List<String> diagnostics;

        CompilationFailedException(List<String> diagnostics) {
            super(String.join("\n", diagnostics));
            this.diagnostics = diagnostics;
        }

        List<String> diagnostics() {
            return diagnostics;
        }
    }
}
