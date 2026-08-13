# 메서드 검증 — @Validated는 세 번째 "인터셉터 뒤에 전략 객체" 구현체다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25-cache-abstraction`](../25-cache-abstraction/cache-abstraction.md)과 마찬가지로 그 카탈로그에 속하지 않는 로드맵 밖 심화 주제다. `@Transactional`(13·14주차) → `@Cacheable`(25번)에 이어, 서비스 계층 메서드 파라미터/반환값에 붙이는 `@Validated`가 같은 "인터셉터 + 애노테이션→전략 객체 변환 + self-invocation 우회" 골격을 세 번째로 재현하는지, 그리고 어디서 갈라지는지를 확인한다. 덧붙여 [`22-mvc-exception-handling`](../22-mvc-exception-handling/mvc-exception-handling.md)이 컨트롤러 `@Valid @RequestBody`에서 찾아낸 "EL 구현체 없으면 조용히 no-op" 문제가, 서비스 계층 메서드 검증에서도 똑같이 조용한지를 소스로 확인한다.

## 1. 이번 질문

- `@Validated` + 파라미터의 `@NotBlank`/`@Min` 같은 제약은 프록시 위에서 정확히 무엇을 가로채는가 — `TransactionInterceptor`/`CacheInterceptor`와 얼마나 대칭적인가?
- 이 프록시는 어떻게 생성되는가 — 12주차에서 배운 `AbstractAutoProxyCreator`와 같은 메커니즘인가?
- 검증 그룹(`groups`)은 클래스 레벨과 메서드 레벨에 동시에 지정할 수 있는가, 있다면 어느 쪽이 우선하는가?
- 파라미터 검증과 반환값 검증은 같은 시점에 일어나는가 — 실패하면 대상 메서드가 실행되는가, 안 되는가?
- self-invocation은 여기서도 우회되는가?
- `@Validated`가 클래스에 없으면 제약 애노테이션은 어떻게 되는가 — 22번 문서가 찾아낸 "조용한 no-op"이 여기서도 반복되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Validation")는 `MethodValidationPostProcessor`를 빈으로 등록하면 `@Validated`가 붙은 클래스의 public 메서드에 JSR-380 검증이 적용된다고 설명하고, "위반이 있으면 `ConstraintViolationException`을 던진다"고 명시한다.
- 문서는 검증 그룹을 "`@Validated`의 `value()`로 지정한다"고만 설명하고, 클래스 레벨과 메서드 레벨에 동시에 붙었을 때의 우선순위는 다루지 않는다 — 이번 실험은 소스로 직접 확인했다.
- Bean Validation(JSR-380) 명세 자체는 "cascade 검증(`@Valid`)은 파라미터 객체 자신의 필드 제약까지 함께 검사한다"고 설명한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Validated`가 붙은 클래스는 12주차에서 배운 `AnnotationAwareAspectJAutoProxyCreator`(모든 `Advisor` 빈을 모아서 대상마다 매칭하는 범용 자동 프록시 생성기)와 같은 경로로 프록시가 될 거라 예상했다 — **틀렸다.** `MethodValidationPostProcessor`는 자기 자신이 `BeanPostProcessor`이자 `Advisor` 보유자라서, 매칭되는 빈마다 **직접** `ProxyFactory`로 프록시를 만든다 — `Advisor`를 여러 소스에서 수집할 필요가 없는, 훨씬 단순한 경로였다.
- 클래스 레벨 `@Validated`와 메서드 레벨 `@Validated(Group.class)`가 같이 있으면 둘 다 적용(합집합)될 거라 예상했다 — **틀렸다.** 메서드 레벨이 있으면 클래스 레벨은 완전히 무시된다(대체, 합집합 아님).
- 파라미터 검증에 실패해도 "일단 로그만 남기고 대상 메서드는 실행할 것"이라 예상했다 — **틀렸다.** 파라미터 위반은 대상 메서드 호출 자체를 막는다.
- `@Validated`가 없는 클래스에 제약 애노테이션만 붙이면, 22번 문서의 `OptionalValidatorFactoryBean`처럼 "그래도 뭔가 시도는 하다가 조용히 포기할 것"이라 예상했다 — **틀렸다.** 이 경우는 아예 프록시 자체가 생기지 않아서 시도조차 하지 않는다 — 같은 "조용한 무력화"지만 원인은 다르다(22번은 검증기 부트스트랩 실패, 여기는 애초에 인터셉터 미적용).

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/method-validation-lab`](../../experiments/method-validation-lab)

