# 컴포넌트 스캔 — 클래스패스를 뒤져서 BeanDefinition 후보를 찾아내는 방법

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 7주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 10(Mini Component Scanner)에 대응하는 분석 문서다. 같은 주제(컴포넌트 스캔 + 컬렉션 주입)를 실전 응용으로 다루는 프로젝트 11(Plugin Auto Discovery)은 나중에 별도로 진행해 13번에 이어 붙였다.

**후기**: 12번 절은 원래 "메타 애노테이션 지원과 ASM 기반 재구현은 지금 범위 밖으로 남겨 뒀다 - 실제로 구현해 보면 `Class.forName` 기반 스캐너와 성능·안전성 차이를 직접 측정해 볼 만하다"며 그 실측을 남겨 뒀다(`docs/retrospective/retrospective.md` 7번 절 "남겨 둔 질문"의 핵심 16주 목록 마지막 항목). mini-webmvc의 세 가지 생략과 `mini-observability-starter`의 Micrometer 연동을 채운 뒤, 이 저장소에 남아 있던 마지막 항목도 채웠다 - `AsmComponentScanner`를 추가해서 실제로 바이트코드만 읽는 스캐너를 만들고, 손으로 만든 "링크가 실패하는 비후보 클래스"로 안전성 차이를, 300개 합성 클래스로 로딩 개수·소요 시간 차이를 직접 측정했다. 그 과정에서 `ComponentScanner`의 실제 버그(`LinkageError`가 catch되지 않고 새어 나오는 것)도 발견해서 고쳤다. 5·8·10·11·12번 절에 그 내용을 반영했다.

## 1. 이번 질문

클래스패스는 어떻게 탐색하는가? 그 과정에서 모든 클래스를 실제로 로딩하는가? `@Component` 후보는 어떻게 판단하는가? 빈 이름은 어떻게 생성되는가? include filter와 exclude filter는 어떻게 함께 적용되는가?

## 2. 공식 문서 요약

- `ClassPathScanningCandidateComponentProvider`가 클래스패스 스캔의 핵심 엔진이고, `ClassPathBeanDefinitionScanner`가 그 결과를 실제 `BeanDefinition`으로 등록한다.
- 후보 판정은 `TypeFilter`(주석 기반 `AnnotationTypeFilter`, 타입 기반 `AssignableTypeFilter`, 정규식 기반 `RegexPatternTypeFilter` 등)의 조합으로 이뤄진다 — include filter들 중 하나라도 통과하고, exclude filter에는 전혀 걸리지 않아야 후보가 된다.
- `BeanNameGenerator`가 빈 이름을 결정한다. 기본 구현(`AnnotationBeanNameGenerator`)은 명시적 이름(`@Component("이름")`)이 없으면 클래스 simple name을 decapitalize한다 — 2주차에서 이미 확인한 규칙이다.
- 실제 클래스를 로딩하지 않고 바이트코드만 읽는 `MetadataReader`(ASM 기반)가 애노테이션 검사에 쓰인다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 클래스패스 스캔은 결국 클래스를 리플렉션으로 로딩해서 애노테이션을 검사할 것이라 예상했다 — 우리 구현은 실제로 그렇게 했다. Spring이 이걸 피한다는 것은 카탈로그의 "심화 과제"를 통해 미리 알고 있었지만, 정확히 어떤 방식(ASM)으로 피하는지는 이번에 소스로 처음 확인했다.
- include filter와 exclude filter가 동시에 걸리면 어느 쪽이 이길지 명확한 근거 없이 "exclude가 이길 것"이라고 가정하고 설계했다 — 실제로 공식 테스트로 정확히 이 가정이 맞다는 걸 확인했다(9번).
- 빈 이름 생성 규칙(decapitalize)은 2주차에서 이미 확인했으므로 예상대로였다.

## 4. 최소 재현 코드

[`mini-spring/mini-component-scan`](../../mini-spring/mini-component-scan)

```java
ComponentScanner scanner = new ComponentScanner();
scanner.addExcludeFilter(type -> Excludable.class.isAssignableFrom(type));

Map<String, Class<?>> found = scanner.scan("lab.minispring.scan.fixtures", "lab.minispring.scan.other");
// found.get("scannedComponent") == ScannedComponent.class
// found.get("customName")       == NamedComponent.class (명시적 이름)
```

