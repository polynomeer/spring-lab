package lab.experiments.refresh;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Lazy
@Component
public class LazySingleton {

    public LazySingleton() {
        RefreshEventLog.record("constructor:lazySingleton");
    }
}
