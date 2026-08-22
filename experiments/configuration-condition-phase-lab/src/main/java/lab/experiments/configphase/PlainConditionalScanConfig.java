package lab.experiments.configphase;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import lab.experiments.configphase.scanned.ScannedComponent;

// @ComponentScan은 ConfigurationClassParser.doProcessConfigurationClass()에서
// "즉시" 수행된다(componentScanParser.parse() -> scanner.doScan()) - @Bean
// 메서드처럼 모델링만 해뒀다가 reader가 나중에 일괄 등록하는 것과 달리, 스캔으로
// 찾은 빈 정의는 그 자리에서 바로 레지스트리에 등록된다. 그래서 이 클래스 레벨의
// PARSE_CONFIGURATION 단계 조건이 "건너뛰기"로 판정되면(processConfigurationClass
// 맨 앞의 shouldSkip 체크) @ComponentScan 자체가 아예 실행되지 않는다.
@Configuration
@Conditional(PlainOnMissingBeanCondition.class)
@ComponentScan(basePackageClasses = ScannedComponent.class)
public class PlainConditionalScanConfig {
}
