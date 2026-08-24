# `${...}` 플레이스홀더 해석 — 같은 문법, 서로 다른 두 개의 시점

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`49`](../49-scoped-proxy/scoped-proxy.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`40`](../40-property-source-ordering/property-source-ordering.md)번 문서는 `Environment`가 여러 `PropertySource`를 어떤 순서로 뒤져서 값 하나를 결정하는지를 다뤘고, [`05`](../05-beanfactory-postprocessor/beanfactory-postprocessor.md)번 문서는 `PropertySourcesPlaceholderConfigurer`가 "제일 먼저 실행돼야 하는 `BeanFactoryPostProcessor`"라는 사실을 스쳐 지나가며 언급했다. 하지만 `${...}`라는 같은 문법이 XML 스타일 `<property value="${x}"/>`(또는 이를 흉내낸 `MutablePropertyValues`)와 `@Value("${x}")` 애너테이션에서 "실제로 언제, 어떻게" 해석되는지는 아직 정면으로 본 적이 없었다 - 이번엔 그 둘이 완전히 다른 메커니즘이라는 것을 직접 재현해서 확인한다.

## 1. 이번 질문

- `<property value="${x}"/>`(또는 `BeanDefinition`의 `PropertyValues`에 직접 넣은 `"${x}"`)와 `@Value("${x}")`는 둘 다 같은 `${...}` 문법을 쓰는데, 실제로 해석되는 시점도 같은가?
- `PropertySourcesPlaceholderConfigurer`는 `BeanFactoryPostProcessor`인데, 정확히 무엇을 "후처리"하는가 - 빈 인스턴스인가, 빈 정의(`BeanDefinition`) 자체인가?
- `@Value`의 `${x}`는 애너테이션 속성(자바 언어 스펙상 컴파일 타임 상수)인데, 이게 "해석된다"는 건 정확히 무엇이 바뀐다는 뜻인가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스는 `PropertySourcesPlaceholderConfigurer`가 `${...}` 플레이스홀더를 `Environment`의 값으로 치환해 주는 `BeanFactoryPostProcessor`라고 설명하고, 대표적인 두 사용처로 XML 빈 정의의 `${...}`와 `@Value`를 나란히 예시로 든다.
- `BeanFactoryPostProcessor` 인터페이스의 Javadoc은 "컨텍스트의 내부 빈 정의(bean definitions)를 읽고 수정할 수 있는 훅"이라고만 설명할 뿐, 이 특정 구현체가 정확히 "어떻게" 수정하는지(빈 정의 트리 전체를 순회하는 방문자 패턴을 쓴다는 것)는 언급하지 않는다.
- 두 사용처가 겉보기엔 같아 보이지만 서로 다른 해석 메커니즘을 탄다는 사실은 API 문서 수준에서 명시적으로 다뤄지지 않는다 - 소스를 직접 봐야 드러나는 지점이다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `PropertySourcesPlaceholderConfigurer`가 `BeanFactoryPostProcessor`이므로, XML 스타일 프로퍼티 값과 `@Value` 애너테이션 모두 "빈이 실제로 만들어질 때" 그때그때 해석될 거라 예상했다 — **틀렸다.** XML 스타일 프로퍼티 값(`BeanDefinition`의 `MutablePropertyValues`)은 `PropertySourcesPlaceholderConfigurer`가 `BeanFactoryPostProcessor`로 실행되는 **단 한 번의 패스** 동안, 컨테이너 안의 **모든** 빈 정의를 순회하며 그 자리에서 즉시 치환된다 - 아직 어떤 빈도 만들어지기 전이다.
- `@Value("${x}")`도 같은 `PropertySourcesPlaceholderConfigurer`가 처리하니 똑같이 그 한 번의 패스에서 처리될 거라 예상했다 — **틀렸다.** 애너테이션 속성은 자바 언어 스펙상 컴파일 타임 상수라 런타임에 값을 바꿔치기할 방법 자체가 없다 - `PropertySourcesPlaceholderConfigurer`는 그 대신 "이 문자열을 해석해 주는 함수" 하나를 빈 팩토리의 `embeddedValueResolvers` 목록에 등록만 해 두고, 실제 해석은 그 빈이 **정말로 만들어지는 순간**(다른 `BeanPostProcessor`가 `@Value` 필드를 채우려고 그 함수를 호출할 때)까지 미뤄진다.
- 그래서 `PropertySourcesPlaceholderConfigurer` 실행 "직후"에 두 값을 각각 들여다보면 서로 다른 상태로 보일 거라 예상했다 - **맞았다.** XML 스타일 값은 이미 해석된 문자열이지만, `@Value`의 애너테이션 리터럴은 여전히 `"${x}"` 그대로다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/placeholder-resolution-lab`](../../experiments/placeholder-resolution-lab)

```java
// XML <property value="${greeting}"/> 스타일을 코드로 흉내낸 것
GenericBeanDefinition widgetBd = new GenericBeanDefinition();
widgetBd.setBeanClass(Widget.class);
widgetBd.getPropertyValues().add("label", "${greeting}");
context.registerBeanDefinition("widget", widgetBd);
```

```java
@Component
public class AnnotatedWidget {
    @Value("${greeting}")   // 애너테이션 속성 - 컴파일 타임 상수
    private String label;
}
```

```java
// PropertySourcesPlaceholderConfigurer보다 먼저 실행되는 PriorityOrdered BFPP
Object raw = beanFactory.getBeanDefinition("widget").getPropertyValues()
        .getPropertyValue("label").getValue();
