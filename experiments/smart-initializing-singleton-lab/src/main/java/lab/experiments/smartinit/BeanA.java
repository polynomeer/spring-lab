package lab.experiments.smartinit;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

// SmartInitializingSingletonConfig에서 beanB보다 먼저 선언된다 - 그런데도
// afterSingletonsInstantiated()가 호출되는 시점에는 beanB가 이미 완전히 만들어져 있다.
// preInstantiateSingletons()의 "본 루프"가 모든 싱글턴을 먼저 다 만들고, 그 뒤에 별도의
// 두 번째 루프가 SmartInitializingSingleton만 골라 콜백을 호출하기 때문이다 - 선언 순서와
// 무관하다.
public class BeanA implements SmartInitializingSingleton {

    private final RecordingLifecycleLog log;
    private final ApplicationContext context;

    public BeanA(RecordingLifecycleLog log, ApplicationContext context) {
        this.log = log;
        this.context = context;
        log.record("BeanA.constructor");
    }

    @Override
    public void afterSingletonsInstantiated() {
        BeanB beanB = context.getBean(BeanB.class);
        log.record("BeanA.afterSingletonsInstantiated sees BeanB.postConstructDone=" + beanB.isPostConstructDone());
    }
}
