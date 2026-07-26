package lab.experiments.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// LoggingAspect의 어드바이스 대상 - 이 빈은 결국 AOP 프록시로 컨테이너에 등록된다.
@Component
public class ProxiedCircularA {

    private ProxiedCircularB b;

    @Autowired
    public void setB(ProxiedCircularB b) {
        this.b = b;
    }

    public ProxiedCircularB getB() {
        return b;
    }

    public String greet() {
        return "a";
    }
}
