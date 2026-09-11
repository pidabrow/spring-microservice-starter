---
workItemRef: GH-40
artifact: analysis
type: feat
version: 1
status: draft
persona: analyst
links:
  issue: https://github.com/pidabrow/spring-microservice-starter/issues/40
  branch: feat/GH-40-opt-in-idempotency-key-for-post-endpoints
  pr: null
  ci_run: null
approved_at: null
approved_by: null
---
# Analysis

## Context

The starter exposes non-idempotent POST endpoints and already guarantees
at-least-once delivery outbound (ADR-007, transactional outbox). The inbound
side has no equivalent: a client that retries after a timeout cannot tell
whether the first attempt was applied, and the service will happily apply it
twice. For a starter that is meant to be forked into production services, an
inbound de-duplication story is the missing half of the reliability picture.

`Idempotency-Key` is the de facto industry header and has an IETF Standards
Track draft (`draft-ietf-httpapi-idempotency-key-header`, still a draft, latest
revision expires December 2026). It defines the header, the notion of an
idempotency fingerprint and expiry semantics. This change follows its
vocabulary; it does not claim conformance to a document that is still work in
progress.

## Questions and answers

| # | Question | Answer |
| - | -------- | ------ |
| 1 | Where does idempotency apply? | Opt-in per endpoint via an annotation. Enforcing it globally would be too opinionated for a starter meant to be forked and trimmed. |
| 2 | Missing header | 400 on annotated endpoints only; untouched elsewhere. |
| 3 | Key scope | `(tenant_id, idempotency_key)`, combined with the fingerprint from Q4. |
| 4 | Same key, different request | 422, decided by comparing a fingerprint of method, path and body. |
| 5 | Replay of a completed request | Return the stored status and body, marked with a replay header. |
| 6 | Concurrent request with the same key | 409 while the first is in flight. Blocking and waiting would hold a servlet thread and turn a client retry storm into a thread-pool outage. |
| 7 | 5xx responses | Key is released, so the client may retry. Only successful and 4xx outcomes are final. |
| 8 | Retention | Configurable, default 24h, purged by a scheduled job modelled on `OutboxCleanupService`. |
| 9 | Placement | Port in `platform-common`, interceptor in `platform-web`, JPA adapter and Flyway migration in `platform-infrastructure`, mirroring the outbox. |

No open questions remain.

## In scope

- An opt-in annotation for handler methods requiring an idempotency key.
- Request interception: key validation, fingerprint computation, claim, replay.
- A tenant-scoped store with a unique constraint on `(tenant_id, key)`.
- Retention job with configurable TTL.
- Problem responses (RFC 9457) for missing key, fingerprint mismatch and
  in-flight conflict.
- Integration tests over the real Postgres via the existing Testcontainers
  setup, including a concurrency test.

## Out of scope

- GET, PUT and DELETE. They are idempotent by HTTP semantics; adding key
  handling there would imply we do not trust our own handlers.
- Distributed caches. The stack has Postgres and no Redis, and introducing one
  for this would be a much larger architectural decision than the feature
  warrants.
- Consumer-side de-duplication of Kafka messages. The outbox is at-least-once
  by design, so consumers need their own de-duplication. Related, but a
  different boundary and a different change.
- Automatic retry or backoff advice to clients.
- Cross-service key sharing. Keys are local to this service.

## Impact

- **`platform-common`**: new outbound port and the annotation. No dependency on
  Spring in the port itself, per the domain purity rule.
- **`platform-web`**: a new interceptor, ordered after `TenantContextInterceptor`
  because the tenant must be resolved before a tenant-scoped key can be claimed.
- **`platform-infrastructure`**: JPA adapter, Flyway migration, retention job.
  All adapter classes package-private, enforced by ArchUnit.
- **`sample-service`**: one annotated endpoint as the reference usage.
- **Domain layer**: untouched. Idempotency is an edge concern and must not leak
  inward.
- **Operational**: one additional write and one read per annotated request, plus
  a periodic delete. Table growth is bounded by the TTL.
- **Decision record**: response caching requires wrapping the servlet response,
  which sits close to the "no hidden magic" rule in `architecture.md`. That
  trade-off, and the choice of Postgres over a cache, deserve an ADR rather than
  a paragraph buried in a spec.
