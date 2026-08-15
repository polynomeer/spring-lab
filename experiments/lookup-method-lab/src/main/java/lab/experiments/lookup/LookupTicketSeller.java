package lab.experiments.lookup;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

// 추상 클래스인데도 컴포넌트 스캔 후보가 될 수 있다 - @Lookup 메서드를 하나라도 갖고
// 있으면(ClassPathScanningCandidateComponentProvider#isCandidateComponent의 특별 규칙,
// 문서 9번 절 참고) 그 자체로 유효한 빈 후보로 인정된다. 이 추상 메서드는 Spring이
// 빈을 만드는 시점에 CGLIB로 이 클래스를 상속한 서브클래스를 만들어서 직접 오버라이드한다
// - 나중에 별도 프록시로 감싸는 게 아니라, 이 객체 "자신"이 그 오버라이드된 서브클래스다.
@Component
public abstract class LookupTicketSeller {

    @Lookup
    public abstract Ticket nextTicket();

    public Ticket sellViaSelfInvocation() {
        // this.nextTicket()은 AOP 프록시(25·26·29번)와 달리 "감싸는 별도 객체"를 거치지
        // 않는다 - this 자신이 이미 오버라이드된 CGLIB 서브클래스 인스턴스이므로,
        // self-invocation도 정상적으로 오버라이드된 동작(매번 새 프로토타입 조회)을 탄다.
        return this.nextTicket();
    }
}
