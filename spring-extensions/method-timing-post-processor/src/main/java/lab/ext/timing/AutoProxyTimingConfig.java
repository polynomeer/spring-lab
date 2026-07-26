package lab.ext.timing;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * project 9 4단계: {@link MethodTimingBeanPostProcessor}(수동으로 Advisor를 적용) 대신
 * {@code DefaultAdvisorAutoProxyCreator}(컨테이너의 모든 {@link Advisor} 빈을
 * {@code BeanFactoryAdvisorRetrievalHelper}로 찾아 대상 빈에 자동으로 적용하는 표준
 * {@code AbstractAdvisorAutoProxyCreator} 구현체)를 등록한다 - 우리가 직접 짠 "이 빈을
 * 감쌀지 판단하는 BeanPostProcessor" 코드가 통째로 사라지고, Advisor 빈 하나만 등록하면
 * 나머지는 프레임워크가 알아서 한다.
 */
@Configuration
@ComponentScan(basePackageClasses = AutoProxyTimingConfig.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TimingConfig.class))
public class AutoProxyTimingConfig {

    @Bean
    static Advisor timingAdvisor() {
        return new DefaultPointcutAdvisor(new MeasureTimeAnnotationPointcut(), new TimingMethodInterceptor());
    }

    @Bean
    static DefaultAdvisorAutoProxyCreator autoProxyCreator() {
        return new DefaultAdvisorAutoProxyCreator();
    }
}
