package lab.dashboard.scenario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 저장된 시나리오 하나 - docs/plan/04-dynamic-scenario-design.md 3번 절의 스키마.
 * {@code sourceCode}는 2단계(즉석 코드 작성)에서만 채워진다 - 1단계에서 만드는 시나리오는
 * 항상 기존 실험 모듈의 이미 컴파일된 클래스를 가리키므로 null로 남는다. 스키마를 미리
 * 갖춰 두면 2단계에서 별도 마이그레이션 없이 그대로 확장할 수 있다.
 */
@Entity
@Table(name = "scenario_definition")
public class ScenarioDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** STOMP 라우팅/조회에 쓰이는 안정적인 slug - 사람이 읽는 제목은 {@link #title}. */
    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    // 순서를 보존해야 한다 - 2단계에서 여러 모듈을 골랐을 때 클래스패스를 이어붙이는 순서가
    // 곧 이 리스트의 순서이기 때문이다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scenario_module_path", joinColumns = @JoinColumn(name = "scenario_id"))
    @Column(name = "module_path", nullable = false)
    @OrderColumn(name = "position")
    private List<String> gradleModulePaths = new ArrayList<>();

    @Column(nullable = false)
    private String mainClass;

    @Lob
    private String sourceCode;

    @Lob
    @Column(nullable = false)
    private String breakpointSpec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterpreterKind interpreterKind = InterpreterKind.NONE;

    private Instant createdAt;
    private Instant updatedAt;

    protected ScenarioDefinitionEntity() {
        // JPA
    }

    public ScenarioDefinitionEntity(String name, String title, String description, List<String> gradleModulePaths,
                                     String mainClass, String breakpointSpec, InterpreterKind interpreterKind) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.gradleModulePaths = new ArrayList<>(gradleModulePaths);
        this.mainClass = mainClass;
        this.breakpointSpec = breakpointSpec;
        this.interpreterKind = interpreterKind;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getGradleModulePaths() {
        return List.copyOf(gradleModulePaths);
    }

    /** 1단계 REST API가 쓰는 전체 필드 교체 - id/interpreterKind는 건드리지 않는다. */
    public void update(String name, String title, String description, List<String> gradleModulePaths,
                        String mainClass, String breakpointSpec) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.gradleModulePaths = new ArrayList<>(gradleModulePaths);
        this.mainClass = mainClass;
        this.breakpointSpec = breakpointSpec;
    }

    public String getMainClass() {
        return mainClass;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getBreakpointSpec() {
        return breakpointSpec;
    }

    public InterpreterKind getInterpreterKind() {
        return interpreterKind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
