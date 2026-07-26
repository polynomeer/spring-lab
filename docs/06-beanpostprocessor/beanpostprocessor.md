# BeanPostProcessor — 흩어져 있던 답을 모으고, 1주차부터 남겨 둔 질문 닫기

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 6주차에 대응하는 분석 문서다. 카탈로그(`docs/plan/02-project-catalog.md`)는 `BeanFactoryPostProcessor`와 `BeanPostProcessor`를 "5. BeanFactoryPostProcessor와 BeanPostProcessor" 하나로 묶어 두었고, 그 실습(project 8·9)은 이미 [`05-beanfactory-postprocessor.md`](../05-beanfactory-postprocessor/beanfactory-postprocessor.md)에서 다뤘다. 그래서 이 문서는 새 실험 코드보다 **지금까지 여러 주에 걸쳐 나온 답을 로드맵의 4가지 질문에 맞춰 모으고, 그 과정에서 발견한 진짜 빈틈 하나(`getEarlyBeanReference`)를 소스로 닫는 것**에 집중한다.

## 1. 이번 질문

로드맵이 제시한 4가지 핵심 질문:
1. `BeanPostProcessor`는 빈 생명주기의 어느 위치에서 실행되는가?
2. 프록시는 어느 후처리기에서 생성되는가?
3. 인스턴스 생성 전후 후처리는 어떻게 다른가?
4. 하나의 빈에 여러 후처리기가 적용되면 순서는 어떻게 정해지는가?

1~3번은 4·5주차 문서에서 이미 구체적으로 답했다(2번 절에서 정리). 4번도 대부분 답했지만 **딱 하나, 순환 참조와 AOP 프록시가 겹치는 경우**는 1주차 문서([`bean-factory-getbean.md`](../01-ioc-container/bean-factory-getbean.md) 8번)에서 "프록시가 필요한 빈은 원본을 조기 노출하면 왜 문제가 되는가?"라는 질문으로 남겨 두고 그냥 지나갔다. 이 문서의 새 내용은 그 질문 하나를 닫는 것이다.

## 2. 지금까지의 답 — 문서 간 상호 참조

| 로드맵 질문 | 답이 있는 곳 | 핵심 요지 |
| --- | --- | --- |
| 생명주기의 어느 위치인가 | [`04-bean-lifecycle.md`](../04-bean-lifecycle/bean-lifecycle.md) 6번 | `invokeAwareMethods` → `postProcessBeforeInitialization` → 초기화 콜백(`@PostConstruct`/`afterPropertiesSet`/custom init) → `postProcessAfterInitialization` |
| 프록시는 어디서 생성되는가 | [`05-beanfactory-postprocessor.md`](../05-beanfactory-postprocessor/beanfactory-postprocessor.md) 13번 | `AbstractAutoProxyCreator.postProcessAfterInitialization()` → `wrapIfNecessary()`. 우리 `MethodTimingBeanPostProcessor`(project 9)와 `mini-container`의 프록시 교체 실험이 같은 구조 |
| 인스턴스 생성 전후 후처리의 차이 | [`04-bean-lifecycle.md`](../04-bean-lifecycle/bean-lifecycle.md) 6·8번 | `InstantiationAwareBeanPostProcessor`는 `createBeanInstance` 전후(생성자 호출 자체를 가로챌 수 있음), 평범한 `BeanPostProcessor`는 `initializeBean` 전후(이미 만들어진 인스턴스만 다룸) |
| 여러 후처리기의 순서 | [`04-bean-lifecycle.md`](../04-bean-lifecycle/bean-lifecycle.md) 9번 | `PriorityOrdered` → `Ordered` → 나머지, 그리고 `MergedBeanDefinitionPostProcessor`는 우선순위와 무관하게 맨 끝으로 재등록됨(`@PostConstruct`가 우리 커스텀 BPP보다 늦게 실행된 이유) |

이 표 자체가 로드맵이 요구한 "`BeanFactoryPostProcessor`와 `BeanPostProcessor` 비교 문서"의 요약이다. 축소 구현("후처리기 체인")은 project 7([`mini-container`](../../mini-spring/mini-container))에서, 빈 생명주기 파이프라인 다이어그램은 [`04-bean-lifecycle/diagrams/lifecycle-sequence.md`](../04-bean-lifecycle/diagrams/lifecycle-sequence.md)에서 이미 만들었다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 순환 참조 상황에서 "조기 참조"는 그냥 아직 다 안 만들어진 원본 객체를 그대로 내주는 것이라고 생각했다.
- `AbstractAutoProxyCreator`가 프록시를 만드는 시점은 `postProcessAfterInitialization`(5주차에서 확인)뿐이라고 생각했다 — 즉 순환 참조 중에 조기 노출되는 시점에는 아직 프록시가 없고, 원본이 노출될 수밖에 없을 거라 예상했다.
- 이 경우 "나중에 프록시로 바뀌면 조기 참조를 들고 있던 다른 빈은 계속 원본을 참조하게 되는 문제가 생기겠다"고 예상했다 — 그런데 Spring이 이걸 어떻게 막는지는 전혀 몰랐다.

