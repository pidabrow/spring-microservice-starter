# Claude Code — project context (draft)

> **Status:** Draft. This file gives [Claude Code](https://code.claude.com/docs) a short entry point. It intentionally **does not** duplicate `.cursor/rules` or `architecture.md` — read those for full detail.

## What this repo is

Opinionated **Spring Boot 3** + **Java 21** monorepo (Gradle Kotlin DSL): **platform modules** + **sample service**. Architecture is **hexagonal** (ports & adapters), with **strict immutability** in the domain, **soft multi-tenancy**, and **ArchUnit** enforcing boundaries.

## Source of truth (read before larger changes)

| Topic | Location |
|--------|----------|
| Architecture overview | [`architecture.md`](architecture.md) |
| Architectural decisions | [`docs/adr/`](docs/adr/) |
| IDE / Cursor rule packs (testing, JPA, CI, security, …) | [`.cursor/rules/*.mdc`](.cursor/rules/) |
| Platform testing (Testcontainers, fixtures) | [`docs/adr/ADR-009__platform-testing-infrastructure.md`](docs/adr/ADR-009__platform-testing-infrastructure.md) |

If guidance conflicts, prefer **`architecture.md`** and the relevant **ADR**, then `.cursor/rules`.

## Local verification

From the repo root:

```bash
./gradlew build
```

Use `./gradlew test` when iterating on tests only.

## Conventions (summary)

- **Root Java package:** `com.pidabrow.starter`
- **Dependency direction:** `sample-service` → platform modules only (never the reverse)
- **IDs:** UUID (v7 where specified in rules), not sequential public IDs in APIs
- **Tenancy:** tenant context at the edge; do not rely on ad-hoc `WHERE` for isolation

## Relationship to Cursor rules

[`.cursor/rules/*.mdc`](.cursor/rules/) are written for **Cursor** but encode the same project standards. When working in the terminal, treat them as **authoritative checklists** alongside `architecture.md` — avoid restating them here to prevent drift.

---

*End of draft.*
