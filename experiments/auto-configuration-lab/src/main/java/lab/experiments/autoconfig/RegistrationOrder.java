package lab.experiments.autoconfig;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// 각 자동 설정이 실제로 어떤 순서로 평가/등록됐는지 기록하기 위한 관찰용 정적 로그 -
// AutoConfigurationSorter가 정렬한 결과를 눈으로 확인하는 용도일 뿐이다.
public final class RegistrationOrder {

    private static final List<String> order = new CopyOnWriteArrayList<>();

    private RegistrationOrder() {
    }

    public static void record(String name) {
        order.add(name);
    }

    public static List<String> order() {
        return List.copyOf(order);
    }

    public static void reset() {
        order.clear();
    }
}