// → "${greeting}"  (아직 원본 그대로)
```

```java
// PropertySourcesPlaceholderConfigurer보다 나중에 실행되는 plain(순서 미지정) BFPP
Object raw = beanFactory.getBeanDefinition("widget").getPropertyValues()
        .getPropertyValue("label").getValue();
// → "hello"  (BeanDefinition 자체가 이미 mutate됨)

Field field = AnnotatedWidget.class.getDeclaredField("label");
field.getAnnotation(Value.class).value();
// → "${greeting}"  (같은 시점인데도 여전히 리터럴 그대로 - 컨테이너가 아직 이 필드를 안 건드림)
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `PropertySourcesPlaceholderConfigurer` | `Environment`의 `PropertySource`들을 하나의 `PropertyResolver`로 묶어, 두 가지 완전히 다른 방식으로 `${...}`를 해석하게 만드는 진입점 |
| `BeanDefinitionVisitor` | 빈 정의 하나(그리고 그 프로퍼티 값·생성자 인자·부모 이름 등)를 재귀적으로 순회하며 문자열 값을 `StringValueResolver`로 바꿔치기하는 방문자 - "즉시·일괄" 해석 경로 |
| `ConfigurableBeanFactory#addEmbeddedValueResolver` | `StringValueResolver`를 목록에 "등록"만 해 두는 지점 - 즉시 아무것도 해석하지 않는다 |
| `ConfigurableBeanFactory#resolveEmbeddedValue` | 등록된 `embeddedValueResolvers`를 실제로 호출하는 지점 - `@Value` 처리 시(`AutowiredAnnotationBeanPostProcessor` 등)마다, 즉 빈이 만들어질 때마다 새로 호출된다 - "지연·건별" 해석 경로 |
| `PropertyResourceConfigurer` (`PropertySourcesPlaceholderConfigurer`의 상위) | `PriorityOrdered`를 구현하되 기본 `order`가 `Ordered.LOWEST_PRECEDENCE` - 48번(BPP 등록 순서) 문서에서 확인한 그룹/order 규칙이 `BeanFactoryPostProcessor`에도 그대로 적용된다는 걸 보여주는 실제 사례 |

## 6. 호출 흐름

