# Claude Code — project context

> Short entry point for [Claude Code](https://code.claude.com/docs). Does **not** duplicate `.cursor/rules`, `architecture.md`, or ADRs — it points at them.

## What this repo is

Opinionated **Spring Boot 3** + **Java 21** monorepo (Gradle Kotlin DSL): five platform modules + one sample service. Architecture is **hexagonal** (ports & adapters), with **strict immutability** in the domain, **soft multi-tenancy**, and **ArchUnit** enforcing boundaries. Root package: `com.pidabrow.starter`.

## Module map

```
platform-common           Spring Boot starter baseline — foundation for every other module
platform-data             JPA + PostgreSQL driver                (api: platform-common)
platform-web              Spring Web + Bean Validation           (api: platform-common)
platform-infrastructure   Kafka, ShedLock, Spring Security crypto, Jackson, Postgres runtime
                                                                 (api: platform-common)
platform-testing          Test fixtures: Spring Boot Test + Testcontainers (Postgres, Kafka)
                                                                 (testFixturesApi: platform-data)
sample-service            Reference Spring Boot app wiring all platform modules; adds Flyway,
                          json-patch; hosts ArchUnit tests and uses H2 + Awaitility in tests
```

**Dependency direction:** `sample-service` → `platform-*` (never reverse). Platform modules depend on `platform-common` only, except `platform-infrastructure` which is a peer consumer and `platform-testing` which pulls `platform-data` as test fixtures.

## Source of truth (read before larger changes)

| Topic | Location |
|---|---|
| Architecture overview (hexagonal, immutability, package-private adapters) | [`architecture.md`](architecture.md) |
| Architectural decisions (ADR-001…ADR-011) | [`docs/adr/`](docs/adr/) |
| Flow walkthroughs (e.g. transactional outbox) | [`docs/flows/`](docs/flows/) |
| Cursor rule packs (project standards by topic) | [`.cursor/rules/*.mdc`](.cursor/rules/) |

**Conflict resolution:** `architecture.md` → relevant ADR → `.cursor/rules`.

**Key ADRs to know by heart:**
- ADR-005 — Lightweight Hexagonal Architecture (the architectural baseline)
- ADR-002 — Soft Multi-Tenancy (every entity except `Tenant` has `tenantId`)
- ADR-007 — Transactional Outbox for External Integration
- ADR-009 — Platform Testing Infrastructure (Testcontainers, fixtures, multi-tenancy)
- ADR-011 — API Contract & Error Handling (RFC 7807, OpenAPI)

## When to consult which rule pack

`.cursor/rules/*.mdc` are not auto-loaded here. Before starting matching work, read the pack:

| Working on… | Read first |
|---|---|
| **Any** change (to declare classification: DOCS_ONLY / BUILD_ONLY / REFACTOR / BEHAVIOR_CHANGE) | `.cursor/rules/00-base.mdc` |
| Adding modules, moving code between layers, hexagonal boundaries | `.cursor/rules/10-repo-structure.mdc` |
| Writing Java/Spring code (records, sealed types, constructor injection, REST) | `.cursor/rules/20-java-spring-style.mdc` |
| JPA entities, repositories, migrations, tenancy, CQRS projections | `.cursor/rules/30-data-jpa.mdc` |
| Writing or modifying tests | `.cursor/rules/40-testing.mdc` |
| `.github/workflows/`, Gradle pipeline, before creating a PR | `.cursor/rules/50-ci-pipeline.mdc` |
| Auth, crypto, logging of PII, secrets handling | `.cursor/rules/60-security-privacy.mdc` |

If no pack matches, default to `architecture.md` + the closest ADR.

## Local verification

```bash
./gradlew check                           # compile + all tests (includes ArchUnit)
./gradlew test                            # tests only, repo-wide
./gradlew :sample-service:test            # tests for the sample service (where ArchUnit lives)
./gradlew :<module>:test                  # tests for a single module (narrowest loop)
./gradlew :sample-service:bootRun         # run the sample service locally
./gradlew build                           # check + assembly — run before declaring done
```

**No dedicated ArchUnit task.** ArchUnit runs inside `:sample-service:test` via `archunit-junit5`. After any structural change, run at least `:sample-service:test`; run the full `build` before finishing the task.

## Conventions

- **Root Java package:** `com.pidabrow.starter`
- **Dependency direction:** `sample-service` → platform modules only; platform modules never depend on `sample-service`
- **Domain immutability:** strict — no setters, records or final fields, state changes return new instances
- **No persistence leaking:** JPA entities are persistence-adapter internals; they never reach domain or application layers
- **Package visibility:** adapter implementations are `package-private`; only ports and DTOs are public
- **IDs:** UUID-based (see `.cursor/rules/30-data-jpa.mdc` / `architecture.md` for version specifics) — never expose sequential IDs in public APIs
- **Tenancy:** every entity except `Tenant` carries `tenantId`; tenant context is a security boundary resolved at the edge; **never** use ad-hoc `WHERE tenant_id = ?` for isolation — rely on the platform mechanism (ADR-002, ADR-006)

## Working agreements for Claude

- **Declare change classification upfront** per `00-base.mdc` before writing code: `DOCS_ONLY`, `BUILD_ONLY`, `REFACTOR`, or `BEHAVIOR_CHANGE`. Tests are mandatory unless the classification explicitly exempts them (`40-testing.mdc`).
- **ArchUnit is non-negotiable.** After any structural change (new package, moved class, new module dependency), run `:sample-service:test` before finishing.
- **Schema changes require an ADR.** If a change introduces a migration, stop and draft a new ADR first — don't write the migration ahead of the decision.
- **ADRs are historical record.** Do not edit files in `docs/adr/` to reflect new decisions — write a new ADR that supersedes the old one.
- **Branch hygiene.** Never commit directly to `main`. Work on `feature/…`, `fix/…`, or `chore/…` branches and land changes via PR with green CI (`50-ci-pipeline.mdc`).
- **Ask before inventing architectural intent.** If `architecture.md` and the ADRs don't cover a decision, surface the gap rather than picking a direction silently.
- **Prefer the narrowest verification loop** while iterating (`:<module>:test`), but always finish with `./gradlew build`.

---

*Living document. Update when module layout or top-level conventions change; otherwise put detail in ADRs or rule packs.*