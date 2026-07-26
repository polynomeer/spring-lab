package lab.experiments.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SetterCircularB {

    private SetterCircularA a;

    @Autowired
    public void setA(SetterCircularA a) {
        this.a = a;
    }

    public SetterCircularA getA() {
        return a;
    }
}