```java
@Configuration
public class ValidationPlaygroundConfig {
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }
}
```

```java
public interface OrderService {
    void placeOrder(@NotBlank String customerId, @Min(1) int quantity);

    void placeValidatedOrder(@Valid OrderRequest request);   // cascade

    @Validated(ExpressGroup.class)                            // 메서드 레벨 그룹 override
    void placeExpressOrder(@Valid OrderRequest request);

    @NotNull
    String findCustomerName(@NotBlank String customerId);    // 반환값 검증
}

@Validated                                                    // 클래스 레벨 - 기본 그룹
public class OrderServiceImpl implements OrderService { ... }
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `MethodValidationInterceptor` | 프록시가 실제로 호출하는 `MethodInterceptor` — `CacheInterceptor`/`TransactionInterceptor`와 대칭이지만, 파라미터 검증 실패 시 `invocation.proceed()` 자체를 호출하지 않는다는 점이 다르다 |
| `MethodValidationPostProcessor` | `@Validated` 클래스를 찾아 **직접** `ProxyFactory`로 프록시를 만드는 `BeanPostProcessor`(`AbstractAdvisingBeanPostProcessor` 계열) — `AbstractAutoProxyCreator`보다 훨씬 단순한, 단일 고정 `Advisor` 전용 경로 |
| `MethodValidationAdapter` | 실제 `jakarta.validation.executable.ExecutableValidator` 호출과 그룹 결정(`determineValidationGroups`)을 담당하는 위임 대상 |
| `AnnotationMatchingPointcut` | `@Validated`가 붙은 **클래스**만 골라내는 `Pointcut` — 프록시 생성 여부를 결정하는 첫 번째 관문 |
| `ExecutableValidator` | JSR-380 표준 API — 파라미터(`validateParameters`)와 반환값(`validateReturnValue`)을 각각 별도로 검증 |
| `ConstraintViolationException` | 기본 경로(`adaptConstraintViolations=false`, 이번 실험의 기본값)에서 던져지는 예외 |
| (후기·미실행) `MethodValidationException`/`MethodValidationResult` | `adaptConstraintViolations=true`일 때 대신 던져지는, Spring MVC의 `HandlerMethodValidationException`과 같은 계열의 최신(6.1+) 표현 방식 |
| (실험) `InvocationCounter` | 대상 메서드가 실제로 실행됐는지 계측 |

## 6. 호출 흐름

```text
프록시 메서드 호출
  → MethodValidationInterceptor#invoke(invocation)
      → target = getTarget(invocation)                          (프록시 자신이 아니라 실제 대상)
      → groups = determineValidationGroups(invocation)
          → AnnotationUtils.findAnnotation(method, Validated.class)   ← 메서드 레벨 먼저 확인
          → 없으면 대상 클래스(or 프록시된 인터페이스)에서 @Validated 조회 ← 없으면 클래스 레벨로 폴백
      → violations = validationAdapter.invokeValidatorForArguments(target, method, arguments, groups)
          → ExecutableValidator#validateParameters(target, method, arguments, groups)
      → violations 비어있지 않으면:
          throw new ConstraintViolationException(violations)     ← invocation.proceed() 호출 전에 리턴
      → returnValue = invocation.proceed()                       (여기서 실제 대상 메서드 실행)
      → violations = validationAdapter.invokeValidatorForReturnValue(target, method, returnValue, groups)
          → ExecutableValidator#validateReturnValue(target, method, returnValue, groups)
      → violations 비어있지 않으면:
          throw new ConstraintViolationException(violations)     ← 이미 실행은 끝난 뒤
      → return returnValue
```

self-invocation(`this.placeOrder(...)`)은 위 흐름 전체를 건너뛴다 — `MethodValidationInterceptor`가 끼어들 프록시 경계 자체를 거치지 않기 때문이다(11·12·25번과 동일한 구조).

프록시 생성 경로(`AbstractAdvisingBeanPostProcessor` vs `AbstractAutoProxyCreator`)와 파라미터/반환값 검증 시점 차이를 함께 그린 다이어그램: [`diagrams/method-validation-flow.md`](diagrams/method-validation-flow.md)

## 7. 브레이크포인트

25번(캐시)과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 — 핵심이 제어 흐름보다 "그룹이 어떻게 결정되는가", "검증이 호출 전/후 어디서 일어나는가"라는 순서였고, `InvocationCounter` 기반 실행 확인이 더 직접적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.validation.beanvalidation.MethodValidationInterceptor#invoke
org.springframework.validation.beanvalidation.MethodValidationAdapter#determineValidationGroups
org.springframework.validation.beanvalidation.MethodValidationAdapter (필드 초기화부 - Validator 부트스트랩)
org.springframework.validation.beanvalidation.MethodValidationPostProcessor#createMethodValidationAdvice
org.springframework.aop.framework.AbstractAdvisingBeanPostProcessor#postProcessAfterInitialization
```

