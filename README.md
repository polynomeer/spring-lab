# spring-internals-lab

[![Build](https://github.com/polynomeer/spring-internals-lab/actions/workflows/build.yml/badge.svg)](https://github.com/polynomeer/spring-internals-lab/actions/workflows/build.yml)

Spring Framework/Boot 내부 구조를 공식 문서·소스코드·디버깅으로 직접 확인하며, 핵심 추상화를 축소된 형태로 재구현해 보는 학습 저장소다. Spring을 블랙박스로 쓰는 대신, "왜 이렇게 동작하는가"를 실행 결과와 실제 릴리스 소스로 검증한다.

## 왜 이 저장소인가

Spring을 오래 써 왔어도 "IoC 컨테이너가 정확히 언제 빈을 만드는가", "AOP 프록시는 self-invocation을 왜 못 잡는가", "`@Transactional`은 애노테이션 하나로 어떻게 JDBC `Connection`을 관리하는가" 같은 질문에는 소스코드 수준으로 답하기 어려운 경우가 많다. 이 저장소는 20주 분량의 로드맵을 따라 이런 질문들을 하나씩 닫아 나간 기록이다.

각 주차는 다음 순환을 한 번씩 완성한다.

```text
공식 문서 → 질문 → 최소 예제 → 인터페이스 → 구현체 → 디버깅 → 공식 테스트 → 축소 구현 → 설계 의도 정리
```

## 시작점

- [`docs/retrospective/retrospective.md`](docs/retrospective/retrospective.md) — 20주 전체를 가로지르는 회고. 반복된 설계 패턴, 구현하다 실제로 발견한 버그들, 로드맵이 던진 핵심 질문에 대한 답을 정리했다. **처음 온다면 여기부터.**
- [`docs/plan/01-roadmap.md`](docs/plan/01-roadmap.md) — 주차별 로드맵과 진행 상황 표.
- [`docs/plan/02-project-catalog.md`](docs/plan/02-project-catalog.md) — 32개 구현 프로젝트의 코드 스켈레톤과 완료 현황.
- [`docs/plan/00-methodology.md`](docs/plan/00-methodology.md) — 위 순환 방법론과 매주 문서 템플릿.
- `docs/01-*` ~ `docs/20-*` — 주차별 분석 문서(각 12개 절 + mermaid 다이어그램).

## 진행 현황

핵심 8단계(1~16주차: IoC 컨테이너 → 빈 생명주기 → 확장점 → 컴포넌트 스캔/DI → AOP → 트랜잭션 → Spring MVC)와 선택 과정(17~20주차: Spring Boot 내부 - `SpringApplication`/자동 설정/조건부 설정/Starter 직접 구현)까지 20주 전부 완료. 22개 코드 모듈, 217개 자동화 테스트 전부 통과.

## 디렉터리 구조

```text
experiments/       # 실제 Spring/Boot로 동작을 검증하는 실험 모듈
mini-spring/       # Spring 핵심 추상화를 축소 재구현한 모듈
spring-extensions/ # 실제 Spring 확장점(BeanPostProcessor, ArgumentResolver, AutoConfiguration 등)을 활용하는 코드
tools/             # 이 저장소 자체를 위한 도구(JDI 기반 브레이크포인트 트레이서)
docs/<NN>-<topic>/ # 주차별 분석 문서 + diagrams/
docs/plan/         # 방법론·로드맵·프로젝트 카탈로그
docs/retrospective/ # 20주 전체 회고
```

## 빌드/테스트

Java 21, Gradle(Kotlin DSL), JUnit 5 + AssertJ.

```bash
./gradlew build                                                              # 전체 빌드 + 테스트
./gradlew :experiments:ioc-container-lab:test                                 # 모듈 하나만 테스트
./gradlew :mini-spring:mini-container:test --tests "*SimpleBeanFactoryTest"    # 테스트 클래스 하나만
```

자세한 버전 고정, 디버깅 도구(`tools/jdi-tracer`) 사용법, 커밋 컨벤션은 [`CLAUDE.md`](CLAUDE.md)를 참고한다.
