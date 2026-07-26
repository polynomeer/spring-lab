package lab.experiments.proxy;

public class SelfInvokingGreeter implements Greetable {

    @Override
    public String greet(String name) {
        return "Outer: " + greetInner(name);
    }

    // greet()이 프록시가 아니라 this를 통해 이 메서드를 직접 호출한다 - 프록시 바깥에서
    // greetInner()를 직접 부르는 것과 결과를 비교해서 self-invocation이 어드바이스를
    // 건너뛴다는 것을 확인하는 용도다.
    public String greetInner(String name) {
        return "Inner: " + name;
    }
}
