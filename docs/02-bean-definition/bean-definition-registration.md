# BeanDefinition과 빈 등록 — 등록 경로에 따라 메타데이터가 어떻게 달라지는가

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 2주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 3(BeanDefinition Registry Inspector)에 대응하는 분석 문서다.

## 1. 이번 질문

같은 애플리케이션 안에서도 빈을 등록하는 경로가 다르면(`@Component` 스캔, `@Bean` 메서드, `registerBeanDefinition()` 직접 등록, 정적 팩토리 메서드, `Supplier`) 실제로 만들어지는 `BeanDefinition`의 메타데이터(`beanClass`, `scope`, `factoryBeanName`, `factoryMethodName`, instance supplier 여부, `role`)가 어떻게 달라지는가?

## 2. 공식 문서 요약

- `BeanDefinition`은 빈 인스턴스가 아니라 "어떻게 만들 것인가"에 대한 메타데이터(클래스, scope, lazy 여부, 생성자 인자, 프로퍼티 값 등)를 담는 객체다.
- 설정 메타데이터를 표현하는 방식은 XML, 애노테이션(`@Component`/`@Configuration`+`@Bean`), 프로그래밍 방식(`BeanDefinitionRegistry.registerBeanDefinition`) 세 가지가 있지만, 컨테이너 내부에서는 결국 전부 `BeanDefinition`으로 수렴한다.
- 빈 이름은 명시하지 않으면 기본 규칙을 따른다: 컴포넌트 스캔은 클래스 simple name을 decapitalize(`NotificationService` → `notificationService`), `@Bean`은 메서드 이름을 그대로 쓴다(`paymentService()` → `paymentService`).

## 3. 예상 동작 (소스를 보기 전에 작성)

- 어떤 경로로 등록하든 `BeanDefinition.getBeanClassName()`은 실제 빈의 구체 클래스를 가리킬 것이라 예상했다.
- `factoryBeanName`/`factoryMethodName`은 `@Bean` 방식에서만 나타나고, 나머지는 전부 비어 있을 것이라 예상했다.
- Spring 내부 인프라 빈은 항상 고정된 목록으로 등록될 것이라 예상했다.
- `@Configuration` 클래스 자신의 `BeanDefinition`은 원본 클래스(`AppConfig`)를 그대로 가리킬 것이라 예상했다 — CGLIB 프록시는 "인스턴스가 생성될 때"만 관여할 거라 생각했다.

## 4. 최소 재현 코드

[`experiments/bean-definition-inspector`](../../experiments/bean-definition-inspector)

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
context.register(AppConfig.class);                                              // @Component + @Bean

context.registerBeanDefinition("manualBean", new RootBeanDefinition(ManualBean.class));

RootBeanDefinition legacyClientDefinition = new RootBeanDefinition(LegacyClient.class);
legacyClientDefinition.setFactoryMethodName("create");                          // 정적 팩토리 메서드
context.registerBeanDefinition("legacyClient", legacyClientDefinition);

context.registerBeanDefinition("supplierBean",                                  // Supplier 기반
        BeanDefinitionBuilder.genericBeanDefinition(SupplierBean.class, SupplierBean::new)
                .getBeanDefinition());

context.refresh();
BeanDefinitionInspector.print(context);
```

전체 코드: [`BeanDefinitionInspectorLab.java`](../../experiments/bean-definition-inspector/src/main/java/lab/experiments/beandef/BeanDefinitionInspectorLab.java)

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `BeanDefinition` | 빈 생성 메타데이터의 최소 계약 (`getBeanClassName`, `getScope`, `getFactoryBeanName`, `getFactoryMethodName`, `getRole` 등) |
| `AbstractBeanDefinition` | `BeanDefinition`의 대표 구현. `resolveBeanClass`, `getInstanceSupplier` 등 실질적인 로직을 가짐 |
| `RootBeanDefinition` | 코드로 직접 만드는 `BeanDefinition` 구현체 (부모 정의 없이 단독으로 완결) |
| `GenericBeanDefinition` | 부모를 가질 수 있는 범용 `BeanDefinition` 구현체. `BeanDefinitionBuilder`가 내부적으로 사용 |
| `BeanDefinitionRegistry` | `registerBeanDefinition`/`getBeanDefinition` 등 등록소 계약. `DefaultListableBeanFactory`가 구현 |
| `ConfigurationClassPostProcessor` | `@Configuration`/`@Component`/`@Bean`을 읽어 `BeanDefinition`으로 변환하는 `BeanDefinitionRegistryPostProcessor`이자, refresh 후반에 Full 모드 설정 클래스를 CGLIB로 치환하는 `BeanFactoryPostProcessor` |
| `ConfigurationClassBeanDefinitionReader` | `@Bean` 메서드 하나하나를 `BeanDefinition`으로 변환 |
| `ConfigurationClassEnhancer` | `@Configuration(proxyBeanMethods=true)` 클래스의 CGLIB 서브클래스를 실제로 생성 |
| `AnnotationConfigUtils` | 컨테이너 부트스트랩에 필요한 내부 인프라 빈(`internalConfigurationAnnotationProcessor` 등)을 등록 |

## 6. 호출 흐름

시퀀스 다이어그램: [`diagrams/bean-definition-flow.md`](diagrams/bean-definition-flow.md)

```text
context.register(AppConfig.class)
  → AnnotatedBeanDefinitionReader가 "appConfig" BeanDefinition 등록
  → AnnotationConfigUtils.registerAnnotationConfigProcessors() → 인프라 빈들 등록

