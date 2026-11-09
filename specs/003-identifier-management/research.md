# Research: Identifier Management

**Feature**: `003-identifier-management` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

Each section records a decision, why it was made, and the alternatives considered. Sections marked
**Provisional (owner to confirm)** were approved by the project owner on 2026-11-09 as plan
decisions D-1 to D-10; they settle a non-blocking open question of the spec (OQ-002, OQ-003,
OQ-006, OQ-008, OQ-005 residual) as a recorded decision (constitution I). They change no explicit
requirement, they can be changed later through configuration or a spec amendment, and they are
listed again in plan.md so the owner can approve or replace them.

## R-01 Domain module and package

- **Decision**: a new backend domain `com.pim.backend.identifier`, with the layers of constitution
  XII (`controller`, `service`, `repository`, `model`, `dto`, `config`). No existing package is
  modified; `product` is untouched (spec A-006).
- **Rationale**: section 3.1 "Administración de Identificadores" is its own requirement area
  (constitution III, IV). The name follows the spec's terminology. Other domains will later call
  `IdentifierService` (constitution III: the service is the only entry point) or the REST API
  (FR-012).
- **Alternatives**: placing it in `common` (rejected: `common` must not hold business rules, XII);
  inside `product` (rejected: identifiers cover six object types, not only products).

## R-02 Storage: in memory

- **Decision**: object-type configurations and registered system IDs are kept **in memory**,
  behind two repository interfaces (`ObjectTypeConfigurationRepository`, `SystemIdRegistry`), with
  in-memory implementations. Configurations are seeded at startup (R-05). Registered IDs start
  empty and are lost on restart.
- **Rationale**: constitution IX 2.2.0 allows MongoDB only for the **product catalog**; "all other
  backend data MUST use in-memory mock data … until a specification and a constitution amendment
  extend MongoDB to it". System IDs of attributes, LOVs, templates, classifications and interfaces
  are not product catalog data, and keeping product IDs in a different store from the other five
  would split one global uniqueness rule (FR-001) across two stores. Repository interfaces keep
  MongoDB a later swap (IX, XII).
- **Alternatives**: MongoDB for the registry (rejected for now: needs a constitution amendment
  first; recommended for the feature that makes IDs durable); MongoDB for products only (rejected:
  global uniqueness across two stores, and it would add data to the feature 002 catalog, OQ-009).
- **Consequence**: IDs do not survive a backend restart. Acceptable for this phase (no real data,
  constitution IX); recorded in plan.md.

## R-03 API shape

- **Decision**: REST under `/api/v1` (constitution VII, ADR 0001):
  - `POST /api/v1/system-ids`: assign a system ID to a new object (manual or automatic) and apply
    the duplicate-ID rule. 201 (assigned), 200 (same object, FR-009a), 409 (different object,
    FR-009b), 403 (manual ID without permission, FR-003), 400 (invalid request).
  - `GET /api/v1/system-ids/{systemId}`: read one registered ID. 200 or 404.
  - `PUT` and `PATCH /api/v1/system-ids/{systemId}`: always rejected with 405 and a Spanish message
    saying a system ID can never be changed (FR-007).
  - `GET /api/v1/object-types`: the six object types with their configuration (collection envelope).
  - `GET /api/v1/object-types/{objectType}`, `PUT /api/v1/object-types/{objectType}`: read and
    replace one type's configuration (sequence and key-field declaration; FR-006, FR-013).
- **Rationale**: plural nouns and HTTP verbs (VII). Global uniqueness (FR-001) means an ID alone
  identifies its object, so `system-ids/{systemId}` needs no type in the path. Explicit `PUT`/`PATCH`
  handlers give the "explanatory message" of FR-007 instead of Spring's generic 405.
- **Alternatives**: `POST /object-types/{type}/system-ids` (rejected: reads by ID would also need
  the type, which uniqueness makes redundant); no `PUT`/`PATCH` handler (rejected: the generic 405
  has no Spanish explanation, FR-007, FR-011).

