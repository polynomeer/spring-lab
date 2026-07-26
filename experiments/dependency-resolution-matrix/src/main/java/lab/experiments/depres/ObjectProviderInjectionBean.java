package lab.experiments.depres;

import org.springframework.beans.factory.ObjectProvider;

public class ObjectProviderInjectionBean {

    private final ObjectProvider<Dependency> dependencyProvider;

    public ObjectProviderInjectionBean(ObjectProvider<Dependency> dependencyProvider) {
        this.dependencyProvider = dependencyProvider;
    }

    public ObjectProvider<Dependency> getDependencyProvider() {
        return dependencyProvider;
    }
}
