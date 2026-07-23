# 학습 방법론

Spring Framework 내부 구조를 공부하는 이 저장소의 기본 작업 방식은, 주제 하나마다 다음 순환을 완성하는 것이다.

```text
공식 문서 → 질문 하나 선정 → 최소 재현 예제 → 핵심 인터페이스 → 대표 구현체
→ 디버깅 → 공식 테스트 분석 → 축소 구현 → 설계 의도 정리
```

공식 문서를 처음부터 끝까지 읽거나 소스코드를 무작정 따라가는 방식은 피한다. 한 주제에 대해 이 순환을 한 번 완성하는 것이, 여러 주제를 얕게 훑는 것보다 낫다.

## Framework와 Boot를 분리해서 본다

`@SpringBootApplication` + `SpringApplication.run()`은 `ApplicationContext` 생성, 환경 로딩, 컴포넌트 스캔, 자동 설정, 빈 등록/생성, `BeanPostProcessor` 실행, 내장 서버 시작, MVC 인프라 등록을 한 번에 숨긴다.

- **Framework에서 볼 것**: `ApplicationContext`, `BeanFactory`, `BeanDefinition`, 빈 생성 생명주기, 의존성 주입, AOP 프록시, 트랜잭션, Spring MVC
- **Boot에서 볼 것**: `SpringApplication`, 자동 설정, 조건부 설정, Starter, 외부 설정, 내장 서버 초기화

Framework 내부 구조를 먼저 이해한 뒤 Boot를 본다. Boot는 Framework 위의 자동화 계층이므로, Framework을 모르면 Boot 코드는 조건 분기와 설정 코드의 나열처럼만 보인다.

## 버전 고정

기준 스택:

```text
Java 21
Spring Boot 3.x
Spring Framework 6.2.x
```

Spring Framework 소스를 내려받을 때는 `main`이 아니라 실제 사용 버전의 태그를 체크아웃한다.

```bash
git clone https://github.com/spring-projects/spring-framework.git
cd spring-framework
git checkout v6.2.x
```

정확한 패치 버전은 실제 프로젝트 의존성에서 확인한다.

```bash
./gradlew dependencies
# 또는
./mvnw dependency:tree
```

## 공식 문서를 읽는 3단계

1. **전체 지도 파악** — 세부 구현 대신 다음만 기록한다: 이 기능이 해결하는 문제, 주요 API, 핵심 인터페이스, 확장 포인트, 예외·제약 조건.
2. **예제와 함께 읽기** — 문서의 예제를 직접 실행하고, 그 예제에 대한 구체적 질문을 만든다 (예: `@Bean` 메서드는 언제 호출되는가?).
3. **소스코드와 연결** — 문서에 등장한 핵심 타입을 인터페이스 계층부터 순서대로 따라간다.

## 소스코드 읽기 규칙

- **인터페이스 → 추상 클래스 → 대표 구현체 → 전략 객체 → 보조 유틸리티** 순서로 읽는다. 구현체(예: `AbstractAutowireCapableBeanFactory`)부터 열지 않는다.
- **질문 하나만 정하고 읽는다.** "`AutowiredAnnotationBeanPostProcessor`를 전부 읽는다" 대신 "생성자 후보가 여러 개면 어떤 생성자를 선택하는가?"처럼 좁힌다.
- **정상 실행 경로만 먼저 따라간다.** 순서: 정상 경로 → 주요 예외 → 경계 조건 → 확장 포인트 → 호환성·최적화 코드.
- **호출 깊이를 제한한다.** 현재 질문과 무관한 Reflection·ClassLoader·JVM 내부까지 계속 따라 들어가지 않는다.
- **메서드를 세 종류로 구분한다**: 흐름 제어 메서드(`createBean`), 실제 작업 메서드(`doCreateBean`), 확장 포인트 메서드(`resolveBeforeInstantiation`).
- **클래스 전체를 완독하지 않는다.** 클래스 단위가 아니라 질문 단위로 코드를 본다.

## 디버깅

학습용 애플리케이션에서 컨텍스트를 직접 띄우고 브레이크포인트를 건다. 컨테이너 기본 주제라면 다음이 시작점이다.

