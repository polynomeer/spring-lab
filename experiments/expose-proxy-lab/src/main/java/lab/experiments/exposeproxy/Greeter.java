package lab.experiments.exposeproxy;

public interface Greeter {

    String greet();

    String greetViaPlainSelfInvocation();

    String greetViaAopContext();
}
