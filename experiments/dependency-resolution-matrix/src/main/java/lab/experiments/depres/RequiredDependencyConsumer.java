package lab.experiments.depres;

public class RequiredDependencyConsumer {

    private final Dependency dependency;

    public RequiredDependencyConsumer(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }
}
