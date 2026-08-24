package lab.experiments.scopedproxy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// 싱글턴이다 - 컨테이너가 시작될 때(preInstantiateSingletons) 즉시 만들어지고,
// 그 생성자 인자로 TenantWidget을 "지금 당장" 필요로 한다.
@Component
public class HolderNoProxy {

    private final TenantWidget widget;

    public HolderNoProxy(@Qualifier("tenantWidgetNoProxy") TenantWidget widget) {
        this.widget = widget;
    }

    public TenantWidget widget() {
        return widget;
    }
}
