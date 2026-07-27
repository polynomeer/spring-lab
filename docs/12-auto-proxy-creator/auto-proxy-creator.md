# 자동 프록시 생성과 self-invocation — 손으로 짠 것과 프레임워크가 하는 것

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 12주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 9(Method Timing BeanPostProcessor 2~4단계)·프로젝트 20(Annotation-Based Auto Proxy Creator)에 대응하는 분석 문서다.

## 1. 이번 질문

- 프록시는 컨테이너의 어느 단계에서 생성되는가?
- 어떤 빈이 프록시 대상인지 어떻게 결정하는가? Advisor는 어떻게 수집되는가?
- self-invocation이 AOP를 통과하지 않는 이유는 무엇인가? — 11주차에서 순수 `ProxyFactory` 레벨로 확인했으니, 이번엔 "누가 프록시를 만들었는지"(수동 `BeanPostProcessor` vs 자동 프록시 생성기)와 무관하게 그 결론이 그대로 유지되는지 확인한다.

## 2. 공식 문서 요약

- Spring 레퍼런스 매뉴얼("Using the `ProxyFactoryBean` to Create AOP Proxies", "Advising Beans")은 실무에서는 `ProxyFactoryBean`/`ProxyFactory`를 빈마다 일일이 등록하는 대신 `AnnotationAwareAspectJAutoProxyCreator`(또는 그 상위인 `AbstractAdvisorAutoProxyCreator`) 같은 자동 프록시 생성기를 컨테이너에 하나 등록해 두면, 컨테이너에 등록된 모든 `Advisor` 빈을 찾아 대상이 되는 모든 빈에 자동으로 적용한다고 설명한다.
- 자동 프록시 생성기 자체가 `BeanPostProcessor`라는 점, 그리고 `Advisor`/`Advice`/`Pointcut` 타입의 빈은 스스로 프록시 대상에서 제외된다는 점이 명시돼 있다.
- self-invocation 절(11주차에서 이미 인용)은 "프록시를 만든 메커니즘과 무관하게" 항상 성립하는 구조적 한계라고 설명한다 — 이번 주 실험이 바로 이 "메커니즘과 무관함"을 검증한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@Configuration` 클래스 안에서 `@Bean`으로 `BeanPostProcessor`를 만들면 인스턴스 메서드든 정적 메서드든 상관없을 거라 예상했다 — **틀렸다.** 인스턴스 메서드로 선언하면 그 `@Bean` 메서드를 호출하기 위해 `@Configuration` 클래스 자체를 먼저 완전히 인스턴스화해야 하는데, 그 시점이 `BeanPostProcessor`들의 등록 완료 시점보다 늦어서 컨테이너가 경고를 내고 넘어간다 — 실행해서 로그를 보기 전까지는 전혀 몰랐던 함정이다.
- 두 개의 `@Configuration` 클래스를 같은 패키지에 두고 각자 `@ComponentScan`으로 그 패키지를 스캔하면 서로 독립적일 거라 예상했다 — **틀렸다.** `@Configuration` 자체가 `@Component`라서, 서로가 서로를 컴포넌트 스캔으로 주워 담아 한 컨텍스트 안에 두 자동 프록시 메커니즘이 동시에 활성화되는 사고를 겪었다(8번에서 자세히).
- `AbstractAdvisorAutoProxyCreator`가 "Advisor 빈을 찾는" 로직이 우리가 손으로 짠 `AopUtils.canApply()` 호출과는 전혀 다른, 훨씬 복잡한 별도 알고리즘일 거라 예상했다 — 소스를 읽어 보니 내부적으로 정확히 같은 `AopUtils.canApply()`(`AopUtils.findAdvisorsThatCanApply`를 통해)를 쓴다는 것을 확인했다(9·11번).

## 4. 최소 재현 코드

**실제 Spring** — [`spring-extensions/method-timing-post-processor`](../../spring-extensions/method-timing-post-processor)

수동(우리가 만든 `BeanPostProcessor`, `AopUtils.canApply`로 대상 판별):
```java
public class MethodTimingBeanPostProcessor implements BeanPostProcessor {
    private final Advisor advisor;
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!AopUtils.canApply(advisor, bean.getClass())) return bean;
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.addAdvisor(advisor);
        return proxyFactory.getProxy();
    }
}
```

자동(같은 `Advisor` 빈, 프레임워크가 대상 판별부터 프록시 생성까지 전부 처리):
```java
@Bean
static Advisor timingAdvisor() {
    return new DefaultPointcutAdvisor(new MeasureTimeAnnotationPointcut(), new TimingMethodInterceptor());
}

