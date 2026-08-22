# BeanDefinition 덮어쓰기 — 같은 이름의 빈 두 개를 등록하면 조용히 하나가 사라진다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`44`](../44-expose-proxy/expose-proxy.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 1~2주차는 `BeanDefinitionRegistry`에 빈 정의를 "등록하는" 쪽만 다뤘는데, 같은 이름으로 **두 번** 등록하면 무슨 일이 일어나는지는 한 번도 확인한 적이 없다 - 순수 Spring Framework의 기본값이 조용한 덮어쓰기라는 것, 그리고 [`40-property-source-ordering`](../40-property-source-ordering/property-source-ordering.md)에서 이미 본 "값이 아니라 위치가 결정한다"는 패턴이 빈 등록에서도 똑같이 반복된다는 것을 확인한다.

## 1. 이번 질문

- 같은 이름의 `BeanDefinition`을 두 번 등록하면 예외가 나는가, 아니면 조용히 대체되는가?
- 대체된다면, 나중에 등록한 쪽이 이기는가 아니면 처음 등록한 쪽이 유지되는가?
- 이걸 막고 싶으면(=명시적 실수 방지) 어떻게 하는가?
- 막았을 때 나는 예외는 어느 쪽이 새 정의고 어느 쪽이 기존 정의인지 구분해서 알려주는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스는 `DefaultListableBeanFactory#setAllowBeanDefinitionOverriding(boolean)`을 "같은 이름으로 다시 등록된 빈 정의가 기존 것을 덮어쓸지 여부를 제어한다"고 설명한다.
- 문서는 Spring Boot가 2.1부터 `SpringApplication` 기반 애플리케이션에서 이 값을 기본적으로 `false`로 바꿨다는 것을 별도로 언급한다 - 반대로 말하면 **순수 Spring Framework**(`AnnotationConfigApplicationContext` 등)의 기본값이 무엇인지는 이 언급의 반대쪽을 유추해야 알 수 있다.
- `BeanDefinitionOverrideException`(Spring 5.1부터) API 문서는 "새 정의"와 "기존 정의"를 각각 `getBeanDefinition()`/`getExistingDefinition()`으로 구분해서 제공한다고 명시한다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 같은 이름으로 두 번째 빈을 등록하면 Spring Framework가 기본적으로 예외를 던질 거라 예상했다(이름 충돌은 명백한 실수처럼 보이므로) — **틀렸다.** 순수 `AnnotationConfigApplicationContext`의 기본값은 **허용**(조용한 대체)이다 - Boot가 2.1부터 이 기본값을 뒤집어서 `false`로 바꾼 것이었지, Framework 자체의 기본값이 엄격했던 게 아니다.
- 덮어쓰기가 허용될 때, "먼저 등록한 게 원본이니 나중 것이 무시될 것"이라 예상했다 — **틀렸다.** 정확히 반대다 - **나중에 등록한 쪽이 이긴다.** 처음 등록된 정의가 있던 자리를 그냥 덮어쓴다.
- `allowBeanDefinitionOverriding(false)`로 막았을 때 나는 예외가 "이미 등록된 빈이 있다"는 정도의 애매한 메시지만 줄 거라 예상했다 — **틀렸다.** `BeanDefinitionOverrideException`은 새 정의와 기존 정의 **둘 다**를 구조적으로(객체로) 담고 있어서, 어느 설정 클래스에서 왔는지까지 프로그래밍적으로 구분할 수 있다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/bean-definition-overriding-lab`](../../experiments/bean-definition-overriding-lab)

```java
@Configuration
public class ConfigA {
    @Bean public Greeting greeting() { return new Greeting("from-A"); }
}

@Configuration
public class ConfigB {
    @Bean public Greeting greeting() { return new Greeting("from-B"); }   // 같은 이름
}
```

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
context.register(ConfigA.class, ConfigB.class);
context.refresh();
context.getBean(Greeting.class).message();   // "from-B" - 나중에 등록된 쪽이 이김

// 순서를 뒤집으면...
context.register(ConfigB.class, ConfigA.class);
context.getBean(Greeting.class).message();   // "from-A" - 이번엔 A가 이김
```

