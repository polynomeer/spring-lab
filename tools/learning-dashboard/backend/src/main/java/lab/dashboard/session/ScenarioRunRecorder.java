package lab.dashboard.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * 같은 이름으로 {@link ScenarioStarted}가 다시 온 경우)은 그냥 버린다 - 어중간하게 잘린
 * 기록을 남기는 것보다 "완료된 실행만 진짜 기록"이라는 단순한 규칙이 낫다.
 *
 * <p>시나리오 이름을 키로 하는 맵으로 기록 중인 실행들을 관리한다 - 처음엔 단일
 * {@code AtomicReference}였지만, docs/plan/04-dynamic-scenario-design.md 7번 절 "A/B 비교
 * 실행"으로 두 개의 시나리오가 동시에 돌 수 있게 되면서, 나중에 시작한 쪽의 {@code ScenarioStarted}가
 * 앞서 기록 중이던(이름이 다른) 실행을 조용히 밀어내 버리는 문제가 생겨 맵으로 일반화했다.
 *
 * <p>동시성: 한 실행의 모든 이벤트({@code ScenarioStarted} 포함)는 결국 같은 스레드
 * 하나({@code ScenarioSession#pump}가 도는 리더 스레드, 또는 그 실행을 시작시킨 STOMP
 * 처리 스레드)에서 동기적으로(Spring의 기본 {@code ApplicationEventMulticaster}는 비동기가
 * 아니다) 순서대로 발행되므로, 이름별 버퍼 자체는 별도 동기화가 필요 없다 - {@link ConcurrentHashMap}은
 * "서로 다른 이름의 기록들이 동시에 존재할 수 있다"는 것만 안전하게 드러내면 된다.
 */
@Component
public class ScenarioRunRecorder {

    private final ScenarioRunRepository repository;
    private final ObjectMapper mapper;
    private final Map<String, Recording> recordings = new ConcurrentHashMap<>();

    public ScenarioRunRecorder(ScenarioRunRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @EventListener
    public void onStarted(ScenarioStarted event) {
        recordings.put(event.scenarioName(), new Recording(event.scenarioName(), Instant.now()));
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
        Recording recording = recordings.get(event.scenarioName());
        if (recording != null) {
            finish(recording, null, true);
        }
    }

    private Recording append(String scenarioName, Map<String, Object> envelope) {
        Recording recording = recordings.get(scenarioName);
        if (recording == null) {
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
            recordings.remove(recording.scenarioName, recording);
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
