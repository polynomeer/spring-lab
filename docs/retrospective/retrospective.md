# 핵심 16주 회고 — Spring 내부 구조를 다시 짜 보며 배운 것

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)가 요구하는 최종 산출물이다. 1주차(IoC와 BeanFactory)부터 16주차(컨트롤러 메서드 호출과 응답 변환)까지, 8단계(IoC 컨테이너 → 빈 생명주기 → 확장점 → 컴포넌트 스캔/DI → AOP → 트랜잭션 → Spring MVC)를 관통하며 반복적으로 나타난 패턴과 직접 부딪힌 버그들을 모은다. 개별 주차의 세부 내용은 각 문서를 참고하고, 이 문서는 그 16개 문서를 가로지르는 결을 정리하는 데 집중한다.

## 0. 숫자로 보는 16주

- 주제 문서 16개(`docs/01-*` ~ `docs/16-*`), 그중 15개에 mermaid 다이어그램 포함(6주차는 새 실험 없이 4·5주차를 종합하는 회고 성격이라 다이어그램 없이 소스 분석만 남김)
- 코드 모듈 22개: `experiments/` 10개(실제 Spring으로 검증), `mini-spring/` 7개(축소 재구현), `spring-extensions/` 4개(실제 확장점 활용), `tools/` 1개(`jdi-tracer`)
- 자동화 테스트 196개, 전부 통과(`./gradlew build` 기준)
- 공식 `spring-framework` 소스(v6.2.19 태그)를 근거로 인용한 주요 클래스/메서드: `DefaultListableBeanFactory`, `AbstractAutowireCapableBeanFactory`, `ConfigurationClassEnhancer`, `AutowiredAnnotationBeanPostProcessor`, `AbstractAutoProxyCreator`, `AbstractAdvisorAutoProxyCreator`, `TransactionAspectSupport`, `AbstractPlatformTransactionManager`, `DispatcherServlet`, `InvocableHandlerMethod` 등

## 1. 8단계 로드맵 요약

| 단계 | 주차 | 핵심 발견 한 줄 | 문서 |
| --- | --- | --- | --- |
| IoC 컨테이너 | 1~2 | `getBean(Class)`는 이름 기반 조회의 wrapper가 아니라 별도 스캔 단계를 거친다 - `BeanDefinition`의 메타데이터는 등록 경로(컴포넌트 스캔 vs `@Bean` vs 수동 등록)에 따라 형태 자체가 다르다 | [1주차](../01-ioc-container/bean-factory-getbean.md), [2주차](../02-bean-definition/bean-definition-registration.md) |
| `refresh()`와 빈 생명주기 | 3~4 | `refresh()`의 12단계는 고정된 순서지만, 그 안에서 실행되는 `BeanPostProcessor`의 순서는 "우선순위 인터페이스"보다 "언제 재등록됐는가"가 더 강하게 좌우한다 | [3주차](../03-context-refresh/context-refresh.md), [4주차](../04-bean-lifecycle/bean-lifecycle.md) |
| 확장점(BFPP/BPP) | 5~6 | 같은 인터페이스, 같은 코드라도 **등록 방법**이 **실행 시점**을 바꾼다 - 그리고 조기 노출된 참조와 최종 빈의 일치는 우연이 아니라 `earlyBeanReferences` 맵 하나로 명시적으로 보장된다 | [5주차](../05-beanfactory-postprocessor/beanfactory-postprocessor.md), [6주차](../06-beanpostprocessor/beanpostprocessor.md) |
| 컴포넌트 스캔·DI | 7~10 | 여러 후보 사이의 우선순위(생성자 선택, `@Primary`/`@Qualifier`, 순환 참조 해결)는 전부 "판단 로직"보다 "탐지/우회 규칙"에 가깝다 - `@Lazy`는 순환을 해결하는 게 아니라 순환 자체를 없애 버린다 | [7주차](../07-component-scan/component-scan.md), [8주차](../08-configuration-bean/configuration-bean.md), [9주차](../09-dependency-resolution/dependency-resolution.md), [10주차](../10-primary-qualifier-circular/primary-qualifier-circular.md) |
| Spring AOP | 11~12 | 프록시 기반 AOP의 모든 한계(self-invocation, final/private 메서드, equals/hashCode 우회)는 "버그"가 아니라 "프록시가 원본과 별개의 객체"라는 한 가지 구조적 사실에서 전부 파생된다 | [11주차](../11-proxy-interceptor/proxy-interceptor.md), [12주차](../12-auto-proxy-creator/auto-proxy-creator.md) |
| 트랜잭션 | 13~14 | `@Transactional`은 AOP 인터셉터 하나일 뿐이다 - checked 예외는 기본적으로 롤백하지 않고, `REQUIRES_NEW`는 진짜 다른 커넥션을, `NESTED`는 같은 커넥션의 savepoint를 쓴다는 것이 전파 속성의 실체다 | [13주차](../13-transactional-internals/transactional-internals.md), [14주차](../14-transaction-propagation/transaction-propagation.md) |
| Spring MVC | 15~16 | `DispatcherServlet`은 겨우 몇 줄짜리 반복문(`getHandler`)이고, 복잡성은 전부 `HandlerMapping`/`HandlerAdapter`/`ArgumentResolver`/`ReturnValueHandler`라는 잘게 쪼개진 확장점들이 만들어 낸다 | [15주차](../15-dispatcher-servlet/dispatcher-servlet.md), [16주차](../16-controller-invocation/controller-invocation.md) |

