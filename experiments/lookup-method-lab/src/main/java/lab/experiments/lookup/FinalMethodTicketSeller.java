package lab.experiments.lookup;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

// @Lookup을 final 메서드에 붙이면 어떻게 되는가 - CGLIB는 서브클래싱으로 동작하므로 자바
// 언어 규칙상 final 메서드는애초에 오버라이드할 수 없다. 이 몸체가 실제로 실행된다면(즉
// null이 반환된다면) @Lookup이 조용히 무시된 것이다 - 실행해서 확인한다.
@Component
public class FinalMethodTicketSeller {

    @Lookup
    public final Ticket nextTicket() {
        return null;
    }
}
