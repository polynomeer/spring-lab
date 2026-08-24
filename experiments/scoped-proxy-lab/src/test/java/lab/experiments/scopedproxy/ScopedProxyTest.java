package lab.experiments.scopedproxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopedProxyTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * proxyMode 없이(ScopedProxyMode.NO) "tenant" 스코프 빈을 싱글턴 생성자에 직접
     * 주입하면, 컨테이너 시작 시점(preInstantiateSingletons)에 즉시 TenantScope#get()이
     * 호출된다 - 이 시점에 현재 스레드에 테넌트가 바인딩돼 있지 않으므로 refresh()
     * 자체가 실패한다.
     */
    @Test
    void withoutScopedProxyEagerSingletonCreationFailsWhenNoTenantIsBound() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerScope("tenant", new TenantScope());
            context.register(TenantWidgetConfig.class, HolderNoProxy.class);

            assertThatThrownBy(context::refresh)
                    .isInstanceOf(UnsatisfiedDependencyException.class)
                    .cause()
                    .isInstanceOf(ScopeNotActiveException.class);
        }
    }

    /**
     * proxyMode = TARGET_CLASS면 싱글턴이 주입받는 것은 CGLIB 프록시다 - 실제
     * TenantWidget을 스코프에서 꺼내는 일이 "메서드가 호출되는 순간"까지 미뤄지므로,
     * 컨테이너 시작 시점에 테넌트가 없어도 refresh() 자체는 문제없이 끝난다. 다만
     * 그렇게 미뤄 둔 대가는 사라지지 않는다 - 결국 메서드를 호출하는 순간 테넌트가
     * 없으면 그때 가서 똑같은 종류의 예외가 난다(대상이 원본 "tenantWidgetProxied"가
     * 아니라 내부 이름 "scopedTarget.tenantWidgetProxied"로 바뀌었을 뿐).
     */
    @Test
    void withScopedProxyEagerSingletonCreationSucceedsButFailureIsDeferredToInvocationTime() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerScope("tenant", new TenantScope());
            context.register(TenantWidgetConfig.class, HolderProxied.class);
            context.refresh();

            HolderProxied holder = context.getBean(HolderProxied.class);

            assertThatThrownBy(() -> holder.widget().id())
                    .isInstanceOf(ScopeNotActiveException.class)
                    .hasMessageContaining("scopedTarget.tenantWidgetProxied");
        }
    }

    /**
     * 같은 싱글턴(HolderProxied)의 같은 필드(widget)를 다시 주입받지 않고도, 스레드의
     * "현재 테넌트"를 바꿔 가며 호출하는 것만으로 매번 다른 실제 TenantWidget
     * 인스턴스로 투명하게 리다이렉트된다. 같은 테넌트로 되돌아가면 TenantScope가
     * 캐싱해 둔 같은 인스턴스를 다시 돌려준다.
     */
    @Test
    void scopedProxyTransparentlyRedirectsToCurrentTenantsInstanceOnEachCall() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerScope("tenant", new TenantScope());
            context.register(TenantWidgetConfig.class, HolderProxied.class);
            context.refresh();

            HolderProxied holder = context.getBean(HolderProxied.class);

            TenantContext.setTenant("acme");
            int acmeId = holder.widget().id();

            TenantContext.setTenant("globex");
            int globexId = holder.widget().id();

            TenantContext.setTenant("acme");
            int acmeIdAgain = holder.widget().id();

            assertThat(acmeId).isNotEqualTo(globexId);
            assertThat(acmeIdAgain).isEqualTo(acmeId);
        }
    }

    /**
     * "tenantWidgetProxied"라는 공개 이름은 더 이상 TenantWidget 자체가 아니라
     * ScopedProxyFactoryBean이 만든 CGLIB 프록시다. 진짜 TenantWidget 빈 정의는
     * "scopedTarget.tenantWidgetProxied"라는 내부 이름으로 옮겨져 있고, 그 프록시는
     * ScopedObject 인터페이스를 구현한다(32번 FactoryBean 문서에서 확인한 "&" 접두사
     * 역참조 규약과 같은 결의 또 다른 예 - 다만 이번엔 접두사가 아니라 별도의
     * 내부 이름으로 원본을 감춘다).
     */
    @Test
    void publicBeanNameBecomesAScopedProxyWhileTheRealTargetMovesToAnInternalName() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerScope("tenant", new TenantScope());
            context.register(TenantWidgetConfig.class, HolderProxied.class);
            context.refresh();

            assertThat(context.containsBeanDefinition("scopedTarget.tenantWidgetProxied")).isTrue();
            assertThat(context.getBean("tenantWidgetProxied")).isInstanceOf(ScopedObject.class);
        }
    }
}