## 2. 16주에 걸쳐 반복된 설계 패턴

같은 원칙이 서로 다른 주제에서 계속 다시 나타났다는 것 자체가, 이 패턴들이 우연이 아니라 Spring 전체를 관통하는 설계 철학이라는 증거다.

### "정교한 판단"보다 "예측 가능한 순서"

- 생성자가 여럿이고 `@Autowired`가 없으면 Spring은 "가장 그럴듯한" 생성자를 추측하지 않고 기본 생성자로 물러난다(9주차).
- 여러 `HandlerMapping`이 등록되면 "더 적합한" 것을 고르지 않고 **등록 순서(order)** 대로 첫 매칭을 채택한다(15주차) - `HandlerMethodArgumentResolverComposite`도 똑같이 먼저 등록된 리졸버를 채택한다(16주차, 공식 테스트 `checkArgumentResolverOrder`로 확인).
- 이 원칙의 예외처럼 보이는 것(리터럴 경로가 변수 경로를 항상 이기는 것, 15주차)도 사실은 "등록 순서"가 아니라 "패턴 자체의 구조(`PathPattern.SPECIFICITY_COMPARATOR`)"라는 또 다른 고정된 규칙일 뿐, 여전히 "실행 시점의 똑똑한 판단"은 아니다.
- 왜 반복되는가: 똑똑한 휴리스틱은 코드베이스가 커질수록 "왜 이번엔 다르게 골랐지?"라는 디버깅 비용을 만든다. Spring은 일관되게 "무엇이 우선인지 예측 가능하게 만드는 것"을 "가장 좋은 것을 자동으로 고르는 것"보다 우선시한다.

### 확장점은 좁고 합성 가능하게 쪼갠다

- `BeanFactoryPostProcessor`(정의 수정)과 `BeanPostProcessor`(인스턴스 개입)는 서로 다른 시점의 서로 다른 관심사라 분리됐다(5~6주차).
- `HandlerMapping`("무엇을 호출할지")과 `HandlerAdapter`("어떻게 호출할지")가 분리된 것도 같은 이유다(15주차).
- 16주차에서 가장 선명하게 드러난 사례: `HandlerMethodReturnValueHandler`(반환 타입 전체를 다시 정의하는 무거운 확장점)와 `ResponseBodyAdvice`(이미 있는 처리 파이프라인에 살짝 끼어드는 가벼운 확장점)가 분리돼 있다는 것을 모르고 직접 `ReturnValueHandler`를 만들면, 전역 `ResponseBodyAdvice`를 조용히 우회해 버리는 함정에 빠진다 - 실제로 겪었다.
- 왜 반복되는가: 좁은 확장점은 서로 합성(compose)할 수 있지만, 넓은 확장점은 서로 겹치거나 충돌한다. Pointcut+Advice(11주차), BeanFactoryAdvisorRetrievalHelper의 `AopUtils.canApply()`(12주차)도 전부 "판정 로직 하나 + 그걸 호출하는 배선"으로 쪼개져 있어서 재사용이 가능했다.

### 프록시는 원본과 별개의 객체라는 사실 하나가 만드는 모든 것

