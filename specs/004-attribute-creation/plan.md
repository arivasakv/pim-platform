# Implementation Plan: Attribute Management – Attribute Creation

**Branch**: `004-attribute-creation` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-attribute-creation/spec.md` (approved
2026-11-09 after five clarifications; FR-006 amended by decision D-4)

## Summary

Add a backend domain `com.pim.backend.attribute` and a frontend page "Crear atributo" that let a
user with the `ATTRIBUTE_CREATE` permission create one attribute definition at a time: display name
(unique, ignoring case), optional description, one of the seven data types of ERF 1.1 §3.2.1 and that
type's properties. Every attribute is global and ungrouped (clarifications 3 and 4). The system ID
comes from feature 003's `IdentifierService` (object type `ATTRIBUTE`), called only after every
attribute rule has passed. List-of-values attributes are offered but refused (422) until a
list-of-values feature exists (clarification 5). Data is in memory (constitution IX). The frontend
reads the data types and options from `GET /api/v1/attribute-types` and posts to
`POST /api/v1/attributes`.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript (strict) with React 19 and Vite (frontend)

**Primary Dependencies**: existing only — Spring Boot 4.1.2 web MVC and validation; React, Vitest,
Testing Library. **No new dependency** in `backend/build.gradle` or `frontend/package.json`.

**Storage**: in memory (research R-02) behind `AttributeRepository`; feature 003's registry (also in
memory) holds the system IDs. Lost on restart.

**Testing**: JUnit 5 + AssertJ + MockMvc (backend); Vitest + Testing Library with mocked `fetch`
(frontend). The backend build keeps needing the local `pim-test` MongoDB because feature 002's
context starts with it.

**Target Platform**: local development; one backend instance.

**Project Type**: web application (separate frontend and backend, constitution III).

**Performance Goals**: an attribute is created in under 200 ms locally; SC-001 (under 2 minutes for a
user) is a usability target checked in the UI walkthrough.

**Constraints**: Spanish UI and messages; identity only in development headers, never in paths or
bodies (VIII); one lock for "check name → assign ID → save" (R-18); no frontend attribute data
(FR-022).

**Scale/Scope**: 47 backend production classes in `attribute` (as built), one line in
`config.CorsConfiguration`; frontend: 1 page, 1 API client, 1 form-logic module, 3 new UI components
with one shared stylesheet, 1 navigation entry; 3 REST operations (contract v1.0.0).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | No `[NEEDS CLARIFICATION]`; non-blocking open questions settled as decisions D-1 to D-12, approved by the project owner on 2026-11-09 with the spec; D-4 option A chosen and FR-006 amended first (spec before code) |
| II | Technology stack | PASS | Java 21/Spring Boot/Gradle, React/TS strict/Vite; REST JSON; no new library |
| III | Separated apps, modular monolith | PASS | New domain `attribute`; it uses feature 003 only through `IdentifierService` (public service) and that service's model types; the frontend talks to the backend only through the documented API |
| IV | Business scope | PASS | Domain from ERF 1.1 §3.2 (tier 2); terms "atributo", "tipo de dato", "lista de valores" used consistently |
| V | Code quality | PASS | Constructor injection, records, domain names; ESLint, Prettier, Spotless |
| VI | Testing | PASS | Unit, integration (every endpoint and error status), every validation rule tested with status and body; frontend success, validation, 403/409/422 and connection-error tests |
| VII | API design | PASS | `/api/v1`, plural nouns, DTOs, collection envelope, RFC 9457 via `common.ApiProblems`, 201/200/400/403/404/409/422; OpenAPI in `contracts/` |
| VIII | Security | PASS (risk recorded) | No secrets. Permissions from development headers (D-2, D-3), read in one class per side, never in paths or bodies; anyone can send them (same risk as feature 003 D-7) |
| IX | Development scope | PASS | In memory only; no MongoDB for attributes; no integration; no AI |
| X | AI governance | PASS | Spec changes only through approved clarifications; decisions recorded here |
| XI | Sources | PASS (merge condition) | Requirements cite §3.2 and §3.2.1. CF-001 to CF-003 must be resolved or accepted before merge (Quality Gate 8). D-6 settles CF-003 provisionally |
| XII | Package structure | PASS | `attribute.{controller, service, repository, model, dto, config}`; `config.CorsConfiguration` gains `POST` (cross-cutting, no business rule); nothing in the root package |

**Gate result (before Phase 0)**: PASS.

**Post-design re-check (after Phase 1)**: PASS. The contract keeps identity out of paths and bodies;
no dependency, no database, no integration added. Feature 003 code and tests are not changed (D-4
option A).

## Recorded Decisions (approved)

Approved by the project owner on 2026-11-09. Any change goes through a spec or plan amendment.

| ID | Question | Decision | Research |
|---|---|---|---|
| D-1 | Storage | In memory; lost on restart (constitution IX). MongoDB needs an amendment covering attributes and feature 003's registry together | R-02 |
| D-2 | Permissions (OQ-004a) | One code `ATTRIBUTE_CREATE` covers FR-002 and FR-019 while all attributes are global; header `X-Pim-Permissions`; manual IDs keep feature 003's header and code `ATTRIBUTE` | R-06 |
| D-3 | Frontend permissions | `VITE_DEV_PERMISSIONS` / `VITE_DEV_MANUAL_ID_PERMISSIONS` in `frontend/.env` (documented in `.env.example`), sent as headers | R-07 |
| D-4 | FR-006 key field vs. feature 003 tests | **Option A**: FR-006 amended; no key field declared in feature 003 now; the display name is sent as a key-field value only if feature 003 declares it (today it does not, so none is sent); behaviour unchanged thanks to D-5 | R-04 |
| D-5 | "Same object" (OQ-012) | Display-name check first → 409 `DISPLAY_NAME_IN_USE`; feature 003's `SAME_OBJECT` never reached | R-05 |
| D-6 | Masks (OQ-008a, CF-003) | Case format and capture mask (regex) are two properties; both allowed on rich text | R-09 |
| D-7 | Limits (OQ-003) | Display name 1–120; description ≤ 1,000; lengths 0/1–100,000; regex ≤ 500 | R-08, R-09 |
| D-8 | Numeric (OQ-003) | `INTEGER`/`DECIMAL` required; precision 1–10 only and always for decimals; one-sided ranges allowed | R-10 |
| D-9 | Date/time (OQ-008b) | Five fixed formats; cross-date validations are business rules (out of scope) | R-11 |
| D-10 | Currency (OQ-011) | ISO code fixed per attribute; decimals 0–4 (default 2); symbol derived | R-12 |
| D-11 | Reference (OQ-010) | Target = one of feature 003's object types; cardinality single/multiple (default single) | R-13 |
| D-12 | LOV without lists | Empty `ListOfValuesCatalog`; 422 `LIST_OF_VALUES_NOT_AVAILABLE`; replaced by the LOV feature | R-14 |

OQ-004b (type change later), OQ-013 (value filtering) and OQ-014 (§3.2.6 metadata) stay open: none
affects creation as specified.

## Project Structure

### Documentation (this feature)

```text
specs/004-attribute-creation/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── attributes.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks; not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/
├── config/
│   └── CorsConfiguration.java                 # + POST (R-17)
└── attribute/                                 # NEW domain
    ├── controller/
    │   ├── AttributeController.java           # POST /attributes, GET /attributes/{id}
    │   ├── AttributeTypeController.java       # GET /attribute-types
    │   ├── RequestPermissionsReader.java      # X-Pim-Permissions + feature 003's manual-ID header (R-06)
    │   ├── AttributePropertiesMapper.java     # request properties of the chosen type → model (HTTP mapping only)
    │   └── AttributeExceptionHandler.java     # attribute and feature 003 errors → Problem Details
    ├── service/
    │   └── AttributeService.java              # rules, LOV check, uniqueness, IdentifierService call, save; one lock
    ├── repository/
    │   ├── AttributeRepository.java, InMemoryAttributeRepository.java
    │   └── ListOfValuesCatalog.java, EmptyListOfValuesCatalog.java   # replaced by the LOV feature
    ├── model/
    │   ├── Attribute, AttributeDraft, AttributeDataType, AttributeScope, DisplayName, AttributePermissions,
    │   ├── AttributeProperties (sealed) + TextProperties, NumericProperties, ListOfValuesProperties,
    │   │   DateTimeProperties, CurrencyProperties, ReferenceProperties,
    │   ├── CaseFormat, NumberFormat, SelectionMode, DateFormat, Cardinality, ReferenceTargetType,
    │   ├── AttributeErrorCode, Codes (enum lookup helper)
    │   └── exceptions: InvalidAttributeException, AttributeCreateNotPermittedException,
    │       DisplayNameInUseException, ListOfValuesUnavailableException, AttributeNotFoundException
    └── dto/
        ├── AttributeCreationRequest + one *PropertiesDto per type, AttributeResponse,
        └── AttributeTypeResponse, OptionResponse, AttributeTypeCollectionResponse

