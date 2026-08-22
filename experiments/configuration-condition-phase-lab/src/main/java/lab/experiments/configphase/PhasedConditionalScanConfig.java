package lab.experiments.configphase;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import lab.experiments.configphase.scanned.ScannedComponent;

// PhasedOnMissingBeanCondition(REGISTER_BEAN 단계)을 @ComponentScan과 같은 클래스에
// 붙이면, ConfigurationClassParser.doProcessConfigurationClass()가 스캔을 수행하기
// 직전에 collectRegisterBeanConditions()로 REGISTER_BEAN 단계 조건이 있는지 확인하고
// 있으면 즉시 ApplicationContextException을 던진다 - "스캔은 파싱 시점에 즉시
// 실행되는데, REGISTER_BEAN 조건은 그보다 나중에야 의미가 있다"는 모순을 Spring이
// 스스로 막아 놓은 것이다.
@Configuration
@Conditional(PhasedOnMissingBeanCondition.class)
@ComponentScan(basePackageClasses = ScannedComponent.class)
public class PhasedConditionalScanConfig {
}
