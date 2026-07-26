package lab.ext.rewriter;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = RewriterConfig.class)
public class RewriterConfig {
}
