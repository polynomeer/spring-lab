# @Import — 모든 @EnableXxx 애노테이션 뒤에 있는 세 가지 등록 전략

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`34`](../34-profile-condition/profile-condition.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. [`08-configuration-bean`](../08-configuration-bean/configuration-bean.md) 문서 스스로가 "`@Import`/`ImportSelector`/`ImportBeanDefinitionRegistrar`는 카탈로그의 '추가 실험' 목록에 있었지만 범위 밖으로 미뤘다"고 명시적으로 밝혀 둔 빈틈이다. `DeferredImportSelector`(18주차, 자동 설정)는 이미 깊이 다뤘지만, 그 상위 개념인 평범한 `@Import`와 `ImportSelector`/`ImportBeanDefinitionRegistrar`/`ImportAware` 자체는 아직 실행으로 확인한 적이 없었다 — 이 셋이 바로 `@EnableCaching`(25번)·`@EnableAsync`(29번)·`@EnableScheduling`(28번) 같은 모든 `@EnableXxx` 애노테이션이 내부적으로 쓰는 등록 메커니즘이다.

## 1. 이번 질문

- `@Import(ConcreteConfig.class)`처럼 클래스를 직접 주는 것과, `ImportSelector`/`ImportBeanDefinitionRegistrar`를 주는 것은 무엇이 다른가?
- `ImportSelector`가 반환한 클래스 이름들은 실제로 어떻게 처리되는가 - 일반 `@Configuration` 클래스와 똑같이 취급되는가?
- `ImportBeanDefinitionRegistrar`는 `ImportSelector`로는 할 수 없는 무엇을 할 수 있는가?
- `@Import`로 가져온 `@Configuration` 클래스가, 자신을 가져온 애노테이션(`@EnableXxx`)의 속성값을 읽고 싶으면 어떻게 하는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Composing Java-based Configurations")는 `@Import`가 `@Configuration` 클래스뿐 아니라 `ImportSelector`/`ImportBeanDefinitionRegistrar` 구현체도 받을 수 있다고 설명하고, `@EnableXxx` 스타일 애노테이션이 이 패턴 위에서 만들어진다고 언급한다.
- `ImportSelector` Javadoc은 `selectImports(AnnotationMetadata)`가 "가져올 클래스의 정규화된 이름 배열"을 반환한다고 설명하고, `ImportBeanDefinitionRegistrar` Javadoc은 `BeanDefinitionRegistry`에 직접 접근할 수 있다고 설명한다 - 하지만 왜 두 확장점이 함께 존재하는지, 언제 어느 쪽을 골라야 하는지는 다루지 않는다.
- `ImportAware`는 "`@Import`로 가져와진 `@Configuration` 클래스가 자신을 가져온 클래스의 `AnnotationMetadata`를 주입받고 싶을 때 구현한다"고 설명하지만, 그 주입이 생성자나 필드 자동 주입이 아니라 **별도의 콜백 시점**에 일어난다는 것은 이름만으로는 알 수 없다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- `ImportSelector`가 반환한 클래스 이름들은 뭔가 특별한 방식으로("이미 선택된 것"이니) 조건 평가 없이 바로 등록될 거라 예상했다 — **틀렸다.** 그 클래스들도 처음 등장한 `@Configuration` 클래스와 똑같이 `ConfigurationClassParser`를 처음부터 다시 거친다 - `@Conditional`도, 또 다른 `@Import`도 그 안에서 다시 평가된다.
- `ImportBeanDefinitionRegistrar`가 `ImportSelector`보다 "더 발전된" 상위 호환일 거라 예상했다(둘 다 애노테이션 속성을 읽을 수 있으니) — **부분적으로 틀렸다.** `ImportBeanDefinitionRegistrar`는 `BeanDefinitionRegistry`에 직접 접근해서 **빈 개수 자체를 런타임에 동적으로 결정**할 수 있다는, `ImportSelector`(정적으로 정해진 `@Configuration` 클래스 목록만 고를 수 있음)로는 표현할 수 없는 능력이 따로 있다.
- `@Import`로 가져온 `@Configuration` 클래스는 자신을 가져온 애노테이션의 속성값에 (어떤 형태로든) 자동으로 접근할 수 있을 거라 예상했다 — **틀렸다.** 아무것도 안 하면 그 클래스는 자신이 `@Import`로 가져와졌다는 사실 자체를 모른다 - `ImportAware`를 명시적으로 구현해야만, 그것도 생성자 시점이 아니라 **초기화 이후의 별도 콜백**으로 그 정보를 받는다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/import-selector-lab`](../../experiments/import-selector-lab)

```java
@Retention(RUNTIME) @Target(TYPE)
@Import(GreetingImportSelector.class)
public @interface EnableGreeting {
    String[] languages() default {};
}

public class GreetingImportSelector implements ImportSelector {
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        String[] languages = (String[]) importingClassMetadata
                .getAnnotationAttributes(EnableGreeting.class.getName()).get("languages");
        // languages 값에 따라 반환할 클래스 이름 배열이 달라짐
    }
}
```

```java
@Import(RepeatedGreetingRegistrar.class)
public @interface EnableRepeatedGreeting { int repeatCount() default 1; }

