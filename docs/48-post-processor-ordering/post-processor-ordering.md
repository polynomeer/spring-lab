# BeanPostProcessor 등록 순서 — order 값보다 "어느 그룹인가"가 먼저 결정된다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`47`](../47-configuration-condition-phase/configuration-condition-phase.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`06`](../06-beanpostprocessor/beanpostprocessor.md)번 문서는 여러 `BeanPostProcessor`의 실행 순서가 "`PriorityOrdered` → `Ordered` → 나머지" 순이라는 걸 이미 요약해 뒀지만, 그건 어디까지나 요약이었지 실제로 재현해서 확인한 적은 없었다. `@Order` 애너테이션의 숫자 값 하나가 전체 순서를 정한다고 흔히 오해하기 쉬운 지점이라, 이번엔 그 오해가 실제로 어떻게 깨지는지 직접 실행으로 확인한다.

## 1. 이번 질문

- 여러 `BeanPostProcessor`가 있을 때, 최종 실행 순서는 "모든 BPP를 order 값 하나로 통째 정렬한 결과"인가, 아니면 다른 원리로 정해지는가?
- `PriorityOrdered`를 구현한 BPP와 `Ordered`만 구현한 BPP가 섞여 있으면, 각 BPP의 order 값끼리 직접 경쟁하는가, 아니면 인터페이스 종류 자체가 먼저 승부를 가르는가?
- `Ordered`/`PriorityOrdered` 어느 쪽도 구현하지 않고 `@Order` 애너테이션만 붙인 BPP는 그 값이 실제로 반영되는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Custom BeanPostProcessor Registration Order")는 `BeanPostProcessor`가 `Ordered`(또는 `PriorityOrdered`)를 구현하면 순서를 제어할 수 있다고 짧게 언급하지만, "몇 개의 그룹으로 나뉘는지", "그룹과 order 값 중 무엇이 우선인지"는 API 문서 수준에서 다루지 않는다.
- `PriorityOrdered`의 Javadoc은 "일반 `Ordered` 빈들보다 먼저 처리돼야 하는 우선순위 빈"이라고 설명하지만, 이게 "order 값이 아무리 극단적이어도 그룹 경계를 넘지 못한다"는 뜻까지 명시적으로 말하지는 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `PriorityOrdered` 구현체와 `Ordered` 구현체를 섞어 두면, 결국 둘 다 `getOrder()`가 있으니 그 값끼리 비교해서 정렬될 거라 예상했다 — **틀렸다.** `registerBeanPostProcessors()`는 등록 대상을 `PriorityOrdered` 구현 여부/`Ordered` 구현 여부/그 외로 **먼저 세 그룹으로 분리**하고, 각 그룹을 통째로 순서대로 등록한다(`PriorityOrdered` 그룹 전체 → `Ordered` 그룹 전체 → 나머지 그룹 전체). `getOrder()` 값은 **같은 그룹 안에서만** 정렬에 쓰인다 - `PriorityOrdered`이면서 order 값이 `Integer.MAX_VALUE`인 BPP조차, `Ordered`만 구현하고 order 값이 `Integer.MIN_VALUE`인 BPP보다 항상 먼저 실행된다.
- `@Order` 애너테이션만 붙이고 `Ordered`/`PriorityOrdered` 어느 쪽도 구현하지 않으면, 그래도 애너테이션 값을 읽어서 어느 정도는 순서에 반영해 줄 거라 예상했다 — **틀렸다.** 버킷 분류 자체가 `beanFactory.isTypeMatch(ppName, PriorityOrdered.class)`/`Ordered.class`, 즉 순수하게 "인터페이스를 구현했는가"만 검사한다. `@Order` 애너테이션만 있는 BPP는 "나머지" 버킷으로 떨어지고, 그 버킷은 **정렬 호출 자체가 없어서**(소스에서 직접 확인) 애너테이션 값과 완전히 무관하게 `@Bean` 메서드가 등록된 순서 그대로 실행된다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/post-processor-ordering-lab`](../../experiments/post-processor-ordering-lab)

```java
public class PriorityOrderedBpp implements BeanPostProcessor, PriorityOrdered {
    // getOrder()가 있어도 이 값은 "PriorityOrdered 버킷 안에서만" 비교된다
}

public class OrderedBpp implements BeanPostProcessor, Ordered {
    // 같은 getOrder() 계약이지만 소속 버킷 자체가 다르다
}

