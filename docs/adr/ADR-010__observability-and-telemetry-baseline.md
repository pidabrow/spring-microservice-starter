# ADR-010 — Observability & Telemetry Baseline (Metrics, Tracing, Structured Logging)

**Status:** <!-- TODO: fill in — not stated in original ADR -->
**Date:** <!-- TODO: fill in — not stated in original ADR -->
**Decision-makers:** <!-- TODO: fill in — not stated in original ADR -->
**Consulted:** <!-- TODO: fill in — not stated in original ADR -->
**Informed:** <!-- TODO: fill in — not stated in original ADR -->
**Related:** ADR-002 (Soft Multi-Tenancy), ADR-005 (Lightweight Hexagonal Architecture), ADR-006 (Tenant-Aware Auditing), ADR-007 (Transactional Outbox)

## Context and Problem Statement

Focus: Production Readiness, Observability, Operations.

Microservices generated from this starter must be "Day 2 Operations" ready out-of-the-box. This requires a standardized approach to application health monitoring, metrics collection, and distributed tracing.

Given our architectural decisions regarding event-driven auditing (ADR-006), transactional outbox (ADR-007), and soft multi-tenancy (ADR-002), it is crucial that every log entry and metric in the system carries the appropriate context (specifically, the tenant and actor identity). Manual propagation of this context is error-prone and pollutes business logic. We need a centralized, infrastructure-level mechanism to handle observability without leaking into the pure Domain layer.

## Decision Drivers

- Standardized health monitoring, metrics collection, and distributed tracing ("Day 2 Operations" readiness).
- Every log entry and metric must carry tenant and actor identity context (aligned with ADR-002, ADR-006).
- Manual propagation of this context is error-prone and pollutes business logic.
- Observability must not leak into the pure Domain layer.

## Considered Options

- Spring Cloud Sleuth for Tracing.
- Infrastructure-level Agents Only (e.g., Datadog/New Relic Java Agents).
- No Structured Logging (Plain Text everywhere).
- Standardized observability stack based on Spring Boot 3 / Micrometer ecosystem.

## Decision Outcome

Chosen option: "Standardized observability stack based on Spring Boot 3 / Micrometer ecosystem."

### 1. Health & Metrics (Actuator Baseline)

- We enable Spring Boot Actuator endpoints: `/actuator/health/liveness`, `/actuator/health/readiness` (for Kubernetes/orchestrator probes), and `/actuator/prometheus` (for metric scraping).
- To prevent infrastructure data leaks, Actuator endpoints must be secured or exposed on a separate management port (e.g., `management.server.port=8081`).

### 2. Distributed Tracing

- We adopt `micrometer-tracing` (using the W3C Trace Context standard).
- A unique `traceId` and `spanId` must be automatically generated for every incoming HTTP request and propagated to downstream systems or message brokers (Kafka).

### 3. Structured Logging (JSON) & Context Enrichment (MDC)

- **Format:** We adopt `logstash-logback-encoder` to output logs in structured JSON format. This is mandatory for the `production` profile to allow easy parsing by log aggregators (ELK, Datadog). The `local` profile will retain human-readable plain text console logs.
- **MDC Enrichment:** We mandate the automatic injection of architectural context into the Mapped Diagnostic Context (MDC). Every log entry must include `tenant_id`, `actor_id`, and `trace_id`.

### Consequences

**Positive**

- **Out-of-the-box Production Readiness:** Immediate compatibility with standard monitoring stacks (Prometheus, Grafana, ELK).
- **Faster Debugging:** Full log correlation via `traceId` across synchronous API calls and asynchronous Kafka events.
- **Tenant-Isolated Telemetry:** Platform operators can easily filter logs and metrics by `tenant_id` to diagnose customer-specific issues.
- **Domain Purity:** Observability concerns are pushed to the edge (Inbound/Outbound Adapters), keeping the Domain entirely agnostic of logging frameworks and metrics registries.

**Negative**

- **Dependency Bloat:** Adds several dependencies to `build.gradle` (Actuator, Micrometer Tracing, Logstash Encoder).
- **Performance Overhead:** Slight CPU/Memory overhead for JSON serialization in logs and trace generation (acceptable for most business applications).

### Confirmation

<!-- TODO: fill in — original ADR does not describe a confirmation/verification mechanism -->

## Pros and Cons of the Options

### Spring Cloud Sleuth for Tracing

- Bad, because Sleuth is officially deprecated in Spring Boot 3.x (rejected).

### Infrastructure-level Agents Only (e.g., Datadog/New Relic Java Agents)

- Bad, because relying solely on proprietary agents makes local development and open-source observability (like local Zipkin/Jaeger or Prometheus) harder (rejected).

### No Structured Logging (Plain Text everywhere)

- Bad, because plain text logs require complex Grok parsing rules on the infrastructure side (Logstash/Fluentd) to extract `trace_id` and `tenant_id` (rejected).

### Standardized observability stack based on Spring Boot 3 / Micrometer ecosystem (chosen)

- Good, because of out-of-the-box production readiness (immediate compatibility with Prometheus, Grafana, ELK).
- Good, because of faster debugging via full log correlation (`traceId`) across synchronous and asynchronous flows.
- Good, because of tenant-isolated telemetry (filter logs/metrics by `tenant_id`).
- Good, because it keeps the Domain entirely agnostic of logging frameworks and metrics registries.
- Bad, because it adds dependency bloat (Actuator, Micrometer Tracing, Logstash Encoder).
- Bad, because of slight CPU/Memory overhead for JSON serialization in logs and trace generation.

## Notes for AI

When implementing or scaffolding features based on this ADR, the AI must adhere strictly to these steps:

1. **Dependency Management:**
    - Add `spring-boot-starter-actuator`.
    - Add `micrometer-registry-prometheus`.
    - Add `micrometer-tracing-bridge-brave` (or OpenTelemetry equivalent).
    - Add `net.logstash.logback:logstash-logback-encoder`.
2. **Logback Configuration:**
    - Create a `logback-spring.xml` file.
    - Define a `console` appender (plain text) for the `local`/`dev` Spring profiles.
    - Define a `json` appender using `LogstashEncoder` for the `prod` profile. Ensure MDC fields (`tenant_id`, `trace_id`, `actor_id`) are explicitly included in the JSON output.
3. **MDC Web Filter:**
    - In the `platform-web` module (Inbound Adapter), create a `Filter` (e.g., `TelemetryContextFilter`).
    - This filter must extract the current tenant and actor from the `ActorContextHolder` (defined in ADR-006) and put them into the `MDC` (using keys `tenant_id` and `actor_id`).
    - Ensure the MDC is properly cleared in a `finally` block to prevent thread-pool contamination.
4. **Hexagonal Constraints:**
    - Never use `MDC.put()` or Micrometer classes inside the pure Domain layer. If the Domain needs to report a metric, it should publish a `DomainEvent`, which an Outbound Adapter will translate into a Micrometer metric increment.

## More Information

<!-- TODO: fill in — original ADR does not reference further material beyond the Related ADRs already listed above -->
