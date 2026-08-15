package lab.experiments.profile;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// "default"는 특별한 예약어가 아니라, AbstractEnvironment#doGetDefaultProfiles()가
// 기본으로 돌려주는 Set<String>의 내용물일 뿐이다 - 활성 프로파일이 하나도 없을 때만
// 이 이름이 "활성"인 것처럼 취급된다.
@Component
@Profile("default")
public class DefaultNotifier implements Notifier {

    @Override
    public String describe() {
        return "default";
    }
}
