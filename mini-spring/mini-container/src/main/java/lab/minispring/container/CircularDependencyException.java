package lab.minispring.container;

public class CircularDependencyException extends RuntimeException {

    public CircularDependencyException(String creationPath) {
        super("Circular dependency detected: " + creationPath);
    }
}
