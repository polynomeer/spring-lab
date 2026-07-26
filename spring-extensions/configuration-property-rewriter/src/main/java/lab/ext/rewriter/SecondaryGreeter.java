package lab.ext.rewriter;

import org.springframework.stereotype.Component;

@Component
public class SecondaryGreeter implements Greeter {

    @Override
    public String greet() {
        return "secondary";
    }
}
