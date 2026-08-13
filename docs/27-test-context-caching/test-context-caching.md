# TestContext 프레임워크 — 이 저장소의 모든 테스트가 매번 새 ApplicationContext를 만들지 않는 이유

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25-cache-abstraction`](../25-cache-abstraction/cache-abstraction.md)·[`26-method-validation`](../26-method-validation/method-validation.md)과 마찬가지로 그 카탈로그 밖의 심화 주제다. 다만 앞의 둘은 "인터셉터 + 애노테이션→전략 객체 변환"이라는 같은 AOP 골격의 세 번째·네 번째 변주였던 반면, 이번 주제는 의도적으로 다른 확장 메커니즘을 고른다 — `TestExecutionListener` 체인과 `ContextCache`. 이 저장소의 실험 모듈은 지금까지 전부 `new AnnotationConfigApplicationContext(...)`를 테스트마다 직접 만들어 왔는데, 실제 Spring 프로젝트의 표준 방식(`@ExtendWith(SpringExtension.class)` + `@ContextConfiguration`)은 그렇게 하지 않는다 - 왜, 그리고 정확히 어떤 조건에서 컨텍스트를 재사용하는지를 직접 실행해서 확인한다.

## 1. 이번 질문

- `SpringExtension`은 테스트 클래스마다 정말 새 `ApplicationContext`를 만드는가, 아니면 재사용하는가?
- 재사용한다면 "같은 설정"의 기준은 무엇인가 — 클래스 이름? 빈 구성? 다른 무언가?
- `@DirtiesContext`는 정확히 무엇을 하는가 — 즉시 컨텍스트를 닫는가, 아니면 나중에 닫는가?
- 같은 클래스 안의 `@Test` 메서드들은 매번 새 컨텍스트를 받는가?
- 컨텍스트가 재사용된다면, 그 안의 싱글턴 빈 상태도 테스트 사이에 이어지는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Testing", "Context Caching")는 "동일한 설정을 사용하는 테스트들은 `ApplicationContext`를 캐시해서 재사용한다"고 명시하고, 그 목적이 "테스트 스위트 전체의 실행 속도"라고 설명한다.
- 문서는 캐시 키가 `locations`, `classes`, `contextInitializerClasses`, `activeProfiles`, `propertySourceLocations`, `propertySourceProperties`, `contextCustomizers`, `parent`, `contextLoader` 등을 포함한 "컨텍스트를 만드는 데 필요한 모든 구성 요소의 조합"이라고 설명한다 — 이번 실험은 그중 가장 단순한 축("설정 클래스가 같은가/다른가")만 검증한다.
- `@DirtiesContext`에 대해서는 "이 컨텍스트가 테스트로 인해 오염됐으니 캐시에서 제거하라"는 신호라고 설명하고, 클래스 레벨 기본 모드가 `AFTER_CLASS`라고 명시한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `@ExtendWith(SpringExtension.class)`가 붙은 두 개의 별도 테스트 클래스는, 설정이 같아도 서로 다른 `TestContextManager` 인스턴스를 가지므로 각자 자기만의 `ApplicationContext`를 만들 거라 예상했다 — **틀렸다.** 캐시가 `TestContextManager`가 아니라 JVM 전체에서 공유되는 정적(static) 저장소라서, 설정이 같으면 완전히 하나로 합쳐진다.
- `@DirtiesContext`가 있으면 그 즉시(테스트 메서드 실행 전이나 직후) 컨텍스트가 닫힐 거라 예상했다 — **부분적으로 틀렸다.** 클래스 레벨 기본 모드(`AFTER_CLASS`)에서는, 그 클래스 자신의 테스트 실행 중에는 캐시된 컨텍스트를 정상적으로 재사용하고, 클래스의 모든 테스트가 끝난 "뒤"에야 폐기된다.
- 캐시가 재사용되는 만큼, 그 안의 싱글턴 빈은 매 테스트 메서드마다 새로 만들어질 거라 예상했다 — **틀렸다.** 컨텍스트가 재사용되면 그 안의 싱글턴 빈 인스턴스도 그대로 재사용된다 — 상태가 있는 빈이라면 이전 테스트가 남긴 상태를 그대로 물려받는다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/test-context-caching-lab`](../../experiments/test-context-caching-lab)

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SharedOnceConfig.class)
public class SharedOnceProbeA {
    @Test void justRuns() { ... }
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SharedOnceConfig.class)   // 완전히 같은 설정 클래스
public class SharedOnceProbeB {
    @Test void justRuns() { ... }
}
```

두 클래스를 각각 별도의 `TestExecutionListener` 생명주기로 실행한 뒤, `SharedOnceConfig`가 등록하는 빈의 생성자 호출 횟수(`ContextCreationCounter`)를 확인한다 — 이 저장소는 두 클래스를 격리된 프로세스로 실행할 수 없으므로, JUnit Platform Launcher API로 같은 JVM 안에서 프로그래밍적으로 구동해서 관찰했다(7번 절 참고).

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `SpringExtension` | JUnit Jupiter의 `Extension` SPI 구현체 - `TestContextManager`를 만들고 각 JUnit 콜백(빈 주입, 컨텍스트 준비 등)을 그 위에 위임한다 |
| `TestContextManager` | 한 테스트 클래스에 대응하는 조정자 - 등록된 `TestExecutionListener` 체인을 순서대로 호출한다 |
| `TestExecutionListener` | `beforeTestClass`/`prepareTestInstance`/`beforeTestMethod`/`afterTestMethod`/`afterTestClass` 콜백 - `DependencyInjectionTestExecutionListener`(`@Autowired` 필드 주입), `DirtiesContextTestExecutionListener` 등이 기본으로 등록된다 |
| `CacheAwareContextLoaderDelegate` | "이 `MergedContextConfiguration`에 대한 컨텍스트를 캐시에서 찾거나, 없으면 만들어서 캐시에 넣는다"는 get-or-create 연산의 진입점 |
| `DefaultContextCache` | `LinkedHashMap` 기반 **LRU 캐시** - 기본 최대 크기(`ContextCache.DEFAULT_MAX_CONTEXT_CACHE_SIZE`)를 넘으면 가장 오래 안 쓴 항목부터 제거 |
| `MergedContextConfiguration` | 캐시 키 자신 - `equals()`/`hashCode()`가 설정 클래스 배열·프로퍼티·프로파일·부모 컨텍스트 등을 전부 포함해서 비교한다 |
| `DirtiesContextTestExecutionListener` | `@DirtiesContext`를 읽어 `TestContext#markApplicationContextDirty()`를 호출 - 실제 캐시 제거는 그 마킹이 처리되는 시점(모드에 따라 메서드 전/후, 클래스 전/후)에 일어난다 |
| (실험) `ContextCreationCounter` | 생성자가 실제로 몇 번 호출됐는지 계측 - 반환값만으로는 캐시 히트인지 새로 만든 것인지 구분 안 됨 |