@Bean
static DefaultAdvisorAutoProxyCreator autoProxyCreator() {
    return new DefaultAdvisorAutoProxyCreator();
}
```

**축소 구현** — [`mini-spring/mini-auto-proxy`](../../mini-spring/mini-auto-proxy)
```java
public final class MiniAutoProxyCreator implements BeanPostProcessor {
    private final MiniAdvisor advisor;
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!hasEligibleMethod(bean.getClass())) return bean;
        MiniProxyFactory proxyFactory = new MiniProxyFactory(bean);
        proxyFactory.addInterceptor(new PointcutFilteringInterceptor(advisor));
        return proxyFactory.getProxy();
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `AbstractAutoProxyCreator` | `BeanPostProcessor#postProcessAfterInitialization`에서 `wrapIfNecessary()` 호출 (6주차에서 이미 다룸) |
| `AbstractAdvisorAutoProxyCreator` | "어떤 Advisor를 어떤 빈에 적용할지" 결정 (`getAdvicesAndAdvisorsForBean` → `findEligibleAdvisors`) |
| `DefaultAdvisorAutoProxyCreator` | `AbstractAdvisorAutoProxyCreator`의 기본 구현체 — 별다른 커스터마이징 없이 컨테이너의 모든 `Advisor` 빈을 후보로 삼음 |
| `AnnotationAwareAspectJAutoProxyCreator` | `@Aspect` 클래스까지 `Advisor`로 변환해 후보에 추가하는 구현체(`@EnableAspectJAutoProxy`가 등록) — 이번 실험에서는 직접 다루지 않았다 |
| `BeanFactoryAdvisorRetrievalHelper` | `findAdvisorBeans()` — 컨테이너에서 `Advisor` 타입 빈을 전부 찾아옴 |
| `AopUtils#canApply` / `findAdvisorsThatCanApply` | Advisor의 Pointcut이 대상 클래스의 메서드 중 하나라도 매칭하는지 판정 — 우리 수동 구현과 자동 프록시 생성기가 **내부적으로 같은 메서드**를 쓴다 |
| (mini) `MiniAutoProxyCreator` | `hasEligibleMethod()`로 직접 판정(`AopUtils.canApply`에 대응하는 자체 구현) |
| (mini) `PointcutFilteringInterceptor` | mini-aop(11주차)의 무조건 적용 인터셉터 체인 위에 "이 메서드에만" 필터를 얹는 어댑터 |

## 6. 호출 흐름

```text
[컨테이너 초기화 시점 - 자동 프록시 생성기가 등록되는 순서]
registerBeanPostProcessors()  (refresh()의 한 단계, 3주차)
  → BeanDefinitionRegistryPostProcessor/BeanFactoryPostProcessor 먼저 실행
  → BeanPostProcessor 타입 빈들을 우선순위대로 인스턴스화 + 등록
      static @Bean 메서드는 이 시점에 바로 호출 가능 (Configuration 클래스 본체가 필요 없음)
      instance @Bean 메서드는 Configuration 클래스 본체를 먼저 완성해야 호출 가능
        → 다른 BeanPostProcessor보다 늦게 등록될 수 있음 → BeanPostProcessorChecker가 경고

[빈 하나가 만들어질 때 - AbstractAdvisorAutoProxyCreator]
postProcessAfterInitialization(bean, beanName)
  → isInfrastructureClass(beanClass)? (Advice/Pointcut/Advisor/AopInfrastructureBean이면 스킵)
  → wrapIfNecessary(bean, beanName, cacheKey)
      → getAdvicesAndAdvisorsForBean(beanClass, beanName, null)
          → findEligibleAdvisors(beanClass, beanName)
              → findCandidateAdvisors() = BeanFactoryAdvisorRetrievalHelper#findAdvisorBeans()
                  (컨테이너의 모든 Advisor 빈 조회 - 우리는 timingAdvisor 하나)
              → findAdvisorsThatCanApply(candidates, beanClass)
                  → 내부적으로 AopUtils.canApply(advisor, beanClass) 각 후보에 대해 호출
      → 후보가 하나라도 있으면 ProxyFactory로 프록시 생성, 없으면 원본 그대로 반환

[동일 시나리오, 수동 BeanPostProcessor]
postProcessAfterInitialization(bean, beanName)
  → AopUtils.canApply(advisor, bean.getClass())?      ← 정확히 같은 유틸리티 메서드
  → 아니오: 원본 반환 / 예: ProxyFactory로 프록시 생성
```

