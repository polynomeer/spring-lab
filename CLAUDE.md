# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This repository is currently empty of source code (no build configuration, no commits). It is a learning lab for exploring Spring Framework internals — how the container works under the hood (bean creation/lifecycle, AOP proxies, transaction management, `DispatcherServlet` request handling, etc.) by reading the official docs/source alongside Spring, then reimplementing the core abstractions in reduced form, rather than just using Spring as a black box.

The full study plan lives in `docs/plan/`:
- [`docs/plan/00-methodology.md`](docs/plan/00-methodology.md) — the read → minimal example → interface → debug → test → reduced-implementation cycle, version pinning (Java 21 / Spring Boot 3.x / Spring Framework 6.2.x), source-reading rules, and the per-topic doc template.
- [`docs/plan/01-roadmap.md`](docs/plan/01-roadmap.md) — the week-by-week topic sequence (IoC container → bean lifecycle → extension points → component scan/`@Configuration` → DI → AOP → transactions → Spring MVC → optional Boot internals), each with official-docs references, key types, and deliverables.
- [`docs/plan/02-project-catalog.md`](docs/plan/02-project-catalog.md) — 32 concrete implementation projects with code skeletons, mapped to the roadmap weeks and split into required/deep-dive/portfolio tiers.

## Directory convention

Once code is scaffolded, keep it split by role rather than one monolithic app:
```text
experiments/       # real Spring used to verify/probe actual behavior
mini-spring/       # reduced from-scratch implementations of Spring's core abstractions
spring-extensions/ # code that uses Spring's real extension points (BFPP, BPP, ArgumentResolver, AutoConfiguration, ...)
sample-app/        # integrated application combining what's been learned
docs/<NN>-<topic>/ # per-topic analysis doc + diagrams, written per the template in docs/plan/00-methodology.md
```

No build tool or module layout (Gradle/Maven, single vs. multi-module, Java/Kotlin) has been chosen yet. When the first module is scaffolded, add build/test/run commands here (including how to run a single test).

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
