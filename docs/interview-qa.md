# Spring 내부 구조 Q&A — 기초부터 면접 실전까지

이 저장소(`docs/plan/01-roadmap.md`의 8단계 + 선택 4주 + 심화 주제)에서 실제로 읽고, 실행하고, 디버깅하고, 축소 재구현하며 검증한 내용을 바탕으로 정리한 질문/답변 모음이다. 일반적인 "Spring 면접 예상 질문" 목록과 다른 점은, 여기 적힌 답변 대부분이 이 저장소 안의 실제 소스 확인·런타임 관찰·테스트로 뒷받침된다는 것이다 — 각 답변 끝에 근거가 되는 문서를 링크해 뒀으니, "왜 그런지 더 보고 싶다"는 질문에는 그 문서를 따라가면 된다.

구성은 로드맵의 단계(1~8단계 + Boot + 심화 주제) 순서를 그대로 따르고, 각 단계마다 **기초 → 중급 → 심화** 세 층위로 질문을 쌓았다. 마지막 절은 "꼬리 질문이 이어지는" 실전 면접 시나리오다.

---

## 목차

1. [IoC 컨테이너와 빈](#1-ioc-컨테이너와-빈)
2. [빈 생명주기](#2-빈-생명주기)
3. [컨테이너 확장 포인트](#3-컨테이너-확장-포인트)
4. [설정 클래스와 컴포넌트 스캔](#4-설정-클래스와-컴포넌트-스캔)
5. [의존성 주입](#5-의존성-주입)
6. [Spring AOP](#6-spring-aop)
7. [트랜잭션](#7-트랜잭션)
8. [Spring MVC](#8-spring-mvc)
9. [Spring Boot 내부 동작](#9-spring-boot-내부-동작)
10. [애플리케이션 이벤트](#10-애플리케이션-이벤트)
11. [실전 면접 시나리오](#11-실전-면접-시나리오)

---

## 1. IoC 컨테이너와 빈

### 기초

**Q. IoC(제어의 역전)와 DI(의존성 주입)는 어떻게 다른가?**
IoC는 "객체 생성과 의존관계 관리의 주도권을 객체 자신이 아니라 외부(컨테이너)가 갖는다"는 원칙이고, DI는 그 IoC를 구현하는 구체적인 방법 중 하나다(생성자 주입, 세터 주입, 필드 주입). IoC의 다른 구현 방법으로 서비스 로케이터 패턴 등도 있지만, Spring은 DI를 기본 전략으로 채택했다.

**Q. `BeanFactory`와 `ApplicationContext`의 차이는?**
`BeanFactory`는 빈 등록·조회·생성이라는 컨테이너의 최소 계약이고, `ApplicationContext`는 그 위에 `MessageSource`(국제화), `ApplicationEventPublisher`(이벤트), `Environment`(프로파일/프로퍼티), `BeanFactoryPostProcessor`/`BeanPostProcessor` 자동 등록 같은 엔터프라이즈 기능을 얹은 상위 인터페이스다. 실무에서 `BeanFactory`를 직접 쓸 일은 거의 없다 — `ApplicationContext`가 사실상 표준이다.
→ [`experiments/ioc-container-lab`](../experiments/ioc-container-lab), [`mini-spring/mini-container`](../mini-spring/mini-container)

**Q. `BeanDefinition`이 하는 역할은?**
실제 빈 인스턴스가 아니라 "이 빈을 어떻게 만들 것인가"에 대한 메타데이터다 — 클래스 이름(또는 팩토리 메서드), 스코프, 지연 초기화 여부, 생성자/프로퍼티 값, 의존 관계 등을 담는다. 컨테이너 초기화의 첫 단계는 이 `BeanDefinition`들을 모으는 것이고, 실제 인스턴스화는 그 다음이다 — "정의"와 "생성"이 분리돼 있다는 게 핵심이다.
→ [`experiments/bean-definition-inspector`](../experiments/bean-definition-inspector)

### 중급

**Q. 싱글톤 스코프인데 왜 스레드 안전성을 따로 신경 써야 하는가?**
싱글톤은 "컨테이너당 인스턴스 하나"를 보장할 뿐, 그 인스턴스의 상태 필드에 대한 동시 접근 안전성은 전혀 보장하지 않는다. 상태가 없는(stateless) 빈이라면 문제없지만, 가변 필드를 인스턴스 변수로 두면 여러 스레드가 동시에 그 필드를 건드리는 전형적인 동시성 문제가 그대로 발생한다.

**Q. 순환 참조(circular dependency)는 왜 생성자 주입에서는 해결이 안 되는가?**
Spring의 순환 참조 해결(3단계 캐시)은 "아직 프로퍼티가 채워지지 않은, 하지만 이미 인스턴스화는 끝난" 빈을 조기 참조로 내어주는 방식이다. 생성자 주입은 인스턴스화 자체가 의존 빈의 생성을 전제로 하므로, "아직 인스턴스화되지 않은" 빈을 조기 참조로 내어줄 방법이 없다 — 인스턴스화 이전 시점의 순환은 근본적으로 끊을 지점이 없다.
→ [`experiments/circular-dependency-lab`](../experiments/circular-dependency-lab)

### 심화

**Q. 3단계 캐시(three-level cache)가 실제로 왜 3단계여야 하는가? 2단계로는 왜 안 되는가?**
- 1차 캐시(`singletonObjects`): 완전히 생성이 끝난 싱글톤.
- 2차 캐시(`earlySingletonObjects`): 조기 노출된, 아직 초기화가 덜 끝난 원본 인스턴스.
- 3차 캐시(`singletonFactories`): "필요한 시점에 조기 참조를 만들어 주는 팩토리".

2단계(1차+2차)만으로는 AOP 프록시 대상 빈의 순환을 처리할 수 없다. 프록시가 필요한 빈은 "원본 인스턴스"가 아니라 "프록시"가 최종적으로 주입돼야 하는데, 프록시를 씌울지 여부는 보통 초기화 후반(`BeanPostProcessor`)에 결정된다. 3차 캐시는 "아직 프록시할지 결정 안 된 원본을 넣어 두고, 실제로 누군가 조기 참조를 요청하는 시점에 그 팩토리를 실행해 (필요하면 프록시로) 만들어서 2차 캐시로 승격"하는 지연 평가 지점을 제공한다 — 이 지연 평가 지점이 없으면 "프록시가 아닌 원본 참조가 순환 관계에 있는 다른 빈에 주입돼 버리는" 문제가 생긴다.
→ [`docs/10-primary-qualifier-circular`](10-primary-qualifier-circular), [`mini-spring/mini-container`](../mini-spring/mini-container)

**Q. `getBean()`을 컨테이너 초기화 도중(예: `BeanFactoryPostProcessor` 안에서) 호출하면 무슨 일이 생기는가?**
`BeanFactoryPostProcessor`는 아직 `BeanDefinition` 조작 단계이지 빈 인스턴스화 단계가 아니다. 이 시점에 `getBean()`을 호출하면 그 빈이 다른 `BeanFactoryPostProcessor`들이 아직 반영하지 않은 `BeanDefinition`을 기준으로 조기 인스턴스화되어 버린다 — Spring이 이런 조기 인스턴스화를 강하게 권장하지 않고 로그 경고까지 남기는 이유다("BeanFactoryPostProcessor가 다른 BeanPostProcessor 등록보다 먼저 실행되도록" 하는 로직과 연결된다).

---

## 2. 빈 생명주기

### 기초

**Q. 빈 생명주기의 큰 순서는?**
`BeanDefinition` 등록 → 인스턴스 생성(생성자 호출) → 프로퍼티 주입(`populateBean`) → `Aware` 콜백(`BeanNameAware` 등) → `BeanPostProcessor#postProcessBeforeInitialization` → `@PostConstruct`/`InitializingBean#afterPropertiesSet`/커스텀 init 메서드 → `BeanPostProcessor#postProcessAfterInitialization` → (사용) → 컨테이너 종료 시 `@PreDestroy`/`DisposableBean#destroy`.
→ [`experiments/bean-lifecycle-recorder`](../experiments/bean-lifecycle-recorder), [`mini-spring/mini-container`](../mini-spring/mini-container)

**Q. `@PostConstruct`와 생성자의 차이는 무엇이고, 왜 둘 다 필요한가?**
생성자가 호출되는 시점에는 아직 의존성 주입(프로퍼티/세터 주입)이 끝나지 않았을 수 있다(생성자 주입이 아닌 경우). `@PostConstruct`는 "모든 의존성 주입이 끝난 뒤"라는 시점을 보장받는 확장 지점이라, "주입된 의존성을 활용한 초기화 로직"은 생성자가 아니라 `@PostConstruct`에 둬야 안전하다.

### 중급

**Q. `BeanPostProcessor`의 두 콜백(`postProcessBeforeInitialization`/`postProcessAfterInitialization`) 사이에는 정확히 무엇이 실행되는가?**
그 사이에 `@PostConstruct`/`InitializingBean#afterPropertiesSet`/`@Bean(initMethod=...)` 같은 "초기화 메서드"들이 실행된다. 즉 `postProcessBeforeInitialization`은 "초기화 메서드가 실행되기 직전", `postProcessAfterInitialization`은 "초기화 메서드까지 다 끝난 직후"다. AOP 프록시가 만들어지는 지점(`AbstractAutoProxyCreator`)이 바로 `postProcessAfterInitialization`인 이유는, 초기화 로직까지 전부 반영된 "완성된" 원본 객체를 감싸야 하기 때문이다.
→ [`docs/12-auto-proxy-creator`](12-auto-proxy-creator)

**Q. 프로토타입 스코프 빈에도 `@PreDestroy`가 호출되는가?**
아니다. 싱글톤은 컨테이너가 생명주기를 끝까지 관리하지만, 프로토타입 빈은 컨테이너가 생성 후에는 관리를 넘겨준다(그 인스턴스에 대한 참조를 컨테이너가 갖고 있지 않음) — 그래서 컨테이너 종료 시 프로토타입 빈의 소멸 콜백은 호출되지 않는다. 이는 자주 놓치는 실무 함정이다(리소스를 여는 프로토타입 빈은 직접 정리해야 한다).

### 심화

**Q. `SmartInitializingSingleton`은 왜 따로 있는가? `@PostConstruct`로는 부족한 경우가 있는가?**
`@PostConstruct`는 "그 빈 자신의 초기화가 끝났다"는 것만 보장하지, "다른 모든 싱글톤 빈들의 초기화까지 전부 끝났다"는 것은 보장하지 않는다(빈 생성 순서에 따라 아직 안 만들어진 다른 빈이 있을 수 있다). `SmartInitializingSingleton#afterSingletonsInstantiated()`는 "모든 non-lazy 싱글톤이 전부 준비된 뒤"에 호출되므로, "다른 빈들의 존재를 전제로 하는 후처리"(예: 전체 핸들러 목록을 스캔해서 매핑 테이블을 만드는 것)에 적합하다.

**Q. `initializeBean` 내부에서 예외가 나면 이미 등록된 `DisposableBean` 콜백은 어떻게 되는가?**
초기화 도중 실패한 빈은 애초에 싱글톤 레지스트리에 등록되지 않으므로(등록은 생성이 성공적으로 끝난 뒤에 이뤄진다), 그 실패한 빈에 대한 `destroy()`가 나중에 호출될 일도 없다 — 다만 이미 생성된 다른 협력 빈들에 대한 정리는 컨테이너가 별도로 책임진다.

---

## 3. 컨테이너 확장 포인트

### 기초

**Q. `BeanFactoryPostProcessor`와 `BeanPostProcessor`는 어떻게 다른가?**
`BeanFactoryPostProcessor`는 빈이 인스턴스화되기 **전**, `BeanDefinition` 메타데이터 자체를 조작하는 확장 지점이다(예: 프로퍼티 플레이스홀더 치환). `BeanPostProcessor`는 빈이 인스턴스화된 **후**, 개별 빈 인스턴스를 가로채 감싸거나 검사하는 확장 지점이다(예: AOP 프록시 생성). 전자는 "설계도를 고치는 것", 후자는 "완성품을 검수/개조하는 것"에 가깝다.

### 중급

**Q. `@Autowired` 처리는 어떤 `BeanPostProcessor`가 담당하는가?**
`AutowiredAnnotationBeanPostProcessor`다. 생성자 주입은 `InstantiationAwareBeanPostProcessor#determineCandidateConstructors`에서(인스턴스화 시점 자체에 개입), 필드/세터 주입은 `postProcessProperties`(옛 `postProcessPropertyValues`)에서(인스턴스화 후, 프로퍼티 주입 단계에) 처리된다 — 같은 애노테이션이라도 주입 방식에 따라 개입 시점이 다르다는 게 포인트다.

### 심화

**Q. `BeanFactoryPostProcessor`들끼리도 순서가 있는가? `@Configuration` 클래스는 언제 처리되는가?**
`ConfigurationClassPostProcessor`(가장 대표적인 `BeanFactoryPostProcessor`)가 `PriorityOrdered`를 구현해서 다른 사용자 정의 `BeanFactoryPostProcessor`보다 먼저 실행되도록 보장된다 — `@Configuration`/`@ComponentScan`/`@Import` 등을 해석해서 새로운 `BeanDefinition`들을 등록하는 일 자체가 하나의 `BeanFactoryPostProcessor`이기 때문에, 다른 `BeanFactoryPostProcessor`가 그 결과(새로 등록된 빈들)에 의존할 수 있으려면 이게 먼저 끝나야 한다. `invokeBeanFactoryPostProcessors()`가 `PriorityOrdered` → `Ordered` → 나머지 순으로 그룹을 나눠 실행하는 이유다.

---

## 4. 설정 클래스와 컴포넌트 스캔

### 기초

**Q. `@Bean`으로 등록한 메서드와 `@Component` + 컴포넌트 스캔의 차이는?**
`@Bean`은 개발자가 직접 인스턴스 생성 로직을 코드로 작성하는 방식(제어 가능, 서드파티 클래스에도 적용 가능)이고, `@Component`(+ `@ComponentScan`)는 클래스 자체에 애노테이션을 붙여 컨테이너가 자동으로 찾아 등록하게 하는 방식이다. 내가 소스를 수정할 수 없는 라이브러리 클래스는 `@Bean`으로만 등록할 수 있다.

### 중급

**Q. `@Configuration` 클래스가 CGLIB로 프록시되는 이유는?**
같은 `@Configuration` 클래스 안에서 한 `@Bean` 메서드가 다른 `@Bean` 메서드를 **자바 메서드 호출**로 참조해도(`return new Foo(bar())` 형태), 그 `bar()` 호출이 매번 새 인스턴스를 만드는 게 아니라 컨테이너의 싱글톤 레지스트리를 거치도록 강제해야 한다. CGLIB 프록시가 이 메서드 호출을 가로채서 "이미 등록된 싱글톤이 있으면 그걸 반환하고, 없으면 실제로 호출해서 등록 후 반환"하는 식으로 동작한다 — `proxyBeanMethods = false`로 끄면 이 보장이 사라지고 순수 자바 호출이 된다.
→ [`experiments/configuration-proxy-lab`](../experiments/configuration-proxy-lab)

### 심화

**Q. 리터럴 경로(`/users/me`)와 변수 경로(`/users/{id}`)가 같은 컨트롤러에 있을 때 어느 게 우선하는가? (URL 매핑도 결국 "더 구체적인 것 우선"이라는 설계 원칙이 반복되는 사례)**
리터럴 경로가 항상 우선한다 — 등록 순서와 무관하다. `PathPattern` 비교 로직이 "변수가 없는, 더 구체적인 패턴"을 우선순위가 높다고 판단하기 때문이다. 이는 컴포넌트 스캔 자체의 질문은 아니지만, "더 구체적인 것이 이긴다"는 원칙이 Spring 전반(컨트롤러 로컬 `@ExceptionHandler` vs `@ControllerAdvice`, 리터럴 vs 변수 경로)에서 반복된다는 걸 보여 준다.
→ [`experiments/dispatcher-servlet-trace`](../experiments/dispatcher-servlet-trace)

---

## 5. 의존성 주입

### 기초

**Q. 생성자 주입을 필드 주입보다 권장하는 이유는?**
1) 의존성을 `final`로 선언할 수 있어 불변성이 보장된다. 2) 필수 의존성이 누락되면 컴파일이 아니라 최소한 객체 생성 시점(테스트에서 `new`로 직접 만들 때 포함)에 바로 드러난다 — 필드 주입은 Spring 컨테이너 없이는 그 누락을 발견할 방법이 없다. 3) 순환 참조가 있으면 (생성자 주입에서는 3단계 캐시로도 해결이 안 되므로) 컨테이너 기동 시점에 즉시 실패해서 설계 문제를 조기에 드러낸다.

### 중급

**Q. 후보 생성자가 여러 개면 어떤 걸 선택하는가?**
`@Autowired`가 붙은 생성자가 있으면 그걸 우선 사용한다. 없고 생성자가 하나뿐이면 그 생성자를 자동으로 사용한다(Spring 4.3+). 생성자가 여러 개인데 어느 것에도 `@Autowired`가 없으면, 파라미터가 없는 기본 생성자가 있어야 그걸 사용하고, 그렇지 않으면 예외가 발생한다.

**Q. 같은 타입의 빈이 여러 개 있을 때는 어떻게 하나를 고르는가?**
`@Qualifier`로 이름을 지정하거나, 필드/파라미터 이름이 특정 빈 이름과 일치하면 그것으로 매칭한다(`@Primary`가 붙은 빈이 있으면 그게 기본 우선). 이 중 아무것도 해당하지 않으면 `NoUniqueBeanDefinitionException`이 발생한다.

### 심화

**Q. `Optional<T>`, `ObjectProvider<T>`, `List<T>` 같은 "컬렉션/래퍼 타입" 의존성 주입은 내부적으로 어떻게 다른가?**
`List<T>`/`Map<String, T>`는 해당 타입의 **모든** 빈을 한 번에 수집해서 주입한다(개별 빈이 없어도 빈 컬렉션이 주입되어 실패하지 않는다). `Optional<T>`/`ObjectProvider<T>`는 "그 타입의 빈이 없을 수도 있다"는 것을 명시적으로 표현하는 래퍼로, 지연 조회(`ObjectProvider#getIfAvailable()`)나 여러 개 중 하나만 필요한 경우의 우아한 처리를 가능하게 한다 — 특히 `ObjectProvider`는 순환 참조 상황에서 "지금 당장 필요한 게 아니라 나중에 필요할 때 조회하겠다"는 지연 전략으로도 쓰인다.

---

## 6. Spring AOP

### 기초

**Q. Spring AOP와 AspectJ의 차이는?**
Spring AOP는 프록시 기반(런타임에 프록시 객체를 만들어 감싸는 방식)이라 **메서드 호출**에만 적용할 수 있고, 스프링 빈으로 등록된 객체에만 동작한다. AspectJ는 컴파일 타임/로드 타임 위빙(바이트코드 자체를 조작)이라 필드 접근이나 생성자 호출까지 포함해 훨씬 넓은 지점에 적용할 수 있지만, 별도의 컴파일/로딩 과정이 필요하다. Spring AOP는 "대부분의 실무 요구(메서드 단위 부가 기능)에는 충분하면서 설정이 훨씬 간단하다"는 트레이드오프를 택한 것이다.

### 중급

**Q. JDK 동적 프록시와 CGLIB 프록시는 언제 각각 선택되는가?**
대상 클래스가 인터페이스를 구현하고 있으면 기본적으로 JDK 동적 프록시(인터페이스 기반), 인터페이스가 없으면 CGLIB(대상 클래스를 상속하는 서브클래스 생성)를 쓴다. Spring Boot 2.0+부터는 `proxyTargetClass=true`가 기본값이라 인터페이스가 있어도 CGLIB를 우선 사용하도록 바뀌었다(일관성 있는 프록시 타입을 위해).
→ [`experiments/proxy-playground`](../experiments/proxy-playground), [`mini-spring/mini-aop`](../mini-spring/mini-aop)

**Q. self-invocation(자기 자신 호출) 문제란 무엇인가?**
같은 클래스 안에서 `this.method()`로 다른 메서드를 호출하면, 그 호출은 프록시를 거치지 않고 원본 객체로 직접 간다 — 그 메서드에 `@Transactional`이나 `@Async` 같은 프록시 기반 부가 기능이 붙어 있어도 전혀 적용되지 않는다. 프록시는 "그 빈을 컨테이너에서 조회해서 호출할 때"만 개입하는데, `this` 호출은 그 조회 과정 자체를 거치지 않기 때문이다.
→ [`experiments/transaction-propagation-playground`](../experiments/transaction-propagation-playground)의 `invokeRollbackMethodThroughSelfInvocation` 테스트

### 심화

**Q. `private` 메서드에 `@Transactional`을 붙이면 왜 적용되지 않는가?**
프록시(JDK든 CGLIB든)는 "원본 메서드를 오버라이드(또는 그 자리를 가로채는 별도 구현)해서 부가 기능을 끼워 넣는" 방식으로 동작한다. `private` 메서드는 서브클래스나 다른 클래스에서 오버라이드할 수 없으므로, 프록시가 애초에 가로챌 지점이 없다 — 컴파일도, 애노테이션 처리도 정상적으로 되지만 런타임에는 그냥 조용히 무시된다(예외조차 나지 않는다는 게 더 위험한 지점이다).
→ [`experiments/transaction-propagation-playground`](../experiments/transaction-propagation-playground)의 `invokePrivateTransactionalMethod` 테스트

**Q. `AnnotationAwareAspectJAutoProxyCreator`(자동 프록시 생성기)는 어떤 시점에, 무엇을 기준으로 "이 빈을 프록시해야 하는가"를 판단하는가?**
`BeanPostProcessor#postProcessAfterInitialization` 시점에(초기화가 완전히 끝난 원본 인스턴스를 대상으로), 등록된 `Advisor`들의 `Pointcut`을 그 빈의 클래스/메서드에 대해 평가해서 하나라도 매칭되면 프록시를 생성한다. 이 판단이 초기화 **이후**에 이뤄지기 때문에, 3단계 캐시의 3차 캐시(팩토리)가 "프록시할지 아직 모르는" 원본을 미리 노출했다가, 실제 조기 참조가 필요한 시점에 이 판단을 대신 앞당겨 수행하는 것이다(1번 절 심화 질문과 연결).
→ [`docs/12-auto-proxy-creator`](12-auto-proxy-creator), [`mini-spring/mini-auto-proxy`](../mini-spring/mini-auto-proxy)

---

## 7. 트랜잭션

### 기초

**Q. `@Transactional`은 어떻게 동작하는가? AOP와 무슨 관계인가?**
`@Transactional`은 그 자체로 트랜잭션을 관리하는 코드가 아니라, `TransactionInterceptor`(하나의 `MethodInterceptor`, 즉 AOP Advice)를 그 메서드에 적용하라는 지시다. 프록시가 그 메서드 호출을 가로채서 `TransactionInterceptor`가 트랜잭션 시작 → 원본 메서드 실행 → 성공하면 커밋/실패하면 롤백을 감싸는 구조다 — 즉 트랜잭션 관리 자체가 AOP의 활용 사례 중 하나다.

**Q. 체크 예외(checked exception)가 발생하면 기본적으로 롤백되는가?**
아니다. `@Transactional`의 기본 롤백 규칙은 `RuntimeException`과 `Error`에 대해서만 롤백하고, 체크 예외는 롤백하지 않는다(커밋된다). 체크 예외에도 롤백하려면 `@Transactional(rollbackFor = Exception.class)`처럼 명시해야 한다.
→ [`experiments/transaction-propagation-playground`](../experiments/transaction-propagation-playground)의 `noRollbackOnCheckedExceptionByDefault`/`rollbackOnCheckedExceptionWithRollbackFor` 테스트

### 중급

**Q. `REQUIRED`와 `REQUIRES_NEW`의 실제 차이는?**
`REQUIRED`(기본값)는 이미 진행 중인 트랜잭션이 있으면 그대로 참여한다(같은 물리적 `Connection`) — 참여자의 실패는 즉시 롤백이 아니라 "rollback-only" 표시만 남기고, 최종 커밋 시점에 owner가 그 표시를 보고 실제로 롤백한다. `REQUIRES_NEW`는 기존 트랜잭션을 잠시 떼어내고(suspend) 완전히 **다른 물리적 `Connection`**으로 독립된 트랜잭션을 새로 시작한다 — 이 새 트랜잭션은 바깥 트랜잭션과 완전히 무관하게 커밋/롤백된다(바깥이 나중에 실패해도 이미 커밋된 `REQUIRES_NEW`는 되돌릴 수 없다).
→ [`docs/14-transaction-propagation`](14-transaction-propagation), [`experiments/transaction-propagation-playground`](../experiments/transaction-propagation-playground)

**Q. `NESTED`는 `REQUIRES_NEW`와 결과가 비슷해 보이는데 어떻게 다른가?**
결과(내부 실패가 외부로 번지지 않음)는 비슷하지만 메커니즘이 완전히 다르다. `REQUIRES_NEW`는 별도의 물리적 `Connection`/트랜잭션이고, `NESTED`는 **같은 Connection, 같은 물리적 트랜잭션 안에서 JDBC savepoint**로 구현된다. `NESTED`가 실패하면 savepoint까지만 롤백되고, 바깥 트랜잭션은 rollback-only로 표시조차 되지 않는다.
→ [`docs/14-transaction-propagation`](14-transaction-propagation)

### 심화

**Q. 참여자(REQUIRED로 합류한 메서드)가 예외를 던졌는데 호출자가 그걸 catch로 삼켜버리면 무슨 일이 생기는가?**
참여자의 실패는 이미 트랜잭션 전체를 "rollback-only"로 표시해 둔 상태다. 호출자가 예외를 삼켜서 메서드가 정상적으로 리턴된 것처럼 보여도, owner의 `commit()` 호출 시점에 그 rollback-only 표시를 확인하고 **실제로는 롤백**한 뒤 `UnexpectedRollbackException`을 던진다 — "예외를 잡았으니 안전하다"는 호출자 쪽 직관이 트랜잭션 경계에서는 성립하지 않는 대표적인 사례다.
→ [`docs/14-transaction-propagation`](14-transaction-propagation)의 jdi-tracer 세션(7.1절) — 실제 브레이크포인트로 `rollback()`/`processRollback()`이 호출되는 지점을 확인했다

**Q. `suspend()`/`resume()`은 정확히 무엇을 하는가?**
`TransactionSynchronizationManager`라는 스레드 로컬 레지스트리에 바인딩된 현재 트랜잭션 리소스(Connection, 격리 수준, 트랜잭션 이름, 등록된 Synchronization 콜백들)를 통째로 스레드에서 떼어냈다가(suspend), 새 트랜잭션이 끝나면 그대로 되돌려 놓는(resume) 것이다. jdi-tracer로 직접 확인한 결과, "아무 트랜잭션도 없는 상태에서 새 트랜잭션을 시작할 때"도 `suspend(null)`이 호출되는데(빈 스냅샷을 찍는 방어적 호출), 실제로 "기존 트랜잭션을 떼어내는" 진짜 suspend는 `handleExistingTransaction()`의 `REQUIRES_NEW` 분기에서 실제 `transaction` 객체를 인자로 다시 호출될 때다.
→ [`docs/14-transaction-propagation`](14-transaction-propagation) 7.1절

**Q. `TransactionSynchronizationManager`가 `PlatformTransactionManager` 내부가 아니라 별도의 정적 클래스로 분리된 이유는?**
"현재 스레드에 어떤 자원이 묶여 있는가"는 트랜잭션 매니저 하나만의 관심사가 아니다 — `JdbcTemplate` 같은, 트랜잭션과 직접 관련 없어 보이는 코드도 "지금 트랜잭션 안이면 그 커넥션을 재사용해야 한다"는 사실을 알아야 한다. 이 지식을 특정 트랜잭션 매니저 구현체에 가두지 않고 스레드 전역 정적 레지스트리로 분리한 덕에, 트랜잭션 관리와 무관한 코드도 `DataSourceUtils.getConnection()` 하나만 호출하면 된다.
→ [`docs/14-transaction-propagation`](14-transaction-propagation) 11번 절

---

## 8. Spring MVC

### 기초

**Q. 요청이 `DispatcherServlet`에 도착한 뒤 컨트롤러까지 가는 흐름은?**
`HttpServlet#service` → `FrameworkServlet#processRequest` → `DispatcherServlet#doDispatch` → 등록된 `HandlerMapping`들을 순서대로 조회해 요청에 맞는 핸들러를 찾음 → 그 핸들러에 맞는 `HandlerAdapter`를 찾음 → `HandlerAdapter`가 실제로 핸들러(컨트롤러 메서드)를 호출.
→ [`docs/15-dispatcher-servlet`](15-dispatcher-servlet), [`experiments/dispatcher-servlet-trace`](../experiments/dispatcher-servlet-trace)

### 중급

**Q. `HandlerMapping`과 `HandlerAdapter`를 왜 분리했는가?**
"어느 핸들러가 이 요청을 처리하는가"(매핑)와 "그 핸들러를 실제로 어떻게 호출하는가"(호출 방식)는 서로 다른 관심사다. Spring MVC는 컨트롤러가 `@RequestMapping` 메서드일 수도, 옛날 방식의 `Controller` 인터페이스 구현체일 수도, 단순 `HttpRequestHandler`일 수도 있게 지원하는데, 이 서로 다른 "핸들러의 형태"마다 호출 방식이 다르다. `HandlerMapping`은 "무엇을"만 찾고, `HandlerAdapter`는 그 "무엇"의 실제 타입에 맞춰 "어떻게" 호출할지를 각각 전담하게 분리한 것이다 — 새로운 핸들러 형태를 추가해도 매핑 로직은 건드릴 필요가 없다.

**Q. `HandlerInterceptor`는 정확히 어느 지점에서 실행되는가?**
`HandlerAdapter`가 컨트롤러를 호출하기 전(`preHandle`), 호출 후 뷰 렌더링 전(`postHandle`), 뷰 렌더링까지 끝난 뒤(`afterCompletion`) 세 지점에 개입한다. `preHandle`이 `false`를 반환하면 그 지점에서 요청 처리가 중단되고 이후 인터셉터의 `preHandle`도, 컨트롤러도 호출되지 않는다.
→ [`experiments/dispatcher-servlet-trace`](../experiments/dispatcher-servlet-trace)의 `BlockingInterceptor` 테스트

### 심화

**Q. 컨트롤러 로컬 `@ExceptionHandler`와 `@ControllerAdvice`가 같은 예외를 처리할 수 있으면 누가 이기는가?**
컨트롤러 로컬 핸들러가 항상 먼저다. `ExceptionHandlerExceptionResolver#getExceptionHandlerMethod()`는 해당 컨트롤러 전용 캐시를 advice 캐시보다 먼저 조회하고, 매칭되면 그 자리에서 즉시 반환한다(advice는 아예 확인하지도 않는다). 여러 `@ControllerAdvice`가 경쟁하면 `@Order`가 작은 쪽이 이긴다(`ControllerAdviceBean.findAnnotatedBeans()`가 `OrderComparator`로 미리 정렬해 둔다).
→ [`docs/22-mvc-exception-handling`](22-mvc-exception-handling), [`experiments/mvc-exception-pipeline`](../experiments/mvc-exception-pipeline)

**Q. 세 가지 `HandlerExceptionResolver`(`ExceptionHandlerExceptionResolver`/`ResponseStatusExceptionResolver`/`DefaultHandlerExceptionResolver`)는 각각 무엇을 겨냥하는가?**
사용자가 작성한 `@ExceptionHandler` 메서드(`ExceptionHandlerExceptionResolver`), 사용자가 명시적으로 던진 상태 코드(`ResponseStatusException`/`@ResponseStatus` → `ResponseStatusExceptionResolver`), Spring MVC 인프라 자체가 던지는 예외(타입 변환 실패, JSON 파싱 실패, 검증 실패, 핸들러 없음, 메서드 불일치 → `DefaultHandlerExceptionResolver`) — 이렇게 "예외의 출처"별로 나뉘어 있어서, 사용자가 아무 예외 처리도 하지 않았어도 마지막 리졸버가 항상 표준 HTTP 상태 코드로 안전망 역할을 한다.
→ [`docs/22-mvc-exception-handling`](22-mvc-exception-handling)

**Q. `@Valid` 검증이 실패하면 어떤 예외가 나고, 왜 그게 400으로 이어지는가?**
`MethodArgumentNotValidException`이 발생하고, `DefaultHandlerExceptionResolver`가 이를 400(Bad Request)으로 매핑한다. 단, `@Valid`가 실제로 동작하려면 클래스패스에 JSR-380 구현체(Hibernate Validator 등)와 그 구현체가 필요로 하는 부가 런타임(EL 구현체)까지 갖춰져 있어야 한다 — 하나라도 빠지면 `OptionalValidatorFactoryBean`이 그 설정 실패를 조용히 삼키고 검증을 아예 no-op으로 만들어 버려서, 검증이 "실패"가 아니라 "그냥 실행되지 않는" 형태로 나타난다.
→ [`docs/22-mvc-exception-handling`](22-mvc-exception-handling)의 "직접 겪은 버그 1"

---

## 9. Spring Boot 내부 동작

### 기초

**Q. `SpringApplication.run()`은 대략 어떤 단계를 거치는가?**
`SpringApplicationRunListeners`로 시작 이벤트 발행 → `Environment` 준비(프로파일/프로퍼티) → 배너 출력 → `ApplicationContext` 생성(웹 환경 여부에 따라 구현체 선택) → 컨텍스트 준비(빈 등록, `ApplicationContextInitializer` 적용) → `refresh()`(일반 Spring 컨테이너 초기화 전체) → 실행 후 콜백(`ApplicationRunner`/`CommandLineRunner`).
→ [`docs/17-spring-application`](17-spring-application), [`experiments/spring-application-lifecycle`](../experiments/spring-application-lifecycle)

### 중급

**Q. 자동 설정(auto-configuration)은 어떻게 조건부로 적용되는가?**
`@Conditional` 계열 애노테이션(`@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty` 등)으로 "이 조건이 만족될 때만 이 설정 클래스/빈을 등록하라"고 표현한다. 내부적으로 `Condition` 인터페이스의 `matches()`가 `ConditionContext`(현재 `BeanFactory`, `Environment`, `ClassLoader` 등에 접근 가능)를 기준으로 판단한다.
→ [`docs/19-conditional-configuration`](19-conditional-configuration)

**Q. 사용자가 직접 등록한 빈이 있으면 왜 자동 설정이 물러나는가?**
자동 설정 클래스들이 대개 `@ConditionalOnMissingBean`을 함께 쓰기 때문이다 — "사용자가 이미 이 타입의 빈을 등록했다면, 자동 설정은 아무것도 하지 않는다"는 규칙을 명시적으로 코드에 박아 둔 것이다. 게다가 자동 설정 클래스들은 `@AutoConfiguration(after = ...)` 등으로 사용자 정의 `@Configuration`보다 항상 **나중에** 평가되도록 순서가 보장되므로, `@ConditionalOnMissingBean` 판단 시점에는 이미 사용자 빈이 등록된 뒤다.
→ [`docs/18-auto-configuration`](18-auto-configuration)

### 심화

**Q. 자동 설정 후보들은 어디서, 어떤 순서로 읽어 오는가?**
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 파일(Boot 2.7+, 그 이전엔 `spring.factories`)에 나열된 클래스 목록을 `ImportCandidates`가 읽는다. `AutoConfigurationImportSelector`가 이 후보들을 `DeferredImportSelector`로 등록하는데, `DeferredImportSelector`는 일반 `@Import`보다 **뒤로 미뤄져서**, 사용자 정의 설정이 전부 처리된 뒤 가장 마지막에 일괄 처리된다 — "가장 나중에, 하지만 한꺼번에" 처리되는 이 타이밍이 자동 설정이 사용자 설정에 항상 양보할 수 있는 근거다.
→ [`docs/18-auto-configuration`](18-auto-configuration), [`docs/19-conditional-configuration`](19-conditional-configuration), [`experiments/auto-configuration-lab`](../experiments/auto-configuration-lab)

**Q. 커스텀 스타터를 만들 때 `autoconfigure` 모듈과 `starter` 모듈을 왜 분리하는가?**
`autoconfigure` 모듈은 실제 자동 설정 로직(조건부 빈 등록)을 담고, `starter` 모듈은 코드가 거의 없이 "어떤 의존성들을 함께 끌어올지"만 선언하는 순수 의존성 묶음이다. 이렇게 분리하면 다른 스타터가 이 `autoconfigure`만 골라 의존할 수도 있고(실제 Boot의 여러 스타터가 공통 `autoconfigure` 모듈을 공유하는 방식), 자동 설정 로직 자체를 독립적으로 테스트할 수 있다.
→ [`docs/20-custom-starter`](20-custom-starter), [`spring-extensions/mini-observability-starter`](../spring-extensions/mini-observability-starter)

---

## 10. 애플리케이션 이벤트

### 기초

**Q. `ApplicationEvent`/`ApplicationListener`와 옵저버 패턴의 관계는?**
Spring의 이벤트 시스템은 옵저버 패턴을 컨테이너 차원에서 구현한 것이다 — `ApplicationEventPublisher`가 주제(subject), `ApplicationListener`/`@EventListener`가 관찰자(observer)다. 발행자는 "누가 듣고 있는지" 전혀 몰라도 되고, 리스너는 자신이 관심 있는 이벤트 타입만 구독하면 된다 — 발행자와 리스너 사이의 결합을 없애는 게 목적이다.

### 중급

**Q. 리스너가 여러 개일 때 실행 순서와, 하나가 예외를 던지면 나머지는 어떻게 되는가?**
`@Order`로 지정한 순서대로 실행되고, 기본 멀티캐스터(`SimpleApplicationEventMulticaster`)에 별도 `errorHandler`가 없으면 리스너 하나의 예외가 그대로 호출자에게 전파되면서 **이후 순서의 리스너는 아예 호출되지 않는다** — "리스너 하나만 실패하고 나머지는 계속된다"는 흔한 오해와 다르다.
→ [`docs/21-application-events`](21-application-events), [`experiments/application-event-lab`](../experiments/application-event-lab)

### 심화

**Q. `@TransactionalEventListener(phase = AFTER_COMMIT)`는 트랜잭션이 없을 때 어떻게 동작하는가?**
`fallbackExecution` 기본값이 `false`라서, 트랜잭션이 아예 없으면 그 이벤트는 **조용히 버려진다**(즉시 동기 실행되는 게 아니다) — "커밋된 데이터만 보고 반응해야 하는" 이 리스너의 존재 이유 자체가, 트랜잭션 없는 상황에서는 성립하지 않기 때문에 의도적으로 그렇게 설계됐다.

**Q. 이 AFTER_COMMIT 리스너 안에서 예외가 나면(예: 메시지 브로커 전송 실패) 무슨 일이 생기는가?**
DB 커밋은 이미 끝난 뒤이므로 그 커밋 자체는 되돌릴 수 없고, 리스너의 실패는 그냥 예외로 끝난다 — 이 실패를 기록해 두거나 재시도할 durable한 장치가 이 접근 자체에는 없다("dual write" 문제). 이걸 해결하는 표준 패턴이 트랜잭셔널 아웃박스(주문과 "발행 예정" 이벤트를 같은 DB 트랜잭션에 함께 기록하고, 실제 발행은 별도 폴러가 재시도 가능한 방식으로 처리)다.
→ [`docs/23-transactional-outbox`](23-transactional-outbox), [`sample-app/transactional-outbox-order`](../sample-app/transactional-outbox-order)

**Q. 자식 컨텍스트에서 발행한 이벤트는 부모 컨텍스트의 리스너에게도 전달되는가?**
그렇다. `AbstractApplicationContext#publishEvent()`는 로컬 멀티캐스트 후 부모 컨텍스트가 있으면 재귀적으로 같은 이벤트를 부모에도 발행한다 — 반대로 부모가 발행한 이벤트는 자식에게 전달되지 않는다(단방향, 자식 → 부모로만 전파).
→ [`docs/21-application-events`](21-application-events)

---

## 11. 실전 면접 시나리오

실제 면접에서는 단답형보다 "이 상황에서 어떻게 될 것 같냐"는 시나리오형 질문과 꼬리 질문이 이어지는 경우가 많다. 몇 가지를 정리한다.

---

**시나리오 1.** "서비스 A의 메서드에 `@Transactional`이 붙어 있고, 그 안에서 `this.otherMethod()`를 호출했는데 `otherMethod()`에도 `@Transactional(propagation = REQUIRES_NEW)`가 붙어 있습니다. `otherMethod()`는 정말 별도 트랜잭션에서 실행될까요?"

> 아니다 — self-invocation이라 프록시를 거치지 않고 원본 객체로 직접 호출되므로, `otherMethod()`의 `@Transactional`은 전혀 적용되지 않는다. 바깥 트랜잭션에 그냥 참여(사실상 같은 트랜잭션의 연장)한다. 이걸 고치려면 `otherMethod()`를 별도 빈으로 분리해서, A가 그 빈을 주입받아 프록시를 거쳐 호출해야 한다.
>
> **꼬리 질문**: "그럼 왜 컴파일 타임이나 실행 시점에 경고/에러가 안 나나요?" → 프록시는 순수 런타임 메커니즘이라, Spring이 바이트코드를 분석해서 "이 호출은 self-invocation이다"라고 감지할 방법이 없다(AspectJ 컴파일 타임 위빙이라면 이야기가 다르다). 조용히 무시되는 게 더 위험한 이유다.

---

**시나리오 2.** "주문 서비스가 `@Transactional` 메서드 안에서 결제 서비스(`REQUIRED`로 참여)를 호출했는데, 결제가 실패해서 예외를 던졌습니다. 주문 서비스가 이 예외를 `catch`로 삼키고 정상적으로 리턴하면 최종적으로 커밋될까요?"

> 아니다 — 결제 서비스의 실패가 이미 그 트랜잭션을 rollback-only로 표시해 뒀기 때문에, 주문 서비스의 `commit()` 호출 시점에 `UnexpectedRollbackException`이 던져지면서 실제로는 롤백된다. "예외를 잡았다"는 것과 "트랜잭션이 안전하다"는 것은 별개다.
>
> **꼬리 질문**: "그럼 결제 실패가 주문 전체에 영향을 주지 않게 하려면?" → 결제 호출을 `REQUIRES_NEW`로 바꾸거나(독립된 트랜잭션이라 결제 실패가 별도로 롤백되고 끝난다), 애초에 결제와 주문을 같은 트랜잭션에 묶을 필요가 없는 설계인지(예: 트랜잭셔널 아웃박스로 비동기 처리) 다시 검토해야 한다.

---

**시나리오 3.** "REST 컨트롤러에서 `@RequestBody`로 받는 DTO에 `@Valid`와 `@NotBlank`를 붙였는데, 빈 문자열을 보내도 400이 아니라 200이 옵니다. 뭐가 문제일까요?"

> 여러 원인이 있을 수 있지만, 이 저장소에서 실제로 겪은 원인은: Bean Validation 구현체(Hibernate Validator)는 클래스패스에 있지만, 그 구현체가 메시지 보간에 필요로 하는 EL(`jakarta.el`) 구현체가 없어서 `ValidatorFactory` 생성 자체가 실패했고, Spring의 `OptionalValidatorFactoryBean`이 그 실패를 예외로 알리지 않고 조용히 검증을 no-op으로 만들어 버린 경우다. 로그를 자세히 보면(`INFO` 레벨) "Failed to set up a Bean Validation provider" 메시지가 있을 것이다.
>
> **꼬리 질문**: "그럼 이런 종류의 '조용한 실패'를 어떻게 미리 잡아낼 수 있나요?" → 통합 테스트에서 실제로 검증 실패 케이스(빈 문자열 등)를 보내 400을 기대하는 테스트를 반드시 둬야 한다. 의존성이 "클래스패스에 있다"는 것과 "완전히 동작한다"는 것은 다른 이야기이므로, 실제 동작을 검증하는 테스트가 유일한 안전망이다.

---

**시나리오 4.** "컨트롤러 클래스 안에 `@ExceptionHandler(CustomException.class)`가 있고, 별도의 `@ControllerAdvice` 클래스에도 같은 `CustomException`을 처리하는 핸들러가 있습니다. `@ControllerAdvice`에 `@Order(Ordered.HIGHEST_PRECEDENCE)`를 줘도 컨트롤러 로컬 핸들러가 이깁니다. 왜죠?"

> `@Order`는 여러 `@ControllerAdvice` **끼리의** 우선순위를 정하는 것이지, 컨트롤러 로컬 핸들러와 advice 사이의 우선순위에는 영향을 주지 않는다. `ExceptionHandlerExceptionResolver`는 로컬 핸들러를 아예 별도의, advice보다 먼저 조회하는 캐시에서 찾기 때문에 구조적으로 항상 로컬이 이긴다 — `@Order`로 이걸 뒤집을 방법은 없다.
>
> **꼬리 질문**: "그럼 advice가 반드시 처리하게 하려면?" → 컨트롤러의 로컬 `@ExceptionHandler`를 제거하는 수밖에 없다. "전역 정책이 항상 이겨야 한다"는 요구사항이라면,애초에 컨트롤러 로컬 핸들러를 두지 않는 것이 설계 원칙이 되어야 한다.

---

**시나리오 5.** "주문을 저장하고 나서 메시지 큐에 이벤트를 발행하는 코드를, `@TransactionalEventListener(AFTER_COMMIT)`로 구현했습니다. 그런데 가끔 주문은 저장됐는데 메시지가 안 온다는 문의가 들어옵니다. 원인이 뭘까요?"

> DB 커밋과 메시지 브로커 전송이 서로 다른 두 시스템에 걸친, 원자성이 없는 두 번의 쓰기(dual write)이기 때문이다. `AFTER_COMMIT`은 "커밋된 뒤에 실행을 미룬다"는 타이밍만 보장할 뿐, 그 실행(브로커 전송) 자체의 성공을 보장하지 않는다 — 전송이 실패하면 그 사실을 기록해 둘 곳도, 재시도할 방법도 없다.
>
> **꼬리 질문**: "그럼 어떻게 고치나요?" → 트랜잭셔널 아웃박스 패턴 — 주문 저장과 "이 이벤트를 나중에 발행해야 한다"는 아웃박스 레코드를 같은 DB 트랜잭션에 함께 커밋하고, 별도의 폴러가 아웃박스 테이블을 읽어 브로커에 전송을 시도한다. 전송이 실패해도 아웃박스 레코드는 미발행 상태로 남아 다음 폴링에서 재시도된다. 다만 이건 "적어도 한 번(at-least-once)" 전달만 보장하므로, 컨슈머 쪽에서 메시지 ID 기반 멱등성 처리가 함께 필요하다.

---

## 참고 — 주제별 대응 문서

| 주제 | 문서 |
| --- | --- |
| IoC 컨테이너 기초 | [`docs/plan/01-roadmap.md`](plan/01-roadmap.md) 1단계 |
| 순환 참조(3단계 캐시) | [`docs/10-primary-qualifier-circular`](10-primary-qualifier-circular) |
| 자동 프록시 생성기 | [`docs/12-auto-proxy-creator`](12-auto-proxy-creator) |
| 트랜잭션 내부 동작 | [`docs/13-transactional-internals`](13-transactional-internals) |
| 트랜잭션 전파 | [`docs/14-transaction-propagation`](14-transaction-propagation) |
| DispatcherServlet 요청 처리 | [`docs/15-dispatcher-servlet`](15-dispatcher-servlet) |
| 컨트롤러 메서드 호출 | [`docs/16-controller-invocation`](16-controller-invocation) |
| SpringApplication 생명주기 | [`docs/17-spring-application`](17-spring-application) |
| 자동 설정 | [`docs/18-auto-configuration`](18-auto-configuration) |
| 조건부 설정 | [`docs/19-conditional-configuration`](19-conditional-configuration) |
| 커스텀 스타터 | [`docs/20-custom-starter`](20-custom-starter) |
| 애플리케이션 이벤트 | [`docs/21-application-events`](21-application-events) |
| MVC 예외 처리 우선순위 | [`docs/22-mvc-exception-handling`](22-mvc-exception-handling) |
| 트랜잭셔널 아웃박스 | [`docs/23-transactional-outbox`](23-transactional-outbox) |

전체 진행 상황과 프로젝트 목록은 [`docs/plan/02-project-catalog.md`](plan/02-project-catalog.md), 20주 전체를 되짚는 회고는 [`docs/retrospective.md`](retrospective.md)에 있다.
