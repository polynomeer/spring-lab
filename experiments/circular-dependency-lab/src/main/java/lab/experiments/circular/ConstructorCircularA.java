package lab.experiments.circular;

import org.springframework.stereotype.Component;

@Component
public class ConstructorCircularA {

    private final ConstructorCircularB b;

    public ConstructorCircularA(ConstructorCircularB b) {
        this.b = b;
    }

    public ConstructorCircularB getB() {
        return b;
    }
}
