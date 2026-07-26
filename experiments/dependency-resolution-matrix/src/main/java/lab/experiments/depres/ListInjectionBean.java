package lab.experiments.depres;

import java.util.List;

public class ListInjectionBean {

    private final List<Greeter> greeters;

    public ListInjectionBean(List<Greeter> greeters) {
        this.greeters = greeters;
    }

    public List<Greeter> getGreeters() {
        return greeters;
    }
}
