# SmartInitializingSingleton — "이 빈이 다 만들어졌다"가 아니라 "모든 빈이 다 만들어졌다"는 콜백

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`40`](../40-property-source-ordering/property-source-ordering.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 4주차(빈 생성과 생명주기)는 `@PostConstruct`/`InitializingBean`처럼 **그 빈 자신**이 완성됐음을 알리는 콜백을 다뤘는데, `SmartInitializingSingleton`은 그것과는 다른 질문에 답한다 - "이 빈이 아니라, **이 컨텍스트의 모든 싱글턴**이 다 만들어졌는가"를 알려주는 콜백이다. 두 질문이 왜 다른 답을 요구하는지, 그리고 그 답이 `@Lazy` 빈 앞에서 어떻게 깨지는지를 확인한다.

## 1. 이번 질문

- `SmartInitializingSingleton.afterSingletonsInstantiated()`는 정확히 언제 호출되는가 - 각 빈이 만들어질 때마다인가, 아니면 딱 한 번인가?
- 이 콜백이 호출되는 시점에, 이 빈보다 **나중에 선언된** 다른 싱글턴들도 이미 완전히 만들어져 있다고 믿을 수 있는가?
- 이 콜백은 `ContextRefreshedEvent`(3주차의 `refresh()` 마지막 단계에서 발행)보다 먼저인가, 나중인가?
- `@Lazy`로 등록된 빈이 이 인터페이스를 구현하면 어떻게 되는가?

## 2. 공식 문서 요약

- `SmartInitializingSingleton`의 Javadoc은 "모든 일반(비-lazy) 싱글턴 빈이 미리 인스턴스화된 직후, `BeanFactory` 초기화가 끝나기 전에 호출된다"고 명시하고, `@PostConstruct`/`InitializingBean`과의 차이를 "다른 싱글턴 빈들이 이미 초기화됐다는 것을 안전하게 가정할 수 있다"는 문장으로 설명한다.
- 문서는 "일반적으로 `ApplicationListener`를 써서 같은 효과를 낼 수도 있지만, `SmartInitializingSingleton`은 최소한의 의존성으로 그 효과를 낸다"는 취지의 설명도 남긴다 - `ContextRefreshedEvent` 리스너와의 상대적 시점 차이는 명시하지 않는다.
- `@Lazy` 빈에 대해서는 이 콜백이 어떻게 되는지 문서가 다루지 않는다 - "모든 일반 싱글턴"이라는 표현 안에 `@Lazy` 빈이 포함되는지는 소스를 봐야 알 수 있었다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `afterSingletonsInstantiated()`가 각 빈이 만들어질 때마다(즉 `@PostConstruct`처럼 빈 하나당 한 번씩) 호출될 거라 예상했다 — **틀렸다.** 이 메서드 이름 자체가 복수형(Singletons)인 이유가 있었다 - 컨텍스트 전체에서 **딱 한 번**, 모든 일반 싱글턴이 다 만들어진 뒤에 한꺼번에 호출된다.
- 이 콜백이 호출되는 시점에, 자신보다 **먼저** 선언된 빈들만 완성돼 있을 거라 예상했다(생성이 선언 순서대로 진행되니) — **틀렸다.** 선언 순서와 무관하게 **모든** 일반 싱글턴이 이미 완성돼 있다 - 이 콜백을 위한 별도의 두 번째 순회가 첫 번째 순회(모든 싱글턴 생성) 전체가 끝난 뒤에야 시작되기 때문이다.
- `@Lazy` 빈이라도 나중에 명시적으로 `getBean()`을 부르면, 그 시점에 이 콜백이 뒤늦게라도 호출될 거라 예상했다 — **틀렸다.** 그 특별한 두 번째 순회는 컨텍스트 시작 시점에 딱 한 번만 실행되고 끝난다 - `@Lazy` 빈은 애초에 그 순회의 대상 목록(`preInstantiateSingletons()`가 만든 `beanNames`)에 포함된 적이 없으므로, 나중에 아무리 강제로 생성해도 이 콜백은 영원히 호출되지 않는다.
- `ContextRefreshedEvent`와 이 콜백 중 어느 게 먼저인지는 확신이 없었다 — 3주차에서 배운 `refresh()`의 12단계 순서(11번째 `finishBeanFactoryInitialization` → 12번째 `finishRefresh`)를 떠올리면 이 콜백이 먼저일 거라 추론했고, 실행으로 확인해 맞았다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/smart-initializing-singleton-lab`](../../experiments/smart-initializing-singleton-lab)

```java
@Configuration
public class SmartInitConfig {
    @Bean
    public BeanA beanA(RecordingLifecycleLog log, ApplicationContext context) {
        return new BeanA(log, context);      // beanB보다 먼저 선언됨
    }

    @Bean
    public BeanB beanB(RecordingLifecycleLog log) {
        return new BeanB(log);               // @PostConstruct에서 postConstructDone = true
    }

    @Bean
    @Lazy
    public LazySmartBean lazySmartBean(RecordingLifecycleLog log) {
        return new LazySmartBean(log);
    }
}

public class BeanA implements SmartInitializingSingleton {
    public void afterSingletonsInstantiated() {
        BeanB beanB = context.getBean(BeanB.class);
        log.record("sees BeanB.postConstructDone=" + beanB.isPostConstructDone());   // 항상 true
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `SmartInitializingSingleton` | `afterSingletonsInstantiated()` 메서드 하나 - "모든 일반 싱글턴이 다 만들어졌다"는 신호를 받는 콜백 |
| `DefaultListableBeanFactory#preInstantiateSingletons` | `refresh()`의 11번째 단계(`finishBeanFactoryInitialization`)에서 호출되는, 모든 비-lazy 싱글턴을 실제로 만드는 메서드 - 그 안에 "본 루프"와 "콜백 전용 두 번째 루프"가 순차적으로 들어 있음 |
| `ContextRefreshedEvent` | `refresh()`의 12번째(마지막) 단계에서 발행되는 이벤트 - `SmartInitializingSingleton`보다 항상 뒤 |
| `@Lazy` | 빈을 `preInstantiateSingletons()`의 "본 루프" 대상에서 아예 제외시키는 애노테이션 - 그 결과 콜백 대상 목록에도 포함되지 못함 |

## 6. 호출 흐름

```text
AbstractApplicationContext#refresh()
  → finishBeanFactoryInitialization(beanFactory)         (11번째 단계, 3주차)
      → beanFactory.preInstantiateSingletons()
          → beanNames = 이 BeanFactory에 등록된 모든 BeanDefinition 이름 목록
          → for (String beanName : beanNames) {           ← 본 루프 - "모든" 비-lazy 싱글턴 생성
                if (!isLazy(beanName)) getBean(beanName)    (getBean 내부에서 @PostConstruct 등도 실행)
            }
          → // 본 루프가 완전히 끝난 뒤에만 시작되는 별도 순회
          → for (String beanName : beanNames) {            ← 두 번째 루프
                Object instance = getSingleton(beanName, false)
                if (instance instanceof SmartInitializingSingleton smart) {
                    smart.afterSingletonsInstantiated()     ← 이 시점엔 beanNames의 모든
                                                                비-lazy 싱글턴이 이미 완성돼 있음
                }
            }
  → finishRefresh()                                       (12번째, 마지막 단계)
      → publishEvent(new ContextRefreshedEvent(this))      ← SmartInitializingSingleton보다 항상 뒤

(나중에) context.getBean(LazySmartBean.class)
  → 이 시점에 처음 생성됨 - 하지만 beanNames 목록에 애초에 없었으므로,
    이미 끝난 두 번째 루프의 대상이 될 방법이 없음 → afterSingletonsInstantiated() 영원히 호출 안 됨
```

"본 루프 → 두 번째 루프"의 순차 구조와, `@Lazy` 빈이 그 두 루프 모두에서 빠지는 지점을 함께 그린 다이어그램: [`diagrams/smart-init-timing.md`](diagrams/smart-init-timing.md)

## 7. 브레이크포인트

25~40번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "호출 순서"와 "호출 여부"라는 결과였고, `RecordingLifecycleLog`로 직접 기록하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.factory.SmartInitializingSingleton (인터페이스 Javadoc)
org.springframework.beans.factory.support.DefaultListableBeanFactory#preInstantiateSingletons
org.springframework.context.support.AbstractApplicationContext#finishBeanFactoryInitialization
org.springframework.context.support.AbstractApplicationContext#finishRefresh
```

## 8. 런타임 관찰

[`SmartInitializingSingletonTest`](../../experiments/smart-initializing-singleton-lab/src/test/java/lab/experiments/smartinit/SmartInitializingSingletonTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| `BeanA`(비-lazy, `SmartInitializingSingleton` 구현)의 콜백 호출 여부 | 호출됨 |
| `BeanA`가 `beanB`보다 먼저 선언됐는데도, 콜백 시점에 `BeanB`의 `@PostConstruct` 완료 여부 | 이미 완료됨(`true`) - 선언 순서와 무관 |
| `SmartInitializingSingleton` 콜백 vs `ContextRefreshedEvent` 발행 순서 | 콜백이 항상 먼저 |
| `@Lazy`로 등록된 `LazySmartBean`을 컨텍스트 시작 직후 확인 | 아직 생성조차 안 됨 - 콜백도 당연히 없음 |
| 그 뒤 `context.getBean(LazySmartBean.class)`로 강제 생성 | 생성자는 실행되지만, `afterSingletonsInstantiated()`는 **여전히 호출되지 않음** |

**직접 겪은 것**: 네 번째 실험(마지막 행)을 실행해 보기 전까지는 "그래도 늦게라도 생성되면 컨테이너가 콜백을 호출해 주지 않을까"라는 미련이 남아 있었다 - `getBean()`이 어쨌든 그 빈을 "정상적으로" 만들어 주는 것처럼 느껴졌기 때문이다. 하지만 실제로는 생성자 로그(`LazySmartBean.constructor`)만 남고 콜백 로그는 끝까지 나타나지 않았다 - `preInstantiateSingletons()`의 두 번째 루프가 애초에 "그 시점에 등록돼 있던 빈 이름 목록"이라는 고정된 스냅샷 위에서만 동작하고, 그 이후에 만들어진 빈에 대해서는 아무 후속 조치도 없다는 것을 실행이 먼저 확인해 줬다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `DefaultListableBeanFactory#preInstantiateSingletons`의 실제 소스: 첫 번째 `for (String beanName : beanNames)` 루프(모든 비-lazy 싱글턴을 실제로 생성)가 완전히 끝난 뒤, `// Trigger post-initialization callback for all applicable beans...`라는 주석과 함께 **같은 `beanNames` 리스트**를 다시 순회하며 `getSingleton(beanName, false)`로 이미 만들어진 인스턴스를 가져와 `SmartInitializingSingleton` 여부를 확인한다는 것을 확인했다 - 8번 절 전체 관찰의 정확한 근거다.
- 그 두 번째 루프가 `beanFactory.getBean()`이 아니라 `getSingleton(beanName, false)`(두 번째 인자 `false` = "없으면 만들지 마라")를 쓴다는 것도 확인했다 - 이 시점엔 이미 만들어져 있어야 하는 빈만 대상으로 한다는 뜻이고, `@Lazy` 빈처럼 아직 안 만들어진 빈에 대해서는 이 호출이 그냥 `null`을 돌려주고 지나간다(애초에 `beanNames` 목록 자체에 `@Lazy` 빈이 없으므로 이 경우가 실제로 발생하지도 않지만, 방어적으로 그렇게 짜여 있다).
- `AbstractApplicationContext#finishBeanFactoryInitialization`(11번째 단계, 3주차에서 이미 여러 번 확인한 메서드)이 `beanFactory.preInstantiateSingletons()`를 호출하는 마지막 줄이고, `finishRefresh()`(12번째, 마지막 단계)는 그다음 별도의 `refresh()` 최상위 흐름에서 호출된다는 것을 재확인했다 - `ContextRefreshedEvent`가 `SmartInitializingSingleton` 콜백보다 항상 뒤라는 것의 구조적 근거다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `mini-spring/mini-container`가 이미 다룬 "모든 싱글턴을 미리 만든다"는 골격(4주차) 위에 "그 전체 과정이 끝난 뒤 한 번 더 순회한다"는 루프 하나를 추가하는 것은 새로운 학습이 되지 않는다 - 이번 주의 가치는 그 추가된 루프가 만드는 **타이밍 보장**(모든 비-lazy 싱글턴의 완성)과, 그 보장이 `@Lazy` 앞에서 정확히 어떻게 깨지는지에 있었다.

## 11. Spring 설계 의도

- **왜 `@PostConstruct`/`InitializingBean`으로는 부족한가**: 그 둘은 "이 빈 자신의 의존성이 전부 주입됐다"는 것만 보장한다 - 만약 A가 B에 의존하지 않는데도(생성자/필드 의존성 없이) A의 초기화 로직이 "다른 어떤 빈이든 이미 만들어져 있다"고 가정하고 싶다면, `@PostConstruct` 시점에는 그 가정이 성립하지 않을 수 있다(B가 A보다 나중에 생성될 수도 있으므로). `SmartInitializingSingleton`은 "이 빈의 의존성 그래프"가 아니라 "컨테이너 전체의 완성"이라는, 훨씬 넓은 범위의 보장을 원하는 소수의 경우를 위한 별도 확장점이다.
- **왜 `ApplicationListener<ContextRefreshedEvent>`가 아니라 별도 인터페이스를 두었는가**: `ContextRefreshedEvent`도 "컨텍스트가 다 준비됐다"는 비슷한 신호를 주지만, 그건 이벤트 발행/구독이라는 상대적으로 무거운 메커니즘(21주차에서 다룬 멀티캐스터, 리스너 등록)을 거친다. `SmartInitializingSingleton`은 `BeanFactory`가 이미 갖고 있는 정보(어떤 빈이 `SmartInitializingSingleton`인지)를 갖고 직접 순회하며 호출하는, 이벤트 인프라를 전혀 거치지 않는 훨씬 가벼운 경로다 - "컨텍스트가 완전히 준비된 시점"이라는 같은 개념을, 무거운 범용 메커니즘(이벤트)과 가벼운 전용 메커니즘(직접 콜백) 두 가지로 모두 제공해서 사용자가 필요에 맞게 고를 수 있게 한 것이다.
- **왜 `@Lazy` 빈은 이 콜백에서 조용히 빠지는가**: 이 콜백의 존재 이유 자체가 "일반적인 시작 절차 안에서, 그 시점까지 만들어진 모든 것이 완성됐다는 것을 보장한다"는 것이다 - `@Lazy` 빈은 정의상 "그 일반적인 시작 절차 밖에서, 필요할 때" 만들어지는 것을 사용자가 선택한 빈이다. 만약 `@Lazy` 빈이 나중에 생성될 때마다 이 콜백을 다시 호출해 준다면, "모든 싱글턴이 다 만들어진 뒤 딱 한 번"이라는 이 인터페이스의 핵심 계약 자체가 깨진다(그 빈이 생성되는 시점에 정말로 "모든" 싱글턴이 다시 만들어져 있다는 보장을 새로 확인해야 하기 때문이다) - 차라리 그 계약을 명확히 "시작 시점의 일반 싱글턴에게만 적용된다"고 좁혀 두는 편이, 어설프게 확장해서 계약을 흐리는 것보다 낫다는 판단으로 보인다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Lazy` 빈은 나중에 강제로 생성해도 이 콜백을 영원히 받지 못한다는 것 - "결국 만들어지기만 하면 관련 콜백도 다 실행되겠지"라는 막연한 기대가, "이 콜백은 시작 시점의 특정 순간에만 유효한 일회성 이벤트"라는 사실 앞에서 깨졌다.
- 예상 밖이었던 것: 선언 순서가 이 콜백 시점의 "완성도 보장"과 전혀 무관하다는 것 - `BeanA`가 `beanB`보다 먼저 선언됐다는 사실이, `afterSingletonsInstantiated()`가 호출될 즈음에는 아무 의미가 없어진다. "먼저 선언된 것이 정보적으로 더 이르다"는 직관이, "두 단계로 나뉜 루프 구조" 앞에서 무너졌다.
- 예상대로였던 것(재확인): 이 콜백이 `ContextRefreshedEvent`보다 먼저라는 것 - 3주차에서 배운 `refresh()`의 12단계 순서(11번째 `finishBeanFactoryInitialization` 안에 이 콜백이 있고, `ContextRefreshedEvent`는 그다음 12번째 `finishRefresh`에서 발행)를 그대로 적용하면 자연스럽게 도출되는 결론이었다.
- 새로 배운 것: `preInstantiateSingletons()`라는, 이 저장소가 4주차부터 알고 있던 메서드 하나가 사실은 "모든 싱글턴을 만드는 루프"와 "그 뒤에 콜백만 처리하는 별도 루프"라는 **두 단계**로 이뤄져 있었다는 것 - `SmartInitializingSingleton`을 몰랐다면 이 메서드를 여전히 "싱글턴을 만드는 루프 하나"로만 기억했을 것이다. 지금까지 이 저장소가 여러 번 다뤄 온 메서드에서도, 새로운 질문 하나를 들고 다시 열어 보면 몰랐던 구조가 나올 수 있다는 걸 확인했다.