context.registerBeanDefinition("manualBean"/"legacyClient"/"supplierBean", ...)
  → refresh() 이전에 이미 registry에 존재 (BeanDefinitionRegistryPostProcessor가 손대지 않음)

context.refresh()
  → invokeBeanFactoryPostProcessors
    → ConfigurationClassPostProcessor#processConfigBeanDefinitions   (BeanDefinitionRegistryPostProcessor 단계)
        → @ComponentScan 처리 → "notificationService" 등록
        → ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForBeanMethod("paymentService")
            → instance @Bean 메서드라서 setFactoryBeanName("appConfig") + setUniqueFactoryMethodName("paymentService")
            → beanClass는 끝까지 설정되지 않음 (null로 남음)
    → ConfigurationClassPostProcessor#enhanceConfigurationClasses     (BeanFactoryPostProcessor 단계)
        → CONFIGURATION_CLASS_FULL(proxyBeanMethods=true)인 "appConfig"만 골라냄
        → ConfigurationClassEnhancer#enhance(AppConfig.class) → AppConfig$$SpringCGLIB$$0
        → beanDef.setBeanClass(AppConfig$$SpringCGLIB$$0)

BeanDefinitionInspector.inspect()
  → getBeanDefinitionNames() / getBeanDefinition() 만 호출 — 어떤 빈도 인스턴스화하지 않음
