package lab.experiments.aspectordering;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class AspectOrderingTest {

    /**
     * AspectOrderingConfig는 @Bean 메서드를 order 값과 어긋나는 순서(C, A, B)로
     * 선언해 뒀다 - 그런데도 실행 순서가 order 값(A=1 < B=2 < C=3)을 정확히 따른다면,
     * 그건 등록/선언 순서가 아니라 order 값 자체가 advisor 정렬을 결정한다는 증거다.
     * 그리고 그 순서는 단순 나열이 아니라 "양파 껍질"처럼 중첩된다: order 값이 가장
     * 작은(=우선순위가 가장 높은) 어드바이스가 가장 바깥쪽을 감싸서, 진입("before")은
     * 가장 먼저, 빠져나옴("after")은 가장 나중에 일어난다.
     */
    @Test
    void lowestOrderValueBecomesTheOutermostAdviceRegardlessOfBeanDeclarationOrder() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AspectOrderingConfig.class)) {
            context.getBean(Greeter.class).greet();

            @SuppressWarnings("unchecked")
            List<String> log = context.getBean("log", List.class);

            assertThat(log).containsExactly(
                    "A-before", "B-before", "C-before",
                    "C-after", "B-after", "A-after");
        }
    }

    /**
     * 완전히 같은 세 어드바이스에 order 값만 뒤집어(A=3, B=2, C=1) 등록하면, 중첩
     * 순서도 그대로 뒤집힌다 - 앞 테스트의 결과가 우연이 아니라 order 값에 의해
     * 결정론적으로 통제된다는 것을 다시 확인한다.
     */
    @Test
    void reversingOrderValuesReversesTheNestingCompletely() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ReversedAspectOrderingConfig.class)) {
            context.getBean(Greeter.class).greet();

            @SuppressWarnings("unchecked")
            List<String> log = context.getBean("log", List.class);

            assertThat(log).containsExactly(
                    "C-before", "B-before", "A-before",
                    "A-after", "B-after", "C-after");
        }
    }
}
