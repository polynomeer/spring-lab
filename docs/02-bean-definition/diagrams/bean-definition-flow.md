# AppConfig의 BeanDefinition이 refresh() 동안 바뀌는 흐름

[`bean-definition-registration.md`](../bean-definition-registration.md)의 6번(호출 흐름) 항목을 시각화한 것. `experiments/bean-definition-inspector`의 `BeanDefinitionInspectorLab`이 실제로 거치는 경로다.

```mermaid
sequenceDiagram
    participant Lab as BeanDefinitionInspectorLab
    participant Reader as AnnotatedBeanDefinitionReader
    participant Registry as BeanDefinitionRegistry
    participant CCPP as ConfigurationClassPostProcessor
    participant CCBDR as ConfigurationClassBeanDefinitionReader
    participant Enhancer as ConfigurationClassEnhancer

    Lab->>Reader: context.register(AppConfig.class)
    Reader->>Registry: registerBeanDefinition("appConfig", ...)
    Reader->>Registry: AnnotationConfigUtils.registerAnnotationConfigProcessors()
    Note right of Registry: internalConfigurationAnnotationProcessor 등 인프라 빈 등록<br/>(jakarta.annotation-api 없으면 internalCommonAnnotationProcessor는 빠짐)

    Lab->>Registry: registerBeanDefinition("manualBean"/"legacyClient"/"supplierBean", ...)
    Note right of Registry: refresh() 이전에 직접 등록 — beanClass/factoryMethod/instanceSupplier 그대로 보존

    Lab->>CCPP: context.refresh() → invokeBeanFactoryPostProcessors

    CCPP->>CCPP: processConfigBeanDefinitions (BeanDefinitionRegistryPostProcessor 단계)
    CCPP->>Registry: @ComponentScan 처리 → registerBeanDefinition("notificationService", ...)
    CCPP->>CCBDR: @Bean 메서드 처리 → loadBeanDefinitionsForBeanMethod("paymentService")
    CCBDR->>Registry: setFactoryBeanName("appConfig") + setUniqueFactoryMethodName("paymentService")
    Note right of Registry: instance @Bean 메서드라 beanClass는 끝까지 null

    CCPP->>CCPP: enhanceConfigurationClasses (BeanFactoryPostProcessor 단계)
    Note right of CCPP: CONFIGURATION_CLASS_FULL(proxyBeanMethods=true)인<br/>appConfig만 대상으로 선정
    CCPP->>Enhancer: enhancer.enhance(AppConfig.class)
    Enhancer-->>CCPP: AppConfig$$SpringCGLIB$$0
    CCPP->>Registry: appConfigBeanDef.setBeanClass(AppConfig$$SpringCGLIB$$0)
    Note right of Registry: 인스턴스가 생기기도 전에<br/>BeanDefinition의 beanClass 자체가 교체됨

    Lab->>Registry: BeanDefinitionInspector.inspect() → getBeanDefinitionNames()/getBeanDefinition()
    Note right of Lab: 메타데이터만 읽는다 — 어떤 빈도 인스턴스화하지 않음
```
