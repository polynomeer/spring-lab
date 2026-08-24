package lab.experiments.placeholder;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@Configuration
public class PlaceholderLabConfig {

    @Bean
    public List<String> log() {
        return new ArrayList<>();
    }

    // BeanFactoryPostProcessor이므로 static이어야 다른 @Configuration 클래스보다
    // 먼저(그 자신을 인스턴스화하려고 컨테이너 전체를 조기 초기화하지 않고) 등록된다.
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public static EarlyInspectorBfpp earlyInspectorBfpp(List<String> log) {
        return new EarlyInspectorBfpp(log);
    }

    @Bean
    public static LateInspectorBfpp lateInspectorBfpp(List<String> log) {
        return new LateInspectorBfpp(log);
    }
}
