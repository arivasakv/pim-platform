---

description: "Task list for feature 005: attribute groups (with MongoDB storage for features 003 and 004)"
---

# Tasks: Attribute Management – Attribute Groups

**Input**: Design documents from `specs/005-attribute-groups/`

**Prerequisites**: [plan.md](./plan.md) (decisions D-1 to D-9 approved, D-1 = MongoDB),
[spec.md](./spec.md) (approved 2026-11-09), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/attribute-groups.openapi.yaml](./contracts/attribute-groups.openapi.yaml),
[quickstart.md](./quickstart.md), [ADR 0003](../../docs/decisions/0003-mongodb-attribute-administration.md)

**Tests**: Included (constitution VI): unit tests for business logic, integration tests for every
endpoint with success and error paths, a test for every validation rule with its HTTP status and
error body. Write each test first and confirm it fails (a compile failure counts).

**Organization**: Phase 2 moves features 003 and 004 to MongoDB (their approved amendments), adds the
attribute list endpoint (D-3) and the group model. Then one phase per user story (US1–US3: P1, US4:
P2, US5: P3), each with its backend and frontend part.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story the task belongs to (US1–US5)
- Commands are **Windows PowerShell**. Gradle runs in `backend\`, npm in `frontend\`.
- Path abbreviations: **`MAIN`** = `backend/src/main/java/com/pim/backend`,
  **`TEST`** = `backend/src/test/java/com/pim/backend`, **`FE`** = `frontend/src`.

## Ground rules for every task

- **Features 003 and 004 change storage only** (their amendments of 2026-11-09, ADR 0003): MongoDB
  implementations replace the in-memory ones behind the **same interfaces**; services, models, rules,
  controllers, responses and messages stay as they are, except the additions this file names
  (`contains(ObjectType)`, `findAll()`, `GET /api/v1/attributes`). Their existing test assertions stay;
  tests only gain collection cleanup. Feature 002 is not touched.
- **MongoDB** (constitution 2.3.0, ADR 0003): Spring Data MongoDB, same connection as feature 002;
  documents and Spring Data repositories are **package-private in each domain's `repository`
  package** and mapped to model types (XII). Collections and indexes exactly as in ADR 0003 §2.
  No new dependency.
- **In-memory implementations become test fakes**: `git mv` them from `MAIN` to the same package
  under `TEST` (unit tests keep using them); nothing in-memory remains in production code.
- **Feature 004 only through `AttributeService`** from the group domain (constitution III); the group
  domain reuses the public model type `AttributePermissions` and reads `X-Pim-Permissions` itself.
- **Errors**: RFC 9457 via `common.ApiProblems`; Spanish texts; codes exactly as in the contract's
  `Problem.errors[].code` enum. Unreachable MongoDB → 503 via the existing
  `common.DataAccessExceptionHandler` (no fallback to memory).
- **Integration tests** use the local MongoDB `pim-test` and call the shared reset helper (T004) in
  `@BeforeEach`, because data now outlives a Spring context.
- After each phase: backend `.\gradlew.bat spotlessApply; .\gradlew.bat build`; frontend
  `npm run lint; npm run format:check; npm test; npm run build`. All pass.

---

## Phase 1: Setup

- [X] T001 Confirm the baselines on branch `005-attribute-groups` with the local MongoDB running:
  `.\gradlew.bat clean build` passes (237 tests after feature 004) and the frontend checks pass (113
  tests). Record both counts.

---

## Phase 2: Foundational (blocking prerequisites)

### Cross-cutting

- [X] T002 [P] Write `TEST/attributegroup/controller/AttributeGroupCorsIntegrationTest.java`: preflight
  `OPTIONS /api/v1/attribute-groups/x` from `http://localhost:5173` with `Access-Control-Request-Method`
  `PUT`, and again with `DELETE` (header `content-type,x-pim-permissions`) → 200, allowed origin, and the
  method listed in `Access-Control-Allow-Methods`.
- [X] T003 Add `"PUT"` and `"DELETE"` to `allowedMethods` in `MAIN/config/CorsConfiguration.java`
  (keep `GET`, `POST`; update the Javadoc). T002 passes; existing CORS tests unchanged.
