package lab.ext.timing;

import org.springframework.stereotype.Component;

@Component
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void notifyUser() {
        // @MeasureTime이 전혀 없는 빈 - 후처리기가 손대지 않고 그대로 둬야 한다.
    }
}
