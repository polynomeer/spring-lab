package lab.experiments.profile;

import org.springframework.stereotype.Component;

// @Profile이 아예 없다 - ProfileCondition#matches()는 애노테이션 자체가 없으면 true를
// 돌려준다(조건 없음). 어떤 프로파일 조합에서도 항상 등록된다.
@Component
public class AlwaysOnNotifier implements Notifier {

    @Override
    public String describe() {
        return "always";
    }
}