- [X] T004 Create `TEST/common/AttributeAdministrationTestData.java` (test helper): `reset(MongoTemplate,
  DefaultObjectTypeConfigurations)` **empties** the collections `system_ids`, `object_type_configurations`,
  `attributes` and `attribute_groups` with `mongoTemplate.remove(new Query(), collection)` and re-seeds
  the default object-type configurations by calling `DefaultObjectTypeConfigurations.seedMissing()`
  (T010), so each integration test starts like a fresh install.
  **Never drop the collections**: dropping removes the unique indexes `attribute_display_name_unique` and
  `attribute_group_name_unique`, which are created once at startup (T015, T022), and the uniqueness
  tests (T012, T021) would then fail.

### Feature 003 → MongoDB (amendment)

- [X] T005 [P] Write `TEST/identifier/repository/MongoSystemIdRegistryIntegrationTest.java` (`@SpringBootTest`,
  collections reset): `add` then `find` with other letter case returns the registration with every
  field (`systemId` as given, `objectType`, `keyFieldValues` in order, `assignment`, `registeredAt`);
  `contains` ignores case; `count`; adding an ID already stored (other case) → `IllegalStateException`;
  data written through one repository instance is read by a new instance (survives a restart).
- [X] T006 [P] Write `TEST/identifier/repository/MongoObjectTypeConfigurationRepositoryIntegrationTest.java`:
  `save` then `find` round-trips sequence (`kind`, `prefix` null or set, `minimumDigits`, `nextValue`) and
  key fields; `findAll` returns them in `ObjectType` order; `contains`; `find` of a missing type →
  `IllegalStateException`; `save` replaces.
- [X] T007 Extend `TEST/identifier/config/DefaultObjectTypeConfigurationsTest.java`: seeding inserts the
  six defaults when none exist (existing assertions) and **does not overwrite** an existing configuration
  (save `ATTRIBUTE` with `nextValue` 42, seed, still 42).
- [X] T008 Add `boolean contains(ObjectType)` to `MAIN/identifier/repository/ObjectTypeConfigurationRepository.java`;
  `git mv` `MAIN/identifier/repository/InMemorySystemIdRegistry.java` and
  `InMemoryObjectTypeConfigurationRepository.java` to `TEST/identifier/repository/` (remove
  `@Repository`; Javadoc: test fake) and implement `contains` in the fake.
- [X] T009 Implement in `MAIN/identifier/repository/`: package-private documents `SystemIdDocument`
  (`@Document("system_ids")`, `@Id` = ID upper-cased with `Locale.ROOT`, plus `systemId`, `objectType`,
  `keyFieldValues`, `assignment`, `registeredAt`) and `ObjectTypeConfigurationDocument`
  (`@Document("object_type_configurations")`, `@Id` = object-type code, `kind`, `prefix`,
  `minimumDigits`, `nextValue`, `keyFields`); `@Repository` classes `MongoSystemIdRegistry` (insert;
  `DuplicateKeyException` → `IllegalStateException`) and `MongoObjectTypeConfigurationRepository`
  (upsert; `findAll` sorted by `ObjectType` order), mapping to the model types. T005, T006 pass.
- [X] T010 Change `MAIN/identifier/config/DefaultObjectTypeConfigurations.java`: add a public method
  `void seedMissing()` that saves a default only for types where `repository.contains(type)` is false,
  and make `afterPropertiesSet()` call it (startup behaviour unchanged otherwise). The test helper (T004)
  calls `seedMissing()` after emptying the collections. T007 passes (it calls `seedMissing()` directly).
- [X] T011 Add `@BeforeEach` calling `AttributeAdministrationTestData.reset` to
  `TEST/identifier/controller/SystemIdControllerIntegrationTest.java` and
  `TEST/identifier/controller/ObjectTypeControllerIntegrationTest.java` (inject `MongoTemplate` and
  `DefaultObjectTypeConfigurations`); no assertion changes. All feature 003 tests pass.

### Feature 004 → MongoDB (amendment) and attribute list (D-3)

- [X] T012 [P] Write `TEST/attribute/repository/MongoAttributeRepositoryIntegrationTest.java`: save and
  `findBySystemId` (other letter case) round-trip an attribute of **each of the seven data types** with
  all properties (BigDecimal minimum/maximum keep their value; `currencySymbol` derived; LOV ID kept);
  `existsWithDisplayName` ignores case; a second attribute with the same display name (other case) or
  ID → `IllegalStateException` (unique index `attribute_display_name_unique`); `findAll()` sorted by
  display name ignoring case; a new repository instance reads the same data.
