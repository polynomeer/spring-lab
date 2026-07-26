package lab.experiments.depres;

import java.util.Optional;

public class OptionalInjectionBean {

    private final Optional<Dependency> dependency;

    public OptionalInjectionBean(Optional<Dependency> dependency) {
        this.dependency = dependency;
    }

    public Optional<Dependency> getDependency() {
        return dependency;
    }
}
