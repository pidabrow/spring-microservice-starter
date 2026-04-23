# ADR-010 — Observability & Telemetry Baseline (Metrics, Tracing, Structured Logging)

**Focus:** Production Readiness, Observability, Operations  
**Related:** ADR-002 (Soft Multi-Tenancy), ADR-005 (Lightweight Hexagonal Architecture), ADR-006 (Tenant-Aware Auditing), ADR-007 (Transactional Outbox)

## Context

Microservices generated from this starter must be "Day 2 Operations" ready out-of-the-box. This requires a standardized approach to application health monitoring, metrics collection, and distributed tracing.

Given our architectural decisions regarding event-driven auditing (ADR-006), transactional outbox (ADR-007), and soft multi-tenancy (ADR-002), it is crucial that every log entry and metric in the system carries the appropriate context (specifically, the tenant and actor identity). Manual propagation of this context is error-prone and pollutes business logic. We need a centralized, infrastructure-level mechanism to handle observability without leaking into the pure Domain layer.

## Decision

We adopt a standardized observability stack based on the Spring Boot 3 / Micrometer ecosystem:

### 1. Health & Metrics (Actuator Baseline)
- We enable Spring Boot Actuator endpoints: `/actuator/health/liveness`, `/actuator/health/readiness` (for Kubernetes/orchestrator probes), and `/actuator/prometheus` (for metric scraping).
- To prevent infrastructure data leaks, Actuator endpoints must be secured or exposed on a separate management port (e.g., `management.server.port=8081`).

### 2. Distributed Tracing
- We adopt `micrometer-tracing` (using the W3C Trace Context standard).
- A unique `traceId` and `spanId` must be automatically generated for every incoming HTTP request and propagated to downstream systems or message brokers (Kafka).

### 3. Structured Logging (JSON) & Context Enrichment (MDC)
- **Format:** We adopt `logstash-logback-encoder` to output logs in structured JSON format. This is mandatory for the `production` profile to allow easy parsing by log aggregators (ELK, Datadog). The `local` profile will retain human-readable plain text console logs.
- **MDC Enrichment:** We mandate the automatic injection of architectural context into the Mapped Diagnostic Context (MDC). Every log entry must include `tenant_id`, `actor_id`, and `trace_id`.

## Consequences

### Positive
- **Out-of-the-box Production Readiness:** Immediate compatibility with standard monitoring stacks (Prometheus, Grafana, ELK).
- **Faster Debugging:** Full log correlation via `traceId` across synchronous API calls and asynchronous Kafka events.
- **Tenant-Isolated Telemetry:** Platform operators can easily filter logs and metrics by `tenant_id` to diagnose customer-specific issues.
- **Domain Purity:** Observability concerns are pushed to the edge (Inbound/Outbound Adapters), keeping the Domain entirely agnostic of logging frameworks and metrics registries.

### Trade-offs / Negative
- **Dependency Bloat:** Adds several dependencies to `build.gradle` (Actuator, Micrometer Tracing, Logstash Encoder).
- **Performance Overhead:** Slight CPU/Memory overhead for JSON serialization in logs and trace generation (acceptable for most business applications).

## Alternatives Considered

1. **Spring Cloud Sleuth for Tracing** *Rejected:* Sleuth is officially deprecated in Spring Boot 3.x. Micrometer Tracing is the modern, native replacement.
2. **Infrastructure-level Agents Only (e.g., Datadog/New Relic Java Agents)** *Rejected:* While powerful, relying solely on proprietary agents makes local development and open-source observability (like local Zipkin/Jaeger or Prometheus) harder. Baking Micrometer into the application makes the starter vendor-agnostic.
3. **No Structured Logging (Plain Text everywhere)** *Rejected:* Plain text logs require complex Grok parsing rules on the infrastructure side (Logstash/Fluentd) to extract `trace_id` and `tenant_id`. Emitting JSON directly from the application guarantees reliable parsing and immediate observability.

## Notes & Implementation Guidelines (For AI Assistants / Cursor)

When implementing or scaffolding features based on this ADR, the AI must adhere strictly to these steps:

1. **Dependency Management:** - Add `spring-boot-starter-actuator`.
    - Add `micrometer-registry-prometheus`.
    - Add `micrometer-tracing-bridge-brave` (or OpenTelemetry equivalent).
    - Add `net.logstash.logback:logstash-logback-encoder`.
2. **Logback Configuration:** - Create a `logback-spring.xml` file.
    - Define a `console` appender (plain text) for the `local`/`dev` Spring profiles.
    - Define a `json` appender using `LogstashEncoder` for the `prod` profile. Ensure MDC fields (`tenant_id`, `trace_id`, `actor_id`) are explicitly included in the JSON output.
3. **MDC Web Filter:** - In the `platform-web` module (Inbound Adapter), create a `Filter` (e.g., `TelemetryContextFilter`).
    - This filter must extract the current tenant and actor from the `ActorContextHolder` (defined in ADR-006) and put them into the `MDC` (using keys `tenant_id` and `actor_id`).
    - Ensure the MDC is properly cleared in a `finally` block to prevent thread-pool contamination.
4. **Hexagonal Constraints:** - Never use `MDC.put()` or Micrometer classes inside the pure Domain layer. If the Domain needs to report a metric, it should publish a `DomainEvent`, which an Outbound Adapter will translate into a Micrometer metric increment.