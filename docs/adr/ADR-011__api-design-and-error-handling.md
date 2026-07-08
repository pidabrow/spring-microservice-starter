# ADR-011 — API Contract & Error Handling (RFC 7807, OpenAPI)

**Status:** <!-- TODO: fill in — not stated in original ADR -->
**Date:** <!-- TODO: fill in — not stated in original ADR -->
**Decision-makers:** <!-- TODO: fill in — not stated in original ADR -->
**Consulted:** <!-- TODO: fill in — not stated in original ADR -->
**Informed:** <!-- TODO: fill in — not stated in original ADR -->
**Related:** ADR-005 (Lightweight Hexagonal Architecture), ADR-010 (Observability & Telemetry Baseline)

## Context and Problem Statement

Focus: Developer Experience (DX), Client Integration, API Standardization.

As an opinionated microservice starter, this project must provide a predictable and standardized way for external clients (frontends, mobile apps, or other microservices) to interact with its APIs and handle errors.

Without a strict convention, developers tend to invent custom JSON error structures and rely on out-of-date wiki pages for API documentation. Furthermore, because we enforce a strict Lightweight Hexagonal Architecture (ADR-005), we must ensure that infrastructural HTTP concerns do not leak into our pure Domain or Use Case layers.

We need a baseline for:

1. **API Documentation:** Automatically generated, standard-compliant, and easy to explore.
2. **Error Contracts:** A globally consistent JSON structure for HTTP errors that provides enough context for debugging (integrating with the tracing introduced in ADR-010).

## Decision Drivers

- Provide a predictable, standardized way for external clients to interact with the API and handle errors.
- Avoid custom JSON error structures and out-of-date wiki-based API documentation.
- Keep infrastructural HTTP concerns out of the pure Domain and Use Case layers (ADR-005).
- Provide enough error context for debugging, integrating with tracing (ADR-010).

## Considered Options

- Custom JSON Error Format.
- Contract-First OpenAPI (writing `.yaml` first, generating Java code).
- `@ResponseStatus` on Domain Exceptions.
- Problem Details for HTTP APIs (RFC 7807) + Code-First OpenAPI.

## Decision Outcome

Chosen option: "Problem Details for HTTP APIs (RFC 7807) + Code-First OpenAPI."

### 1. Standard Error Responses (RFC 7807)

- We adopt **Problem Details for HTTP APIs (RFC 7807)** as the single, global format for all API errors.
- We will leverage Spring Boot 3's built-in support (`spring.mvc.problem-details.enabled=true`).
- **Traceability:** Every Problem Detail response must include the `traceId` (from Micrometer/MDC) to allow clients to report exact failure identifiers.

### 2. API Documentation (OpenAPI 3.0)

- We adopt the **Code-First OpenAPI** approach using `springdoc-openapi-starter-webmvc-ui`.
- The Swagger UI will be exposed locally and in non-production environments to facilitate rapid frontend integration and manual testing.

### 3. Hexagonal Architecture Constraints (Strict Rules)

- **Domain Purity:** Domain exceptions (e.g., `UserNotFoundException`, `InvalidTenantOperationException`) MUST NOT contain any Spring Web annotations (such as `@ResponseStatus`).
- **Adapter Responsibility:** The mapping between Domain Exceptions and HTTP Status Codes must happen exclusively in the Inbound Web Adapter layer.
- **API Annotations:** Swagger/OpenAPI annotations (`@Tag`, `@Operation`, `@Schema`) are allowed **ONLY** on REST Controllers and Request/Response DTOs within the web adapter layer. They must never appear on Domain Entities or Use Case (Inbound Port) interfaces.

### Consequences

**Positive**

- **Predictable Client Integrations:** Frontend teams and API consumers always know what error structure to expect.
- **Automated Sync:** API documentation is always in sync with the actual code (Code-First approach).
- **Clean Architecture Maintained:** The core domain remains agnostic of the web delivery mechanism (HTTP/REST).
- **Better Debugging:** Including `traceId` in the API error response significantly reduces Mean Time To Resolution (MTTR) when users report bugs.

**Negative**

- **Controller Clutter:** Swagger annotations can make the Inbound Adapter layer visually noisy.
- **Mapping Boilerplate:** Developers must explicitly map domain exceptions to Problem Details in a global exception handler rather than simply annotating the exceptions.

### Confirmation

<!-- TODO: fill in — original ADR does not describe a confirmation/verification mechanism -->

## Pros and Cons of the Options

### Custom JSON Error Format

- Bad, because it forces every client to write custom parsing logic for our specific starter (rejected). Historically common (e.g., `{ "errorMessage": "...", "errorCode": 123 }`), whereas RFC 7807 is an IETF standard natively supported by Spring Boot 3, providing immediate interoperability.

### Contract-First OpenAPI (Writing `.yaml` first, generating Java code)

- Bad, because it introduces a steeper learning curve and heavy build-plugin complexity (e.g., OpenAPI Generator Maven/Gradle plugins) (rejected). Arguably better for cross-team API design, but for a lightweight microservice starter the Code-First approach offers a much better Developer Experience (DX) and faster iteration speed.

### `@ResponseStatus` on Domain Exceptions

- Bad, because it directly violates the Lightweight Hexagonal Architecture (ADR-005) — the Domain must not know about HTTP (rejected). Extremely common in Spring Boot tutorials, but not compatible with this project's architecture.

### Problem Details for HTTP APIs (RFC 7807) + Code-First OpenAPI (chosen)

- Good, because of predictable client integrations (frontend teams and API consumers always know what error structure to expect).
- Good, because API documentation is always automatically kept in sync with the actual code (Code-First approach).
- Good, because the core domain remains agnostic of the web delivery mechanism (HTTP/REST).
- Good, because including `traceId` in the API error response significantly reduces Mean Time To Resolution (MTTR).
- Bad, because Swagger annotations can make the Inbound Adapter layer visually noisy.
- Bad, because developers must explicitly map domain exceptions to Problem Details in a global exception handler rather than simply annotating the exceptions.

## Notes for AI

When implementing or scaffolding features based on this ADR, the AI must adhere to the following steps:

1. **Dependency Management:** Add `springdoc-openapi-starter-webmvc-ui` to the build script. Enable problem details in `application.yml` (`spring.mvc.problem-details.enabled: true`).
2. **Global Exception Handler:** Create a `@RestControllerAdvice` (e.g., `GlobalExceptionHandler`) in the `platform-web` (or equivalent web adapter) module.
3. **Exception Mapping:** Implement `@ExceptionHandler` methods that catch specific Domain Exceptions and return Spring's `ProblemDetail` object.
4. **TraceId Injection:** Extend the `ProblemDetail` generation to automatically inject the current `traceId` from the Tracer/MDC context into the `ProblemDetail` properties map (e.g., `problemDetail.setProperty("traceId", currentTraceId)`).
5. **Validation:** Ensure that `MethodArgumentNotValidException` (DTO validation errors) are also mapped into RFC 7807 format, including specific field errors in the `properties` map.

## More Information

<!-- TODO: fill in — original ADR does not reference further material beyond the Related ADRs already listed above -->
