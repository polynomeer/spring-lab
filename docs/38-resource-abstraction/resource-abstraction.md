# Resource 추상화 — 접두어 없는 경로 하나가 컨텍스트 구현체에 따라 완전히 다르게 해석된다

[`docs/plan/01-roadmap.md`](../plan/01-roadmap.md)의 핵심 16주 + 선택 4주 커리큘럼과 [`docs/plan/02-project-catalog.md`](../plan/02-project-catalog.md)의 32개 프로젝트는 이미 전부 완료됐다 — 이 문서도 [`25`](../25-cache-abstraction/cache-abstraction.md)~[`37`](../37-aware-callbacks/aware-callbacks.md)번과 마찬가지로 그 카탈로그 밖의 심화 주제다. `@PropertySource`(`classpath:application.properties` 같은 문자열), 컴포넌트 스캔(내부적으로 `ResourcePatternResolver`를 씀), `ResourceLoaderAware`(37번에서 다룸)까지 이 저장소가 여러 번 스쳐 지나간 `Resource` 추상화 자체를 처음으로 정면에서 다룬다. `AnnotationConfigApplicationContext`만 써 온 이 저장소에서는 한 번도 드러나지 않았던, "접두어 없는 경로"가 컨텍스트 구현체마다 다르게 해석된다는 사실을 확인한다.

## 1. 이번 질문

- `context.getResource("어떤/경로")`처럼 접두어(`classpath:`, `file:` 등)가 없는 경로는 어떻게 해석되는가 - 항상 클래스패스 기준인가?
- `classpath:`를 명시하면 컨텍스트 구현체가 무엇이든 항상 같은 결과를 주는가?
- jar 안에 들어 있는 클래스패스 리소스는 `Resource`의 모든 메서드를 똑같이 지원하는가?
- `classpath*:`와 와일드카드(`*`)를 함께 쓰면 실제로 여러 파일을 한 번에 찾아낼 수 있는가?

## 2. 공식 문서 요약

- Spring Framework 레퍼런스("Resources")는 `Resource`가 `URL`의 한계(클래스패스 상대 경로나 `ServletContext` 상대 경로를 표현 못 함)를 극복하기 위한 추상화라고 설명하고, `classpath:`/`classpath*:`/`file:`/`http:` 같은 접두어로 위치를 명시할 수 있다고 설명한다.
- 문서는 "접두어가 없으면 `ApplicationContext` 구현체에 따라 그 기본 해석이 달라진다"고 짧게 언급하지만, 정확히 어떤 구현체가 무엇으로 해석하는지 표로 정리해 주지는 않는다 — 이번 실험은 그 표를 직접 실행으로 채웠다.
- `getFile()`이 "리소스가 파일 시스템에 없으면 예외를 던질 수 있다"고 API 문서에 명시돼 있지만, "jar 안의 리소스"가 그 대표적인 경우라는 것은 예제 없이는 체감하기 어렵다.

## 3. 예상 동작 (소스를 보기 전에 작성)

- 접두어 없는 경로는 `ApplicationContext` 종류와 무관하게 항상 클래스패스 기준일 거라 예상했다(이 저장소가 지금까지 계속 그렇게 동작하는 걸 봐 왔으므로) — **틀렸다.** `AnnotationConfigApplicationContext`(그리고 그 상위의 `GenericApplicationContext`, `ClassPathXmlApplicationContext`)는 클래스패스 기준이지만, `FileSystemXmlApplicationContext`는 같은 접두어 없는 문자열을 **파일 시스템 상대 경로**로 해석한다.
- `getFile()`은 클래스패스 리소스라면 항상 정상적으로 동작할 거라 예상했다 — **틀렸다.** 클래스패스 리소스가 실제로는 jar 파일 내부에 있을 수 있고, 그런 경우 `getFile()`은 `FileNotFoundException`을 던진다 - `getInputStream()`은 여전히 정상 동작하는데도 그렇다.
- `classpath*:`와 `classpath:`가 결과에서 큰 차이가 없을 거라 예상했다(둘 다 "클래스패스에서 찾는다"는 점은 같으니) — 이번 실험은 그 차이(여러 클래스패스 루트에 걸친 집계)까지는 직접 재현하지 않았지만, 와일드카드 패턴(`*.properties`) 자체가 여러 파일을 한 번에 찾아낸다는 것은 확인했다.

## 4. 최소 재현 코드

**실제 Spring** — [`experiments/resource-lab`](../../experiments/resource-lab)

