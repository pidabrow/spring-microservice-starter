---
name: testing
description: Testing policy, workflow, and commands for this Spring Boot 3 / Java 21 monorepo. Use when writing, modifying, or auditing tests — unit tests, integration tests with Testcontainers, or ArchUnit tests. Also use when classifying a change (DOCS_ONLY, BUILD_ONLY, REFACTOR, BEHAVIOR_CHANGE) to determine whether tests are required. Authoritative testing policy lives in .cursor/rules/40-testing.mdc; this skill points there and adds tactics that belong to the terminal workflow (commands, fixture locations, ArchUnit execution in :sample-service:test).
---

# Testing

The authoritative testing policy for this repo is `.cursor/rules/40-testing.mdc`. **Read it in full before writing or modifying tests.** This skill delegates to that rule pack and adds terminal-workflow tactics not present in the rule itself.

## Workflow

1. **Establish change classification** per `.cursor/rules/00-base.mdc`: `DOCS_ONLY`, `BUILD_ONLY`, `REFACTOR`, or `BEHAVIOR_CHANGE`. The classification decides whether tests are required and which exceptions apply.
2. **Read `.cursor/rules/40-testing.mdc` end-to-end.** Do not summarise from memory — read the file.
3. **Apply the policy** to the change at hand. When the policy and the request conflict, surface the conflict; do not paper over it.

## Where test infrastructure lives

- **Shared fixtures** (Spring Boot Test + Testcontainers for Postgres and Kafka): `platform-testing` module, consumed via `testImplementation(testFixtures(project(":platform-testing")))`. Test ergonomics, fixtures, and multi-tenancy wiring are specified in ADR-009.
- **ArchUnit tests**: live in `sample-service/src/test`. There is no dedicated Gradle task — they run as part of `:sample-service:test` via `archunit-junit5`.
- **Other sample-service test dependencies**: H2, Awaitility.

## Running tests (narrowest loop first)

```bash
./gradlew :<module>:test            # single module — fastest feedback
./gradlew :sample-service:test      # includes ArchUnit
./gradlew test                      # repo-wide tests
./gradlew check                     # tests + other verification
./gradlew build                     # final gate before declaring done
```

Iterate with the narrowest loop that proves the change. Before declaring a task complete, run `./gradlew build`.

## Before declaring a testing task done

- Classification from `00-base.mdc` honoured (and stated in the summary).
- Policy from `40-testing.mdc` followed for that classification.
- Narrowest relevant test loop green.
- For structural changes: `:sample-service:test` green (ArchUnit).
- `./gradlew build` green.