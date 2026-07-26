package lab.experiments.depres;

import org.springframework.stereotype.Component;

@Component
public class SecondaryGreeter implements Greeter {

    @Override
    public String greet() {
        return "secondary";
    }
}
