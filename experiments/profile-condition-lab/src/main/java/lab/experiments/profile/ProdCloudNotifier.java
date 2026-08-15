package lab.experiments.profile;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 하나의 문자열 안에 "&"가 있으면 복합 불리언 표현식(Profiles.of())으로 파싱된다 - prod와
// cloud가 "둘 다" 활성이어야 매칭된다.
@Component
@Profile("prod & cloud")
public class ProdCloudNotifier implements Notifier {

    @Override
    public String describe() {
        return "prod-cloud";
    }
}
