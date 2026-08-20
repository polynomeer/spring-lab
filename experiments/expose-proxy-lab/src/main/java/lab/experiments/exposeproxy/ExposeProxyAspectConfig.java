package lab.experiments.exposeproxy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// exposeProxy = true 하나가 AnnotationAwareAspectJAutoProxyCreator(12번 문서)가 만드는
// 모든 프록시에 AdvisedSupport#exposeProxy = true를 설정해 준다 - @Transactional/@Cacheable
// 같은 다른 선언적 기능과 정확히 같은 자동 프록시 생성 경로를 그대로 탄다.
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
public class ExposeProxyAspectConfig {

    @Bean
    public LoggingAspect loggingAspect() {
        return new LoggingAspect();
    }

    @Bean
    public OrderService orderService() {
        return new OrderServiceImpl();
    }
}