## 8. 런타임 관찰

[`MethodValidationTest`](../../experiments/method-validation-lab/src/test/java/lab/experiments/validation/MethodValidationTest.java) (11개):

| 실험 | 결과 |
| --- | --- |
| 유효한 파라미터 | 대상 메서드 정상 실행 |
| `customerId`가 빈 문자열(`@NotBlank` 위반) | `ConstraintViolationException`, 대상 메서드는 **실행 안 됨**(카운터 0) |
| `quantity`가 0(`@Min(1)` 위반) | 위반 1건짜리 `ConstraintViolationException`, 대상 메서드 실행 안 됨 |
| `@Valid`가 붙은 중첩 파라미터(`OrderRequest`)의 내부 필드 위반 | cascade 검증으로 그대로 잡힘, 대상 메서드 실행 안 됨 |
| 유효한 중첩 파라미터 | 정상 실행 |
| `courier`가 null인 채로 **기본 그룹** 메서드 호출 | 통과 — `courier`의 제약은 `ExpressGroup` 전용이라 기본 그룹에서는 검사되지 않음 |
| 같은 요청을 **메서드 레벨 `@Validated(ExpressGroup.class)`** 메서드로 호출 | `ConstraintViolationException` — 메서드 레벨 그룹이 클래스 레벨 기본 그룹을 대체함 |
| `@NotNull` 반환값 메서드가 `null`을 반환 | `ConstraintViolationException` — 이번엔 대상 메서드가 **이미 실행된 뒤**(카운터 1)에 실패 |
| 정상적인 반환값 | 그대로 통과 |
| self-invocation으로 `this.placeOrder("", -1)` 호출 | 예외 없이 통과 — 프록시를 거치지 않아 검증 자체가 우회됨 |
| `@Validated`가 없는 클래스의 같은 모양 제약 | 예외 없이 통과 — 프록시가 아예 생기지 않아 제약이 읽히지도 않음 |

