package lab.ext.timing;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

// AutoProxyTimingConfig와 같은 패키지를 스캔하므로, 그쪽의 @Configuration(그 자체도
// @Component)까지 여기 컴포넌트 스캔에 같이 딸려 들어와 두 설정이 한 컨텍스트에서
// 섞이는 것을 막기 위해 명시적으로 제외한다.
@Configuration
@ComponentScan(basePackageClasses = TimingConfig.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AutoProxyTimingConfig.class))
public class TimingConfig {

    // BeanPostProcessor(및 그 의존성)는 컨테이너 초기화 아주 이른 시점에 필요하다 - 인스턴스
    // 팩토리 메서드로 선언하면 이 @Configuration 빈 자체가 먼저 완전히 만들어져야 해서 그
    // 타이밍을 놓친다(Spring이 콘솔에 남기는 경고와 동일한 이유). static으로 선언해 이 문제를
    // 피한다.
    @Bean
    static Advisor timingAdvisor() {
        return new DefaultPointcutAdvisor(new MeasureTimeAnnotationPointcut(), new TimingMethodInterceptor());
    }

    @Bean
    static MethodTimingBeanPostProcessor methodTimingBeanPostProcessor(Advisor timingAdvisor) {
        return new MethodTimingBeanPostProcessor(timingAdvisor);
    }
}
