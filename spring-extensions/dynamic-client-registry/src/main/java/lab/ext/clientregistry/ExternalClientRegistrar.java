package lab.ext.clientregistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

/**
 * 카탈로그 프로젝트 4 - 설정 파일(YAML)에 나열된 항목 수만큼 {@link ExternalApiClient} 빈을
 * 동적으로 등록한다. 컴파일 시점엔 몇 개가 등록될지, 이름이 뭔지조차 모른다 - 이 클래스가
 * {@code postProcessBeanDefinitionRegistry()} 안에서 등록소(registry)에 직접
 * {@link BeanDefinition}을 만들어 넣기 때문이다. Spring Boot의 자동 설정도 근본적으로
 * 같은 지점(더 정교한 조건 평가가 얹혀 있을 뿐)에서 같은 일을 한다.
 */
public class ExternalClientRegistrar implements BeanDefinitionRegistryPostProcessor {

    private final String configLocation;
    private final int role;

    public ExternalClientRegistrar(String configLocation) {
        this(configLocation, BeanDefinition.ROLE_APPLICATION);
    }

    public ExternalClientRegistrar(String configLocation, int role) {
        this.configLocation = configLocation;
        this.role = role;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        Resource resource = new ClassPathResource(configLocation);
        if (!resource.exists()) {
            // 설정 파일이 아예 없으면 조용히 아무것도 등록하지 않는다 - 이 랩이 다루는 클라이언트
            // 목록은 선택 사항이라는 전제라, 여기서 예외를 던지면 오히려 "설정이 없어도 컨텍스트는
            // 정상적으로 뜬다"는 걸 확인하려는 실험 자체가 불가능해진다.
            return;
        }

        Map<String, Object> root = load(resource);
        Object rawClients = root == null ? null : root.get("external-clients");
        if (!(rawClients instanceof List<?> clients)) {
            return;
        }

        for (Object rawClient : clients) {
            Map<String, Object> client = (Map<String, Object>) rawClient;
            String name = (String) client.get("name");
            String baseUrl = (String) client.get("base-url");
            int timeoutMillis = (Integer) client.get("timeout");

            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ExternalApiClient.class)
                    .addConstructorArgValue(name)
                    .addConstructorArgValue(baseUrl)
                    .addConstructorArgValue(timeoutMillis);
            BeanDefinition definition = builder.getBeanDefinition();
            definition.setRole(role);

            // registerBeanDefinition은 같은 이름이 이미 있으면 기본적으로 "조용히 덮어쓴다" -
            // 예외를 던지지 않는다(allowBeanDefinitionOverriding이 기본값 true인 한). 설정 파일에
            // 같은 name이 중복되면 나중 항목이 이긴다는 뜻이다 - 05-beanfactory-postprocessor
            // 문서 14번 절에서 이 경계 조건을 직접 확인한다.
            registry.registerBeanDefinition(name, definition);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 등록소 구조 자체는 이미 위에서 다 끝났다 - 여기서는 할 일이 없다.
    }

    private Map<String, Object> load(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + configLocation, e);
        }
    }
}