두 경로를 나란히 그린 다이어그램: [`diagrams/manual-vs-automatic-proxy-creation.md`](diagrams/manual-vs-automatic-proxy-creation.md)

## 7. 브레이크포인트

이번 주제는 실행 결과(8번)로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다.

```text
org.springframework.context.support.PostProcessorRegistrationDelegate#registerBeanPostProcessors
org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator#findEligibleAdvisors
org.springframework.aop.framework.autoproxy.BeanFactoryAdvisorRetrievalHelper#findAdvisorBeans
org.springframework.aop.support.AopUtils#canApply
```

## 8. 런타임 관찰

**`spring-extensions/method-timing-post-processor`** — [`MethodTimingBeanPostProcessorTest`](../../spring-extensions/method-timing-post-processor/src/test/java/lab/ext/timing/MethodTimingBeanPostProcessorTest.java)(6개, 수동) + [`AutoProxyCreatorTest`](../../spring-extensions/method-timing-post-processor/src/test/java/lab/ext/timing/AutoProxyCreatorTest.java)(4개, 자동):

| 실험 | 수동(`MethodTimingBeanPostProcessor`) | 자동(`DefaultAdvisorAutoProxyCreator`) |
| --- | --- | --- |
| `@MeasureTime`이 붙은 인터페이스 빈 | 측정됨, JDK 프록시 | 측정됨, `AopUtils.isAopProxy()` true |
| 인터페이스 없는 빈(`LegacyReport`) | CGLIB로 측정됨 | CGLIB로 측정됨 |
| `@MeasureTime`이 전혀 없는 빈 | 프록시 안 됨 | 프록시 안 됨 |
| self-invocation (`checkout()` 내부에서 `this.placeOrder()`) | 측정 안 됨 | 측정 안 됨 (동일) |

**직접 겪은 두 가지 사고**(둘 다 처음엔 실패로 나타났다가 원인을 찾아 고침):
1. 두 `@Configuration`(`TimingConfig`, `AutoProxyTimingConfig`)이 같은 패키지를 `@ComponentScan`해서 서로를 컴포넌트로 주워 담았다 — 그 결과 한 컨텍스트에 수동 BeanPostProcessor와 자동 프록시 생성기가 동시에 떠서 같은 빈을 **두 번** 감쌌고, `TimingLog`에 `"placeOrder"`가 두 번 기록됐다. `excludeFilters`로 서로를 명시적으로 제외해서 해결했다.
2. `methodTimingBeanPostProcessor` `@Bean` 메서드를 인스턴스 메서드로 선언했더니 콘솔에 "non-static factory method... consider declaring it as static instead" 경고가 났다. `static`으로 바꿔서 해결했다 — 원리는 11번에서.

**`mini-spring/mini-auto-proxy`** — [`MiniAutoProxyCreatorTest`](../../mini-spring/mini-auto-proxy/src/test/java/lab/minispring/autoproxy/MiniAutoProxyCreatorTest.java)(5개):

| 실험 | 결과 |
| --- | --- |
| `@MiniTransactional` 붙은 빈 | `MiniAutoProxyCreator`가 프록시로 교체 |
| `transfer()` 정상 호출 | `BEGIN transfer` → `COMMIT transfer` 순서로 기록 |
| `balance()`(애노테이션 없음) 호출 | 트랜잭션 로그에 아무것도 안 남음 — 같은 프록시라도 메서드별로 갈림 |
| `transfer()`가 예외를 던짐 | `BEGIN transfer` → `ROLLBACK transfer`, 원래 예외 그대로 전파 |
| `@MiniTransactional`이 전혀 없는 빈(`NotifierImpl`) | 프록시 안 됨, 원본 인스턴스 그대로 |

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스로 확인했다.

