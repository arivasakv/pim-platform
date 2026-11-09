# Implementation Plan: Identifier Management (Administración de Identificadores)

**Branch**: `003-identifier-management` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-identifier-management/spec.md` (approved
2026-11-09 after five clarifications)

## Summary

Add a backend domain `com.pim.backend.identifier` that assigns system IDs to new objects of six
types (product, attribute, list of values, template, classification, interface) and enforces the
rules of ERF 1.1 §3.1: an ID is unique across all six types (FR-001), given manually only at
creation by a user with that type's permission (FR-003, FR-004) or generated from a per-type
numeric or alphanumeric sequence (FR-005, FR-006), never changed afterwards (FR-007), and, when an
existing ID is reused, accepted as the same object only for the same type with identical declared
key-field values, otherwise rejected with a Spanish error (FR-009, FR-013). It is exposed as a
small versioned REST API with no frontend (FR-012). All identifier data is held in memory behind
repository interfaces (constitution IX); nothing in feature 002 changes.

## Technical Context

**Language/Version**: Java 21 (backend only)

**Primary Dependencies**: Spring Boot 4.1.2 (`spring-boot-starter-webmvc`,
`spring-boot-starter-validation`), already in `backend/build.gradle`. **No new dependency.**

**Storage**: in memory (research R-02): `SystemIdRegistry` and `ObjectTypeConfigurationRepository`
interfaces with in-memory implementations; configurations seeded at startup. Data is lost on
restart. MongoDB is not used by this feature (constitution IX allows it for the product catalog
only).

**Testing**: JUnit 5, AssertJ, Spring `@SpringBootTest` + MockMvc (existing). Unit tests for model
and service; integration tests for every endpoint and error status; a 100-way concurrency test and
a 1,000-assignment volume test (SC-002). The Spring context still starts feature 002's MongoDB
configuration, so the build keeps needing the local `pim-test` MongoDB, as today.

**Target Platform**: the existing single backend instance (local development).

**Project Type**: web service (backend of the existing web application); frontend unchanged.

**Performance Goals**: an assignment answers in under 100 ms locally; 1,000 assignments, 100 of
them concurrent, with no duplicate (SC-002).

**Constraints**: one global lock for assignments and configuration changes (R-10), valid for a
single backend instance only; Spanish user-facing messages (FR-011); identity and permissions never
in paths or bodies (constitution VIII).

**Scale/Scope**: 6 object types; about 30 production classes in `identifier` plus one method added
to `common.ApiProblems`; 6 REST operations on 4 paths (contract v1.0.0).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | No `[NEEDS CLARIFICATION]` left; non-blocking open questions settled as recorded decisions D-1 to D-10 (R-02 to R-10). Spec and decisions approved by the project owner on 2026-11-09 |
| II | Technology stack | PASS | Java 21, Spring Boot, Gradle, REST/JSON. No new dependency, no Spring Security |
| III | Modular monolith | PASS | New domain package `identifier`; no access to other domains; controllers only map HTTP and call `IdentifierService` |
| IV | Business scope | PASS | Domain from ERF 1.1 §3.1 (tier 2); terminology "system ID", "object type", "key fields" used consistently in spec, API and code |
| V | Code quality | PASS | Constructor injection, records for value objects and DTOs, domain names (no abbreviations except LOV), Spotless |
| VI | Testing | PASS | Unit, integration, validation (every 400 code), 403/404/405/409 paths, concurrency and volume (R-12) |
| VII | API design | PASS | `/api/v1`, plural nouns, DTOs, collection envelope, RFC 9457 via `common.ApiProblems`, Bean Validation plus domain rules, 201/200/400/403/404/405/409; OpenAPI in `contracts/` |
| VIII | Security | PASS (risk recorded) | No secrets. Permissions read from a development header by one class (R-08), never from path or body, so authentication can replace it without contract changes to bodies or paths. Risk: anyone can send the header (see Provisional Decisions) |
| IX | Development scope | PASS | In-memory data only; no MongoDB for identifiers, no external integration, no AI. Import/export not built (only the rule FR-010 applied by the API) |
| X | AI governance | PASS | Spec changes only through approved clarifications; decisions recorded here |
| XI | Sources | PASS (merge condition) | Requirements cite §3.1 only. **Quality Gate 8** requires no unresolved source conflicts at merge: CF-001 to CF-003 (OQ-011) and CF-004 (OQ-010) must be resolved or explicitly accepted by the owner before merge |
| XII | Package structure | PASS | `identifier.{controller, service, repository, model, dto, config}`; `common` gains only a generic Problem builder; nothing in the root package; no non-conforming package touched |

**Gate result (before Phase 0)**: PASS. No deviation from a MUST rule.

**Post-design re-check (after Phase 1)**: PASS. Data model, contract and quickstart add no
database, no dependency, no frontend change and no external integration. The contract keeps user
identity out of paths and bodies (VIII). One condition remains: source conflicts must be
resolved or accepted before merge (XI, Quality Gate 8).

## Recorded Decisions (approved)

Recorded decisions that settle the spec's non-blocking open questions (constitution I). **Approved
by the project owner on 2026-11-09.** Each can be replaced later without changing the API shape,
except D-4 (would add a permission check); any change goes through a spec or plan amendment.

| ID | Question | Decision | Research |
|---|---|---|---|
| D-1 | Storage | In memory; IDs are lost on restart. Durable storage needs a constitution amendment (IX) and a later feature | R-02 |
| D-2 | Sequence format (OQ-002) | `ALPHANUMERIC` = prefix (1–10 letters) + zero-padded number; `NUMERIC` = zero-padded number; settings `minimumDigits` (1–18) and `nextValue` | R-05 |
| D-3 | Defaults (OQ-002) | All six `ALPHANUMERIC`, 6 digits, start 1, prefixes PRD, ATR, LOV, PLT, CLS, INT | R-05 |
| D-4 | Who changes configuration (OQ-002) | Anyone, in this feature (no permission is specified; user/role administration is out of scope) | R-05 |
| D-5 | Collision of a generated ID (OQ-003) | Skip to the next free value; never fail, never reuse | R-06 |
| D-6 | Manual ID format (OQ-008) | Trimmed; 1–40 of `A–Z a–z 0–9 - _ .`; compared ignoring case; need not follow the sequence format | R-07 |
| D-7 | Permission source (A-003) | Development header `X-Pim-Manual-Id-Permissions` until authentication exists | R-08 |
| D-8 | Type with no key fields (OQ-005 residual) | Reusing an ID of such a type is always 409; when key fields are declared, requests must send exactly those | R-09 |
| D-9 | Concurrency (OQ-006) | One lock; gaps allowed; first of two simultaneous identical manual IDs wins | R-10 |
| D-10 | "Update" in FR-009(a) | 200 `SAME_OBJECT`; the registry changes nothing (it holds only type, ID and key values); the calling feature updates its object | R-04 |

## Project Structure

### Documentation (this feature)

```text
specs/003-identifier-management/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── identifiers.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks; not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/
├── common/
│   └── ApiProblems.java                       # + problem(request, status, title, detail, errors)
└── identifier/                                # NEW domain (constitution III, XII)
    ├── controller/
    │   ├── SystemIdController.java            # POST /system-ids, GET/PUT/PATCH /system-ids/{id}
    │   ├── ObjectTypeController.java          # GET /object-types, GET/PUT /object-types/{type}
    │   ├── ManualIdPermissionsReader.java     # reads X-Pim-Manual-Id-Permissions (R-08)
    │   └── IdentifierExceptionHandler.java    # domain exceptions → Problem Details (R-11)
    ├── service/
    │   └── IdentifierService.java             # assignment, duplicate rule, config changes; one lock (R-10)
    ├── repository/
    │   ├── SystemIdRegistry.java              # interface
    │   ├── InMemorySystemIdRegistry.java
    │   ├── ObjectTypeConfigurationRepository.java   # interface
    │   └── InMemoryObjectTypeConfigurationRepository.java
    ├── model/
    │   ├── ObjectType.java, SystemId.java, SequenceKind.java, SequenceConfiguration.java,
    │   ├── KeyFieldDeclaration.java, ObjectTypeConfiguration.java,
    │   ├── SystemIdRegistration.java, AssignmentMethod.java, AssignmentOutcome.java,
    │   ├── AssignmentResult.java, ManualIdPermissions.java, IdentifierErrorCode.java
    │   └── exceptions: InvalidIdentifierRequestException, ManualIdNotPermittedException,
    │       SystemIdInUseException, SequenceExhaustedException, SystemIdNotFoundException,
    │       SystemIdImmutableException, ObjectTypeNotFoundException
    ├── dto/
    │   ├── SystemIdAssignmentRequest.java, SystemIdAssignmentResponse.java,
    │   ├── SystemIdRegistrationResponse.java, SequenceDto.java,
    │   └── ObjectTypeConfigurationRequest.java, ObjectTypeConfigurationResponse.java,
    │       ObjectTypeConfigurationCollectionResponse.java
    └── config/
        ├── DefaultObjectTypeConfigurations.java   # seeds the six defaults at startup (R-05)
        └── IdentifierClockConfiguration.java      # Clock bean for registeredAt (testable time)

