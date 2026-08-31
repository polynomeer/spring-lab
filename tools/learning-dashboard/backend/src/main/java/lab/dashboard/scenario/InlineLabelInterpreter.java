package lab.dashboard.scenario;

import java.util.List;
import java.util.Map;

import lab.dashboard.interpret.ScenarioInterpreter;
import lab.dashboard.interpret.SemanticEvent;
import lab.tools.jdi.TraceEvent;

/**
 * {@link InlineLabelParser}가 뽑아낸 "메서드 단순 이름 → 라벨" 맵을 그대로 재생하는
 * 해석기 - 라벨이 붙은 메서드에서 히트가 나올 때만 이벤트를 만들고, 그 외에는 원본 이벤트
 * 로그에만 남는다(SemanticEventLog는 이미 type/attributes를 그대로 보여주는 범용
 * 컴포넌트라, 프론트엔드는 이 라벨을 위한 별도 코드가 전혀 필요 없다).
 */
public class InlineLabelInterpreter implements ScenarioInterpreter {

    private final Map<String, String> labelsByMethod;

    public InlineLabelInterpreter(Map<String, String> labelsByMethod) {
        this.labelsByMethod = labelsByMethod;
    }

    @Override
    public List<SemanticEvent> onHit(TraceEvent hit) {
        String label = labelsByMethod.get(hit.location().methodName());
        if (label == null) {
            return List.of();
        }
        return List.of(SemanticEvent.of(label, hit.hitId(), "method", hit.location().methodName()));
    }
}
