# User Registration Flow (ST-008)

## Purpose

This document explains the **User Registration flow** introduced in story [#24](https://github.com/pidabrow/spring-microservice-starter/issues/24).  
It focuses on:

- How a new user is registered in a **multi-tenant** environment.
- How **password security** and **email uniqueness** are enforced.
- How **Correlation ID** is propagated through the system.
- How the flow integrates with the **Transactional Outbox** and **audit log**.

---

## High-Level Overview

### End-to-End Flow (Happy Path)

```text
Client
  |
  | 1) POST /api/v1/users/register
  v
Sample Service (Web)
  - CorrelationContextInterceptor   (X-Correlation-Id -> CorrelationContextHolder)
  - TenantContextInterceptor        (X-Tenant-Id -> TenantContextHolder)
  - ActorContextInterceptor         (X-Actor-*) -> ActorContextHolder
  - UserController.registerUser()
  |
  v
Sample Service (Application)
  - RegisterUserUseCase
      1) normalize email (trim + lowercase)
      2) tenant-aware uniqueness check
      3) hash password with BCrypt (cost = 12)
      4) create User domain object with passwordHash
      5) save via SaveUserPort
      6) publish UserCreatedEvent(correlationId)
  |
  v
Platform Common
  - SpringDomainEventPublisher (DomainEventPublisher)
      -> publishes UserCreatedEvent
  |
  v
Platform Infrastructure
  - IntegrationEventListener
      DomainEvent -> MessageOutboxEntity
        message_type   = WELCOME_EMAIL_REQUEST
        origin_event_type = UserCreatedEvent
        destination    = notification-events
        partition_key  = userId
        headers:
          x-tenant-id
          x-message-type
          x-correlation-id (from UserCreatedEvent)
  - OutboxRelayService
      PENDING -> Kafka via KafkaMessagePublisher
  |
  v
Kafka
  - topic: notification-events
  - key  : userId
  - headers: x-tenant-id, x-message-type, x-correlation-id
```

---

## HTTP Layer

### Endpoint

```text
POST /api/v1/users/register
Content-Type: application/json

Headers:
  X-Tenant-Id      : <UUID> (required)
  X-Correlation-Id : <UUID> (optional; generated if missing)
  X-Actor-Type     : SYSTEM | USER (optional; defaults to SYSTEM)
  X-Actor-Id       : <UUID> (required for USER)
```

### Request DTO: `RegisterUserRequest`

```text
RegisterUserRequest {
  email     : string (required, @Email, @NotBlank)
  password  : string (required, @NotBlank)
  firstName : string (required, @NotBlank)
  lastName  : string (required, @NotBlank)
}
```

> **Note:** Phone number is not part of the registration DTO in this iteration.  
> The domain `User` record still requires a non-blank phone number, so a placeholder value is used during registration (see the Application Layer section).

### HTTP Validation & Errors

- Invalid payload (missing or malformed email/password/firstName/lastName) → `400 Bad Request`
- Duplicate email within the same tenant → `409 Conflict` with message from `UserAlreadyExistsException`
- Missing / invalid `X-Tenant-Id` → `400 Bad Request` from `TenantContextInterceptor`
- Missing / invalid actor headers (for `USER`) → `400 Bad Request` from `ActorContextInterceptor`

---

## Context Interceptors

### CorrelationContextInterceptor

```text
Intercepts every HTTP request:

1) Read X-Correlation-Id header:
   - if present and valid UUID → use it
   - else → generate UUID v7

2) Set CorrelationContextHolder.setContext(CorrelationContext(correlationId))

3) After request → CorrelationContextHolder.clearContext()
```

This provides a **single Correlation ID per request**, used later in `UserCreatedEvent` and Kafka headers.

### TenantContextInterceptor

```text
1) Read X-Tenant-Id header
2) Parse UUID
3) TenantContextHolder.setContext(TenantContext(tenantId))
4) After request → TenantContextHolder.clearContext()
```

Tenant ID is a **security boundary** and is later used in:

- User creation (`User.tenantId`)
- Hibernate tenant filter
- Outbox records (`tenant_id` column, `x-tenant-id` header)

### ActorContextInterceptor

```text
1) Read X-Actor-Type, X-Actor-Id
2) Resolve ActorContext:
   - SYSTEM (default) or
   - USER(userId)
3) ActorContextHolder.setContext(...)
4) After request → ActorContextHolder.clearContext()
```

Actor context is used by auditing and other cross-cutting concerns.

---

## Application Layer: `RegisterUserUseCase`

### Responsibilities

```text
RegisterUserUseCase.execute(
  email,
  password,
  firstName,
  lastName
)
```

1. **Tenant Resolution**

   ```text
   tenantId = TenantContextHolder.getTenantId()
   if tenantId is null → IllegalStateException
   ```

2. **Email Normalization**

   ```text
   normalizedEmail = email.trim().toLowerCase()
   ```

3. **Uniqueness Check (Fail-Fast)**

   ```text
   if (checkUserExistsPort.existsByEmail(normalizedEmail)) {
       throw UserAlreadyExistsException(normalizedEmail) // -> HTTP 409
   }
   ```

4. **Password Hashing**

   ```text
   passwordHash = passwordEncoder.encode(rawPassword)
   // BCrypt, cost factor = 12 → ~250–500 ms per hash
   ```

5. **User Creation (Domain)**

   ```text
   defaultPreferences = new UserPreferences(emailEnabled = true, smsEnabled = false)
   // Placeholder phone number used for registration; can be updated later
   placeholderPhoneNumber = "+0000000000"

   user = User.createWithPassword(
       tenantId,
       normalizedEmail,
       placeholderPhoneNumber,
       firstName,
       lastName,
       defaultPreferences,
       passwordHash
   )
   ```

   The `User` record enforces:

   - `id` non-null (UUID v7 generated by `User.createWithPassword`)
   - `tenantId`, `email`, `phoneNumber`, `firstName`, `lastName`, `preferences` all non-null / non-blank.

6. **Persistence (Atomic)**

   ```text
   savedUser = saveUserPort.save(user)
   ```

   - Internally uses JPA (`UserPersistenceAdapter` / `UserEntity`).
   - Unique DB constraint on `(email, tenant_id)` ensures **source-of-truth** uniqueness.

7. **Handling Race Conditions**

   ```text
   try {
       savedUser = saveUserPort.save(user)
   } catch (DataIntegrityViolationException e) {
       throw new UserAlreadyExistsException(normalizedEmail);
   }
   ```

   This covers concurrent registration attempts that pass the in-memory `existsByEmail` check but clash on the DB-level constraint.

8. **Domain Event Publishing**

   ```text
   correlationId = CorrelationContextHolder.getCorrelationId()
   event = UserCreatedEvent.of(savedUser.id(), tenantId, correlationId)
   eventPublisher.publish(event)
   ```

   - Event is a **record implementing `DomainEvent`**.
   - Contains `entityId`, `tenantId`, `entityType = "User"`, `occurredAt`, and `correlationId`.

---

## Persistence Layer

### UserEntity

Relevant aspects for registration:

- JPA entity `UserEntity` (adapter) maps to domain `User`:

  ```text
  fields:
    id           : UUID (v7)
    tenant_id    : UUID
    email        : String (column: email, NOT NULL)
    phone_number : String (NOT NULL)
    first_name   : String (NOT NULL)
    last_name    : String (NOT NULL)
    preferences  : JSONB
    password_hash: String (NULLABLE)
  ```

- Mapping:

  ```text
  UserEntity.fromDomain(User user)
  -> copies email, phone, names, preferences, passwordHash
  ```

- Domain model remains immutable; `UserEntity` exists purely in the persistence adapter.

### Migration: `V7__add_password_hash_and_unique_email_constraint.sql`

```sql
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

CREATE UNIQUE INDEX idx_users_email_tenant_unique
  ON users(email, tenant_id);
```

Effects:

- `password_hash` is now persisted on the `users` table.
- `(email, tenant_id)` is unique → final authority for email uniqueness within tenant.

---

## Transactional Outbox Integration

### IntegrationEventListener

```text
@EventListener
void handleDomainEvent(DomainEvent event) {
    messageType       = resolveMessageType(event);
    originEventType   = event.getClass().getName();
    destination       = resolveDestination(event);
    partitionKey      = event.entityId().toString();
    tenantId          = event.tenantId();
    payload           = serializeEvent(event); // JSON map
    correlationId     = extractCorrelationId(event);

    headers = {
      "x-tenant-id"     : tenantId.toString(),
      "x-message-type"  : messageType,
      "x-correlation-id": correlationId.toString()
    };

    outboxRecord = MessageOutboxEntity.create(
        tenantId,
        messageType,
        originEventType,
        destination,
        partitionKey,
        payload,
        headers
    );

    outboxRepository.save(outboxRecord); // same DB transaction as user insert
}
```

#### Message Type Mapping

```text
resolveMessageType(event):

  case UserCreatedEvent           -> "WELCOME_EMAIL_REQUEST"
  case UserUpdatedEvent           -> "USER_UPDATED"
  case UserDeletedEvent           -> "USER_DELETED"
  case EntityCreatedEvent         -> "ENTITY_CREATED"
  case EntityUpdatedEvent         -> "ENTITY_UPDATED"
  case NotificationRequestedEvent -> "NOTIFICATION_REQUESTED"
```

> This is **intentional**:  
> In the context of external integrations, `UserCreatedEvent` → **"WELCOME_EMAIL_REQUEST"**  
> (see ADR-008: event strategy).

#### Destination Mapping

```text
resolveDestination(event):

  case UserCreatedEvent, NotificationRequestedEvent -> "notification-events"
  default                                            -> "domain-events"
```

#### Correlation ID Extraction

```text
extractCorrelationId(event):

  if (event instanceof UserCreatedEvent uce)
      return uce.correlationId();
  else
      return UuidV7Generator.generate(); // fallback
```

This guarantees that:

- For **UserCreatedEvent**, the outbox record (and Kafka message) carries the **same Correlation ID** as the original HTTP request.
- For other events, a fresh Correlation ID is generated if needed.

---

## Kafka Publishing

### OutboxRelayService + KafkaMessagePublisher

High-level:

```text
OutboxRelayService:
  - Polls message_outbox for PENDING records
  - For each record:
      messagePublisher.publish(destination, partitionKey, payload, headers)
      -> on success: status = SENT
      -> on failure: retry_count++, possibly FAILED after max retries

KafkaMessagePublisher:
  - Builds ProducerRecord<String, String>
  - Sets headers:
      x-tenant-id
      x-message-type
      x-correlation-id
  - Sends synchronously (get()) and handles errors
```

### Message Shape

```text
Kafka Topic: notification-events

Key    : userId (string)
Value  : JSON serialized DomainEvent (UserCreatedEvent payload)
Headers:
  x-tenant-id     : <tenant UUID>
  x-message-type  : "WELCOME_EMAIL_REQUEST"
  x-correlation-id: <correlationId from HTTP request>
```

This ensures per-user ordering and end-to-end traceability.

---

## Impact on Audit Log

### What Changed for Auditing?

1. **Domain Events**

   - `UserCreatedEvent` now carries a `correlationId`.
   - This additional field enhances traceability but does **not** change the core audit semantics:
     - `entityId`, `tenantId`, `entityType`, `occurredAt` remain intact.

2. **AuditLog Mechanism**

   - The audit log is implemented via **separate listeners** (e.g. `AuditLogListener`) and/or JPA entity listeners in `platform-data`.
   - It reacts to:
     - Domain events (`UserCreatedEvent`, `UserUpdatedEvent`, etc.)
     - Persistence changes to audited entities.

   The **mapping of `UserCreatedEvent` → "WELCOME_EMAIL_REQUEST"** in `IntegrationEventListener` is **only for the outbox** (integration layer).  
   It does **not** alter the domain event used by the audit subsystem.

3. **Traceability**

   - Since `UserCreatedEvent` now contains `correlationId`, an audit record created in response to this event can:
     - Log or store this ID for easier cross-system correlation (optional extension).
   - Even if the current audit schema does not have an explicit `correlation_id` column, logs and metrics can use it to link:
     - HTTP request
     - domain events
     - audit entries
     - outbox records
     - Kafka messages

### Summary of Audit Impact

- **No breaking changes** to `audit_log` semantics.
- **Additional capability**:
  - End-to-end tracing possible via `correlationId`.
  - Audit log entries for user creation can be correlated with outbox messages and Kafka events using:
    - `tenant_id`,
    - `entity_id` (userId),
    - and optionally `correlationId` (if persisted or logged).

---

## Sequence Diagram (ASCII)

### Full Registration + Outbox + Audit (Conceptual)

```text
Client            Web Layer           Application           Domain/Event        Outbox              Kafka       Audit
  | POST /register  |                     |                      |                |                  |           |
  |---------------->|                     |                      |                |                  |           |
  |                 | CorrelationContext  |                      |                |                  |           |
  |                 | TenantContext       |                      |                |                  |           |
  |                 | ActorContext        |                      |                |                  |           |
  |                 |-------------------->| RegisterUserUseCase  |                |                  |           |
  |                 |                     | normalize email      |                |                  |           |
  |                 |                     | existsByEmail?       |                |                  |           |
  |                 |                     | hash password (BCrypt)|               |                  |           |
  |                 |                     | create User (domain) |                |                  |           |
  |                 |                     | saveUserPort.save()  |--> UserEntity  |                  |           |
  |                 |                     |                      |                |                  |           |
  |                 |                     | publish(UserCreatedEvent+correlationId)|                 |           |
  |                 |                     |--------------------->| DomainEvent    |                  |           |
  |                 |                     |                      |                |                  |           |
  |                 |                     |                      |  AuditListener |--> AuditLog       |           |
  |                 |                     |                      |                |                  |           |
  |                 |                     |                      |  IntegrationEventListener         |           |
  |                 |                     |                      |---------------->| MessageOutbox(PENDING)|      |
  |                 |                     |                      |                |                  |           |
  | 201 CREATED     |                     |                      |                |                  |           |
  |<----------------|                     |                      |                |                  |           |
  |                 |                     |                      |                | OutboxRelayService            |
  |                 |                     |                      |                |--------------->| Kafka msg  |
  |                 |                     |                      |                | status=SENT     |           |
```

---

## Notes & Future Improvements

- **Phone Number Handling**
  - Currently a placeholder is used for `phoneNumber` during registration.
  - Future improvement: extend `RegisterUserRequest` and `RegisterUserUseCase` to accept a real phone number and drop the placeholder.

- **Correlation ID in AuditLog Schema**
  - If desired, add a `correlation_id` column to `audit_log` and populate it from events.
  - This would provide first-class DB-level correlation in addition to log-based correlation.

- **ArchUnit Rules**
  - Ensure no domain/application code depends directly on `BCryptPasswordEncoder` or other Spring Security classes.
  - Only the `PasswordEncoder` port should be visible outside infrastructure.


