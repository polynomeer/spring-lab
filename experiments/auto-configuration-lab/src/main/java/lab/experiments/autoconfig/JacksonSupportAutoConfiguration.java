package lab.experiments.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

// 이 모듈의 main 소스셋에는 Jackson이 없다 - name 속성으로 클래스 이름을 문자열로만 주면
// 컴파일 의존성 없이도(즉 클래스가 아예 없어도 컴파일은 되고) 런타임에 클래스패스 존재
// 여부만으로 조건을 평가할 수 있다. 실제 Spring Boot의 여러 자동 설정이 "있으면 켜고 없으면
// 조용히 꺼지는" 선택적 연동에 이 패턴을 쓴다.
@AutoConfiguration
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
public class JacksonSupportAutoConfiguration {

    @Bean
    public JacksonProbe jacksonProbe() {
        return new JacksonProbe();
    }
}
