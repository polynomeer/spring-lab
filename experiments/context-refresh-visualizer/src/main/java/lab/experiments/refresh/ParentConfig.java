package lab.experiments.refresh;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParentConfig {

    @Bean
    public ParentOnlyBean parentOnlyBean() {
        return new ParentOnlyBean();
    }

    @Bean
    public SharedBean shared() {
        return new SharedBean("parent");
    }
}