- [X] T013 [P] Write `TEST/attribute/controller/AttributeListIntegrationTest.java` (reset in
  `@BeforeEach`): `GET /api/v1/attributes` with no attributes → 200 `{"items":[],"total":0}`; after
  creating "Volumen", "peso", "Color" → `total` 3, items in order Color, peso, Volumen, each shaped like
  `GET /api/v1/attributes/{id}` (only the matching properties object).
- [X] T014 Add `List<Attribute> findAll()` to `MAIN/attribute/repository/AttributeRepository.java`
  (Javadoc: sorted by display name ignoring case); `git mv` `MAIN/attribute/repository/InMemoryAttributeRepository.java`
  to `TEST/attribute/repository/` (remove `@Repository`; test fake) and implement `findAll` there.
- [X] T015 Implement in `MAIN/attribute/repository/`: package-private `AttributeDocument`
  (`@Document("attributes")`, `@Id` = system ID upper-cased, `systemId`, `displayName`,
  `displayNameKey` = display name lower-cased, `description`, `dataType`, `scope`, one nullable embedded
  sub-document per property type, `createdAt`) and `@Repository MongoAttributeRepository`
  (`InitializingBean`: creates unique index `attribute_display_name_unique` on `displayNameKey`; insert,
  duplicate key → `IllegalStateException`; `findAll` sorted by `displayNameKey`), mapping to and from the
  model records. T012 passes.
- [X] T016 Add `List<Attribute> findAll()` to `MAIN/attribute/service/AttributeService.java` (under the
  lock) and `GET /api/v1/attributes` to `MAIN/attribute/controller/AttributeController.java` returning
  a collection envelope (new `MAIN/attribute/dto/AttributeCollectionResponse.java`: `items`, `total`).
  Add the operation to `specs/004-attribute-creation/contracts/attributes.openapi.yaml` and bump it to
  version 1.1.0 (additive). T013 passes.
- [X] T017 Add `@BeforeEach` reset (T004) to `TEST/attribute/controller/AttributeControllerIntegrationTest.java`
  and `TEST/attribute/controller/AttributeListOfValuesIntegrationTest.java`; no assertion changes. All
  feature 004 tests pass.

### Group model and repository

- [X] T018 [P] Write `TEST/attributegroup/model/GroupNameTest.java` (FR-003, D-5): "trimmed; 1–80
  characters"; `null` → `REQUIRED`, blank or 81 characters → `INVALID_GROUP_NAME` (field `name`);
  equality ignores case (`Locale.ROOT`), value keeps the case given.
- [X] T019 [P] Write `TEST/attributegroup/model/AttributeGroupTest.java`: `AttributeGroup.create(id, name,
  description, createdAt)` has no attributes; description "trimmed; up to 500 characters; blank = absent"
  (501 → `INVALID_GROUP_DESCRIPTION`); `withAttributes`/`renamed` return copies keeping `id`;
  `attributeIds` unmodifiable, at most once each ignoring case; `contains(attributeId)` ignores case.
- [X] T020 Create in `MAIN/attributegroup/model/`: `GroupName`, `AttributeGroup` (record: `id`, `name`,
  `description` Optional, `attributeIds` List, `createdAt`), `AttributeGroupErrorCode` (the contract's
  codes with Spanish templates, e.g. `GROUP_NAME_IN_USE` → "Ya existe un grupo con el nombre \"%s\". Usa
  otro nombre.", `GROUP_NOT_EMPTY` → "El grupo \"%s\" tiene %s atributos. Quítalos antes de eliminarlo.",
  `ATTRIBUTE_NOT_FOUND` → "No existe un atributo con el ID %s."), and unchecked exceptions
  `InvalidGroupException` (field, code), `GroupChangeNotPermittedException`, `GroupNotFoundException`,
  `GroupNameInUseException`, `GroupNotEmptyException`, `UnknownAttributeException`,
  `AttributeNotInGroupException`. T018, T019 pass.
- [X] T021 [P] Write `TEST/attributegroup/repository/MongoAttributeGroupRepositoryIntegrationTest.java`:
  save/find by `id`; `findAll` sorted by name ignoring case; `existsWithName` ignores case and can
  exclude one group (for renames); unique index `attribute_group_name_unique` refuses a duplicate name
  (→ `IllegalStateException`); `save` of an existing `id` replaces; `delete`; a new instance reads the data.
