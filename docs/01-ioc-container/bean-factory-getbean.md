# IoC와 BeanFactory — getBean(Class)의 실제 호출 경로

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 1주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 1(수동 IoC 컨테이너 실험)·프로젝트 2 1단계(Mini BeanFactory)에 대응하는 분석 문서다.

## 1. 이번 질문

`DefaultListableBeanFactory.getBean(PaymentService.class)`처럼 **타입으로** 빈을 조회하면, `BeanDefinition` 등록부터 싱글턴 인스턴스 반환까지 실제로 어떤 메서드를 거치는가? 그리고 이 경로는 **이름으로** 조회(`getBean("paymentService")`)할 때와 무엇이 다른가?

## 2. 공식 문서 요약

- IoC 컨테이너는 설정 메타데이터(애노테이션, `@Bean`, XML 등)를 읽어 객체를 생성·구성·조립하는 역할을 한다. 컨테이너 내부에서 빈 설정은 `BeanDefinition`으로 표현된다.
- `BeanFactory`는 최소 계약이다: `getBean`, `containsBean`, `isSingleton` 등 빈 조회에 필요한 기본 기능만 제공한다.
- `ApplicationContext`는 `BeanFactory`를 확장해 이벤트 발행, 국제화(`MessageSource`), 환경 추상화(`Environment`), `BeanFactoryPostProcessor`/`BeanPostProcessor` 자동 등록 같은 애플리케이션 인프라를 추가로 제공한다.
- `BeanFactory` 인터페이스 계층은 책임별로 분리되어 있다: `ListableBeanFactory`(전체 빈 열거), `HierarchicalBeanFactory`(부모 컨테이너), `AutowireCapableBeanFactory`(외부 객체에 대한 자동 와이어링).

## 3. 예상 동작 (소스를 보기 전에 작성)

`getBean(Class)`는 `getBean(String)`의 얇은 wrapper일 것이라 예상했다 — 즉 타입 정보로 빈 이름 하나를 찾아낸 뒤 곧바로 이름 기반 경로로 위임할 것이라 생각했다. 또한 순환 참조가 전혀 없는 이 예제(`PaymentService`는 의존성이 없음)에서는 "조기 참조(circular reference)" 관련 코드는 전혀 실행되지 않을 것이라 예상했다.

## 4. 최소 재현 코드

[`experiments/ioc-container-lab`](../../experiments/ioc-container-lab)

```java
DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
RootBeanDefinition paymentDefinition = new RootBeanDefinition(PaymentService.class);
beanFactory.registerBeanDefinition("paymentService", paymentDefinition);

PaymentService paymentService = beanFactory.getBean(PaymentService.class);
paymentService.pay();
```

전체 코드: [`BeanFactoryLab.java`](../../experiments/ioc-container-lab/src/main/java/lab/experiments/ioc/BeanFactoryLab.java)

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `BeanFactory` | `getBean`/`containsBean`/`isSingleton` — 빈 조회의 최소 계약 |
| `ListableBeanFactory` | 등록된 모든 빈 이름·타입을 열거 (`getBeanNamesForType` 등) |
| `AutowireCapableBeanFactory` | 컨테이너 밖 객체에 대한 자동 와이어링 지원 |
| `DefaultListableBeanFactory` | 위 인터페이스들의 대표 구현체. `BeanDefinitionRegistry`이기도 함 |
| `AbstractBeanFactory` | `getBean`의 흐름 제어(`doGetBean`) — 캐시 조회, 부모 위임, 타입 변환을 템플릿으로 구성 |
| `AbstractAutowireCapableBeanFactory` | 실제 빈 생성(`createBean`/`doCreateBean`) — 인스턴스화·DI·초기화 |
| `DefaultSingletonBeanRegistry` | 싱글턴 캐시(`singletonObjects`)와 조기 참조(`earlySingletonObjects`) 관리 |
| `RootBeanDefinition` | 코드로 직접 만드는 `BeanDefinition` 구현체 |

## 6. 호출 흐름

시퀀스 다이어그램: [`diagrams/getbean-sequence.md`](diagrams/getbean-sequence.md)