- self-invocation이 어떤 어드바이스(로깅, 트랜잭션, 커스텀 프록시)에도 걸리지 않는 이유(11~13주차)
- `final`/`private` 메서드가 CGLIB로도 가로챌 수 없는 이유(11주차)
- `equals`/`hashCode`가 인터셉터 체인을 건너뛰는 이유(11주차) - 프록시 자신의 정체성으로 비교해야 하기 때문
- 조기 노출된 참조가 최종 등록된 프록시와 반드시 같아야 하는 이유, 그리고 그것을 보장하기 위해 `earlyBeanReferences` 맵이 필요한 이유(6주차)
- 이 모든 것이 "프록시 구현이 미숙해서"가 아니라 "프록시가 대상 인스턴스를 감싸는 별개의 객체"라는 한 가지 구조적 전제에서 논리적으로 따라 나온다는 것을, 11주차부터 13주차까지 세 번 다른 맥락(순수 AOP → 자동 프록시 생성 → `@Transactional`)에서 반복 확인했다.

### ThreadLocal 기반 자원 바인딩

- 순환 참조 해결의 3단계 캐시(`singletonObjects`/`earlySingletonObjects`/`singletonFactories`, 1·6주차)
- 트랜잭션의 `TransactionSynchronizationManager`(스레드에 `Connection`을 바인딩, 13~14주차)
- 둘 다 "현재 진행 중인 작업의 상태를 스레드에 걸어 두고, 나중에 같은 스레드의 다른 코드가 그 상태를 다시 찾아 쓴다"는 같은 메커니즘이다. mini 구현(`mini-container`의 `beanCreationPath`, `mini-transaction`의 `holderThreadLocal`)에서 이 메커니즘을 직접 만들어 보고서야, "왜 ThreadLocal인가"(같은 스레드=같은 논리적 작업 단위)가 왜 자연스러운 선택인지 체감했다.

## 3. 구현하다가 실제로 발견한 버그들

이 목록 자체가 이 학습 방식(읽기만 하지 않고 직접 짜 보기)이 왜 효과적이었는지를 보여준다 - 전부 "이해했다고 생각했는데 실행해 보니 아니었던" 순간들이다.

| 주차 | 버그 | 원인 |
| --- | --- | --- |
| 5 | `BeanFactoryPostProcessor`를 빈으로 등록했더니 컴포넌트 스캔보다 늦게 실행됨 | 등록 **방법**이 실행 **시점**을 바꾼다는 것을 몰랐던 것 |
| 10 | `@Lazy` 순환 참조 테스트에서 `isSameAs` 실패 | 양쪽에 `@Lazy`를 붙여서 프록시가 프록시를 감싸는 상황을 만듦 |
| 12 | 두 컨텍스트가 서로의 컴포넌트 스캔에 걸려 한 곳에서 같은 빈을 두 번 프록시함 | `@Configuration`도 `@Component`라서, 같은 패키지를 스캔하는 두 설정이 서로를 주워 담음 |
| 12 | `BeanPostProcessor`용 `@Bean`을 인스턴스 메서드로 선언 | `@Configuration` 클래스 본체가 먼저 완성돼야 해서 등록 타이밍을 놓침 - `static`으로 고침 |
| 14 | rollback-only 전파를 추가하자 "connection is closed" 오류 | `MiniTransactionInterceptor`가 `commit()`을 `proceed()`의 `try` 안에 둬서, `commit()`이 던진 예외를 "실행 실패"로 오인해 이미 끝난 트랜잭션을 또 롤백하려 함 - `TransactionAspectSupport`처럼 `commit()`을 `try` 밖으로 옮겨 고침 |
| 15 | Jackson 없이 `@RestController` 테스트가 406/415로 실패, 동시에 인터셉터 순서 테스트에서 `postHandle` 누락 | 하나의 누락된 의존성(Jackson)이 서로 무관해 보이는 두 테스트를 동시에 깨뜨림 - 예외 경로가 `postHandle`을 건너뛰기 때문 |
| 16 | mini-webmvc에 경로 변수를 추가하자 `/api/users/wrapped`가 이따금 500으로 실패 | `Class#getMethods()`의 순회 순서가 보장되지 않아 `/api/users/{id}`가 먼저 등록되면 `id="wrapped"`로 해석됨 - 15주차에서 확인한 리터럴 vs 변수 경로 specificity 문제가 mini에서 그대로 재발 |

마지막 두 항목은 특히 인상적이다 - 14주차의 버그는 "TransactionAspectSupport의 실제 구조를 다시 확인해서" 고쳤고, 16주차의 버그는 "15주차에 이미 배운 교훈"이 새 기능을 추가하자마자 다시 검증을 요구한 사례다. 매주 배운 것이 다음 주로 그냥 넘어가는 게 아니라, 코드를 확장할 때마다 다시 시험대에 오른다.