**직접 겪은 것**: 처음엔 메서드 레벨 그룹과 클래스 레벨 그룹이 "합쳐질 것"(둘 다 검사)이라 예상하고 `defaultGroupIgnoresAConstraintScopedToAnotherGroup` 테스트를 작성했는데, 실행해 보니 예상대로 통과했다 — 그런데 `methodLevelValidatedGroupEnforcesTheGroupScopedConstraint`를 작성하면서 "그럼 기본 그룹 제약(`@NotBlank customerId`, `@Positive quantity`)도 같이 검사되겠지"라고 다시 예상했는데, `determineValidationGroups`의 소스(6번 절)를 보고서야 메서드 레벨이 있으면 클래스 레벨을 완전히 **대체**한다는 것(즉 `ExpressGroup`에도 `Default`가 자동으로 포함되지 않는다는 것)을 확인했다. 이번 실험의 `placeExpressOrder`는 courier 위반만으로 실패를 확인했기 때문에 이 대체 동작 자체가 테스트를 깨뜨리진 않았지만, "그룹을 지정하면 그 그룹만 검사되고 기본 그룹은 사라진다"는 것은 실제 서비스 코드에서 흔히 겪는 함정이라 설계 의도(11번)에 남겨 둔다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `MethodValidationAdapter#determineValidationGroups`의 실제 소스: `AnnotationUtils.findAnnotation(method, Validated.class)`를 **먼저** 시도하고, `null`일 때만 대상 클래스(또는 AOP 프록시라면 프록시된 인터페이스들)에서 조회한다는 것을 코드 순서 그대로 확인했다 — 8번 절의 그룹 override 결과가 왜 그렇게 나오는지의 직접적인 근거다.
- `MethodValidationInterceptor#invoke`의 실제 소스: 파라미터 위반이 있으면 `invocation.proceed()` 호출 없이 바로 예외를 던지고, 반환값 검증은 `invocation.proceed()`가 반환한 뒤에만 실행된다는 것을 코드 순서로 확인했다 — 8번 절 "실행 안 됨 vs 이미 실행됨"의 근거다.
- `MethodValidationAdapter`의 기본 생성자(무인자)가 `SingletonSupplier.of(() -> Validation.buildDefaultValidatorFactory().getValidator())`로 `Validator`를 지연 초기화한다는 것을 확인했다 — 22번 문서의 `OptionalValidatorFactoryBean.afterPropertiesSet()`(`ValidationException`을 **catch해서 로그만 남기는** `InitializingBean`)과 정확히 비교되는 지점이다: 여기엔 그런 try-catch가 전혀 없다. 이 부트스트랩이 EL 구현체 없이 실패하면(22번이 확인한 것과 같은 원인) 컨텍스트가 뜨는 시점이 아니라 **첫 검증 대상 메서드 호출 시점에, 잡히지 않은 채로** 실패할 것으로 예상된다 — 다만 이번 모듈은 정상적인 검증 실험을 위해 `jakarta-el`을 처음부터 포함시켰기 때문에(4번 절 최소 재현 코드 참고), 이 실패를 실제로 재현하지는 않았다. 소스 비교만으로 확인한 항목이라는 것을 정직하게 밝혀 둔다.
- `MethodValidationPostProcessor#afterPropertiesSet`(`AbstractBeanFactoryAwareAdvisingPostProcessor` 상위)의 실제 소스: `AnnotationMatchingPointcut(Validated.class, true)`로 만든 `Pointcut`을 `DefaultPointcutAdvisor` 하나에 담아 `this.advisor` 필드에 고정해 둔다는 것, 그리고 `AbstractAdvisingBeanPostProcessor#postProcessAfterInitialization`이 **그 하나의 `Advisor`만** 갖고 매칭되는 빈마다 즉시 `ProxyFactory`를 만든다는 것을 확인했다 — `AnnotationAwareAspectJAutoProxyCreator`(12주차)가 `BeanFactoryAdvisorRetrievalHelper`로 빈 팩토리 전체에서 `Advisor` 빈들을 매번 모아 오는 것과 다르게, 여기는 처음부터 갈아 끼울 `Advisor`가 하나로 고정돼 있다는 구조적 차이의 근거다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `MethodValidationInterceptor`의 골격(애노테이션 → 전략 객체 조회 → 프록시 경계에서의 가로채기, self-invocation 우회)은 `mini-spring/mini-aop`·`mini-spring/mini-transaction`이 이미 다뤘고, 25번(캐시)이 그 골격의 세 번째 반복이라는 것도 이미 확인했다 — 뼈대를 다시 만드는 것은 새로운 학습이 아니다. 이번 주의 가치는 뼈대가 아니라 **행동**(그룹 override의 대체 시맨틱, 검증 전/후 시점 차이, `@Validated` 부재 시의 완전한 무력화)에 있었고, 그건 실제 Spring을 실행해서 확인하는 쪽이 훨씬 직접적이었다.

## 11. Spring 설계 의도

