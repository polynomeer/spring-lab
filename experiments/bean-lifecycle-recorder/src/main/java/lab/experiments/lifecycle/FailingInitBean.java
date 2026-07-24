package lab.experiments.lifecycle;

import org.springframework.beans.factory.InitializingBean;

public class FailingInitBean implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        throw new IllegalStateException("afterPropertiesSet boom");
    }
}
