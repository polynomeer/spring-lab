package lab.ext.observability.core;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "request-observation")
public class RequestObservationProperties {

    /**
     * 이 기능 전체를 켜고 끈다. matchIfMissing 관례를 따라 기본값은 true다.
     */
    private boolean enabled = true;

    /**
     * 이 시간을 넘긴 요청은 "느린 요청"으로 기록된다. "500ms"처럼 Boot의 Duration 표기법을
     * 그대로 쓸 수 있다.
     */
    private Duration slowThreshold = Duration.ofMillis(500);

    /**
     * 이 패턴(Ant 스타일)에 매칭되는 경로는 관찰 대상에서 제외한다.
     */
    private List<String> excludePaths = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    public void setSlowThreshold(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}
