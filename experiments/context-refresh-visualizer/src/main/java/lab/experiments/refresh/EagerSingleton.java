package lab.experiments.refresh;

import org.springframework.stereotype.Component;

@Component
public class EagerSingleton {

    public EagerSingleton() {
        RefreshEventLog.record("constructor:eagerSingleton");
    }
}