## 4. 로드맵이 제시한 4가지 핵심 주제에 대한 답

### Spring은 어떻게 확장 가능한 객체 생성 파이프라인을 만들었는가?

`BeanDefinition`(1~2주차)이라는 메타데이터 계층으로 "무엇을 어떻게 만들지"를 실제 생성과 분리하고, `refresh()`(3주차)라는 고정된 12단계 파이프라인 곳곳에 `BeanFactoryPostProcessor`/`BeanPostProcessor`(5~6주차)라는 확장점을 심어 뒀다. 각 확장점은 좁은 책임(정의 수정 vs 인스턴스 개입, 4주차의 `MergedBeanDefinitionPostProcessor` vs 일반 `BeanPostProcessor` 구분까지)만 지므로, 서로 다른 관심사(AOP 프록시, `@PostConstruct`, `@Autowired` 등)가 전부 이 하나의 파이프라인 위에서 충돌 없이 공존한다.

### `BeanPostProcessor`는 왜 Spring 확장성의 중심인가?

인스턴스를 "고치는" 정도가 아니라 **완전히 다른 객체로 통째로 교체**할 수 있기 때문이다(5주차, `MethodTimingBeanPostProcessor`가 원본 대신 프록시를 반환하는 실험). `AbstractAutoProxyCreator`(AOP 자동 프록시, 6·12주차)와 `AbstractAdvisorAutoProxyCreator`(11~12주차)가 전부 이 하나의 확장점 위에서 구현된 기능이라는 것을 확인했다 - Spring AOP는 별도의 특별한 메커니즘이 아니라 `BeanPostProcessor`의 한 가지 활용 사례일 뿐이다.

### `@Transactional`은 애노테이션 하나로 어떻게 JDBC Connection을 관리하는가?

`@Transactional`은 결국 `TransactionInterceptor`라는 `MethodInterceptor` 하나다(13주차) - 11~12주차에서 다룬 프록시/AOP 인프라 위에 그대로 얹힌 것이다. 실제 `Connection` 관리는 `TransactionSynchronizationManager`가 스레드에 바인딩한 `ConnectionHolder`(14주차)를 통해 이뤄지고, `DataSourceUtils.getConnection()`이 이 바인딩을 조회/생성하는 창구 역할을 한다. `REQUIRED`(참여)/`REQUIRES_NEW`(suspend 후 새 커넥션)/`NESTED`(같은 커넥션의 savepoint)는 전부 "이 스레드 바인딩을 어떻게 다룰 것인가"에 대한 서로 다른 정책일 뿐, 근본 메커니즘은 하나다.

### `DispatcherServlet`은 다양한 컨트롤러 호출 방식을 어떻게 추상화하는가?

`DispatcherServlet` 자신은 "핸들러가 무엇인지" 전혀 모른다(15주차) - `HandlerMapping`이 핸들러를 찾고, 그 핸들러의 "형태"를 아는 `HandlerAdapter`가 실제 호출을 담당한다. 애노테이션 기반 컨트롤러(`HandlerMethod`)의 경우 그 호출 자체도 다시 `ArgumentResolver`(인자 채우기)와 `ReturnValueHandler`+`ResponseBodyAdvice`(응답 만들기)로 잘게 쪼개져 있다(16주차). 결과적으로 `DispatcherServlet`부터 시작하는 요청 처리 전체가, 각자 좁은 책임을 지는 확장점들의 체인일 뿐 하나의 거대한 로직 덩어리가 아니라는 것이 이번 학습에서 가장 분명해진 그림이다.

## 5. Mini Spring Framework 지도

각 mini 모듈이 서로 어떻게 의존하는지, 어느 주차에 만들어졌는지:

```text
mini-container (1·4·7·8·9·10주차, project 2/7/10/13/15/17)
  └─ IoC 컨테이너 핵심: BeanDefinition, BeanPostProcessor, 생성자 주입, 순환 참조 탐지
mini-component-scan (7주차, project 10) → mini-container 확장
mini-java-config (8주차, project 13) → mini-container에 의존

mini-aop (11주차, project 19)
  └─ MethodInterceptor/MethodInvocation/MiniProxyFactory - mini-container와 독립적
mini-auto-proxy (12주차, project 20) → mini-container + mini-aop에 의존
  └─ BeanPostProcessor(mini-container)와 인터셉터 체인(mini-aop)이 만나는 지점
mini-transaction (13~14주차, project 22) → mini-aop에 의존
  └─ MiniTransactionInterceptor는 mini-aop의 MethodInterceptor 하나일 뿐

mini-webmvc (15~16주차, project 27)
  └─ Servlet API 위의 독립적인 요청 처리 파이프라인 - 다른 mini 모듈에 의존하지 않음
```