- [X] T022 Create `MAIN/attributegroup/repository/AttributeGroupRepository.java` (interface: `findById`,
  `findAll`, `existsWithName(GroupName, String excludedId)`, `insert`, `update`, `delete`), package-private
  `AttributeGroupDocument` (`@Document("attribute_groups")`, `@Id` UUID, `name`, `nameKey`, `description`,
  `attributeIds`, `createdAt`) and `@Repository MongoAttributeGroupRepository` (`InitializingBean`
  creates the unique `nameKey` index). T021 passes.
- [X] T023 [P] Create `MAIN/attributegroup/controller/GroupPermissionsReader.java` (`@Component`; reads
  `X-Pim-Permissions` into `com.pim.backend.attribute.model.AttributePermissions`; Javadoc: development
  stand-in, clarification 3) and `MAIN/attributegroup/controller/AttributeGroupExceptionHandler.java`
  (`basePackageClasses`, highest precedence): `InvalidGroupException` and Bean Validation → 400 (`@NotNull`
  → `REQUIRED`; `@Size` on `name` → `INVALID_GROUP_NAME`, on `description` → `INVALID_GROUP_DESCRIPTION`,
  on `attributeIds` → `INVALID_ATTRIBUTE_IDS`; no silent fallback); unreadable body → 400 `REQUIRED`.
  Later tasks add the other mappings. The handler maps **only** its own exception types and the
  validation/body exceptions named here: no `Exception`, `RuntimeException` or `DataAccessException`
  handler, so MongoDB failures still reach `common.DataAccessExceptionHandler` (503, G1).

**Checkpoint**: full build passes; features 003 and 004 run on MongoDB with all their tests green.

---

## Phase 3: User Story 1 - Create an attribute group (Priority: P1) 🎯 MVP

**Goal**: create groups and list them. **Independent Test**: quickstart API 1, 3, 4; UI U1, U2.

- [X] T024 [P] [US1] Write `TEST/attributegroup/service/AttributeGroupServiceTest.java` (US1 part; real
  `AttributeService` and `IdentifierService` on the in-memory test fakes, an in-memory group fake in the
  test, fixed `Clock`): `create` without `ATTRIBUTE_CREATE` → `GroupChangeNotPermittedException`; with it
  → group with UUID id, no attributes, `createdAt`; duplicate name ignoring case → `GroupNameInUseException`;
  `findAll` sorted by name.
- [X] T025 [P] [US1] Write `TEST/attributegroup/controller/AttributeGroupControllerIntegrationTest.java`
  (US1 part, reset in `@BeforeEach`): `GET /api/v1/attribute-groups` → 200 `total` 0 (FR-015); `POST`
  `{"name":"Logísticos","description":"Dimensiones"}` → 201, `Location: /api/v1/attribute-groups/{id}`,
  body `id`, `name`, `description`, `attributeCount` 0, `attributes` `[]`, `createdAt`; list now has it;
  `" logísticos "` → 409 `GROUP_NAME_IN_USE`; missing name → 400 `REQUIRED`; blank or 81 characters →
  400 `INVALID_GROUP_NAME`; 501-character description → 400 `INVALID_GROUP_DESCRIPTION`; no header →
  403 `ATTRIBUTE_CREATE_NOT_PERMITTED`, title "Sin permiso para administrar grupos".
  Storage failure (A-006, edge case "the system cannot store the change"): in a separate class
  `TEST/attributegroup/controller/AttributeGroupStorageFailureIntegrationTest.java` with
  `@MockitoBean AttributeGroupRepository` throwing `DataAccessResourceFailureException` → `POST` and
  `GET /api/v1/attribute-groups` answer 503 with the existing Spanish problem body of
  `common.DataAccessExceptionHandler`, and nothing is saved (`insert` called at most once, no other write).
- [X] T026 [P] [US1] Create DTOs in `MAIN/attributegroup/dto/`: `AttributeGroupRequest` (`@NotNull
  @Size(max = 200) String name`, `@Size(max = 1000) String description`), `GroupMemberResponse`
  (`systemId`, `displayName`, `dataType`), `AttributeGroupResponse` (`id`, `name`, `description`,
  `attributeCount`, `attributes`, `createdAt`), `AttributeGroupSummaryResponse` (`id`, `name`,
  `description`, `attributeCount`), `AttributeGroupCollectionResponse` (`items`, `total`).
