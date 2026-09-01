package lab.dashboard.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lab.dashboard.scenario.ScenarioRunEntity;
import lab.dashboard.scenario.ScenarioRunRepository;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * docs/plan/04-dynamic-scenario-design.md 7번 절 "실행 히스토리 스냅샷" - {@link ScenarioSession}이
 * 발행하는 것과 똑같은 이벤트를 {@code lab.dashboard.web.ScenarioWebSocketController}와
 * 나란히 구독해서(전송 계층과는 완전히 별개 - 서로 존재를 모른다), 완료된 실행 하나를
 * {@link ScenarioMessageMapper}로 만든 JSON 봉투 목록 그대로 DB에 남긴다.
 *
 * <p>도중에 다른 시나리오로 교체돼 끝까지 못 간 실행(=완료/타임아웃 이벤트를 못 받은 채
 * {@link ScenarioStarted}가 다시 온 경우)은 그냥 버린다 - 어중간하게 잘린 기록을 남기는
 * 것보다 "완료된 실행만 진짜 기록"이라는 단순한 규칙이 낫다.
 *
 * <p>동시성: 한 실행의 모든 이벤트({@code ScenarioStarted} 포함)는 결국 같은 스레드
 * 하나({@code ScenarioSession#pump}가 도는 리더 스레드, 또는 그 실행을 시작시킨 STOMP
 * 처리 스레드)에서 동기적으로(Spring의 기본 {@code ApplicationEventMulticaster}는 비동기가
 * 아니다) 순서대로 발행되므로, 버퍼 자체는 별도 동기화가 필요 없다 - {@link AtomicReference}는
 * "지금 기록 중인 실행이 바뀌었다"는 것만 안전하게 드러내면 된다.
 */
@Component
public class ScenarioRunRecorder {

    private final ScenarioRunRepository repository;
    private final ObjectMapper mapper;
    private final AtomicReference<Recording> current = new AtomicReference<>();

    public ScenarioRunRecorder(ScenarioRunRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @EventListener
    public void onStarted(ScenarioStarted event) {
        current.set(new Recording(event.scenarioName(), Instant.now()));
    }

    @EventListener
    public void onRawHit(RawHitReceived event) {
        append(event.scenarioName(), ScenarioMessageMapper.forHit(event));
    }

    @EventListener
    public void onSemanticEvent(SemanticEventReceived event) {
        append(event.scenarioName(), ScenarioMessageMapper.forSemantic(event));
    }

    @EventListener
    public void onStdout(ScenarioStdoutReceived event) {
        append(event.scenarioName(), ScenarioMessageMapper.forStdout(event));
    }

    @EventListener
    public void onExited(ScenarioExited event) {
        Recording recording = append(event.scenarioName(), ScenarioMessageMapper.forExited(event));
        if (recording != null) {
            finish(recording, event.totalHits(), false);
        }
    }

    @EventListener
    public void onTimedOut(ScenarioTimedOut event) {
        Recording recording = current.get();
        if (recording != null && recording.scenarioName.equals(event.scenarioName())) {
            finish(recording, null, true);
        }
    }

    private Recording append(String scenarioName, Map<String, Object> envelope) {
        Recording recording = current.get();
        if (recording == null || !recording.scenarioName.equals(scenarioName)) {
            // 우리가 시작을 못 본 실행(예: 백엔드 재시작 도중에 낀 이벤트) - 기록하지 않는다.
            return null;
        }
        recording.events.add(envelope);
        return recording;
    }

    private void finish(Recording recording, Integer totalHits, boolean timedOut) {
        try {
            String json = mapper.writeValueAsString(recording.events);
            repository.save(new ScenarioRunEntity(
                    recording.scenarioName, recording.startedAt, Instant.now(), totalHits, timedOut, json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize scenario run events", e);
        } finally {
            current.compareAndSet(recording, null);
        }
    }

    private static final class Recording {
        private final String scenarioName;
        private final Instant startedAt;
        private final List<Map<String, Object>> events = new ArrayList<>();

        Recording(String scenarioName, Instant startedAt) {
            this.scenarioName = scenarioName;
            this.startedAt = startedAt;
        }
    }
}
