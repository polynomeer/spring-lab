package lab.sampleapp.orderplatform.plugin;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * SelfDescribingPlugin을 구현한 빈이라면 컴포넌트 스캔으로 등록됐든 @Bean으로
 * 등록됐든 상관없이 초기화 완료 시점(postProcessAfterInitialization - 즉
 * @PostConstruct/afterPropertiesSet이 이미 끝난 뒤)에 자동으로 PluginCatalog에
 * 등록한다.
 *
 * <p>주의: BeanPostProcessor 빈 자신의 생성자 의존성(PluginCatalog)은 컨테이너가
 * "일반 빈보다 먼저" 인스턴스화한다(BeanPostProcessor는 등록 자체를 위해 조기 생성돼야
 * 하므로) - PluginCatalog가 다른 무언가에 의존하고 있었다면 그 의존 대상까지 덩달아
 * 조기 생성되면서 AOP 프록시 적용 같은 후속 처리를 우회할 위험이 있다(Spring 레퍼런스
 * 문서가 명시적으로 경고하는 함정). 여기서는 PluginCatalog가 의존성이 없는 leaf라서
 * 안전하다.
 */
@Component
public class PluginRegistrationBeanPostProcessor implements BeanPostProcessor {

    private final PluginCatalog catalog;

    public PluginRegistrationBeanPostProcessor(PluginCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SelfDescribingPlugin plugin) {
            catalog.register(new PluginDescriptor(plugin.pluginKind(), beanName, plugin.pluginKey()));
        }
        return bean;
    }
}