이 의존 구조 자체가 4번의 답을 그대로 반영한다 - `mini-auto-proxy`가 "빈 생성 후처리"(mini-container)와 "인터셉터 체인"(mini-aop)을 잇는 지점이라는 것, `mini-transaction`이 그 인터셉터 체인 위에 정책 하나를 얹은 것뿐이라는 것.

## 6. 학습 완료 기준 자가 점검

`docs/plan/01-roadmap.md`의 기준을 그대로 가져와 각 항목의 근거를 링크한다.

**초급**
- IoC와 DI 구분: [1주차](../01-ioc-container/bean-factory-getbean.md)
- `BeanFactory`/`ApplicationContext` 설명: [1주차](../01-ioc-container/bean-factory-getbean.md), [3주차](../03-context-refresh/context-refresh.md)
- `BeanDefinition`의 역할: [2주차](../02-bean-definition/bean-definition-registration.md)
- 빈 생명주기 순서: [4주차](../04-bean-lifecycle/bean-lifecycle.md)

**중급**
- `refresh()`의 주요 단계: [3주차](../03-context-refresh/context-refresh.md)(`tools/jdi-tracer`로 12단계 전부 실제 호출 스택 확인)
- 컴포넌트 스캔/설정 클래스 파싱 흐름: [7주차](../07-component-scan/component-scan.md), [8주차](../08-configuration-bean/configuration-bean.md)
- `@Autowired` 후보 선택 과정: [9주차](../09-dependency-resolution/dependency-resolution.md), [10주차](../10-primary-qualifier-circular/primary-qualifier-circular.md)
- `BeanPostProcessor`와 프록시 생성의 관계: [5주차](../05-beanfactory-postprocessor/beanfactory-postprocessor.md) 13번 절, [6주차](../06-beanpostprocessor/beanpostprocessor.md)

**고급**
- AOP 인터셉터 체인을 소스코드로 추적: [11주차](../11-proxy-interceptor/proxy-interceptor.md)(`ReflectiveMethodInvocation` 실제 구조까지 mini와 비교)
- `@Transactional` 호출 흐름과 전파 속성: [13주차](../13-transactional-internals/transactional-internals.md), [14주차](../14-transaction-propagation/transaction-propagation.md)
- `DispatcherServlet`부터 응답 직렬화까지: [15주차](../15-dispatcher-servlet/dispatcher-servlet.md), [16주차](../16-controller-invocation/controller-invocation.md)
- Spring 내부에 직접 브레이크포인트 설정: `tools/jdi-tracer`로 1·3주차에서 실제 사용 - 이후 주차는 실행 결과(공식 테스트 재현)와 소스 확인으로 대체하는 경우가 많아졌다(효율을 위한 의도적 선택, 각 문서 7번 절에 명시)
- 단순 사용법이 아니라 설계 의도 설명: 각 문서의 11번 절("Spring 설계 의도") 16개 전부

## 7. 남겨 둔 질문과 다음 단계

의도적으로 범위 밖에 둔 것들(각 문서 10번 절에 기록됨) 중 특히 다시 다뤄볼 만한 것:

- mini 구현들의 일관된 생략: JSON 실제 역직렬화(mini-webmvc), CGLIB 상당 서브클래스 프록시(mini-aop), NESTED 전파(mini-transaction), `@ControllerAdvice` 전역 예외 처리(mini-webmvc) - 전부 "핵심 메커니즘을 이해하는 데는 필요 없었던" 것들이다.
- 7주차에서 남긴 ASM 기반 컴포넌트 스캔의 실제 성능/안전성 비교는 시도하지 않았다.
- 16주차에서 발견한 mini-webmvc의 인자 리졸버 캐싱 부재는 정확성에는 영향 없지만 실제라면 성능 이슈가 됐을 것이다.

다음은 로드맵의 선택 과정(17~20주차, Spring Boot 내부 - `SpringApplication`, 자동 설정, 조건부 설정, 직접 만드는 Starter)이다. 이 16주가 확인한 확장점들(`BeanFactoryPostProcessor`, `BeanPostProcessor`, `Condition`)이 Spring Boot의 자동 설정 메커니즘에서 그대로 다시 등장할 것으로 예상한다.
