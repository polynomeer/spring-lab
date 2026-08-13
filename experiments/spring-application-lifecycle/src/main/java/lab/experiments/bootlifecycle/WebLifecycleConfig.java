package lab.experiments.bootlifecycle;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

// LifecycleConfig(WebApplicationType.NONE 실험)와 별도로 둔 이유: @EnableAutoConfiguration은
// spring-boot-autoconfigure/내장 톰캣이 필요한데, 원래 실험은 그 의존성이 필요 없었다. 이
// 클래스만 실제 웹 자동 설정(DispatcherServletAutoConfiguration, 내장 톰캣 등)을 전부 켠다.
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = LifecycleConfig.class)
public class WebLifecycleConfig {
}