## 6. 호출 흐름

```text
JUnit이 테스트 클래스 인스턴스를 만듦
  → SpringExtension#postProcessTestInstance (JUnit 콜백)
      → TestContextManager#prepareTestInstance
          → 각 TestExecutionListener#prepareTestInstance 순서대로 호출
              → DependencyInjectionTestExecutionListener
                  → TestContext#getApplicationContext()
                      → CacheAwareContextLoaderDelegate#loadContext(mergedConfig)
                          → synchronized (contextCache) {
                                context = contextCache.get(mergedConfig)   ← 캐시 조회
                                if (context == null) {
                                    context = loadContextInternal(mergedConfig)  ← 새로 생성
                                    contextCache.put(mergedConfig, context)
                                }
                             }
                  → AutowiredAnnotationBeanPostProcessor류를 통해 테스트 인스턴스의
                    @Autowired 필드에 주입
  → 각 @Test 메서드 실행 (같은 클래스 안에서는 매번 같은 TestContext, 같은 컨텍스트)
  → TestContextManager#afterTestClass
      → DirtiesContextTestExecutionListener#afterTestClass
          → 클래스 레벨 @DirtiesContext가 있으면(기본 AFTER_CLASS)
              testContext.markApplicationContextDirty()
                  → contextCache.remove(mergedConfig) + context.close()
```

self-invocation 우회(25·26번)와 달리, 이번 메커니즘은 프록시가 아니라 **테스트 실행 생명주기 콜백 체인**이라는 완전히 다른 확장점이다 — 그런데도 "get-or-create + 키 기반 캐시"라는 아이디어 자체는 `DefaultSingletonBeanRegistry`(4주차, 빈 하나를 캐시하는 것)의 정신을 "빈 하나"가 아니라 "컨텍스트 전체"로 한 단계 끌어올린 것으로 볼 수 있다.