- [X] T027 [US1] Create `MAIN/attributegroup/service/AttributeGroupService.java` (`@Service`; inject
  `AttributeGroupRepository`, `AttributeService`, `Clock`; one lock): `create(name, description,
  AttributePermissions)`, `findAll()`. T024 passes.
- [X] T028 [US1] Create `MAIN/attributegroup/controller/AttributeGroupController.java`
  (`/api/v1/attribute-groups`): `GET` (summaries) and `POST` (201 + `Location`); extend the handler with
  403 and 409 `GROUP_NAME_IN_USE` ("Nombre en uso"). T025 passes.
- [X] T029 [P] [US1] Write `FE/api/attributeGroupsApi.test.ts` (mocked `fetch`, `vi.stubEnv`):
  `listGroups()`, `createGroup()` with header `X-Pim-Permissions` from `VITE_DEV_PERMISSIONS`; 400/403/
  404/409/422 → `AttributeGroupApiError` with `kind`, `detail`, `errors`; network failure → `unavailable`.
- [X] T030 [P] [US1] Write `FE/pages/AttributeGroupsPage.test.tsx` (US1): loading, then empty state
  "Todavía no hay grupos de atributos." with a create form ("Nombre", "Descripción", "Crear grupo") when
  `VITE_DEV_PERMISSIONS` contains `ATTRIBUTE_CREATE`; **without it**, the empty state and list are shown
  but no create form (US4 scenario 4); creating "Logísticos" lists it with "0 atributos" and shows the
  Spanish confirmation "Grupo «Logísticos» creado." (US1 scenario 1); a 409 shows the message next to
  "Nombre"; empty name → UX message without calling the API; backend unreachable → connection error
  message with a "Reintentar" button.
- [X] T031 [US1] Implement `FE/api/attributeGroupsApi.ts` (types from the contract; reuse the header
  logic of `FE/api/attributesApi.ts` by exporting it from there or a small shared helper in `FE/api/`).
  T029 passes.
- [X] T032 [US1] Implement `FE/pages/AttributeGroupsPage.tsx` and `.module.css` (list + create form
  shown only with the `ATTRIBUTE_CREATE` development permission, confirmation message after creating,
  design-system components only); add `{ path: '/grupos-de-atributos', label: 'Grupos de atributos',
  icon: 'components' }` to `FE/navigation.ts`, the route in `FE/App.tsx` and one new case in
  `FE/navigation.test.ts`. T030 passes.

---

## Phase 4: User Story 2 - Add attributes to a group (Priority: P1)

**Goal**: add one or more existing attributes; an attribute may be in several groups.
**Independent Test**: quickstart API 2, 5–9; UI U3.

- [X] T033 [P] [US2] Extend `AttributeGroupServiceTest.java`: add two existing attributes → `added`
  both, group lists them in order; add one already present (other letter case) → `alreadyInGroup`, no
  duplicate; one unknown ID among known ones → `UnknownAttributeException` naming it, nothing added; the
  same attribute in two groups; attributes unchanged (`attributeService.find` equal before and after);
  no permission → refused; unknown group → `GroupNotFoundException`.
- [X] T034 [P] [US2] Extend `AttributeGroupControllerIntegrationTest.java` (create attributes through
  `POST /api/v1/attributes` first): `POST …/{id}/attributes` `{"attributeIds":["ATR000001","ATR000002"]}`
  → 200 `added`, `group.attributeCount` 2, members with `displayName` and `dataType`; repeat with
  `atr000001` → `alreadyInGroup`; `["ATR000003","ATR999"]` → 422 `ATTRIBUTE_NOT_FOUND` naming `ATR999`,
  count unchanged; `[]` and 101 IDs → 400 `INVALID_ATTRIBUTE_IDS`; unknown group → 404 `GROUP_NOT_FOUND`;
  no header → 403; `GET /api/v1/attributes/ATR000001` unchanged.
- [X] T035 [US2] Add `MAIN/attributegroup/dto/AddAttributesRequest.java` (`@NotNull @Size(min = 1, max =
  100) List<String> attributeIds`), `AddAttributesResponse` (`group`, `added`, `alreadyInGroup`),
  `MAIN/attributegroup/model/AddAttributesOutcome.java`; implement `addAttributes(groupId, ids,
  permissions)` in the service (validate all through `AttributeService.find` first, then add) and the
  endpoint; handler: 404 `GROUP_NOT_FOUND`, 422 `ATTRIBUTE_NOT_FOUND` ("Atributo no encontrado").
  T033, T034 pass.
