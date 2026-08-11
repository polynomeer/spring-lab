package lab.minispring.aop;

/** final 클래스는 애초에 서브클래싱 자체가 불가능하다 - CGLIB도, ByteBuddy도 프록시할 수 없다. */
public final class FinalService {

    public String greet() {
        return "hello from a final class";
    }
}
