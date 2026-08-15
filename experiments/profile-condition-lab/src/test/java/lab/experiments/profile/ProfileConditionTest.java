package lab.experiments.profile;

import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileConditionTest {

    @Test
    void noActiveProfilesFallsBackToTheDefaultProfileOnly() {
        withProfiles(new String[0], descriptions ->
                assertThat(descriptions).containsExactlyInAnyOrder("default", "not-prod", "always"));
    }

    @Test
    void activatingDevDeactivatesTheDefaultProfileEvenThoughDevIsUnrelatedToProd() {
        withProfiles(new String[] {"dev"}, descriptions -> {
            // 활성 프로파일이 하나라도 있으면(설령 그게 "default"와 무관한 "dev"라도)
            // AbstractEnvironment#isProfileActive()의 "activeProfiles가 비어 있을 때만
            // defaultProfiles를 본다"는 조건 자체가 더 이상 성립하지 않는다 - default 빈은
            // 완전히 사라진다.
            assertThat(descriptions).containsExactlyInAnyOrder("dev", "not-prod", "always");
        });
    }

    @Test
    void compoundAndExpressionRequiresBothProfilesActive() {
        withProfiles(new String[] {"prod", "cloud"}, descriptions ->
                assertThat(descriptions).containsExactlyInAnyOrder("prod", "prod-cloud", "always"));
    }

    @Test
    void prodAloneWithoutCloudDoesNotSatisfyTheCompoundExpression() {
        withProfiles(new String[] {"prod"}, descriptions -> {
            assertThat(descriptions).contains("prod");
            // "prod & cloud"는 cloud가 없으면 통과하지 않는다 - AND는 부분 일치를 허용하지 않는다.
            assertThat(descriptions).doesNotContain("prod-cloud");
            // prod가 활성이므로 "!prod"는 거짓 - not-prod 빈은 등록되지 않는다.
            assertThat(descriptions).doesNotContain("not-prod");
        });
    }

    @Test
    void beanWithoutAtProfileIsAlwaysPresentRegardlessOfActiveProfiles() {
        withProfiles(new String[0], descriptions -> assertThat(descriptions).contains("always"));
        withProfiles(new String[] {"dev"}, descriptions -> assertThat(descriptions).contains("always"));
        withProfiles(new String[] {"prod", "cloud"}, descriptions -> assertThat(descriptions).contains("always"));
    }

    @Test
    void filteredOutBeansAreAbsentFromTheBeanDefinitionRegistryNotJustUnresolved() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.register(ProfileScanConfig.class);
            context.refresh();

            // ProfileCondition은 빈 정의 등록 시점(7주차의 컴포넌트 스캔 파이프라인)에
            // 걸린다 - 조건에 안 맞는 클래스는 BeanDefinition 자체가 아예 존재하지 않는다.
            // "빈은 있는데 조건 때문에 조회가 안 되는 것"이 아니다.
            assertThat(context.containsBeanDefinition("prodNotifier")).isFalse();
            assertThat(context.getBeanDefinitionNames()).doesNotContain("prodNotifier");
        }
    }

    private void withProfiles(String[] activeProfiles, Consumer<Set<String>> assertion) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(activeProfiles);
            context.register(ProfileScanConfig.class);
            context.refresh();

            Set<String> descriptions = context.getBeansOfType(Notifier.class).values().stream()
                    .map(Notifier::describe)
                    .collect(java.util.stream.Collectors.toSet());
            assertion.accept(descriptions);
        }
    }
}