```

## 7. 브레이크포인트

이번 주제는 실시간 디버깅(`tools/jdi-tracer`) 대신 소스 코드를 직접 읽어서(6번) 확인했다. 다음은 추적 후보로 남겨둔다.

```text
org.springframework.context.annotation.ConfigurationClassPostProcessor#processConfigBeanDefinitions
org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForBeanMethod
org.springframework.context.annotation.ConfigurationClassPostProcessor#enhanceConfigurationClasses
org.springframework.context.annotation.ConfigurationClassEnhancer#enhance
```

## 8. 런타임 관찰

`BeanDefinitionInspectorLab`을 실행해서 얻은 실제 출력([`BeanDefinitionInspectorTest`](../../experiments/bean-definition-inspector/src/test/java/lab/experiments/beandef/BeanDefinitionInspectorTest.java)로 고정):

| beanName | beanClass | factoryBean | factoryMethod | instanceSupplier | role |
| --- | --- | --- | --- | --- | --- |
| `notificationService` (`@Component`) | `NotificationService` | — | — | false | 0 (APPLICATION) |
| `paymentService` (`@Bean`, instance method) | **null** | `appConfig` | `paymentService` | false | 0 |
| `manualBean` (`registerBeanDefinition`) | `ManualBean` | — | — | false | 0 |
| `legacyClient` (정적 팩토리 메서드) | `LegacyClient` | — | `create` | false | 0 |
| `supplierBean` (`Supplier`) | `SupplierBean` | — | — | **true** | 0 |
| `appConfig` (`@Configuration`) | **`AppConfig$$SpringCGLIB$$0`** | — | — | false | 0 |
| `internalConfigurationAnnotationProcessor` 등 4개 | 각 프로세서 클래스 | — | — | false | **2 (INFRASTRUCTURE)** |

세 가지가 3번의 예상과 달랐다.

- **`paymentService`의 `beanClass`가 `null`이다.** `@Bean` instance 메서드는 `factoryBeanName`+`factoryMethodName`으로만 표현되고, 실제 반환 타입은 미리 확정하지 않는다.
- **`appConfig`의 `beanClass`가 원본 `AppConfig`가 아니라 `AppConfig$$SpringCGLIB$$0`다.** 인스턴스가 생성되기도 전에, `BeanDefinition` 단계에서 이미 CGLIB 서브클래스로 바뀌어 있다.
- **`internalCommonAnnotationProcessor`가 아예 등록되지 않았다.** 이 모듈의 `build.gradle.kts`는 `spring-context`만 의존하고 `jakarta.annotation-api`를 추가하지 않았는데, `AnnotationConfigUtils`가 `jakarta.annotation.PostConstruct`가 클래스패스에 있는지 확인한 뒤에만 이 프로세서를 등록하기 때문이다 (9번 참고). 등록되는 인프라 빈이 4개뿐이라는 것 자체가 "클래스패스 조건부 등록"의 실물 사례다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스(`/Users/hammac/Study/spring-framework-src`, 이 랩 저장소 밖에 별도로 둠 — [`bean-factory-getbean.md`](../01-ioc-container/bean-factory-getbean.md) 9번 참고)로 확인했다.

**CGLIB 치환 조건** — `spring-context/src/test/java/org/springframework/context/annotation/ConfigurationClassPostProcessorTests.java`
- `enhancementIsPresentBecauseSingletonSemanticsAreRespected()`: 기본값(`proxyBeanMethods=true`)이면 `beanFactory.getBeanDefinition("config").getBeanClass().getName()`이 CGLIB 클래스 구분자를 포함한다 — 우리가 관찰한 `AppConfig$$SpringCGLIB$$0`와 정확히 같은 현상.
- `enhancementIsNotPresentForProxyBeanMethodsFlagSetToFalse()`: Lite 모드면 CGLIB 치환이 없다.
- `enhancementIsNotPresentForStaticMethods()`: `@Bean` 메서드가 전부 `static`이면 치환하지 않는다 — static 메서드는 `this`를 통한 교차 호출이 애초에 불가능해서 프록시로 가로챌 필요가 없기 때문이다.
- `enhancementIsNotPresentWithEmptyConfig()`: `@Bean` 메서드가 하나도 없는 설정 클래스는 애초에 치환 대상에서 제외된다.

**static @Bean vs instance @Bean의 `BeanDefinition` 차이** — `ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod()` 소스를 직접 읽었다(6번 인용).
```java
if (metadata.isStatic()) {
    // static @Bean method
    beanDef.setBeanClass(sam.getIntrospectedClass());   // 선언 클래스 자체를 beanClass로
    beanDef.setUniqueFactoryMethodName(methodName);
}
else {
    // instance @Bean method
    beanDef.setFactoryBeanName(configClass.getBeanName());  // factoryBean으로 참조
    beanDef.setUniqueFactoryMethodName(methodName);
    // beanClass는 설정하지 않음
}
```
우리 `paymentService()`는 instance 메서드라 `beanClass=null`이 된 것이고, 만약 `static`으로 선언했다면 `beanClass=AppConfig`(또는 그 CGLIB 서브클래스), `factoryBeanName=null`이 되어 — 우리가 `legacyClient`를 위해 수동으로 만든 "정적 팩토리 메서드" `BeanDefinition`(`beanClass` 설정 + `factoryMethodName` 설정, `factoryBeanName` 없음)과 **완전히 같은 모양**이 된다. 실무에서 종종 보는 "무거운 초기화가 필요 없는 `@Bean`은 `static`으로 선언하라"는 권고가 왜 프록시 문제(`enhancementIsNotPresentForStaticMethods`)와 함께 언급되는지도 이 구조로 설명된다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

[`mini-spring/mini-container`](../../mini-spring/mini-container)의 `BeanDefinition`은 1주차에서 이미 `beanClass`+`Scope`만 담는 최소 형태로 만들어 두었다. 이번 주 로드맵이 목표로 제시한 "`SimpleBeanDefinition`, `BeanDefinitionRegistry`, `SimpleBeanFactory`"는 그때 이미 상당 부분 채워졌다.

**아직 없는 것** (이번 실험에서 비교한 축과 대응)
- `factoryBeanName`/`factoryMethodName` 개념 자체가 없다 — `mini-container`는 항상 `beanClass`의 기본 생성자만 리플렉션으로 호출한다. `legacyClient`(정적 팩토리 메서드) 같은 등록 방식은 흉내낼 수 없다.
- `instanceSupplier` 개념도 없다 — `supplierBean` 같은 등록 방식도 아직 불가능하다.
- 컴포넌트 스캔이 없어 `@Component` 방식과 동등한 자동 등록도 없다 (project 10, Mini Component Scanner에서 다룰 대상).
- `role`(APPLICATION/SUPPORT/INFRASTRUCTURE) 구분이 없다 — 모든 빈이 동등하게 취급된다.

이 격차들은 project 13(Mini Java Config Parser)과 project 10(Mini Component Scanner)에서 채워질 예정이다.

## 11. Spring 설계 의도

- **`@Bean` instance 메서드가 `beanClass`를 미리 정하지 않는 이유**: 메서드의 선언 반환 타입이 인터페이스이거나 상위 타입일 수 있어서, 실제로 만들어지는 구체 타입은 메서드가 호출되기 전까지 알 수 없다. `beanClass`를 미리 못박아 두면 실제 반환값과 어긋날 위험이 있으므로, 타입 판단을 뒤로 미루고 `factoryBeanName`+`factoryMethodName`이라는 "어떻게 만들 것인가"만 기록해 둔다.
- **`static` `@Bean`은 왜 다르게 취급되는가**: static 메서드는 설정 객체의 인스턴스가 없어도 호출 가능하므로, `factoryBeanName`(누구를 통해 호출할지) 자체가 필요 없다. 대신 "어느 클래스의 static 메서드인가"를 `beanClass`로 직접 표현할 수 있다 — 이는 우리가 `legacyClient`를 위해 수동으로 만든 것과 동일한 모양이다. 즉 Spring의 "정적 팩토리 메서드"와 "static `@Bean`"은 서로 다른 기능이 아니라 **같은 `BeanDefinition` 형태의 두 가지 진입 경로**다.
- **`@Configuration` 클래스를 `BeanDefinition` 단계에서 미리 CGLIB로 치환하는 이유**: `proxyBeanMethods=true`(Full 모드)에서는 같은 설정 클래스 안의 `@Bean` 메서드끼리 서로 호출해도 매번 새 객체가 아니라 컨테이너가 관리하는 싱글턴을 돌려받아야 한다. 이를 위해 실제로 인스턴스화되는 객체 자체가 원본이 아니라 메서드 호출을 가로채는 CGLIB 서브클래스여야 한다. 이 치환을 `BeanDefinition.beanClass` 자체에서 해버리면, 이후의 일반적인 빈 생성 파이프라인(리플렉션으로 생성자 호출 등)은 "이미 대체된 클래스"를 대상으로 아무 특수 분기 없이 그대로 동작한다 — Config 클래스 처리 로직과 범용 빈 생성 파이프라인을 분리하지 않고 재사용하기 위한 설계다.
- **인프라 빈이 클래스패스 조건부로 등록되는 이유**: `jakarta.annotation-api`가 없는 환경에 `CommonAnnotationBeanPostProcessor`를 무조건 등록해버리면 클래스 로딩 시점에 `ClassNotFoundException`이 난다. 필요한 클래스가 있을 때만 등록하는 이 패턴은 Spring Boot의 `@ConditionalOnClass`가 자동 설정 전체에 걸쳐 일반화한 것과 동일한 아이디어다.

## 12. 결론 (예상과 실제의 차이)

- 예상과 다르게, `@Bean` 메서드가 전부 `factoryBean`+`factoryMethod` 형태로만 표현되는 게 아니었다 — **static이냐 instance냐**에 따라 `beanClass`를 직접 쓰는지 `factoryBeanName`을 쓰는지가 갈렸고, static `@Bean`은 우리가 수동으로 만든 "정적 팩토리 메서드" `BeanDefinition`과 형태가 완전히 같았다.
- 예상과 다르게, `@Configuration` 클래스의 `beanClass`는 인스턴스 생성 시점이 아니라 **`refresh()`의 `BeanFactoryPostProcessor` 단계에서 이미** CGLIB 서브클래스로 바뀌어 있었다.
- 예상과 다르게, Spring 내부 인프라 빈은 고정된 목록이 아니라 **클래스패스에 따라 개수가 달라졌다** — 이 저장소의 `bean-definition-inspector` 모듈은 `jakarta.annotation-api`가 없어서 5개가 아니라 4개만 등록됐다.
- 새로 열린 질문: `enhanceConfigurationClasses`/`ConfigurationClassEnhancer#enhance`를 실제로 `tools/jdi-tracer`로 추적해 보지는 않았다(7번) — CGLIB가 인터셉터(`BeanMethodInterceptor`)를 어떻게 등록해서 같은 클래스 안 `@Bean` 메서드 호출을 가로채는지는 8주차(`@Configuration`과 `@Bean`)에서 더 다룰 만하다.
