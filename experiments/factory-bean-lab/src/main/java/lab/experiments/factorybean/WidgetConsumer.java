package lab.experiments.factorybean;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

// 같은 "widget" 빈 정의 하나를 두 가지 선언 타입으로 각각 주입받는다 - 필드 타입이
// Widget이면 산출물을, FactoryBean<Widget>이면 팩토리 자신을 받는다. getObjectType()이
// 미리 Widget.class를 알려주기 때문에, 오토와이어링 후보 판정 시점에 getObject()를 호출해
// 실제로 인스턴스화해 보지 않고도 이 구분이 가능하다.
public class WidgetConsumer {

    @Autowired
    private Widget product;

    @Autowired
    private FactoryBean<Widget> factory;

    public Widget product() {
        return product;
    }

    public FactoryBean<Widget> factory() {
        return factory;
    }
}
