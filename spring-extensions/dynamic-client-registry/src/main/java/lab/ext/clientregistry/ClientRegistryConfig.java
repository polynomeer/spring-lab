package lab.ext.clientregistry;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = ClientRegistryConfig.class)
public class ClientRegistryConfig {
}
