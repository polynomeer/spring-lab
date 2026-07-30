package lab.experiments.bootlifecycle;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

// SpringApplication.addListeners()로 등록해야 한다 - 컴포넌트 스캔으로 등록되는 일반 빈은
// ApplicationContext가 이미 만들어져 그 빈 자신이 인스턴스화된 뒤의 이벤트만 받을 수 있다
// (BeanRegisteredObserver와 실제로 비교해서 확인한다). 이 관찰자는 ApplicationStartingEvent처럼
// ApplicationContext 자체가 아직 없는 시점의 이벤트까지 전부 받아야 하므로, 컨테이너 밖에서
// SpringApplication에 직접 등록한다.
public final class LifecycleObserver implements ApplicationListener<ApplicationEvent> {

    private final List<Observation> observations = new ArrayList<>();

    public record Observation(
            String eventName, boolean environmentAvailable, boolean contextAvailable, boolean beanLookupAvailable) {
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        observations.add(observe(event));
    }

    public List<Observation> observations() {
        return List.copyOf(observations);
    }

    private Observation observe(ApplicationEvent event) {
        ConfigurableApplicationContext context = extractContext(event);
        boolean environmentAvailable = (event instanceof ApplicationEnvironmentPreparedEvent) || context != null;
        boolean contextAvailable = context != null;
        boolean beanLookupAvailable = context != null && canLookUpMarkerBean(context);
        return new Observation(event.getClass().getSimpleName(), environmentAvailable, contextAvailable,
                beanLookupAvailable);
    }

    private ConfigurableApplicationContext extractContext(ApplicationEvent event) {
        if (event instanceof ApplicationContextInitializedEvent e) {
            return e.getApplicationContext();
        }
        if (event instanceof ApplicationPreparedEvent e) {
            return e.getApplicationContext();
        }
        if (event instanceof ApplicationStartedEvent e) {
            return e.getApplicationContext();
        }
        if (event instanceof ApplicationReadyEvent e) {
            return e.getApplicationContext();
        }
        if (event instanceof ApplicationFailedEvent e) {
            return e.getApplicationContext();
        }
        if (event instanceof AvailabilityChangeEvent<?> e && e.getSource() instanceof ConfigurableApplicationContext ctx) {
            return ctx;
        }
        return null;
    }

    private boolean canLookUpMarkerBean(ConfigurableApplicationContext context) {
        try {
            context.getBean(MarkerBean.class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
