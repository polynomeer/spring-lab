package lab.experiments.circular;

import org.springframework.stereotype.Component;

@Component
public class ConstructorCircularB {

    private final ConstructorCircularA a;

    public ConstructorCircularB(ConstructorCircularA a) {
        this.a = a;
    }

    public ConstructorCircularA getA() {
        return a;
    }
}