## R-04 "Same object" check and the "update"

- **Decision**: the registry keeps, for each ID, its object type and the key-field values sent at
  assignment. On `POST` with an existing ID: same type and identical values in every declared key
  field → **200 with outcome `SAME_OBJECT`** and the existing registration; otherwise **409**.
  Values are compared exactly, after trimming surrounding spaces.
- **Rationale**: FR-009(a) and the 2026-11-09 clarification. The only object data this feature
  owns is type, ID and key-field values, and those are equal by definition, so the "update" of
  FR-009(a) changes nothing here: the 200 tells the calling feature to update its own object.
  Nothing is created (SC-005).
- **Alternatives**: letting the registry store and merge other object data (rejected: that data
  belongs to the owning features, spec A-001, FR-013).

## R-05 Default configuration and sequence format — **Provisional (owner to confirm)** (OQ-002)

- **Decision**:
  - Sequence settings: `kind` (`NUMERIC` or `ALPHANUMERIC`), `prefix` (letters A–Z, 1–10, required
    for `ALPHANUMERIC`, absent for `NUMERIC`), `minimumDigits` (1–18, zero padding), `nextValue`
    (1 or more). A generated ID is `prefix` + `nextValue` padded to `minimumDigits`; the number grows
    past the padding when needed, so a sequence does not run out in practice (the limit is the
    largest 18-digit value; reaching it gives 409 `SEQUENCE_EXHAUSTED`).
  - Defaults, seeded at startup: every type `ALPHANUMERIC`, `minimumDigits` 6, `nextValue` 1, with
    prefixes `PRD` (product), `ATR` (attribute), `LOV` (list of values), `PLT` (template), `CLS`
    (classification), `INT` (interface). First product ID: `PRD000001`.
  - Who may change a configuration: not restricted in this feature (spec FR-012 defines no
    permission for it and user/role administration is out of scope). Recorded as a deviation to
    revisit when authentication exists.
- **Rationale**: section 3.1 requires only "numeric or alphanumeric" and "per object type".
  Distinct default prefixes keep the six sequences apart, as global uniqueness needs (OQ-002 note);
  R-06 guarantees uniqueness even if a configuration makes two sequences overlap.
- **Alternatives**: base-36 counters for alphanumeric (rejected: hard to read, no business ask);
  disjoint number ranges per type (rejected: harder to configure and still needs R-06); a
  permission for configuration changes (rejected: would invent a business rule).

## R-06 Generated-ID collision — **Provisional (owner to confirm)** (OQ-003)

- **Decision**: when the next generated value is already used by any object of any type (for
  example, given manually earlier), the generator **skips it and takes the next free value**. It
  never saves a used ID and never fails because of a collision. Skipped values are not reused.
- **Rationale**: section 3.1 asks for an «inédita» (never used) ID (FR-005). Skipping keeps
  creation working without asking anything of the caller.
- **Alternatives**: rejecting the creation (rejected: the caller gave no ID and cannot fix it);
  forbidding manual IDs that match a sequence format (rejected: invents a business rule, OQ-008).

## R-07 Manual ID format and comparison — **Provisional (owner to confirm)** (OQ-008)

- **Decision**: surrounding spaces are trimmed; an empty or blank value means "no ID given" (spec
  A-004). A manual ID must then be 1–40 characters of letters A–Z/a–z, digits, `-`, `_` or `.`;
  otherwise 400 with code `INVALID_SYSTEM_ID`. IDs are stored as given but **compared ignoring
  letter case**, so `a-100` and `A-100` are the same ID. A manual ID need not follow its type's
  sequence format.
- **Rationale**: a restricted, URL-safe character set lets the ID appear in `GET /system-ids/{id}`
  without encoding problems. Case-insensitive comparison prevents two IDs that people would read as
  the same.
- **Alternatives**: case-sensitive comparison (rejected: allows confusable duplicates); requiring
  the sequence format (rejected: no business ask).

## R-08 Manual-ID permission without authentication — **Provisional (owner to confirm)** (FR-003, A-003)

