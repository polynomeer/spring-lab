package lab.experiments.proxy;

public class GreetableImpl implements Greetable {

    private String lastGreeted;

    @Override
    public String greet(String name) {
        this.lastGreeted = name;
        return "Hello, " + name;
    }

    public String getLastGreeted() {
        return lastGreeted;
    }
}
