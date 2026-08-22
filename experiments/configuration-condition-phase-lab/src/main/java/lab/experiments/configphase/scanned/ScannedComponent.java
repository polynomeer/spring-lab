package lab.experiments.configphase.scanned;

import org.springframework.stereotype.Component;

// 별도 하위 패키지에 둔 이유는 다른 실험용 @Configuration 클래스들이 컴포넌트
// 스캔에 함께 딸려 들어오지 않게 스캔 대상을 이 클래스 하나로만 좁히기 위해서다.
@Component
public class ScannedComponent {
}
