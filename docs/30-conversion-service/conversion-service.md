# ConversionService — 빈 이름 문자열 하나가 타입 변환 전체를 켜고 끈다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`29`](../29-async-methods/async-methods.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. 지금까지 다섯 편은 전부 "메서드에 붙은 애노테이션이 어떻게 처리되는가"(인터셉터, `BeanPostProcessor`, 테스트 캐시)였는데, 이번엔 방향을 바꿔 `@Value("${...}")`로 들어오는 **문자열이 목표 타입으로 어떻게 바뀌는가**를 본다. 그리고 그 답이 "빈 이름이 정확히 `\"conversionService\"`인가"라는, 코드 어디에도 눈에 띄게 강조되지 않는 조건 하나에 달려 있다는 것을 실행으로 확인한다.

## 1. 이번 질문

- `@Value("${app.point}")`가 커스텀 타입(`Point`)으로 변환되려면 무엇이 있어야 하는가?
- `ConversionService` 빈을 등록하기만 하면 되는가, 아니면 다른 조건이 더 있는가?
- 커스텀 컨버터 없이도 `List<Integer>`처럼 변환되는 타입이 있는가 - 있다면 왜인가?
- `ConversionService` 빈이 없거나 잘못 설정됐을 때는 어떤 예외가 나는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Spring Type Conversion", "The ConversionService API")는 `Converter<S, T>`를 구현해서 `ConversionService`에 등록하면 `@Value`, XML 프로퍼티 값, 데이터 바인딩에 모두 적용된다고 설명한다.
- 문서는 `ConversionService`를 컨테이너가 인식하게 하려면 "빈 이름을 `conversionService`로 등록하라"고 짧게 언급하지만, 그 이름을 놓치면 정확히 어떤 일이 일어나는지(예외 종류, 실패 시점)는 설명하지 않는다 — 이번 실험은 소스와 실행으로 직접 확인했다.
- `DefaultConversionService`가 기본으로 제공하는 컨버터 목록(문자열↔숫자, 문자열↔열거형, 컬렉션/배열 변환 등)은 API 문서에 나열돼 있지만, "커스텀 컨버터를 하나도 등록하지 않아도 `List<Integer>`가 되는 이유"까지 연결해서 설명하지는 않는다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `ConversionService` 타입의 빈을 아무 이름으로나 등록하면, 컨테이너가 타입으로 찾아서 자동으로 연결해 줄 거라 예상했다 — **틀렸다.** 컨테이너는 정확히 `"conversionService"`라는 리터럴 문자열 이름의 빈만 찾는다 - 타입이 정확히 일치해도 이름이 다르면 완전히 무시된다.
- 이름이 잘못된 `ConversionService` 빈이 있으면, 최소한 기본 `PropertyEditor` 기반 변환으로라도 폴백해서 부분적으로는 동작할 거라 예상했다 — **틀렸다.** 이름이 틀린 경우와 아예 등록하지 않은 경우가 **완전히 동일한** 실패로 이어진다 - 컨테이너 입장에서는 둘 사이에 차이가 전혀 없다.
- 커스텀 컨버터를 등록하지 않으면 `List<Integer>` 같은 컬렉션 타입은 변환이 안 될 거라 예상했다 — **틀렸다.** `DefaultConversionService`가 내장하고 있는 문자열→컬렉션 분리 변환기 덕분에, `ConversionService` 빈만 (이름을 맞춰서) 등록하면 커스텀 컨버터 없이도 `"1,2,3"` → `List<Integer>`가 그냥 된다.
- 변환 실패는 애플리케이션 시작과 무관하게, 그 값을 실제로 쓰는 시점에야 나타날 거라 예상했다 — **틀렸다.** `@Value` 필드는 빈 생성(`populateBean`) 시점에 바로 채워지므로, 변환 실패는 `ApplicationContext` 시작(`refresh()`) 자체를 막는 컨텍스트 시작 실패로 나타난다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/conversion-service-lab`](../../experiments/conversion-service-lab)

```java
public record Point(int x, int y) {}

public class StringToPointConverter implements Converter<String, Point> {
    public Point convert(String source) {
        String[] parts = source.split(",");
        return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }
}

