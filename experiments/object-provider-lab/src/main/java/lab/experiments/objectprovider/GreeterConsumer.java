package lab.experiments.objectprovider;

import org.springframework.beans.factory.ObjectProvider;

// ObjectProvider<Greeter>는 생성자 시점에는 아무것도 조회하지 않는다 - Greeter 후보가
// 0개든, 1개든, 여러 개든 이 빈 자신의 생성은 항상 성공한다. 실제 후보 개수에 따른 차이는
// 이 안의 메서드를 실제로 호출하는 시점에야 드러난다.
public class GreeterConsumer {

    private final ObjectProvider<Greeter> greeters;

    public GreeterConsumer(ObjectProvider<Greeter> greeters) {
        this.greeters = greeters;
    }

    public ObjectProvider<Greeter> greeters() {
        return greeters;
    }
}
