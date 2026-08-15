package lab.experiments.profile;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevNotifier implements Notifier {

    @Override
    public String describe() {
        return "dev";
    }
}