전체 코드: [`ComponentScanner.java`](../../mini-spring/mini-component-scan/src/main/java/lab/minispring/scan/ComponentScanner.java)

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `ClassPathScanningCandidateComponentProvider` | 클래스패스를 뒤져 후보 `BeanDefinition`을 찾는 엔진 |
| `MetadataReader` | 클래스를 로딩하지 않고 바이트코드(ASM)만 읽어 애노테이션·타입 정보를 제공 |
| `TypeFilter` | include/exclude 판정 전략 (`AnnotationTypeFilter`, `AssignableTypeFilter`, `RegexPatternTypeFilter`) |
| `BeanNameGenerator` | 빈 이름 생성 전략 |
| `AnnotatedBeanDefinitionReader` | 스캔 결과가 아니라 명시적으로 등록하는 클래스(예: `context.register(AppConfig.class)`)를 위한 리더 — 스캐너와는 다른 진입점 |
| (mini) `ComponentScanner` | 디렉터리 순회 + 필터 + 이름 생성을 전부 우리 손으로 구현한 대응물 |
| (후기) `AsmComponentScanner` | `MetadataReader`에 대응 - ASM `ClassReader`로 바이트코드만 읽어 후보 여부를 판단하고, 후보로 판명된 것만 `Class.forName`으로 로딩 |

## 6. 호출 흐름

파이프라인 그림: [`diagrams/scan-pipeline.md`](diagrams/scan-pipeline.md)

```text
scan(basePackage...)
  → 각 패키지마다 findClasses()
      → classLoader.getResources(패키지 경로) 로 디렉터리 URL 수집
      → 디렉터리를 재귀적으로 순회하며 .class 파일 나열
      → Class.forName(name, initialize=false, classLoader) 로 로딩
  → isEligible(class)
      → 인터페이스·추상 클래스면 제외
      → (@MiniComponent 있음) OR (include filter 중 하나라도 일치) 여야 통과
      → exclude filter에 하나라도 걸리면 무조건 제외
  → resolveBeanName(class)
      → @MiniComponent.value()가 있으면 그 값
      → 없으면 BeanNameGenerator.generateName()
  → 중복 이름이면 DuplicateComponentNameException, 아니면 결과 Map에 누적
```

## 7. 브레이크포인트

이번 프로젝트는 순수 자바로 만든 축소 구현이 중심이라 Spring 내부를 디버깅할 대상이 없다. 대신 우리 코드가 실제 Spring의 어느 지점과 대응하는지는 5번 표에 정리했다. 다음은 향후 실제 Spring 스캔 과정을 추적하고 싶을 때의 후보다.

```text
org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#findCandidateComponents
org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#scanCandidateComponents
org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#isCandidateComponent
```

## 8. 런타임 관찰

[`ComponentScannerTest`](../../mini-spring/mini-component-scan/src/test/java/lab/minispring/scan/ComponentScannerTest.java)로 확인한 것:

| 실험 | 결과 |
| --- | --- |
| 기본 스캔 | `@MiniComponent`가 붙은 구체 클래스만, 이름은 decapitalize |
| `@MiniComponent("customName")` | 명시적 이름이 decapitalize 기본값을 이긴다 |
| 인터페이스/추상 클래스 | `@MiniComponent`가 붙어 있어도 제외됨 |
| 하위 패키지 | 재귀적으로 함께 스캔됨 |
| 서로 다른 패키지 여러 개 | 한 번의 `scan()` 호출로 전부 합쳐짐 |
| include filter | `@MiniComponent`가 없는 클래스도 필터 조건에 맞으면 후보에 추가됨 |
| exclude filter | `@MiniComponent`가 있어도 필터에 걸리면 제외됨 (include보다 항상 우선) |
| 이름 충돌 | 서로 다른 클래스가 같은 이름으로 귀결되면 `DuplicateComponentNameException` |
| 커스텀 `BeanNameGenerator` | 기본 decapitalize 대신 사용되지만, 애노테이션 명시 이름보다는 후순위 |

(후기) [`AsmComponentScannerTest`](../../mini-spring/mini-component-scan/src/test/java/lab/minispring/scan/AsmComponentScannerTest.java) (2개, `ComponentScanner`와 `AsmComponentScanner`를 같은 임시 디렉터리에 대해 나란히 돌린 비교 실험):