public class RepeatedGreetingRegistrar implements ImportBeanDefinitionRegistrar {
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        int repeatCount = (int) metadata.getAnnotationAttributes(...).get("repeatCount");
        for (int i = 0; i < repeatCount; i++) {
            registry.registerBeanDefinition("greeting" + i, ...);   // 개수가 런타임에 결정됨
        }
    }
}
```

```java
@Import(GreetingSettingsHolder.class)
public @interface ConfiguresGreeting { String prefix() default ""; }

@Configuration
public class GreetingSettingsHolder implements ImportAware {
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.prefix = (String) importMetadata.getAnnotationAttributes(...).get("prefix");
    }
}
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `@Import` | `@Configuration` 클래스, `ImportSelector`, `ImportBeanDefinitionRegistrar`를 섞어서 받을 수 있는 메타 임포트 애노테이션 |
| `ImportSelector` | `AnnotationMetadata`를 받아 "가져올 클래스 이름 배열"을 **동적으로 계산**해서 돌려주는 전략 - 반환된 클래스는 다시 `ConfigurationClassParser`를 거침 |
| `ImportBeanDefinitionRegistrar` | `AnnotationMetadata` + `BeanDefinitionRegistry`를 직접 받아 빈 정의를 **프로그래밍적으로** 등록하는 전략 - 중간에 또 다른 `@Configuration` 클래스가 필요 없음 |
| `ImportAware` | `@Import`로 가져와진 `@Configuration` 클래스가 자신을 가져온 클래스의 `AnnotationMetadata`를 받고 싶을 때 구현하는 콜백 인터페이스 |
| `ConfigurationClassPostProcessor.ImportAwareBeanPostProcessor` | `ImportAware` 구현체를 찾아 `setImportMetadata()`를 호출해 주는, `ConfigurationClassPostProcessor` 내부의 전용 `BeanPostProcessor` |
| `ConfigurationClassParser#processImports` | `@Import` 값 하나하나를 세 가지(`@Configuration`/`ImportSelector`/`ImportBeanDefinitionRegistrar`) 중 무엇인지 `instanceof`로 구분해서 각각 다르게 처리하는 지점 |

## 6. 호출 흐름

