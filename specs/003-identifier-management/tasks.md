---

description: "Task list for feature 003: identifier management (system IDs)"
---

# Tasks: Identifier Management (Administración de Identificadores)

**Input**: Design documents from `specs/003-identifier-management/`

**Prerequisites**: [plan.md](./plan.md) (decisions D-1 to D-10 approved), [spec.md](./spec.md)
(approved 2026-11-09), [research.md](./research.md), [data-model.md](./data-model.md),
[contracts/identifiers.openapi.yaml](./contracts/identifiers.openapi.yaml),
[quickstart.md](./quickstart.md)

**Tests**: Included. Constitution VI requires unit tests for business logic, integration tests for
every endpoint (success and error paths), and a test for every validation rule. Each test task
comes before the code it checks: write it first and confirm it fails (a compile failure counts).

**Organization**: one phase per user story in priority order (US1, US2, US3 are P1; US4 is P2; US5
is P3), after a foundational phase with the model, repositories and seeding all stories need.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story the task belongs to (US1–US5)
- Paths are relative to the repository root. Commands are **Windows PowerShell**; Gradle runs in
  `backend\` (`.\gradlew.bat …`).
- Abbreviations used in paths below: **`MAIN`** = `backend/src/main/java/com/pim/backend`,
  **`TEST`** = `backend/src/test/java/com/pim/backend`.

## Ground rules for every task

- **Backend only.** No change under `frontend/` (FR-012, R-13).
- **Do not modify** feature 002 (`MAIN/product/**`, `TEST/product/**`,
  `specs/002-existing-product-search/**`), `.specify/memory/constitution.md` or `CLAUDE.md`.
- **In memory only** (constitution IX, D-1): no MongoDB, JPA or other storage for identifier data;
  no new dependency in `backend/build.gradle` (plan "Primary Dependencies").
- **Packages** (constitution XII): new code only in `com.pim.backend.identifier.{controller,
  service, repository, model, dto, config}`; the only change outside it is `common.ApiProblems`
  (T002–T003). `model` uses no Spring, HTTP or DTO type; `dto` is used only by `controller`;
  controllers call only `IdentifierService`.
- **Java**: Java 21, records for value objects and DTOs, constructor injection only (constitution V).
- **Errors**: every error is RFC 9457 Problem Details built with `common.ApiProblems` (ADR 0001,
  R-11), with Spanish `title`, `detail` and `errors[].message` (FR-011); `type` is omitted.
  Error codes are exactly those in the contract's `Problem.errors[].code` enum.
- **Contract**: endpoints, statuses, headers and bodies follow
  `specs/003-identifier-management/contracts/identifiers.openapi.yaml` v1.0.0. If code and contract
  disagree, stop and report; do not change the contract silently.
- **Atomicity** (R-10, D-9): every `IdentifierService` method that reads and writes identifier
  data runs under one shared lock.
- **Integration tests** start the full Spring context (feature 002 needs the local MongoDB
  `pim-test`, as today). Identifier state is in memory, so each identifier integration test class
  uses `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` to start from the defaults.
- After each phase: `.\gradlew.bat spotlessApply` then `.\gradlew.bat build`; all tests pass.

---

## Phase 1: Setup

**Purpose**: confirm the starting point.

- [X] T001 Confirm the baseline on branch `003-identifier-management`: with the local MongoDB
  running, `.\gradlew.bat clean build` in `backend\` passes (61 tests at the end of feature 002).
  Record the test count; later phases must keep every existing test passing unchanged.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: error helper, model, repositories, default configuration and the shared exception
handler. No user story can start before this phase is complete.

### Error helper

- [X] T002 [P] Write `TEST/common/ApiProblemsTest.java`: `ApiProblems.problem(request, status,
  title, detail, errors)` returns a ProblemDetail with that status, title, detail, `instance` = the
  request path, and `errors` = the given list; with an empty list, no `errors` property is set.
  Also assert `invalidRequest(...)` is unchanged (status 400, detail `INVALID_REQUEST_DETAIL`).
- [X] T003 Add `public static ProblemDetail problem(HttpServletRequest request, HttpStatus status,
  String title, String detail, List<ApiFieldError> errors)` to `MAIN/common/ApiProblems.java`
  (R-11), with Javadoc. Existing methods unchanged. T002 passes.

### Model (`MAIN/identifier/model/`)

- [X] T004 [P] Create enums in `MAIN/identifier/model/`: `ObjectType.java` with constants in this
  order `PRODUCT, ATTRIBUTE, LIST_OF_VALUES, TEMPLATE, CLASSIFICATION, INTERFACE` (FR-001;
  data-model "ObjectType"); `SequenceKind.java` (`NUMERIC, ALPHANUMERIC`); `AssignmentMethod.java`
  (`MANUAL, AUTOMATIC`); `AssignmentOutcome.java` (`ASSIGNED, SAME_OBJECT`).
- [X] T005 [P] Create `MAIN/identifier/model/IdentifierErrorCode.java`: one constant per contract
  error code (`REQUIRED, UNKNOWN_OBJECT_TYPE, INVALID_SYSTEM_ID, MISSING_KEY_FIELD,
  UNKNOWN_KEY_FIELD, INVALID_KEY_FIELD_VALUE, INVALID_KEY_FIELD_NAME, INVALID_SEQUENCE_KIND,
  INVALID_PREFIX, INVALID_MINIMUM_DIGITS, INVALID_NEXT_VALUE, MANUAL_ID_NOT_PERMITTED,
  SYSTEM_ID_IN_USE, SEQUENCE_EXHAUSTED`), each with a Spanish message template (`%s` for the ID,
  type or field where the contract examples name one), e.g. `MANUAL_ID_NOT_PERMITTED` → "No tienes permiso para
  definir el ID %s para un objeto de tipo %s."; `SYSTEM_ID_IN_USE` → "El ID %s ya
  pertenece a otro objeto. Indica un ID nuevo que no esté en uso." and `INVALID_SYSTEM_ID` → "El ID
  solo puede tener de 1 a 40 letras, números, guiones, guiones bajos o puntos."
- [X] T006 [P] Write `TEST/identifier/model/SystemIdTest.java` (FR-001, R-07, A-004):
  `SystemId.parseManual(String)` returns empty for `null`, `""` and `"   "`; trims `" A-100 "` to
  `A-100`; accepts exactly 1–40 characters from `A–Z a–z 0–9 - _ .`; rejects `A B`, `A/1`, `Ñ1`
  and a 41-character value with `InvalidIdentifierRequestException` (field `systemId`, code
  `INVALID_SYSTEM_ID`); `value()` keeps the case given; `equals`/`hashCode` ignore case
  (`a-100` equals `A-100`).
- [X] T007 Implement `MAIN/identifier/model/SystemId.java` (record or final class) so T006 passes.
  Also `SystemId.of(String)` for generated and stored values (same format check).
- [X] T008 [P] Write `TEST/identifier/model/SequenceConfigurationTest.java` (FR-006, R-05,
  data-model "SequenceConfiguration"): `ALPHANUMERIC` requires `prefix` "1–10 letters A–Z"
  (rejects absent, `prd`, `PR1`, 11 letters → `INVALID_PREFIX`); `NUMERIC` rejects any prefix
  (`INVALID_PREFIX`); `minimumDigits` "1–18" (0 and 19 → `INVALID_MINIMUM_DIGITS`); `nextValue`
  "1 to 999,999,999,999,999,999" (0 → `INVALID_NEXT_VALUE`); `format(1)` with prefix `PRD`, 6 digits
  → `PRD000001`; `format(1234567)` with 6 digits → `PRD1234567` (grows past padding); `NUMERIC`,
  8 digits, `format(1000)` → `00001000`; `withNextValue(n)` returns a copy.
- [X] T009 Implement `MAIN/identifier/model/SequenceConfiguration.java` (record: `kind`, `prefix`
  nullable, `minimumDigits`, `nextValue`; constant `MAX_VALUE = 999_999_999_999_999_999L`) so T008
  passes.
- [X] T010 [P] Write `TEST/identifier/model/KeyFieldDeclarationTest.java` (FR-013, data-model
  "KeyFieldDeclaration"): empty is allowed; "0–20 names, each 1–60 characters of letters, digits or
  `_`, starting with a letter; no repeats"; violations → `INVALID_KEY_FIELD_NAME` (field
  `keyFields`); order is kept; the list is unmodifiable.
- [X] T011 Implement `MAIN/identifier/model/KeyFieldDeclaration.java` (record wrapping
  `List<String> fieldNames`, `empty()` factory) so T010 passes.
- [X] T012 [P] Write `TEST/identifier/model/ManualIdPermissionsTest.java` (FR-003, R-08):
  `ManualIdPermissions.fromCodes(null)` and `fromCodes("")` allow nothing; `"PRODUCT, TEMPLATE"`
  allows exactly those two; unknown codes (`WIDGET`) are ignored; `allows(ObjectType)`.
- [X] T013 Implement `MAIN/identifier/model/ManualIdPermissions.java` (record over an
  `EnumSet<ObjectType>`) so T012 passes.
- [X] T014 [P] Create records in `MAIN/identifier/model/`: `ObjectTypeConfiguration.java`
  (`objectType`, `sequence`, `keyFields`); `SystemIdRegistration.java` (`systemId`, `objectType`,
  `keyFieldValues` as an unmodifiable ordered `Map<String,String>`, `assignment`
  (`AssignmentMethod`), `registeredAt` (`Instant`)); `AssignmentResult.java` (`outcome`,
  `registration`). No setters (FR-007).
- [X] T015 [P] Create domain exceptions in `MAIN/identifier/model/`:
  `InvalidIdentifierRequestException` (field name, `IdentifierErrorCode`, message arguments),
  `ManualIdNotPermittedException` (`ObjectType` and the requested ID text), `SystemIdInUseException` (`SystemId`),
  `SequenceExhaustedException` (`ObjectType`), `SystemIdNotFoundException` (the requested text),
  `ObjectTypeNotFoundException` (the requested text, for an unknown object type in a path),
  `SystemIdImmutableException` (the requested text). All unchecked; none depends on Spring.

### Repositories (`MAIN/identifier/repository/`)

- [X] T016 Create interfaces `MAIN/identifier/repository/SystemIdRegistry.java`
  (`Optional<SystemIdRegistration> find(SystemId)`, `boolean contains(SystemId)`,
  `void add(SystemIdRegistration)`, `int count()`) and
  `MAIN/identifier/repository/ObjectTypeConfigurationRepository.java`
  (`List<ObjectTypeConfiguration> findAll()` in `ObjectType` order,
  `ObjectTypeConfiguration find(ObjectType)`, `void save(ObjectTypeConfiguration)`). No business
  rules (XII).
- [X] T017 [P] Write `TEST/identifier/repository/InMemorySystemIdRegistryTest.java`: `add` then
  `find` by the same ID in other letter case returns it; `contains` ignores case; `count`;
  `add` of an ID already present throws `IllegalStateException` (the service must check first).
- [X] T018 Implement `MAIN/identifier/repository/InMemorySystemIdRegistry.java` (`@Repository`,
  `HashMap` keyed by `SystemId`) so T017 passes.
- [X] T019 [P] Implement `MAIN/identifier/repository/InMemoryObjectTypeConfigurationRepository.java`
  (`@Repository`, `EnumMap`).
- [X] T020 [P] Write `TEST/identifier/config/DefaultObjectTypeConfigurationsTest.java` (FR-006,
  FR-013, D-3): after seeding, the repository holds six configurations, all `ALPHANUMERIC`,
  `minimumDigits` 6, `nextValue` 1, prefixes `PRD, ATR, LOV, PLT, CLS, INT` in `ObjectType` order,
  and empty key-field declarations.
- [X] T021 Implement `MAIN/identifier/config/DefaultObjectTypeConfigurations.java`
  (`@Configuration` with an `ApplicationRunner` bean or an initializing component that saves the
  six defaults into `ObjectTypeConfigurationRepository` at startup) so T020 passes.
- [X] T022 [P] Create `MAIN/identifier/config/IdentifierClockConfiguration.java`: a `Clock` bean
  (`Clock.systemUTC()`) used for `registeredAt`, so tests can use a fixed clock.

### Shared exception handler

- [X] T023 Create `MAIN/identifier/controller/IdentifierExceptionHandler.java`:
  `@RestControllerAdvice(basePackageClasses = IdentifierExceptionHandler.class)`,
  `@Order(Ordered.HIGHEST_PRECEDENCE)` (like `ProductExceptionHandler`). In this task it maps:
  `InvalidIdentifierRequestException` → 400 via `ApiProblems.invalidRequest(request, "Solicitud no
  válida", errors)` with one `ApiFieldError(field, code, message)`;
  `MethodArgumentNotValidException` → 400 with one error per violated field, the code chosen by
  field and constraint: `@NotNull` on any field → `REQUIRED` (field = its JSON path, e.g.
  `sequence.kind`); `@Size` on `systemId` → `INVALID_SYSTEM_ID`; `@Size` on `keyFieldValues`
  (more than 20 entries) → `UNKNOWN_KEY_FIELD`. A task that adds another constraint also adds its
  mapping here; there is no silent fallback to `REQUIRED`;
  `HttpMessageNotReadableException` → 400 `REQUIRED` (field `body`). Later tasks add the other
  mappings.

**Checkpoint**: `.\gradlew.bat build` passes; nothing is exposed over HTTP yet.

---

## Phase 3: User Story 1 - Every new object gets an automatic system ID (Priority: P1) 🎯 MVP

**Goal**: `POST /api/v1/system-ids` without `systemId` assigns a new ID from the type's sequence;
`GET /api/v1/system-ids/{systemId}` reads it.

**Independent Test**: two `ATTRIBUTE` requests give `ATR000001` and `ATR000002`; a `TEMPLATE`
request gives `PLT000001` (quickstart scenarios 2, 3, 6).

### Tests for User Story 1

- [X] T024 [P] [US1] Write `TEST/identifier/service/IdentifierServiceTest.java` (automatic part;
  in-memory repositories seeded with the defaults, fixed `Clock`): two `ATTRIBUTE` assignments
  without ID → `ATR000001`, `ATR000002`, outcome `ASSIGNED`, `assignment` `AUTOMATIC`,
  `registeredAt` = the clock's instant (FR-005); a `TEMPLATE` → `PLT000001` (own sequence,
  FR-006); blank `systemId` `"   "` is treated as absent and needs no permission (A-004); skip (R-06/D-5),
  in this order on a fresh service: generate two attributes (`ATR000001`, `ATR000002`), add a
  registration for `ATR000003` directly to the registry, then the next two automatic attributes
  get `ATR000004` and `ATR000005`; with `nextValue` =
  `SequenceConfiguration.MAX_VALUE` the first generation succeeds and the next throws
  `SequenceExhaustedException`, saving nothing; `find` of an unknown ID throws
  `SystemIdNotFoundException`.
- [X] T025 [P] [US1] Write `TEST/identifier/controller/SystemIdControllerIntegrationTest.java`
  (automatic part; `@SpringBootTest`, `@AutoConfigureMockMvc`, `@DirtiesContext` per method):
  `POST /api/v1/system-ids` `{"objectType":"ATTRIBUTE","keyFieldValues":{}}` → 201, header
  `Location: /api/v1/system-ids/ATR000001`, body `outcome` `ASSIGNED`, `registration.systemId`
  `ATR000001`, `objectType` `ATTRIBUTE`, `assignment` `AUTOMATIC`, `keyFieldValues` `{}`,
  `registeredAt` present; second call → `ATR000002`; `GET /api/v1/system-ids/atr000001` → 200 with
  the registration (case ignored); `GET /api/v1/system-ids/XYZ-1` → 404 `application/problem+json`,
  title "No encontrado", detail naming `XYZ-1`; 400 `REQUIRED` when `objectType` or
  `keyFieldValues` is missing; 400 `UNKNOWN_OBJECT_TYPE` for `"objectType":"WIDGET"`; 400 `INVALID_SYSTEM_ID`
  for a `systemId` longer than 60 characters; 400 `UNKNOWN_KEY_FIELD` (field `keyFieldValues`) for
  21 entries in `keyFieldValues`; `SEQUENCE_EXHAUSTED` (constitution VI: status and
  body): save an `ATTRIBUTE` configuration with `nextValue` = `SequenceConfiguration.MAX_VALUE`
  through the `ObjectTypeConfigurationRepository` bean, then the first `POST` → 201 and the
  second → 409 `application/problem+json`, title "Secuencia agotada", one error (field
  `objectType`, code `SEQUENCE_EXHAUSTED`), and no new registration.

### Implementation for User Story 1

- [X] T026 [P] [US1] Create DTOs in `MAIN/identifier/dto/`: `SystemIdAssignmentRequest.java`
  (record: `@NotNull String objectType`, `@Size(max = 60) String systemId` nullable,
  `@NotNull @Size(max = 20) Map<String, String> keyFieldValues`); `SystemIdRegistrationResponse.java`
  (`systemId`, `objectType`, `keyFieldValues`, `assignment`, `registeredAt`) with a static
  `from(SystemIdRegistration)`; `SystemIdAssignmentResponse.java` (`outcome`, `registration`).
- [X] T027 [US1] Create `MAIN/identifier/service/IdentifierService.java` (`@Service`, constructor
  injection of both repositories and `Clock`; one private lock object): `AssignmentResult
  assign(ObjectType type, String requestedSystemId, Map<String,String> keyFieldValues,
  ManualIdPermissions permissions)` with the automatic path (data-model "Assignment flow": blank
  ID → generate the next free value from the type's sequence, skipping IDs already registered,
  register `AUTOMATIC`, advance `nextValue` past the value used; throw
  `SequenceExhaustedException` past `MAX_VALUE`) and `SystemIdRegistration find(String systemId)`.
  A non-blank `requestedSystemId` throws `UnsupportedOperationException` until T034 replaces it.
  T024 passes.
- [X] T028 [US1] Create `MAIN/identifier/controller/SystemIdController.java`
  (`@RestController`, `/api/v1/system-ids`): `POST` (`@Valid` body; map `objectType` text to
  `ObjectType`, unknown → `InvalidIdentifierRequestException(objectType, UNKNOWN_OBJECT_TYPE)`;
  call `IdentifierService.assign` with `ManualIdPermissions.fromCodes(null)` for now; 201 +
  `Location` for `ASSIGNED`) and `GET /{systemId}` (200).
- [X] T029 [US1] Extend `MAIN/identifier/controller/IdentifierExceptionHandler.java`:
  `SystemIdNotFoundException` → 404 via `ApiProblems.problem`, title "No encontrado", detail "No
  existe un objeto con el ID %s.", no errors; `SequenceExhaustedException` → 409, title "Secuencia
  agotada", one error (field `objectType`, code `SEQUENCE_EXHAUSTED`). T025 passes.

**Checkpoint**: US1 works on its own (automatic IDs, read by ID). Build passes.

---

## Phase 4: User Story 2 - An authorized user sets the ID when creating an object (Priority: P1)

**Goal**: a manual `systemId` is accepted only with the type's permission from header
`X-Pim-Manual-Id-Permissions`; otherwise 403. An ID already used by any object is not assigned
again.

**Independent Test**: quickstart scenarios 4, 5 and 7.

### Tests for User Story 2

- [X] T030 [P] [US2] Extend `TEST/identifier/service/IdentifierServiceTest.java` (manual part,
  FR-003, FR-004, R-07): with permission `CLASSIFICATION`, `" CLS-ROPA "` → registered as
  `CLS-ROPA`, `MANUAL`, `ASSIGNED`; without that permission (none, or only `PRODUCT`) →
  `ManualIdNotPermittedException`, registry count unchanged, no automatic ID assigned instead;
  permission is checked before the format, so `"A B"` without permission → 403 exception, and
  `"A B"` with permission → `InvalidIdentifierRequestException` `INVALID_SYSTEM_ID`; `cls-ropa`
  for `PRODUCT` with permission `PRODUCT` after `CLS-ROPA` exists → `SystemIdInUseException`
  (global, case ignored, FR-001); a manual value equal to a type's future generated value (e.g.
  `ATR000001` for `ATTRIBUTE`) is accepted, and the next automatic attribute gets `ATR000002`
  (R-06).
- [X] T031 [P] [US2] Extend `TEST/identifier/controller/SystemIdControllerIntegrationTest.java`
  (manual part): header `X-Pim-Manual-Id-Permissions: CLASSIFICATION` +
  `{"objectType":"CLASSIFICATION","systemId":"CLS-ROPA","keyFieldValues":{}}` → 201, `MANUAL`;
  same body with `systemId` `CLS-OTRA` and no header, and with header `PRODUCT` → 403
  `application/problem+json`, title "ID manual no permitido", error field `systemId` code
  `MANUAL_ID_NOT_PERMITTED`, and both `detail` and the error message contain `CLS-OTRA`
  (FR-011), then `GET /api/v1/system-ids/CLS-OTRA` → 404; with permission,
  `systemId` `A B` → 400 `INVALID_SYSTEM_ID`; header `PRODUCT` + `systemId` `cls-ropa` for
  `PRODUCT` after `CLS-ROPA` exists → 409, title "ID en uso", detail and error message contain
  `cls-ropa` and ask for a new, unused ID (FR-011); `systemId` `"   "` without header → 201
  automatic `PRD000001`.

### Implementation for User Story 2

- [X] T032 [P] [US2] Create `MAIN/identifier/controller/ManualIdPermissionsReader.java`
  (`@Component`): `ManualIdPermissions read(HttpServletRequest)` (or from an
  `@RequestHeader(required = false)` value) using header name constant
  `X-Pim-Manual-Id-Permissions` and `ManualIdPermissions.fromCodes`. Javadoc states it is a
  development stand-in until authentication exists (R-08, D-7).
- [X] T033 [US2] Update `MAIN/identifier/controller/SystemIdController.java` to pass the
  permissions from `ManualIdPermissionsReader` to `IdentifierService.assign`.
- [X] T034 [US2] Implement the manual path in `MAIN/identifier/service/IdentifierService.java`
  (replacing the T027 placeholder), inside the lock: no permission for the type →
  `ManualIdNotPermittedException`; `SystemId.parseManual` (format, 400); ID not registered
  (ignoring case) → register `MANUAL`, `ASSIGNED`; ID registered → `SystemIdInUseException` (the
  same-object case is added in T043). T030 passes.
- [X] T035 [US2] Extend `MAIN/identifier/controller/IdentifierExceptionHandler.java`:
  `ManualIdNotPermittedException` → 403, title "ID manual no permitido", detail "No tienes permiso
  para definir manualmente el ID %s para este tipo de objeto. Envía la solicitud sin ID para que el
  sistema genere uno." with the ID as given in the request (FR-011), one error (field `systemId`,
  code `MANUAL_ID_NOT_PERMITTED`, message naming the ID and the type); `SystemIdInUseException` → 409, title "ID en uso", detail = error message =
  `SYSTEM_ID_IN_USE` text with the ID as given in the request. T031 passes.

**Checkpoint**: US1 and US2 work; build passes.

---

## Phase 5: User Story 3 - A saved object's ID never changes (Priority: P1)

**Goal**: every attempt to change a system ID is rejected with a Spanish explanation (FR-007).

**Independent Test**: quickstart scenario 12.

### Tests for User Story 3

- [X] T036 [P] [US3] Extend `TEST/identifier/controller/SystemIdControllerIntegrationTest.java`
  (immutability): after an automatic `ATR000001`, `PUT /api/v1/system-ids/ATR000001` with any JSON
  body, and `PATCH` with any body, each → 405, header `Allow: GET`, `application/problem+json`,
  title "ID no modificable", detail "El ID de sistema ATR000001 no se puede modificar una vez
  guardado."; afterwards `GET` returns the same registration (same `systemId`, `assignment`,
  `registeredAt`); `PUT` with header `X-Pim-Manual-Id-Permissions: ATTRIBUTE` is rejected the same
  way (FR-007: by any role); `PUT /api/v1/system-ids/UNKNOWN-1` → 405 as well.
- [X] T037 [P] [US3] Add to `TEST/identifier/service/IdentifierServiceTest.java`: a registration
  returned by `assign` or `find` cannot be changed (its `keyFieldValues` map is unmodifiable) and
  `IdentifierService` offers no method that changes a registration (assert by reflection that no
  public method other than `assign`, `find`, `findConfigurations`, `findConfiguration` and
  `replaceConfiguration` exists).

### Implementation for User Story 3

- [X] T038 [US3] Add `PUT` and `PATCH` `/{systemId}` handlers to
  `MAIN/identifier/controller/SystemIdController.java` that accept and ignore any body and throw
  `SystemIdImmutableException(systemId)` (R-03).
- [X] T039 [US3] Extend `MAIN/identifier/controller/IdentifierExceptionHandler.java`:
  `SystemIdImmutableException` → 405 with header `Allow: GET` (return
  `ResponseEntity<ProblemDetail>`), title "ID no modificable", detail as in T036, no errors.
  T036 and T037 pass.

**Checkpoint**: US1–US3 (all P1) work; build passes.

---

## Phase 6: User Story 4 - Creating or importing with an existing ID (Priority: P2)

**Goal**: reusing an existing ID returns 200 `SAME_OBJECT` only for the same type with identical
declared key-field values; otherwise 409 (FR-009, FR-010, FR-013, R-04, R-09).

**Independent Test**: quickstart scenarios 9, 10, 11. Key fields are declared in tests through the
`ObjectTypeConfigurationRepository` bean, so this story does not depend on US5.

### Tests for User Story 4

- [X] T040 [P] [US4] Extend `TEST/identifier/service/IdentifierServiceTest.java` (duplicate rule):
  with `ATTRIBUTE` key fields `[code]` declared in the repository: manual `A-100`
  `{code: COLOR}` → `ASSIGNED`; repeating it → `SAME_OBJECT`, the same registration object values,
  registry count unchanged (SC-005); `{code: " COLOR "}` → `SAME_OBJECT` (values trimmed);
  `{code: TALLA}` → `SystemIdInUseException`, registration unchanged; `A-100` for `TEMPLATE` →
  `SystemIdInUseException`; `TEMPLATE` (no key fields) reusing `PLT000001` → `SystemIdInUseException`
  (R-09/D-8); missing `code` → `MISSING_KEY_FIELD`; extra `{code: X, color: Y}` →
  `UNKNOWN_KEY_FIELD`; a value blank or over 200 characters → `INVALID_KEY_FIELD_VALUE` (data-model
  "keyFieldValues … values trimmed, 1–200 characters"); the automatic path validates key fields the
  same way; a type with no key fields accepts only `{}`.
- [X] T041 [P] [US4] Extend `TEST/identifier/controller/SystemIdControllerIntegrationTest.java`
  (duplicate rule; declare `ATTRIBUTE` key fields `[code]` through the
  `ObjectTypeConfigurationRepository` bean in the test): header `ATTRIBUTE`, `A-100`
  `{code: COLOR}` → 201, then same request → 200 with `outcome` `SAME_OBJECT` and no `Location`;
  `{code: TALLA}` → 409 `SYSTEM_ID_IN_USE` whose detail asks for a new, unused ID, and `GET
  /api/v1/system-ids/A-100` still shows `{code: COLOR}`; missing `code` → 400 `MISSING_KEY_FIELD`;
  extra field → 400 `UNKNOWN_KEY_FIELD`; `{code: "   "}` and a 201-character value → 400
  `INVALID_KEY_FIELD_VALUE` with field `code` (constitution VI: status and body); `TEMPLATE`
  reusing `PLT000001` (header `TEMPLATE`) → 409.

### Implementation for User Story 4

- [X] T042 [US4] In `MAIN/identifier/service/IdentifierService.java`, validate `keyFieldValues`
  against the type's `KeyFieldDeclaration` before any other check on both paths (trim values;
  `MISSING_KEY_FIELD`, `UNKNOWN_KEY_FIELD`, `INVALID_KEY_FIELD_VALUE` with the field name) and store
  the trimmed values in declaration order.
- [X] T043 [US4] In `MAIN/identifier/service/IdentifierService.java`, replace the "registered →
  in use" branch of the manual path with FR-009: same `ObjectType`, non-empty declaration and equal
  key-field values → `AssignmentResult(SAME_OBJECT, existing registration)` with nothing changed;
  otherwise `SystemIdInUseException`. T040 passes.
- [X] T044 [US4] Update `MAIN/identifier/controller/SystemIdController.java`: outcome
  `SAME_OBJECT` → 200 with the same body shape and no `Location`. T041 passes.

**Checkpoint**: US1–US4 work; build passes.

---

## Phase 7: User Story 5 - Configure the ID sequence of each object type (Priority: P3)

**Goal**: read the six configurations and replace one (sequence and key fields); later IDs follow
the new configuration; saved IDs never change (FR-006, FR-008, FR-012, FR-013).

**Independent Test**: quickstart scenarios 1, 8, 13, 14, and the `PUT` cases of 15.

### Tests for User Story 5

- [X] T045 [P] [US5] Extend `TEST/identifier/service/IdentifierServiceTest.java` (configuration):
  `findConfigurations()` returns six in `ObjectType` order; `replaceConfiguration(PRODUCT,
  NUMERIC/8/1000, [])` then an automatic product → `00001000`, and the earlier `PRD000001`
  registration is unchanged (FR-008); lowering `nextValue` to 1 for `ATTRIBUTE` after
  `ATR000001`–`ATR000002` exist → next automatic is `ATR000003` (skip); changing the key-field
  declaration leaves existing registrations' `keyFieldValues` unchanged.
- [X] T046 [P] [US5] Write `TEST/identifier/controller/ObjectTypeControllerIntegrationTest.java`
  (`@DirtiesContext` per method): `GET /api/v1/object-types` → 200, `total` 6, `items` in order
  with the defaults (D-3); `GET /api/v1/object-types/TEMPLATE` → 200; `GET
  /api/v1/object-types/WIDGET` → 404 problem; `PUT /api/v1/object-types/PRODUCT`
  `{"sequence":{"kind":"NUMERIC","minimumDigits":8,"nextValue":1000},"keyFields":[]}` → 200 with
  that configuration, then `POST /api/v1/system-ids` for `PRODUCT` → `00001000`; `PUT` errors →
  400 with codes: `kind` `HEXADECIMAL` → `INVALID_SEQUENCE_KIND`; `NUMERIC` with `prefix` `PRD`,
  `ALPHANUMERIC` without prefix, prefix `prd` → `INVALID_PREFIX`; `minimumDigits` 0 or 19 →
  `INVALID_MINIMUM_DIGITS`; `nextValue` 0 → `INVALID_NEXT_VALUE`; `keyFields` `["1code"]`,
  `["code","code"]` or 21 names → `INVALID_KEY_FIELD_NAME`; missing `sequence` or `keyFields` →
  `REQUIRED`; `PUT /api/v1/object-types/WIDGET` → 404.

### Implementation for User Story 5

- [X] T047 [P] [US5] Create DTOs in `MAIN/identifier/dto/`: `SequenceDto.java` (`kind` text,
  `prefix` nullable, `minimumDigits` `Integer`, `nextValue` `Long`, `@NotNull` on `kind`,
  `minimumDigits`, `nextValue`); `ObjectTypeConfigurationRequest.java` (`@NotNull @Valid
  SequenceDto sequence`, `@NotNull List<String> keyFields`); `ObjectTypeConfigurationResponse.java`
  (`objectType`, `sequence`, `keyFields`, static `from(ObjectTypeConfiguration)`);
  `ObjectTypeConfigurationCollectionResponse.java` (`items`, `total`).
- [X] T048 [US5] Add to `MAIN/identifier/service/IdentifierService.java`, under the lock:
  `List<ObjectTypeConfiguration> findConfigurations()`, `ObjectTypeConfiguration
  findConfiguration(ObjectType)`, `ObjectTypeConfiguration replaceConfiguration(ObjectType,
  SequenceConfiguration, KeyFieldDeclaration)` (saves the new configuration; never touches
  registrations). T045 passes.
- [X] T049 [US5] Create `MAIN/identifier/controller/ObjectTypeController.java`
  (`/api/v1/object-types`): `GET` (collection envelope), `GET /{objectType}`, `PUT /{objectType}`
  (`@Valid` body → model value objects → service). The `{objectType}` path variable is a
  `String`; the controller looks it up in `ObjectType` and throws `ObjectTypeNotFoundException`
  when unknown. In the body, an unknown `sequence.kind` text →
  `InvalidIdentifierRequestException(sequence.kind, INVALID_SEQUENCE_KIND)`; the
  `SequenceConfiguration` and `KeyFieldDeclaration` constructors raise the other 400 codes.
- [X] T050 [US5] Extend `MAIN/identifier/controller/IdentifierExceptionHandler.java`: unknown
  object type in a path (`ObjectTypeNotFoundException`) → 404, title "No encontrado", detail "No existe el tipo de objeto %s.". T046
  passes.

**Checkpoint**: all five stories work; build passes.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T051 [P] Write `TEST/identifier/service/IdentifierServiceConcurrencyTest.java` (SC-002,
  R-10): 100 automatic `PRODUCT` assignments submitted at once from a thread pool → 100 distinct
  IDs `PRD000001`–`PRD000100`; 1,000 assignments spread over all six types (including the 100) →
  no duplicate ID across types; two threads sending the same manual ID `CLS-X` at the same moment
  (with permission) → exactly one `ASSIGNED` and one `SystemIdInUseException` (no key fields
  declared, D-8).
- [X] T052 Compare every operation, status, header, field and error code in
  `specs/003-identifier-management/contracts/identifiers.openapi.yaml` with the controllers and
  handler; list any difference and fix the code (report instead if the contract itself is wrong).
- [X] T053 [P] Check every expected value in `specs/003-identifier-management/quickstart.md`
  (IDs, statuses, error codes) against the finished code; if one differs while the code follows the
  contract, correct the quickstart and report it; otherwise confirm no change is needed.
- [X] T054 Run `.\gradlew.bat spotlessApply` and `.\gradlew.bat clean build` in `backend\` with the
  local MongoDB running; all tests pass (T001 count plus the new tests). Report the per-class test
  counts.
- [X] T055 Run quickstart scenarios 1–15 against a running backend (`.\gradlew.bat bootRun`) and
  record the results in the implementation report; scenario 16 is covered by T051.
- [X] T056 Scope check (quickstart "Scope checks"): `git diff --stat main` shows no change under
  `frontend/`, `MAIN/product/`, `TEST/product/`, `specs/002-existing-product-search/`,
  `.specify/memory/constitution.md` or `CLAUDE.md`; `backend/build.gradle` unchanged; no class
  outside `MAIN/identifier/` except `MAIN/common/ApiProblems.java`.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)** → **Foundational (2)** → user stories → **Polish (8)**.
- **US1 (3)** creates `IdentifierService` and `SystemIdController`; **US2 (4)**, **US3 (5)** and
  **US4 (6)** extend those files, so they follow US1. US3 depends only on US1.
- **US4** depends on US2 (it replaces the manual path's "in use" branch, T043).
- **US5 (7)** depends on US1 (it changes what the automatic path generates); it does not need US2–US4.
- Recommended order: US1 → US2 → US3 → US4 → US5 (priority order).

### Within each phase

- Tests before implementation; a test task and its implementation may share a file only where
  stated ("Extend").
- `IdentifierService.java`, `SystemIdController.java`, `IdentifierExceptionHandler.java`,
  `IdentifierServiceTest.java` and `SystemIdControllerIntegrationTest.java` are edited by several
  tasks: never run two of those tasks in parallel.

### Parallel opportunities

- Foundational: T002, T004, T005, T006, T008, T010, T012, T014, T015 together; then T017, T019,
  T020, T022 together.
- US1: T024, T025, T026 together.
- US2: T030, T031, T032 together.
- US3: T036, T037 together.
- US4: T040, T041 together.
- US5: T045, T046, T047 together.
- Polish: T051 and T053 together.

### Parallel example: User Story 1

```text
T024 IdentifierServiceTest (automatic)          ┐
T025 SystemIdControllerIntegrationTest (automatic) ├─ in parallel
T026 DTOs                                         ┘
then T027 IdentifierService → T028 SystemIdController → T029 handler mappings
```

## Requirement coverage

| Requirement | Tasks |
|---|---|
| FR-001 (global uniqueness) | T006, T007, T017, T018, T030, T031, T034, T051 |
| FR-002 (always an ID) | T024, T027 |
| FR-003 (manual ID with permission) | T012, T013, T030–T035 |
| FR-004 (manual only at creation) | T030, T036, T038 |
| FR-005 (automatic, never used before) | T008, T009, T024, T027, T030 |
| FR-006 (sequence per type) | T008, T009, T020, T021, T045–T049 |
| FR-007 (immutable) | T014, T036–T039 |
| FR-008 (config change keeps IDs) | T045, T046, T048 |
| FR-009 (duplicate rule) | T040–T044 |
| FR-010 (all creation paths) | T040, T043 (single `assign` entry point for every caller) |
| FR-011 (Spanish messages naming the ID) | T005, T029, T031, T035, T036, T039, T041 |
| FR-012 (versioned API, no screen) | T025–T029, T046, T049, T052, T056 |
| FR-013 (key-field declaration) | T010, T011, T020, T040–T042, T045–T049 |
| SC-001 | T024, T027 |
| SC-002 | T051 |
| SC-003 | T036, T037 |
| SC-004 | T031, T041 |
| SC-005 | T040, T041 |
| SC-006 | T045, T046 |
| SC-007 (message review) | T055 (messages shown to the owner in the report) |
| Decisions D-1 to D-10 | D-1 T016–T019; D-2/D-3 T008, T020; D-4 T049; D-5 T024; D-6 T006; D-7 T032; D-8 T040; D-9 T027, T051; D-10 T043, T044 |

## Implementation Strategy

1. **MVP = Phases 1–3 (US1)**: automatic IDs and read by ID. Stop and validate with quickstart
   scenarios 2, 3 and 6.
2. **All P1 = + US2, US3**: manual IDs with permission and immutability.
3. **+ US4**: the duplicate rule with key fields.
4. **+ US5**: configuration over the API.
5. **Polish**: concurrency and volume, contract check, full build, quickstart run, scope check.

Before merge (plan, Constitution Check XI): the source conflicts CF-001 to CF-004 (spec OQ-010,
OQ-011) must be resolved or explicitly accepted by the owner (Quality Gate 8).
