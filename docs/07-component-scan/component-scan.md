# 컴포넌트 스캔 — 클래스패스를 뒤져서 BeanDefinition 후보를 찾아내는 방법

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md) 7주차, [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md) 프로젝트 10(Mini Component Scanner)에 대응하는 분석 문서다.

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

**생략한 것**
- **ASM 기반 메타데이터 읽기** — 가장 큰 격차다. 우리는 후보 여부를 판단하기 위해 `Class.forName()`으로 클래스를 로딩(링크)한 뒤에야 애노테이션을 검사한다. Spring은 `MetadataReader`로 클래스 파일의 바이트코드만 읽어서, 후보가 아닌 것으로 판명되면 그 클래스는 JVM에 전혀 로딩되지 않는다.
- 메타 애노테이션(스테레오타입) 지원 — `@Service`/`@Repository`처럼 `@Component`를 메타 애노테이션으로 붙인 커스텀 애노테이션까지 인식하는 것. 우리는 `@MiniComponent` 직접 부착만 인식한다.
- jar 파일 안의 클래스 스캔 — 지금은 로컬 파일시스템 디렉터리만 지원한다(테스트/개발 환경 한정).
- 인덱스 기반 스캔(`spring-context-indexer`의 `META-INF/spring.components`) — 컴파일 타임에 후보 목록을 미리 만들어 두는 최적화로, 아예 다루지 않았다.

## 11. Spring 설계 의도

- **왜 ASM으로 바이트코드만 읽는가**: 애노테이션 하나 확인하자고 클래스를 로딩하면, 그 클래스의 static 초기화 블록이 실행되거나(우리는 `initialize=false`로 이건 피했다) 그 클래스가 참조하는 다른 클래스들까지 연쇄적으로 로딩·링크될 위험이 있다. 특히 스캔 대상 패키지에 아직 클래스패스에 없는 의존성을 참조하는 클래스가 섞여 있으면 `NoClassDefFoundError`로 애플리케이션 전체 기동이 막힐 수 있다. 바이트코드 레벨에서 애노테이션만 읽으면 이런 부작용 없이 "이 클래스가 후보인가"만 안전하게 판단할 수 있다.
- **왜 include와 exclude를 별도 리스트로 분리했는가**: "기본적으로 `@Component`인 것만 스캔하되, 특정 패키지의 특정 타입만 예외로 빼고 싶다"처럼 두 조건을 조합해서 쓰는 경우가 많다. 하나의 조건식으로 합치는 대신 두 리스트(그리고 "exclude가 항상 이긴다"는 단순한 규칙)로 나누면, 사용자는 "포함시킬 것"과 "그래도 빼고 싶은 것"을 독립적으로 선언할 수 있다.
- **왜 빈 이름 생성이 전략 인터페이스(`BeanNameGenerator`)로 분리돼 있는가**: 컴포넌트 스캔과 `@Bean` 메서드는 이름 결정 규칙이 다르다(전자는 클래스 이름, 후자는 메서드 이름). 이름 생성 규칙 자체를 전략으로 빼두면, 두 등록 경로가 서로 다른 규칙을 쓰면서도 "이름이 없으면 생성기에 위임한다"는 같은 골격을 공유할 수 있다.

## 12. 결론 (예상과 실제의 차이)

- 예상대로였던 것: exclude filter가 include filter보다 항상 우선한다는 가정 — 공식 테스트로 정확히 확인됐다.
- 이번에 새로 구체화된 것: "Spring은 클래스를 로딩하지 않는다"는 것은 알고 있었지만, **정확히 어떤 방식(ASM 바이트코드 읽기)이고 왜 그렇게 해야 하는지**(연쇄적 클래스 로딩/링크의 부작용 회피)는 이번에 처음 정리했다. 우리 구현은 `initialize=false`로 절반만 흉내 냈다 — static 초기화는 피했지만 로딩·링크 자체는 여전히 발생한다.
- 새로 열린 질문: 메타 애노테이션(스테레오타입) 지원과 ASM 기반 재구현은 지금 범위 밖으로 남겨 뒀다 — 실제로 구현해 보면 `Class.forName` 기반 스캐너와 성능·안전성 차이를 직접 측정해 볼 만하다.
