# User Registration Flow

## Purpose of this document

This document is an onboarding guide for developers joining the project.  
It explains:

- **What “user registration” means in this system**
- **Which components are involved and how they interact**
- **Where the important architectural decisions and risks are**  
  (multi-tenancy, security, outbox, correlation IDs)

The focus is on **real behavior and architecture**, not on writing prose for its own sake.

---

## Business overview

- **Endpoint**: `POST /api/v1/users/register`
- **Input** (`RegisterUserRequest`):
  - `email`
  - `password`
  - `firstName`
  - `lastName`
- **Goal**:
  - Create a new `User` within the current **tenant**.
  - Store a **BCrypt password hash** (cost factor 12).
  - Enforce **email uniqueness per tenant**, case-insensitive.
  - Emit a **domain event** that drives a notification (e.g. welcome email) via the outbox pattern.

**Guarantees:**

- User row and outbox row are created in **one database transaction**.
- The same email can exist in **different tenants**, but not twice within the same tenant.
- Only the **password hash** is stored; raw passwords are never persisted or logged.

---

## Architectural placement

The flow follows the project’s hexagonal architecture.

- **Inbound adapter (web)**  
  `sample-service/src/main/java/.../api/controller/UserController.java`
- **Use case (inbound port implementation)**  
  `sample-service/src/main/java/.../application/usecase/RegisterUserUseCase.java`
- **Domain**  
  `User`, `UserPreferences`, `UserAlreadyExistsException`
- **Outbound ports**
  - `CheckUserExistsPort` – tenant-aware uniqueness check
  - `SaveUserPort` – persisting the `User`
- **Outbound adapters (persistence)**
  - `UserPersistenceAdapter`
  - `UserEntityRepository` (Spring Data JPA)
- **Shared infrastructure**
  - `TenantContextHolder` – current tenant
  - `CorrelationContextHolder` – correlation ID
  - `PasswordEncoder` – hashing abstraction (BCrypt adapter lives in `platform-infrastructure`)
  - `DomainEventPublisher` – publishing domain events (to outbox / Spring events)

### Component diagram (ASCII)

```text
[HTTP Client]
     |
     v
[UserController] --(RegisterUserRequest DTO)----------------------.
     |                                                            |
     v                                                            |
[RegisterUserUseCase]                                             |
     |           uses                          uses               |
     |--------------------.                 .---------------------'
     v                    v                 v
[CheckUserExistsPort]  [PasswordEncoder]  [SaveUserPort]
     |                    |                 |
     v                    |                 v
[UserEntityRepository]    |         [UserPersistenceAdapter]
                          |                 |
                          '--------.  .-----'
                                   v  v
                         [DomainEventPublisher]
                                   |
                                   v
                           [Outbox / Kafka / etc.]
```

---

## End-to-end request flow

### 1. HTTP entry point

1. Client calls:
   - `POST /api/v1/users/register`
   - JSON body mapped to `RegisterUserRequest`.
2. `UserController.registerUser(...)`:
   - Validates the payload via `@Valid`.
   - Passes primitive values (`email`, `password`, `firstName`, `lastName`) to `RegisterUserUseCase.execute(...)`.
   - Does **not** contain domain logic; it is a thin inbound adapter.

ASCII:

```text
Client
  |
  | POST /api/v1/users/register (RegisterUserRequest)
  v
UserController.registerUser()
  |
  v
RegisterUserUseCase.execute(...)
```

---

### 2. Use case orchestration (`RegisterUserUseCase`)

`RegisterUserUseCase` is responsible for:

- enforcing preconditions (tenant context present),
- orchestrating validation and checks (email normalization, uniqueness),
- coordinating persistence,
- publishing the domain event.

Key steps inside `execute(...)`:

1. **Resolve tenant**

   ```text
   tenantId = TenantContextHolder.getTenantId()
   if tenantId == null -> IllegalStateException
   ```

   The flow assumes that **some upstream component** (e.g. filter, interceptor) has already set the tenant context for the current request.

2. **Normalize email**

   ```text
   normalizedEmail = email.trim().toLowerCase()
   ```

   This is crucial for:
   - predictable uniqueness checks,
   - avoiding duplicate accounts caused by case differences,
   - safely comparing emails in queries and constraints.

3. **Uniqueness check (fail-fast)**

   ```text
   if checkUserExistsPort.existsByEmail(normalizedEmail) then
       throw UserAlreadyExistsException(normalizedEmail)
   ```

   - This is a **tenant-aware** check.
   - It is a **guard** before hitting the database unique constraint.
   - This protects the user experience by failing quickly when a duplicate is obvious.

4. **Password hashing**

   ```text
   passwordHash = passwordEncoder.encode(password)
   ```

   - Implementation uses BCrypt with **cost factor 12** (see tests).
   - Only the hash is stored; raw passwords never leave the use case.

5. **Create domain aggregate**

   ```text
   defaultPreferences = new UserPreferences(true, false)
   user = User.createWithPassword(
       tenantId,
       normalizedEmail,
       null,                // phoneNumber for this flow is absent
       firstName,
       lastName,
       defaultPreferences,
       passwordHash
   )
   ```

   - Domain object is responsible for **enforcing invariants** (e.g. non-null fields).
   - At this point, the domain model already contains a **password hash**, never a raw password.

