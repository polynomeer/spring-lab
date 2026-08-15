package lab.experiments.importselector;

import java.util.Map;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

// ImportSelector와 달리 중간에 또 다른 @Configuration 클래스를 거치지 않는다 - 이 메서드
// 안에서 BeanDefinitionRegistry에 직접 빈을 등록한다. repeatCount() 값만큼 빈을 "동적으로"
// 만들어 낼 수 있다는 것이 핵심 - @Configuration + @Bean 메서드로는 애초에 표현할 수 없는
// (메서드 개수가 컴파일 타임에 고정되므로) 유연성이다.
public class RepeatedGreetingRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes =
                importingClassMetadata.getAnnotationAttributes(EnableRepeatedGreeting.class.getName());
        int repeatCount = (int) attributes.get("repeatCount");
        String message = (String) attributes.get("message");

        for (int i = 0; i < repeatCount; i++) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(Greeting.class)
                    .addConstructorArgValue(message + "-" + i);
            registry.registerBeanDefinition("greeting" + i, builder.getBeanDefinition());
        }
    }
}
