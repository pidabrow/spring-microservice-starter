# Transactional Outbox Flow Guide

This guide explains how the Transactional Outbox is implemented in this repository and how to reason about reliability, retries, and operational behavior.

## Why This Exists

We need reliable integration with external systems (Kafka) without introducing inconsistent state between database writes and message publishing.

### Dual Write Risk

The dual write problem appears when one business action tries to:
1. commit business data to DB
2. publish an event to Kafka

Because DB and Kafka are not in one ACID transaction, partial success is possible:
- DB commit succeeds, Kafka publish fails -> downstream never receives the event
- Kafka publish succeeds, DB transaction rolls back -> downstream sees a state that does not exist

Transactional Outbox solves this by persisting integration intent inside the same DB transaction as business data.

---

## High-Level Flow

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

## Components

- `IntegrationEventListener`  
  Captures `DomainEvent` and inserts outbox record within the same transaction.

- `message_outbox` table  
  Stores payload, headers, routing metadata, status, retries, and timestamps.

- `OutboxRelayService`  
  Periodically polls `PENDING` records and publishes them externally.

- `MessagePublisher` port  
  Hexagonal boundary for publishing messages.

- `KafkaMessagePublisher` adapter  
  Publishes to Kafka topic from `destination`; uses `partition_key` as key.

- `OutboxCleanupService`  
  Deletes old `SENT` records according to retention policy.

---

## Message Metadata

Each outbox message includes:

- `tenant_id` (DB column)
- `message_type`
- `destination` (target topic)
- `partition_key` (ordering key)
- headers:
  - `x-tenant-id`
  - `x-message-type`
  - `x-correlation-id`

---

## State Machine

```text
PENDING --(publish success)--> SENT
PENDING --(publish failure)--> PENDING (retry_count++)
PENDING --(retry_count >= 5)-> FAILED
```

---

## Retry and Failure Semantics

- Delivery guarantee is **At-Least-Once**
- Retries are tracked via `retry_count`
- Final failure state is `FAILED` after 5 attempts
- Current implementation does not auto-publish to Kafka DLQ
- `FAILED` records stay visible in DB for operational handling/re-drive

---

## Scheduling and Concurrency

ShedLock prevents multiple instances from processing the same scheduled job concurrently:
- relay job lock: `outboxRelay`
- cleanup job lock: `outboxCleanup`

---

## Operational Notes

- Monitor counts of `PENDING`, `FAILED`, and relay lag (`created_at` to `processed_at`)
- Keep consumers idempotent (at-least-once delivery may duplicate messages)
- Build replay tooling for `FAILED` records as a future improvement