```text
getBean(PaymentService.class)
  → resolveNamedBean → getBeanNamesForType(type) → isTypeMatch(name) → getSingleton(name, false)   [히트 1]
  → getBean(name)
    → doGetBean(name, null, null, false)                                                            [히트 2]
      → getSingleton(name, false)   1차 캐시 확인                                                    [히트 3]
      → getSingleton(name, true)    조기 참조 확인                                                   [히트 4]
      → getSingleton(name, singletonFactory)   캐시 미스 → 생성 위임                                  [히트 5]
        → createBean(name, mbd, null)                                                                [히트 6]
          → doCreateBean(name, mbd, null)                                                            [히트 7]
            → createBeanInstance → new PaymentService()                                              [히트 8]
            → populateBean(name, bean, mbd)                                                          [히트 9]
            → initializeBean(name, bean, mbd)                                                        [히트 10]
          → getSingleton(name, false)   생성 후 조기 노출 여부 재확인 → addSingleton                    [히트 11]
→ paymentService.pay()                                                                                [히트 12]
```

## 7. 브레이크포인트

[`tools/jdi-tracer/specs/bean-factory-lab.txt`](../../tools/jdi-tracer/specs/bean-factory-lab.txt)에 정의된 스펙으로 실행:

```text
org.springframework.beans.factory.support.AbstractBeanFactory#doGetBean
org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBean,doCreateBean,createBeanInstance,populateBean,initializeBean
lab.experiments.ioc.PaymentService#pay
```

```bash
./gradlew -q :tools:jdi-tracer:run --args="\"<experiments:ioc-container-lab 런타임 classpath>\" lab.experiments.ioc.BeanFactoryLab specs/bean-factory-lab.txt"
```

`AbstractApplicationContext#refresh`는 이 예제에서 **호출되지 않는다** — `BeanFactoryLab`은 `ApplicationContext`를 전혀 쓰지 않고 `DefaultListableBeanFactory`를 직접 다루기 때문이다. `refresh()` 흐름은 `AnnotationConfigApplicationContext`를 쓰는 3주차(Context Refresh Visualizer) 주제에서 다룬다.

## 8. 런타임 관찰

- **호출 순서**: 위 6번 흐름대로 총 12회 브레이크포인트에 도달했다.
- **객체 타입**: 히트 5의 `singletonFactory`는 `AbstractBeanFactory$$Lambda`로 관찰됐다 — `getSingleton(String, ObjectFactory<?>)`가 `createBean` 호출을 람다로 감싸 캐시 로직과 생성 로직을 분리하고 있음을 직접 확인.
- **빈 상태**: 히트 9에서 `bw`(BeanWrapper)가 이미 `BeanWrapperImpl` 인스턴스로 준비돼 있었다 — `populateBean`은 리플렉션을 직접 쓰지 않고 `BeanWrapper`를 경유해 프로퍼티에 접근한다.
- **조기 참조 체크가 항상 실행됨**: 히트 4(생성 전)와 히트 11(생성 후)의 `getSingleton(name, allowEarlyReference=...)` 호출은 순환 참조가 전혀 없는 이 예제에서도 실행됐다. 즉 Spring은 "순환 참조가 있는 경우"를 특수 분기로 처리하는 게 아니라, 모든 싱글턴 생성 경로가 조기 노출 체크를 통과하도록 설계했다 — 10주차(순환 참조) 주제에서 이 지점을 다시 볼 필요가 있다.
- **타입 조회의 추가 비용**: 히트 1은 `doGetBean`이 시작되기도 전에 발생한다. `getBean(Class)`는 먼저 `getBeanNamesForType`으로 등록된 모든 빈 이름을 훑어 타입이 일치하는 후보를 찾고, 그 과정에서 후보 각각에 대해 `isTypeMatch → getSingleton(name, false)`를 호출한다. 즉 타입 기반 조회는 이름 기반 조회보다 최소 한 단계(후보 이름 스캔)를 더 거친다 — 3번의 예상과 달랐던 부분.
- **`BeanFactoryLabTest`의 6개 실험 결과** (실제 코드로 검증, [`BeanFactoryLabTest.java`](../../experiments/ioc-container-lab/src/test/java/lab/experiments/ioc/BeanFactoryLabTest.java)):

  | 실험 | 결과 |
  | --- | --- |
  | 등록 전/후 `containsBean()` | `false` → `true` |
  | singleton 빈을 두 번 `getBean()` | 동일 인스턴스 (`isSameAs`) |
  | prototype 빈을 두 번 `getBean()` | 서로 다른 인스턴스 |
  | 이름 조회 vs 타입 조회 | 동일 인스턴스 반환 |
  | 동일 타입 빈 2개 등록 후 타입 조회 | `NoUniqueBeanDefinitionException` |
  | 미등록 빈 조회 | `NoSuchBeanDefinitionException` |

