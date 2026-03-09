# Transactional Outbox Flow (Draft)

This draft explains the current Transactional Outbox implementation in this repository.
Audience: developers onboarding to the project and contributors touching integration reliability.

---

## 1) Why Outbox Exists

We want to publish integration events to Kafka **without dual-write inconsistency**.

### Dual-write problem (short)
A business operation tries to:
1. commit business data to DB
2. publish message to Kafka

Those are different systems with no shared ACID transaction, so partial success can happen:
- DB committed, Kafka publish failed -> event lost
- Kafka published, DB rolled back -> ghost event

### Outbox principle
In the same business transaction:
- write business state
- write `message_outbox` record (`PENDING`)

Later, an async relay publishes from outbox to Kafka and updates status.

---

## 2) End-to-End Flow

```text
┌──────────────────────────┐
│ Controller / Inbound API │
└─────────────┬────────────┘
              │
              v
┌───────────────────────────────┐
│ Use Case (@Transactional)     │
│ - writes business state       │
│ - publishes DomainEvent       │
└─────────────┬─────────────────┘
              │   (same DB transaction)
              v
┌──────────────────────────────────────────────┐
│ IntegrationEventListener (@EventListener)    │
│ - maps DomainEvent -> message_outbox row     │
│ - status=PENDING, retry_count=0              │
└─────────────┬────────────────────────────────┘
              │
              v
┌───────────────────────────────┐
│ DB: message_outbox (PENDING)  │
└─────────────┬─────────────────┘
              │ async polling
              v
┌──────────────────────────────────────────────┐
│ OutboxRelayService (@Scheduled + ShedLock)  │
│ - fetches pending batch                      │
│ - visibility buffer: created_at < now()-1s  │
│ - retries up to 5                            │
└─────────────┬────────────────────────────────┘
              │
              v
┌────────────────────────────────┐
│ MessagePublisher (port)        │
│ KafkaMessagePublisher (adapter)│
└───────┬────────────────────────┘
        │
        ├── success (ACK) -> mark SENT + processed_at
        │
        └── failure -> retry_count++ / last_error
                       if retry_count >= 5 -> FAILED
```

---

## 3) Port vs Adapter (Quick Context)

- `MessagePublisher` = **port** (stable contract)
- `KafkaMessagePublisher` = **adapter** (Kafka-specific plugin)

So relay logic depends on the contract, not Kafka API details.

---

## 4) Outbox-to-Kafka Mapping (Record Shape)

```text
DATABASE: message_outbox record                 KAFKA: ProducerRecord
┌───────────────────────────────┐              ┌────────────────────────────────────┐
│ destination                   │─────────────▶│ Topic (e.g., "notification-events")│
├───────────────────────────────┤              ├────────────────────────────────────┤
│ partition_key (entity_id)     │─────────────▶│ Message Key (String)               │
├───────────────────────────────┤              ├────────────────────────────────────┤
│ payload (JSONB)               │─────────────▶│ Message Value (JSON byte[])        │
├───────────────────────────────┤              ├────────────────────────────────────┤
│ headers (JSONB)               │              │ Kafka Headers:                     │
│  - x-tenant-id                │─────────────▶│  - x-tenant-id                     │
│  - x-message-type             │─────────────▶│  - x-message-type                  │
│  - x-correlation-id           │─────────────▶│  - x-correlation-id                │
└───────────────────────────────┘              └────────────────────────────────────┘
```

---

## 5) Ordering Semantics

```text
HOW ORDERING WORKS

partition_key = "user-123"
        │
        └──────────────▶ Hash(key) ──────────────▶ Partition 2
                                                  │
                                                  └────▶ [MSG 1] -> [MSG 2] -> [MSG 3]
                                                           (same key, same partition)

Guaranteed order for events with key "user-123".
```

### Why `partition_key` in outbox is good
Persisting `partition_key` in DB gives:
- deterministic retries (same key every retry)
- stable ordering per entity
- easier debugging/audit of routing decisions
- no recomputation risk in relay

---

## 6) Why `entityId` key (not `tenantId` key)

Current design uses `entityId` as `partition_key` to preserve ordering per aggregate/entity.

- `entityId` key:
  - best for per-entity event order
  - better partition spread

- `tenantId` key:
  - groups tenant traffic
  - can create tenant hotspots and weaker per-entity isolation

Tenant context is still preserved via `tenant_id` column and `x-tenant-id` header.

---

## 7) State Machine and Retries

```text
PENDING --(publish success)--> SENT
PENDING --(publish failure)--> PENDING (retry_count++)
PENDING --(retry_count >= 5)-> FAILED
```

```text
ACK / STATUS FLOW

message_outbox.status = PENDING
        │
        ├─ publish success (Kafka ACK) ─────────▶ status = SENT, processed_at = now
        │
        └─ publish failure ──────────────────────▶ retry_count++, last_error
                                                   if retry_count >= 5 -> status = FAILED
```

### DLQ note
Current implementation does **not** auto-publish to Kafka DLQ after final failure.
`FAILED` records remain in DB for operational re-drive/inspection.

---

## 8) Scheduling and Concurrency

- Relay is scheduled
- ShedLock prevents multi-instance double processing
- Visibility buffer (`created_at < now() - 1s`) avoids reading not-yet-visible inserts
- Batch processing limit improves throughput control

Cleanup job:
- periodically removes old `SENT` records (retention policy)

---

## 9) Practical Operational Checklist

- Monitor counts of `PENDING`, `FAILED`, `SENT`
- Alert on growing `FAILED`
- Track relay lag (`created_at` -> `processed_at`)
- Keep consumers idempotent (at-least-once delivery)
- Add replay tooling for `FAILED` as next hardening step

---

## 10) One-line Mental Model

```text
Business TX writes: [business tables + outbox row]
Async infra does:   [outbox row -> Kafka -> status update]
```