```text
[registerBeanPostProcessors 이전, invokeBeanFactoryPostProcessors 단계]
EarlyInspectorBfpp(PriorityOrdered, HIGHEST_PRECEDENCE) 실행
  → widget의 BeanDefinition.getPropertyValues().getPropertyValue("label")
    → 아직 "${greeting}" 그대로 (아무도 건드리지 않음)

PropertySourcesPlaceholderConfigurer(PriorityOrdered, LOWEST_PRECEDENCE) 실행
  → postProcessBeanFactory()
    → processProperties() → doProcessProperties()
      → BeanDefinitionVisitor(valueResolver) 생성
      → beanFactory.getBeanDefinitionNames() 전체를 순회
          → visitor.visitBeanDefinition(각 BeanDefinition)
            → visitPropertyValues() → resolveValue() → resolveStringValue()
              → "${greeting}" 발견 → Environment에서 "hello" 조회
              → pvs.add("label", "hello")   ← BeanDefinition 자체를 그 자리에서 mutate!
      → beanFactory.resolveAliases(valueResolver)   (별칭도 같은 패스에서 처리)
      → beanFactory.addEmbeddedValueResolver(valueResolver)
          ← 여기서는 아무것도 해석하지 않는다 - 함수를 목록에 "등록"만 해 둔다

LateInspectorBfpp(plain, PriorityOrdered/Ordered 아님) 실행 (48번 규칙상 항상 나중)
  → widget의 BeanDefinition.getPropertyValues().getPropertyValue("label")
    → 이미 "hello" (PropertySourcesPlaceholderConfigurer가 방금 mutate해 둠)
  → AnnotatedWidget.class의 @Value 애너테이션 리터럴 값
    → 여전히 "${greeting}" (mutate할 대상 자체가 없으므로 - 아직 아무 일도 안 일어남)

[BeanFactoryPostProcessor 단계가 모두 끝난 뒤, preInstantiateSingletons()]
AutowiredAnnotationBeanPostProcessor가 AnnotatedWidget을 실제로 만들 때
  → @Value("${greeting}") 필드를 채우려고
    → beanFactory.resolveEmbeddedValue("${greeting}")
      → 아까 등록해 둔 valueResolver를 "지금" 처음 호출 → "hello" 반환
  → 필드에 "hello" 대입
```

BeanDefinition 자체가 mutate되는 즉시·일괄 경로와, 함수만 등록해 뒀다가 빈 생성 시점에 호출되는 지연·건별 경로가 어떻게 갈리는지를 함께 그린 다이어그램: [`diagrams/placeholder-resolution-flow.md`](diagrams/placeholder-resolution-flow.md)

## 7. 브레이크포인트

이번 주제도 25~49번과 같은 이유로 `tools/jdi-tracer`를 통한 별도 추적은 하지 않았다 - 8번 절의 실행 결과(BFPP 실행 순서에 따라 정확히 갈리는 원시 값)가 이미 충분히 구체적인 증거였다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 확인했다:

```text
org.springframework.beans.factory.config.PlaceholderConfigurerSupport#doProcessProperties
org.springframework.beans.factory.config.BeanDefinitionVisitor#visitPropertyValues
org.springframework.beans.factory.config.BeanDefinitionVisitor#resolveValue
org.springframework.beans.factory.config.ConfigurableBeanFactory#addEmbeddedValueResolver
```

`doProcessProperties()`의 마지막 세 줄이 이번 문서의 핵심을 그대로 담고 있었다: `BeanDefinitionVisitor`로 모든 빈 정의를 순회한 다음, `resolveAliases()`(별칭도 같은 즉시 경로), 그리고 마지막 줄 `addEmbeddedValueResolver(valueResolver)`만 유일하게 "지금 뭔가를 해석"하는 게 아니라 "나중을 위해 함수를 등록"하는 별개의 문장이었다.

## 8. 런타임 관찰

[`PlaceholderResolutionTest`](../../experiments/placeholder-resolution-lab/src/test/java/lab/experiments/placeholder/PlaceholderResolutionTest.java) (1개, 5단계를 한 번에 검증):

