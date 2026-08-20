package lab.experiments.smartinit;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SmartInitializingSingletonTest {

    @Test
    void afterSingletonsInstantiatedFiresForTheEagerSingletonThatImplementsIt() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SmartInitConfig.class)) {
            RecordingLifecycleLog log = context.getBean(RecordingLifecycleLog.class);

            assertThat(log.events()).anyMatch(e -> e.startsWith("BeanA.afterSingletonsInstantiated"));
        }
    }

    @Test
    void byTheTimeItFiresEveryOtherEagerSingletonIsAlreadyFullyConstructed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SmartInitConfig.class)) {
            RecordingLifecycleLog log = context.getBean(RecordingLifecycleLog.class);

            // BeanA는 beanB보다 먼저 선언됐지만(SmartInitConfig 참고), afterSingletonsInstantiated()
            // 시점에는 BeanB의 @PostConstruct가 이미 끝나 있다.
            assertThat(log.events())
                    .anyMatch(e -> e.equals("BeanA.afterSingletonsInstantiated sees BeanB.postConstructDone=true"));
        }
    }

    @Test
    void itFiresBeforeContextRefreshedEventIsPublished() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SmartInitConfig.class)) {
            RecordingLifecycleLog log = context.getBean(RecordingLifecycleLog.class);
            List<String> events = log.events();

            // finishBeanFactoryInitialization()(11번째 단계)이 preInstantiateSingletons()를
            // 호출하는 자리이고, ContextRefreshedEvent는 그다음 finishRefresh()(12번째,
            // 마지막 단계)에서 발행된다 - afterSingletonsInstantiated()가 항상 먼저다.
            int smartInitIndex = indexOfFirstStartingWith(events, "BeanA.afterSingletonsInstantiated");
            int refreshedEventIndex = events.indexOf("ContextRefreshedEvent");

            assertThat(smartInitIndex).isGreaterThanOrEqualTo(0);
            assertThat(refreshedEventIndex).isGreaterThanOrEqualTo(0);
            assertThat(smartInitIndex).isLessThan(refreshedEventIndex);
        }
    }

    @Test
    void aLazySingletonsCallbackNeverFiresAutomaticallyEvenAfterItIsLazilyCreatedLater() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SmartInitConfig.class)) {
            RecordingLifecycleLog log = context.getBean(RecordingLifecycleLog.class);

            // @Lazy라서 컨텍스트 시작 시점에는 생성조차 안 됐다.
            assertThat(log.events()).noneMatch(e -> e.contains("LazySmartBean"));

            // 지금 강제로 생성해도...
            context.getBean(LazySmartBean.class);
            assertThat(log.events()).anyMatch(e -> e.equals("LazySmartBean.constructor"));

            // afterSingletonsInstantiated()는 여전히 호출되지 않는다 - 그 특별한 두 번째
            // 순회는 refresh() 안에서 딱 한 번만 실행되고 이미 끝났다. 늦게 만들어진 빈은
            // 그 순회 대상 목록에 애초에 없었다.
            assertThat(log.events()).noneMatch(e -> e.contains("LazySmartBean.afterSingletonsInstantiated"));
        }
    }

    private int indexOfFirstStartingWith(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
