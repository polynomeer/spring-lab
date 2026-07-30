package lab.experiments.autoconfig;

public class DefaultGreetingService implements GreetingService {

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
