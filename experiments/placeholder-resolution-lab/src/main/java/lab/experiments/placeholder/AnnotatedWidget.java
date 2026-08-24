package lab.experiments.placeholder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Widget과 대비되는 "현대적인" 주입 방식 - ${...}가 BeanDefinition의 프로퍼티
// 값이 아니라 애너테이션 속성(컴파일 타임 상수, 절대 변경 불가능) 안에 박혀 있다.
@Component
public class AnnotatedWidget {

    @Value("${greeting}")
    private String label;

    public String getLabel() {
        return label;
    }
}
