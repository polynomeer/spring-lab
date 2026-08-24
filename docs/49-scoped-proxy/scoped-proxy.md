# 스코프드 프록시 — 실패를 없애는 게 아니라 "언제 터지는가"를 옮기는 장치

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`48`](../48-post-processor-ordering/post-processor-ordering.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`36`](../36-custom-scope/custom-scope.md)번 문서는 커스텀 `Scope` SPI 자체(스코프 하나를 어떻게 구현하는가)를 다뤘지만, 그 스코프의 빈을 **더 넓은 스코프(싱글턴)에 안전하게 주입**하는 방법인 `proxyMode`는 다루지 않았다. 이번엔 36번에서 이미 확인한 "스코프가 활성 상태가 아니면 예외가 난다"는 사실이, 스코프드 프록시 유무에 따라 정확히 "언제" 드러나는지를 직접 재현한다.

## 1. 이번 질문

- 좁은 스코프(요청/테넌트 등)의 빈을 싱글턴에 그냥 주입하면 실제로 무슨 일이 벌어지는가? 컨테이너 시작 자체가 실패하는가?
- `@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)`를 붙이면 그 실패가 정말 사라지는가, 아니면 다른 시점으로 옮겨질 뿐인가?
- 스코프드 프록시로 주입된 참조 하나가, 실제로 스코프를 전환할 때마다 다른 실제 객체로 정확히 리다이렉트되는가?
- 원래 빈 이름으로 등록되는 것이 정말 원본 객체가 아니라면, 그 원본은 어디로, 어떤 이름으로 옮겨가는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Scoped Beans as Dependencies")는 더 짧은 생명주기의 스코프 빈을 싱글턴에 주입하려면 `<aop:scoped-proxy/>`(XML) 또는 `@Scope(proxyMode=...)`(애너테이션)로 스코프드 프록시를 만들어야 한다고 설명하고, "실제 대상 객체 대신 프록시가 주입되어 필요할 때마다 실제 대상 객체를 요청한다"고 개념적으로 서술한다.
- 문서는 프록시가 "실패를 없애 준다"고는 말하지 않지만, 명시적으로 "그 실패를 언제부터 언제로 옮기는가"까지 다루지는 않는다 - 이번 실험이 채우려는 지점이다.
- `ScopedProxyMode`에는 `TARGET_CLASS`(CGLIB로 대상 클래스를 상속)와 `INTERFACES`(JDK 동적 프록시)가 있다는 것도 언급되지만, 두 모드가 만드는 프록시가 어떤 이름으로 등록되고 원본은 어디로 가는지는 API 문서 수준에서 다루지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 스코프드 프록시 없이 좁은 스코프 빈을 싱글턴 생성자에 주입하면, 애플리케이션 실행 중 그 빈을 실제로 "사용하려는 시점"에야 실패할 거라 예상했다 — **틀렸다.** 싱글턴은 컨테이너 시작 시점(`preInstantiateSingletons`)에 즉시(eager) 생성되고, 그 생성자 인자를 채우기 위해 스코프 빈이 즉시 요청되므로, 실패는 애플리케이션 로직이 실행되기도 전에 `refresh()` 자체에서 일어난다.
- 스코프드 프록시를 쓰면 스코프가 활성화돼 있지 않아도 항상 안전하게 동작할 거라 예상했다 — **틀렸다.** `refresh()`는 확실히 성공하지만, 그건 실패가 사라진 게 아니라 "프록시의 메서드를 실제로 호출하는 시점"으로 옮겨진 것뿐이다 - 스코프가 여전히 비활성 상태에서 프록시 메서드를 호출하면 정확히 같은 종류(`ScopeNotActiveException`)의 예외가, 다만 다른 이름의 빈("scopedTarget."으로 시작하는 내부 이름)을 대상으로 다시 난다.
- 원래 빈 이름("tenantWidgetProxied")으로 `getBean()`을 호출하면 실제 대상 클래스가 아니라 뭔가 감싼 객체(프록시)가 나올 거라 예상했는데, 그 프록시가 정확히 어떤 타입/인터페이스인지는 예상하지 못했다 - 실제로는 `ScopedObject`라는, 프록시임을 프로그램적으로 확인하고 대상을 조작할 수 있게 해 주는 전용 인터페이스를 구현하고 있었다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/scoped-proxy-lab`](../../experiments/scoped-proxy-lab)

```java
@Configuration
public class TenantWidgetConfig {
    @Bean
    @Scope(scopeName = "tenant", proxyMode = ScopedProxyMode.NO)      // 기본값
    public TenantWidget tenantWidgetNoProxy() { return new TenantWidget(); }