| 관찰 시점 | 대상 | 값 |
| --- | --- | --- |
| `PropertySourcesPlaceholderConfigurer` 실행 전(`HIGHEST_PRECEDENCE` BFPP) | `widget`의 `BeanDefinition` 프로퍼티 값 | `"${greeting}"` (원본) |
| `PropertySourcesPlaceholderConfigurer` 실행 후(plain BFPP) | `widget`의 `BeanDefinition` 프로퍼티 값 | `"hello"` (이미 mutate됨) |
| 같은 시점(plain BFPP) | `AnnotatedWidget.label` 필드의 `@Value` 애너테이션 리터럴 | `"${greeting}"` (여전히 원본) |
| `refresh()` 완료 후 | `Widget` 빈의 `label` | `"hello"` |
| `refresh()` 완료 후 | `AnnotatedWidget` 빈의 `label` | `"hello"` |

**직접 겪은 것**: 처음엔 두 검증용 `BeanFactoryPostProcessor`를 "먼저"/"나중"으로 순서를 강제하려고 각각 다른 `order` 값을 임의로 골랐는데, 48번 문서에서 이미 확인해 둔 "그룹이 값보다 우선한다"는 규칙을 그대로 재사용할 수 있다는 걸 깨닫고 나서 훨씬 간단해졌다 - `PropertySourcesPlaceholderConfigurer`가 `PriorityOrdered`이면서 기본값이 `LOWEST_PRECEDENCE`(그 그룹 안에서 사실상 꼴찌)라는 걸 알고 있었으므로, "먼저" 검증기는 `PriorityOrdered` + `HIGHEST_PRECEDENCE`로, "나중" 검증기는 아예 `Ordered`를 구현하지 않은 plain BFPP로 만드는 것만으로 순서가 정확히 보장됐다 - 이전 주제에서 확인한 메커니즘이 이번 주제의 실험 설계 자체를 더 쉽게 만들어 준 첫 사례였다.

## 9. 공식 테스트 분석

이번 주제는 별도의 공식 유닛 테스트를 찾아 인용하는 대신, `doProcessProperties()` 메서드 자체의 구조(순회·mutate하는 세 줄과, 등록만 하는 마지막 한 줄이 나란히 있는 것)가 이미 충분히 구체적인 명세 역할을 했다 - 46~49번 문서가 예외 메시지·런타임 예외·소스 주석 하나로 설계 의도를 증명했던 것과 같은 정신이 이번에도 반복됐다. 다만 이번엔 "메서드 하나 안에 있는 서로 다른 성격의 코드 줄들"이 그 증거였다는 점이 조금 달랐다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `BeanDefinitionVisitor`의 재귀적 순회 자체(프로퍼티 값 → 생성자 인자 → 중첩된 컬렉션/맵까지 내려가는 것)는 방문자 패턴의 평범한 응용이라 알고리즘적으로 새로울 게 없다 - 이번 주의 가치는 순회 구현 난이도가 아니라 "같은 `${...}` 문법이 저장 위치(가변 `BeanDefinition` vs 불변 애너테이션)에 따라 완전히 다른 시점에 해석된다"는, 눈으로 직접 확인하기 전까진 당연해 보이지 않는 사실을 재현하는 데 있었다.

## 11. Spring 설계 의도