- **`AdvisorAutoProxyCreatorTests#commonInterceptorAndAdvisor()`**(`spring-context`): XML로 등록한 여러 `Advisor`/공통 인터셉터가 `AbstractAdvisorAutoProxyCreator`에 의해 각 대상 빈에 자동으로 적용되는 것을 `AopUtils.isAopProxy()`로 확인한다 — `IntroductionAdvisor`(`Lockable`)까지 섞인 더 복잡한 시나리오지만, "컨테이너의 Advisor 빈들이 자동으로 대상 빈에 적용된다"는 핵심 메커니즘은 우리 `AutoProxyCreatorTest`와 동일하다.
- **`isInfrastructureClass`/`BeanPostProcessorChecker`를 직접 검증하는 이름의 공식 단위 테스트는 찾지 못했다** — 정직하게 밝혀 둔다. 둘 다 소스(4·11번에서 인용)와 우리가 직접 겪은 경고 로그(8번)로 확인했다.
- self-invocation이 자동 프록시 생성기에서도 동일하게 우회된다는 것을 명시적으로 검증하는 공식 테스트도 찾지 못했다 — 11주차와 마찬가지로, 이는 `@Transactional` 통합 테스트(13주차 예정)에서 간접적으로만 다뤄지는 것으로 보인다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-auto-proxy`(project 20) — 새 모듈로 만들고, `mini-container`(빈 생성/`BeanPostProcessor`)와 `mini-aop`(11주차, 인터셉터 체인)에 의존하게 했다. AOP가 "빈 생성 후처리와 연결된다"는 로드맵의 학습 포인트를 코드 의존성 구조 자체로 드러내기 위해서다.

**구현한 것**
- `MiniAutoProxyCreator`: `mini-container`의 `BeanPostProcessor`를 구현 — `hasEligibleMethod()`로 대상 여부를 판정(실제 Spring의 `AopUtils.canApply()`에 대응)하고, 맞으면 `MiniProxyFactory`로 감싼다
- `MiniPointcut`/`MiniAdvisor`: Pointcut과 Advice를 분리하는 조합 객체(카탈로그가 명시한 학습 포인트)
- `PointcutFilteringInterceptor`: `MiniProxyFactory`(11주차, 인터셉터를 모든 메서드에 무조건 적용)를 건드리지 않고 그 위에 "이 메서드에만" 필터를 얹는 어댑터

**생략한 것 (의도적)**
- **`isInfrastructureClass` 상당의 가드가 없다** — 실제 Spring은 `Advisor`/`Advice`/`Pointcut` 자신이 실수로 프록시 대상이 되는 것(무한 루프 위험)을 막는 명시적 검사가 있다. mini에서는 애초에 `MiniAdvisor`/`MiniPointcut`/`MethodInterceptor` 구현체들을 `SimpleBeanFactory`에 빈으로 등록하지 않고(생성자 인자로 직접 조립) 순수 자바 객체로만 다루기 때문에, 이 문제 자체가 구조적으로 발생하지 않는다 — "막을 필요가 없어서 생략"과 "막을 방법이 없어서 생략"은 다르다는 점을 명확히 해 둔다.
- **`Advisor` 빈을 컨테이너에서 자동으로 찾는 `BeanFactoryAdvisorRetrievalHelper` 상당 기능이 없다** — mini는 `Advisor`를 `MiniAutoProxyCreator` 생성자에 직접 넘겨준다(호출자가 조립). 여러 개의 서로 다른 `Advisor`를 자동으로 수집해 조합하는 기능은 범위 밖으로 뒀다.
- **정렬(`sortAdvisors`)이나 `@Order` 기반 우선순위**는 다루지 않았다 — Advisor가 하나뿐이라 필요하지 않았다.

## 11. Spring 설계 의도

- **왜 `BeanPostProcessor`를 만드는 `@Bean` 메서드는 `static`이어야 안전한가**: 컨테이너는 `refresh()`의 `registerBeanPostProcessors()` 단계(3주차)에서 다른 빈들이 만들어지기 **전에** 모든 `BeanPostProcessor`를 먼저 등록해야 한다 — 그래야 나중에 만들어지는 모든 빈이 빠짐없이 후처리 대상이 된다. 그런데 인스턴스 `@Bean` 메서드는 그 메서드를 담고 있는 `@Configuration` 클래스의 인스턴스가 먼저 필요하고, 그 인스턴스 자체도 하나의 빈이라 일반적인 빈 생성 파이프라인(다른 `BeanPostProcessor`의 후처리 대상이 되는)을 거친다. `static` 메서드는 이런 순환 의존이 없어서 `@Configuration` 클래스 본체를 만들 필요 없이 이른 시점에 바로 호출할 수 있다 — Spring이 이걸 경고로 안내하는 것은 "고쳐야 동작하는 버그"가 아니라 "고치지 않으면 놓치는 최적의 타이밍"을 알려주는 것이다.
- **왜 자동 프록시 생성기는 `Advisor`/`Advice`/`Pointcut` 자신을 프록시 대상에서 제외하는가(`isInfrastructureClass`)**: 만약 제외하지 않으면, 컨테이너가 `Advisor` 빈을 만들 때도 `AbstractAutoProxyCreator`가 "이 빈도 프록시해야 하나?"를 검사하려 들고, 그 검사 과정에서 다시 `Advisor` 빈들을 조회하는 등 자기 자신을 참조하는 구조가 만들어질 위험이 있다. 프레임워크의 "배관"(infrastructure)에 해당하는 타입을 원천적으로 후보에서 걷어내는 것은 이런 자기 참조/순환을 프레임워크 차원에서 미리 차단하는 방어적 설계다.
- **왜 수동 구현과 자동 프록시 생성기가 내부적으로 같은 `AopUtils.canApply()`를 쓰는가**: "이 빈이 이 Advisor의 대상인가"라는 판정은 본질적으로 한 가지 질문(대상 클래스의 메서드 중 Pointcut에 매칭되는 게 있는가)이다. Spring은 이 판정 로직을 `AopUtils`라는 재사용 가능한 유틸리티로 뽑아 놓아서, 우리처럼 손으로 `BeanPostProcessor`를 짜더라도 프레임워크가 자동 프록시 생성기 내부에서 쓰는 것과 똑같이 검증된 로직을 그대로 가져다 쓸 수 있게 했다 — "직접 만든 것"과 "자동화된 것"의 차이가 판정 로직의 차이가 아니라, 그 판정을 **누가, 언제 호출하는가**(우리가 매 빈마다 손으로 부르는가, 프레임워크가 컨테이너 초기화 파이프라인 안에서 자동으로 부르는가)의 차이일 뿐이라는 뜻이다.
- **왜 self-invocation은 프록시를 누가 만들었는지와 무관하게 항상 우회되는가**: 11주차에서 확인했듯, 이는 프록시가 "대상 인스턴스를 감싸는 별개의 객체"라는 구조 자체에서 나오는 한계다. `AbstractAdvisorAutoProxyCreator`든 우리 수동 `BeanPostProcessor`든, 결국 마지막에는 똑같이 `new ProxyFactory(bean)` 하나를 만들 뿐이다 — "누가 그 프록시를 만드는 코드를 호출했는가"는 프록시와 원본 인스턴스가 별개 객체라는 사실 자체와는 아무 관계가 없다. 이번 주 실험(8번)이 확인한 것은 정확히 이 지점이다: 자동화 여부는 self-invocation 문제의 원인이 아니므로, 자동화한다고 이 문제가 사라지지도 않는다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Configuration` 안에서 `BeanPostProcessor`용 `@Bean`을 인스턴스 메서드로 선언하면 안 된다는 것 — 코드가 컴파일도 되고 대부분의 경우 잘 동작하는 것처럼 "보여서" 더 위험한 함정이었다. 경고 로그를 무시했다면 이번 주의 두 컨텍스트 교차 오염 버그(8번)와 뒤섞여 원인 파악이 훨씬 오래 걸렸을 것이다.
- 예상 밖이었던 것: 두 개의 독립적인 `@Configuration`을 같은 패키지에 두면 서로의 컴포넌트 스캔에 걸려든다는 것 — 사소해 보이지만 "격리된 실험 두 개를 나란히 만든다"는 이번 주의 설계 자체를 정면으로 건드리는 문제였다.
- 예상대로였던 것(실행으로 재확인): self-invocation은 프록시 생성 메커니즘(수동 `BeanPostProcessor` vs `DefaultAdvisorAutoProxyCreator`)과 무관하게 항상 우회된다 — 11주차의 결론이 "우리가 짠 코드의 우연한 특성"이 아니라 "프록시 기반 AOP의 구조적 성질"이라는 것을 다시 한번 확인했다.
- 새로 배운 것: 우리가 손으로 짠 `AopUtils.canApply()` 기반 판정이 실제 `AbstractAdvisorAutoProxyCreator` 내부와 **글자 그대로 같은 유틸리티 메서드**를 쓴다는 것 — "자동화"는 새로운 알고리즘이 아니라, 이미 있는 판정 로직을 컨테이너 초기화 파이프라인의 올바른 지점에서 자동으로 호출해 주는 배선(wiring)의 문제였다.
- 6단계(Spring AOP, 11~12주차)가 이걸로 마무리된다. 다음은 7단계, `@Transactional` 내부 동작으로 넘어간다 — 프록시 기반 AOP의 가장 중요한 실무 응용이자, self-invocation이 실무에서 가장 자주 사고로 이어지는 지점이다.
