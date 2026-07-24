package lab.experiments.lifecycle;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class LoggingSmartInitializingSingleton implements SmartInitializingSingleton {

    @Override
    public void afterSingletonsInstantiated() {
        LifecycleEventLog.record("SmartInitializingSingleton:afterSingletonsInstantiated");
    }
}