@Order(Integer.MIN_VALUE)   // 숫자로는 가장 앞선 우선순위처럼 보이지만
public class PlainOrderAnnotatedFirstBpp implements BeanPostProcessor {
    // Ordered/PriorityOrdered 미구현 -> "나머지" 버킷 -> 이 값은 아예 안 읽힌다
}
```

```java
@Configuration
public class RecordingConfig {
    // 선언 순서를 최종 실행 순서와 일부러 어긋나게 배치
    @Bean PlainOrderAnnotatedSecondBpp plainSecond(List<String> log) { ... }   // @Order(MAX_VALUE)
    @Bean OrderedBpp orderedLow(List<String> log) { ... }                      // order=MIN_VALUE
    @Bean PriorityOrderedBpp priorityOrderedHigh(List<String> log) { ... }     // order=MAX_VALUE
    @Bean PlainOrderAnnotatedFirstBpp plainFirst(List<String> log) { ... }     // @Order(MIN_VALUE)
    @Bean PriorityOrderedBpp priorityOrderedLow(List<String> log) { ... }      // order=MIN_VALUE
}
```

```java
// 실제 실행 순서 (Target 빈에 대한 postProcessBeforeInitialization 호출 순서)
"priorityOrdered(order=MIN_VALUE)",   // PriorityOrdered 버킷, 버킷 안 정렬로 먼저
"priorityOrdered(order=MAX_VALUE)",   // PriorityOrdered 버킷, order=MAX_VALUE라도 Ordered 버킷보다 먼저
"ordered(order=MIN_VALUE)",           // Ordered 버킷 - 숫자로는 가장 앞서지만 그룹 경계를 못 넘음
"plain(@Order=MAX_VALUE, 두 번째)",   // "나머지" 버킷 - 애너테이션 무시, plainSecond가 먼저 선언됐을 뿐
"plain(@Order=MIN_VALUE, 첫 번째)"    // 애너테이션 값(MIN_VALUE)이 무색하게 마지막
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `PostProcessorRegistrationDelegate#registerBeanPostProcessors` | `refresh()` 중 모든 `BeanPostProcessor` 빈을 조회해, `PriorityOrdered`/`Ordered`/나머지 세 리스트(+ 내부용 `MergedBeanDefinitionPostProcessor` 리스트)로 분리한 뒤 그룹 순서대로 컨테이너에 등록 |
| `PriorityOrdered` | `Ordered`를 확장만 할 뿐 추가 메서드가 없는 마커 인터페이스 - 순전히 "어느 그룹에 속하는가"를 가르는 타입 신호로만 쓰인다 |
| `AnnotationAwareOrderComparator` (`sortPostProcessors`가 내부적으로 사용) | 같은 그룹 안에서만 `getOrder()`/`@Order` 값을 비교해 정렬 |
| `ConfigurableListableBeanFactory#isTypeMatch` | 버킷 분류의 유일한 기준 - 빈을 실제로 인스턴스화하지 않고도 "이 이름의 빈이 이 인터페이스를 구현하는가"를 판정 |

## 6. 호출 흐름

```text
[AbstractApplicationContext#refresh() -> registerBeanPostProcessors()]
postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class)
각 이름을 순회하며 세 그룹으로 분류(인스턴스화 없이 isTypeMatch로 판정):
  - isTypeMatch(name, PriorityOrdered.class) → priorityOrderedPostProcessors
  - isTypeMatch(name, Ordered.class)         → orderedPostProcessorNames
  - 그 외                                     → nonOrderedPostProcessorNames

1) sortPostProcessors(priorityOrderedPostProcessors)   ← 그룹 "안에서만" order 값으로 정렬
   registerBeanPostProcessors(그룹 1 전체)               ← 그룹 전체를 한 번에 등록

2) orderedPostProcessorNames의 빈들을 이제야 getBean()으로 인스턴스화
   sortPostProcessors(orderedPostProcessors)             ← 역시 그룹 "안에서만" 정렬
   registerBeanPostProcessors(그룹 2 전체)

3) nonOrderedPostProcessorNames의 빈들을 getBean()으로 인스턴스화
   (sortPostProcessors 호출이 없음!)                     ← @Order 애너테이션이 있어도 무시
   registerBeanPostProcessors(그룹 3 전체 - 조회 순서 그대로)

4) internalPostProcessors(MergedBeanDefinitionPostProcessor)만 재정렬 후 맨 끝에 재등록
```

세 그룹의 경계가 order 값보다 항상 우선한다는 것과, "나머지" 그룹이 왜 애너테이션 값을 무시하는지를 함께 그린 다이어그램: [`diagrams/post-processor-ordering-flow.md`](diagrams/post-processor-ordering-flow.md)

## 7. 브레이크포인트

