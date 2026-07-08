# CLAUDE.md — Project Rules & Context

> Single source of truth for AI coding agents working in this repository. Read it in full before making non-trivial changes.
>
> This file is read natively by both **Claude Code** (as `CLAUDE.md`) and **Cursor**. Cursor's own CLI documentation and Rules documentation state that a root-level `CLAUDE.md` is read the same way as `AGENTS.md` — always applied to every conversation, regardless of any rule-level configuration — and this is confirmed for the Cursor CLI. If you're relying on this in the Cursor IDE specifically, double-check **Cursor Settings → Rules** to confirm `CLAUDE.md` shows up as an applied rule for this workspace. There is no other rules file in this repository — do not reintroduce `.cursor/rules/*.mdc` or an `AGENTS.md` duplicate of this content.

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
| ADR template & migration rules | [45 — ADR Policy](#45--adr-policy) |
| Flow walkthroughs (e.g. transactional outbox) | [`docs/flows/`](docs/flows/) |
| Project rules, by topic | sections below (`00`–`60`) |

**Conflict resolution:** `architecture.md` → relevant ADR → this file.

**Key ADRs to know by heart:**
- ADR-005 — Lightweight Hexagonal Architecture (the architectural baseline)
- ADR-002 — Soft Multi-Tenancy (every entity except `Tenant` has `tenantId`)
- ADR-007 — Transactional Outbox for External Integration
- ADR-009 — Platform Testing Infrastructure (Testcontainers, fixtures, multi-tenancy)
- ADR-011 — API Contract & Error Handling (RFC 7807, OpenAPI)

## Which section to read first

| Working on… | Read first |
|---|---|
| **Any** change (to declare classification: DOCS_ONLY / BUILD_ONLY / REFACTOR / BEHAVIOR_CHANGE) | [00 — Base Rules (HARD)](#00--base-rules-hard-change-classification) |
| Adding modules, moving code between layers, hexagonal boundaries | [10 — Repository Structure](#10--repository-structure-hexagonal) |
| Writing Java/Spring code (records, sealed types, constructor injection, REST) | [20 — Java & Spring Boot Standards](#20--java--spring-boot-standards) |
| JPA entities, repositories, migrations, tenancy, CQRS projections | [30 — Data & JPA Rules](#30--data--jpa-rules) |
| Writing or modifying tests | [40 — Testing Policy](#40--testing-policy) |
| Writing a new ADR or migrating an existing one to the canonical format | [45 — ADR Policy](#45--adr-policy) |
| `.github/workflows/`, Gradle pipeline, before creating a PR | [50 — CI / Pipeline Rules (HARD)](#50--ci--pipeline-rules-hard) |
| Auth, crypto, logging of PII, secrets handling | [60 — Security & Privacy Rules](#60--security--privacy-rules) |

If no section matches, default to `architecture.md` + the closest ADR.

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
- **IDs:** UUID-based (see [30 — Data & JPA Rules](#30--data--jpa-rules) / `architecture.md` for version specifics) — never expose sequential IDs in public APIs
- **Tenancy:** every entity except `Tenant` carries `tenantId`; tenant context is a security boundary resolved at the edge; **never** use ad-hoc `WHERE tenant_id = ?` for isolation — rely on the platform mechanism (ADR-002, ADR-006)

## Working agreements

- **Declare change classification upfront** per [00 — Base Rules (HARD)](#00--base-rules-hard-change-classification) before writing code: `DOCS_ONLY`, `BUILD_ONLY`, `REFACTOR`, or `BEHAVIOR_CHANGE`. Tests are mandatory unless the classification explicitly exempts them ([40 — Testing Policy](#40--testing-policy)).
- **ArchUnit is non-negotiable.** After any structural change (new package, moved class, new module dependency), run `:sample-service:test` before finishing.
- **Schema changes require an ADR.** If a change introduces a migration, stop and draft a new ADR first — don't write the migration ahead of the decision.
- **ADRs are historical record.** Do not edit files in `docs/adr/` to reflect new decisions — write a new ADR that supersedes the old one. Format-only migrations performed under the adr-format skill (`.claude/skills/adr-format/SKILL.md`) are exempt from this rule, provided the decision content is preserved exactly and only the structure changes.
- **Ask before inventing architectural intent.** If `architecture.md` and the ADRs don't cover a decision, surface the gap rather than picking a direction silently.
- **Prefer the narrowest verification loop** while iterating (`:<module>:test`), but always finish with `./gradlew build`.

---

# 00 — Base Rules (HARD, Change Classification)

## Source of truth
- Read and follow `/architecture.md`
- Architectural decisions live in `/docs/adr`
- If a change conflicts with ADRs, propose an ADR update instead of coding around it

## Change classification (MANDATORY)
Every change MUST be classified as exactly one of:
- DOCS_ONLY
- BUILD_ONLY
- REFACTOR
- BEHAVIOR_CHANGE

If uncertain, default to BEHAVIOR_CHANGE.

## Branch pre-flight (MANDATORY)

Before creating, editing, or deleting any file — even for a single-file or `DOCS_ONLY`
change — check the current branch (`git branch --show-current`). If it is `main` or
`master`, stop and create + checkout a new branch (`feature/*`, `fix/*`, `chore/*`) before
making any changes. This applies even when a task involves editing many files in sequence:
create the branch once, at the very start, before the first file edit — do not wait until
the work is done to discover you're still on `main`.

**Branch from up-to-date main.** Before cutting the new branch, update local `main` first
(`git checkout main && git pull`, or `git fetch origin && git reset --hard origin/main` on
`main` only — never on a feature branch). Do not branch off a stale local `main`.

**One branch, one task.** Do not reuse an already-existing feature branch for a new,
unrelated task, even if it's still checked out from a previous step in the same session.
Each distinct task gets its own branch cut from current `main`. Before starting a new task,
switch back to `main`, pull latest, and only then create the next branch. Two unrelated
changes must never land as sequential commits on the same branch.

## Non-negotiables
- Keep changes minimal and focused
- Prefer explicit, readable code over magic
- Do not introduce new libraries/frameworks without an ADR
- Do not add configuration-driven abstractions

## Definition of Done (global)
- Code compiles
- Relevant tests updated and passing (per [40 — Testing Policy](#40--testing-policy))
- CI pipeline passes
- Git workflow and PR rules in [50 — CI / Pipeline Rules (HARD)](#50--ci--pipeline-rules-hard) followed (dedicated branch, PR into `main`)
- No weakening of existing rules or checks

## Failure handling
- Never work around failing CI
- Never disable tests or checks to make CI green

---

# 10 — Repository Structure (Hexagonal)

## Modules
- Sample service depends on platform modules
- Platform modules must NEVER depend on the sample service
- Avoid generic "utils/common" dumping grounds

## Hexagonal boundaries (sample service)
Organize code as Ports & Adapters:

### Inbound adapters
- `api` (REST controllers, request/response DTOs)

### Inbound ports (application)
- `application.port.in` (use case interfaces, if useful)
- `application.usecase` (implementations, orchestration, transactions, event publishing)

### Domain
- `domain` (entities, domain rules; avoid Spring dependencies)

### Outbound ports
- `application.port.out` (persistence ports, audit ports, etc.)

### Outbound adapters
- `infrastructure.persistence` (JPA implementations)
- `infrastructure.events` (listeners, event wiring)
- `infrastructure.*` (external integrations)

Rule:
- Inbound ports must not depend on inbound adapters.
- Domain must not depend on adapters.
- Adapters depend inward; never the reverse.

## Naming
- Root package: `com.pidabrow.starter`
- Avoid generic class names (`Application`, `ServiceImpl`, `Utils`)
- Prefer descriptive names (`CreateUserUseCase`, `TenantContext`, `AuditEventListener`)

## Documentation
- New modules require an update to `/architecture.md`
- **Architecture as Code**: Use ArchUnit to enforce these boundaries in every build.

---

# 20 — Java & Spring Boot Standards

## Spring Boot & Java 21 baseline
- Spring Boot 3.x, Java 21.
- **Modern Java**: Use Pattern Matching for `switch`, Sealed Interfaces for domain events, and Records for DTOs/Value Objects.
- Prefer constructor injection.
- No field injection.

## Hexagonal discipline
- Controllers (inbound adapters) only: validation, mapping, calling inbound ports
- Use cases (inbound ports) contain orchestration and transactions
- Outbound access only via ports (interfaces), implemented by adapters
- Keep Spring annotations out of domain; prefer Spring in adapters/use cases wiring
- Domain exceptions must extend `com.pidabrow.starter.common.exception.BusinessException` — `GlobalExceptionHandler` relies on this hierarchy for RFC 7807 mapping

## REST API
- Use explicit request/response DTOs
- Never expose JPA entities directly from controllers
- **Error Handling**: Use `Result<T>` or `Either` pattern for business failures instead of throwing checked exceptions in the domain.
- Keep error responses consistent and explicit

## Avoid
- Generic abstractions
- Broad AOP
- Persistence callbacks as business triggers
- **Nulls**: Use `Optional` for return types; use `@NonNullApi` for packages.

---

# 30 — Data & JPA Rules

## Immutability & State Management (L10 Standard)
- **Domain Immutability**: Domain models MUST be immutable. State changes return new instances.
- **No Public Setters**: JPA entities MUST NOT have public setters. Use purposeful business methods (e.g., `deactivate()`).
- **Collections**: Always wrap collections in `Collections.unmodifiableList()` or similar when exposing them.
- **Records for Projections**: Use Java Records for all read-only queries (CQRS light). Never stream entities to the application layer for read-only purposes.

## Soft multi-tenancy
- Every entity except `Tenant` MUST contain `tenantId`
- Tenant isolation must NOT rely on joins
- Tenant context is a security boundary

## Hexagonal persistence
- Application/use cases talk to persistence via outbound ports (interfaces)
- JPA repositories live in outbound adapters (infrastructure.persistence)

## Identity & Primary Keys (Strict UUID)
- **UUID as Primary Key**: All entities MUST use `java.util.UUID` as their primary key. No `Long` or `Integer` IDs.
- **UUID v7 (Time-Ordered)**: Use UUID v7 to prevent B-Tree fragmentation. This ensures that IDs are sequential in time, keeping database inserts fast while maintaining global uniqueness.
- **No ID Enumeration**: Public APIs must never expose sequential IDs to prevent data scraping and ID enumeration attacks.
- **Generation Strategy**: Prefer generating the ID in the domain layer or using a dedicated generator in the persistence adapter. The domain object should ideally be "born" with an ID.

## JPA/Hibernate
- Default fetch is LAZY
- Avoid `cascade = ALL`
- Avoid `@ManyToMany`
- Keep entities persistence-focused; avoid leaking persistence into the domain model design

## Auditing & Timestamps
- **Database-Driven Timestamps**: `created_at` and `updated_at` MUST be managed by the database (e.g., `DEFAULT CURRENT_TIMESTAMP` and database triggers).
- **JPA Synchronization**: Use Hibernate's `@Generated` annotation to ensure the persistence context is updated with database-generated timestamps after save/update.
- **Audit Trail**: Do not rely on application-level clock for record-keeping integrity. The database server clock is the single source of truth.

## Migrations
- Use Flyway
- Every schema change requires a migration

---

# 40 — Testing Policy

## Global rule
Tests are mandatory unless explicitly exempted by change classification.

## DOCS_ONLY
- No tests required
- No production code changes allowed
- CI must pass

## BUILD_ONLY
- Existing tests MUST pass
- New tests optional unless runtime behavior changes

## REFACTOR
- Existing tests MUST pass
- Do not delete tests
- Add tests only if modified code was previously untested

## BEHAVIOR_CHANGE (DEFAULT)
- Tests are NON-NEGOTIABLE
- Tests must fail before and pass after the change
- Choose correct test level:
  - Unit tests for domain/use case logic
  - Integration tests for persistence, migrations, multi-tenancy
  - Web tests for REST API changes

## Test Types & Levels
- **Domain/Use Case**: Unit tests (Socialized, focused on behavior).
- **Persistence/Multi-tenancy**: Integration tests with Testcontainers.
- **Architecture**: **ArchUnit tests** are mandatory to verify Hexagonal boundaries and Immutability rules.

## Quality rules
- Tests must be deterministic
- Fix flaky tests; never mute them
- Prefer clarity over clever setups

## Beyond happy path (HARD for BEHAVIOR_CHANGE)
- **Success-only coverage is not enough.** Where the change introduces or affects behavior, tests MUST also cover **failure paths**, **validation errors**, and **denied / unauthorized** outcomes when those are part of the contract (for example: wrong input, missing tenant, forbidden action).
- Add **boundary** or **edge** cases when they guard real domain or infrastructure rules (not speculative coverage).
- Prefer naming that makes the scenario obvious (see Test Naming Convention below).

## Test Naming Convention
- **Format**: Use `should_[expectedBehavior]_when_[condition]`.
- **Snake Case**: Always use `snake_case` for test method names. It improves readability in test reports and IDEs.
- **Example**: `should_deny_access_when_user_has_no_tenant_id()`.
- **Avoid**: Prefixes like `test...` or overly formal `WHEN_..._THEN_...` unless the use case is extremely complex.
- **Display Name**: For complex scenarios, use `@DisplayName("Descriptive sentence")` to explain the "why" behind the test.

---

# 45 — ADR Policy

## Canonical template

Every ADR in `docs/adr/` must follow this structure:

```
# ADR-{NNN} — {Title}

**Status:** {Proposed | Accepted | Rejected | Deprecated | Superseded by ADR-XXX}
**Date:** {YYYY-MM-DD}
**Decision-makers:** {who}
**Consulted:** {optional}
**Informed:** {optional}
**Related:** {ADR-XXX, files, rules}

## Context and Problem Statement
## Decision Drivers
## Considered Options
## Decision Outcome
### Consequences
**Positive** / **Negative**
### Confirmation
## Pros and Cons of the Options
## Notes for AI
## More Information
```

## Hard rules for migration

1. **Never fabricate content.** If the original ADR does not contain enough information to
   fill a section (Decision Drivers, Considered Options, Confirmation, per-option Pros/Cons,
   Related, Decision-makers), leave an HTML comment placeholder:
   `<!-- TODO: fill in — [what's missing] -->`
   Do not infer business rationale, stakeholders, or test names that aren't stated or
   directly implied by the original text.
2. **Preserve the original decision content exactly.** Reformatting must not change what
   was decided — only how it's structured. Implementation-detail bullets that read as
   conventions/gotchas for a coding agent (not architectural facts) should be moved into
   `## Notes for AI` rather than duplicated in `Decision`.
3. **One file per turn.** Migrate exactly one ADR file, then stop and show the full diff.
   Do not proceed to the next file and do not write/commit until the user explicitly approves.
4. **All content in English**, including TODO comments, even if the source ADR (or the
   conversation invoking this rule) is in another language.
5. **Do not touch file names or ADR numbers.** Only the internal structure changes.

---

# 50 — CI / Pipeline Rules (HARD)

## Absolute rules
- CI MUST be green after every change.
- Never weaken CI without an ADR.
- Never merge broken CI.

## Pull Request gating (HARD)
- All changes MUST go through a Pull Request.
- CI MUST run on `pull_request` events.
- A PR MUST NOT be merged unless all required CI checks pass.
- Branch protection MUST enforce required status checks on the main branch.

## Branch workflow (HARD)
- Do **not** push commits directly to the protected default branch (`main`). Implement work on a **dedicated branch** created from `main` (for example `feature/…`, `fix/…`, or `chore/…`).
- Open a PR from that branch into `main`. Treat the branch as the unit of review together with its PR.

## After pushing (HARD)

After pushing a branch, wait for CI to complete and check the actual result — for example
`gh pr checks --watch`, or by polling `gh run view` / `gh run list` until a conclusion is
available. Report the real conclusion (success/failure) to the user. Never assume or state
that CI passed without having actually checked.

## Local expectation (HARD where feasible)
- Before pushing, ensure `./gradlew build` passes locally (unless not feasible due to environment limits).

## If CI fails
You MUST:
1. Determine whether the failure is caused by your change.
2. Fix it in the same PR/change set.

## Not allowed
- Disabling jobs.
- Using `continue-on-error`.
- Ignoring failing checks.

---

# 60 — Security & Privacy Rules

## Secrets
- Never commit secrets
- Never hardcode credentials or tokens
- Use environment variables for local and CI usage

## PII & Logging
- Do not log emails, tokens, or secrets
- Audit logs must support masking of sensitive fields

## Auth scaffolding
- Keep auth minimal until defined by ADR
- No insecure shortcuts or temporary bypasses

---

# Autonomy & Safety Principles

What an AI coding agent can do without asking, and what stays off-limits in this repository. Runtime enforcement for Claude Code lives in `.claude/settings.json` and for Cursor CLI in `.cursor/cli.json`; this section is the constitution both should follow.

### Principles

1. **Read freely, write deliberately.** Any read operation on the project tree is autonomous. Shell-based writes (outside `Edit`/`Write` tools) require explicit user approval.
2. **Feature branches are sandboxes.** Push, commit, non-interactive rebase — all autonomous on `feature/*`, `fix/*`, `chore/*` branches. Anything touching `main` or `master` requires explicit approval. Never commit directly to `main`; land changes via PR with green CI ([50 — CI / Pipeline Rules (HARD)](#50--ci--pipeline-rules-hard)).
3. **Build and test loops are autonomous.** `./gradlew test`, `./gradlew build`, `./gradlew check` and their `:module:` variants run without confirmation. Diagnostic inspection (`find`, `grep`, `jar tf`, `unzip -l`, ad-hoc `python3 -c`) is autonomous.
4. **GitHub read and PR authorship are autonomous.** `gh pr create`, `gh pr checks`, `gh run view` — autonomous. PR **merge** and any release/repo lifecycle operation — never autonomous.

### Hard rules (never, even if asked mid-session)

- No `rm -rf`, no `sudo`, no `chmod 777`, no piped `curl`/`wget` to shell.
- No `git push --force` in any form.
- No push, merge, or branch-switch onto `main`/`master`.
- No reading of secret files: `.env*`, private keys (`*.pem`, `*.key`, `id_rsa`, `id_ed25519`).
- No `gh pr merge`, no `gh release`, no `gh repo create`/`delete`.

If a session legitimately needs one of these (e.g., a destructive cleanup), the user runs it manually. The agent proposes the command, the user executes.

### Cross-references

- Claude Code runtime enforcement: `.claude/settings.json`
- Cursor CLI runtime enforcement: `.cursor/cli.json` — a **best-effort convenience mirror**, not a security boundary. Known deltas vs. `.claude/settings.json`: Cursor's `Shell(...)` token only matches on the first token of the command plus an optional `command:argsGlob` suffix (no multi-word prefix matching like Claude's `Bash(git status:*)`), so subcommand-level allow/deny is approximate, not exact; there is no `Edit` token (covered by `Write`); there is no `additionalDirectories` equivalent; and `rm`/`chmod` are denied entirely on the Cursor side (Claude Code only denies recursive `rm` and `chmod 777`), since Cursor's first-token matching cannot express subcommand-level exceptions. The wide deny globs err on the side of blocking — expect occasional false-positive denials on innocent commands whose arguments happen to contain the matched substrings (e.g. a commit message mentioning `push` and `main`); when that happens, run the command manually. The deny entries for destructive/branch-unsafe git operations have **not** been behaviorally verified against `cursor-agent` (not installable in this sandbox — see PR discussion) — treat them as best-effort until manually confirmed. The actual boundary protecting `main` is GitHub branch protection ([50 — CI / Pipeline Rules (HARD)](#50--ci--pipeline-rules-hard)), not this file.
- Skills (shared by Cursor and Claude Code): `.claude/skills/`
- Personal session overrides: `CLAUDE.local.md` (gitignored)

---

*Living document. Update when module layout or top-level conventions change; otherwise put detail in ADRs.*
