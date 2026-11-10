# Research: Attribute Management – Attribute Creation

**Feature**: `004-attribute-creation` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

Each section gives a decision, its rationale and the alternatives considered. Sections marked
**Provisional (owner to confirm)** settle a non-blocking open question of the spec as a recorded
decision (constitution I); they are listed again in plan.md for approval. All of them, and option A of
R-04, were approved by the project owner on 2026-11-09 as plan decisions D-1 to D-12.

## R-01 Domain module

- **Decision**: new backend domain `com.pim.backend.attribute` with the layers of constitution XII.
  The frontend gets one new page, an API client and a few new design-system form components.
- **Rationale**: §3.2 "Administración de atributos" is its own requirement area (constitution III,
  IV); the package name follows the spec's terminology.
- **Alternatives**: adding attributes to `identifier` (rejected: identifiers are cross-type; attribute
  rules belong to the attribute domain).

## R-02 Storage — **Provisional (owner to confirm)**

- **Decision**: attribute definitions are kept **in memory** behind an `AttributeRepository`
  interface, with an index of display names compared ignoring case (FR-023). They are lost on
  restart, like feature 003's registrations, which hold their system IDs.
- **Rationale**: constitution IX 2.2.0 allows MongoDB for the **product catalog** only; "all other
  backend data MUST use in-memory mock data … until a specification and a constitution amendment
  extend MongoDB to it" (spec DEP-07). Feature 003, which holds the attributes' system IDs, is also
  in memory; storing attributes durably while their IDs are lost on restart would make IDs reusable
  for stored attributes.
- **Alternatives**: MongoDB for attributes (rejected for now: needs a constitution amendment, and
  feature 003's registry would have to move to MongoDB at the same time).

## R-03 Reusing feature 003

- **Decision**: `AttributeService` calls `identifier.service.IdentifierService.assign(ATTRIBUTE,
  requestedSystemId, keyFieldValues, manualIdPermissions)`, where `keyFieldValues` holds
  `displayName` only if feature 003 declares that key field for `ATTRIBUTE` (read with
  `findConfiguration(ATTRIBUTE)`), and is empty otherwise (FR-006 as amended; feature 003 rejects
  undeclared key fields) (constitution III: another domain is
  used only through its service). All attribute rules are checked **before** the call, so a refused
  attribute never consumes or registers a system ID. The attribute is then saved with the ID
  returned. Feature 003's exceptions (`ManualIdNotPermittedException`, `SystemIdInUseException`,
  `InvalidIdentifierRequestException` for `INVALID_SYSTEM_ID`, `SequenceExhaustedException`) are part
  of that service's public contract; the attribute domain's exception handler maps them with feature
  003's Spanish messages (FR-005).
- **Rationale**: one identifier mechanism (FR-004); no orphan IDs for refused attributes.
- **Alternatives**: calling feature 003 over HTTP from the backend (rejected: same process, adds a
  network hop); generating IDs in the attribute domain (forbidden by FR-004).

## R-04 FR-006 key field versus feature 003's tests — **owner decision needed**

- **Finding**: FR-006 (clarification 2) asks to declare `displayName` as the key field of the
  `ATTRIBUTE` object type in feature 003. Doing so at startup changes feature 003's default
  configuration, and 5 tests in feature 003's `SystemIdControllerIntegrationTest` assign `ATTRIBUTE` IDs with
  `keyFieldValues: {}`; they would fail with `MISSING_KEY_FIELD`. Making them pass means changing
  feature 003's tests, which the feature request forbids ("Do not modify existing features").
- **Options**:
  - **A (recommended)**: do not declare the key field in this feature; amend FR-006 to say the
    declaration happens when the identifier test fixtures can be changed (or with attribute editing,
    DEP-08). Behaviour does not change: FR-023 refuses a duplicate display name before feature 003
    is called (R-05), so feature 003's "same object" outcome is never reached for attributes.
  - **B**: declare the key field at startup (as FR-006 says) and change those 5 feature 003 tests to
    send a display name for `ATTRIBUTE`; feature 003's code stays unchanged.
- **Decision (2026-11-09, owner)**: **option A**. FR-006 was amended in the spec before any code.

## R-05 Duplicate display name and feature 003's "same object" (OQ-012) — **Provisional**

