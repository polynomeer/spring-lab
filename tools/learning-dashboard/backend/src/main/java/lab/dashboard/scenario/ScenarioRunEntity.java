package lab.dashboard.scenario;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 완료된(정상 종료 또는 타임아웃 강제 종료) 시나리오 실행 하나의 기록 -
 * docs/plan/04-dynamic-scenario-design.md 7번 절 "실행 히스토리 스냅샷". 새 자식 JVM을
 * 다시 띄우지 않고도 지난 실행을 다시 볼 수 있게 한다. 이건 라이브 실행을 흉내 낸 가짜
 * 데이터가 아니라 - {@code eventsJson}에 담긴 것은 그 시나리오가 실제로 만들어 낸 진짜
 * JDI 히트/semantic 이벤트 스트림 그대로다.
 *
 * <p>도중에 다른 시나리오로 교체돼 끝까지 못 간 실행은 기록하지 않는다(완료 또는 타임아웃만) -
 * 어중간하게 잘린 기록을 남기는 것보다는, "완료된 실행만 진짜 기록"이라는 단순한 규칙이
 * 낫다고 판단했다. {@link ScenarioDefinitionEntity}를 FK로 참조하지 않는다 - 원본 시나리오가
 * 나중에 수정/삭제돼도 이 기록은 "그 시점에 무엇을 실행했는지"를 그대로 간직해야 한다.
 */
@Entity
@Table(name = "scenario_run")
public class ScenarioRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String scenarioName;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant finishedAt;

    /** 정상 종료면 최종 히트 개수, 타임아웃 강제 종료면 null(끝까지 못 셌으므로). */
    private Integer totalHits;

    @Column(nullable = false)
    private boolean timedOut;

    /** ScenarioMessageMapper가 만드는 것과 같은 모양의 JSON 봉투 배열 - 순서 그대로. */
    @Lob
    @Column(nullable = false)
    private String eventsJson;

    protected ScenarioRunEntity() {
        // JPA
    }

    public ScenarioRunEntity(String scenarioName, Instant startedAt, Instant finishedAt, Integer totalHits,
                              boolean timedOut, String eventsJson) {
        this.scenarioName = scenarioName;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalHits = totalHits;
        this.timedOut = timedOut;
        this.eventsJson = eventsJson;
    }

    public Long getId() {
        return id;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Integer getTotalHits() {
        return totalHits;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public String getEventsJson() {
        return eventsJson;
    }
}
