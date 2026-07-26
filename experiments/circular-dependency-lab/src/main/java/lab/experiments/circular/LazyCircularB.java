package lab.experiments.circular;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

// 이쪽만 @Lazy를 붙인다. Spring이 A를 먼저 만들려고 하면: A의 생성자가 B를 요구 ->
// B의 생성자가 A를 요구하지만 @Lazy라 지연 프록시로 즉시 만족 -> B 완성 -> A도 완성.
// B를 먼저 만들려고 해도: A가 필요한 자리는 어차피 지연 프록시라 즉시 만족 -> B 완성 ->
// 이어서 A를 만들면 이번엔 진짜 B가 이미 있다. 어느 쪽이 먼저든 성공한다.
@Component
public class LazyCircularB {

    private final LazyCircularA a;

    public LazyCircularB(@Lazy LazyCircularA a) {
        this.a = a;
    }

    public LazyCircularA getA() {
        return a;
    }
}
