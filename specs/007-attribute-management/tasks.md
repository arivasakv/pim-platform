# Tasks: Attribute Management – Consult, Edit and Delete

**Input**: Design documents from `specs/007-attribute-management/` (spec, plan, research, data-model,
contracts/attributes-v1.2.openapi.yaml, quickstart)

**Prerequisites**: plan decisions D-1 to D-10, approved by the project owner on 2026-11-11.

**Tests**: requested by the feature request (§6). Within each phase, write the tests first and see them
fail.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 consult, US2 edit, US3 delete

## Path Conventions

- `BE/` = `backend/src/main/java/com/pim/backend/`
- `BT/` = `backend/src/test/java/com/pim/backend/`
- `FE/` = `frontend/src/`

---

## Phase 1: Setup

- [X] T001 Check the starting point: `git status` clean on `main`; backend `.\gradlew.bat clean build`
  and frontend `npm test`, `npm run lint`, `npm run build` pass before any change; record test counts
  (backend 427, frontend 158) in this file's notes.

---

## Phase 2: Foundational (blocks every story)

**Purpose**: model, storage and the two directories every story uses (research R-02, R-03, R-08).

### Tests first

- [X] T002 [P] Extend `BT/attribute/model/AttributeTest.java`: `Attribute.updated(displayName,
  description, properties, at)` keeps `systemId`, `dataType`, `scope`, `createdAt` and sets
  `updatedAt`; `Attribute.create` has empty `updatedAt`; properties must still match the data type.
- [X] T003 [P] Extend `BT/attribute/repository/MongoAttributeRepositoryIntegrationTest.java`: `update`
  replaces by `_id` and keeps the display-name index (a duplicate name → `IllegalStateException`);
  `delete` removes the document; `existsWithDisplayName(name, exceptSystemId)` ignores the excepted
  attribute and letter case; a document inserted **without** `updatedAt` (raw `MongoTemplate` insert, as
  features 004–006 wrote them) reads with empty `updatedAt`.
- [X] T004 [P] Extend `BT/attribute/repository/InMemoryAttributeRepositoryTest.java` with the same three
  operations for the fake.
- [X] T005 [P] New `BT/lov/service/ListOfValuesCatalogAdapterTest.java` (or extend the existing lookup
  test): `namesById()` returns every list's name keyed by upper-cased system ID; empty map without lists.
- [X] T006 [P] New `BT/attributegroup/service/AttributeGroupDirectoryAdapterTest.java`:
  `groupsByAttribute()` maps upper-cased attribute IDs to `GroupReference(id, name)` sorted by name
  ignoring case; `runIfInNoGroup` runs the action and returns empty when no group contains the
  attribute (ignoring letter case), and returns the containing groups without running it otherwise.

### Implementation

- [X] T007 Extend `BE/attribute/model/Attribute.java`: component `Optional<Instant> updatedAt` (not null;
  empty from `create`), method `updated(DisplayName, String description, AttributeProperties, Instant)`
  using `normalizedDescription`; update the Javadoc ("created once and never changed" no longer holds).
- [X] T008 [P] New `BE/attribute/model/GroupReference.java` (record `id`, `name`) and
  `BE/attribute/model/AttributeDetails.java` (record `attribute`, `List<GroupReference> groups`,
  `Optional<String> listOfValuesName`).
- [X] T009 Extend `BE/attribute/model/AttributeErrorCode.java` with, exactly:
  `ATTRIBUTE_CHANGE_NOT_PERMITTED("No tienes permiso para modificar atributos.")`,
  `SYSTEM_ID_IMMUTABLE("El ID de sistema no se puede cambiar.")`,
  `DATA_TYPE_IMMUTABLE("El tipo de dato no se puede cambiar.")`,
  `ATTRIBUTE_IN_GROUPS("El atributo pertenece a %s: %s. Quítalo de esos grupos antes de eliminarlo.")`
  (first argument "1 grupo" / "N grupos", second the names joined with ", "). Existing codes unchanged.
