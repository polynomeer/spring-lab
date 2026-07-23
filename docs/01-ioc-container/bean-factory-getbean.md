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

`spring-framework` 저장소를 `v6.2.19` 태그로 shallow clone해서(`/Users/hammac/Study/spring-framework-src`, 이 랩 저장소 밖에 별도로 둠 — git 이력에는 포함하지 않음) 확인했다.

**타입 조회 예외 케이스** — `spring-beans/src/test/java/org/springframework/beans/factory/DefaultListableBeanFactoryTests.java`
- `getBeanByTypeWithNoneFound()`: 후보가 없으면 `NoSuchBeanDefinitionException` — 8번에서 확인한 미등록 예외와 정확히 같은 케이스다.
- `getBeanByTypeWithAmbiguity()`: 같은 타입의 `BeanDefinition`이 두 개면 `NoUniqueBeanDefinitionException` — `BeanFactoryLabTest.typeLookupFailsWhenMultipleCandidatesExist()`와 동일한 시나리오를 공식 테스트가 그대로 검증하고 있다.

**타입 스캔이 인스턴스를 강제로 만들지 않는다는 근거** — `getBeanByTypeWithPrimary()`:
```java
RootBeanDefinition bd1 = new RootBeanDefinition(TestBean.class);
bd1.setLazyInit(true);                    // bd1은 lazy, 후보에는 포함되지만 즉시 만들어지면 안 됨
RootBeanDefinition bd2 = new RootBeanDefinition(TestBean.class);
bd2.setPrimary(true);                     // bd2가 @Primary
...
TestBean bean = lbf.getBean(TestBean.class);
assertThat(bean.getBeanName()).isEqualTo("bd2");
assertThat(lbf.containsSingleton("bd1")).isFalse();   // ← bd1은 끝까지 인스턴스화되지 않았다
```
이 마지막 단언이 8번에서 관찰한 히트 1(`getBeanNamesForType` → `isTypeMatch` → `getSingleton(name, false)`)의 설계 의도를 정확히 증명한다: 타입 후보를 찾는 단계는 **타입 메타데이터만으로 판단**하고, 후보가 아닌(lazy·non-primary) 빈은 실제로 생성하지 않는다.

**조기 참조 체크가 왜 "항상" 실행되는지** — 같은 파일의 순환 참조 테스트 세 개를 비교하면 이유가 드러난다.
- `extensiveCircularReference()`: 1000개의 빈이 **프로퍼티(setter) 참조**로 원형을 이루는데도 `preInstantiateSingletons()`가 성공한다 — setter 주입은 조기 노출된 미완성 참조를 나중에 다시 주입받아도 문제가 없기 때문이다.
- `circularReferenceThroughAutowiring()`: **생성자** 자동와이어링으로 자기 자신을 필요로 하면 `UnsatisfiedDependencyException`이 발생한다 — 생성자 인자는 조기 노출된 참조로 대체할 수 없다(객체가 아직 존재하지 않으므로).
- `prototypeCircleLeadsToException()`: **prototype** 스코프의 순환 참조는 `BeanCreationException`(원인은 `BeanCurrentlyInCreationException`) — prototype은 캐시 슬롯이 없어 조기 노출 자체가 불가능하다.

즉 히트 4/11에서 본 조기 참조 체크는 "이번엔 순환이 있는지" 미리 판단해서 조건부로 켜는 게 아니라, **모든 singleton 생성이 거치는 동일한 경로**이기 때문에 항상 실행된다 — 이 절 초입에서 확인한 것과 일치한다. 다만 그 체크가 실제로 순환을 "해결"해 주는 것은 setter/property 주입 조합뿐이고, 생성자 주입과 prototype 스코프에서는 여전히 예외로 끝난다는 것이 공식 테스트로 확인된 새로운 사실이다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

[`mini-spring/mini-container`](../../mini-spring/mini-container) — 프로젝트 2의 1단계(인스턴스 저장 방식)부터 3단계(지연 생성)까지, 그리고 "구현 완료 조건"에 있던 타입 기반 조회·동일 타입 중복 예외·생성 중인 빈 상태 관리까지 전부 구현됐다.

