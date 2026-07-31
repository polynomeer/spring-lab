package lab.experiments.mvcerror;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

// @ComponentScan 대신 명시적으로 @Import한다 - application-event-lab에서 실제로 겪었듯,
// 패키지 전체를 스캔하면 테스트 소스셋의 헬퍼 클래스까지 함께 주워 담길 위험이 있다.
@Configuration
@EnableWebMvc
@Import({DemoController.class, HighPriorityAdvice.class, LowPriorityAdvice.class, CommonAdvice.class})
public class ExceptionPipelineConfig {
}