```java
DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();
beanFactory.setAllowBeanDefinitionOverriding(false);
context.register(ConfigA.class, ConfigB.class);
context.refresh();
// → BeanDefinitionOverrideException: 'greeting' ... factoryBeanName=configB ... 이미 있던 factoryBeanName=configA
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `DefaultListableBeanFactory#allowBeanDefinitionOverriding` | `Boolean`(박싱 타입, nullable) 필드 - 기본값 `null` |
| `DefaultListableBeanFactory#isAllowBeanDefinitionOverriding` | `!Boolean.FALSE.equals(...)`로 판정 - `null`이든 `true`든 전부 "허용"으로 취급, 오직 명시적 `false`만 "금지" |
| `DefaultListableBeanFactory#registerBeanDefinition` | 같은 이름의 기존 정의가 있으면 `isBeanDefinitionOverridable()`을 확인해서, 허용이면 그냥 `beanDefinitionMap.put()`으로 덮어쓰고 금지면 예외를 던지는 지점 |
| `BeanDefinitionOverrideException` | `getBeanName()`/`getBeanDefinition()`(새 정의)/`getExistingDefinition()`(기존 정의)를 각각 제공하는 전용 예외 - `BeanDefinitionStoreException`의 하위 클래스 |

## 6. 호출 흐름

```text
context.register(ConfigA.class, ConfigB.class); context.refresh();
  → ConfigurationClassPostProcessor(2주차)가 ConfigA, ConfigB를 순서대로 파싱
      → ConfigA의 @Bean greeting() → registerBeanDefinition("greeting", defA)
          → beanDefinitionMap에 "greeting" 없음 → 그냥 추가
      → ConfigB의 @Bean greeting() → registerBeanDefinition("greeting", defB)
          → beanDefinitionMap에 "greeting" 이미 있음(defA)
          → isBeanDefinitionOverridable("greeting")
              → isAllowBeanDefinitionOverriding()
                  → !Boolean.FALSE.equals(this.allowBeanDefinitionOverriding)
                  → allowBeanDefinitionOverriding이 null(기본값)이면 → true (허용)
          → 허용이므로: logBeanDefinitionOverriding(...) (info/debug 로그만) 후
            beanDefinitionMap.put("greeting", defB)   ← defA가 있던 자리를 defB로 그냥 교체

(allowBeanDefinitionOverriding(false)로 명시적으로 금지한 경우)
          → isBeanDefinitionOverridable("greeting") → false
          → throw new BeanDefinitionOverrideException("greeting", defB, defA)
              (defB = 새로 등록하려던 것, defA = 이미 있던 것 - 이 순서 그대로 생성자에 전달)
          → 이 예외는 별도로 감싸지지 않고 refresh() 밖으로 그대로 전파
```

등록 순서가 승자를 결정하는 지점과, `allowBeanDefinitionOverriding`이 조회되는 조건 분기를 함께 그린 다이어그램: [`diagrams/bean-definition-override-flow.md`](diagrams/bean-definition-override-flow.md)

## 7. 브레이크포인트