- **Decision**: one permission per object type (FR-003). Until authentication exists, the
  requesting user's permissions arrive in a **development header**
  `X-Pim-Manual-Id-Permissions`, a comma-separated list of object-type codes (for example
  `PRODUCT,TEMPLATE`). One class in `identifier.controller` reads it and passes a
  `ManualIdPermissions` model value to the service; the service decides. A manual ID for a type not
  in the header → 403 `MANUAL_ID_NOT_PERMITTED` with a message naming the requested ID (FR-011),
  nothing saved, no automatic ID instead.
- **Rationale**: constitution VIII asks that authorization can be added later without changing
  paths or bodies: identity stays out of both, and only the header reader changes when real
  authentication arrives. No new dependency (Spring Security is not introduced).
- **Alternatives**: Spring Security with a stub user (rejected: a new dependency and configuration
  for a placeholder, II and V); a permission field in the body (rejected: VIII); a fixed set in
  configuration (rejected: cannot test the authorized and unauthorized cases in one run).
- **Risk**: anyone can send the header. Acceptable only while the platform has no authentication
  and no real data (IX); recorded in Complexity Tracking.

## R-09 Empty key-field declaration — **Provisional (owner to confirm)** (OQ-005 residual)

- **Decision**: every type starts with an **empty** key-field declaration (FR-013: this feature
  fixes no key fields). While a type declares none, a `POST` that reuses an existing ID of that type
  cannot be confirmed as the same object and gets **409**. When a type declares key fields, a
  `POST` for that type must send a value for each one (400 `MISSING_KEY_FIELD` otherwise); extra
  fields are rejected (400 `UNKNOWN_KEY_FIELD`).
- **Rationale**: treating "no key fields" as "same object" would bring back the "same type is
  enough" rule the owner rejected (clarification 3). Refusing is the safe default.
- **Alternatives**: treat as same object (rejected, as above); require at least one key field per
  type (rejected: FR-013 leaves the lists to the owning features).

## R-10 Concurrency — **Provisional (owner to confirm)** (OQ-006)

- **Decision**: assignment (generate or validate, check, register) runs as one atomic step in the
  service, guarded by a single lock. Generated IDs per type increase but may have gaps (R-06).
  When two requests send the same manual ID at the same moment, the first registered wins and the
  second gets the FR-009 outcome (200 or 409). Configuration changes take the same lock.
- **Rationale**: the backend is one instance (constitution III), so one in-process lock is enough
  and simplest to reason about. SC-002 is verified with 100 parallel requests.
- **Alternatives**: lock-free maps plus atomic counters per type (rejected: a global uniqueness
  check spans types, which makes per-type atomics insufficient); a database constraint (rejected:
  storage is in memory, R-02). A future durable store needs a unique index instead.

## R-11 Errors

- **Decision**: all errors use `common.ApiProblems` (RFC 9457, ADR 0001), from a handler limited
  to the identifier controllers (`identifier.controller.IdentifierExceptionHandler`), like
  `ProductExceptionHandler`. `ApiProblems` gains one generic builder for problems without field
  errors (403, 404, 405, 409), with a Spanish title and detail; the 409 detail names the ID and asks
  for a new, unused one (FR-009b, FR-011).
- **Rationale**: one error format (VII); domain-specific messages stay in the domain (XII).
- **Alternatives**: a global handler in `common` (rejected: identifier messages are
  domain-specific).

## R-12 Testing

- **Decision**: unit tests for `model` value objects and `IdentifierService` (with in-memory
  repositories); `@SpringBootTest` + MockMvc integration tests for every endpoint and every error
  status; one concurrency test (100 parallel assignments, SC-002) and one volume test (1,000
  assignments). These tests do not need MongoDB beyond what the existing context startup already
  needs (the local `pim-test` database used by feature 002's tests).
- **Rationale**: constitution VI. No new test dependency.

## R-13 Frontend

- **Decision**: no frontend change (FR-012, clarification 2).
