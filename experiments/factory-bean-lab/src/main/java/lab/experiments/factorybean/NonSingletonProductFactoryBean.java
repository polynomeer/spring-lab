package lab.experiments.factorybean;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.FactoryBean;

// isSingleton() = false - FactoryBeanRegistrySupport#getObjectFromFactoryBean()가
// factoryBeanObjectCache를 아예 거치지 않고 getObject()를 매 호출마다 새로 실행하게 만든다.
// 이 빈 "자신"은 여전히 평범한 싱글턴 스코프 빈이다(SingletonProductFactoryBeanConfig에
// 스코프를 따로 지정하지 않았으므로) - 팩토리 인스턴스 자체의 싱글턴 여부와, 그 팩토리가
// 만드는 산출물의 싱글턴 여부는 서로 다른 두 개의 스위치다.
public class NonSingletonProductFactoryBean implements FactoryBean<Widget> {

    private final AtomicInteger getObjectCallCount = new AtomicInteger();

    @Override
    public Widget getObject() {
        getObjectCallCount.incrementAndGet();
        return new Widget();
    }

    @Override
    public Class<?> getObjectType() {
        return Widget.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    public int getObjectCallCount() {
        return getObjectCallCount.get();
    }
}
