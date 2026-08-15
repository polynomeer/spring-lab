package lab.experiments.propertysource;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class PropertySourceOrderingTest {

    @Test
    void standardEnvironmentPutsSystemPropertiesBeforeSystemEnvironmentVariables() {
        // StandardEnvironment#customizePropertySources()가 systemProperties를 먼저
        // addLast()하고, systemEnvironment를 그다음에 addLast()한다 - 처음이 비어 있는
        // 목록에 addLast를 두 번 하면 먼저 추가한 쪽이 앞자리를 차지한다.
        // PropertySourcesPropertyResolver는 목록을 순서대로 훑어 첫 매치를 채택하므로,
        // 이 순서 자체가 곧 우선순위다.
        StandardEnvironment environment = new StandardEnvironment();

        List<String> names = environment.getPropertySources().stream()
                .map(PropertySource::getName)
                .toList();

        assertThat(names).containsExactly(
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    }

    @Test
    void addFirstWinsOverAddLastRegardlessOfInsertionOrder() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();

        sources.addLast(new MapPropertySource("low", Map.of("greeting", "from-low")));
        sources.addFirst(new MapPropertySource("high", Map.of("greeting", "from-high")));

        // 삽입 순서(low를 먼저 넣었다)가 아니라, 목록 안에서의 "위치"가 우선순위를 결정한다.
        assertThat(environment.getProperty("greeting")).isEqualTo("from-high");
    }

    @Test
    void reversingThePositionsFlipsWhichValueWins() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();

        sources.addFirst(new MapPropertySource("a", Map.of("greeting", "from-a")));
        sources.addLast(new MapPropertySource("b", Map.of("greeting", "from-b")));
        assertThat(environment.getProperty("greeting")).isEqualTo("from-a");

        // 같은 두 PropertySource를 그대로 두고 순서만 바꾼다 - 값이 바뀐 게 아니라
        // "누가 먼저 조회되는가"만 바뀌었는데도 결과가 뒤집힌다.
        sources.remove("a");
        sources.addLast(new MapPropertySource("a", Map.of("greeting", "from-a")));
        assertThat(environment.getProperty("greeting")).isEqualTo("from-b");
    }

    @Test
    void addBeforePositionsRelativeToAnExistingNamedSource() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addLast(new MapPropertySource("anchor", Map.of("greeting", "from-anchor")));

        // "anchor" 바로 앞에 꽂는다 - addFirst처럼 맨 앞으로 보내는 게 아니라, 이름으로
        // 지정한 상대 위치에 삽입한다.
        sources.addBefore("anchor", new MapPropertySource("before-anchor", Map.of("greeting", "from-before-anchor")));

        assertThat(environment.getProperty("greeting")).isEqualTo("from-before-anchor");
    }

    @Test
    void systemPropertySetAtRuntimeOverridesACustomLowerPriorityPropertySource() {
        // StandardEnvironment는 systemProperties를 이미 맨 앞자리에 갖고 있다(1번째 실험) -
        // 애플리케이션이 addLast로 추가한 커스텀 설정(예: @PropertySource로 읽은 파일)은
        // 항상 그 뒤에 온다. -Dapp.name=... 같은 실행 시점 시스템 프로퍼티가 파일 기반
        // 설정을 조용히 덮어쓸 수 있는 이유다.
        String key = "lab.experiments.propertysource.overridden";
        System.setProperty(key, "from-system-property");
        try {
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources()
                    .addLast(new MapPropertySource("applicationConfig", Map.of(key, "from-application-config")));

            assertThat(environment.getProperty(key)).isEqualTo("from-system-property");
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void nestedPlaceholdersResolveTheInnerPlaceholderFirst() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("inner", "resolved-inner")));

        // "outer"라는 키 자체는 정의돼 있지 않다 - ${outer:...} 의 기본값 부분이 또 다른
        // 플레이스홀더(${inner})라서, 그 기본값을 쓰기 전에 먼저 재귀적으로 해석한다.
        String resolved = environment.resolvePlaceholders("${outer:${inner}}");

        assertThat(resolved).isEqualTo("resolved-inner");
    }
}