```java
// 같은 문자열, 다른 컨텍스트 구현체 → 다른 Resource 타입
new AnnotationConfigApplicationContext().getResource("lab/experiments/resource/greeting.txt");
// → ClassPathResource

new FileSystemXmlApplicationContext().getResource("lab/experiments/resource/greeting.txt");
// → FileSystemResource (실제 파일은 없으므로 exists() == false)

new FileSystemXmlApplicationContext().getResource("classpath:lab/experiments/resource/greeting.txt");
// → 명시적 접두어가 컨텍스트 기본값을 덮어씀 → ClassPathResource
```

```java
Resource resource = new ClassPathResource("org/springframework/context/annotation/Configuration.class");
resource.getInputStream();   // 정상 - spring-context-6.2.19.jar 안에서 스트림을 읽음
resource.getFile();          // FileNotFoundException - jar 안이라 실제 File이 없음
```

```java
Resource[] resources = new PathMatchingResourcePatternResolver()
        .getResources("classpath*:lab/experiments/resource/data/*.properties");
// → 3개 (a.properties, b.properties, c.properties)
```

## 5. 핵심 타입

| 타입 | 책임 |
| --- | --- |
| `Resource` | `URL`의 한계를 넘어서는 리소스 추상화 - `exists()`/`getInputStream()`/`getFile()`/`getURL()` 등 |
| `ResourceLoader` | 문자열 위치를 `Resource`로 변환하는 전략 - `getResource(String)` 메서드 하나 |
| `DefaultResourceLoader#getResourceByPath` | 접두어 없는 경로의 기본 해석 방식(`ClassPathContextResource`) - `protected`라서 하위 클래스가 오버라이드 가능 |
| `AbstractXmlApplicationContext`/`FileSystemXmlApplicationContext` | `getResourceByPath()`를 오버라이드해서 접두어 없는 경로를 `FileSystemResource`로 해석 |
| `ClassPathResource` | 클래스로더 또는 클래스 기준 상대 경로로 리소스를 찾음 - jar 내부/외부를 가리지 않고 `getInputStream()`은 지원하지만 `getFile()`은 실제 파일 시스템 항목일 때만 지원 |
| `ResourcePatternResolver`/`PathMatchingResourcePatternResolver` | `classpath*:`와 Ant 스타일 와일드카드(`*`, `**`)를 지원하는 확장 - 컴포넌트 스캔(7주차)이 내부적으로 쓰는 바로 그 클래스 |

## 6. 호출 흐름

```text
context.getResource(location)
  → location이 특정 접두어(예: "classpath:")로 시작하는가?
      예 → 그 접두어에 대응하는 Resource 구현체로 직접 생성(예: ClassPathResource)
      아니오 → location이 유효한 URL 형식인가(file:, http: 등 URL로 파싱 가능)?
          예 → UrlResource
          아니오 (평범한 상대 경로 문자열) → getResourceByPath(location) 호출
              → DefaultResourceLoader의 기본 구현: new ClassPathContextResource(path, classLoader)
              → FileSystemXmlApplicationContext가 오버라이드한 버전: new FileSystemResource(path)
                  (이 지점 하나가 "같은 문자열, 다른 컨텍스트 = 다른 결과"의 전부다)

resource.getFile()
  → 이 Resource가 실제로 로컬 파일 시스템의 File 하나에 대응하는가?
      ClassPathResource가 jar 밖의 평범한 디렉터리를 가리키면 → 예, File 반환
      ClassPathResource가 jar 안의 항목을 가리키면        → 아니오, FileNotFoundException
  (getInputStream()은 이 구분과 무관하게 항상 클래스로더의 스트림 열기로 동작)

resolver.getResources("classpath*:.../*.properties")
  → PathMatchingResourcePatternResolver가 패턴을 "루트 디렉터리 부분"과 "와일드카드 부분"으로 분리
  → 루트 디렉터리 자체를 먼저 Resource로 확보(들)
  → 그 안에서 와일드카드에 매칭되는 파일들을 전부 순회하며 Resource 배열로 반환
```

접두어 없는 경로가 컨텍스트 구현체에 따라 갈리는 지점과, jar 안/밖 리소스의 `getFile()` 지원 여부를 함께 그린 다이어그램: [`diagrams/resource-resolution-paths.md`](diagrams/resource-resolution-paths.md)

## 7. 브레이크포인트

25~37번과 같은 이유로 `tools/jdi-tracer`로 직접 추적하지는 않았다 - 핵심이 "어떤 타입의 `Resource`가 나오는가"라는 결과였고, `instanceof` 확인과 `exists()`로 직접 검증하는 쪽이 더 결정적이었다. 대신 다음 지점을 실제 릴리스 소스(`spring-framework-src`, v6.2.19 로컬 체크아웃)로 직접 읽었다.