- [X] T010 [P] New exceptions in `BE/attribute/model/`: `AttributeChangeNotPermittedException`,
  `ImmutableFieldException(field, code)` (`systemId`/`SYSTEM_ID_IMMUTABLE`, `dataType`/
  `DATA_TYPE_IMMUTABLE`), `AttributeInGroupsException(List<GroupReference> groups)` with the T009
  message.
- [X] T011 Extend `BE/attribute/repository/AttributeDocument.java` with nullable `Instant updatedAt`
  (mapped to and from `Attribute.updatedAt`); extend `AttributeRepository` and
  `MongoAttributeRepository` with `existsWithDisplayName(DisplayName, String exceptSystemId)`,
  `update(Attribute)` (replace by `_id`; `DuplicateKeyException` → `IllegalStateException`) and
  `delete(String systemId)`; extend the fake `BT/attribute/repository/InMemoryAttributeRepository.java`.
  T003 and T004 pass.
- [X] T012 Extend `BE/attribute/repository/ListOfValuesCatalog.java` with `Map<String, String>
  namesById()`; implement it in `BE/lov/service/ListOfValuesLookup.java` (from the repository's
  `findAll`) and `BE/lov/service/ListOfValuesCatalogAdapter.java`; the fake
  `BT/attribute/repository/EmptyListOfValuesCatalog.java` returns `Map.of()`; any other test catalog
  fake (e.g. in `AttributeServiceTest`) returns its lists' names. T005 passes.
- [X] T013 New `BE/attribute/repository/AttributeGroupDirectory.java` (interface:
  `groupsByAttribute()`, `runIfInNoGroup(String attributeId, Runnable action)`; Javadoc: callers must not
  hold the attribute lock, research R-06).
- [X] T014 Add `runIfInNoGroup(String attributeId, Runnable action)` to
  `BE/attributegroup/service/AttributeGroupService.java` (under its existing lock; returns the groups
  containing the attribute, empty when the action ran); existing methods unchanged. New
  `BE/attributegroup/service/AttributeGroupDirectoryAdapter.java` implementing
  `AttributeGroupDirectory` with `ObjectProvider<AttributeGroupService>` (no bean cycle, R-02). T006
  passes.
- [X] T015 Add `BE/attribute/service/AttributeService.java` constructor parameter
  `AttributeGroupDirectory`; new test fake `BT/attribute/repository/NoAttributeGroups.java` (no groups;
  `runIfInNoGroup` runs the action) and a configurable `FakeAttributeGroups` for service tests; pass
  `new NoAttributeGroups()` in every existing `new AttributeService(…)` call
  (`AttributeServiceTest`, `AttributeServiceConcurrencyTest`, `AttributeGroupServiceTest`,
  `AttributeGroupServiceConcurrencyTest`, `ListOfValuesServiceTest`,
  `ListOfValuesServiceConcurrencyTest`) without changing any assertion. Full backend build passes.

**Checkpoint**: backend builds; all existing tests pass unchanged in their assertions.

---

## Phase 3: User Story 1 – Consult and search attributes (P1) 🎯 MVP

**Goal**: "Atributos" lists every attribute with groups and list name, searches, filters, opens a
detail, and creates through feature 004's form.

**Independent test**: spec US1; quickstart API 1–2, UI U1–U5, U10.

### Tests first

