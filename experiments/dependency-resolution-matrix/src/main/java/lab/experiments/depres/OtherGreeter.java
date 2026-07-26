package lab.experiments.depres;

import org.springframework.stereotype.Component;

// @Primary가 없는 두 번째 "일반" 구현체 - SecondaryGreeter와 짝지어 모호성(ambiguous) 실험에 쓴다.
@Component
public class OtherGreeter implements Greeter {

    @Override
    public String greet() {
        return "other";
    }
}