```text
ConfigurationClassParser#doProcessConfigurationClass (8주차의 @Configuration 파싱)
  → processImports(configClass, sourceClass, getImports(sourceClass), ..., checkForCircularImports=true)
      → for (SourceClass candidate : importCandidates) {
            if (candidate.isAssignable(ImportSelector.class)) {
                selector = instantiate(candidate)
                String[] importedClassNames = selector.selectImports(currentSourceClass.getMetadata())
                    (EnableGreeting을 붙인 클래스의 AnnotationMetadata를 그대로 넘김)
                → 반환된 클래스 이름들을 asSourceClasses()로 다시 SourceClass화
                → processImports(...)를 재귀 호출              ← 일반 @Configuration과 동일한 파싱 경로
            }
            else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
                registrar = instantiate(candidate)
                configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata())
                    (실제 등록은 나중에 ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsForConfigurationClass에서
                     registrar.registerBeanDefinitions(metadata, registry) 호출로 일어남)
            }
            else {
                // 평범한 @Configuration 클래스 - processConfigurationClass()로 그대로 파싱
                processConfigurationClass(candidate.asConfigClass(configClass), ...)
            }
        }

빈 인스턴스화 이후 (initializeBean 단계)
  → ImportAwareBeanPostProcessor#postProcessBeforeInitialization
      → bean이 ImportAware를 구현하면
          → importStack에서 이 클래스를 가져온 애노테이션의 AnnotationMetadata를 찾아
            bean.setImportMetadata(metadata) 호출
```

세 가지 임포트 전략이 갈리는 지점과 `ImportAware`의 별도 콜백 타이밍을 함께 그린 다이어그램: [`diagrams/import-mechanisms.md`](diagrams/import-mechanisms.md)

## 7. 브레이크포인트

