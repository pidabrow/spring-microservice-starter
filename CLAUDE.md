# Claude Code — project context

> Short entry point for [Claude Code](https://code.claude.com/docs). Does **not** duplicate `.cursor/rules`, `architecture.md`, or ADRs — it points at them.

## What this repo is

Opinionated **Spring Boot 3** + **Java 21** monorepo (Gradle Kotlin DSL): platform modules + sample service. Architecture is **hexagonal** (ports & adapters), with **strict immutability** in the domain, **soft multi-tenancy**, and **ArchUnit** enforcing boundaries.

## Module map

<!-- TODO: fill from `settings.gradle.kts` + per-module `build.gradle.kts`. Keep each line to one purpose. -->

```
platform/
  <module-a>/         # <one-line purpose>
  <module-b>/         # <one-line purpose>
  <module-c>/         # <one-line purpose>
sample-service/       # reference service wiring platform modules together
```

**Dependency direction:** `sample-service` → `platform/*` only. Platform modules never depend on `sample-service` and only on each other per the layering in `architecture.md`.

## Source of truth (read before larger changes)

| Topic | Location |
|---|---|
| Architecture overview | [`architecture.md`](architecture.md) |
| Architectural decisions | [`docs/adr/`](docs/adr/) |
| Platform testing (Testcontainers, fixtures) | [`docs/adr/ADR-009__platform-testing-infrastructure.md`](docs/adr/ADR-009__platform-testing-infrastructure.md) |
| Cursor rule packs (standards encoded per topic) | [`.cursor/rules/*.mdc`](.cursor/rules/) |

**Conflict resolution:** `architecture.md` → relevant ADR → `.cursor/rules`.

## When to consult which rule pack

`.cursor/rules/*.mdc` are not auto-loaded here. Read the relevant pack **before** starting the matching kind of work:

<!-- TODO: one row per `.mdc` file actually present. Remove rows that don't exist. -->

| Working on… | Read first |
|---|---|
| Writing/modifying tests | `.cursor/rules/<testing>.mdc` |
| JPA entities, repositories, migrations | `.cursor/rules/<jpa>.mdc` |
| CI / Gradle / build scripts | `.cursor/rules/<ci>.mdc` |
| Security-sensitive code (auth, tenant isolation, crypto) | `.cursor/rules/<security>.mdc` |
| <other pack> | `.cursor/rules/<other>.mdc` |

If no pack matches the task, default to `architecture.md` + the closest ADR.

## Local verification

<!-- TODO: confirm these commands match `./gradlew tasks` output. Replace placeholders. -->

From repo root:

```bash
./gradlew build                     # full verification (compile + tests + ArchUnit)
./gradlew test                      # unit tests only
./gradlew <archUnitTaskName>        # architectural constraints only
./gradlew :sample-service:bootRun   # run the sample service locally
./gradlew :<module>:test            # tests for a single module
```

Use the narrowest command that proves the change. Run `./gradlew build` before declaring work done on anything structural.

## Conventions

- **Root Java package:** `com.pidabrow.starter`
- **Dependency direction:** `sample-service` → platform modules only (never reverse; platform modules layered per `architecture.md`)
- **IDs:** UUID v7 for <!-- TODO: where? public resources? entities? clarify or delete --> ; never sequential IDs in public APIs
- **Tenancy:** tenant context resolved at the edge; **never** rely on ad-hoc `WHERE tenant_id = ?` for isolation — use the platform mechanism (see ADR <!-- TODO: number -->)
- **Domain immutability:** strict; no setters, no in-place mutation

## Working agreements for Claude

- **ArchUnit is non-negotiable.** After any structural change (new package, moved class, new module dependency), run the ArchUnit task before finishing.
- **Schema changes require an ADR.** If a change introduces a migration, stop and propose an ADR draft before writing the migration.
- **ADRs are historical record.** Do not edit files in `docs/adr/` to reflect new decisions — write a new ADR that supersedes the old one.
- **Ask before inventing architectural intent.** If `architecture.md` and ADRs don't cover a decision, surface the gap rather than picking a direction silently.
- **Prefer the narrowest verification loop** while iterating (single-module test), but always finish with `./gradlew build`.

---

*Living document. Update when module layout or top-level conventions change; otherwise leave it alone and put detail in ADRs or rule packs.*