6. **Persist user and handle races**

   ```text
   try:
       savedUser = saveUserPort.save(user)
   catch DataIntegrityViolationException:
       throw new UserAlreadyExistsException(normalizedEmail)
   ```

   - `saveUserPort` is implemented by the persistence adapter around JPA.
   - The database enforces a **unique constraint on (email, tenant_id)**.
   - If another concurrent request sneaks in between the uniqueness check and the insert, the DB throws `DataIntegrityViolationException`, which is mapped back to `UserAlreadyExistsException`.

7. **Publish domain event with correlation ID**

   ```text
   correlationId = CorrelationContextHolder.getCorrelationId()
   event = UserCreatedEvent.of(savedUser.id(), tenantId, correlationId)
   eventPublisher.publish(event)
   ```

   - The `DomainEventPublisher` implementation bridges the domain event into the outbox / messaging layer.
   - The **correlation ID** flows from the HTTP layer into the event so downstream systems can trace the request.

8. **Return result**

   - The use case returns the newly created `User` aggregate.
   - `UserController` maps it back to `UserResponse` DTO and returns `201 Created`.

---

## Transactional and outbox behavior

The registration flow is **transactional**:

- `@Transactional` on `RegisterUserUseCase.execute(...)` wraps:
  - inserting the `User`,
  - writing the **outbox message**.
- If **any** part fails (including the outbox write):
  - the **entire transaction is rolled back**,
  - no partially registered user is left in the database.

Tests in `UserRegistrationIntegrationTest` verify that:

- A successful registration:
  - persists a `UserEntity` with:
    - normalized email,
    - non-null password hash starting with `$2a$12$`,
  - persists an outbox record with:
    - correct message type (e.g. `WELCOME_EMAIL_REQUEST`),
    - destination (e.g. `notification-events`),
    - partition key equal to the `user.id`,
    - status `PENDING`,
    - correlation ID present in headers (JSONB).
- Unique constraint is enforced both:
  - logically (via `CheckUserExistsPort`),
  - physically (via DB constraint on `(email, tenant_id)`).
- The same email can be used in **different tenants**.

ASCII view of the transaction:

```text
BEGIN TRANSACTION
  INSERT INTO users (..., email, tenant_id, password_hash, ...)
  INSERT INTO message_outbox (..., message_type, headers, status, ...)
COMMIT
```

If any insert fails:

```text
ROLLBACK
```

---

## Multi-tenancy

- Every user belongs to a **tenant** (`tenantId`).
- Tenant context is provided by `TenantContextHolder`:
  - typically set by a web filter / interceptor before the controller runs.
- The same email:
  - **cannot** be reused within the same tenant,
  - **can** be reused in another tenant (different `tenantId`).

This design keeps tenant isolation strict while still allowing shared emails across tenants when needed.

---

## Correlation IDs

- `CorrelationContextHolder` carries a `correlationId` per request.
- It is:
  - set at the web boundary (e.g. from an HTTP header like `x-correlation-id`, or generated if missing),
  - read by `RegisterUserUseCase` to enrich `UserCreatedEvent`.
- Downstream services and logs can join on this correlation ID to reconstruct the full story of a registration.

---

## Security considerations

- **Password handling**
  - Raw passwords exist only:
    - in the HTTP request body,
    - in a local variable inside `RegisterUserUseCase`.
  - Before persistence, the password is passed through `PasswordEncoder.encode(...)` (BCrypt, cost 12).
  - Only the hash is written to the database; hashes start with `$2a$12$` and have length 60.

- **Email normalization**
  - Prevents multiple accounts that differ only by letter case.
  - Simplifies queries and auditing.

- **No leaking secrets**
  - The system must not log raw passwords or full hashes.
  - Audit and debug logs should use correlation IDs and user IDs, not credentials.

---

## Where to start when changing the flow

If you need to extend or modify user registration, typical entry points are:

1. **Change HTTP contract / validation**
   - `RegisterUserRequest` (add fields, validation annotations).
   - `UserController.registerUser(...)` (map new fields into the use case).

2. **Change business rules**
   - `RegisterUserUseCase` (e.g. different password policy, additional checks, extra events).
   - Domain model `User` and `UserPreferences` if invariants change.

3. **Change persistence behavior**
   - `SaveUserPort` / `UserPersistenceAdapter` / `UserEntityRepository`.
   - Database migrations to adjust constraints or schema (e.g. composite indexes for multi-tenant constraints).

4. **Change integration behavior**
   - `DomainEventPublisher` or the outbox processing logic (e.g. topic name, message format).
   - Downstream consumers of the `UserCreatedEvent`.

Before making changes, look at:

- `RegisterUserUseCaseTest` – unit tests for use case behavior.
- `UserRegistrationIntegrationTest` – full integration tests (Postgres + outbox).
- ADRs related to user registration and security (e.g. email normalization, password hashing policy, outbox guarantees).

---

