package lab.experiments.composedannotation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import lab.experiments.composedannotation.scanned.LoggableService;

import static org.assertj.core.api.Assertions.assertThat;

class ComposedAnnotationTest {

    /**
     * LoggableService에는 @Component가 직접 붙어 있지 않다 - 오직 @Loggable(메타
     * 애노테이션으로 @Component를 붙인 합성 애노테이션)만 있다. 평범한 리플렉션
     * (getAnnotation)은 이 메타 애노테이션 관계를 전혀 모르지만, Spring 내부(컴포넌트
     * 스캔의 AnnotationTypeFilter 등)가 실제로 쓰는 AnnotatedElementUtils는 이
     * 관계를 재귀적으로 따라가 @Component를 "합성"해서 돌려준다.
     */
    @Test
    void plainReflectionMissesMetaAnnotationButAnnotatedElementUtilsSynthesizesIt() {
        assertThat(LoggableService.class.getAnnotation(Component.class)).isNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(LoggableService.class, Component.class))
                .isNotNull();
    }

    /**
     * @Loggable만 붙은 클래스가 실제로 컴포넌트 스캔에 걸려 빈으로 등록된다 - 위
     * 테스트가 보여준 "합성"이 단순히 리플렉션 API의 눈속임이 아니라, 컴포넌트
     * 스캔의 실제 판정 로직에도 그대로 반영된다는 것을 확인한다.
     */
    @Test
    void classAnnotatedOnlyWithComposedStereotypeIsPickedUpByComponentScan() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CarConfig.class)) {
            assertThat(context.containsBean("loggableService")).isTrue();
        }
    }

    /**
     * @Fast의 value 속성은 @AliasFor(annotation = Qualifier.class, attribute = "value")로
     * @Qualifier.value()에 연결돼 있다. AnnotatedElementUtils로 합성한 @Qualifier를
     * 보면, 기본값("fast-lane")이든 명시적으로 준 값("turbo")이든 정확히 그대로
     * 전달된다는 걸 확인할 수 있다.
     */
    @Test
    void aliasForTransfersBothDefaultAndExplicitValuesToTheMetaAnnotation() throws NoSuchMethodException {
        Qualifier defaultQualifier = AnnotatedElementUtils.findMergedAnnotation(
                CarConfig.class.getDeclaredMethod("defaultFastEngine"), Qualifier.class);
        Qualifier explicitQualifier = AnnotatedElementUtils.findMergedAnnotation(
                CarConfig.class.getDeclaredMethod("turboEngine"), Qualifier.class);

        assertThat(defaultQualifier.value()).isEqualTo("fast-lane");
        assertThat(explicitQualifier.value()).isEqualTo("turbo");
    }

    /**
     * 앞의 "합성"이 리플렉션 차원의 관찰에 그치지 않고, 실제 의존성 해석
     * (QualifierAnnotationAutowireCandidateResolver)에도 그대로 반영된다는 것을
     * 최종적으로 확인한다 - 같은 타입(Engine) 빈이 둘 있는데도 @Fast("turbo")
     * 하나만으로 정확히 turboEngine이 선택된다.
     */
    @Test
    void aliasedQualifierValueActuallyDrivesAutowiringCandidateSelection() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CarConfig.class)) {
            assertThat(context.getBean(EngineConsumer.class).engine().label()).isEqualTo("turbo");
        }
    }
}
