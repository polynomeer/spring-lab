package lab.experiments.refresh;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class LoggingApplicationListener implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        RefreshEventLog.record("event:ContextRefreshedEvent:" + event.getApplicationContext().getDisplayName());
    }
}