| 실험 | 결과 |
| --- | --- |
| `@MiniComponent`가 없고 존재하지 않는 슈퍼클래스를 참조하는(ASM `ClassWriter`로 직접 만든) 클래스 하나를 섞어서 스캔 | `ComponentScanner`는 그 클래스를 로딩(링크)하려다 `NoClassDefFoundError`로 스캔 전체가 실패한다(`ComponentScanException`으로 감싸짐, 아래 "직접 겪은 버그" 참고). `AsmComponentScanner`는 바이트코드만 보고 "`@MiniComponent`가 없다"는 걸 알아서, 그 클래스 이름을 `Class.forName`에 **단 한 번도 넘기지 않고** 정상 완료된다 - `RecordingClassLoader`로 실제 로딩 시도 자체를 기록해서 확인했다 |
| 300개 합성 클래스(그중 20개만 `@MiniComponent`) 스캔 | 둘 다 같은 20개를 찾아내지만(결과는 동일), `ComponentScanner`는 300개 전부를 `Class.forName`으로 로딩하고 `AsmComponentScanner`는 후보로 판명된 20개만 로딩한다. 실측 소요 시간(3회 측정, JVM 웜업 없는 단발성 실행 기준): reflection 21~24ms 대 ASM 13~15ms - 약 35~40% 빠르다. 흥미로운 부수 관찰: 둘 다 `java.lang.Object`를 딱 한 번 더 로딩하지만(첫 슈퍼클래스 해석이 캐싱됨), `ComponentScanner`만 `MiniComponent` 애노테이션 클래스 자체도 한 번 더 로딩한다(`isAnnotationPresent()`가 리플렉션으로 애노테이션 타입을 resolve해야 하기 때문) - `AsmComponentScanner`는 애노테이션 서술자 문자열만 비교하므로 그 클래스를 아예 로딩하지 않는다 |

**직접 겪은 버그(후기)**: 이 비교 테스트를 짜기 전까지, `ComponentScanner.scanDirectory()`의 `catch (ClassNotFoundException e)`는 `NoClassDefFoundError`(그 상위 타입인 `LinkageError`)를 잡지 못했다 - `Class.forName(name, false, classLoader)`는 `initialize=false`여도 JVMS 5.3에 따라 슈퍼클래스는 로딩 시점에 즉시 resolve해야 하므로, 슈퍼클래스가 없는 클래스 하나가 스캔 대상 패키지에 섞여 있으면 `ComponentScanException`이 아니라 catch되지 않은 `Error`가 그대로 `scan()` 밖으로 새어 나왔다. `LinkageError`도 함께 잡도록 고쳤다 - 다른 실패(`IOException` 등)와 마찬가지로 `ComponentScanException`으로 일관되게 감싸진다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19 소스의 `ClassPathScanningCandidateComponentProviderTests`로 확인했다.

- **`withNoFilters()`**: `useDefaultFilters=false`로 만들고 필터를 하나도 추가하지 않으면 `findCandidateComponents()`가 빈 결과를 반환한다 — 스캐너는 "필터에 맞는 것만" 찾지, 기본적으로 아무것도 후보가 아니다. 우리 `isEligible()`이 `@MiniComponent` 애노테이션도 일종의 "항상 켜져 있는 include 조건"으로 취급한 것과 같은 설계다.
- **`customSupportedIncludeAndExcludeFilterWithScan()`**: `addIncludeFilter(@Component)` + `addExcludeFilter(@Service)` + `addExcludeFilter(@Repository)`를 함께 걸면, `@Component`이면서 `@Service`/`@Repository`가 아닌 것만 후보로 남는다 — **exclude가 include보다 항상 이긴다**는 것을 정확히 검증한다. 우리 `isEligible()`의 "matches 판정 후 exclude로 다시 거른다" 순서가 이 테스트가 보여주는 것과 동일한 규칙이다.
- **`excludeFilterWithScan()`**: 정규식 기반 exclude filter(`RegexPatternTypeFilter`)로 이름에 `Named`가 들어간 클래스를 제외하는 것도 같은 exclude-wins 원칙의 다른 예다.

## 10. 축소 구현 (구현한 것 / 생략한 것)

