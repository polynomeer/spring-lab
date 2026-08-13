package lab.experiments.testcontext;

import java.util.concurrent.atomic.AtomicInteger;

// 생성자가 호출된 횟수 자체를 계측한다 - ApplicationContext가 실제로 몇 번 새로 만들어졌는지는
// 반환값으로는 알 수 없고(매번 논리적으로 "같은" 빈처럼 보인다), 인스턴스 생성 횟수로만
// 구분할 수 있다. cache-abstraction-lab/method-validation-lab의 InvocationCounter와 같은 역할.
public class ContextCreationCounter {

    private static final AtomicInteger instancesCreated = new AtomicInteger();

    public ContextCreationCounter() {
        instancesCreated.incrementAndGet();
    }

    public static void reset() {
        instancesCreated.set(0);
    }

    public static int count() {
        return instancesCreated.get();
    }
}
