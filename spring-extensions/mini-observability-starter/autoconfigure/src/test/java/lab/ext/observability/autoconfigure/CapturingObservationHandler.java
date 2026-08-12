package lab.ext.observability.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

// core 모듈의 동명 테스트 지원 클래스와 같은 역할 - 모듈별 테스트 소스셋이 분리돼 있어
// 공유할 수 없으므로 그대로 복제했다(이 저장소의 다른 Fake*/테스트 지원 클래스들도 모듈마다
// 독립적으로 두는 것과 같은 관례).
final class CapturingObservationHandler implements ObservationHandler<Observation.Context> {

    private final List<Observation.Context> completed = new ArrayList<>();

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStop(Observation.Context context) {
        completed.add(context);
    }

    List<Observation.Context> completedObservations() {
        return List.copyOf(completed);
    }
}