No `attribute.config` package: `createdAt` uses the existing `Clock` bean (a second `Clock` bean would
make injection ambiguous for feature 003's `IdentifierService`).

backend/src/test/java/com/pim/backend/attribute/   # model, service, controller tests (mirrors packages)

frontend/src/
├── api/attributesApi.ts (+ .test.ts)          # GET types, POST attribute, error mapping; sends dev headers
├── attributes/attributeForm.ts (+ .test.ts)   # form state → request; UX-only checks (backend is authoritative)
├── pages/AttributeCreationPage.tsx (+ .module.css, .test.tsx)
├── components/ui/SelectField.tsx, CheckboxField.tsx, TextAreaField.tsx, FormField.module.css (shared)  # new, gallery entries
├── components/ui/index.ts                     # export the new components
├── pages/ComponentGalleryPage.tsx             # show the new components
├── navigation.ts, App.tsx                     # "Crear atributo" entry and route
└── vite-env.d.ts, ../.env.example             # VITE_DEV_PERMISSIONS, VITE_DEV_MANUAL_ID_PERMISSIONS
```

**Structure Decision**: web application; one new backend domain and one new frontend page, using
the existing layouts (constitution III, XII). Feature 002 and 003 code is not changed.

## Implementation Order (for /speckit-tasks)

1. Contract in place. CORS `POST`. Backend model value objects and their tests (every validation
   rule of data-model.md).
2. Repository, empty LOV catalog, `AttributeService` with feature 003 wired in (tests first).
3. API: types endpoint, create and read endpoints, exception handler, integration tests per story
   (US1 → US2 → US3 → US4 → US5).
4. Frontend: new UI components (gallery), API client, form logic, page, navigation; tests first.
5. Full builds, quickstart API and UI scenarios, contract and scope checks.

## Complexity Tracking

No deviation from a MUST rule. Items for explicit reviewer approval:

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| Development permission headers, set from Vite env (D-2, D-3) | FR-002/FR-019 need permissions; no authentication exists | Backend "dev user" with all permissions: 403 path untestable from the UI, silently grants everything |
| Attributes lost on restart (D-1) | Constitution IX | MongoDB needs an amendment, and feature 003's registry would have to follow |
| Three new design-system components | The form needs select, checkbox and text area inputs | Native unstyled inputs would break the look and feel of feature 001 |