- **Decision**: the display-name check (FR-023) runs first, under the attribute lock. A request
  whose display name exists is refused with **409 `DISPLAY_NAME_IN_USE`**, whatever ID it carries,
  so feature 003 is never asked about it. If feature 003 ever answers `SAME_OBJECT` (it cannot under
  this order), the service treats it as a conflict and saves nothing.
- **Rationale**: editing is out of scope; the clarified uniqueness rule already covers the case.

## R-06 Permissions without authentication — **Provisional (owner to confirm)** (FR-002, FR-019, OQ-004a)

- **Decision**: one permission code, **`ATTRIBUTE_CREATE`**, covers FR-002 (create attributes) and
  FR-019 (affect the global structure), because every attribute in this feature is global (FR-018).
  A separate global permission is revisited when local scope arrives. Until authentication exists,
  the permissions arrive in a **development header `X-Pim-Permissions`** (comma-separated codes),
  read by one class in `attribute.controller`. A manual system ID still needs feature 003's
  permission, sent in feature 003's header `X-Pim-Manual-Id-Permissions` (code `ATTRIBUTE`).
  Missing `ATTRIBUTE_CREATE` → **403 `ATTRIBUTE_CREATE_NOT_PERMITTED`**, checked first.
- **Rationale**: same approach as feature 003 (decision D-7 there): identity stays out of paths and
  bodies, so authentication can replace the header reader later (constitution VIII); no new
  dependency. No role names are fixed (CF-001).
- **Alternatives**: Spring Security stub (new dependency for a placeholder); two permissions now
  (FR-002 and FR-019 would always be required together).

## R-07 How the frontend sends the permissions — **Provisional (owner to confirm)**

- **Decision**: the frontend reads two optional development settings,
  `VITE_DEV_PERMISSIONS` and `VITE_DEV_MANUAL_ID_PERMISSIONS`, from its `.env` file (documented in
  `frontend/.env.example`, not committed with values), and sends them as the two headers. Without
  them the backend refuses creation (403) and the page shows the message.
- **Rationale**: there is no login; these are not secrets (constitution VIII allows non-sensitive
  values in the bundle) and they are removed when authentication arrives.
- **Alternatives**: a fixed "development user" with all permissions in the backend (rejected: the
  403 path could not be tried from the UI, and it silently grants everything).

## R-08 Display name and description — **Provisional** (FR-003, FR-023)

- **Decision**: display name trimmed, **1–120 characters**; uniqueness compared ignoring letter case
  (locale-independent) after trimming. Description optional, trimmed, **up to 1,000 characters**;
  blank is stored as absent.
- **Rationale**: limits keep the form and storage predictable; the source gives none.

## R-09 Text properties — **Provisional** (FR-008, FR-009, OQ-003, OQ-008a)

- **Decision**: all optional. `minLength` 0–100,000; `maxLength` 1–100,000; `minLength ≤ maxLength`
  when both are given. `caseFormat`: `UPPERCASE` (Mayúsculas), `LOWERCASE` (Minúsculas),
  `MIXED_CASE` (Capitalización mixta), or absent. `captureMask`: a regular expression of up to 500
  characters that must compile. `richText`: true or false (default false). The case format and the
  capture mask are **two separate properties** (CF-003 settled as "both exist"); neither is
  forbidden for rich text.
- **Rationale**: keeps both wordings of the source without inventing interactions.

## R-10 Numeric properties — **Provisional** (FR-010, OQ-003)

- **Decision**: `numberFormat` required: `INTEGER` or `DECIMAL`. `precision` (decimals) required
  for `DECIMAL`, **1–10**, and not allowed for `INTEGER`. `minimum` and `maximum` optional (either or
  both), decimal numbers of at most 18 significant digits; whole numbers for `INTEGER`; at most
  `precision` decimals for `DECIMAL`; `minimum ≤ maximum`.

## R-11 Date/time properties — **Provisional** (FR-013, OQ-008b)

- **Decision**: `dateFormat` required, one of `DD/MM/AAAA`, `MM/DD/AAAA`, `AAAA-MM-DD`,
  `DD/MM/AAAA HH:mm`, `AAAA-MM-DD HH:mm`. Logical validations between dates (e.g. end not before
  start) are **not** attribute properties: they compare two attributes and belong to business rules
  (§3.2.4, out of scope).