컨텍스트 캐시 히트/미스 경로와 `@DirtiesContext`가 캐시를 제거하는 시점을 함께 그린 다이어그램: [`diagrams/context-cache-flow.md`](diagrams/context-cache-flow.md)

## 7. 브레이크포인트

이번 주제는 25·26번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 — 핵심이 "캐시가 재사용되는가"라는 실행 결과였고, 그건 `ContextCreationCounter`로 직접 관찰하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate#loadContext
org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate (defaultContextCache 정적 필드)
org.springframework.test.context.cache.DefaultContextCache (LruCache 내부 클래스)
org.springframework.test.context.support.AbstractDirtiesContextTestExecutionListener#dirtyContext
org.springframework.test.context.support.DirtiesContextTestExecutionListener
```

이번 주제는 실제 실행이 만든 "격리 불가능"이라는 제약도 하나 있었다: `DefaultCacheAwareContextLoaderDelegate.defaultContextCache`가 JVM 전체에서 공유되는 `static final` 필드라서, 이 실험을 별도 테스트 클래스 여러 개로 자연스럽게 나눠서 실행하면 서로의 캐시 상태를 오염시킨다. `JUnit Platform Launcher` API로 "probe" 테스트 클래스들을 하나의 드라이버 테스트 안에서 프로그래밍적으로 직접 구동해서, 실행 순서와 시점을 결정론적으로 통제했다(8번 절).

## 8. 런타임 관찰

[`TestContextCachingTest`](../../experiments/test-context-caching-lab/src/test/java/lab/experiments/testcontext/TestContextCachingTest.java) (3개, JUnit Platform Launcher로 `probes` 패키지의 테스트 클래스를 직접 구동):

| 실험 | 결과 |
| --- | --- |
| 같은 설정 클래스를 쓰는 별도의 두 테스트 클래스 | `ApplicationContext` 생성 **1회** — 두 번째 클래스는 캐시 히트 |
| 구조는 같지만 클래스 자체가 다른 두 설정 | `ApplicationContext` 생성 **2회** — 캐시 키가 클래스 자체를 구분함 |
| 같은 설정 클래스 A → `@DirtiesContext`가 붙은 같은 설정 클래스 → 같은 설정 클래스 B | 1회(A, 새로 생성) → 1회 그대로(dirty 클래스는 캐시를 재사용해서 실행됨) → **2회**(B, dirty 클래스가 끝나면서 캐시가 폐기돼 새로 생성) |

[`BeanStatePersistsAcrossTestMethodsTest`](../../experiments/test-context-caching-lab/src/test/java/lab/experiments/testcontext/BeanStatePersistsAcrossTestMethodsTest.java) (2개, 순서 고정):

| 실험 | 결과 |
| --- | --- |
| 같은 클래스의 `@Test` 메서드 1이 싱글턴 빈 상태를 1로 증가 | 값 1 |
| 이어지는 `@Test` 메서드 2가 그 빈을 다시 조회 | 리셋된 0이 아니라 **1을 그대로 물려받음** — 같은 컨텍스트, 같은 싱글턴 인스턴스이기 때문 |

**직접 겪은 것**: 세 번째 행(`@DirtiesContext`)을 처음 설계할 때는 "dirty로 표시된 클래스 자신의 테스트 실행도 캐시 미스로 새 컨텍스트를 받을 것"이라 예상했는데, 실행해 보니 그 클래스 자신은 여전히 **재사용된 컨텍스트**로 실행되고(카운터가 그대로 1), 오직 그 클래스가 다 끝난 **다음** 테스트만 새 컨텍스트를 받았다(카운터가 2로). `AbstractDirtiesContextTestExecutionListener`의 소스(9번 절)를 보고서야, `@DirtiesContext`는 "지금 이 컨텍스트를 오염시켰다"는 신호일 뿐 "지금 당장 새로 만들어라"가 아니라는 것을 확인했다 — 오염 표시와 실제 제거 사이에 시차가 있고, 그 시차 동안에는 오염된 바로 그 컨텍스트가 계속 쓰인다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `DefaultCacheAwareContextLoaderDelegate`의 실제 소스: `static final ContextCache defaultContextCache = new DefaultContextCache();`가 클래스 로딩 시점에 딱 한 번 만들어지고, 무인자 생성자가 이 정적 인스턴스를 그대로 쓴다는 것을 확인했다 — 7번 절에서 겪은 "격리 불가능" 문제의 직접적인 근거이자, 8번 절 첫 번째 행("서로 다른 테스트 클래스인데도 하나로 합쳐짐")이 왜 가능한지의 근거다.
- `DefaultCacheAwareContextLoaderDelegate#loadContext`의 실제 소스: `synchronized (this.contextCache) { context = this.contextCache.get(mergedConfig); if (context == null) { ...생성 후 put... } }`이라는 전형적인 "조회 후 없으면 생성해서 저장"(get-or-create) 패턴을, `synchronized` 블록 하나로 원자적으로 감싸고 있다는 것을 확인했다 — 25번 문서의 `sync = true`(`ConcurrentHashMap#computeIfAbsent`)와 목적은 같지만(같은 키에 대한 중복 생성 방지), 구현 방법은 다르다(락 vs `computeIfAbsent`)는 비교 지점이다.
- `DefaultContextCache`의 실제 소스: 내부 저장소가 `LinkedHashMap`을 상속한 `LruCache`이고, `removeEldestEntry()`를 오버라이드해서 최대 크기를 넘으면 가장 오래 안 쓴 항목을 제거한다는 것을 확인했다 — 이번 실험은 이 LRU 축출 자체는 재현하지 않았다(기본 최대 크기가 32라서, 이 실험의 캐시 엔트리 3~4개로는 절대 안 넘친다). 대신 `@DirtiesContext`에 의한 **명시적** 제거만 재현했다.
- `AbstractDirtiesContextTestExecutionListener#dirtyContext`의 실제 소스: `testContext.markApplicationContextDirty(hierarchyMode)`만 호출한다 - 컨텍스트를 직접 닫지 않는다. 실제로 캐시에서 제거하고 `close()`하는 동작은 `TestContext` 구현체(`DefaultTestContext`) 쪽, 그리고 그 마킹을 "언제" 호출하느냐(메서드 전/후 vs 클래스 전/후)는 `DirtiesContextBeforeModesTestExecutionListener`/`DirtiesContextTestExecutionListener`가 `@DirtiesContext`의 `classMode`/`methodMode` 속성을 읽어 결정한다는 것을 확인했다 — "언제 더러워졌다고 표시할지"와 "실제로 치우는 것"이 서로 다른 리스너·클래스로 분리돼 있다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `ContextCache`의 핵심 자체("키로 조회하고 없으면 만들어서 저장한다")는 `mini-spring/mini-container`가 4주차에 이미 `DefaultSingletonBeanRegistry` 축소 구현으로 다룬 것과 본질적으로 같은 아이디어(get-or-create + 캐시)라서, 뼈대를 다시 만드는 것은 새로운 학습이 되지 않는다. 이번 주의 가치는 "그 아이디어가 빈 하나가 아니라 테스트 클래스 전체의 `ApplicationContext`로 확장됐을 때 생기는 구체적인 규칙"(캐시 키의 구성 요소, `@DirtiesContext`의 시차, 같은 클래스 내 상태 공유)에 있었고, 그건 mini 구현보다 실제 Spring을 JUnit Platform Launcher로 직접 구동해서 확인하는 쪽이 훨씬 직접적이었다.