**구현한 것**
- 디렉터리 기반 클래스패스 탐색(하위 패키지 재귀 포함), 여러 패키지 동시 스캔
- `@MiniComponent` 기반 기본 판정 + include/exclude `Predicate<Class<?>>` 필터, exclude 우선
- 명시적 이름 vs `BeanNameGenerator` 기본값
- 이름 충돌 감지

- **(후기에서 추가) `AsmComponentScanner`**: 실제 `MetadataReader`와 같은 접근 - ASM `ClassReader`로 클래스 파일의 바이트코드(클래스 modifier, `@MiniComponent` 애노테이션 서술자, 그 `value()`)만 읽어서 후보 여부를 판단하고, 후보로 판명된 것만 `Class.forName`으로 실제 로딩한다. `ComponentScanner`와 같은 `Map<String, Class<?>>` 계약을 그대로 지키므로 나란히 비교할 수 있다.

**생략한 것**
- 메타 애노테이션(스테레오타입) 지원 — `@Service`/`@Repository`처럼 `@Component`를 메타 애노테이션으로 붙인 커스텀 애노테이션까지 인식하는 것. 우리는 `@MiniComponent` 직접 부착만 인식한다.
- jar 파일 안의 클래스 스캔 — 지금은 로컬 파일시스템 디렉터리만 지원한다(테스트/개발 환경 한정).
- 인덱스 기반 스캔(`spring-context-indexer`의 `META-INF/spring.components`) — 컴파일 타임에 후보 목록을 미리 만들어 두는 최적화로, 아예 다루지 않았다.
- **(후기에서 추가) `AsmComponentScanner`는 커스텀 `Predicate<Class<?>>` include/exclude 필터를 지원하지 않는다** — 바이트코드만으로 판단할 수 있는 조건은 `@MiniComponent` 애노테이션과 클래스 modifier(구체/추상/인터페이스)뿐이다. 필터까지 로딩 없이 지원하려면 실제 Spring의 `TypeFilter`처럼 필터 자체가 `Class<?>` 대신 메타데이터(`AnnotationMetadata`)를 받는 형태로 다시 설계돼야 한다 - `ComponentScanner`가 이미 갖고 있던 필터 기능을 `AsmComponentScanner`로 그대로 옮기지 않은 것은, 두 스캐너의 "판단 대상"(로딩된 `Class` vs 바이트코드 메타데이터) 자체가 근본적으로 다르기 때문이다.

## 11. Spring 설계 의도

- **왜 ASM으로 바이트코드만 읽는가**: 애노테이션 하나 확인하자고 클래스를 로딩하면, 그 클래스의 static 초기화 블록이 실행되거나(우리는 `initialize=false`로 이건 피했다) 그 클래스가 참조하는 다른 클래스들까지 연쇄적으로 로딩·링크될 위험이 있다. 특히 스캔 대상 패키지에 아직 클래스패스에 없는 의존성을 참조하는 클래스가 섞여 있으면 `NoClassDefFoundError`로 애플리케이션 전체 기동이 막힐 수 있다. 바이트코드 레벨에서 애노테이션만 읽으면 이런 부작용 없이 "이 클래스가 후보인가"만 안전하게 판단할 수 있다.
- **왜 include와 exclude를 별도 리스트로 분리했는가**: "기본적으로 `@Component`인 것만 스캔하되, 특정 패키지의 특정 타입만 예외로 빼고 싶다"처럼 두 조건을 조합해서 쓰는 경우가 많다. 하나의 조건식으로 합치는 대신 두 리스트(그리고 "exclude가 항상 이긴다"는 단순한 규칙)로 나누면, 사용자는 "포함시킬 것"과 "그래도 빼고 싶은 것"을 독립적으로 선언할 수 있다.
- **왜 빈 이름 생성이 전략 인터페이스(`BeanNameGenerator`)로 분리돼 있는가**: 컴포넌트 스캔과 `@Bean` 메서드는 이름 결정 규칙이 다르다(전자는 클래스 이름, 후자는 메서드 이름). 이름 생성 규칙 자체를 전략으로 빼두면, 두 등록 경로가 서로 다른 규칙을 쓰면서도 "이름이 없으면 생성기에 위임한다"는 같은 골격을 공유할 수 있다.
- **(후기에서 추가) "연쇄적 클래스 로딩·링크의 부작용"이 추상적인 위험이 아니라 재현 가능한 버그였다**: 11주차 문서(118번)는 이 위험을 소스를 읽고 추론해서 적어 뒀을 뿐이었다. `AsmComponentScanner`를 만들면서 그 위험을 직접 재현해 보니(존재하지 않는 슈퍼클래스를 참조하는 클래스 하나), `ComponentScanner`가 그 클래스를 로딩하려다 스캔 전체가 `Error`로 죽는 것을 실제로 관찰했다 - 그리고 그 실패가 우리가 미리 약속한 `ComponentScanException`조차 아니라, catch 블록의 허점 때문에 새어 나오는 catch되지 않은 `Error`였다는 것까지 발견했다. "왜 ASM으로 읽는가"라는 질문에 대한 답이 이번엔 추론이 아니라 재현된 사고로 확인된 셈이다.

