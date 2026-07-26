package lab.experiments.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SetterCircularA {

    private SetterCircularB b;

    @Autowired
    public void setB(SetterCircularB b) {
        this.b = b;
    }

    public SetterCircularB getB() {
        return b;
    }
}