- **왜 `MethodValidationPostProcessor`는 12주차의 `AbstractAutoProxyCreator`가 아니라 더 단순한 `AbstractAdvisingBeanPostProcessor` 계열을 쓰는가**: `@Transactional`/`@Cacheable`은 사용자가 `TransactionManager`/`CacheManager`를 여러 개 등록하고, 서로 다른 `Advisor`(트랜잭션 어드바이저, AspectJ로 짠 커스텀 어드바이스 등)가 빈 팩토리 안에 동시에 여러 개 존재할 수 있다는 것을 전제로 설계됐다 — 그래서 `AbstractAutoProxyCreator`는 매 빈마다 "빈 팩토리 전체에서 적용 가능한 `Advisor`를 모아 매칭한다"는 범용 탐색을 수행한다. 반면 메서드 검증은 애초에 "이 클래스에 검증을 적용할지 말지"만 결정하면 되고, 적용할 `Advisor`(검증 로직 자체)는 딱 하나로 고정돼 있다 — 여러 `Advisor` 후보를 모아야 할 이유가 없으므로, `MethodValidationPostProcessor`는 그 단순함에 맞는 더 가벼운 기반 클래스를 선택했다. 확장점의 복잡도를 실제 요구사항의 복잡도에 맞춘 사례다.
- **왜 메서드 레벨 `@Validated(Group.class)`가 클래스 레벨을 "대체"하지 "합집합"이 아닌가**: `determineValidationGroups`가 클래스 레벨을 아예 보지도 않고 메서드 레벨에서 찾으면 바로 반환하기 때문이다(6번 절). 이건 "이 메서드만은 다른 규칙을 쓰겠다"는 완전한 오버라이드를 표현하기 위한 선택으로 보인다 — 만약 합집합이었다면, 메서드마다 "기본 그룹은 포함하고 싶은지"를 또 다른 방법으로 표현해야 했을 것이다. 대신 Spring은 "메서드 레벨을 쓰면 그 메서드의 그룹 결정권 전체를 넘겨받는다"는 단순한 규칙 하나로 정리했다 — 대가는, 메서드 레벨 그룹을 쓰면서 기본 그룹 제약도 함께 검사하고 싶다면 `@Validated({Default.class, ExpressGroup.class})`처럼 `Default.class`를 명시적으로 포함시켜야 한다는 것이다(8번 절에서 직접 겪은 함정).
- **왜 `@Validated`가 없는 클래스는 (22번 문서의 사례처럼 조용히 실패하는 대신) 아예 시도조차 안 하는가**: 22번 문서의 `OptionalValidatorFactoryBean`은 "검증기 자체를 부트스트랩하다가 실패"하는 경우를 감당하기 위한 방어 설계였다 - 검증을 시도했다는 것 자체는 확실하다. 반면 여기서는 애초에 "이 클래스는 검증 대상이 아니다"라는 판단이 프록시 생성 단계(포인트컷 매칭)에서 끝나 버린다 — 실행 시점에는 그냥 평범한 POJO 메서드 호출일 뿐이라, 검증을 "시도하다가 실패"할 여지 자체가 없다. 같은 "조용한 무력화"라는 결과지만, 하나는 실행 경로에 진입했다가 예외를 삼킨 것이고 하나는 애초에 그 경로에 들어가지도 않은 것이다 — 원인이 다르면 디버깅 방법도 달라야 한다는 실용적인 교훈이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 클래스 레벨과 메서드 레벨 `@Validated`가 "합쳐질 것"이라는 직관이 틀렸다 — 메서드 레벨은 클래스 레벨을 완전히 대체한다. `@Transactional`의 `propagation`/`isolation` 같은 속성들이 메서드 레벨에서 클래스 레벨을 그대로 오버라이드하는 것과 표면적으로는 비슷해 보이지만, 검증 그룹은 "속성값 하나를 바꾸는 것"이 아니라 "어떤 제약을 검사할지 그 집합 전체를 바꾸는 것"이라 훨씬 잘 놓치는 함정이었다.
- 예상 밖이었던 것: 프록시 생성 메커니즘 자체가 12주차에서 배운 것과 다른 경로(`AbstractAdvisingBeanPostProcessor`)였다는 것 — "AOP로 구현된 선언적 기능은 다 `AnnotationAwareAspectJAutoProxyCreator`를 거칠 것"이라는 가정이 이번에 처음 깨졌다. Spring은 요구사항이 단순하면 실제로 더 단순한 기반 클래스를 쓴다.
- 예상대로였던 것(재확인): self-invocation 우회, 파라미터 검증이 대상 메서드 실행을 막는다는 것 — 둘 다 AOP 프록시와 "실패를 조기에 막는다"는 상식적인 설계가 그대로 들어맞았다.
- 새로 배운 것(소스로만 확인, 미실행): `adaptConstraintViolations=true`로 바꾸면 `ConstraintViolationException` 대신 `MethodValidationException`을 던지는 별도 경로가 있다는 것 - 이건 Spring MVC 6.1+가 컨트롤러의 `@RequestParam`/`@PathVariable` 제약 위반을 `HandlerMethodValidationException`으로 감싸는 것과 같은 계열의 표현 방식으로 보인다. 서비스 계층(`ConstraintViolationException`, JSR-380 표준)과 MVC 계층(`MethodValidationException` 계열, Spring 자체 확장)이 왜 서로 다른 예외 체계로 시작했다가 이제 하나로 수렴하려 하는지는, 이 저장소가 아직 MVC 레벨 파라미터 제약을 다루지 않았으므로(22번 문서는 `@RequestBody` DTO 검증만 다룸) 앞으로 이어질 수 있는 질문으로 남긴다.
- **25번(캐시)에 이어, 이번에도 "인터셉터 뒤에 전략 객체를 갈아 끼운다"는 패턴이 재확인됐다.** 다만 이번엔 그 패턴이 항상 같은 방식으로 프록시를 만드는 건 아니라는 것 — 이 저장소가 반복해서 강조해 온 "확장점을 요구사항 크기에 맞게 쪼갠다"는 원칙이, 프록시 "생성" 전략 자체에도 적용된다는 걸 새로 확인한 주였다.
