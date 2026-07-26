package lab.experiments.circular;

import org.springframework.stereotype.Component;

// A -> B는 그냥 평범한(eager) 생성자 의존성이다 - @Lazy는 B -> A 한쪽에만 있으면 순환을
// 끊기에 충분하다(어느 쪽이 먼저 생성되든 마찬가지다. LazyCircularB.java 주석 참고).
@Component
public class LazyCircularA {

    private final LazyCircularB b;

    public LazyCircularA(LazyCircularB b) {
        this.b = b;
    }

    public LazyCircularB getB() {
        return b;
    }
}
