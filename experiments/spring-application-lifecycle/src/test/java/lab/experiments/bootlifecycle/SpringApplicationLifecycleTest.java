package lab.experiments.bootlifecycle;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SpringApplicationLifecycleTest {

    @BeforeEach
    void resetBeanRegisteredObserver() {
        BeanRegisteredObserver.reset();
    }

    private LifecycleObserver.Observation find(List<LifecycleObserver.Observation> observations, String eventName) {
        return observations.stream()
                .filter(o -> o.eventName().equals(eventName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no observation recorded for " + eventName));
    }

    @Test
    void eventsFireInOrderWithIncreasingAvailability() {
        SpringApplication application = new SpringApplication(LifecycleConfig.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        LifecycleObserver observer = new LifecycleObserver();
        application.addListeners(observer);

        ConfigurableApplicationContext context = application.run();
        try {
            List<LifecycleObserver.Observation> observations = observer.observations();
            List<String> eventNames = observations.stream().map(LifecycleObserver.Observation::eventName).toList();

            assertThat(eventNames).containsSubsequence(
                    "ApplicationStartingEvent",
                    "ApplicationEnvironmentPreparedEvent",
                    "ApplicationContextInitializedEvent",
                    "ApplicationPreparedEvent",
                    "ApplicationStartedEvent",
                    "ApplicationReadyEvent");

            // ApplicationStartingEvent - ConfigurableBootstrapContext뿐, Environment조차 없다.
            assertThat(find(observations, "ApplicationStartingEvent").environmentAvailable()).isFalse();

            // ApplicationEnvironmentPreparedEvent - Environment는 있지만 ApplicationContext는 아직 없다.
            assertThat(find(observations, "ApplicationEnvironmentPreparedEvent").environmentAvailable()).isTrue();
            assertThat(find(observations, "ApplicationEnvironmentPreparedEvent").contextAvailable()).isFalse();

            // ApplicationContextInitializedEvent - ApplicationContextInitializer까지 적용된
            // ApplicationContext는 있지만, @ComponentScan조차 아직 전개되지 않아서 MarkerBean의
            // BeanDefinition 자체가 없다.
            assertThat(find(observations, "ApplicationContextInitializedEvent").contextAvailable()).isTrue();
            assertThat(find(observations, "ApplicationContextInitializedEvent").beanLookupAvailable()).isFalse();

            // ApplicationPreparedEvent - primary source(LifecycleConfig)는 BeanDefinition으로
            // 등록됐지만, refresh()가 아직 호출되지 않아 @ComponentScan 전개도, 싱글턴 생성도
            // 이뤄지지 않았다.
            assertThat(find(observations, "ApplicationPreparedEvent").contextAvailable()).isTrue();
            assertThat(find(observations, "ApplicationPreparedEvent").beanLookupAvailable()).isFalse();

            // ApplicationStartedEvent/ApplicationReadyEvent - refresh()가 끝나서 빈 조회가 가능하다.
            assertThat(find(observations, "ApplicationStartedEvent").beanLookupAvailable()).isTrue();
            assertThat(find(observations, "ApplicationReadyEvent").beanLookupAvailable()).isTrue();
        } finally {
            context.close();
        }
    }

    @Test
    void beanRegisteredListenerMissesEventsPublishedBeforeItExists() {
        SpringApplication application = new SpringApplication(LifecycleConfig.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        ConfigurableApplicationContext context = application.run();
        try {
            List<String> observedByBean = BeanRegisteredObserver.eventNames();

            // 컴포넌트 스캔으로 등록된 빈은 refresh()가 끝나 자기 자신이 만들어진 뒤의
            // 이벤트만 받을 수 있다 - ApplicationContext 자체가 없던 시점의 이벤트는
            // 구조적으로 놓칠 수밖에 없다.
            assertThat(observedByBean).doesNotContain(
                    "ApplicationStartingEvent",
                    "ApplicationEnvironmentPreparedEvent",
                    "ApplicationContextInitializedEvent",
                    "ApplicationPreparedEvent");
            assertThat(observedByBean).contains("ApplicationStartedEvent", "ApplicationReadyEvent");
        } finally {
            context.close();
        }
    }
}
