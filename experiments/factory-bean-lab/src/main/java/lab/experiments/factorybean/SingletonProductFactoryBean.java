package lab.experiments.factorybean;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.FactoryBean;

// isSingleton()을 오버라이드하지 않는다 - FactoryBean의 기본 구현이 이미 true를 돌려준다.
// 이 빈 자신이 컨테이너 안에서 싱글턴 스코프인 것과는 별개로, "이 팩토리가 만드는 산출물이
// 매번 같은가"를 이 메서드 하나가 결정한다.
public class SingletonProductFactoryBean implements FactoryBean<Widget> {

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

    public int getObjectCallCount() {
        return getObjectCallCount.get();
    }
}