    @Bean
    @Scope(scopeName = "tenant", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public TenantWidget tenantWidgetProxied() { return new TenantWidget(); }
}

@Component
public class HolderProxied {
    private final TenantWidget widget;
    public HolderProxied(@Qualifier("tenantWidgetProxied") TenantWidget widget) {
        this.widget = widget;   // 실제로는 CGLIB 프록시가 주입됨
    }
    public TenantWidget widget() { return widget; }
}
```

```java
// 프록시 없이: 컨테이너 시작 자체가 실패한다
context.register(TenantWidgetConfig.class, HolderNoProxy.class);
context.refresh();
// → UnsatisfiedDependencyException ← ScopeNotActiveException:
//   "Scope 'tenant' is not active for the current thread; consider defining
//    a scoped proxy for this bean if you intend to refer to it from a singleton"

// 프록시 사용: 컨테이너 시작은 성공, 실패는 호출 시점으로 미뤄질 뿐
context.register(TenantWidgetConfig.class, HolderProxied.class);
context.refresh();                        // 성공
HolderProxied holder = context.getBean(HolderProxied.class);
holder.widget().id();                     // 지금에야 ScopeNotActiveException
                                           // (대상 빈 이름은 "scopedTarget.tenantWidgetProxied")

TenantContext.setTenant("acme");
holder.widget().id();                     // 1
TenantContext.setTenant("globex");
holder.widget().id();                     // 2 - 같은 프록시, 다른 실제 인스턴스
TenantContext.setTenant("acme");
holder.widget().id();                     // 1 - 같은 테넌트로 돌아오면 캐싱된 그 인스턴스
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ScopedProxyMode` | `NO`(기본값) / `TARGET_CLASS`(CGLIB 서브클래싱) / `INTERFACES`(JDK 동적 프록시) - `@Scope`가 만들 프록시의 종류를 지정 |
| `ScopedProxyUtils#createScopedProxy` | `@Scope(proxyMode=...)`가 명시되면 원본 빈 정의를 통째로 바꿔치기 - 원본 이름은 `ScopedProxyFactoryBean`(프록시)에게 넘기고, 진짜 빈 정의는 `"scopedTarget." + 원본이름`이라는 내부 이름으로 재등록 |
| `ScopedProxyFactoryBean` | `FactoryBean<Object>` - `getObject()`가 반환하는 것이 실제 CGLIB/JDK 프록시. 이 프록시가 구현하는 `ScopedObject` 덕분에 "이건 스코프드 프록시다"라는 걸 타입으로 확인할 수 있음 |
| `SimpleBeanTargetSource` (AOP `TargetSource`) | 프록시로 들어온 메서드 호출마다 "지금 이 순간" 대상 빈을 다시 조회하는 지점 - 이게 바로 "매번 현재 스코프의 인스턴스로 리다이렉트"가 실제로 일어나는 자리 |
| `TenantScope` (36번과 같은 패턴) | 테넌트별로 빈 인스턴스를 캐싱하고, 현재 테넌트가 없으면 예외를 던지는 커스텀 `Scope` 구현 |

## 6. 호출 흐름

```text
[프록시 없이 - ScopedProxyMode.NO]
preInstantiateSingletons() → HolderNoProxy 생성 시도
  → 생성자 인자 TenantWidget 해석 → getBean("tenantWidgetNoProxy")
    → AbstractBeanFactory#doGetBean → 스코프가 "tenant"임을 확인 → TenantScope#get() 호출
      → requireTenant() → 현재 스레드에 테넌트 없음 → IllegalStateException
    → ScopeNotActiveException으로 감싸짐("consider defining a scoped proxy...")
  → UnsatisfiedDependencyException으로 감싸짐 → refresh() 자체가 실패

[프록시 사용 - ScopedProxyMode.TARGET_CLASS]
파싱 단계에서 ScopedProxyUtils#createScopedProxy가 빈 정의를 바꿔치기:
  "tenantWidgetProxied"        → ScopedProxyFactoryBean(CGLIB 프록시를 만드는 FactoryBean)
  "scopedTarget.tenantWidgetProxied" → 진짜 TenantWidget의 @Scope("tenant") 빈 정의(autowireCandidate=false)

preInstantiateSingletons() → HolderProxied 생성 시도
  → 생성자 인자 TenantWidget 해석 → getBean("tenantWidgetProxied")
    → ScopedProxyFactoryBean#getObject() → 이미 만들어 둔 CGLIB 프록시 반환(대상은 아직 조회 안 함)
  → HolderProxied 생성 성공, refresh() 성공

이후 holder.widget().id() 호출(스코프 활성 여부와 무관하게 항상 이 프록시를 거침)
  → CGLIB 인터셉터 → SimpleBeanTargetSource#getTarget()
    → beanFactory.getBean("scopedTarget.tenantWidgetProxied")
      → TenantScope#get() 호출 ← 바로 이 순간에야 테넌트 유무가 확인됨
        - 테넌트 없음 → ScopeNotActiveException (대상은 "scopedTarget.tenantWidgetProxied")
        - 테넌트 "acme" → 캐시에 없으면 새로 생성해 캐싱, 있으면 캐싱된 것 반환
    → 실제 TenantWidget#id() 호출 → 결과 반환
```

`refresh()` 시점의 실패와 호출 시점의 실패가 어떻게 갈리는지, 그리고 빈 이름이 어떻게 바꿔치기되는지를 함께 그린 다이어그램: [`diagrams/scoped-proxy-flow.md`](diagrams/scoped-proxy-flow.md)

## 7. 브레이크포인트

이번 주제도 25~48번과 같은 이유로 `tools/jdi-tracer`를 통한 별도 추적은 하지 않았다 - 8번 절의 실행 결과(정확한 예외 타입·메시지·빈 이름)가 이미 충분히 구체적인 증거였다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 확인했다:

```text
org.springframework.aop.scope.ScopedProxyUtils#createScopedProxy
org.springframework.aop.scope.ScopedProxyFactoryBean
org.springframework.beans.factory.support.ScopeNotActiveException
```

## 8. 런타임 관찰

[`ScopedProxyTest`](../../experiments/scoped-proxy-lab/src/test/java/lab/experiments/scopedproxy/ScopedProxyTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| 프록시 없이, 테넌트 미바인딩 상태로 싱글턴 생성 | `UnsatisfiedDependencyException` ← `ScopeNotActiveException`(`refresh()` 시점에 즉시 실패) |
| 프록시 사용, 테넌트 미바인딩 상태로 싱글턴 생성 | `refresh()` 성공 - 하지만 `holder.widget().id()`를 호출하는 순간 `ScopeNotActiveException`(대상 빈 이름이 `"scopedTarget.tenantWidgetProxied"`로 바뀜) |
| 프록시 사용, "acme" → "globex" → "acme" 순으로 테넌트 전환하며 호출 | 매번 다른(또는 같은 테넌트로 돌아오면 같은) 실제 `TenantWidget` 인스턴스로 정확히 리다이렉트 |
| `getBean("tenantWidgetProxied")`의 실제 타입과 내부 빈 정의 존재 여부 | `ScopedObject`를 구현하는 CGLIB 프록시(`TenantWidget$$SpringCGLIB$$0`); `containsBeanDefinition("scopedTarget.tenantWidgetProxied")`는 `true` |

**직접 겪은 것**: 처음엔 "프록시 없이 실패하는 지점"의 예외 체인을 `hasRootCauseInstanceOf(ScopeNotActiveException.class)`로 검증하려다 테스트가 실패했다 - 실제 체인은 `UnsatisfiedDependencyException` → `ScopeNotActiveException` → `IllegalStateException`(우리 `TenantScope.requireTenant()`가 던진 것) 세 겹이라, "루트 원인"은 `ScopeNotActiveException`이 아니라 그 안의 `IllegalStateException`이었다. `hasRootCauseInstanceOf` 대신 `.cause()`로 바로 한 단계만 내려가는 것으로 고쳐서야 의도한 검증이 됐다 - 예외 체인이 몇 겹인지 미리 짐작하지 말고 실제로 찍어 봐야 한다는, 이 저장소가 반복해서 확인해 온 교훈을 다시 겪었다. 그리고 스파이크 단계에서 콘솔에 찍힌 실제 예외 메시지("consider defining a scoped proxy for this bean if you intend to refer to it from a singleton")가, 이번 실험이 재현하려던 바로 그 해법을 Spring 자신이 예외 메시지 안에 미리 알려 주고 있었다는 걸 보고 - 이 메시지 자체가 이미 이번 문서의 결론을 한 줄로 요약하고 있었다는 걸 깨달았다.

## 9. 공식 테스트 분석

이번 주제는 별도의 공식 유닛 테스트를 찾아 인용하는 대신, `ScopeNotActiveException`이 던져지는 실제 예외 메시지 자체("consider defining a scoped proxy for this bean if you intend to refer to it from a singleton")와 `ScopedProxyFactoryBean` 클래스 Javadoc("Proxies created using this factory bean are thread-safe singletons and may be injected into shared objects, with transparent scoping behavior")이 이미 충분히 구체적인 명세 역할을 했다 - 46·47·48번 문서가 예외 메시지·런타임 예외·소스 주석 하나로 설계 의도를 증명했던 것과 같은 정신이 이번에도 반복됐다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. CGLIB 서브클래싱 기반 프록시 생성과 `TargetSource`를 매 호출마다 다시 조회하는 AOP 인터셉터 체인(11·12주차의 `mini-aop`/`mini-auto-proxy`가 이미 다룬 프록시 메커니즘의 응용)은, 이번 주의 핵심 질문(실패가 "언제"로 옮겨지는가)에 비해 축소 구현으로 재현하기엔 지나치게 큰 부담이었다 - 11·12주차가 이미 다룬 "프록시가 메서드 호출을 가로챈다"는 골격 위에, 이번 주에 확인한 "그 가로채기가 매번 대상을 다시 조회한다"는 규칙을 개념적으로 얹어 이해하는 것으로 충분했다.

## 11. Spring 설계 의도

- **왜 실패를 아예 없애지 않고 시점만 옮기는가**: 스코프드 프록시의 목적은 "스코프 불일치를 무시해도 된다"가 아니라 "생성자 주입이라는, 원래는 즉시 실행돼야 하는 지점과, 실제 사용이라는 지연 가능한 지점을 분리"하는 것이다. 만약 스코프가 정말 비활성 상태인데도 프록시가 조용히 기본값이나 `null`을 반환하게 만들었다면, 그건 실패를 감추는 것이지 해결하는 게 아니다 - 대신 실패의 "종류"는 그대로 유지하면서 "시점"만, 정말로 그 값이 필요해지는 순간(메서드 호출)까지 미루는 것이 스코프 불일치를 정직하게 다루는 방법이다. 이 저장소가 44번(`AopContext`/`exposeProxy`)과 47번(`ConfigurationCondition`)에서 반복해서 본 "잘못된 사용을 조용히 허용하기보다 명확하게(그리고 가능한 한 정확한 시점에) 실패시킨다"는 원칙이 여기서도 그대로 나타난다.
- **왜 원본 빈을 별도 이름("scopedTarget.")으로 옮기고 `autowireCandidate=false`로 만드는가**: 만약 원본 빈이 원래 이름을 유지한 채 프록시와 공존한다면, 타입 기반 자동 와이어링이 어느 쪽을 골라야 할지 모호해진다(둘 다 `TenantWidget` 타입이므로). 원본을 "숨겨진" 내부 이름으로 옮기고 자동 와이어링 후보에서 제외하는 것은, 이 저장소가 45번(BeanDefinition overriding)·46번(제네릭 의존성)에서 반복해서 본 "컨테이너가 후보를 좁힐 때 모호함을 만들지 않도록 미리 설계한다"는 원칙과 같은 방향이다 - 사용자가 "tenantWidgetProxied"를 요청하면 항상 프록시만 받도록, 선택의 여지 자체를 원천적으로 없애 버린 것이다.
- **왜 프록시가 `ScopedObject`라는 별도 인터페이스를 구현하게 하는가**: 스코프드 프록시는 단순히 "투명하게 위임만 하는 객체"가 아니라, 호출하는 쪽이 명시적으로 "이건 프록시다, 필요하면 대상을 직접 조작하고 싶다"고 표현할 수 있는 통로도 함께 제공한다(`getTargetObject()`/`removeFromScope()`). 이건 32번(`FactoryBean`)에서 확인한 `&` 접두사 역참조와 같은 결의 설계다 - "평소엔 실제 객체처럼 투명하게 동작하되, 필요할 때는 그 이면의 메커니즘에 접근할 수 있는 탈출구를 남겨 둔다"는 원칙이 이번엔 별도 마커 인터페이스라는 다른 형태로 반복된다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 스코프드 프록시가 "실패를 없애 준다"는 첫인상이 실제로는 "실패의 시점을 옮겨 줄 뿐"이라는 것 - 처음엔 이 둘이 거의 같은 말처럼 느껴졌는데, 프록시를 써도 여전히 테넌트 없이 메서드를 호출하면 (예외 메시지의 대상 빈 이름만 다를 뿐) 똑같은 `ScopeNotActiveException`이 난다는 걸 직접 보고 나서야, "프록시는 안전망이 아니라 타이밍 조정 장치"라는 걸 정확히 구분하게 됐다.
- 예상 밖이었던 것: `ScopeNotActiveException`의 메시지가 이미 "스코프드 프록시를 고려하라"고 스스로 알려 준다는 것 - Spring이 이 실수(좁은 스코프 빈을 싱글턴에 직접 주입)를 아주 흔한 실수로 예상하고, 예외 메시지 자체에 해법까지 미리 심어 뒀다는 걸 실행 로그를 통해 처음 알게 됐다.
- 예상대로였던 것(재확인): 36번에서 이미 배운 "스코프 SPI는 캐싱/조회 책임을 전적으로 `Scope` 구현체에 위임한다"는 원칙이, 이번엔 "그 `Scope#get()`이 정확히 언제 호출되는가"라는 다른 축에서도 그대로 적용됐다 - 프록시가 있든 없든 결국 `TenantScope#get()`이 호출되는 지점은 똑같이 "누군가 그 빈을 실제로 필요로 하는 순간"이고, 프록시는 그 "누군가"가 컨테이너 자신(생성자 주입)이 아니라 애플리케이션 코드(메서드 호출)가 되도록 바꿔치기한 것뿐이었다.
- 새로 배운 것: 빈 이름 하나("tenantWidgetProxied")가 실제로는 완전히 다른 두 개의 빈 정의(공개용 프록시 + 내부용 원본)로 쪼개질 수 있다는 것, 그리고 그 내부 이름에 `"scopedTarget."`이라는 고정 접두사가 붙는다는 것 - 32번의 `&` 접두사, 45번의 override 규칙, 46번의 선언된 타입 규칙에 이어, 이 저장소가 계속 봐 온 "빈 이름/이름 규약이 곧 계약"이라는 패턴이 이번엔 "이름 하나가 두 개의 빈 정의로 갈라진다"는 새로운 형태로 나타났다.