## 4. 최소 재현 코드

이번 절은 새 실행 코드 대신 소스 인용으로 대체한다 — 실제 순환 참조 + AOP 프록시 조합의 런타임 재현은 9~10주차(순환 참조, project 16 Circular Dependency Laboratory)에서 제대로 다룰 계획이라 지금 서두르지 않는다. 대신 관련 있는 세 지점을 순서대로 인용한다.

**① 조기 노출 등록** — `AbstractAutowireCapableBeanFactory#doCreateBean` (1·4주차에서 이미 본 지점)
```java
if (earlySingletonExposure) {
    addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
}
```

**② 조기 참조를 만드는 실제 로직** — 같은 클래스의 `getEarlyBeanReference(String, RootBeanDefinition, Object)`
```java
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
            exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
        }
    }
    return exposedObject;
}
```

**③ `AbstractAutoProxyCreator`의 구현** — `spring-aop`
```java
public Object getEarlyBeanReference(Object bean, String beanName) {
    Object cacheKey = getCacheKey(bean.getClass(), beanName);
    this.earlyBeanReferences.put(cacheKey, bean);
    return wrapIfNecessary(bean, beanName, cacheKey);   // postProcessAfterInitialization과 같은 메서드
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `SmartInstantiationAwareBeanPostProcessor` | `InstantiationAwareBeanPostProcessor`를 확장. `predictBeanType`, `determineCandidateConstructors`, **`getEarlyBeanReference`** 제공 |
| `AbstractAutoProxyCreator` | `SmartInstantiationAwareBeanPostProcessor`이자 `BeanPostProcessor`. `getEarlyBeanReference`와 `postProcessAfterInitialization`이 **같은 `wrapIfNecessary()`를 공유** |
| `earlyBeanReferences` (Map) | `AbstractAutoProxyCreator` 내부 상태. "이 빈은 이미 조기 참조 시점에 프록시로 감쌌다"를 기록해서 이중 래핑을 막음 |

## 6. 호출 흐름

```text
doCreateBean
  → createBeanInstance                          (원본 인스턴스 생성)
  → addSingletonFactory(name, () -> getEarlyBeanReference(...))   ← 등록만 해 둠, 아직 호출 안 됨
  → populateBean / initializeBean 진행 중...
      → (다른 빈이 이 빈을 필요로 해서 getSingleton(name, true) 호출하면)
        singletonFactory.getObject() 최초 실행
          → getEarlyBeanReference(name, mbd, bean)
              → AbstractAutoProxyCreator.getEarlyBeanReference(bean, name)
                  → earlyBeanReferences.put(cacheKey, bean)
                  → wrapIfNecessary(bean, name, cacheKey)   ← 여기서 프록시가 "미리" 만들어짐
              → 이 프록시가 다른 빈에게 조기 참조로 전달됨
  → initializeBean 마지막에 applyBeanPostProcessorsAfterInitialization
      → AbstractAutoProxyCreator.postProcessAfterInitialization(bean, name)
          → earlyBeanReferences.remove(cacheKey) == bean 이면(조기 참조가 이미 만들어졌으면)
              → 그냥 원본 bean을 그대로 반환 (다시 래핑하지 않음 - 이중 프록시 방지)
  → doCreateBean 마지막 (1주차 "히트 11"과 동일한 지점)
      → earlySingletonReference = getSingleton(name, false)
      → 조기 참조가 있었다면 exposedObject를 그 조기 참조(프록시)로 교체
      → 최종적으로 컨테이너에 등록되는 건 조기 참조 때 만든 바로 그 프록시
