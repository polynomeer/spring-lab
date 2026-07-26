package lab.ext.timing;

import org.springframework.stereotype.Component;

// 인터페이스가 없는 클래스 - @MeasureTime이 붙어 있어도 JDK 동적 프록시로는 감쌀 수 없다는
// 한계를 보여주기 위한 대상 (project 9의 2단계, Spring ProxyFactory/CGLIB에서 해결될 문제).
@Component
public class LegacyReport {

    @MeasureTime
    public void generate() {
    }
}