## 9. 공식 테스트 분석

**미완료.** `docs/plan/00-methodology.md`의 버전 고정 절차대로 `spring-framework` 소스 저장소(`v6.2.x` 태그)를 아직 로컬에 clone하지 않아, `DefaultListableBeanFactoryTests` 같은 공식 테스트를 실제로 읽지 못했다. 이번 주차 안에 저장소를 내려받아 `DefaultListableBeanFactoryTests`, `AbstractBeanFactoryTests`에서 `getBean(Class)`와 `getSingleton` 관련 테스트 메서드를 찾아 이 절을 보완해야 한다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

[`mini-spring/mini-container`](../../mini-spring/mini-container) — 프로젝트 2의 1단계(인스턴스 저장 방식)까지만 구현됐다.

**구현한 것**
- 이름 기반 등록(`registerSingleton`)과 조회(`getBean(String)`)
- `containsBean(String)`
- 미등록 조회 시 전용 예외(`NoSuchBeanException`)

**생략한 것** (다음 단계에서 다룰 대상)
- `BeanDefinition` 자체가 없음 — 인스턴스를 직접 등록해야 하며, 클래스만 등록해 두고 지연 생성하는 방식이 아니다.
- 리플렉션 기반 생성, prototype scope, 타입 기반 조회, 동일 타입 중복 검증
- 조기 참조/순환 참조 처리 — 이번 관찰(8번)에서 확인한 "생성 전/후 조기 참조 재확인" 같은 구조가 전혀 없다.

## 11. Spring 설계 의도

- **캐시 조회와 생성 로직의 분리**: `getSingleton(name, ObjectFactory)`가 생성 로직을 람다로 받는 구조이기 때문에, `DefaultSingletonBeanRegistry`는 "어떻게 만드는지"를 몰라도 되고 오직 캐시 관리(락, 조기 노출, 예외 시 정리)에만 집중할 수 있다. 실제 생성 방법(`AbstractAutowireCapableBeanFactory`)과 캐시 정책(`DefaultSingletonBeanRegistry`)이 다른 클래스로 분리된 이유다.
- **조기 참조 체크를 항상 거치는 이유**: 순환 참조 여부를 미리 판단해서 분기하는 대신, 모든 싱글턴 생성이 "생성 전 조기 참조 확인 → 생성 → 생성 후 조기 노출 여부 재확인"이라는 동일한 경로를 지나가게 만들면, 순환 참조 지원 코드가 특수 케이스가 아니라 정상 경로의 일부가 된다. 코드 경로가 하나로 통일되므로 예외 케이스가 줄고, AOP 프록시가 섞인 순환 참조(10주차 주제)도 같은 메커니즘으로 처리할 수 있다.
- **타입 조회가 이름 스캔을 거치는 이유**: `BeanFactory`의 1급 식별자는 "이름"이다(`BeanDefinitionRegistry`가 이름으로 등록/조회되므로). 타입 기반 조회는 이름 기반 계약 위에 얹은 편의 기능이라, 후보 이름들을 먼저 찾아야 한다 — 이는 타입 기반 조회가 이름 기반 조회보다 근본적으로 더 비싼 연산임을 의미한다.

## 12. 결론 (예상과 실제의 차이)

- 예상과 다르게, `getBean(Class)`는 `getBean(String)`의 단순 wrapper가 아니라 **먼저 후보 이름을 찾는 별도의 스캔 단계**(`getBeanNamesForType`)를 거친 뒤에야 이름 기반 경로로 들어간다.
- 예상과 다르게, 순환 참조가 전혀 없는 단일 빈 생성에서도 **조기 참조 체크(`allowEarlyReference`)가 생성 전/후 두 번 실행**된다 — 순환 참조 처리는 특수 분기가 아니라 모든 싱글턴 생성이 지나가는 공통 경로다.
- 아직 열린 질문: 공식 테스트 분석(9번)이 비어 있다. 다음 작업으로 `spring-framework` 소스를 clone해서 이 절을 보완하는 것이 좋다.
