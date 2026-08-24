package lab.experiments.scopedproxy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

@Configuration
public class TenantWidgetConfig {

    // proxyMode 없음(기본값 ScopedProxyMode.NO) - 이 빈을 요청하는 즉시
    // TenantScope#get()이 실행되어, 그 순간 현재 스레드에 테넌트가 없으면 바로 실패한다.
    @Bean
    @Scope(scopeName = "tenant", proxyMode = ScopedProxyMode.NO)
    public TenantWidget tenantWidgetNoProxy() {
        return new TenantWidget();
    }

    // proxyMode = TARGET_CLASS - "tenantWidgetProxied"라는 이름으로 실제 등록되는 것은
    // TenantWidget이 아니라 CGLIB로 TenantWidget을 상속한 프록시(ScopedProxyFactoryBean이
    // 만든 것)다. 진짜 TenantWidget 빈 정의는 "scopedTarget.tenantWidgetProxied"라는
    // 내부 이름으로 옮겨진다.
    @Bean
    @Scope(scopeName = "tenant", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public TenantWidget tenantWidgetProxied() {
        return new TenantWidget();
    }
}
