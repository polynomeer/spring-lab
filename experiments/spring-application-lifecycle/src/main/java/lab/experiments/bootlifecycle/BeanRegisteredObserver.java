package lab.experiments.bootlifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

// LifecycleObserver와 똑같이 모든 ApplicationEvent를 받으려 하지만, 이건 컴포넌트 스캔으로
// 등록되는 평범한 빈이다 - ApplicationContext가 이 빈 자신을 만들 수 있어야 이벤트를 받을 수
// 있으므로, 그전에 발행된 이벤트(ApplicationStartingEvent, ApplicationEnvironmentPreparedEvent)는
// 구조적으로 놓칠 수밖에 없다는 것을 LifecycleObserver와 나란히 비교해서 확인한다.
@Component
public final class BeanRegisteredObserver implements ApplicationListener<ApplicationEvent> {

    private static final List<String> eventNames = new CopyOnWriteArrayList<>();

    static void reset() {
        eventNames.clear();
    }

    static List<String> eventNames() {
        return List.copyOf(eventNames);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        eventNames.add(event.getClass().getSimpleName());
    }
}
