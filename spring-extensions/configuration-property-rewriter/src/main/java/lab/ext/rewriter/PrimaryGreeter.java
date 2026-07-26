package lab.ext.rewriter;

import org.springframework.stereotype.Component;

@ForcePrimary
@Component
public class PrimaryGreeter implements Greeter {

    @Override
    public String greet() {
        return "primary";
    }
}
