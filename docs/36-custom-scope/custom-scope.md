# 커스텀 Scope — "싱글턴도 프로토타입도 아닌 것"을 직접 만들면 드러나는 책임의 경계

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`35`](../35-import-selector/import-selector.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 1~4주차 내내 "싱글턴"과 "프로토타입"이라는 두 가지 스코프만 다뤘는데, `@Scope`의 값은 사실 임의의 문자열이 될 수 있고 `org.springframework.beans.factory.config.Scope`라는 SPI를 직접 구현해서 자기만의 세 번째(네 번째, 다섯 번째...) 스코프를 만들 수 있다. 이번 문서는 "테넌트 하나당 빈 인스턴스 하나"라는 커스텀 스코프를 직접 구현해서, 컨테이너가 대신 해 주는 일과 스코프 구현체 스스로 책임져야 하는 일의 경계가 정확히 어디인지를 확인한다.

## 1. 이번 질문

- 커스텀 `Scope`는 정확히 어떤 계약(메서드)을 구현해야 하는가?
- `@Scope("myScope")`로 등록은 안 된, 존재하지 않는 스코프 이름을 쓰면 어떤 예외가 나는가?
- 캐싱(같은 이름으로 다시 조회하면 같은 인스턴스를 주는 것)은 컨테이너가 해 주는가, 스코프 구현체가 직접 해야 하는가?
- `@PreDestroy`/`DisposableBean`으로 등록해 둔 소멸 콜백은 커스텀 스코프에서 언제, 누가 호출하는가?
- `context.close()`는 커스텀 스코프의 빈들도 정리해 주는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Custom Scopes")는 `Scope` 인터페이스를 구현하고 `ConfigurableBeanFactory#registerScope()`(또는 `CustomScopeConfigurer`)로 등록하면 `singleton`/`prototype`/`request`/`session` 외의 스코프를 쓸 수 있다고 설명한다.
- 문서는 `Scope.get()`이 "필요하면 새로 만들고, 아니면 기존 것을 반환하는" 책임을 진다고 설명하고, `registerDestructionCallback()`이 "이 스코프가 끝날 때 호출돼야 하는 콜백을 등록하는 것"이라고 설명한다 - 하지만 "이 스코프가 끝난다"는 것이 정확히 언제인지, 그 판단 주체가 컨테이너인지 스코프 자신인지는 명시하지 않는다.
- `SimpleThreadScope`(Spring이 기본 제공하는 참고 구현체)의 Javadoc은 "기본으로 등록돼 있지 않다"와 "소멸 콜백을 지원하지 않는다"를 명시하는데, 이번 실험은 그 두 문장이 실제로 무엇을 의미하는지 소스와 실행으로 확인한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 존재하지 않는 스코프 이름을 쓰면 일반적인 `NoSuchBeanDefinitionException`류의 예외가 날 거라 예상했다 — **거의 맞았지만 정확하지는 않았다.** `ScopeNotActiveException`이라는, "스코프 자체가 활성화되지 않았다"는 것을 명확히 표현하는 전용 예외였다.
- 같은 스코프 이름으로 두 번 조회하면 컨테이너(`DefaultSingletonBeanRegistry` 같은 것)가 알아서 캐싱해 줄 거라 예상했다 — **틀렸다.** 캐싱 여부는 전적으로 `Scope.get()` 구현이 결정한다 - 매번 새 인스턴스를 만들지, 내부 `Map`에서 재사용할지는 순전히 그 구현체의 코드에 달려 있다.
- `DisposableBean#destroy()`가 컨테이너에 의해 "언젠가는" 자동으로 호출될 거라 예상했다(싱글턴이 그렇듯) — **틀렸다.** 컨테이너는 그 소멸 로직을 감싼 `Runnable`을 `Scope.registerDestructionCallback()`으로 스코프 구현체에 **넘겨줄 뿐**이다 - 그 `Runnable`을 실제로 언제 실행할지는 스코프 구현체가 직접 결정해야 한다.
- `context.close()`를 하면 커스텀 스코프의 빈들도 정리될 거라 예상했다(컨테이너가 어쨌든 모든 빈의 생명주기를 관리하니) — **틀렸다.** `context.close()`는 싱글턴 레지스트리만 정리한다 - 커스텀 스코프에 등록해 둔 소멸 콜백은 컨텍스트 종료와 완전히 무관하며, 스코프 구현체가 스스로 정리 메서드를 호출하지 않는 한 영원히 실행되지 않는다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/custom-scope-lab`](../../experiments/custom-scope-lab)

```java
public class TenantScope implements Scope {
    private final Map<String, Map<String, Object>> beansByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Runnable>> destructionCallbacksByTenant = new ConcurrentHashMap<>();

    public Object get(String name, ObjectFactory<?> objectFactory) {
        return beansByTenant.computeIfAbsent(requireTenant(), t -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, n -> objectFactory.getObject());   // 캐싱은 여기서 직접
    }

    public void registerDestructionCallback(String name, Runnable callback) {
        destructionCallbacksByTenant.computeIfAbsent(requireTenant(), t -> new ConcurrentHashMap<>())
                .put(name, callback);                                     // 저장만 해 둠, 실행은 안 함
    }

    public void endTenant(String tenantId) {          // Spring이 아니라 애플리케이션 코드가 직접 호출
        Map<String, Runnable> callbacks = destructionCallbacksByTenant.remove(tenantId);
        if (callbacks != null) callbacks.values().forEach(Runnable::run);
        beansByTenant.remove(tenantId);
    }
}
```

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
context.getBeanFactory().registerScope("tenant", new TenantScope());   // refresh() 전에 등록
context.register(TenantConfig.class);
context.refresh();
```

```java
@Bean
@Scope("tenant")
public TenantWidget tenantWidget() {
    return new TenantWidget();   // implements DisposableBean
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `org.springframework.beans.factory.config.Scope` | 커스텀 스코프가 구현해야 하는 SPI - `get`/`remove`/`registerDestructionCallback`/`resolveContextualObject`/`getConversationId` 다섯 메서드 |
| `ConfigurableBeanFactory#registerScope` | 이름과 `Scope` 구현체를 컨테이너에 등록 - `@Scope("이름")`이 이 등록과 매칭됨 |
| `AbstractBeanFactory#doGetBean` | `singleton`/`prototype`이 아닌 스코프 이름을 만나면 등록된 `Scope`를 찾아 `scope.get()`을 호출하는 분기 |
| `ScopeNotActiveException` | 스코프의 `get()`이 `IllegalStateException`을 던지면 `doGetBean`이 그걸 감싸서 다시 던지는 전용 예외 |
| `AbstractBeanFactory#registerDisposableBeanIfNecessary` | 빈이 소멸 로직(`@PreDestroy`/`DisposableBean`/커스텀 destroy 메서드)을 갖고 있으면, 싱글턴이면 직접 등록하고 **커스텀 스코프면 `scope.registerDestructionCallback()`으로 위임**하는 분기점 |
| `SimpleThreadScope`(Spring 기본 제공) | 스레드 하나당 빈 하나 - `get()`은 캐싱하지만 `registerDestructionCallback()`은 경고 로그만 남기고 아무것도 안 함(참고 구현) |
| (실험) `TenantScope` | 소멸 콜백을 실제로 저장했다가 `endTenant()`로 명시적으로 실행하는, `SimpleThreadScope`보다 한 단계 더 완전한 구현 |

## 6. 호출 흐름

```text
context.getBeanFactory().registerScope("tenant", tenantScope)   ← refresh() 전에 등록 (1주차 이후 처음 보는,
                                                                    빈 생성과 무관한 BeanFactory 설정 단계)

context.getBean(TenantWidget.class)
  → AbstractBeanFactory#doGetBean
      → mbd.getScope() = "tenant"  (singleton도 prototype도 아님)
      → scope = this.scopes.get("tenant")
          → 없으면: IllegalStateException("No Scope registered for scope name 'tenant'")
      → try {
            scopedInstance = scope.get(beanName, () -> {
                beforePrototypeCreation(beanName)
                createBean(beanName, mbd, args)     ← 실제 생성은 여기서 (populateBean, initializeBean 등
                                                        4주차의 전체 파이프라인 그대로)
                afterPrototypeCreation(beanName)
            })
        } catch (IllegalStateException ex) {
            throw new ScopeNotActiveException(beanName, "tenant", ex)   ← requireTenant()의 예외가 여기로

createBean 내부 (doCreateBean 이후)
  → registerDisposableBeanIfNecessary(beanName, bean, mbd)
      → mbd.isSingleton()? 아니오(커스텀 스코프) → mbd.isPrototype()? 아니오
      → scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(bean, ...))
          (DisposableBean#destroy()를 감싼 Runnable 하나가 TenantScope에 전달돼 저장됨 -
           아직 실행 안 됨)

애플리케이션 코드가 나중에:
  tenantScope.endTenant("acme")
      → 저장해 둔 Runnable(DisposableBeanAdapter)을 실행 → destroy() 호출
```

컨테이너가 만들어서 넘겨주는 것(`ObjectFactory`, 소멸 `Runnable`)과 `TenantScope`가 스스로 결정해야 하는 것(캐싱, 실행 시점)의 경계를 그린 다이어그램: [`diagrams/custom-scope-responsibility.md`](diagrams/custom-scope-responsibility.md)

## 7. 브레이크포인트

25~35번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "누가 무엇을 하는가"라는 책임 분담이었고, 그건 실행 결과(특히 `context.close()` 이후에도 살아있는 빈, 5번째 실험)로 확인하는 쪽이 더 직접적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.factory.config.Scope (인터페이스 전체)
org.springframework.beans.factory.support.AbstractBeanFactory#doGetBean (커스텀 스코프 분기)
org.springframework.beans.factory.support.AbstractBeanFactory#registerDisposableBeanIfNecessary
org.springframework.beans.factory.support.ScopeNotActiveException
org.springframework.context.support.SimpleThreadScope (참고 구현체)
```

## 8. 런타임 관찰

[`TenantScopeTest`](../../experiments/custom-scope-lab/src/test/java/lab/experiments/customscope/TenantScopeTest.java) (5개):

| 실험 | 결과 |
| --- | --- |
| 같은 테넌트로 두 번 조회 | 같은 `TenantWidget` 인스턴스, `objectFactory` 호출 횟수 **1회** |
| 서로 다른 테넌트로 각각 조회 | 서로 다른 인스턴스 |
| 테넌트가 하나도 바인딩되지 않은 스레드에서 조회 | `ScopeNotActiveException` |
| 빈을 만든 직후(아직 `endTenant()` 호출 전) | `destroy()` **미호출** - 소멸 콜백은 등록만 돼 있고 실행되지 않음 |
| `endTenant("acme")` 호출 후 | 그제서야 `destroy()` 호출됨 |
| `context.close()` 이후(`endTenant()`는 호출 안 함) | 여전히 `destroy()` **미호출** - 컨텍스트 종료가 커스텀 스코프를 전혀 건드리지 않음 |

**직접 겪은 것**: 다섯 번째 행(컨텍스트 종료)을 처음 설계할 때는 "그래도 `context.close()`는 컨테이너 전체를 정리하는 것이니 커스텀 스코프의 소멸 콜백도 어떤 형태로든 건드리지 않을까"라고 반신반의했다 - `DefaultSingletonBeanRegistry#destroySingletons()`가 정확히 "싱글턴"이라는 이름이 붙은 메서드라는 것 자체가 힌트였는데도, 실행해서 직접 확인하기 전까지는 확신이 서지 않았다. 실제로 돌려 보니 `widget.isDestroyed()`가 `context.close()` 이후에도 `false`로 남아 있었다 - 커스텀 스코프를 쓴다는 것은, "빈의 소멸을 컨테이너 종료에 맞춰 정리한다"는 이 저장소가 4주차부터 당연하게 여겨 온 전제 하나를 포기하고 그 책임을 전부 떠안는 것이라는 걸 실감했다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `AbstractBeanFactory#doGetBean`의 실제 소스: `scope.get(beanName, objectFactory)` 호출을 `try` 블록으로 감싸고, `catch (IllegalStateException ex) { throw new ScopeNotActiveException(beanName, scopeName, ex); }`로 재포장한다는 것을 확인했다 - `TenantScope#requireTenant()`가 던지는 평범한 `IllegalStateException`이 왜 호출자에게는 `ScopeNotActiveException`으로 도착하는지의 정확한 근거다. 스코프 구현체는 "일반적인 상태 오류"만 던지면 되고, 그걸 "스코프가 비활성"이라는 의미로 격상시키는 건 컨테이너의 책임이라는 분업이다.
- `AbstractBeanFactory#registerDisposableBeanIfNecessary`의 실제 소스: `mbd.isSingleton()`이면 `registerDisposableBean()`(컨테이너 자신의 싱글턴 레지스트리에 저장), 그 외(프로토타입이 아닌 커스텀 스코프)면 `scope.registerDestructionCallback()`으로 위임한다는 분기를 확인했다 - 두 경로 모두 **똑같은** `DisposableBeanAdapter`(`@PreDestroy`/`DisposableBean`/커스텀 destroy 메서드를 전부 감싼 것)를 만들어 전달한다는 것도 확인했다. "무엇을 정리해야 하는가"를 판단하는 로직은 스코프와 무관하게 재사용되고, "언제 정리하는가"만 스코프별로 다른 저장소로 갈라진다.
- `SimpleThreadScope`의 실제 소스: `registerDestructionCallback()`이 `logger.warn(...)`만 남기고 아무 저장도, 실행도 하지 않는다는 것을 확인했다 - Spring이 기본 제공하는 참고 구현체조차 이 부분을 완전히 구현하지 않은 채로 배포된다는 것은, "이 SPI를 구현하는 사람이 스스로 결정해야 하는 부분"이라는 설계 의도를 가장 확실하게 보여주는 증거다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `mini-spring/mini-container`가 이미 싱글턴 저장소(4주차)를 다뤘고, 커스텀 스코프의 핵심 아이디어("이름으로 조회하고, 없으면 만들어서 저장한다")는 그 싱글턴 레지스트리와 본질적으로 같은 패턴(25번의 캐시, 27번의 컨텍스트 캐시, 32번의 FactoryBean 산출물 캐시와도 같은 계열)이다 - 이번 주의 가치는 그 패턴 자체가 아니라, 그 책임이 컨테이너에서 사용자 코드로 **완전히 넘어갔을 때** 어떤 것들을 스스로 챙겨야 하는지(캐싱, 소멸 콜백 실행 시점, 컨텍스트 종료와의 무관함)에 있었다.

## 11. Spring 설계 의도

- **왜 캐싱을 컨테이너가 대신 해 주지 않고 `Scope.get()`에 완전히 위임하는가**: "테넌트"라는 개념 자체가 Spring이 알 수 없는, 애플리케이션 고유의 개념이다 - 컨테이너가 "이 스코프의 경계가 어디부터 어디까지인지"를 미리 알고 캐싱 정책을 대신 정해 줄 방법이 없다. `request`/`session` 스코프도 서블릿 API의 `HttpServletRequest`/`HttpSession`이라는, 컨테이너가 알 수 없는 외부 개념에 기대어 구현돼 있다 - `Scope` SPI가 `get()`의 캐싱 여부를 통째로 위임하는 것은, "스코프의 경계를 정의하는 지식은 그 스코프를 만드는 사람만 갖고 있다"는 전제를 그대로 반영한 것이다.
- **왜 소멸 콜백은 "등록"과 "실행"이 분리돼 있는가**: 컨테이너는 "이 빈이 소멸될 때 무엇을 해야 하는지"(`@PreDestroy` 등)는 정확히 알지만, "이 스코프가 언제 끝나는지"는 전혀 모른다 - `request` 스코프라면 HTTP 요청이 끝나는 시점, `tenant` 스코프라면 이번 실험처럼 애플리케이션이 명시적으로 결정하는 시점이다. `registerDestructionCallback()`으로 "무엇을 해야 하는가"라는 지식(컨테이너가 가진 것)만 넘기고, "언제"라는 결정(스코프 구현체만 아는 것)은 완전히 분리해 둔 것은, 서로 다른 곳에 있는 두 지식을 억지로 한곳에 모으려 하지 않는 설계다.
- **왜 `context.close()`가 커스텀 스코프를 아예 건드리지 않는가**: `DefaultSingletonBeanRegistry`는 컨테이너 자신이 소유하고 생명주기를 아는 저장소지만, 커스텀 `Scope`는 사용자가 만들어서 등록한 임의의 객체다 - 컨테이너가 그 안에 무엇이 들어 있는지, 어떻게 정리해야 하는지 알 방법이 없다(`Scope` 인터페이스 자체에 "전체 정리"에 해당하는 메서드가 없다). 만약 컨테이너가 모든 등록된 스코프에 대해 뭔가 자동으로 정리를 시도한다면, 그건 스코프 구현체가 전혀 예상하지 못한 시점에 상태를 건드리는 것이 되어 오히려 위험하다 - "내가 모르는 것은 건드리지 않는다"는 소극적인 선택이, 이번 실험처럼 "정리를 깜빡하면 영원히 안 된다"는 대가로 이어진다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `context.close()`가 커스텀 스코프의 빈을 전혀 정리해 주지 않는다는 것 - 4주차부터 "컨테이너가 빈의 생명주기 전체를 책임진다"고 당연하게 여겨 온 전제가, 커스텀 스코프 앞에서는 "컨테이너가 아는 범위 안에서만" 이라는 훨씬 좁은 범위로 축소된다는 것을 실행으로 직접 확인했다.
- 예상 밖이었던 것: Spring이 기본 제공하는 `SimpleThreadScope`조차 소멸 콜백을 제대로 구현하지 않고 배포된다는 것 - "공식 제공 구현체니까 당연히 완전할 것"이라는 막연한 기대가, 이 SPI의 특정 부분(정리 시점 결정)은 애초에 프레임워크가 대신 해 줄 수 없는 영역이라는 걸 보여주는 반례 앞에서 깨졌다.
- 예상대로였던 것(재확인): "이름으로 조회하고 없으면 만들어서 저장한다"는 get-or-create 패턴이 여기서도 반복된다는 것 - 25·27·32번에서 이미 여러 번 본 이 패턴이, 이번엔 컨테이너가 아니라 **사용자가 직접 구현해야 하는 자리**에서 나타났다는 점이 새로웠다.
- 새로 배운 것: `ScopeNotActiveException`처럼, 사용자 코드가 던진 평범한 예외(`IllegalStateException`)를 컨테이너가 더 구체적인 의미를 가진 예외로 다시 포장해 주는 지점이 있다는 것 - 스코프 구현체는 "지금 상태가 이상하다"는 사실만 알리면 되고, 그걸 "스코프가 활성화되지 않았다"는 더 정확한 진단으로 바꾸는 건 컨테이너의 몫이라는 분업을, `doGetBean`의 catch 블록 하나로 확인했다.