```

## 7. 브레이크포인트

이번 문서도 소스 확인으로 검증했고 `tools/jdi-tracer`로 직접 추적하지는 않았다. 실제 순환 참조 + AOP 조합을 만들 project 16에서 다음을 추적 대상으로 남긴다.

```text
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#getEarlyBeanReference
org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#getEarlyBeanReference
org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#postProcessAfterInitialization
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean
```

## 8. 런타임 관찰

이번 문서는 새 실행 결과가 없다 — 4·5주차에서 이미 실행하고 테스트로 고정한 결과(BPP 체인 순서, `MergedBeanDefinitionPostProcessor` 재등록, 프록시 교체)를 재사용한다. 유일하게 새로 확인한 것은 **런타임이 아니라 소스 레벨**: `getEarlyBeanReference`와 `postProcessAfterInitialization`이 `earlyBeanReferences` 맵 하나를 공유해서 "이미 조기 래핑했으면 다시 래핑하지 않는다"는 상호 배제를 구현하고 있다는 점이다.

## 9. 공식 테스트 분석 / 프로덕션 코드에서의 실사용

`getEarlyBeanReference`를 직접 이름으로 검증하는 단위 테스트는 찾지 못했다 — 순환 참조와 AOP가 겹치는 시나리오라 통합 테스트 성격이 강해서인 것으로 보인다(정직하게 밝혀 둔다). 대신 이 콜백이 죽은 코드가 아니라 실제로 쓰인다는 증거를 `spring-test` 모듈에서 찾았다.

`spring-test/src/main/java/org/springframework/test/context/bean/override/WrapEarlyBeanPostProcessor.java` — Spring 6.2의 `@MockitoBean`/bean override 테스트 기능이 정확히 이 인터페이스(`SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference`)를 구현해서, 순환 참조 상황에서도 테스트용 대체 빈이 일관되게 노출되도록 만든다. `AbstractAutoProxyCreator`(AOP)뿐 아니라 완전히 다른 목적(테스트 대체)에도 같은 확장 지점이 재사용되고 있다는 뜻이다 — 이 확장 지점이 "AOP 전용"이 아니라 "조기 노출 시점에 최종본과 동등한 무언가를 미리 보여줘야 하는 모든 경우"를 위한 범용 지점이라는 걸 보여준다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

`mini-spring/mini-container`는 `BeanPostProcessor` 체인(project 7)과 프록시 교체 실험(이번 세션에서 추가)까지는 갖췄지만, **조기 참조 자체가 없다**(1주차 문서 10번에서 이미 밝힌 격차). 그래서 이번 주제의 핵심(조기 참조와 최종 참조를 같은 프록시로 일치시키는 메커니즘)은 재현할 대상 자체가 아직 없다. `CircularDependencyException`(project 17)이 재진입을 "해결"이 아니라 "탐지"만 하기로 한 결정(4주차 문서)과 일관된 상태다.

## 11. Spring 설계 의도

- **`getEarlyBeanReference`를 `postProcessAfterInitialization`과 분리하지 않고 굳이 `earlyBeanReferences` 맵으로 연결한 이유**: 둘을 완전히 독립된 콜백으로 두면, 조기 참조 시점에는 프록시 A를, 최종 초기화 시점에는 또 다른 프록시 B를 만들어버릴 위험이 있다. 순환 참조 중이던 다른 빈은 이미 프록시 A를 필드에 저장해 버렸는데 컨테이너는 프록시 B를 "진짜" 싱글턴으로 등록한다면, 두 빈이 서로 다른 프록시를 참조하는 불일치가 생긴다. 같은 캐시 키로 "이미 만들었으면 다시 만들지 않는다"를 보장해야 이 불일치를 막을 수 있다.
- **이 확장 지점을 `BeanPostProcessor`가 아니라 `SmartInstantiationAwareBeanPostProcessor`라는 별도 인터페이스로 둔 이유**: 조기 참조가 필요한 경우(순환 참조 + 프록시 필요)는 흔치 않다. 대부분의 `BeanPostProcessor`는 이 개념 자체가 필요 없으므로, 기본 인터페이스를 무겁게 만들지 않고 필요한 처리기만 `SmartInstantiationAwareBeanPostProcessor`를 구현하도록 분리했다 — `default` 메서드로 원본 반환을 기본값으로 두어, 대부분의 구현체는 이 존재조차 몰라도 되게 만든 것도 같은 맥락이다.

## 12. 결론 (예상과 실제의 차이, 그리고 닫은 질문)

- 1주차부터 남겨 뒀던 질문 — "프록시가 필요한 빈은 원본을 조기 노출하면 왜 문제가 되는가?" — 에 대한 답: **문제가 되지 않는다, Spring이 `getEarlyBeanReference`로 조기 노출 시점에도 최종본과 동등한 프록시를 미리 만들어 두기 때문이다.** 예상했던 "나중에 프록시로 바뀌면 불일치가 생긴다"는 문제는 실제로 존재하지만, `SmartInstantiationAwareBeanPostProcessor`가 정확히 그 문제를 막기 위한 확장 지점이었다.
- 예상과 달랐던 것: 조기 참조와 최종 참조의 일치를 보장하는 로직이 한곳에 있지 않고 **세 곳에 나뉘어 있다** — ①`doCreateBean`의 `addSingletonFactory` 등록, ②`getEarlyBeanReference`의 `wrapIfNecessary` 호출, ③`postProcessAfterInitialization`의 "이미 만들었으면 재래핑 금지" 체크. 세 지점이 `earlyBeanReferences` 맵 하나로 묶여 있다.
- 이번 문서로 6주차까지의 확장 지점(`BeanFactoryPostProcessor`, `BeanPostProcessor`, `InstantiationAwareBeanPostProcessor`, `SmartInstantiationAwareBeanPostProcessor`, `MergedBeanDefinitionPostProcessor`)을 모두 한 번씩은 다뤘다. 다음은 7주차(컴포넌트 스캔)로 넘어가되, 순환 참조 + AOP의 실제 런타임 재현은 9~10주차(project 16)로 미룬 채로 남겨 둔다.
