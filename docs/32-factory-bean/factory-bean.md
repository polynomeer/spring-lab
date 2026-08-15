# FactoryBean — 빈 이름 하나가 "산출물"과 "만드는 주체" 두 개를 동시에 가리킨다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`31`](../31-lookup-method-injection/lookup-method-injection.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 지금까지 일곱 편이 전부 "컨테이너가 만든 빈 이후에 무슨 일이 더 일어나는가"(프록시, 스케줄링, 타입 변환, 서브클래싱)를 다뤘다면, 이번엔 1주차·2주차로 돌아가 **`getBean()` 자체가 반환하는 값이 항상 그 이름의 빈 인스턴스인가**라는 훨씬 기초적인 질문을 다시 연다. `FactoryBean<T>`는 이 시리즈에서 가장 오래된 확장점(Spring 초창기부터 있던 SPI)이지만, 지금까지 다룬 어떤 것과도 다른 방식으로 "빈 이름"이라는 개념 자체를 두 개로 쪼갠다.

## 1. 이번 질문

- `FactoryBean<T>` 타입의 빈을 등록하면, `getBean("그 이름")`은 정확히 무엇을 돌려주는가?
- 그 팩토리 "자신"을 얻고 싶으면 어떻게 하는가?
- 팩토리가 만드는 산출물도 싱글턴처럼 캐싱되는가 - 캐싱된다면 무엇이 그걸 결정하는가?
- 팩토리 빈 "자신"의 싱글턴 여부와, 그 팩토리가 만드는 산출물의 싱글턴 여부는 같은 것인가?
- `@Autowired`로 주입받을 때, 필드 타입을 산출물 타입으로 선언하는 것과 `FactoryBean<T>`로 선언하는 것은 어떻게 다르게 해석되는가 - 그리고 그 판단을 위해 실제로 객체를 만들어 봐야 하는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Customizing Instantiation Logic with a FactoryBean")는 `FactoryBean<T>`를 구현하면 복잡한 초기화 로직을 캡슐화한 객체를 컨테이너가 마치 평범한 빈처럼 다루게 할 수 있다고 설명한다 - `getBean("myBean")`이 `FactoryBean` 자신이 아니라 `getObject()`의 반환값을 준다고 명시한다.
- 문서는 `&` 접두어(`getBean("&myBean")`)로 팩토리 자신을 얻을 수 있다고 짧게 언급하지만, `isSingleton()`이 이 산출물 캐싱에 정확히 어떻게 관여하는지, 그리고 그게 팩토리 빈 자신의 스코프와 어떻게 다른 개념인지는 깊이 다루지 않는다 — 이번 실험은 소스와 실행으로 그 둘을 분리해서 확인했다.
- `getObjectType()`에 대해서는 "오토와이어링 시 타입을 미리 알 수 있게 해 준다"고만 설명하고, 그게 왜 `getObject()`를 호출하지 않고도 타입 판정이 가능하게 해 주는지는 다루지 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `FactoryBean` 빈을 등록하면 `getBean("이름")`이 그 팩토리 인스턴스를 돌려줄 거라 예상했다(다른 어떤 빈이든 `getBean("이름")`이 그 빈 자신을 돌려주는 게 당연하므로) — **틀렸다.** `getObject()`가 만든 산출물을 돌려준다 - 팩토리 자신을 원하면 `&`를 붙여야 한다.
- 팩토리가 만드는 산출물의 캐싱 여부는 이 팩토리 빈 자신의 스코프(싱글턴/프로토타입) 설정을 그대로 따를 거라 예상했다 — **틀렸다.** 완전히 별개의 스위치(`FactoryBean.isSingleton()`)가 따로 있다 - 팩토리 빈 자신은 평범한 싱글턴인데 산출물은 매번 새로 만들어지는 조합이 가능하다(그리고 실제로 그게 일반적인 용법이다).
- `@Autowired FactoryBean<Widget>`과 `@Autowired Widget`을 같은 클래스에 함께 쓰면, 어느 한쪽만 성공하고 다른 쪽은 실패하거나 모호성 예외가 날 거라 예상했다 — **틀렸다.** 둘 다 같은 빈 정의 하나를 서로 다르게 해석해서 문제없이 주입받는다.
- 타입 판정을 위해 `getObject()`가 먼저 호출되지 않을까 예상했다(타입을 알려면 인스턴스가 있어야 하지 않나) — **틀렸다.** `getObjectType()`이 별도로 존재하는 이유가 정확히 이걸 피하기 위해서였다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/factory-bean-lab`](../../experiments/factory-bean-lab)

```java
public class SingletonProductFactoryBean implements FactoryBean<Widget> {
    public Widget getObject() { return new Widget(); }
    public Class<?> getObjectType() { return Widget.class; }
    // isSingleton()은 오버라이드 안 함 - 기본값 true
}

