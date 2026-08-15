package lab.experiments.importselector;

import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;

// 그냥 @Import된 @Configuration 클래스는 자신을 가져온 애노테이션의 속성값을 알 방법이
// 없다 - 생성자에도, @Bean 메서드 파라미터에도 그게 자동으로 흘러들어오지 않는다. ImportAware를
// 구현해야만 ConfigurationClassPostProcessor의 내부 BeanPostProcessor(ImportAwareBeanPostProcessor)가
// 초기화 이후 별도로 setImportMetadata()를 호출해서 그 정보를 넘겨준다.
@Configuration
public class GreetingSettingsHolder implements ImportAware {

    private String prefix;

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> attributes = importMetadata.getAnnotationAttributes(ConfiguresGreeting.class.getName());
        this.prefix = (String) attributes.get("prefix");
    }

    public String prefix() {
        return prefix;
    }
}
