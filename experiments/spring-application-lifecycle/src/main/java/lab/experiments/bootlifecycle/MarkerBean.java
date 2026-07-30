package lab.experiments.bootlifecycle;

import org.springframework.stereotype.Component;

// 각 생명주기 이벤트 시점에 "빈 조회가 가능한가"를 확인하기 위한 표지 빈 - 그 이상의 의미는
// 없다.
@Component
public class MarkerBean {
}