@Configuration
public class SingletonFactoryBeanConfig {
    @Bean
    public SingletonProductFactoryBean widget() {
        return new SingletonProductFactoryBean();
    }
}
```

```java
context.getBean("widget");    // Widget 인스턴스
context.getBean("&widget");   // SingletonProductFactoryBean 인스턴스 자신
```

```java
public class WidgetConsumer {
    @Autowired private Widget product;               // 산출물
    @Autowired private FactoryBean<Widget> factory;   // 팩토리 자신 - 같은 "widget" 빈 정의
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `FactoryBean<T>` | 복잡한 생성 로직을 캡슐화하는 전략 인터페이스 - `getObject()`/`getObjectType()`/`isSingleton()` 세 메서드 |
| `BeanFactory.FACTORY_BEAN_PREFIX` | `"&"` 리터럴 상수 - 빈 이름 앞에 붙이면 산출물이 아니라 팩토리 자신을 요청한다는 신호 |
| `AbstractBeanFactory#getObjectForBeanInstance` | 방금 얻은(또는 캐시에서 찾은) 원시 빈 인스턴스가 `FactoryBean`인지, 이름에 `&`가 붙었는지를 보고 무엇을 돌려줄지 최종 결정하는 지점 |
| `FactoryBeanRegistrySupport` | `factoryBeanObjectCache`라는, 싱글턴 캐시(`DefaultSingletonBeanRegistry`, 4주차)와는 **별도인** 두 번째 캐시를 관리 - 산출물 전용 |
| (실험) `SingletonProductFactoryBean`/`NonSingletonProductFactoryBean` | `isSingleton()` 하나만 다르게 오버라이드해서, 팩토리 자신의 스코프와 산출물의 캐싱 여부가 독립적이라는 것을 대비시킴 |

## 6. 호출 흐름

```text
context.getBean("widget")                                  (1주차의 BeanFactory#getBean)
  → AbstractBeanFactory#doGetBean("widget")
      → isFactoryDereference = BeanFactoryUtils.isFactoryDereference("widget")   → false ("&" 없음)
      → sharedInstance = getSingleton("widget")             (실제로는 "widget"이라는 이름의
                                                               FactoryBean 원시 인스턴스 자체가
                                                               DefaultSingletonBeanRegistry에 캐시돼 있음)
      → getObjectForBeanInstance(sharedInstance, "widget", "widget", mbd)
          → if (sharedInstance instanceof FactoryBean<?> factoryBean) {
                if (!isFactoryDereference) {                 ← 이름에 "&"가 없으므로 여기로
                    object = getObjectFromFactoryBean(factoryBean, beanName, ...)
                        → FactoryBeanRegistrySupport#getObjectFromFactoryBean
                            → if (factory.isSingleton() && containsSingleton(beanName)) {
                                  factoryBeanObjectCache에서 조회, 없으면 doGetObjectFromFactoryBean()
                                  후 factoryBeanObjectCache.put()                 ← 캐시 경로
                              } else {
                                  doGetObjectFromFactoryBean()만 실행, 캐시 안 함  ← 매번 새로
                              }
                }
            }

context.getBean("&widget")
  → isFactoryDereference = true                              ("&" 있음)
  → sharedInstance(=FactoryBean 원시 인스턴스) 자체를 그대로 반환      (DefaultSingletonBeanRegistry의
                                                                 평범한 싱글턴 캐시 - 4주차와 동일)

@Autowired 필드 타입 판정 (오토와이어링 후보 매칭 시점, getObject() 호출 없이)
  → 필드 타입이 Widget          → FactoryBean.getObjectType()로 Widget.class를 확인 → 이 빈 정의가 후보
  → 필드 타입이 FactoryBean<Widget> → 원시 클래스(SingletonProductFactoryBean)가 FactoryBean을 구현하는지로 판정
```

두 캐시(빈 인스턴스 캐시 vs `factoryBeanObjectCache`)와 `&` 접두어가 그 사이에서 하는 역할을 함께 그린 다이어그램: [`diagrams/factory-bean-dereferencing.md`](diagrams/factory-bean-dereferencing.md)

## 7. 브레이크포인트

25~31번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.beans.factory.FactoryBean#isSingleton (Javadoc 및 기본 구현)
org.springframework.beans.factory.BeanFactory#FACTORY_BEAN_PREFIX
org.springframework.beans.factory.support.AbstractBeanFactory#getObjectForBeanInstance
org.springframework.beans.factory.support.FactoryBeanRegistrySupport#getObjectFromFactoryBean
```

## 8. 런타임 관찰

[`FactoryBeanTest`](../../experiments/factory-bean-lab/src/test/java/lab/experiments/factorybean/FactoryBeanTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| `getBean("widget")` (이름에 `&` 없음) | `Widget` 인스턴스 - 팩토리가 아니라 산출물 |
| `getBean("&widget")` | `SingletonProductFactoryBean` 인스턴스 자신 |
| `isSingleton()`(기본값 true)인 팩토리에서 `getBean("widget")` 두 번 | 같은 `Widget` 인스턴스, `getObject()` 호출 횟수 **1회** |
| `isSingleton() = false`인 팩토리에서 `getBean("widget")` 두 번 | 서로 다른 `Widget` 인스턴스, `getObject()` 호출 횟수 **2회** - 그런데 `getBean("&widget")`은 몇 번을 불러도 **같은** 팩토리 인스턴스 |
| `@Autowired private Widget product` | 산출물이 주입됨 |
| `@Autowired private FactoryBean<Widget> factory` | 같은 빈 정의에서 팩토리 자신이 주입됨(`&widget`으로 직접 조회한 것과 동일 인스턴스) |

**직접 겪은 것**: 네 번째 행을 설계하면서, "`isSingleton() = false`면 팩토리 빈 자신도 매번 새로 만들어지지 않을까"라고 잠깐 헷갈렸다 - 하지만 `@Bean`으로 등록한 `NonSingletonProductFactoryBean` 자체는 이 저장소가 4주차부터 다뤄 온 평범한 싱글턴 빈 등록 규칙을 그대로 따른다(스코프를 따로 지정하지 않았으므로). `isSingleton()`은 어디까지나 "이 팩토리가 만드는 산출물"에 대한 스위치이지, 팩토리 자신의 스코프와는 아무 관계가 없다 - `"&widget"`을 두 번 조회해서 같은 인스턴스인지 직접 비교해 보고서야 이 둘이 완전히 독립적이라는 것을 확실히 했다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다. 이번 주제도 몇 개 클래스의 소스 자체가 가장 직접적인 근거였다.

- `AbstractBeanFactory#doGetBean`의 실제 소스: `isFactoryDereference`(이름이 `"&"`로 시작하는지) 여부와 `sharedInstance instanceof FactoryBean`을 함께 확인해서, 산출물을 돌려줄지 팩토리 자신을 돌려줄지를 결정한다는 것을 확인했다 - 8번 절 첫 두 행의 직접적인 근거다.
- `FactoryBeanRegistrySupport#getObjectFromFactoryBean`의 실제 소스: `if (factory.isSingleton() && containsSingleton(beanName))`라는 조건 하나가 캐시 경로와 매번-새로 경로를 완전히 가른다는 것을 확인했다 - `factoryBeanObjectCache`(`ConcurrentHashMap`)가 `DefaultSingletonBeanRegistry`의 싱글턴 캐시와는 **별도의 저장소**라는 것도 함께 확인했다 - 팩토리 빈 자신은 첫 번째 캐시에, 그 산출물은(캐싱된다면) 두 번째 캐시에 따로 들어간다.
- `FactoryBean#isSingleton()`의 Javadoc 원문: "The singleton status of the FactoryBean itself will generally be provided by the owning BeanFactory; usually, it has to be defined as singleton there."라고 명시한다 - 팩토리 자신의 싱글턴 여부(컨테이너가 관리)와 `isSingleton()`이 말하는 산출물의 싱글턴 여부가 서로 다른 개념이라는 것을 공식 문서에서도 명확히 구분하고 있다는 근거다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `mini-spring/mini-container`가 이미 `getBean()`/싱글턴 캐시라는 골격을 다뤘고, 이번 주의 진짜 가치는 그 골격 위에 "이름 하나가 두 가지로 해석될 수 있다"는 조건 분기 하나(`isFactoryDereference` + `instanceof FactoryBean`)와, 산출물 전용의 **두 번째 캐시**가 별도로 존재한다는 것을 이해하는 데 있었다 - 이건 뼈대를 다시 만드는 것보다, 기존 뼈대(4주차) 위에서 실제 Spring과 나란히 비교하는 쪽이 훨씬 명확했다.

## 11. Spring 설계 의도

- **왜 `getBean("이름")`은 팩토리가 아니라 산출물을 돌려주는 쪽을 기본으로 삼았는가**: `FactoryBean`의 존재 이유 자체가 "복잡한 생성 로직을 감추고, 그 결과물을 마치 평범한 빈처럼 쓰게 한다"는 것이다 - 사용하는 쪽(다른 빈이 이 빈을 의존성으로 주입받는 코드) 입장에서는 애초에 그게 `FactoryBean`으로 구현됐다는 사실 자체를 알 필요가 없어야 한다. 만약 기본값이 반대(팩토리 자신을 돌려주는 것)였다면, `FactoryBean`을 쓰는 모든 곳에서 소비자 코드가 "이건 팩토리니까 `.getObject()`를 한 번 더 불러야 한다"는 사실을 알고 있어야 했을 것이다 - 캡슐화가 새어 나가는 것이다. `&` 접두어라는 명시적 우회로만 팩토리 자신에 접근하게 한 것은, "평소엔 산출물만 보이고, 팩토리 자신을 다루고 싶을 때만 의도적으로 그렇게 요청한다"는 캡슐화 원칙을 지키기 위한 설계다.
- **왜 산출물 캐싱이 팩토리 빈 자신의 스코프와 별개의 스위치인가**: 팩토리 빈 자신의 스코프(싱글턴/프로토타입)는 "이 생성 로직을 담은 객체가 몇 개 필요한가"에 대한 답이고, `isSingleton()`은 "그 로직이 만들어 내는 결과물이 매번 같아야 하는가"에 대한 답이다 - 이 둘은 서로 다른 질문이다. 예를 들어 커넥션 풀을 만드는 `FactoryBean`은 팩토리 자신도 산출물도 둘 다 싱글턴이어야 자연스럽지만, "매 요청마다 새 트랜잭션 컨텍스트 객체를 만들어 주는" `FactoryBean`은 팩토리 로직 자체는 재사용(싱글턴)하면서 산출물만 매번 새로 만들고 싶을 수 있다 - 두 스위치를 분리해 두지 않으면 이런 조합을 표현할 방법이 없다.
- **왜 `getObjectType()`이 `getObject()`와 별도로 존재하는가**: 오토와이어링 후보 판정은 애플리케이션 컨텍스트가 아직 완전히 준비되지 않은 이른 시점에도 일어날 수 있고, 후보가 여러 개면 그중 실제로 선택되지 않은 빈들에 대해서까지 `getObject()`를 호출해서 부작용 있는 생성 로직을 실행해 버리는 것은 낭비이자 위험(순환 참조, 초기화 순서 문제)이다. `getObjectType()`은 "인스턴스를 만들지 않고도 타입만은 미리 알려줄 수 있다"는 훨씬 가벼운 계약을 분리해 둠으로써, 후보 탐색이라는 "저울질" 단계와 실제 생성이라는 "확정" 단계를 명확히 나눈다 - 이 저장소가 반복해서 봐 온 "결정과 실행을 분리한다"는 원칙(2주차의 `BeanDefinition`과 빈 인스턴스 분리와 정확히 같은 정신)이 여기서도 나타난다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `getBean("이름")`이 그 이름의 빈 "자신"을 돌려줄 것이라는, 지금까지 이 저장소 전체에서 한 번도 의심해 본 적 없던 전제가 `FactoryBean` 앞에서는 깨진다는 것 - 1주차부터 당연하게 써 온 `getBean()`의 계약에 예외가 있다는 걸 이렇게 늦게, 로드맵을 다 끝내고서야 마주하게 될 줄은 몰랐다.
- 예상 밖이었던 것: 산출물 캐싱을 위한 저장소가 4주차부터 알고 있던 싱글턴 캐시(`DefaultSingletonBeanRegistry`)와 완전히 별개(`factoryBeanObjectCache`)라는 것 - "싱글턴은 한 곳에 캐시된다"는 단순한 그림이, `FactoryBean`이라는 계층 하나가 끼어들면서 "두 계층의 캐시가 나란히 존재한다"는 그림으로 바뀌었다.
- 예상대로였던 것(재확인): `@Autowired`가 실제 인스턴스를 만들어 보지 않고도 타입을 판정할 수 있어야 한다는 것 - 9주차(생성자 주입과 의존성 탐색)에서 다룬 "후보를 찾는 것"과 "실제로 주입하는 것"이 분리돼 있다는 원칙이, `getObjectType()`이라는 구체적인 메서드로 다시 확인됐다.
- 새로 배운 것: 이 시리즈(25~31번)가 전부 "이미 만들어진 빈에 뭔가를 더한다"(프록시로 감싸거나, 스케줄을 걸거나, 서브클래스로 만들거나)는 방향이었는데, `FactoryBean`은 그 반대다 - **"빈을 만드는 것 자체"를 사용자 코드에 완전히 위임하는** 가장 근본적인 확장점이었다. 이 저장소가 1주차에 배운 `BeanFactory`라는 이름 자체가, 결국 이 확장점의 정신(빈을 만드는 것을 위임할 수 있다)을 가장 압축적으로 담고 있었다는 것을, 로드맵을 완전히 마친 뒤에야 새삼 확인했다.
