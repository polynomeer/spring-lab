package lab.ext.observability.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// 실제 스타터라면 Micrometer ObservationRegistry나 로거로 내보냈을 자리다 - 이 랩에서는
// "관찰 가능한 결과를 조회할 수 있는 빈"으로 단순화해서, 테스트가 로그 출력을 파싱하지 않고
// 직접 결과를 확인할 수 있게 했다.
public class ObservationLog {

    private final List<ObservationEntry> entries = new CopyOnWriteArrayList<>();

    public void record(ObservationEntry entry) {
        entries.add(entry);
    }

    public List<ObservationEntry> entries() {
        return List.copyOf(entries);
    }
}
