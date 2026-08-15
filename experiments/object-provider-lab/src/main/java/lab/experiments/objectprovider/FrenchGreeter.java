package lab.experiments.objectprovider;

import org.springframework.core.annotation.Order;

@Order(30)
public class FrenchGreeter implements Greeter {

    @Override
    public String greet() {
        return "Bonjour";
    }
}