```text
org.springframework.core.io.DefaultResourceLoader#getResourceByPath
org.springframework.context.support.FileSystemXmlApplicationContext#getResourceByPath
org.springframework.core.io.ClassPathResource#getFile (상위 AbstractFileResolvingResource 경유)
org.springframework.core.io.support.PathMatchingResourcePatternResolver#getResources
```

## 8. 런타임 관찰

[`ResourceAbstractionTest`](../../experiments/resource-lab/src/test/java/lab/experiments/resource/ResourceAbstractionTest.java) (5개):

| 실험 | 결과 |
| --- | --- |
| `AnnotationConfigApplicationContext.getResource("경로")`(접두어 없음) | `ClassPathResource`, 실제 존재하는 테스트 리소스를 정상적으로 읽음 |
| `FileSystemXmlApplicationContext.getResource("경로")`(같은 경로, 접두어 없음) | `FileSystemResource` - 실제 파일이 없으므로 `exists() == false` |
| `FileSystemXmlApplicationContext.getResource("classpath:경로")`(명시적 접두어) | 컨텍스트 기본값과 무관하게 `ClassPathResource`, 정상적으로 읽힘 |
| `spring-context` jar 안의 실제 `.class` 파일을 `ClassPathResource`로 | `exists()`/`getInputStream()` 정상, `getFile()`은 `FileNotFoundException` |
| `PathMatchingResourcePatternResolver`로 `*.properties` 와일드카드 조회 | 미리 배치해 둔 3개 파일 모두 발견 |

**직접 겪은 것**: 두 번째 실험(`FileSystemXmlApplicationContext`)을 설계할 때, 처음엔 실제로 존재하는 파일(예: 프로젝트의 `build.gradle.kts`)을 가리켜서 `exists() == true`까지 확인하려 했다 - 그런데 Gradle 테스트 태스크의 작업 디렉터리가 정확히 어디인지에 의존하게 되어 다른 환경에서 깨질 수 있는 테스트가 될 위험이 있었다. 이 실험의 핵심은 "파일이 실제로 존재하는가"가 아니라 "**같은 문자열이 어떤 타입으로 해석되는가**"였으므로, `exists()` 대신 `instanceof FileSystemResource`만 확인하도록 다시 설계했다 - 정확히 무엇을 증명하려는 실험인지를 다시 좁히고 나서야 견고한 테스트가 됐다.

## 9. 공식 테스트 분석

`spring-framework` v6.2.19의 로컬 체크아웃(`~/Study/spring-framework-src`)으로 확인했다.

- `AbstractApplicationContext`의 클래스 레벨 Javadoc 원문을 그대로 확인했다: "Implements resource loading by extending `DefaultResourceLoader`. Consequently treats non-URL resource paths as class path resources ... unless the `getResourceByPath` method is overridden in a subclass." - "오버라이드하지 않는 한"이라는 단서가 정확히 이번 실험의 전제다.
- `FileSystemXmlApplicationContext#getResourceByPath`의 실제 소스: `new FileSystemResource(path)`를 그대로 반환한다는 것을 확인했다 - 슬래시로 시작하면 그것만 잘라내는 사소한 정규화 외에는 별다른 로직이 없다. "오버라이드 하나"가 전체 해석 방식을 바꾼다는 것을 코드 세 줄로 확인했다.
- `ClassPathResource`의 `getFile()`이 상위 클래스(`AbstractFileResolvingResource`)의 구현을 통해 `getURL()`이 가리키는 위치가 `file:` 프로토콜인지 확인하고, `jar:`처럼 다른 프로토콜이면 `FileNotFoundException`을 던진다는 것을 확인했다 - "jar 안에 있으면 안 된다"는 게 특별 케이스가 아니라, "`file:` 프로토콜이 아니면 전부 안 된다"는 훨씬 일반적인 규칙의 한 사례일 뿐이라는 것도 함께 확인했다(예를 들어 `http:` 리소스도 마찬가지로 `getFile()`이 안 됨).

## 10. 축소 구현 (이번 주는 생략)

이번 주제도 별도의 mini 구현을 만들지 않았다. `Resource`/`ResourceLoader`의 핵심 아이디어(문자열 위치 → 추상화된 접근 객체로 변환) 자체는 복잡한 골격이 필요한 주제가 아니다 - 이번 주의 가치는 그 변환 규칙이 컨텍스트 구현체마다, 그리고 리소스가 jar 안에 있는지 밖에 있는지에 따라 갈리는 **구체적인 경계선**을 실행으로 확인하는 데 있었다.

## 11. Spring 설계 의도