- **왜 `BeanDefinition` 프로퍼티 값은 즉시·일괄 해석하는가**: `BeanDefinition`은 애초에 가변 객체이고, 아직 어떤 빈도 만들어지기 전인 `BeanFactoryPostProcessor` 단계에서 안전하게 수정할 수 있도록 설계됐다(`BeanFactoryPostProcessor` 인터페이스 자체의 존재 이유다). 모든 빈 정의를 한 번에 순회해서 미리 다 해석해 두면, 이후 실제 빈 생성 단계에서는 플레이스홀더 해석이라는 관심사를 완전히 잊고 이미 "확정된" 값만 다루면 된다 - 관심사를 단계별로 완전히 분리하는, 이 저장소가 반복해서 본 원칙이다.
- **왜 `@Value`는 지연 해석으로 갈 수밖에 없는가**: 애너테이션 속성은 클래스 파일에 상수로 박혀 있어서 런타임에 그 "원본"을 바꿔치기하는 것 자체가 불가능하다(리플렉션으로 읽을 수는 있어도 쓸 수는 없다). 그래서 유일하게 가능한 설계는 "원본은 그대로 두고, 그걸 해석해 주는 함수를 어딘가에 등록해 뒀다가, 그 값이 실제로 필요해지는 매 순간(그 빈이 만들어질 때마다) 그 함수를 호출한다"는 것뿐이다 - 49번(스코프드 프록시)에서 본 "실패를 없애는 게 아니라 시점을 옮긴다"는 패턴이, 이번엔 "예외"가 아니라 "값 해석" 자체에 적용된 셈이다.
- **왜 두 경로가 최종적으로는 같은 결과(해석된 값)로 수렴하는가**: 사용자 입장에서는 `<property value="${x}"/>`든 `@Value("${x}")`든 "설정값을 주입받는다"는 같은 의도를 표현한 것이다. 내부 메커니즘이 다르다고 해서 최종 결과나 문법까지 다르게 만들면 사용자에게 불필요한 인지 부담을 지우는 것이다 - Spring은 "저장 위치가 다르니 해석 시점도 다를 수밖에 없다"는 내부 제약을 감수하면서도, 겉으로 보이는 `${...}` 문법과 최종 결과는 완전히 통일해서 사용자가 이 차이를 몰라도 되게 만들었다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `PropertySourcesPlaceholderConfigurer`가 "하나의 일관된 메커니즘"이 아니라, 저장 위치에 따라 완전히 다른 두 개의 하위 메커니즘(즉시 mutate하는 `BeanDefinitionVisitor` vs 함수만 등록해 두는 `embeddedValueResolvers`)을 동시에 갖고 있었다는 것 - 이름 하나, 클래스 하나가 실제로는 서로 다른 시간대에 동작하는 두 가지 일을 하고 있었다.
- 예상 밖이었던 것: `doProcessProperties()`라는 메서드 하나 안에서, 코드 세 줄(BeanDefinition 순회 + 별칭 해석)은 "지금 당장 뭔가를 바꾸는" 코드고 마지막 한 줄(`addEmbeddedValueResolver`)만 "나중을 위해 준비만 해 두는" 코드라는 걸, 소스를 나란히 놓고 보기 전까진 구분하지 못했다 - 메서드 이름(`doProcessProperties`)만 보면 이 둘의 성격이 이렇게까지 다를 거라 짐작하기 어려웠다.
- 예상대로였던 것(재확인): 48번(BPP 등록 순서) 문서에서 확인한 "그룹이 order 값보다 우선한다"는 규칙이, `BeanFactoryPostProcessor`에도 그대로 적용된다는 것 - 그리고 이번엔 그 지식이 새로운 발견이 아니라 이번 실험을 설계하는 도구로 바로 재사용됐다는 것 자체가, 이 시리즈가 쌓아 온 이해가 서로 맞물리고 있다는 걸 보여준 사례였다.
- 새로 배운 것: "언제 해석되는가"라는 질문의 답이 "그 값을 어디에 저장했는가"(가변 객체의 필드 vs 불변 애너테이션 상수)에 의해 사실상 강제된다는 것 - 32번의 선언된 타입, 46번의 `@Bean` 반환 타입, 49번의 `ScopedObject`에 이어, "정보를 어디에 어떤 형태로 담아 두느냐가 그 정보를 다루는 메커니즘 자체를 결정한다"는 패턴을 이번엔 "해석 시점"이라는 또 다른 축에서 확인했다.
