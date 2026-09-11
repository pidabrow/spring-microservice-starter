---
workItemRef: GH-40
artifact: spec
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
# Spec

## Requirements

A handler method may declare that it requires an idempotency key. For such a
handler, the service guarantees that two requests carrying the same key within
the same tenant are applied at most once, and that the second caller observes
the outcome of the first rather than a second application.

The key is supplied in the `Idempotency-Key` request header. A request is
identified by the pair of tenant and key; the request content is additionally
fingerprinted so that reusing a key for a different request is reported as a
client error instead of silently returning someone else's answer.

A claim on a key is made before the handler runs and resolved after it
completes. Only outcomes the client can act on are final: successful and 4xx
responses are stored and replayable; a 5xx outcome releases the key, because a
server failure is exactly the case a retry is meant to survive.

Keys expire. Storage is bounded by a configurable retention window, after which
records are deleted and the key may be reused.

## Acceptance criteria

1. A request to an annotated handler carrying an `Idempotency-Key` header is
   processed normally, and its status code and response body are stored against
   `(tenant, key)` with the terminal state.
2. A second request with the same tenant, key and fingerprint, made after the
   first completed, returns the stored status code and body, carries a header
   marking it as a replay, and does not invoke the handler a second time.
3. A second request with the same tenant and key but a different fingerprint is
   rejected with 422 and an RFC 9457 problem response; the stored record is left
   unchanged.
4. A request to an annotated handler without the header is rejected with 400 and
   an RFC 9457 problem response; no record is created.
5. A request to a handler without the annotation is unaffected, whether or not
   the header is present, and creates no record.
6. A second request arriving while the first claim on the same key is still in
   flight is rejected with 409 and an RFC 9457 problem response.
7. When a handler produces a 5xx response, the claim is released: a subsequent
   request with the same key and fingerprint is processed as a new request.
8. The same key value used under two different `X-Tenant-Id` values yields two
   independent records and two independent executions.
9. When a response body exceeds the configured cacheable size, the original
   response is returned to the caller unchanged, the record is stored without a
   body, and a later replay of that key is answered with 409 and a problem
   response stating that the response cannot be replayed.
10. Records whose age exceeds the configured retention window are deleted by the
    scheduled job, and the key becomes reusable afterwards.
11. Key claiming is atomic under concurrency: with N parallel requests carrying
    the same tenant and key, exactly one handler invocation occurs.
12. All new adapter classes are package-private and the domain layer gains no
    new dependency; the existing ArchUnit suite passes unchanged.

## Contracts

**Request header**: `Idempotency-Key`, an opaque client-generated string,
1 to 255 characters. Rejected with 400 if empty or over the limit.

**Response header on replay**: `Idempotent-Replay: true`.

**Fingerprint**: a hash over HTTP method, request path and raw request body.
Stored alongside the record; never returned to the client.

**Store** (`platform-infrastructure`, Flyway migration): table with UUID v7 primary
key, `tenant_id`, `idempotency_key`, `fingerprint`, `state`, `response_status`,
`response_body`, `created_at`, `completed_at`. Unique constraint on
`(tenant_id, idempotency_key)`. Timestamps database-driven, consistent with the
auditing rule in `architecture.md`.

**States**: `IN_PROGRESS`, `COMPLETED`. A released key is deleted rather than
kept in a failed state, so that reuse needs no special-casing.

**Port** (`platform-common`): an outbound interface for claiming a key,
completing a claim and releasing it. No Spring types in the signature.

**Problem types** (RFC 9457): distinct `type` URIs for missing key, fingerprint
mismatch, in-flight conflict and non-replayable response.

## Non-functional constraints

- One additional read and one additional write per annotated request. The claim
  is a single conditional insert, not a read-then-write, so no application-level
  locking is introduced.
- Cacheable response body size is configurable, default 64 KiB.
- Retention window is configurable, default 24 hours.
- The retention job uses the existing ShedLock setup so that multiple instances
  do not purge concurrently.
- The feature is disabled by configuration by default, so that a fork that does
  not need it pays nothing.
- No new infrastructure dependency. Postgres only.

## Open decisions

- **ADR required** for two choices: storing idempotency state in Postgres rather
  than introducing a cache, and wrapping the servlet response to capture the
  body, which is in tension with the "no hidden magic" principle in
  `architecture.md`. Both should be recorded before implementation starts.
- Whether the annotation belongs in `platform-common` or `platform-web`. It is
  consumed by a web adapter, but placing it in `platform-common` keeps handler
  code free of a direct dependency on the web module. To be settled in the ADR.