이번 주제도 25~47번과 같은 이유로 `tools/jdi-tracer`를 통한 별도 추적은 하지 않았다 - 8번 절의 실행 결과(정확한 순서로 기록된 로그)가 이미 충분히 구체적인 증거였다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 확인했다:

```text
org.springframework.context.support.PostProcessorRegistrationDelegate#registerBeanPostProcessors
org.springframework.context.support.PostProcessorRegistrationDelegate#sortPostProcessors
```

`registerBeanPostProcessors` 메서드 맨 위 주석이 특히 인상적이었다: "여러 루프와 여러 리스트를 쓰는 이 구조는 리팩터링해서 단순화할 수 있어 보이지만 의도적인 것이다 - `PriorityOrdered`/`Ordered` 계약을 지키려면 프로세서들이 잘못된 순서로 인스턴스화되거나 등록되지 않게 해야 한다"는 경고와 함께, 이 메서드를 변경하는 PR이 여러 번 반려된 이력을 참고하라는 링크까지 남겨 뒀다 - 이 구조가 우연이 아니라 신중하게 지켜지고 있는 계약이라는 것을 소스 자체가 증언한다.

## 8. 런타임 관찰

[`PostProcessorOrderingTest`](../../experiments/post-processor-ordering-lab/src/test/java/lab/experiments/ppordering/PostProcessorOrderingTest.java) (1개, 5단계 시퀀스를 한 번에 검증):

| 등록된 BPP(선언 순서) | 그룹 | order 값 | 실제 실행 순서 |
| --- | --- | --- | --- |
| plainSecond | 나머지 | `@Order(MAX_VALUE)`(무시됨) | 4번째 |
| orderedLow | `Ordered` | `MIN_VALUE` | 3번째 |
| priorityOrderedHigh | `PriorityOrdered` | `MAX_VALUE` | 2번째 |
| plainFirst | 나머지 | `@Order(MIN_VALUE)`(무시됨) | 5번째(맨 마지막) |
| priorityOrderedLow | `PriorityOrdered` | `MIN_VALUE` | 1번째 |

**직접 겪은 것**: 스파이크 테스트를 돌리자 예상했던 순서가 한 치의 오차 없이 그대로 재현됐다 - 특히 "나머지" 그룹의 두 BPP가 `@Order` 값(각각 `MIN_VALUE`, `MAX_VALUE`로 정반대)과 무관하게 순전히 `@Bean` 메서드 선언 순서(plainSecond가 plainFirst보다 먼저 선언됨)대로 실행된 것이 가장 명확한 증거였다. 부수적으로, 콘솔에 `BeanPostProcessorChecker`의 경고 로그("Bean 'recordingConfig' ... is not eligible for getting processed by all BeanPostProcessors ... consider declaring it as static instead")가 함께 찍혔다 - `priorityOrderedHigh` 같은 BPP를 인스턴스화하려고 `orderLog` 빈(공유 리스트)을 먼저 만드는 과정에서, 그 `orderLog`를 만드는 `@Configuration` 클래스(`RecordingConfig`) 자신의 CGLIB 프록시 빈이 아직 등록되지 않은 다른 BPP들에게는 적용될 기회를 놓친다는, 이 그룹별 등록 순서의 또 다른 부작용을 실행 로그가 스스로 알려 준 셈이다 - 공식 문서가 `@Bean` 팩토리 메서드를 `static`으로 선언하라고 권고하는 이유 중 하나를 실행 경고로 직접 확인했다.

## 9. 공식 테스트 분석

이번 주제는 별도의 공식 유닛 테스트를 찾아 인용하는 대신, `PostProcessorRegistrationDelegate#registerBeanPostProcessors` 메서드 자체의 소스 코드 구조(4개의 분리된 리스트, 그룹별로 분리된 `sortPostProcessors`/`registerBeanPostProcessors` 호출 쌍, "나머지" 그룹만 정렬 호출이 빠져 있는 것)와 메서드 맨 위의 "이 구조를 리팩터링하려 하지 말라"는 주석이 이미 충분히 구체적인 명세 역할을 했다 - 46번(제네릭 의존성) 문서가 예외 메시지 하나로, 47번(`ConfigurationCondition`) 문서가 런타임 예외 하나로 설계 의도를 증명했던 것과 같은 정신이다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `mini-container`가 이미 다루는 `BeanPostProcessor` 체인 자체(6주차)에, 이번 주에 확인한 "그룹으로 먼저 나누고 그룹 안에서만 정렬한다"는 규칙을 개념적으로 얹어 이해하는 것으로 충분했다 - 4개의 리스트를 관리하는 로직 자체는 알고리즘적으로 새로울 게 없고, 이번 주의 가치는 구현 난이도가 아니라 "널리 오해되는 지점을 정확히 재현해서 확인하는 것"에 있었다.