**구현한 것**
- 이름 기반 등록(`registerSingleton`)과 조회(`getBean(String)`), `containsBean(String)`
- `BeanDefinition`(`beanClass`, `Scope`) 등록 — 등록 시점에는 인스턴스를 만들지 않고 최초 `getBean()` 호출 때 리플렉션으로 생성 (`beanDefinitionIsNotInstantiatedUntilFirstGetBean` 테스트로 생성자 호출 횟수를 직접 세어 검증)
- `SINGLETON`/`PROTOTYPE` scope — 싱글턴은 최초 생성 후 캐시, prototype은 매번 새 인스턴스
- 이름 중복 등록 검증(`DuplicateBeanDefinitionException`) — `registerSingleton`/`registerBeanDefinition` 어느 조합으로 중복돼도 감지
- 리플렉션 실패를 감싼 전용 예외(`BeanInstantiationException`)
- 미등록 조회 시 전용 예외(`NoSuchBeanException`)
- **재진입 감지**(project 17 Mini Cycle Detector 선반영) — `beanCreationPath`로 현재 생성 중인 빈 이름의 경로를 추적하다가, 이미 경로에 있는 이름이 다시 요청되면 `CircularDependencyException`을 그 경로("a -> b -> a")와 함께 던진다.
- **타입 기반 조회**(`getBean(Class<T>)`) — `singletonObjects`(이미 만들어진 인스턴스는 `isInstance`로 직접 확인)와 `beanDefinitionMap`(아직 안 만들어졌으면 `beanClass` 메타데이터만으로 `isAssignableFrom` 확인, 인스턴스화하지 않음) 양쪽을 훑어 후보를 모은다. 후보 0개면 `NoSuchBeanException`, 2개 이상이면 `NoUniqueBeanException`. `typeLookupDoesNotInstantiateNonMatchingCandidates` 테스트로 "타입이 다른 BeanDefinition 후보는 생성하지 않는다"까지 검증 — 8·9번에서 확인한 Spring의 실제 동작(`getBeanByTypeWithPrimary`)과 같은 설계다.

**생략한 것** (project 2 범위 밖, 이후 다른 프로젝트에서 다룰 대상)
- 조기 참조를 통한 순환 참조 **해결**(9번의 setter 순환처럼 부분 완성된 참조를 주입해 주는 것) — 지금은 순환을 정확히 탐지해서 실패시킬 뿐, Spring처럼 singleton+setter 조합을 실제로 풀어주지는 못한다. project 17의 목표("해결이 아니라 탐지")와 정확히 일치한다.
- 생성자/필드 의존성 주입 자체(project 15) — 지금 타입 조회와 순환 참조 테스트 모두 정적 필드로 factory를 미리 꽂아 두는 방식으로 DI를 흉내냈다.
- **구조적 차이**: Spring은 캐시 조회(`DefaultSingletonBeanRegistry.getSingleton`)와 생성 방법(`AbstractAutowireCapableBeanFactory`)이 `ObjectFactory` 람다로 분리돼 있다(11번 참고). `SimpleBeanFactory`는 `getBean` → `createBean`(재진입 감지) → `instantiate`(리플렉션)로 3단계는 나눴지만, 캐시 관리와 생성 로직이 여전히 같은 클래스 안에 있다.

