package lab.experiments.objectprovider;

import org.springframework.core.annotation.Order;

@Order(20)
public class KoreanGreeter implements Greeter {

    @Override
    public String greet() {
        return "안녕하세요";
    }
}
