package lab.experiments.depres;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateSelectionTest {

    @Test
    void singleCandidateIsInjectedDirectly() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(SecondaryGreeter.class);
        context.registerBean(GreeterConsumer.class);
        context.refresh();

        assertThat(context.getBean(GreeterConsumer.class).getGreeter()).isInstanceOf(SecondaryGreeter.class);
        context.close();
    }

    @Test
    void noCandidateThrowsUnsatisfiedDependencyException() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        // Dependency 빈을 등록하지 않는다 - 단일 생성자라 항상 "선택"되지만 해석할 수는 없다.
        context.registerBean(RequiredDependencyConsumer.class);

        assertThatThrownBy(context::refresh).isInstanceOf(UnsatisfiedDependencyException.class);
        context.close();
    }

    @Test
    void multipleCandidatesWithoutDisambiguationThrows() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(SecondaryGreeter.class);
        context.registerBean(OtherGreeter.class); // 둘 다 @Primary 아님, 파라미터 이름도 안 맞음
        context.registerBean(GreeterConsumer.class);

        assertThatThrownBy(context::refresh).isInstanceOf(UnsatisfiedDependencyException.class);
        context.close();
    }

    @Test
    void primaryBreaksTheTieAmongMultipleCandidates() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(PrimaryGreeter.class);
        context.registerBean(SecondaryGreeter.class);
        context.registerBean(GreeterConsumer.class);
        context.refresh();

        assertThat(context.getBean(GreeterConsumer.class).getGreeter().greet()).isEqualTo("primary");
        context.close();
    }

    @Test
    void qualifierBreaksTheTieEvenWithoutPrimary() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(SecondaryGreeter.class);
        context.registerBean(OtherGreeter.class);
        context.registerBean(QualifiedGreeterConsumer.class);
        context.refresh();

        assertThat(context.getBean(QualifiedGreeterConsumer.class).getGreeter().greet()).isEqualTo("secondary");
        context.close();
    }

    @Test
    void parameterNameMatchingBeanNameBreaksTheTieWithoutPrimaryOrQualifier() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(SecondaryGreeter.class); // 기본 이름 "secondaryGreeter"
        context.registerBean(OtherGreeter.class);
        context.registerBean(NameMatchedGreeterConsumer.class); // 생성자 파라미터 이름도 secondaryGreeter
        context.refresh();

        assertThat(context.getBean(NameMatchedGreeterConsumer.class).getGreeter().greet()).isEqualTo("secondary");
        context.close();
    }
}