25~44번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어느 쪽이 이기는가"라는 결과였고, 실행 결과로 확인하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.factory.support.DefaultListableBeanFactory#allowBeanDefinitionOverriding (필드 선언부)
org.springframework.beans.factory.support.DefaultListableBeanFactory#isAllowBeanDefinitionOverriding
org.springframework.beans.factory.support.DefaultListableBeanFactory#registerBeanDefinition
org.springframework.beans.factory.support.BeanDefinitionOverrideException (getBeanDefinition/getExistingDefinition)
```

## 8. 런타임 관찰

[`BeanDefinitionOverridingTest`](../../experiments/bean-definition-overriding-lab/src/test/java/lab/experiments/overriding/BeanDefinitionOverridingTest.java) (4개):

| 실험 | 결과 |
| --- | --- |
| `register(ConfigA, ConfigB)`, 기본 설정 | 예외 없음 - `Greeting.message() == "from-B"` |
| `register(ConfigB, ConfigA)`(순서만 뒤집음), 기본 설정 | 예외 없음 - 이번엔 `"from-A"` |
| `allowBeanDefinitionOverriding(false)` + `register(ConfigA, ConfigB)` | `BeanDefinitionOverrideException` - 다른 어떤 것으로도 감싸지지 않고 그대로 전파 |
| 그 예외의 `getBeanName()`/`getBeanDefinition().getFactoryBeanName()`/`getExistingDefinition().getFactoryBeanName()` | 각각 `"greeting"`/`"configB"`(새로 등록하려던 것)/`"configA"`(이미 있던 것) |

**직접 겪은 것**: 세 번째 실험을 작성하기 전, `ConfigurationClassPostProcessor`가 예외를 자기 나름의 형태로 다시 감싸서 던질 거라 예상하고(`BeanDefinitionStoreException`류로 한 번 더 포장될 거라 짐작해서) 처음엔 `getCause()` 체인까지 확인하는 코드를 준비했다 - 실제로 실행해 보니 `BeanDefinitionOverrideException` 자체가 아무 포장 없이 `refresh()` 밖으로 그대로 튀어나왔다. `assertThatThrownBy(...).isInstanceOf(BeanDefinitionOverrideException.class)`라는 가장 단순한 형태로 충분했다 - 예상보다 코드가 간단해졌다는 것 자체가, "이 경로엔 추가로 감싸는 코드가 없다"는 걸 실행으로 확인해 준 것이었다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `DefaultListableBeanFactory`의 실제 소스: `private Boolean allowBeanDefinitionOverriding;`가 초기값 없이(즉 `null`) 선언돼 있고, `isAllowBeanDefinitionOverriding()`이 `return !Boolean.FALSE.equals(this.allowBeanDefinitionOverriding);`로 구현돼 있다는 것을 확인했다 - `null`과 `true`를 굳이 구분하지 않고 "명시적으로 `false`가 아니면 전부 허용"이라는 한 줄로 처리한다. `isBeanDefinitionOverridable(String beanName)`이 그냥 이 메서드에 위임할 뿐이라는 것도 확인했다 - 현재 버전에서는 빈 이름별로 다른 정책을 적용하는 기능은 없고, 메서드 시그니처만 향후 확장(서브클래스가 이름별로 다르게 판단하도록 오버라이드하는 것)을 열어 두고 있다.
- `registerBeanDefinition`의 실제 소스: `if (existingDefinition != null) { if (!isBeanDefinitionOverridable(beanName)) { throw new BeanDefinitionOverrideException(...); } else { logBeanDefinitionOverriding(...); } this.beanDefinitionMap.put(beanName, beanDefinition); }`를 확인했다 - `put()` 호출이 `if`/`else` 분기 **바깥**, 즉 예외를 던지지 않은 경우에만 도달하는 공통 경로에 있다는 것이, "허용되면 그냥 덮어쓴다"의 정확한 근거다.
- `BeanDefinitionOverrideException`의 생성자 실제 소스: `registerBeanDefinition`이 이 예외를 `new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition)`으로 만든다는 것을 확인했다 - 두 번째 인자가 항상 "지금 등록하려는 새 정의", 세 번째가 "이미 맵에 있던 기존 정의"라는 순서가 고정돼 있다는 근거이고, 8번 절 네 번째 행의 `getBeanDefinition()`/`getExistingDefinition()` 결과가 왜 각각 `configB`/`configA`였는지를 설명한다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `beanDefinitionMap.put(name, definition)`이라는 핵심 동작 자체는 `mini-spring/mini-container`(1~4주차)가 이미 다룬 "이름으로 저장한다"는 `Map` 기반 레지스트리와 본질적으로 다르지 않다 - 이번 주의 가치는 그 저장 동작 자체가 아니라, "이미 그 이름이 있을 때 무엇을 할 것인가"라는 **분기 하나**(허용 여부에 따라 조용히 덮어쓸지, 예외를 던질지)와 그 분기의 기본값에 있었다.

## 11. Spring 설계 의도

- **왜 순수 Framework의 기본값은 관대함(허용)인가**: `@Configuration` 클래스를 여러 개 조합해서 애플리케이션을 구성한다는 것 자체가, 사용자가 의도적으로 "나중에 등록한 설정으로 앞선 것을 재정의하고 싶다"는 상황을 자주 만든다 - 예를 들어 테스트 전용 설정 클래스로 프로덕션 설정의 특정 빈만 바꿔치기하는 패턴(`@Import`로 순서를 조정해서 테스트 설정을 나중에 적용)이 흔하다. 기본값을 엄격하게(예외) 잡아 두면 이런 정당한 재정의 패턴까지 매번 명시적인 허용 설정을 요구하게 된다 - Framework는 "유연성을 기본으로 하고, 필요하면 사용자가 직접 조여 잠근다"는 쪽을 택했다.
- **왜 Spring Boot는 이 기본값을 뒤집었는가(문서 언급)**: Boot 애플리케이션은 수십~수백 개의 자동 설정 클래스(18주차)와 사용자 설정이 뒤섞여 있어서, 의도치 않은 빈 이름 충돌이 발생하기 훨씬 쉬운 환경이다 - 그리고 그 충돌이 "조용히" 일어나면, 어떤 자동 설정이 사용자가 의도한 빈을 몰래 덮어써 버려도 아무 신호가 없다. 이런 대규모·다층적 조합 환경에서는 "실수를 조용히 허용하는 것"의 위험이 "정당한 재정의를 막는 것"의 불편함보다 크다고 판단해서, 기본값을 안전한 쪽(예외)으로 뒤집은 것으로 보인다 - 30번(`ConversionService`)·39번(`MessageSource`)에서 본 "이름 하나가 계약"이라는 패턴과 달리, 이번엔 "같은 계층(Framework)인데도 그 위에 얹힌 다른 계층(Boot)이 기본값 자체를 다시 판단해서 뒤집을 수 있다"는 사례다.
- **왜 나중에 등록한 것이 이기는가(먼저 등록한 것이 아니라)**: `beanDefinitionMap.put()`은 자바의 평범한 `Map` 연산이다 - 같은 키로 다시 `put()`하면 당연히 새 값이 이전 값을 대체한다. 만약 Spring이 "처음 등록한 것을 유지한다"는 반대 규칙을 원했다면, `put()` 전에 `containsKey()`를 확인해서 있으면 아예 무시하는 별도 로직이 필요했을 것이다 - 지금의 동작은 특별한 로직을 추가한 결과가 아니라, `Map.put()`의 가장 자연스러운 기본 동작을 그대로 쓴 결과다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: Spring Framework 자체의 기본값이 "엄격함"이 아니라 "관대함"이라는 것 - Boot를 오래 써 온 사람이라면(이 기본값이 엄격하게 느껴지는 환경에 익숙하다면) 정반대로 알고 있기 쉽다. Boot의 기본값이 Framework의 기본값이 아니라 **Boot가 별도로 뒤집어 둔 것**이라는 걸, 이번에 소스로 명확히 구분했다.
- 예상 밖이었던 것: "나중 것이 이긴다"는 규칙이 특별한 로직이 아니라 그냥 `Map.put()`의 자연스러운 결과였다는 것 - "덮어쓰기 정책"이라는 이름 때문에 뭔가 정교한 우선순위 판단 로직이 있을 거라 기대했는데, 실제로는 40번(PropertySource 순서)에서 본 것과는 또 다른 종류의 단순함이었다(거긴 "위치"가 결정했다면, 여긴 "가장 최근 쓰기"가 결정한다).
- 예상대로였던 것(재확인): 명시적으로 설정을 조이면(`allowBeanDefinitionOverriding(false)`) 그 즉시 명확한 실패로 이어진다는 것 - 25~44번 내내 반복해서 본 "명시적으로 요청한 안전장치는 확실하게 작동한다"는 원칙이 여기서도 그대로 확인됐다.
- 새로 배운 것: `BeanDefinitionOverrideException`이 단순히 "충돌했다"는 사실만 알리는 게 아니라, 새 정의와 기존 정의를 프로그래밍적으로 구분해서 제공한다는 것 - 이건 이 예외를 사람이 읽는 로그로만 소비하는 게 아니라, (예를 들어 커스텀 `BeanFactoryPostProcessor`나 진단 도구가) 코드로 그 정보를 파싱해서 "정확히 어느 두 설정이 충돌했는지" 자동으로 리포트할 수 있게 설계됐다는 뜻이다.
