package lab.experiments.objectprovider;

import org.springframework.core.annotation.Order;

@Order(10)
public class EnglishGreeter implements Greeter {

    @Override
    public String greet() {
        return "Hello";
    }
}
