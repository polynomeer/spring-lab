package lab.experiments.lookup;

import org.springframework.stereotype.Component;

// 흔히 겪는 실수 - 프로토타입 빈을 생성자로 "한 번" 주입받으면, 싱글턴인 이 빈 자신은
// 컨테이너 시작 시점에 딱 한 번만 만들어지므로 그 프로토타입도 딱 한 번만 주입된다.
// 이후 sell()을 몇 번을 불러도 같은 Ticket 인스턴스를 계속 돌려준다 - LookupTicketSeller와
// 대조군.
@Component
public class NaiveTicketSeller {

    private final Ticket injectedOnce;

    public NaiveTicketSeller(Ticket injectedOnce) {
        this.injectedOnce = injectedOnce;
    }

    public Ticket sell() {
        return injectedOnce;
    }
}
