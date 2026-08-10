package lab.sampleapp.plugindiscovery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 카탈로그 프로젝트 11의 요구사항을 그대로 매핑한다 - 패키지 스캔으로 찾은 것들을 Spring이
 * 이미 정렬해 준 {@code List<NotificationPlugin>}으로 받아서, 우리가 직접 정의한 조회 키
 * ({@link NotificationPlugin#type()})로 다시 색인한다.
 *
 * <p>Spring 자신도 {@code Map<String, NotificationPlugin>}을 그대로 주입해 줄 수 있지만,
 * 그 맵의 키는 {@code type()}이 아니라 **빈 이름**이다("emailNotificationPlugin" 같은) -
 * 그래서 "타입별 Map"이 필요하면 이렇게 직접 만들어야 한다. 이 차이는
 * {@code PluginDiscoveryTest}에서 두 맵을 나란히 비교해 확인한다.
 */
@Component
public class NotificationPluginRegistry {

    private final List<NotificationPlugin> orderedPlugins;
    private final Map<String, NotificationPlugin> byType;

    public NotificationPluginRegistry(List<NotificationPlugin> plugins) {
        this.orderedPlugins = List.copyOf(plugins);

        Map<String, NotificationPlugin> map = new LinkedHashMap<>();
        for (NotificationPlugin plugin : plugins) {
            NotificationPlugin existing = map.putIfAbsent(plugin.type(), plugin);
            if (existing != null) {
                throw new IllegalStateException("duplicate NotificationPlugin type '" + plugin.type() + "': "
                        + existing.getClass().getSimpleName() + " and " + plugin.getClass().getSimpleName()
                        + " both claim it");
            }
        }
        this.byType = Map.copyOf(map);
    }

    /** Spring이 {@code @Order} 순서대로 정렬해 준 목록 그대로 - enabled 여부와 무관하게 발견된 전부. */
    public List<NotificationPlugin> orderedPlugins() {
        return orderedPlugins;
    }

    public Set<String> discoveredTypes() {
        return byType.keySet();
    }

    /** 우선순위(등록 순서)상 가장 먼저 나오는, 지금 켜져 있는 플러그인 - 없으면 비어 있다. */
    public Optional<NotificationPlugin> firstEnabled() {
        return orderedPlugins.stream().filter(NotificationPlugin::enabled).findFirst();
    }

    /** 런타임에 문자열 하나로 대상 플러그인을 골라 실제로 발송한다 - 전략 패턴의 실제 사용 지점. */
    public void dispatch(String type, NotificationMessage message) {
        NotificationPlugin plugin = byType.get(type);
        if (plugin == null) {
            throw new IllegalArgumentException("no plugin registered for type '" + type + "'");
        }
        if (!plugin.enabled()) {
            throw new IllegalStateException("plugin '" + type + "' is registered but disabled");
        }
        plugin.send(message);
    }
}