- **왜 접두어 없는 경로의 해석을 `protected` 메서드 하나(`getResourceByPath`)로 열어 뒀는가**: `ApplicationContext`의 "자연스러운" 리소스 기준점은 그 컨텍스트의 성격에 따라 다르다 - XML 기반의 파일 시스템 배포 환경(`FileSystemXmlApplicationContext`)에서는 파일 시스템 경로가 더 자연스럽고, 클래스패스 기반 애플리케이션(`AnnotationConfigApplicationContext`)에서는 클래스패스가 더 자연스럽다. 이 "기본값"을 하드코딩하지 않고 오버라이드 가능한 좁은 지점 하나로 분리해 둔 것은, 접두어를 매번 명시하지 않아도 되는 편의성(각 컨텍스트에 맞는 합리적인 기본값)과, 필요하면 언제든 명시적 접두어로 그 기본값을 무시할 수 있는 유연성을 동시에 얻기 위한 설계다.
- **왜 `getFile()`은 jar 내부 리소스에서 실패하는가**: `Resource` 추상화의 목적 자체가 "물리적 위치(파일 시스템 경로, jar 안의 항목, URL 등)를 몰라도 되게 한다"는 것이다 - `getInputStream()`은 이 약속을 정확히 지킨다(어디에 있든 스트림만 열어 주면 됨). 반면 `getFile()`은 그 약속을 깨고 "실제 로컬 `File` 객체"라는, 물리적 위치가 파일 시스템이라는 것을 전제로 하는 훨씬 구체적인 계약을 요구한다 - jar 안의 리소스는 그 전제 자체를 만족할 수 없으므로, 이 메서드만 실패하는 것은 버그가 아니라 애초에 이 메서드가 요구하는 조건이 `getInputStream()`보다 훨씬 좁기 때문이다.
- **왜 `classpath*:`라는 별도 접두어가 필요한가**: 클래스로더의 `getResource(String)`(단수)는 클래스패스 검색 순서상 **첫 번째로 찾은** 항목 하나만 돌려주도록 정의돼 있다 - 여러 jar나 디렉터리에 같은 상대 경로의 파일이 있어도 그중 하나만 보인다. 반면 `getResources(String)`(복수)은 그 이름을 가진 **모든** 항목을 열거해 준다. `classpath:`(단수 의미)와 `classpath*:`(복수 의미)를 별도 접두어로 구분해 둔 것은, "이 위치의 리소스가 클래스패스 어디엔가 유일하게 있다고 가정할 것인가, 아니면 여러 군데 흩어져 있을 수 있다고 가정할 것인가"라는 서로 다른 전제를 사용자가 명시적으로 선택하게 하는 설계다 - 스프링 부트의 `META-INF/spring.factories`류 메커니즘이 항상 `classpath*:`를 쓰는 이유이기도 하다(여러 jar가 각자의 `META-INF/spring.factories`를 가질 수 있으므로).

## 12. 결론 (예상과 실제의 차이)

- 가장 크게 달랐던 것: 접두어 없는 문자열 하나가 컨텍스트 구현체에 따라 완전히 다른 물리적 위치를 가리킬 수 있다는 것 - 이 저장소는 지금까지 `AnnotationConfigApplicationContext`만 써 왔기 때문에 이 차이 자체를 볼 기회가 없었다. "지금까지 항상 이렇게 동작했다"는 관찰이, 사실은 "지금까지 한 가지 구현체만 써 왔다"는 훨씬 좁은 전제 위에 있었다는 걸 이번에 깨달았다.
- 예상 밖이었던 것: `getFile()`의 실패가 예외적인 특수 케이스가 아니라, "`file:` 프로토콜이 아니면 전부 실패한다"는 훨씬 넓고 단순한 규칙의 한 사례였다는 것 - jar 안 리소스만 특별 취급되는 게 아니라, 원격 URL 등 파일 시스템이 아닌 모든 위치가 똑같은 이유로 실패한다.
- 예상대로였던 것(재확인): 명시적 접두어(`classpath:`)가 컨텍스트의 기본 해석을 항상 이긴다는 것 - "명시적으로 요청하면 그게 최우선이고, 아무것도 안 하면 합리적인 기본값이 대신한다"는, 이 저장소가 25~37번 내내 반복해서 봐 온 설계 원칙이 여기서도 그대로 확인됐다.
- 새로 배운 것: `classpath:`와 `classpath*:`의 차이가 단순한 문법적 취향이 아니라, "클래스로더의 단수/복수 조회 API가 원래 이렇게 다르게 동작한다"는 자바 표준 라이브러리 수준의 사실을 그대로 반영한 것이라는 점 - Spring이 새로 발명한 구분이 아니라, 이미 있던 비대칭(`getResource` vs `getResources`)에 접두어라는 이름을 붙여 드러낸 것뿐이었다.
