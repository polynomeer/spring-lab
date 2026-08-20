package lab.experiments.smartinit;

import org.springframework.beans.factory.SmartInitializingSingleton;

// @Lazy로 등록되므로 preInstantiateSingletons()의 본 루프에 애초에 포함되지 않는다 -
// afterSingletonsInstantiated()를 호출해 주는 두 번째 루프도 그 본 루프가 실제로 만든
// beanNames 목록만 대상으로 하므로, 이 빈은 그 목록에도 없다.
public class LazySmartBean implements SmartInitializingSingleton {

    private final RecordingLifecycleLog log;

    public LazySmartBean(RecordingLifecycleLog log) {
        this.log = log;
        log.record("LazySmartBean.constructor");
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.record("LazySmartBean.afterSingletonsInstantiated");
    }
}
