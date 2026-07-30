package lab.experiments.autoconfig;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

// @ComponentScan도, GreetingAutoConfiguration/LoggingSupportAutoConfiguration을 직접
// @Import하지도 않는다 - 오직 @EnableAutoConfiguration 하나가 META-INF/spring/....imports
// 파일을 읽어서 두 자동 설정을 찾아내는지가 이번 실험의 핵심이다.
@Configuration
@EnableAutoConfiguration
public class PlainAppConfig {
}
