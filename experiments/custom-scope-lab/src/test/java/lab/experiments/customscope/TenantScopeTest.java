package lab.experiments.customscope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantScopeTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void sameTenantGetsTheSameInstanceAndTheFactoryRunsOnlyOnce() {
        TenantScope tenantScope = new TenantScope();
        try (AnnotationConfigApplicationContext context = contextWithTenantScope(tenantScope)) {
            TenantContext.setTenant("acme");

            TenantWidget first = context.getBean(TenantWidget.class);
            TenantWidget second = context.getBean(TenantWidget.class);

            assertThat(first.id()).isEqualTo(second.id());
            assertThat(tenantScope.objectFactoryInvocationCount()).isEqualTo(1);
        }
    }

    @Test
    void differentTenantsGetDifferentInstances() {
        TenantScope tenantScope = new TenantScope();
        try (AnnotationConfigApplicationContext context = contextWithTenantScope(tenantScope)) {
            TenantContext.setTenant("acme");
            TenantWidget acmeWidget = context.getBean(TenantWidget.class);

            TenantContext.setTenant("globex");
            TenantWidget globexWidget = context.getBean(TenantWidget.class);

            assertThat(acmeWidget.id()).isNotEqualTo(globexWidget.id());
        }
    }

    @Test
    void requestingTheScopedBeanWithoutABoundTenantThrowsScopeNotActiveException() {
        TenantScope tenantScope = new TenantScope();
        try (AnnotationConfigApplicationContext context = contextWithTenantScope(tenantScope)) {
            // TenantContext를 아무것도 설정하지 않았다 - TenantScope#get()이 던지는
            // IllegalStateException을 AbstractBeanFactory#doGetBean()이
            // ScopeNotActiveException으로 다시 감싼다(소스로 확인, 9번 절 참고).
            assertThatThrownBy(() -> context.getBean(TenantWidget.class))
                    .isInstanceOf(ScopeNotActiveException.class);
        }
    }

    @Test
    void destructionCallbackIsRegisteredEagerlyButOnlyRunsWhenTheScopeEndsTheTenant() {
        TenantScope tenantScope = new TenantScope();
        try (AnnotationConfigApplicationContext context = contextWithTenantScope(tenantScope)) {
            TenantContext.setTenant("acme");
            TenantWidget widget = context.getBean(TenantWidget.class);

            // 빈은 이미 만들어졌고 DisposableBean#destroy()를 감싼 콜백도 이미
            // registerDestructionCallback()으로 TenantScope에 전달됐다 - 하지만 아직 아무도
            // 그 Runnable을 실행하지 않았으므로 destroy()는 호출되지 않은 상태다.
            assertThat(widget.isDestroyed()).isFalse();

            tenantScope.endTenant("acme");

            // endTenant()가 저장해 뒀던 Runnable을 이제야 실행한다.
            assertThat(widget.isDestroyed()).isTrue();
        }
    }

    @Test
    void closingTheApplicationContextDoesNotDestroyCustomScopedBeans() {
        TenantScope tenantScope = new TenantScope();
        TenantWidget widget;
        try (AnnotationConfigApplicationContext context = contextWithTenantScope(tenantScope)) {
            TenantContext.setTenant("acme");
            widget = context.getBean(TenantWidget.class);
        }

        // context.close()는 DefaultSingletonBeanRegistry#destroySingletons()만 처리한다 -
        // 커스텀 스코프에 등록해 둔 destruction callback은 컨테이너 종료와 완전히
        // 무관하다. TenantScope가 endTenant()로 명시적으로 정리하지 않는 한, 이 위젯은
        // 컨텍스트가 닫힌 뒤에도 "살아 있는" 상태(destroy() 미호출)로 남는다.
        assertThat(widget.isDestroyed()).isFalse();
    }

    private AnnotationConfigApplicationContext contextWithTenantScope(TenantScope tenantScope) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerScope("tenant", tenantScope);
        context.register(TenantConfig.class);
        context.refresh();
        return context;
    }
}
