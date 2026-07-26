package lab.experiments.depres;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class PrimaryGreeter implements Greeter {

    @Override
    public String greet() {
        return "primary";
    }
}