- [X] T016 [P] [US1] Extend `BT/attribute/service/AttributeServiceTest.java`: `findAllDetails()` returns
  attributes sorted by display name with their groups (from `FakeAttributeGroups`) and the list name for
  LOV attributes (absent when the catalog doesn't know the list); `findDetails(id)` ignores letter case;
  404 for unknown IDs; `create` answers details with empty groups.
- [X] T017 [P] [US1] Extend `BT/attribute/controller/AttributeListIntegrationTest.java` (real MongoDB,
  real group and list services): `GET /api/v1/attributes` items include `groups` [{id, name}] for an
  attribute in a group (created through `/api/v1/attribute-groups`), `listOfValuesProperties
  .listOfValuesName` for an LOV attribute, no `updatedAt`; `GET /api/v1/attributes/{id}` the same;
  feature 004's existing assertions unchanged.
- [X] T018 [P] [US1] Extend `FE/api/attributesApi.test.ts`: `getAttribute(id)` (200, 404 →
  `AttributeApiError` kind `notFound` or the existing equivalent), `canChangeAttributes()` reads
  `VITE_DEV_PERMISSIONS` like `canChangeLists`; the `Attribute` type carries `groups`, `updatedAt`,
  `listOfValuesProperties.listOfValuesName`.
- [X] T019 [P] [US1] Move the creation tests with the component: `git mv
  FE/pages/AttributeCreationPage.test.tsx FE/attributes/AttributeEditor.test.tsx`; render
  `<AttributeEditor mode="create" onSaved={…} onCancel={…} />`; keep every assertion; the heading check
  becomes the card title "Nuevo atributo"; add one test that `onSaved` receives the created attribute.
- [X] T020 [P] [US1] New `FE/pages/AttributesPage.test.tsx` with a fake backend (`fetch` stub answering
  `/api/v1/attributes`, `/api/v1/attribute-types`, `/api/v1/lists-of-values`): loading then list sorted by
  name with ID, type label (from attribute types), group names and list name; search "pes" and "PES" and
  "logistico" (accent-insensitive on names) filters rows and shows the match count; type filter
  combines with search; no-match message and clearing; empty state with "Nuevo atributo"; load error
  with "Reintentar"; opening an attribute shows the detail (ID, name, description, type, each property
  with Spanish labels, groups, list, created date, changed date when present); "Nuevo atributo" opens the
  editor and a saved attribute appears selected; without `ATTRIBUTE_CREATE` no "Nuevo atributo";
  "Mostrar más" after 50 rows.
- [X] T021 [P] [US1] Update `FE/navigation.test.ts`: order Inicio, Buscar Producto, Lista de Valores,
  Atributos, Grupo de Atributos; `#/atributos` resolves; `#/crear-atributo` resolves to home.

### Implementation

- [X] T022 [US1] `BE/attribute/service/AttributeService.java`: `findAllDetails()`, `findDetails(id)` and
  a private `details(List<Attribute>)` composing groups (`AttributeGroupDirectory.groupsByAttribute()`)
  and list names (`ListOfValuesCatalog.namesById()`) **after** releasing the attribute lock (R-06);
  `create` keeps its rules and returns `AttributeDetails`. T016 passes.
- [X] T023 [US1] DTOs: new `BE/attribute/dto/GroupReferenceResponse.java`; `AttributeResponse` built from
  `AttributeDetails` with `updatedAt`, `groups` and `listOfValuesProperties.listOfValuesName` (check
  `ListOfValuesPropertiesDto` or the response mapping in `AttributePropertiesMapper` for where the list
  ID is written); `AttributeController` `GET`s and `POST` use the details. T017 passes; feature 004 and
  005 integration tests still pass.
- [X] T024 [P] [US1] `FE/api/attributesApi.ts`: types (`GroupReference`, `Attribute.groups`,
  `updatedAt`, `listOfValuesName`), `getAttribute`, `canChangeAttributes`. T018 passes.
- [X] T025 [US1] Move the form: `git mv FE/pages/AttributeCreationPage.tsx
  FE/attributes/AttributeEditor.tsx` (and its CSS module if any to `AttributeEditor.module.css`);
  export `AttributeEditor({ mode, attribute?, onSaved, onCancel })` rendering a `Card` titled "Nuevo
  atributo" (create) with a "Cancelar" button, without `PageHeader`; creation logic, messages and
  validations unchanged. T019 passes.
- [X] T026 [US1] New `FE/pages/AttributesPage.tsx` and `AttributesPage.module.css`, structured like
  `FE/pages/ListsOfValuesPage.tsx`: `PageHeader` "Atributos" with a short description; left column card
  "Atributos" (actions: "Nuevo atributo" when `canChangeAttributes()`), `TextField` type search
  "Buscar por nombre o ID", `SelectField` "Tipo de dato" (option "Todos" + labels from
  `fetchAttributeTypes`), match count ("N atributos"), list of buttons (name, system ID, type label,
  "Grupos: …" or "Sin grupos", "Lista: …" for LOV), "Mostrar más" (50 rows per step); right column:
  detail card (title = display name; system ID; description; type; properties as a definition list with
  Spanish labels and option labels from the types endpoint; currency symbol; reference target; groups;
  list name; "Creado" and "Modificado" dates in es-MX) or the editor; loading, empty, no-match and error
  states with the same texts style as feature 006; success notification after create. Search: trim,
  `normalize('NFD')`, strip combining marks, lower-case, `includes` on display name and system ID.
  T020 passes.
- [X] T027 [US1] `FE/navigation.ts`: replace `{ path: '/crear-atributo', label: 'Crear Atributos' }` with
  `{ path: '/atributos', label: 'Atributos', icon: 'components' }` in the same position; `FE/App.tsx`:
  route `/atributos` → `AttributesPage`, remove `/crear-atributo` and the `AttributeCreationPage` import.
  T021 passes.

**Checkpoint**: US1 works alone (consult, search, filter, create); edit and delete buttons not yet
shown.

---

## Phase 4: User Story 2 – Edit an attribute (P1)

**Goal**: edit name, description and properties with feature 004's form and rules; ID and type fixed.

**Independent test**: spec US2; quickstart API 3–10, UI U6–U7.

### Tests first

- [X] T028 [P] [US2] Extend `BT/attribute/service/AttributeServiceTest.java` (update): saves name,
  description and properties, keeps ID, type, scope, `createdAt`, sets `updatedAt` (fixed clock);
  without `ATTRIBUTE_CREATE` → `AttributeChangeNotPermittedException`, nothing changed; unknown ID →
  `AttributeNotFoundException`; other system ID → `SYSTEM_ID_IMMUTABLE`; other data type →
  `DATA_TYPE_IMMUTABLE`; another attribute's name (" color ") → `DisplayNameInUseException`; own name and
  own name in other case allowed; LOV: unknown list → `LIST_OF_VALUES_NOT_FOUND`, missing selection
  mode → `REQUIRED`; relink to another existing list saved; check order of research R-04 (e.g.
  forbidden before not found; not found before immutable; immutable before name in use).
- [X] T029 [P] [US2] New `BT/attribute/controller/AttributeUpdateIntegrationTest.java` (MockMvc, real
  MongoDB): `PUT` 200 with body per contract v1.2.0 and `updatedAt`; 400 with feature 004 codes (e.g.
  `INVALID_PRECISION`, `INVALID_LENGTH_RANGE`, `INVALID_CAPTURE_MASK`, blank name `REQUIRED`); 403
  `ATTRIBUTE_CHANGE_NOT_PERMITTED` in `errors[]`; 404; 409 `DISPLAY_NAME_IN_USE`; 422
  `SYSTEM_ID_IMMUTABLE`, `DATA_TYPE_IMMUTABLE`, `LIST_OF_VALUES_NOT_FOUND`; after relinking, `GET
  /api/v1/lists-of-values/{id}` of both lists shows the change; edited name shown in
  `GET /api/v1/attribute-groups/{id}`; nothing changed after every refusal.
- [X] T030 [P] [US2] Extend `BT/attribute/service/AttributeServiceConcurrencyTest.java` or
  `BT/lov/service/ListOfValuesServiceConcurrencyTest.java`: relinking an attribute to list B while B is
  deleted, repeated many times — never an attribute linked to a missing list (spec FR-017).
- [X] T031 [P] [US2] Extend `BT/attribute/controller/AttributeCorsIntegrationTest.java`: preflight allows
  `PUT` and `DELETE` from the configured origin (check `CorsConfig`/`WebConfig` allowed methods first;
  if they already allow them, the test only documents it).
- [X] T032 [P] [US2] Extend `FE/attributes/attributeForm.test.ts`: `fromAttribute(attribute)` fills
  every field for each of the 7 types (numbers as strings as the form expects; missing optional
  properties as empty); `toRequest(fromAttribute(a))` equals the editable fields of `a`.
- [X] T033 [P] [US2] Extend `FE/api/attributesApi.test.ts`: `updateAttribute(id, request)` sends `PUT`
  with permission headers and returns the attribute; field errors and 403/404/409/422 map to
  `AttributeApiError` like `createAttribute`.
- [X] T034 [P] [US2] Extend `FE/attributes/AttributeEditor.test.tsx` (edit mode): form filled from the
  attribute; system ID shown read-only; data type select disabled; saving sends `PUT` and calls
  `onSaved`; a 409 shows the duplicate-name message on the display name field; "Cancelar" calls
  `onCancel` without a request.
- [X] T035 [P] [US2] Extend `FE/pages/AttributesPage.test.tsx`: "Editar" (only with permission) opens
  the editor filled in; saving updates the detail and the list row and shows "Atributo «X» actualizado.";
  cancelling returns to the unchanged detail; a 404 on save shows that the attribute no longer exists
  and reloads the list.

### Implementation

- [X] T036 [US2] `BE/attribute/service/AttributeService.java`: `update(String systemId, AttributeDraft
  draft, AttributePermissions)` following research R-04 and data-model "Update" (permission, then under
  the lock: find, immutable checks, LOV checks reusing `checkListOfValues`, name check with
  `existsWithDisplayName(name, systemId)`, `repository.update(attribute.updated(…, clock.instant()))`),
  returning `AttributeDetails`. T028 and T030 pass.
- [X] T037 [US2] `BE/attribute/dto/AttributeUpdateRequest.java` (fields of the contract; `systemId`
  optional, `displayName` and `dataType` required; same size limits as creation);
  `AttributePropertiesMapper` accepts it (extract a shared source of properties or an overload; no
  change in creation behavior); `AttributeController` `@PutMapping("/{systemId}")`;
  `AttributeExceptionHandler` handlers for `AttributeChangeNotPermittedException` (403, field
  `permissions`) and `ImmutableFieldException` (422, field from the exception). T029 and T031 pass.
- [X] T038 [P] [US2] `FE/attributes/attributeForm.ts`: `fromAttribute(attribute): AttributeForm`; reuse
  `toRequest` for updates. T032 passes.
- [X] T039 [P] [US2] `FE/api/attributesApi.ts`: `updateAttribute(systemId, request)`. T033 passes.
- [X] T040 [US2] `FE/attributes/AttributeEditor.tsx`: edit mode (title "Editar atributo", initial form
  from `fromAttribute`, system ID read-only text, data type select disabled, submit through
  `updateAttribute`, button "Guardar cambios"); create mode unchanged. T034 passes.
- [X] T041 [US2] `FE/pages/AttributesPage.tsx`: "Editar" action in the detail card header (permission
  only), editor in the right column, list reload and confirmation after save, not-found handling.
  T035 passes.

**Checkpoint**: US1 and US2 work; feature 004's create tests still pass.

---

## Phase 5: User Story 3 – Delete an attribute (P2)

**Goal**: confirmed deletion; refused while in groups; list kept; ID never reused.

**Independent test**: spec US3; quickstart API 11–15, UI U8–U9.

### Tests first

- [X] T042 [P] [US3] Extend `BT/attribute/service/AttributeServiceTest.java` (delete): deletes an
  ungrouped attribute; in groups (`FakeAttributeGroups`) → `AttributeInGroupsException` naming them,
  nothing deleted; without permission → refused; unknown → not found; an LOV attribute's list is not
  touched (catalog fake unchanged); after deleting, creating a new attribute without ID never gets the
  deleted ID, and creating one with the deleted ID as manual ID is refused by feature 003.
- [X] T043 [P] [US3] New `BT/attribute/controller/AttributeDeletionIntegrationTest.java` (MockMvc, real
  MongoDB and services): 204 then `GET` 404; 403 `ATTRIBUTE_CHANGE_NOT_PERMITTED`; 404; 409
  `ATTRIBUTE_IN_GROUPS` with `detail` naming the groups ("1 grupo" / "2 grupos") and `groups` [{id, name}];
  after removing the memberships through `/api/v1/attribute-groups/{id}/attributes/{attributeId}`, 204;
  deleting the last LOV attribute of a list lets `DELETE /api/v1/lists-of-values/{id}` succeed (204);
  the list and its values are unchanged by the attribute deletion.
- [X] T044 [P] [US3] New `BT/attributegroup/service/AttributeDeletionRaceTest.java`: deleting an
  attribute while it is added to a group, repeated many times — never a group pointing at a missing
  attribute; every group detail still loads (spec FR-017, SC-005).
- [X] T045 [P] [US3] New storage-failure case in the existing attribute storage-failure test (or a new
  `AttributeChangeStorageFailureTest`) following the feature 005/006 pattern: `PUT` and `DELETE` answer
  503 when MongoDB is unreachable.
- [X] T046 [P] [US3] Extend `FE/api/attributesApi.test.ts`: `deleteAttribute(id)` sends `DELETE` with
  permission headers; 409 exposes `groups` from the problem body; 404 maps to not found.
- [X] T047 [P] [US3] Extend `FE/pages/AttributesPage.test.tsx`: "Eliminar" (permission only) shows the
  inline warning naming the attribute and, for LOV attributes, "La lista de valores «L» se conserva.";
  "Cancelar" keeps it; confirming deletes, shows "Atributo «X» eliminado.", removes the row and closes
  the detail; with groups, the warning names them, says to remove it first and offers only
  "Cancelar"; a 409 from the backend (group added meanwhile) shows its message and reloads the detail.

### Implementation

- [X] T048 [US3] `BE/attribute/service/AttributeService.java`: `delete(String systemId,
  AttributePermissions)` per research R-05 (permission; `groupDirectory.runIfInNoGroup(id, () -> {
  synchronized (lock) { find; repository.delete } })`; non-empty result → `AttributeInGroupsException`).
  Never hold the attribute lock while calling the directory. T042 and T044 pass.
- [X] T049 [US3] `AttributeController` `@DeleteMapping("/{systemId}")` → 204;
  `AttributeExceptionHandler` handler for `AttributeInGroupsException` (409, field `groups`, extension
  property `groups` with `{id, name}` items via `ProblemDetail.setProperty`). T043 and T045 pass.
- [X] T050 [P] [US3] `FE/api/attributesApi.ts`: `deleteAttribute(systemId)`; the problem reader keeps
  `groups`. T046 passes.
- [X] T051 [US3] `FE/pages/AttributesPage.tsx`: "Eliminar" action and inline confirmation as in
  `ListsOfValuesPage` (`Notification variant="warning"`), texts of research R-10, list reload and
  confirmation after deletion. T047 passes.

**Checkpoint**: all three stories work.

---

## Phase 6: Polish & Cross-Cutting

- [X] T052 Merge `specs/007-attribute-management/contracts/attributes-v1.2.openapi.yaml` into
  `specs/004-attribute-creation/contracts/attributes.openapi.yaml` (version 1.2.0, description line,
  new paths, schemas, codes); keep 007's file as the record of the change.
- [X] T053 [P] Update `specs/004-attribute-creation/quickstart.md` U1 (creation now from "Atributos" →
  "Nuevo atributo") and `specs/006-lov-management/quickstart.md` U1 and U8 (menu "Atributos"), and any
  spec text that names "Crear Atributos" as the current menu entry (features 004, 006).
- [X] T054 Run the full suites: backend `.\gradlew.bat clean build`; frontend `npm run lint`,
  `npm run format:check`, `npm test`, `npm run build`. Record counts; every pre-existing test passes.
- [X] T055 Run quickstart API scenarios 1–16 against a running backend on a fresh database; record
  results.
- [X] T056 Scope check per quickstart "Scope checks": `git diff --stat` shows no change to features
  001–003, no new dependency, no permission code added; features 005 and 006 changes limited to the
  listed methods.
- [ ] T057 Owner check: quickstart UI scenarios U1–U11 in the browser (left to the project owner).

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → US1 → US2 → US3 → Polish.
- US2 and US3 both need US1's page and details (T022–T027); US3 does not need US2, so after US1 they may
  run in either order, but both edit `AttributeService.java`, `AttributeController.java` and
  `AttributesPage.tsx`, so their implementation tasks touching those files run one at a time.
- Within a phase: [P] tests first, then implementation in ID order.

### Parallel examples

- Phase 2 tests: T002, T003, T004, T005, T006 together.
- US1 tests: T016–T021 together; then T024 alongside T022/T023.
- US2 tests: T028–T035 together; T038 and T039 alongside T036/T037.
- US3 tests: T042–T047 together; T050 alongside T048/T049.

## Implementation Strategy

- **MVP**: Phases 1–3 (consult and search, creation from the new page).
- Then US2 (edit), then US3 (delete); each checkpoint leaves the application working.
- Stop at T057 for the owner's browser check; no commit until asked.

## Notes

- **Results (2026-11-11)**:
  - T001 baseline: backend build passed (427 tests); frontend 158 tests.
  - T054: backend `clean build` 464 tests, 0 failures; frontend 184 tests, lint, format check and
    build pass.
  - T055: quickstart API scenarios 1–15 pass (15/15) on a temporary MongoDB (port 27018) with the
    built jar (port 8089); scenario 16 passes after two restarts (an edit and its `updatedAt` kept,
    attributes without `updatedAt` read normally). The temporary processes were stopped.
  - T056: no change to features 001–003, `build.gradle`, `package.json`, the constitution or
    CLAUDE.md; no permission code added.
  - T057 (browser check of U1–U11) is left to the project owner.
- **Where the implementation differs from the task text** (same behavior):
  - T005: `namesById` is tested in `MongoListOfValuesRepositoryIntegrationTest` (the repository gained
    a projection, research R-03 note) and through the attribute integration tests.
  - T006: `AttributeGroupDirectoryAdapterTest` runs with the Spring context (the adapter resolves the
    group service at call time).
  - T016, T028, T042: in a new `AttributeServiceChangeTest` instead of extending `AttributeServiceTest`.
  - T017, T029, T043: in one new `AttributeChangeIntegrationTest`.
  - T030, T044: in one new `AttributeChangeRaceTest`.
  - T037: the `PUT` body uses feature 004's `AttributeCreationRequest` record (identical fields,
    research R-04 note); no separate DTO.
  - T045: new `AttributeChangeStorageFailureIntegrationTest`.
- **Existing assertions that had to follow the approved response change (D-7)**: feature 004's
  `ignoresAScopeAndGroupsAndAlwaysCreatesAGlobalAttribute` now expects `groups` to be an empty list
  instead of absent; feature 005's `addsExistingAttributes` compares the attribute JSON without the
  new `groups` field (FR-010 is about the attribute's own data).

- Feature 004's existing assertions must not change; only constructor arguments (T015) and the moved
  editor test's render and heading (T019) do.
- Messages are Spanish (es-MX) and come from the backend where they exist.