public class ConvertiblePropertiesHolder {
    @Value("${app.point}")   private Point point;
    @Value("${app.numbers}") private List<Integer> numbers;
}
```

```java
@Configuration
public class CorrectlyNamedConversionServiceConfig {
    @Bean
    public DefaultConversionService conversionService() {      // 이름이 정확히 "conversionService"
        DefaultConversionService cs = new DefaultConversionService();
        cs.addConverter(new StringToPointConverter());
        return cs;
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ConfigurableApplicationContext.CONVERSION_SERVICE_BEAN_NAME` | `"conversionService"`라는 리터럴 문자열 상수 - 이 이름 자체가 계약이다 |
| `AbstractApplicationContext#prepareBeanFactory` | `refresh()`의 두 번째 단계(3주차의 12단계 중 하나) - 이 이름의 빈이 있고 타입이 맞으면 `beanFactory.setConversionService()`를 호출 |
| `AbstractBeanFactory` | `conversionService` 필드를 갖고 있고, `getTypeConverter()`가 새 `SimpleTypeConverter`를 만들 때마다 이 필드를 그대로 주입해 준다 |
| `TypeConverterDelegate` | 실제 변환 로직 - 등록된 `PropertyEditor`가 없고 `ConversionService`가 있으면 그 `ConversionService`로 위임 |
| `DefaultConversionService` | `GenericConversionService` + 표준 컨버터 세트(숫자·열거형·문자열·컬렉션·배열 등)를 미리 등록해 둔 기본 구현체 |
| `Converter<S, T>` | 사용자가 구현하는 1:1 타입 변환 전략 인터페이스 |
| (실험) `ConvertiblePropertiesHolder` | `@Value`로 커스텀 타입(`Point`)과 내장 지원 타입(`List<Integer>`)을 동시에 주입받아 두 경로를 한 번에 관찰 |

## 6. 호출 흐름

```text
ApplicationContext.refresh()                              (3주차의 12단계)
  → prepareBeanFactory(beanFactory)                        (2번째 단계)
      → if (beanFactory.containsBean("conversionService")
              && beanFactory.isTypeMatch("conversionService", ConversionService.class)) {
            beanFactory.setConversionService(
                beanFactory.getBean("conversionService", ConversionService.class));
        }
        (이름이 다르면 이 블록 자체가 건너뛰어진다 - 그 빈은 그냥 평범한 다른 빈으로만 존재)
  → ...
  → finishBeanFactoryInitialization(beanFactory)            (11번째 단계 - 싱글턴 생성)
      → ConvertiblePropertiesHolder 빈 생성
          → populateBean → @Value 필드 처리(AutowiredAnnotationBeanPostProcessor)
              → Environment에서 "${app.point}" → "3,4" 로 플레이스홀더 치환
              → beanFactory.getTypeConverter().convertIfNecessary("3,4", Point.class)
                  → SimpleTypeConverter.conversionService == (설정됐다면 그 인스턴스, 아니면 null)
                  → conversionService != null && conversionService.canConvert(String, Point)?
                      예 → conversionService.convert("3,4", Point.class)   ← 성공 경로
                      아니오(=null이거나 canConvert가 false) → PropertyEditor 탐색
                          → Point에 맞는 PropertyEditor도 없음
                          → IllegalStateException("no matching editors or conversion strategy found")
                          → ConversionNotSupportedException으로 감싸짐
                          → UnsatisfiedDependencyException으로 감싸짐 (필드 주입 실패)
                          → refresh() 자체가 실패
```

빈 이름이 정확히 일치할 때와 그렇지 않을 때 이 흐름이 어디서 갈라지는지를 그린 다이어그램: [`diagrams/conversion-service-wiring.md`](diagrams/conversion-service-wiring.md)

## 7. 브레이크포인트

25~29번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "이름이 일치하는가"라는 단순한 조건 분기라, 실제 실행 결과(성공/실패, 그리고 실패 메시지가 글자 그대로 같은가)로 확인하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.context.ConfigurableApplicationContext#CONVERSION_SERVICE_BEAN_NAME
org.springframework.context.support.AbstractApplicationContext#prepareBeanFactory
org.springframework.beans.factory.support.AbstractBeanFactory#getTypeConverter
org.springframework.beans.TypeConverterDelegate#convertIfNecessary
```

## 8. 런타임 관찰

[`ConversionServiceTest`](../../experiments/conversion-service-lab/src/test/java/lab/experiments/conversion/ConversionServiceTest.java) (3개):

| 실험 | 결과 |
| --- | --- |
| 빈 이름이 정확히 `"conversionService"` | 커스텀 타입(`Point`)·내장 지원 타입(`List<Integer>`) 둘 다 정상 변환 |
| 같은 타입, 다른 이름(`myConversionService`)의 빈 | `refresh()` 자체가 실패 - `UnsatisfiedDependencyException` → `ConversionNotSupportedException` → `IllegalStateException("no matching editors or conversion strategy found")` |
| `ConversionService` 빈을 아예 등록하지 않음 | **글자 그대로 같은** 예외 메시지로 실패 - 이름이 틀린 경우와 완전히 동일하게 취급됨 |

**직접 겪은 것**: 처음에는 이름이 틀린 경우 "적어도 커스텀 `Point` 변환만 실패하고, 내장 지원인 `List<Integer>`는 성공하지 않을까"라고 예상하고 두 필드를 한 번에 관찰하는 테스트를 짰는데, 실제로는 **첫 번째 필드(`point`)에서 이미 실패**해서 두 번째 필드(`numbers`)까지 갈 필요조차 없었다 - `populateBean()`이 필드를 하나씩 순서대로 처리하다가 첫 실패에서 바로 예외를 던지기 때문이다. 그리고 예상 밖이었던 것은, 이름이 틀린 경우와 아예 없는 경우의 실패 메시지를 나란히 비교해 보니 **완전히 동일한 문자열**이었다는 것 - "이름이 틀렸다"는 것을 컨테이너가 조금이라도 다르게 취급해 줄 거라는 막연한 기대가, 실제로는 "존재하지 않는 것과 100% 같다"는 사실 앞에서 완전히 깨졌다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `ConfigurableApplicationContext`의 실제 소스: `String CONVERSION_SERVICE_BEAN_NAME = "conversionService";`가 인터페이스 상수로 박혀 있다는 것을 확인했다 - 이건 관례가 아니라 코드에 하드코딩된 리터럴이라는 뜻이다. `@Qualifier`나 타입 매칭으로 우회할 방법이 없다.
- `AbstractApplicationContext#prepareBeanFactory`의 실제 소스: `beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) && beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)` 두 조건을 **모두** 만족해야 `setConversionService()`가 호출된다는 것을 확인했다 - 이름 확인이 타입 확인보다 먼저 온다(`containsBean`이 이름 기반이므로), 즉 이름이 조건의 출발점이다.
- `AbstractBeanFactory#getTypeConverter`의 실제 소스: 커스텀 `TypeConverter`가 설정돼 있지 않으면 매번 새 `SimpleTypeConverter`를 만들고 `typeConverter.setConversionService(getConversionService())`를 호출한다는 것을 확인했다 - `getConversionService()`가 `null`을 반환하면(=`setConversionService()`가 한 번도 호출된 적 없으면) 그 `SimpleTypeConverter`는 `ConversionService` 없이 만들어진다.
- `TypeConverterDelegate`의 실제 소스: `ConversionService conversionService = this.propertyEditorRegistry.getConversionService(); if (editor == null && conversionService != null && ... )`처럼, `ConversionService`가 `null`이면 그 분기 전체를 건너뛰고 곧장 레거시 `PropertyEditor` 경로로 넘어간다는 것을 확인했다 - `Point`에 맞는 `PropertyEditor`도 없으므로 최종적으로 `IllegalStateException`이 난다. 8번 절 예외 메시지의 정확한 출처다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `ConversionService`/`Converter` SPI 자체(전략 인터페이스 하나를 구현해서 등록한다)는 이 저장소가 여러 번 다뤄 온 "좁은 확장점 하나를 전략 객체로 갈아 끼운다"는 패턴과 다르지 않다 - 이번 주의 진짜 가치는 그 SPI 자체가 아니라, **컨테이너가 그 SPI를 찾아 연결하는 조건이 얼마나 좁고 눈에 안 띄는가**(정확한 문자열 하나)에 있었고, 그건 mini 구현보다 실제 실행 결과(특히 8번 절의 "메시지가 글자 그대로 같다"는 것)로 확인하는 쪽이 훨씬 설득력 있었다.

## 11. Spring 설계 의도

- **왜 타입 매칭이 아니라 정확한 이름 매칭인가**: `BeanFactory`에 `ConversionService` 타입의 빈이 여러 개 있을 수 있다 - 예를 들어 특정 모듈 전용으로 좁게 커스터마이징한 `ConversionService`를 `@Bean`으로 등록해 두고, 그건 그 모듈 코드에서만 명시적으로 주입받아 쓰고 싶은 경우다. 만약 컨테이너가 타입만으로 "이게 전역 기본 변환기다"라고 단정해 버리면, 그런 특수 목적의 `ConversionService` 빈이 하나라도 있는 순간 `NoUniqueBeanDefinitionException`류의 모호성 문제가 생기거나, 의도치 않게 그 특수 목적 빈이 전역으로 승격돼 버릴 위험이 있다. 정확한 이름(`"conversionService"`)을 계약으로 못 박아 두면, "이 이름을 쓰겠다"는 것 자체가 "나는 전역 기본 변환기가 되고 싶다"는 명시적 의사 표현이 된다 - 애매함을 남기지 않는 설계다.
- **왜 이름이 틀린 경우를 특별히 감지해서 경고해 주지 않는가**: `prepareBeanFactory()`는 `refresh()`의 아주 이른 단계(2번째)에서 실행되는데, 이 시점에는 아직 다른 빈들의 `BeanDefinition`조차 완전히 정리되지 않았을 수 있다 - "혹시 이름이 다른 `ConversionService` 타입 빈이 있는지" 전체를 스캔해서 경고하는 것은 이 단계의 책임을 넘어서는 일이다. 게다가 그런 검증은 "사용자가 정말 실수한 것"과 "사용자가 의도적으로 여러 `ConversionService`를 각각 다른 용도로 쓰고 있는 것"을 구분할 수 없다 - 후자를 방해하지 않으려면, 전자에 대한 친절한 경고도 함께 포기해야 한다. 8번 절의 "이름이 틀린 것과 아예 없는 것이 완전히 같다"는 관찰은, 이 설계가 "특별 취급하지 않는다"는 원칙을 정확히 지킨 결과다.
- **왜 `List<Integer>`는 커스텀 컨버터 없이도 되는가**: `DefaultConversionService`는 애플리케이션이 흔히 필요로 하는 변환(숫자·불리언·열거형·컬렉션 분리 등)을 처음부터 갖추고 있다 - `ConversionService` 자체를 "등록하는가"라는 이진 선택 하나만으로 이 모든 기본 변환이 한꺼번에 켜지거나 꺼진다. 이건 이번 저장소가 반복해서 봐 온 "필요한 것만 좁게 확장하게 한다"는 원칙과는 방향이 다르다 - 오히려 "기본값 하나를 등록하면 흔한 요구사항 대부분이 한 번에 해결된다"는, 확장성보다 개발 편의를 우선한 설계 선택이다. 그 대가가 바로 이번 실험 3번째 행이다: 그 "한 번에"를 못 받으면(이름을 틀리면), 원래 아무 문제 없이 되던 것(컬렉션 분리)까지 한꺼번에 멈춘다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 이름이 틀린 `ConversionService` 빈이 "이름이 없는 것보다는 나을 것"이라는 직관이 완전히 틀렸다 - 컨테이너 입장에서는 그 둘이 **글자 그대로 동일**하다. 타입이 정확히 일치하는 빈이 존재하는데도 아무 도움이 안 된다는 것은, "등록했다"와 "컨테이너가 그것을 찾을 수 있는 이름으로 등록했다"가 얼마나 다른 것인지를 극명하게 보여줬다.
- 예상 밖이었던 것: 커스텀 컨버터를 하나도 만들지 않아도 `List<Integer>` 변환이 된다는 것 - `ConversionService`를 "커스텀 타입 변환을 위한 도구"로만 생각했는데, 실제로는 `DefaultConversionService` 자체가 이미 상당히 많은 변환을 내장하고 있어서, 그 등록 여부 하나가 훨씬 넓은 범위의 변환 가능 여부를 좌우한다.
- 예상대로였던 것(재확인): 변환 실패가 애플리케이션 시작 자체를 막는다는 것 - `@Value` 필드 주입이 `refresh()`의 정규 단계(11번째, 싱글턴 생성) 안에서 일어난다는 이 저장소의 반복된 이해(3·4주차)가 그대로 들어맞았다.
- 새로 배운 것: 25~29번이 전부 "메서드 호출을 어떻게 가로채거나 새로 만드는가"였다면, 이번 주는 "값 하나가 어떻게 변환되는가"라는 완전히 다른 층위의 확장점이었다 - 그런데도 그 밑에 깔린 질문("이 확장점을 컨테이너가 인식하려면 정확히 무엇이 필요한가")은 같았다. `@EnableCaching`/`@EnableScheduling`/`@EnableAsync`가 애노테이션의 존재 여부로 확장점을 켰다면, `ConversionService`는 빈 **이름** 문자열 하나로 켠다 - 확장점을 활성화하는 신호의 형태(애노테이션 vs 명명 규약)도 기능마다 다르다는 것을 새로 확인했다.
