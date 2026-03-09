# ADR-008 — User Registration & Password Security

**Focus:** Security, Data Integrity, Observability  
**Context:** Implementation of the User Registration flow (ST-008) in a multi-tenant environment.

## Context
User registration is a critical entry point that must balance security (password protection), data integrity (no duplicates), and observability (traceability across systems). We need to define how passwords travel through the system and how we ensure identity uniqueness.

## Decision

### 1. Password Handling & Hashing
- **Security Boundary**: Raw passwords are accepted ONLY in the Inbound Adapter (REST DTO) over HTTPS.
- **Hashing Algorithm**: The Use Case layer must hash the password using **BCrypt** before creating the `User` entity.
- **Cost Factor**: The BCrypt cost factor is set to **12**.
    - *Rationale*: This provides a ~250-500ms delay per check, which is the industry "sweet spot" to thwart brute-force attacks on modern hardware without degrading UX.
- **Entity Safety**: The `User` JPA entity MUST NEVER hold plaintext passwords. Only `password_hash` is allowed.

### 2. Email Normalization & Uniqueness
- **Normalization**: To prevent account duplication and spoofing, all emails must be converted to **lowercase** before any validation or persistence logic.
- **Uniqueness Enforcement**:
    - Business logic will perform a "fail-fast" check using `existsByEmail`.
    - The final source of truth is a **Database Unique Constraint** on `(email, tenant_id)`.
    - Race conditions resulting in DB constraint violations must be mapped to a `UserAlreadyExistsException` (HTTP 409).

### 3. Traceability (Correlation ID)
- The flow must preserve the **Correlation ID** from the HTTP Request.
- This ID must be propagated to the `UserCreatedEvent` and subsequently to the **Kafka Headers** via the Transactional Outbox. This allows end-to-end debugging of a registration triggered by a specific user request.

### 4. Event Strategy
- We will reuse the generic **`UserCreatedEvent`** (as per ADR-006).
- The `IntegrationEventListener` will listen for this event to trigger the `WELCOME_EMAIL_REQUEST` in the Outbox.

## Implementation Logic Flow
1. **Sanitize**: Trim and lowercase the email.
2. **Validate**: Perform a tenant-aware uniqueness check.
3. **Hash**: Execute `PasswordEncoder.encode(rawPassword)`.
4. **Persist**: Save `User` entity to DB.
5. **Emit**: Publish `UserCreatedEvent` (carrying the current Correlation ID).

## Consequences
- **Positive**: Robust security boundary; plaintext never leaks to logs or domain; full traceability across distributed components.
- **Negative**: Increased CPU load for BCrypt (acceptable trade-off); registration is slightly slower due to intentional hashing delay.
- **Development**: Requires strict ArchUnit rules to ensure no plaintext password fields are added to entities.

## Notes
- Sensitive data masking must be active in all loggers to prevent DTO logging from exposing raw passwords.