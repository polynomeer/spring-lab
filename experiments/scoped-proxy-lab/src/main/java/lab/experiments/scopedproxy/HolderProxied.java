package lab.experiments.scopedproxy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// HolderNoProxy와 구조는 동일하지만, 주입받는 것은 "tenantWidgetProxied"라는 이름의
// 스코프드 프록시다 - 생성자가 실행되는 시점에는 이 프록시 객체 자체만 있으면 되고,
// 실제 TenantWidget을 스코프에서 꺼내는 일은 이 필드의 메서드가 "호출되는" 시점까지
// 미뤄진다.
@Component
public class HolderProxied {

    private final TenantWidget widget;

    public HolderProxied(@Qualifier("tenantWidgetProxied") TenantWidget widget) {
        this.widget = widget;
    }

    public TenantWidget widget() {
        return widget;
    }
}