## 12. 결론 (예상과 실제의 차이)

- 예상대로였던 것: exclude filter가 include filter보다 항상 우선한다는 가정 — 공식 테스트로 정확히 확인됐다.
- 이번에 새로 구체화된 것: "Spring은 클래스를 로딩하지 않는다"는 것은 알고 있었지만, **정확히 어떤 방식(ASM 바이트코드 읽기)이고 왜 그렇게 해야 하는지**(연쇄적 클래스 로딩/링크의 부작용 회피)는 이번에 처음 정리했다. 우리 구현은 `initialize=false`로 절반만 흉내 냈다 — static 초기화는 피했지만 로딩·링크 자체는 여전히 발생한다.
- 새로 열린 질문(당시): 메타 애노테이션(스테레오타입) 지원과 ASM 기반 재구현은 지금 범위 밖으로 남겨 뒀다 — 실제로 구현해 보면 `Class.forName` 기반 스캐너와 성능·안전성 차이를 직접 측정해 볼 만하다.
- **(후기에서 추가) 그 질문을 실제로 열어 보니**: 성능 차이는 예상한 방향(ASM이 더 빠름, 실측 약 35~40%)대로였지만, 그 자체보다 더 중요했던 건 "무엇을 로딩하는가"의 차이였다 - `RecordingClassLoader`로 실제 로딩 시도를 기록해 보니, `ComponentScanner`는 후보가 아닌 클래스까지 전부(300개 중 300개) 로딩하는 반면 `AsmComponentScanner`는 후보로 판명된 것만(20개) 로딩했다. 그리고 그 실험을 준비하는 과정에서 전혀 찾을 생각이 없었던 진짜 버그(`LinkageError` catch 누락)를 우연히 발견해서 고쳤다 - "성능·안전성을 측정해 보자"는 계획이 계획대로 성능은 측정하게 해 줬지만, 안전성 쪽에서는 측정 대신 실제 결함을 찾아내는 결과로 이어졌다. 이것으로 7번 절 "남겨 둔 질문"의 "핵심 16주에서" 목록이 전부 채워졌다.

------

## 13. 추가 실험: 컴포넌트 스캔으로 만드는 플러그인 시스템 (프로젝트 11, Plugin Auto Discovery)

지금까지는 컴포넌트 스캔이 "빈을 찾아 등록한다"는 것 자체를 다뤘다 — [`sample-app/plugin-discovery-system`](../../sample-app/plugin-discovery-system)은 그 결과물을 실제로 어떻게 **활용**하는지, 즉 스캔으로 찾은 같은 인터페이스의 여러 구현체를 전략 패턴처럼 런타임에 골라 쓰는 실전 패턴을 확인한다. 카탈로그가 이 프로젝트를 "포트폴리오 구현"(실제 백엔드 프로젝트로 보여주기 좋은 대상) 등급으로 분류해 둔 이유이기도 하다.

### 최소 재현 코드

```java
public interface NotificationPlugin {
    String type();
    void send(NotificationMessage message);
    default boolean enabled() { return true; }
}
```

`SlackNotificationPlugin`(`@Order(1)`, 우선순위는 가장 높지만 `enabled() = false` — webhook 미설정을 가정), `EmailNotificationPlugin`(`@Order(2)`), `SmsNotificationPlugin`(`@Order(3)`) 셋 다 `@Component`로 스캔된다. [`NotificationPluginRegistry`](../../sample-app/plugin-discovery-system/src/main/java/lab/sampleapp/plugindiscovery/NotificationPluginRegistry.java)가 생성자로 `List<NotificationPlugin>`을 통째로 받아서, `type()`을 키로 하는 자신만의 `Map`을 다시 만든다.