25~34번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어떤 클래스가 등록되는가"라는 결과였고, 그건 실행 결과로 확인하는 쪽이 더 직접적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.context.annotation.ConfigurationClassParser#processImports
org.springframework.context.annotation.ConfigurationClassParser#getImports
org.springframework.context.annotation.ImportSelector#selectImports
org.springframework.context.annotation.ImportBeanDefinitionRegistrar#registerBeanDefinitions
org.springframework.context.annotation.ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor
```

## 8. 런타임 관찰

[`ImportMechanismTest`](../../experiments/import-selector-lab/src/test/java/lab/experiments/importselector/ImportMechanismTest.java) (6개):

| 실험 | 결과 |
| --- | --- |
| `@Import(PlainGreetingConfig.class)` | 그 설정 클래스의 빈이 그대로 등록 |
| `@EnableGreeting(languages = "en")` | `EnglishGreetingConfig`만 파싱됨 - `Greeting` 빈 1개(`"Hello"`) |
| `@EnableGreeting(languages = {"en", "ko"})` | 두 설정 클래스 모두 파싱됨 - `Greeting` 빈 2개 |
| `@EnableRepeatedGreeting(repeatCount = 3, message = "Hi")` | `Greeting` 빈이 정확히 3개(`"Hi-0"`, `"Hi-1"`, `"Hi-2"`) - `@Bean` 메서드를 3개 나열한 게 아니라 런타임에 개수가 결정됨 |
| 그 3개 빈의 이름 | `greeting0`/`greeting1`/`greeting2`로 예측 가능하게 생성됨, `greeting3`은 없음 |
| `@ConfiguresGreeting(prefix = "->")` | `GreetingSettingsHolder.prefix()`가 `"->"` - 생성자·필드 자동 주입이 전혀 없었는데도 값이 채워짐 |

**직접 겪은 것**: `ImportBeanDefinitionRegistrar` 실험을 설계하면서, 처음엔 "`ImportSelector`로도 `repeatCount`만큼 서로 다른 `@Configuration` 클래스 이름을 동적으로 만들어서 반환하면 되지 않을까"라고 생각했다 - 그런데 `ImportSelector`가 반환할 수 있는 건 **이미 존재하는 클래스의 이름**뿐이다. `repeatCount`가 3이든 30이든 그 개수만큼의 `@Configuration` 클래스가 컴파일 타임에 미리 존재해야 한다는 뜻이 되어 버린다 - 이 지점에서 두 확장점의 진짜 차이(정적으로 정해진 후보 중에서 고르는가, 아니면 그 자체로 빈 정의를 만들어 내는가)를 실감했다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `ConfigurationClassParser#processImports`의 실제 소스: `candidate.isAssignable(ImportSelector.class)`/`isAssignable(ImportBeanDefinitionRegistrar.class)`로 분기하고, `ImportSelector`가 돌려준 클래스들에 대해서는 `processImports(configClass, currentSourceClass, importSourceClasses, ..., false)`를 **재귀 호출**한다는 것을 확인했다 - "다시 파싱된다"는 6번 절의 근거다. 반면 `ImportBeanDefinitionRegistrar`는 `configClass.addImportBeanDefinitionRegistrar(registrar, ...)`로 그냥 목록에 추가만 되고, 재귀 파싱 대상이 아니다 - 그 자체가 이미 최종 등록 단계이기 때문이다.
- `getImports`의 실제 소스: `@Import`뿐 아니라 `@Import`가 메타 애노테이션으로 붙은 커스텀 애노테이션(`@EnableGreeting` 같은 것)까지 재귀적으로 훑어서 `@Import` 값을 찾아낸다는 것을 확인했다 - `@EnableGreeting`이 `@Import`를 직접 쓴 게 아니라 메타-애노테이션으로 갖고 있어도 문제없이 동작하는 이유다.
- `ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor`의 실제 소스: `postProcessBeforeInitialization()`에서 `ImportAware` 여부를 확인하고, 이 클래스를 가져온 임포트 스택 정보에서 대응하는 `AnnotationMetadata`를 찾아 `setImportMetadata()`를 호출한다는 것을 확인했다 - 생성자 인자나 `@Autowired` 필드가 아니라 **`BeanPostProcessor` 콜백 하나가 명시적으로 이 값을 밀어 넣어 준다**는 것이, "왜 자동으로 안 되는가"(3번 절)의 정확한 답이다.

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `mini-spring/mini-java-config`가 이미 `@Configuration`/`@Bean` 파싱이라는 골격을 다뤘고, 이번 주의 가치는 그 파싱 파이프라인이 "정적으로 나열된 설정 클래스"뿐 아니라 "런타임에 계산된 클래스 목록"(`ImportSelector`)과 "코드로 직접 등록하는 빈 정의"(`ImportBeanDefinitionRegistrar`)까지 같은 진입점(`@Import`)으로 흡수한다는 **확장성 설계** 자체에 있었다 - 이건 뼈대를 다시 만드는 것보다 실제 `@EnableXxx` 패턴을 직접 재현해서 그 유연성의 한계와 용도를 느껴 보는 쪽이 훨씬 직접적이었다.

## 11. Spring 설계 의도