- [X] T036 [P] [US2] Extend `FE/api/attributesApi.ts` and its test with `listAttributes()`
  (`GET /api/v1/attributes`), and `FE/api/attributeGroupsApi.ts` and its test with `getGroup(id)` and
  `addAttributes(id, ids)`.
- [X] T037 [US2] Extend `FE/pages/AttributeGroupsPage.tsx` and its test: selecting a group opens its
  panel; "Agregar atributos" shows checkboxes for attributes not in the group (from `listAttributes`);
  adding updates the members and the count; a 422 shows the backend message. Without
  `ATTRIBUTE_CREATE` in `VITE_DEV_PERMISSIONS`, the panel shows the members but no "Agregar atributos"
  control (page test), as for the create form (T030).

---

## Phase 5: User Story 3 - Remove an attribute from a group (Priority: P1)

**Goal**: unlink without deleting. **Independent Test**: quickstart API 10, 11; UI U4.

- [X] T038 [P] [US3] Extend the service test and the controller integration test: remove → 204, the
  attribute still exists and stays in its other groups; remove again → 404 `ATTRIBUTE_NOT_IN_GROUP`;
  removing the last group leaves the attribute ungrouped; no permission → 403; unknown group → 404.
- [X] T039 [US3] Implement `removeAttribute(groupId, attributeId, permissions)` and
  `DELETE /api/v1/attribute-groups/{groupId}/attributes/{attributeId}`; handler 404
  `ATTRIBUTE_NOT_IN_GROUP` ("El atributo %s no está en este grupo."). T038 passes.
- [X] T040 [US3] Frontend: `removeAttribute(id, attributeId)` in the API client (+ test); a "Quitar"
  button per member in the panel, shown only with the `ATTRIBUTE_CREATE` development permission (+ page
  test: removed member reappears in the picker; no "Quitar" button without the permission).

---

## Phase 6: User Story 4 - See groups and their attributes (Priority: P2)

**Goal**: list groups, a group's members, an attribute's groups. **Independent Test**: quickstart API
6, 15, 16.

- [X] T041 [P] [US4] Extend the controller integration test: `GET /api/v1/attribute-groups/{id}` → 200
  members (`systemId`, `displayName`, `dataType`) in the order added; unknown → 404 `GROUP_NOT_FOUND`;
  `GET /api/v1/attribute-groups?attributeId=atr000001` → only the groups containing it; unknown attribute
  → empty list; reading needs no header.
- [X] T042 [US4] Implement `find(groupId)` and `findAllContaining(attributeId)` in the service and the
  two `GET` mappings. T041 passes.
- [X] T043 [US4] Frontend: the list shows name, description and count; the panel lists members with
  display name, type label and system ID (+ page test).

---

## Phase 7: User Story 5 - Rename or delete a group (Priority: P3)

**Goal**: housekeeping. **Independent Test**: quickstart API 12–14; UI U5–U7.

- [X] T044 [P] [US5] Extend the service test and the controller integration test: `PUT` rename +
  description → 200, same `id`, members kept; rename to another group's name ignoring case → 409
  `GROUP_NAME_IN_USE`; rename to its own name in other case → allowed; `DELETE` a group with attributes
  → 409 `GROUP_NOT_EMPTY` (message names the group and the count), nothing changes; empty group → 204,
  gone from the list; no permission → 403; unknown → 404.
- [X] T045 [US5] Implement `update(groupId, name, description, permissions)` and `delete(groupId,
  permissions)` with the two mappings; handler 409 `GROUP_NOT_EMPTY` ("Grupo con atributos"). T044 passes.
- [X] T046 [US5] Frontend: `updateGroup`, `deleteGroup` in the API client (+ test); in the panel,
  "Editar" (name, description) and "Eliminar grupo", both shown only with the `ATTRIBUTE_CREATE`
  development permission; deleting a non-empty group shows the backend message (+ page test, including
  no "Editar" or "Eliminar grupo" without the permission). The backend still refuses with 403 whatever
  the page shows (FR-002).

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T047 [P] Write `TEST/attributegroup/service/AttributeGroupServiceConcurrencyTest.java`: 20
  simultaneous creations with the same name → one group; 50 simultaneous additions of distinct
  attributes to one group → all present once.
