package lab.experiments.lookup;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class LookupMethodTest {

    @Test
    void naiveConstructorInjectionFreezesTheSamePrototypeInstance() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LookupConfig.class)) {
            NaiveTicketSeller seller = context.getBean(NaiveTicketSeller.class);

            int first = seller.sell().id();
            int second = seller.sell().id();

            // NaiveTicketSeller 자신이 싱글턴이라 생성자는 딱 한 번만 실행된다 - 그때 주입된
            // 프로토타입 Ticket 인스턴스가 이후 모든 호출에서 그대로 재사용된다.
            assertThat(first).isEqualTo(second);
        }
    }

    @Test
    void lookupMethodReturnsAFreshPrototypeInstanceEachCall() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LookupConfig.class)) {
            LookupTicketSeller seller = context.getBean(LookupTicketSeller.class);

            int first = seller.nextTicket().id();
            int second = seller.nextTicket().id();

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Test
    void theBeanItselfIsACglibGeneratedSubclassOfTheAbstractComponent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LookupConfig.class)) {
            LookupTicketSeller seller = context.getBean(LookupTicketSeller.class);

            // 별도 프록시 객체가 아니라, 빈 "자신"이 LookupTicketSeller를 상속한 CGLIB
            // 생성 서브클래스다.
            assertThat(seller.getClass()).isNotEqualTo(LookupTicketSeller.class);
            assertThat(seller.getClass().getSuperclass()).isEqualTo(LookupTicketSeller.class);
        }
    }

    @Test
    void selfInvocationStillGetsTheOverriddenLookupBehavior() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LookupConfig.class)) {
            LookupTicketSeller seller = context.getBean(LookupTicketSeller.class);

            int first = seller.sellViaSelfInvocation().id();
            int second = seller.sellViaSelfInvocation().id();

            // 25·26·29번의 AOP 프록시였다면 this.nextTicket()이 프록시를 거치지 않아
            // 우회됐을 것이다 - 여기서는 this 자신이 이미 오버라이드된 서브클래스이므로
            // self-invocation이 전혀 문제가 되지 않는다.
            assertThat(first).isNotEqualTo(second);
        }
    }

    @Test
    void abstractComponentWithoutALookupMethodIsNeverRegisteredAsABean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LookupConfig.class)) {
            Map<String, PlainAbstractNoLookup> beans = context.getBeansOfType(PlainAbstractNoLookup.class);

            // ClassPathScanningCandidateComponentProvider#isCandidateComponent()는
            // "추상 클래스 && @Lookup 메서드가 있음"을 명시적으로 요구한다 - 이 클래스는
            // 추상이지만 @Lookup이 없으므로 후보에서 아예 제외된다.
            assertThat(beans).isEmpty();
        }
    }

    @Test
    void lookupOnAFinalMethodIsSilentlyIgnoredSinceCglibCannotOverrideIt() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LookupConfig.class)) {
            FinalMethodTicketSeller seller = context.getBean(FinalMethodTicketSeller.class);

            // final 메서드는 자바 언어 규칙상 서브클래스가 오버라이드할 수 없다 - CGLIB도
            // 예외가 아니다. LookupOverride 자체는 등록되지만 실제로 적용되지 않아서, 원래
            // 메서드 본문(null 반환)이 그대로 실행된다.
            assertThat(seller.nextTicket()).isNull();
        }
    }
}