backend/src/test/java/com/pim/backend/identifier/
├── model/        # SystemIdTest, SequenceConfigurationTest, KeyFieldDeclarationTest, ManualIdPermissionsTest
├── service/      # IdentifierServiceTest (rules), IdentifierServiceConcurrencyTest (SC-002)
├── controller/   # SystemIdControllerIntegrationTest, ObjectTypeControllerIntegrationTest
└── repository/   # InMemorySystemIdRegistryTest
```

**Structure Decision**: web application, backend only. One new domain package under the existing
`com.pim.backend` layout (constitution XII); `frontend/` and `product/` are not touched.

## Implementation Order (for /speckit-tasks)

1. Contract and docs already in place (this plan). `ApiProblems.problem(...)` with a unit test.
2. Model value objects and their tests (SystemId, SequenceConfiguration, KeyFieldDeclaration,
   ManualIdPermissions, ObjectType).
3. Repositories (interfaces, in-memory implementations) and the default-configuration seeding.
4. US1 + US5 (generation, configuration): service tests first, then service, then
   `ObjectTypeController` and the automatic path of `SystemIdController`, with integration tests.
5. US2 (manual ID and permission): tests, then permission reader and service path.
6. US3 (immutability): tests, then `PUT`/`PATCH` 405 handlers.
7. US4 (duplicate rule, key fields): tests, then service rule and 200/409 responses.
8. Concurrency and volume tests (SC-002); quickstart run; contract check; full `clean build`.

## Complexity Tracking

No deviation from a MUST rule. Items the reviewer should approve explicitly (not violations):

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| Development permission header (D-7) | FR-003 needs a permission check and the platform has no authentication | Spring Security stub: new dependency and configuration for a placeholder; body field: breaks VIII |
| Identifier data lost on restart (D-1) | Constitution IX allows MongoDB only for the product catalog | MongoDB registry: needs a constitution amendment first |
| Single global lock (D-9) | Global uniqueness spans all types; one backend instance | Per-type atomics cannot check uniqueness across types |
