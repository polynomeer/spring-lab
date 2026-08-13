# 캐시 추상화 — @Cacheable이 "동시성 안전"을 보장하는 지점은 어디부터 어디까지인가

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서는 그 카탈로그에 속하지 않는, 로드맵 밖 심화 주제다. AOP 프록시(11·12주차)·`@Transactional`(13·14주차)에서 배운 "인터셉터 체인 + 애노테이션 → 전략 객체 변환 + self-invocation 우회"라는 구조가 캐시 추상화(`@Cacheable`)에도 거의 그대로 반복되는지, 그리고 어디서부터 달라지는지를 확인한다.

## 1. 이번 질문

- `@Cacheable`/`@CachePut`/`@CacheEvict`는 프록시 위에서 정확히 무엇을 가로채는가 — `@Transactional`의 `TransactionInterceptor`와 구조가 얼마나 대칭적인가?
- 캐시 키는 기본적으로 어떻게 생성되는가 — **메서드가 다르면 키도 자동으로 구분되는가?**
- `sync = true`는 무엇을 다르게 하는가 — 그냥 캐시 조회에 `synchronized`를 붙이는 것과 어떻게 다른가?
- 대상 메서드가 예외를 던지면 그 결과도 캐시되는가?
- self-invocation은 여기서도 (AOP 프록시 일반과 마찬가지로) 캐싱을 우회하는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Cache Abstraction")는 `@Cacheable`/`@CachePut`/`@CacheEvict`/`@Caching`/`@CacheConfig`의 속성(`value`/`cacheNames`, `key`, `keyGenerator`, `condition`, `unless`, `sync`)을 설명한다.
- 문서는 **기본 키 생성 전략(`SimpleKeyGenerator`)이 메서드 시그니처가 아니라 인자만으로 키를 만든다**는 것, 그리고 그로 인해 "서로 다른 메서드가 같은 캐시를 공유하면 충돌할 수 있으니 주의하라"고 명시적으로 경고한다 — 이번 실험은 그 경고를 실제로 재현한 것이다.
- `sync`에 대해서는 "동시에 같은 키를 요청하는 스레드가 여럿이면 하나만 실제로 계산하게 만든다"고 설명하지만, 그 동시성 보장이 정확히 어디서(캐시 추상화 계층 자신인지, 아니면 `CacheManager` 구현체인지) 이뤄지는지는 레퍼런스 문서만으로는 알 수 없었다 — 소스로 확인해야 했다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 캐시 이름(`cacheNames`)이 곧 캐시 인스턴스이므로, 같은 캐시를 쓰더라도 메서드가 다르면 당연히 키가 자동으로 구분될 거라 예상했다 — **틀렸다.**
- `sync = true`는 캐시 추상화 계층이 새로운 `Lock`이나 `synchronized` 블록을 직접 만들어 구현할 거라 예상했다 — **부분적으로 틀렸다.** 실제로는 `Cache#get(Object key, Callable<T> valueLoader)`라는 계약에 위임되고, `ConcurrentMapCache`의 경우 그 계약이 `ConcurrentHashMap#computeIfAbsent`로 구현된다.
- 예외가 발생해도 "일단 캐시부터 갱신한 뒤 예외를 던질 것"이라 예상했다 — **틀렸다.** 캐시 쓰기는 대상 메서드가 정상적으로 값을 반환한 뒤에만 일어난다.
- self-invocation은 11·12주차에서 배운 대로 당연히 우회될 거라 예상했다 — **맞았다**(재확인).

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/cache-abstraction-lab`](../../experiments/cache-abstraction-lab)

```java
@Configuration
@EnableCaching
public class CachePlaygroundConfig {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
```

```java
@Cacheable(cacheNames = "products", key = "#id")
public Product findById(String id) { ... }

@CachePut(cacheNames = "products", key = "#product.id()")
public Product save(Product product) { ... }

// 프록시를 거치지 않는 내부 호출 - @Cacheable이 적용되지 않는다.
public Product refreshViaSelfInvocation(String id) {
    return this.findById(id);
}

// 서로 다른 메서드가 같은 캐시 이름 + 같은 단일 String 인자를 쓰면 키가 충돌한다.
@Cacheable(cacheNames = "collision-cache")
public Product findByIdRaw(String id) { ... }

@Cacheable(cacheNames = "collision-cache")
public String findNameRaw(String id) { ... }
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `CacheInterceptor` | 프록시가 실제로 호출하는 `MethodInterceptor` 구현체 — `TransactionInterceptor`와 대칭 |
| `CacheAspectSupport` | `execute()`/`executeSynchronized()` 핵심 로직을 담은 상위 클래스 — `TransactionAspectSupport`와 대칭 |
| `AnnotationCacheOperationSource` | `@Cacheable`/`@CachePut`/`@CacheEvict` 애노테이션을 `CacheOperation`으로 변환 |
| `BeanFactoryCacheOperationSourceAdvisor` | `CacheOperationSource`가 뭔가를 반환하는 메서드에만 어드바이스를 적용하는 `Pointcut` + `Advisor` |
| `SimpleKeyGenerator` | 기본 키 생성 전략 — **`Method` 파라미터를 아예 쓰지 않는다** |
| `CacheManager`/`Cache` | 실제 저장소 추상화 — 이번 실험은 `ConcurrentMapCacheManager`/`ConcurrentMapCache` 사용 |
| `CacheErrorHandler` | 캐시 자체(조회/쓰기)에서 발생하는 예외를 다루는 전략 — 대상 메서드의 예외와는 별개 |
| (실험) `InvocationCounter` | 실제 대상 메서드가 몇 번 실행됐는지 계측 — 반환값만으로는 캐시 히트인지 재계산인지 구분 안 됨(mini-webmvc의 `CountingArgumentResolver`와 같은 역할) |

## 6. 호출 흐름

```text
프록시 메서드 호출
  → CacheInterceptor#invoke → CacheAspectSupport#execute(invoker, target, method, args)
      → AnnotationCacheOperationSource#getCacheOperations(method, targetClass)로 CacheOperation 목록 조회
      → contexts.isSynchronized()?
          ┌─ [일반 @Cacheable 경로] ──────────────────────────────────────────────
          │ processCacheEvicts(beforeInvocation=true 인 @CacheEvict부터 먼저 처리)
          │ findCachedValue() → findInCaches() → cache.get(key)  (읽기 - "확인"만 함)
          │   히트면 그 값을 그대로 반환
          │   미스면 evaluate() → invokeOperation() (대상 메서드 실제 호출)
          │     → 정상 반환 시에만 cache.put(key, value)  (쓰기 - "확인"과 분리된 별도 단계)
          │     → 예외면 ThrowableWrapper로 감싸 던짐 - cache.put은 아예 호출되지 않음
          └────────────────────────────────────────────────────────────────────
          ┌─ [sync = true 경로] ──────────────────────────────────────────────
          │ executeSynchronized()
          │   → doGet(cache, key, () -> invokeOperation(invoker))
          │       → cache.get(key, Callable<T> valueLoader)
          │           (ConcurrentMapCache는 store.computeIfAbsent(key, ...)로 구현 -
          │            같은 키에 대한 매핑 함수는 원자적으로 딱 한 번만 실행)
          └────────────────────────────────────────────────────────────────────
      → @CachePut: 조회 없이 항상 invokeOperation() 실행 후 cache.put(key, value)
      → @CacheEvict(beforeInvocation=false, 기본값): invokeOperation() 성공 후 cache.evict(key)/clear()
```

self-invocation의 경우, `this.findById(id)`처럼 같은 객체 안에서 호출하면 위 흐름 전체가 시작되지 않는다 — `CacheInterceptor`가 끼어들 프록시 경계 자체를 거치지 않기 때문이다(12주차 자동 프록시 생성/self-invocation과 동일한 구조).

호출 흐름과 self-invocation 경계를 함께 그린 다이어그램: [`diagrams/cache-interceptor-flow.md`](diagrams/cache-interceptor-flow.md)

## 7. 브레이크포인트

이번 주제도 17주차와 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 — 핵심이 "어떤 메서드가 호출되는가"라는 제어 흐름보다 "언제 읽고 언제 쓰는가"라는 순서와 원자성이라, `InvocationCounter`로 실제 호출 횟수를 계측하는 실행 기반 확인이 더 직접적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.cache.interceptor.CacheAspectSupport#execute(CacheOperationInvoker, Object, Method, Object[])
org.springframework.cache.interceptor.CacheAspectSupport#execute(CacheOperationInvoker, Method, CacheOperationContexts)
org.springframework.cache.interceptor.CacheAspectSupport#executeSynchronized
org.springframework.cache.interceptor.CacheAspectSupport#findCachedValue
org.springframework.cache.interceptor.CacheAspectSupport#evaluate
org.springframework.cache.interceptor.SimpleKeyGenerator#generate
org.springframework.cache.concurrent.ConcurrentMapCache#get(Object, Callable)
```

## 8. 런타임 관찰

[`CacheableCachePutEvictTest`](../../experiments/cache-abstraction-lab/src/test/java/lab/experiments/cache/CacheableCachePutEvictTest.java) (7개):

| 실험 | 결과 |
| --- | --- |
| 같은 키로 두 번 호출 | 대상 메서드 1회만 실행 (캐시 히트) |
| 다른 키로 두 번 호출 | 대상 메서드 2회 실행 |
| `@CachePut`으로 갱신 후 `@Cacheable`로 조회 | `@CachePut`이 쓴 값을 재계산 없이 그대로 읽음 — 같은 캐시·같은 키를 공유하기 때문 |
| `@CacheEvict(key=...)` | 지정한 키만 제거, 나머지 키는 유지 |
| `@CacheEvict(allEntries=true)` | 캐시 전체 제거 |
| `unless`로 걸러진 결과 | 매번 대상 메서드가 실행됨(캐시가 아예 안 됨) — `unless`는 호출 "후"에 평가되므로 |
| `condition = "#useCache"`가 `false` | 캐시 조회/저장 자체가 발생하지 않음 — `condition`은 호출 "전"에 평가되므로 |

[`CacheEdgeCasesTest`](../../experiments/cache-abstraction-lab/src/test/java/lab/experiments/cache/CacheEdgeCasesTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| `this.findById(id)`로 self-invocation | 프록시를 거치지 않아 `@Cacheable`이 적용되지 않음 — 이미 캐시된 키인데도 대상 메서드가 다시 실행됨. 그 호출은 캐시에 쓰지도 않으므로, 이후 프록시를 통한 호출은 원래 캐시된 값을 그대로 히트함 |
| 대상 메서드가 예외를 던짐 | 캐시에 아무것도 남기지 않음 — 다음 호출도 다시 대상 메서드를 실행함 |
| 서로 다른 두 메서드(`findByIdRaw`/`findNameRaw`)가 같은 캐시 이름 + 같은 단일 `String` 인자 | **키가 그대로 충돌** — 먼저 캐시를 채운 메서드의 반환 타입(`Product`)을 다른 메서드가 캐시 히트로 그대로 돌려받으려다 `ClassCastException` 발생. 대상 메서드는 호출조차 안 됨(진짜 히트였다는 뜻) |
| `sync = true` 메서드를 스레드 8개가 같은 키로 동시 호출 | 대상 메서드는 **정확히 1번**만 실행됨 — 나머지 7개는 그 결과를 기다렸다가 받음 |

**직접 겪은 것**: 세 번째 행(키 충돌)은 처음엔 "그냥 값이 서로 덮어써지는 정도"일 거라 예상하고 작성했는데, 실행해 보니 `ClassCastException`이 실제로 발생했다 — 반환 타입이 다른 두 메서드가 캐시를 공유하면 단순한 값 오염이 아니라 **런타임 타입 에러**로 이어진다는 것을 예상보다 훨씬 분명하게 확인했다. 네 번째 행(동시성)도 처음엔 타이밍에 따라 결과가 들쭉날쭉한 flaky 테스트가 될까 걱정했는데, `ConcurrentHashMap#computeIfAbsent`의 원자성 덕분에 스레드 수를 늘려도(8개) 매번 정확히 1회로 결정론적이었다 — "왜 이게 결정론적인가"의 답은 9번 절의 소스 확인에 있다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `SimpleKeyGeneratorTests#singleValue`(`spring-context/src/test/java/org/springframework/cache/interceptor/`): 인자가 하나면 `generateKey(new Object[]{"a"})`가 `"a"` 자신과 `isEqualTo`로 같다고 단언한다 — **키에 메서드 정보가 전혀 섞이지 않는다**는 것을 공식 테스트로도 확인. 8번 절의 키 충돌 실험이 왜 그렇게 재현되는지의 직접적인 근거다.
- `CacheSyncFailureTests`(같은 패키지): `sync = true`를 `unless`와 같이 쓰거나, 여러 캐시에 걸치거나, 같은 메서드에 다른 캐시 오퍼레이션과 함께 쓰면 **컨텍스트 초기화 시점에 `IllegalStateException`으로 즉시 막는다**는 것을 확인했다 — `CacheAspectSupport`가 이런 위험한 조합은 애초에 등록을 거부한다.
- 그런데 8번 절에서 재현한 "서로 다른 메서드가 같은 캐시 이름 + 같은 키 모양을 공유하는" 충돌에 대해서는 이런 fail-fast 가드가 **존재하지 않는다** — 공식 테스트 스위트에도 이 조합을 막는 테스트가 없다. `sync` 오용은 Spring이 스스로 막아 주지만, 캐시 네임스페이스 설계는 전적으로 사용자 책임으로 남겨져 있다는 비대칭을 확인했다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제는 별도의 mini 구현을 만들지 않았다. `CacheInterceptor`의 구조(애노테이션 → 전략 객체 변환, `Pointcut`+`Advisor`로 대상 메서드만 골라 어드바이스 적용, self-invocation이 우회되는 이유)는 이미 `mini-spring/mini-aop`·`mini-spring/mini-auto-proxy`·`mini-spring/mini-transaction`이 다룬 것과 구조적으로 동일해서, 뼈대를 다시 만드는 것은 새로운 학습이 되지 않는다고 판단했다. 이번 주의 실제 가치는 뼈대가 아니라 **행동**(키 충돌·`sync`의 동시성 경계·예외 시 미스캐싱)에 있었고, 그건 mini 구현보다 실제 Spring을 실행해서 확인하는 쪽이 훨씬 직접적이었다 — 17주차가 같은 이유로 mini 구현을 생략했던 것과 같은 판단이다.

## 11. Spring 설계 의도

- **왜 `CacheAspectSupport`가 `TransactionAspectSupport`와 구조적으로 대칭인가**: 둘 다 "애노테이션 → `OperationSource`로 전략 객체 조회 → `Interceptor`가 그 전략 객체를 갖고 실행"이라는 같은 패턴을 따른다. 이 저장소가 11~14주차에서 반복해서 본 "인터셉터 뒤에 전략 객체를 갈아 끼운다"는 설계가, AOP 위에서 구현되는 Spring의 다른 선언적 기능(캐싱, 트랜잭션, 나중엔 `@Async`, `@Retryable`)에도 그대로 재사용되는 하나의 표준 골격이라는 것을 확인했다.
- **왜 `SimpleKeyGenerator`는 메서드를 무시하도록 설계됐는가**: `cacheNames`를 메서드 하나에 대응하는 완전한 네임스페이스로 가정하지 않고, 여러 메서드가 의도적으로 같은 캐시(같은 논리적 엔터티)를 공유할 수 있다는 유연성을 열어 두기 위한 설계로 보인다 — 예를 들어 `findById(id)`와 `save(entity)`가 같은 캐시·같은 키로 서로의 결과를 주고받는 것(8번 절의 `save()`→`findById()` 실험)은 이 설계 덕분에 자연스럽게 가능하다. 대가는 "실수로 공유"와 "의도적으로 공유"를 프레임워크가 구분할 방법이 없다는 것이다 — 그래서 `sync` 오용은 막아도 이 충돌은 막지 않는 비대칭(9번 절)이 생긴다.
- **왜 `sync = true`가 새 락 대신 `Cache#get(key, Callable)` 위임으로 구현됐는가**: 캐시 추상화 계층은 로컬 `ConcurrentHashMap`부터 Redis 같은 분산 캐시까지 서로 다른 `CacheManager` 구현체를 감싼다. "동시 요청을 어떻게 직렬화할 것인가"의 최적 방법은 구현체마다 다르다(로컬은 `computeIfAbsent`로 충분하지만, 분산 캐시는 분산 락이 필요할 수 있다) — 그래서 추상화 계층은 "한 번만 계산해야 한다"는 계약만 `Cache#get(key, Callable)` 인터페이스로 강제하고, 그 계약을 어떻게 지킬지는 각 구현체의 책임으로 넘긴다. 확장점을 좁게 쪼갠다는 이 저장소의 반복된 원칙(회고 문서)이 여기서는 "동시성 보장 메커니즘 자체를 구현체에 위임한다"는 형태로 나타난다.
- **왜 예외가 발생한 호출은 캐시되지 않는가**: `evaluate()`가 `cache.put()`을 호출하는 지점은 `invokeOperation()`이 정상적으로 값을 반환한 뒤뿐이다. 일시적 장애(타임아웃, 네트워크 오류 등)로 실패한 호출 결과를 캐시해 버리면, 캐시 TTL이 끝날 때까지 매 요청이 그 실패를 그대로 재현하는 훨씬 심각한 장애로 이어진다 — "계산 결과를 저장한다"는 캐시 본연의 목적에서, 실패는 애초에 "결과"가 아니라는 판단이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: "캐시 이름이 같아도 메서드가 다르면 당연히 구분되겠지"라는 직관이 완전히 틀렸다 — `SimpleKeyGenerator`가 메서드를 아예 보지 않는다는 사실 하나가, 반환 타입이 다른 두 메서드 사이의 `ClassCastException`까지 실제로 만들어 냈다.
- 예상 밖이었던 것: `sync = true`의 "동시성 안전"이 캐시 추상화 계층 자신의 락이 아니라, 각 `Cache` 구현체(`ConcurrentMapCache`의 경우 `ConcurrentHashMap#computeIfAbsent`)에 위임된 계약이라는 것 — 즉 이 보장의 강도는 어떤 `CacheManager`를 쓰느냐에 따라 달라질 수 있다는 뜻이다.
- 예상대로였던 것(재확인): self-invocation이 캐싱을 우회한다는 것, 그리고 실패한 호출은 캐시되지 않는다는 것 — 둘 다 AOP 프록시의 일반 원칙(11·12주차)과 "부작용 없는 정상 경로만 캐시한다"는 상식적인 설계가 그대로 들어맞았다.
- 새로 배운 것: Spring이 위험한 설정을 **항상** 막아 주는 것은 아니라는 비대칭 — `sync` 오용 조합은 컨텍스트 초기화 시점에 fail-fast로 막지만(9번 절), 캐시 네임스페이스 설계(키 충돌 가능성)는 전적으로 개발자 책임으로 남겨 둔다. "프레임워크가 어디까지 안전망을 쳐 주는가"는 기능마다 다르다는 것을, 코드가 아니라 실제로 막히는 것과 안 막히는 것을 나란히 실행해 봐야 알 수 있었다.
