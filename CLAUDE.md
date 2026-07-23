# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is a learning lab for exploring Spring Framework internals — how the container works under the hood (bean creation/lifecycle, AOP proxies, transaction management, `DispatcherServlet` request handling, etc.) by reading the official docs/source alongside Spring, then reimplementing the core abstractions in reduced form, rather than just using Spring as a black box.

The full study plan lives in `docs/plan/`:
- [`docs/plan/00-methodology.md`](docs/plan/00-methodology.md) — the read → minimal example → interface → debug → test → reduced-implementation cycle, version pinning (Java 21 / Spring Boot 3.x / Spring Framework 6.2.x), source-reading rules, and the per-topic doc template.
- [`docs/plan/01-roadmap.md`](docs/plan/01-roadmap.md) — the week-by-week topic sequence (IoC container → bean lifecycle → extension points → component scan/`@Configuration` → DI → AOP → transactions → Spring MVC → optional Boot internals), each with official-docs references, key types, and deliverables.
- [`docs/plan/02-project-catalog.md`](docs/plan/02-project-catalog.md) — 32 concrete implementation projects with code skeletons, mapped to the roadmap weeks and split into required/deep-dive/portfolio tiers.

## Directory convention

Code is split by role rather than one monolithic app. Each leaf directory is its own Gradle subproject (`experiments:ioc-container-lab`, `mini-spring:mini-container`, ...), declared in `settings.gradle.kts`:
```text
experiments/       # real Spring used to verify/probe actual behavior
mini-spring/       # reduced from-scratch implementations of Spring's core abstractions
spring-extensions/ # code that uses Spring's real extension points (BFPP, BPP, ArgumentResolver, AutoConfiguration, ...)
sample-app/        # integrated application combining what's been learned
docs/<NN>-<topic>/ # per-topic analysis doc + diagrams, written per the template in docs/plan/00-methodology.md
```

Adding a new project from the catalog: create the directory under the right role, add it to `settings.gradle.kts`, add a `build.gradle.kts` only if it needs dependencies beyond what `build.gradle.kts` (root) already applies to all subprojects (Java 21 toolchain, JUnit 5, AssertJ).

## Build / test

Stack: Java 21, Gradle (Kotlin DSL), JUnit 5 + AssertJ. `mini-spring` modules have no Spring dependency by design; `experiments` and `spring-extensions` modules depend on `org.springframework:spring-context` (currently 6.2.19).

```bash
./gradlew build                                                              # build + test everything
./gradlew :experiments:ioc-container-lab:test                                 # test a single module
./gradlew :mini-spring:mini-container:test --tests "*SimpleBeanFactoryTest"    # a single test class
```

Modules don't apply the `application` plugin, so `main()` classes (e.g. `BeanFactoryLab`) are run from the IDE, not via a `./gradlew run` task.

If `./gradlew` picks the wrong JDK (this machine's default `gradle`/`java` on `PATH` may be a newer JDK than 21), point `JAVA_HOME` at a JDK 21 install before invoking it.

## Commit convention

Use [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <subject>`.

- `subject` in the imperative mood, lowercase, no trailing period.
- `scope` is optional — the affected module/dir (`docs`, `mini-spring`, `experiments`, ...).
- Body (optional, blank line after subject): explain *why*, not what — the diff already shows what.

Types:
```text
feat     new capability (a new experiment/mini-spring feature/etc.)
fix      bug fix
docs     documentation only (docs/, CLAUDE.md, README)
refactor code change that neither fixes a bug nor adds a feature
test     adding or correcting tests
chore    tooling, config, dependency bumps
build    build system or module structure changes
```

One logical change per commit. Keep subject under ~72 chars.