## R-12 Currency properties — **Provisional** (FR-014, OQ-011)

- **Decision**: the currency is **fixed per attribute**: `currencyCode` required, an ISO 4217 code
  known to the Java runtime (e.g. `MXN`, `USD`, `EUR`); the symbol shown is derived from it.
  `decimals` 0–4, default 2. Regional separators are presentation, applied where values are shown
  (out of scope).

## R-13 Reference properties — **Provisional** (FR-015, OQ-010)

- **Decision**: `targetObjectType` required, one of feature 003's six object types (`PRODUCT`,
  `ATTRIBUTE`, `LIST_OF_VALUES`, `TEMPLATE`, `CLASSIFICATION`, `INTERFACE`); `cardinality` `SINGLE`
  or `MULTIPLE`, default `SINGLE`. No relationship kind (spare part, cross-sell) in this feature.
- **Rationale**: §3.2.1 says "otro objeto del sistema"; feature 003 defines the system's object
  types.

## R-14 List-of-values attributes (FR-012, clarification 5)

- **Decision**: `selectionMode` (`SINGLE` or `MULTIPLE`) and `listOfValuesId` required. The service
  asks a `ListOfValuesCatalog` interface whether lists exist and whether the ID is one of them. Until
  the LOV feature exists, the only implementation is an empty catalog: every LOV attribute is
  refused with **422 `LIST_OF_VALUES_NOT_AVAILABLE`** ("Aún no existen listas de valores…"),
  checked before the list ID is required. With lists available, an unknown ID gives **422
  `LIST_OF_VALUES_NOT_FOUND`**. The future LOV feature replaces the empty implementation.
- **Rationale**: 422 is the constitution's status for a business-rule violation (VII); the request
  is well formed.

## R-15 Request shape and properties that do not apply (A-004)

- **Decision**: the request has common fields plus one optional properties object per type
  (`textProperties`, `numericProperties`, `listOfValuesProperties`, `dateTimeProperties`,
  `currencyProperties`, `referenceProperties`). Only the object matching `dataType` is read; the
  others are ignored and not stored. Booleans have none. The response carries only the matching
  object.
- **Alternatives**: a polymorphic `properties` field with a type discriminator (rejected: harder for
  the form and the error paths, no gain).

## R-16 Data types and options for the form (FR-022)

- **Decision**: `GET /api/v1/attribute-types` returns the seven types (code and Spanish label) and,
  per type, the option lists the form needs (case formats, number formats, date formats, selection
  modes, target object types, cardinalities). The frontend holds no attribute data.

## R-17 API

- **Decision**: `POST /api/v1/attributes` (201 + `Location`, 400, 403, 409, 422),
  `GET /api/v1/attributes/{systemId}` (200, 404), `GET /api/v1/attribute-types` (200). Errors use
  `common.ApiProblems` (ADR 0001) from `attribute.controller.AttributeExceptionHandler`.
- **CORS**: `config.CorsConfiguration` allows only `GET`; the browser form needs `POST`. The plan
  adds `POST` there (one-line change to a cross-cutting class, constitution XII).

## R-18 Concurrency

- **Decision**: one lock in `AttributeService` around "check display name → assign ID (feature 003)
  → save". Feature 003 takes its own lock inside; feature 003 never calls back, so no deadlock.

## R-19 Testing

- **Decision**: backend unit tests for model validation and `AttributeService` (with a real
  in-memory `IdentifierService` and a test `ListOfValuesCatalog` with lists); MockMvc integration
  tests for each endpoint and error status, with feature 003 wired in. Frontend: Vitest + Testing
  Library tests for the API client (mocked `fetch`), the form logic and the page (success, each type's
  fields, validation, 403, 409, 422, connection error). No new dependency.

## R-20 Frontend design-system additions

- **Decision**: the creation form needs inputs the design system lacks: a select, a checkbox and a
  multi-line text area. They are added to `frontend/src/components/ui/` in the style of `TextField`
  (tokens, error and hint support) and shown in the component gallery. Existing components are not
  changed. One navigation entry, "Crear atributo", is added (navigation.ts says business sections
  add their own entries).