## 11. Spring 설계 의도

- **왜 order 값 하나로 전체를 정렬하지 않고 그룹부터 나누는가**: `PriorityOrdered`는 문자 그대로 "다른 `Ordered` 빈들보다 반드시 먼저 처리돼야 하는" 빈을 위한 계약이다. 만약 전체를 order 값 하나로만 정렬한다면, 평범한 `Ordered` 빈이 극단적으로 작은 order 값(`Integer.MIN_VALUE`)을 선언하는 것만으로 `PriorityOrdered` 빈보다 먼저 실행될 수 있게 되어 그 "반드시 먼저"라는 보장 자체가 무너진다. 그룹을 먼저 나누는 것은, 이 저장소가 반복해서 확인해 온 "타입/인터페이스 기반 계약이 숫자 값보다 우선한다"는 원칙(32번 `FactoryBean`의 선언된 타입, 46번의 `@Bean` 선언된 반환 타입)이 정렬 순서에도 그대로 적용된 것이다.
- **왜 `@Order` 애너테이션만으로는 BPP 순서에 반영되지 않는가**: `registerBeanPostProcessors()`는 그룹을 나누기 위해 아직 실제 빈을 만들지 않은 채로(`getBeanNamesForType` + `isTypeMatch`) 판정해야 한다. `Ordered`/`PriorityOrdered` 구현 여부는 빈 클래스의 타입 정보만으로(리플렉션) 판정 가능하지만, 인터페이스가 아닌 애너테이션 기반 순서 판정까지 이 단계에서 지원하면 검사 비용과 복잡도가 늘어난다 - 그리고 애초에 `Ordered` 인터페이스가 이미 "런타임에 순서를 계산해서 반환할 수도 있는" 유연한 계약(`getOrder()`가 메서드이지 상수가 아님)을 제공하므로, BPP처럼 초기 부트스트랩 단계에서 순서가 중요한 특수한 빈들에게는 애너테이션이 아니라 명시적 인터페이스 구현을 요구하는 쪽을 택한 것으로 보인다.
- **왜 "나머지" 그룹은 아예 정렬하지 않는가**: 정렬 자체가 무의미하다는 뜻이 아니라, 순서를 신경 쓰고 싶다면 `Ordered`(또는 `PriorityOrdered`)를 구현하라는 명시적 요구다. 정렬 기준(order 값)이 없는 빈들에게 어떤 기본 정렬(예: 클래스 이름순)을 적용하면, 개발자가 의도하지 않은 암묵적 순서가 생기고 그 순서가 Spring 버전이나 클래스패스 스캔 방식에 따라 바뀔 수 있다 - 차라리 "정렬 기준이 없으면 등록된 순서를 그대로 보존한다"는 예측 가능한 규칙을 택해서, 순서가 필요한 사람은 반드시 명시적으로 선언하게 만드는 것이다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `@Order` 애너테이션이 "완전히 무시된다"는 것 - 어느 정도는(가중치를 낮게라도) 반영될 거라 예상했는데, 실제로는 버킷 분류에서도 버킷 내부 정렬에서도 전혀 등장하지 않았다. `isTypeMatch`라는 한 줄이 애너테이션 기반 순서 제어의 가능성을 원천적으로 차단하고 있었다.
- 예상 밖이었던 것: `PriorityOrdered`이면서 `order=Integer.MAX_VALUE`(그 인터페이스 안에서는 사실상 최하위 우선순위)인 BPP조차, `Ordered`만 구현하고 `order=Integer.MIN_VALUE`(숫자로는 가장 높은 우선순위)인 BPP보다 항상 먼저 실행된다는 것 - "숫자가 곧 순서"라는 직관이 그룹 경계 앞에서 완전히 무력화되는 걸 실행 결과로 직접 보고 나서야, `PriorityOrdered`라는 이름이 왜 "더 높은 order 값"이 아니라 별도의 마커 인터페이스로 설계됐는지 이해가 됐다.
- 새로 배운 것: `BeanPostProcessorChecker`의 경고 로그가, 그룹별 순차 등록이라는 이번 주의 핵심 메커니즘의 부작용(초기 그룹의 BPP를 만들다가 그 팩토리 클래스 자신이 조기에 인스턴스화되면서 아직 등록 안 된 나머지 BPP들의 적용 대상에서 빠지는 것)까지 실행만으로 스스로 드러내 줬다는 것 - 소스를 먼저 읽지 않고도, 왜 공식 문서가 `@Bean` 팩토리 메서드를 `static`으로 선언하라고 권고하는지를 실행 로그 하나로 체감할 수 있었다.
