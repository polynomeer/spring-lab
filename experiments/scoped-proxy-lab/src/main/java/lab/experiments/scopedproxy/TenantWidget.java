package lab.experiments.scopedproxy;

import java.util.concurrent.atomic.AtomicInteger;

// final이 아니어야 한다 - ScopedProxyMode.TARGET_CLASS는 CGLIB로 이 클래스를
// 상속(서브클래싱)해서 프록시를 만들기 때문에, 클래스 자체도 id() 메서드도 final이면
// 안 된다.
public class TenantWidget {

    private static final AtomicInteger sequence = new AtomicInteger();

    private final int id = sequence.incrementAndGet();

    public int id() {
        return id;
    }
}