```java
public NotificationPluginRegistry(List<NotificationPlugin> plugins) {
    this.orderedPlugins = List.copyOf(plugins);
    Map<String, NotificationPlugin> map = new LinkedHashMap<>();
    for (NotificationPlugin plugin : plugins) {
        NotificationPlugin existing = map.putIfAbsent(plugin.type(), plugin);
        if (existing != null) {
            throw new IllegalStateException("duplicate NotificationPlugin type '" + plugin.type() + "': " + ...);
        }
    }
    this.byType = Map.copyOf(map);
}
```

### 실제로 확인한 것

[`PluginDiscoveryTest`](../../sample-app/plugin-discovery-system/src/test/java/lab/sampleapp/plugindiscovery/PluginDiscoveryTest.java) (8개):

| 실험 | 결과 |
| --- | --- |
| 패키지 스캔으로 플러그인 3개 발견 | `context.getBeansOfType(NotificationPlugin.class)`가 3개 |
| Spring이 자동으로 주입해 주는 `Map<String, NotificationPlugin>`의 키 | **빈 이름**("emailNotificationPlugin" 등)이지, 우리가 정의한 `type()`("email")이 아니다 — 처음엔 `type()`이 키일 거라 예상했는데 틀렸다. 그래서 `NotificationPluginRegistry`가 따로 필요하다 |
| `List<NotificationPlugin>` 생성자 주입 | `@Order` 값(1→slack, 2→email, 3→sms) 순서 그대로 도착 — enabled 여부와 무관하게 발견된 전부가, 정렬된 채로 |
| `registry.firstEnabled()` | 우선순위 1위인 `slack`은 `enabled()=false`라 건너뛰고 `email`을 반환 — "발견됨"과 "지금 쓸 수 있음"이 다르다는 걸 실제로 보여준다 |
| `type()`이 같은 두 플러그인으로 레지스트리 생성 | 생성자에서 즉시 `IllegalStateException` — 등록 자체가 아니라 **레지스트리를 조립하는 시점**에 걸린다(빈 등록은 이미 다 끝난 뒤이므로, `BeanDefinitionRegistry` 수준의 충돌은 아니다) |
| `registry.dispatch("email", message)` | 해당 플러그인의 `send()`가 실제로 호출됨(`SentMessageLog`로 확인) |
| `registry.dispatch("slack", message)`(비활성) | 조용히 무시하지 않고 `IllegalStateException`을 던진다 — 비활성 플러그인 호출을 침묵 실패로 두지 않겠다는 설계 선택 |
| `registry.dispatch("fax", message)`(미등록 타입) | `IllegalArgumentException` |

### Spring 설계 의도

`Map<String, T>` 자동 주입의 키가 빈 이름으로 고정된 이유는, 그 메커니즘이 애초에 "타입 T의 빈들을 이름별로 구분해 달라"는 범용 요청이기 때문이다 — Spring 컨테이너는 `NotificationPlugin`이 `type()`이라는 자체 식별자를 갖고 있다는 걸 알 방법이 없다(그건 우리 도메인 개념이다). 그래서 "빈 이름이 아닌 다른 기준으로 색인하고 싶다"는 요구는 컨테이너가 대신해 줄 수 없고, 항상 애플리케이션 코드(이 경우 `NotificationPluginRegistry`)가 스스로 조립해야 한다 — `List<T>` + `@Order`까지는 컨테이너가 정렬까지 해 주지만, 그 이상의 색인/조회 구조는 언제나 한 걸음 더 나아간 이용자 몫이라는 원칙을 보여준다.

### 남겨둔 것

- 활성화 여부를 외부 설정(`Environment`/프로퍼티)에서 읽어오지 않고 `enabled()`를 코드에 고정했다 — 이 실험의 초점이 "활성화 상태가 DI 후보 선택에 영향을 주지 않는다"는 것 자체라, 설정 바인딩까지 더하면 초점이 흐려진다고 판단했다(프로젝트 4의 YAML 바인딩과는 다른 문제).
- `firstEnabled()`가 폴백 체인(1순위 실패 시 2순위 시도)까지 하지는 않는다 — 지금은 "가장 먼저 발견된, 켜져 있는 플러그인 하나"만 고른다.
