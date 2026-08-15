package lab.experiments.profile;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdNotifier implements Notifier {

    @Override
    public String describe() {
        return "prod";
    }
}
