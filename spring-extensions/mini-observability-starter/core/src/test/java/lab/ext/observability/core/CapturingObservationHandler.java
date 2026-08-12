package lab.ext.observability.core;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

// 실제 Micrometer의 확장점(ObservationHandler)을 그대로 구현한 테스트 지원 클래스 - 옛
// ObservationLog가 하던 역할(테스트가 관찰 결과를 직접 조회)을, 이제는 커스텀 로그 빈이
// 아니라 Micrometer가 실제로 제공하는 SPI로 대신한다. ObservationRegistry에 등록해 두면
// 완료된(stop된) Observation의 Context를 그대로 모은다.
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