```text
AbstractApplicationContext#refresh
AbstractBeanFactory#doGetBean
DefaultSingletonBeanRegistry#getSingleton
AbstractAutowireCapableBeanFactory#createBean
AbstractAutowireCapableBeanFactory#doCreateBean
AbstractAutowireCapableBeanFactory#populateBean
AbstractAutowireCapableBeanFactory#initializeBean
```

`refresh()`는 컨테이너 초기화의 중심 메서드다. 각 단계가 왜 존재하는지 한 줄씩 남긴다.

```text
invokeBeanFactoryPostProcessors  → 빈 생성 전에 BeanDefinition을 변경하는 단계
registerBeanPostProcessors       → 빈 생성 과정에 개입할 후처리기 등록
finishBeanFactoryInitialization  → 남아 있는 non-lazy singleton 빈 생성
```

IDE 디버거가 없는 환경(터미널만 있는 세션 등)에서는 `jdb`를 스크립트로 파이핑해도 브레이크포인트 히트와 명령 입력이 동기화되지 않아 제대로 멈추지 않는다. 이 경우 `tools/jdi-tracer`(자세한 사용법은 `CLAUDE.md`의 "Debugging" 절 참고)로 대상 클래스를 자식 JVM으로 띄워 브레이크포인트마다 스택과 지역 변수를 결정적으로 출력한다.

## 테스트 코드를 설명서로 활용한다

프로덕션 코드는 하위 호환성과 범용성 때문에 분기가 많다. 반대로 테스트는 입력, 기대 동작, 경계 조건, 과거 버그를 명확히 보여준다. 어떤 구현체를 공부했다면 같은 패키지의 테스트 클래스를 검색한다.

```text
1. 테스트 메서드 이름 읽기
2. 가장 작은 테스트 하나 선택
3. Given-When-Then 파악
4. 테스트 실행
5. 구현체 내부에 브레이크포인트 설정
6. 테스트를 조금 변경해보기
```

## 주제별 문서 템플릿

각 주제는 `docs/<NN>-<topic>/`에 아래 형식으로 분석 문서를 남긴다.

```markdown
# 주제

## 1. 이번 질문
## 2. 공식 문서 요약
## 3. 예상 동작 (소스를 보기 전에 작성)
## 4. 최소 재현 코드
## 5. 핵심 타입 (인터페이스 / 추상 클래스 / 대표 구현체 / 전략 인터페이스)
## 6. 호출 흐름
## 7. 브레이크포인트
## 8. 런타임 관찰 (객체 타입 / 프록시 여부 / 빈 상태 / ThreadLocal 상태 / 호출 순서)
## 9. 공식 테스트 분석
## 10. 축소 구현 (구현한 것 / 생략한 것)
## 11. Spring 설계 의도
## 12. 결론 (예상과 실제의 차이)
```

각 주제 폴더에는 최소 하나의 다이어그램(클래스 / 시퀀스 / 상태 전이 / 빈 생명주기 흐름 / 요청 처리 흐름)을 `diagrams/`에 남긴다.

## 완료 기준

한 주제는 다음 세 질문에 답할 수 있을 때 끝난 것으로 본다.

```text
이 기능은 어떤 문제를 해결하는가?
Spring은 어떤 추상화와 실행 순서로 해결하는가?
축소 구현은 무엇을 생략했고, 그로 인해 어떤 문제가 생길 수 있는가?
```

## 하지 말 것

- 클래스 하나를 처음부터 끝까지 완독하는 것
- IDE 호출 추적을 Reflection·ClassLoader·JVM 내부까지 무제한으로 따라가는 것
- 여러 주제를 한 번에 얕게 훑는 것
- 축소 구현을 Spring의 완전한 복제로 만드는 것 (핵심 추상화만 다루는 것이 목적)

세부 주차별 로드맵은 [`01-roadmap.md`](./01-roadmap.md), 구체적인 구현 프로젝트 목록과 코드 스켈레톤은 [`02-project-catalog.md`](./02-project-catalog.md)를 참고한다.