**재진입 감지를 추가하고 나서 드러난 함정** ([`SimpleBeanFactoryCircularReferenceTest`](../../mini-spring/mini-container/src/test/java/lab/minispring/container/SimpleBeanFactoryCircularReferenceTest.java)) — 처음 `beanCreationPath` 검사만 추가했을 때는 테스트가 실패했다. `mini-container`가 아직 생성자 주입이 없어(project 15) 정적 필드에 factory를 꽂아 두고 각 빈의 생성자가 서로 `getBean()`을 호출하게 만들어 순환을 흉내내다 보니, `CircularDependencyException`이 던져지는 지점이 `Constructor#newInstance()` 경계 **안쪽**이었다. 그러면 이전 커밋에서 본 것과 똑같은 문제가 재발한다 — `instantiate()`의 `catch (ReflectiveOperationException e)`가 그 예외를 재귀 단계마다 다시 `BeanInstantiationException`으로 감싸버려서, 탐지는 되지만 바깥에는 여전히 알아보기 힘든 형태로 도달했다. `InvocationTargetException`을 벗겨서 원인이 `RuntimeException`이면 그대로 던지도록 고치자(Spring의 `BeanUtils.instantiateClass`가 `ex.getTargetException()`을 쓰는 것과 같은 발상) 비로소 `CircularDependencyException`이 깨끗하게 표면까지 올라왔다. 실제 project 15(생성자 주입)에서는 의존성 해석이 리플렉션 호출 *이전*에 순수 Java 코드로 일어나므로 이 문제 자체가 없을 것으로 보이지만, 지금의 "생성자가 스스로 컨테이너를 되부르는" 시뮬레이션 방식에서는 반드시 마주치는 함정이었다.

## 11. Spring 설계 의도

- **캐시 조회와 생성 로직의 분리**: `getSingleton(name, ObjectFactory)`가 생성 로직을 람다로 받는 구조이기 때문에, `DefaultSingletonBeanRegistry`는 "어떻게 만드는지"를 몰라도 되고 오직 캐시 관리(락, 조기 노출, 예외 시 정리)에만 집중할 수 있다. 실제 생성 방법(`AbstractAutowireCapableBeanFactory`)과 캐시 정책(`DefaultSingletonBeanRegistry`)이 다른 클래스로 분리된 이유다.
- **조기 참조 체크를 항상 거치는 이유**: 순환 참조 여부를 미리 판단해서 분기하는 대신, 모든 싱글턴 생성이 "생성 전 조기 참조 확인 → 생성 → 생성 후 조기 노출 여부 재확인"이라는 동일한 경로를 지나가게 만들면, 순환 참조 지원 코드가 특수 케이스가 아니라 정상 경로의 일부가 된다. 코드 경로가 하나로 통일되므로 예외 케이스가 줄고, AOP 프록시가 섞인 순환 참조(10주차 주제)도 같은 메커니즘으로 처리할 수 있다.
- **타입 조회가 이름 스캔을 거치는 이유**: `BeanFactory`의 1급 식별자는 "이름"이다(`BeanDefinitionRegistry`가 이름으로 등록/조회되므로). 타입 기반 조회는 이름 기반 계약 위에 얹은 편의 기능이라, 후보 이름들을 먼저 찾아야 한다 — 이는 타입 기반 조회가 이름 기반 조회보다 근본적으로 더 비싼 연산임을 의미한다.

## 12. 결론 (예상과 실제의 차이)

- 예상과 다르게, `getBean(Class)`는 `getBean(String)`의 단순 wrapper가 아니라 **먼저 후보 이름을 찾는 별도의 스캔 단계**(`getBeanNamesForType`)를 거친 뒤에야 이름 기반 경로로 들어간다.
- 예상과 다르게, 순환 참조가 전혀 없는 단일 빈 생성에서도 **조기 참조 체크(`allowEarlyReference`)가 생성 전/후 두 번 실행**된다 — 순환 참조 처리는 특수 분기가 아니라 모든 싱글턴 생성이 지나가는 공통 경로이며, 공식 테스트(`extensiveCircularReference` vs `circularReferenceThroughAutowiring` vs `prototypeCircleLeadsToException`)로 그 체크가 실제로 순환을 해결해 주는 조합은 "singleton + setter/property 주입"뿐이라는 것도 확인했다.
- 새로 열린 질문: `getBeanByTypeWithPrimary`처럼 후보가 여러 개일 때 `@Primary`/우선순위 판정이 어떤 순서로 이뤄지는지는 9주차(생성자 주입과 의존성 탐색)·10주차(`@Primary`, `@Qualifier`) 주제에서 더 깊이 다룬다.
