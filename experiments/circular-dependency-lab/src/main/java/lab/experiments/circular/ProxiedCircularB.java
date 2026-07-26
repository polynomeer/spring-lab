package lab.experiments.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxiedCircularB {

    private ProxiedCircularA a;

    @Autowired
    public void setA(ProxiedCircularA a) {
        this.a = a;
    }

    public ProxiedCircularA getA() {
        return a;
    }
}