- [X] T048 Compare `specs/005-attribute-groups/contracts/attribute-groups.openapi.yaml` (and feature 004's
  v1.1.0 list operation) with the controllers, DTOs and handler; fix the code or report.
- [X] T049 [P] Check the expected values of `specs/005-attribute-groups/quickstart.md`; correct only
  where the code follows the contract, and report.
- [X] T050 Full builds: backend `spotlessApply` + `clean build`; frontend lint, format, test, build.
  Report counts per class / file.
- [X] T051 Run quickstart API scenarios 1–17 against a running backend on a temporary MongoDB (another
  port), including scenario 17: restart the backend, the groups remain, and a new attribute gets the next
  unused ID and is in no group. Record results.
- [X] T052 Owner check: UI scenarios U1–U7 in the browser.
- [X] T053 Scope check: `git diff --stat` shows no change to feature 002, feature 003 and 004 changes
  limited to storage, the `contains`/`findAll` additions, the list endpoint and test cleanup; no new
  dependency; the in-memory classes exist only under `TEST`.

---

## Dependencies & Execution Order

- **Setup (1)** → **Foundational (2)** → US1 (3) → US2 (4) → US3 (5) → US4 (6) → US5 (7) → **Polish (8)**.
- Inside Phase 2: T004 before T011 and T017; feature 003 (T005–T011) before feature 004 (T012–T017),
  because attributes are created through `IdentifierService`; group model and repository (T018–T023)
  can run alongside the feature 004 tasks.
- US2–US5 extend the service, controller, handler, page and their tests created in US1: run them in
  order; never two tasks on the same file at once.

### Parallel opportunities

- Phase 2: T002, T005, T006, T012, T013, T018, T019, T021, T023 (tests and independent files).
- US1: T024, T025, T026, T029, T030. US2: T033, T034, T036. US3: T038. US4: T041. US5: T044.
- Polish: T047, T049.

## Requirement coverage

| Requirement | Tasks |
|---|---|
| FR-001 (create) | T020, T024–T032 |
| FR-002 (`ATTRIBUTE_CREATE`) | T023–T025, T030, T032, T033, T034, T037, T038, T040, T044, T046 |
| FR-003 (unique names) | T018, T021, T022, T024, T025, T044, T047 |
| FR-004 (flat) | T020 (no parent in the model), T022 |
| FR-005 (rename; delete only empty) | T044–T046 |
| FR-006 (add existing attributes) | T013–T016, T033–T037 |
| FR-007 (several groups, or none) | T033, T034, T038, T041 |
| FR-008 (no duplicate membership) | T019, T033, T034, T047 |
| FR-009 (remove without deleting) | T038–T040 |
| FR-010 (attribute unchanged) | T033, T034, T038 |
| FR-011 (unknown group/attribute) | T033–T035, T038, T041, T044 |
| FR-012 (viewing) | T013, T016, T041–T043 |
| FR-013 (page, REST only) | T029–T032, T036, T037, T040, T043, T046 |
| FR-014 (deferred rules) | none to build (recorded in spec) |
| FR-015 (start empty) | T025 |
| FR-016 (UUID identifier) | T020, T024, T025, T044 |
| D-1 (MongoDB, with 003/004) | T004–T017, T021, T022, T051 (restart) |
| A-006 / edge case "cannot store" (503, nothing half-saved) | T023, T025 |
| SC-001, SC-006 | T052 |
| SC-002, SC-004 | T033, T034, T038, T041 |
| SC-003 | T025, T034, T038, T044 |
| SC-005 | T025, T034, T047 |
| SC-007 | T044 |

## Implementation Strategy

1. **Phase 2 first**: features 003 and 004 on MongoDB with all their existing tests green, plus the
   attribute list endpoint. Stop and check: restart the backend, attributes and IDs persist.
2. **MVP = US1**: create and list groups (backend and page).
3. **+ US2, US3** (all P1): add and remove attributes.
4. **+ US4, US5**: viewing details, rename and delete.
5. **Polish**: concurrency, contract and quickstart checks, builds, live run with restart, owner UI
   check, scope check.

Before merge: CF-001 to CF-003 (features 004 and 005) resolved or accepted, and A-002 confirmed.