## 11. Spring 설계 의도

- **왜 `ContextCache`는 `TestContextManager`가 아니라 JVM 전체에서 공유되는 정적 저장소인가**: 목적이 "테스트 스위트 전체의 실행 속도"이기 때문이다(2번 절). `TestContextManager`는 테스트 클래스 하나에 대응하는 일회성 조정자라서, 캐시가 거기 묶여 있었다면 클래스가 바뀔 때마다 캐시가 무의미해진다 - 정확히 이 저장소처럼 같은 설정을 여러 실험 모듈이 반복해서 쓰는 상황에서 캐시가 의미를 가지려면, 캐시의 생명주기가 개별 테스트 클래스보다 길어야 한다. 대가는 이번 실험이 직접 겪은 것(7번 절) - 전역 공유 상태라서 테스트끼리 서로 격리하기가 오히려 어려워진다는 것이다.
- **왜 `@DirtiesContext`는 즉시 닫지 않고 "마킹"만 하는가(9번 절)**: 클래스 레벨 기본 모드(`AFTER_CLASS`)에서, 이 클래스의 테스트들은 여전히 "오염되기 전의" 정상 상태인 캐시된 컨텍스트로 실행돼야 한다 - 오염은 이 클래스가 **자신의 테스트를 실행한 결과로 다음 사용자에게 전가하는 부작용**이지, 이 클래스 자신에게 지금 영향을 주는 게 아니다. 그래서 "마킹"(다음에 이 캐시 엔트리를 볼 사람은 새로 만들어야 한다는 표시)과 "실제 제거"(그 다음 캐시 조회 시점)를 분리해 둔 것은, `@DirtiesContext`가 "내가 방금 어지럽혔다"를 표현하는 것이지 "지금 당장 치워라"를 표현하는 게 아니라는 의도를 정확히 반영한다.
- **왜 같은 클래스 안의 싱글턴 빈 상태는 테스트 사이에 자동으로 리셋되지 않는가**: 컨텍스트 캐싱의 성능 이점 자체가 "컨테이너를 다시 만들지 않는다"는 데서 나온다 - 만약 매 `@Test` 메서드마다 싱글턴 빈들을 자동으로 리셋해 준다면, 그건 사실상 컨텍스트를 다시 만드는 것과 비용이 비슷해지거나(모든 싱글턴을 새로 만들어야 하므로), 아니면 "리셋 가능한 상태"를 프레임워크가 추적해야 하는 훨씬 복잡한 문제가 된다. 대신 Spring은 "캐싱의 이점을 온전히 누리려면 상태 관리는 테스트 작성자의 책임"이라는 명확한 경계를 긋는다 - `@DirtiesContext`(컨텍스트째 버림), `@Transactional` 테스트의 자동 롤백, 또는 테스트 자신이 `@BeforeEach`에서 상태를 초기화하는 것 중 하나를 사용자가 선택해야 한다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: "서로 다른 테스트 클래스는 당연히 서로 다른 컨텍스트를 가질 것"이라는 직관이 완전히 틀렸다 - 캐시가 클래스 경계가 아니라 **설정의 논리적 동일성**(`MergedContextConfiguration`) 경계로 나뉜다는 것을, 카운터 하나로 명확하게 재현했다.
- 예상 밖이었던 것: `@DirtiesContext`가 "즉시 치우기"가 아니라 "오염 마킹 후 지연 제거"라는 2단계 구조였다는 것 - 오염시킨 클래스 자신은 여전히 정상적으로 캐시를 재사용한다.
- 예상대로였던 것(재확인): 컨텍스트가 재사용되면 그 안의 싱글턴 상태도 같이 재사용된다는 것 - "싱글턴은 컨테이너에 묶인 객체"라는 4주차의 기본 원칙이, 이번엔 "컨테이너 자체가 테스트 사이에서 재사용될 수 있다"는 새로운 층위에서 다시 확인됐다.
- 새로 배운 것: 25번(캐시 추상화)의 `sync=true`가 `ConcurrentHashMap#computeIfAbsent`로 원자성을 얻었던 것과, 이번 `ContextCache`가 `synchronized` 블록으로 원자성을 얻는 것이 같은 문제("같은 키로 동시에 들어온 요청이 중복 생성을 하지 않게 한다")에 대한 서로 다른 해법이라는 것 - 두 메커니즘 다 "get-or-create를 원자적으로 만든다"는 같은 목표를 갖고 있지만, 코드베이스마다 그걸 구현하는 도구는 다를 수 있다는 걸 나란히 비교하고서야 명확해졌다.
- **이번 주제는 25·26번과 달리 AOP 프록시가 아니라 테스트 생명주기 콜백 체인이라는 다른 확장점을 골랐는데도, "키로 무언가를 조회하고 없으면 만들어서 저장한다"는 이 저장소가 반복해서 봐 온 get-or-create 패턴(4주차 싱글턴 레지스트리, 25번 캐시 추상화)이 세 번째 층위(테스트 컨텍스트)에서도 그대로 나타났다.** 확장점의 형태는 매번 다르지만, 그 밑에 깔린 아이디어는 놀랍도록 자주 반복된다는 것이 이 저장소 전체를 관통하는 결론이다.