- **왜 `ImportSelector`가 반환한 클래스는 처음부터 다시 파싱되는가**: `ImportSelector`가 고를 수 있는 후보 자체가 평범한 `@Configuration` 클래스이므로, 그 클래스 안에 또 다른 `@Import`나 `@Conditional`, `@ComponentScan`이 있을 수 있다 - 이걸 특별 취급해서 얕게만 처리하면, "이 클래스를 직접 `@Import`했을 때"와 "`ImportSelector`를 거쳐 간접적으로 가져왔을 때"의 동작이 달라지는 일관성 문제가 생긴다. 재귀적으로 같은 파이프라인을 태우는 것은, "어떤 경로로 왔는가와 무관하게 `@Configuration` 클래스는 항상 같은 규칙으로 처리된다"는 일관성을 지키기 위한 선택이다 - 18주차에서 본 `DeferredImportSelector`(자동 설정)도 결국 이 재귀 파싱 경로 위에 얹혀 있다.
- **왜 `ImportBeanDefinitionRegistrar`라는, 표면적으로 `ImportSelector`와 비슷해 보이는 확장점을 따로 두었는가**: `ImportSelector`의 계약(클래스 이름 배열)은 "이미 존재하는 설정 단위 중에서 고른다"는 한계를 갖는다 - 개수 자체가 동적으로 결정되는 경우(이번 실험의 `repeatCount`)나, 애노테이션 값에서 뽑아낸 이름으로 빈을 등록해야 하는 경우(예: `@MapperScan`류 애노테이션이 지정한 패키지의 인터페이스들을 스캔해서 빈으로 등록)는 `@Configuration` 클래스라는 정적 단위로 미리 표현할 수가 없다. `BeanDefinitionRegistry`에 대한 직접 접근을 내주는 것은, 이런 "설정 단위로 미리 못 쪼개는 등록 로직"을 위한 탈출구다.
- **왜 `ImportAware`는 생성자 주입이 아니라 별도 콜백인가**: `@Import`로 가져온 `@Configuration` 클래스가 만들어지는 시점에는, "누가 이 클래스를 가져왔는가"라는 정보가 일반적인 빈 의존성 그래프의 일부가 아니다 - 그건 `BeanDefinition`이 아니라 `ConfigurationClassParser`의 파싱 과정에서만 알 수 있는, 컨테이너 내부의 부가 정보다. 이 정보를 생성자 인자처럼 일반 DI 경로로 흘려보내려면 `BeanDefinition`과 별개의 새로운 전달 체계를 만들어야 했을 것이다 - 대신 Spring은 이미 있는 `BeanPostProcessor`라는 확장점 하나를 재사용해서, "이 특수한 메타데이터가 필요한 소수의 빈에게만" 초기화 이후 시점에 꽂아 준다 - 이 저장소가 반복해서 봐 온 "필요한 것만 좁게 확장한다"는 원칙의 또 다른 사례다.

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: `ImportSelector`가 돌려준 클래스가 "이미 선택이 끝난 결과물"이 아니라 "처음부터 다시 시작되는 파싱 대상"이라는 것 - `@Import`라는 하나의 진입점이 재귀적으로 자기 자신을 다시 부를 수 있다는 그림을 처음 봤을 때는 낯설었지만, 소스를 읽고 나니 오히려 그게 "간접적으로 가져온 설정도 직접 가져온 것과 똑같이 취급한다"는 일관성의 가장 단순한 구현이었다.
- 예상 밖이었던 것: `ImportBeanDefinitionRegistrar`가 `ImportSelector`의 "더 강력한 버전"이 아니라, 아예 다른 종류의 문제(개수가 정적으로 정해지지 않는 등록)를 위한 것이었다는 것 - 둘 다 `AnnotationMetadata`를 받는다는 표면적 유사성 때문에 처음엔 우열 관계로 오해했다.
- 예상대로였던 것(재확인): `@Import`로 가져온 클래스는 스스로를 가져온 애노테이션에 대해 아무것도 모른다는 것 - 26·28·29·34번에서 반복해서 본 "명시적으로 요청하지 않으면 확장 인프라는 조용히 관여하지 않는다"는 원칙이, 이번엔 "정보 접근"이라는 다른 축에서 다시 확인됐다.
- 새로 배운 것: 이 시리즈에서 다룬 `@EnableCaching`(25번)·`@EnableAsync`(29번)·`@EnableScheduling`(28번) 같은 애노테이션들이 각자 서로 다른 `BeanPostProcessor` 등록 전략(`AbstractAutoProxyCreator`, `AbstractAdvisingBeanPostProcessor`, 순수 리플렉션)을 가졌던 것과 별개로, "그 `BeanPostProcessor` 빈 자체를 컨테이너에 밀어 넣는 방법"은 결국 이번 주에 다룬 `@Import` 계열 메커니즘 중 하나로 수렴한다는 것 - 이 시리즈가 다뤄 온 서로 다른 확장점들이, 사실은 "어떻게 켜지는가"라는 훨씬 앞 단계에서는 같은 진입점을 공유하고 있었다.
