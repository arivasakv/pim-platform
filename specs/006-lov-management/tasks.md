---

description: "Task list for feature 006: list of values management"
---

# Tasks: List of Values Management

**Input**: Design documents from `specs/006-lov-management/`

**Prerequisites**:
- [plan.md](./plan.md) (decisions D-1 to D-12 approved 2026-11-11)
- [spec.md](./spec.md) (approved 2026-11-11)
- [research.md](./research.md)
- [data-model.md](./data-model.md)
- [contracts/lists-of-values.openapi.yaml](./contracts/lists-of-values.openapi.yaml)
- [quickstart.md](./quickstart.md)
- constitution 2.4.0 (Principle IX: MongoDB for lists of values)

**Tests**: Included (constitution VI):
- unit tests for business logic;
- integration tests for every endpoint, with success and error paths;
- a test for every validation rule, with its HTTP status and error body.

Write each test first and confirm it fails (a compile failure counts).

**Organization**: Phase 2 builds the model, storage and shared plumbing. Then there's one phase per user
story (US1–US3: P1, US4: P2, US5: P3), each with its backend and frontend part.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story the task belongs to (US1–US5)
- Commands are **Windows PowerShell**. Gradle runs in `backend\`, npm in `frontend\`.
- Path abbreviations:
  - **`MAIN`** = `backend/src/main/java/com/pim/backend`
  - **`TEST`** = `backend/src/test/java/com/pim/backend`
  - **`FE`** = `frontend/src`

## Ground rules for every task

- **Domain `lov`** (D-1): layers `controller`, `service`, `repository`, `model`, `dto` (constitution
  XII). Nothing goes in the root package.
- **Feature 004 only through `AttributeService`** and its public model types (`AttributePermissions`,
  `Attribute`, `ListOfValuesProperties`, `SelectionMode`). Feature 003 only through
  `IdentifierService` (constitution III).
- **Feature 004 changes are limited to**:
  - the two `AttributeService` methods of T014;
  - moving `EmptyListOfValuesCatalog` to the test tree (T015);
  - the list select in its page (T026).
  Feature 004's existing test assertions stay unchanged. Features 002, 003 and 005 are not touched,
  except the shared test helper (T004).
- **MongoDB** (constitution 2.4.0, D-2): Spring Data MongoDB, same connection as the other domains.
  Documents and Spring Data repositories are **package-private in `lov.repository`** and mapped to model
  types. Collections and indexes exactly as in data-model.md "Collections". No new dependency.
- **Locks** (R-04, R-12): one lock in `ListOfValuesService` for every change. List deletion takes the
  lov lock, then calls `AttributeService.runIfNoAttributeLinksTo` (attribute lock). `ListOfValuesLookup`
  and `ListOfValuesCatalogAdapter` take **no** lock.
- **Errors**: RFC 9457 via `common.ApiProblems`, Spanish texts, and codes exactly as in the contract's
  `Problem.errors[].code` and `rowErrors[].code` enums. An unreachable MongoDB gives 503 via the
  existing `common.DataAccessExceptionHandler`.
- **Permission** (D-12): changes need `ATTRIBUTE_CREATE` (header `X-Pim-Permissions`). Reads need none.
- **Integration tests** use the local MongoDB `pim-test` and call the shared reset helper (T004) in
  `@BeforeEach`.
- **After each phase**:
  - backend: `.\gradlew.bat spotlessApply; .\gradlew.bat build`;
  - frontend: `npm run lint; npm run format:check; npm test; npm run build`.
  All must pass.

---

## Phase 1: Setup

- [X] T001 Confirm the baselines on branch `006-lov-management` with the local MongoDB running:
  `.\gradlew.bat clean build` passes, and the frontend checks pass. Record both test counts (expected:
  331 backend and 141 frontend tests after feature 005 and the ID-field change).
- [X] T002 [P] Add multipart limits to `backend/src/main/resources/application.yaml` under
  `spring.servlet.multipart`: `max-file-size: 5MB`, `max-request-size: 6MB`, with a comment citing
  research R-08 (D-9). *Done as 10MB / 11MB, with the 5 MB checked by the import endpoint (422);
  see research R-08 implementation note.*
- [X] T003 [P] Amend `docs/decisions/0003-mongodb-attribute-administration.md`. Add a dated note
  "Amended 2026-11-11 (constitution 2.4.0, feature 006)" and add the rows `lists_of_values` and
  `lov_changes` to its collections table, with the documents and indexes from
  `specs/006-lov-management/data-model.md` "Collections" (D-2, D-10).

---

## Phase 2: Foundational (blocking prerequisites)

### Shared test plumbing

- [X] T004 Extend `TEST/common/AttributeAdministrationTestData.java` so `reset` also empties the
  collections `lists_of_values` and `lov_changes`.

### Model

- [X] T005 [P] Write `TEST/lov/model/ListNameTest.java`:
  - trimmed;
  - 1–80 characters (80 OK, 81 → `InvalidListException` code `INVALID_LIST_NAME`);
  - blank → `INVALID_LIST_NAME`;
  - `null` → `REQUIRED`;
  - `comparisonKey` lower-cases with `Locale.ROOT`;
  - the case given is kept.
- [X] T006 [P] Write `TEST/lov/model/ValueIdTest.java`:
  - trimmed;
  - "1–40 characters of `A–Z a–z 0–9 - _ .`" (40 OK, 41, `a b`, `ñ`, `/` → `INVALID_VALUE_ID`);
  - `0001` keeps its leading zeros and case;
  - `key()` upper-cases with `Locale.ROOT`, so `abc` and `ABC` have the same key;
  - `null` or blank isn't accepted by the factory (generation is decided by the service).
- [X] T007 [P] Write `TEST/lov/model/ListOfValuesTest.java`:
  - **create** → no values, `nextGeneratedNumber` 1, `equivalencesEnabled` false.
  - **`withValueAdded`**:
    - a manual ID that exists ignoring case → `ValueIdInUseException`;
    - label "trimmed, 1–200 characters", required (`REQUIRED`, `INVALID_LABEL`);
    - equivalence "trimmed; up to 200 characters; blank = none" (`INVALID_EQUIVALENCE`).
  - **Generated IDs** (R-07):
    - `0001`, `0002`…, skipping an ID taken by hand (e.g. `0002` taken → `0003`);
    - the counter never goes back after a deletion;
    - padding to at least 4 digits (`9999` → `10000`).
  - **Other changes**:
    - `withValueUpdated` changes label and equivalence only;
    - `withValueRemoved` on an unknown ID → `ValueNotFoundException`;
    - value order = order added;
    - adding the 50,001st value → `ListTooLargeException`;
    - `renamed` and `withEquivalencesEnabled(false)` keep values and equivalences.
- [X] T008 Create the model in `MAIN/lov/model/`:
  - `ListName`, `ValueId`, `ListValue(ValueId id, String label, Optional<String> equivalence)`;
  - `ListOfValues` (record: `systemId`, `name`, `equivalencesEnabled`, `values`,
    `nextGeneratedNumber`, `createdAt`, `updatedAt`; immutable "with" methods; `MAX_VALUES = 50_000`);
  - `LovErrorCode` (codes of the contract enums with Spanish templates);
  - `LinkedAttribute(systemId, displayName, SelectionMode selectionMode)`;
  - `ImportOutcome(ListOfValues list, int added, int updated)`;
  - `ImportRowError(int row, String column, LovErrorCode code, String message)`;
  - `ChangeRecord`;
  - the exceptions `InvalidListException(field, code)`, `ListNotFoundException`,
    `ValueNotFoundException`, `ListNameInUseException`, `ValueIdInUseException`, `ListInUseException`
    (list name, attribute count), `ListTooLargeException`,
    `InvalidImportException(List<ImportRowError>)`, `InvalidImportFileException(code)` (codes
    `INVALID_IMPORT_FILE`, `IMPORT_TOO_LARGE`), `ListChangeNotPermittedException`.

  T005–T007 pass.

### Storage

- [X] T009 [P] Write `TEST/lov/repository/MongoListOfValuesRepositoryIntegrationTest.java` (reset in
  `@BeforeEach`):
  - insert and find by ID ignoring case;
  - `findAll` sorted by name ignoring case;
  - `existsWithName(name, excludedId)` ignoring case;
  - `update` replaces the whole document (values, counter, flags);
  - `delete`;
  - a second insert with the same name key → `IllegalStateException` (unique index
    `lov_name_unique`);
  - the round trip keeps value order, the case of value IDs, and missing equivalences.
- [X] T010 Create `MAIN/lov/repository/ListOfValuesRepository.java`, an interface with `findById`,
  `findAll`, `existsWithName`, `insert`, `update`, `delete`.
- [X] T011 Create, in `MAIN/lov/repository/`:
  - `ListOfValuesDocument` (package-private record, `@Document("lists_of_values")`: `_id` = system ID
    upper-cased, `systemId`, `name`, `nameKey`, `equivalencesEnabled`, `nextGeneratedNumber`,
    `values: List<ValueDocument(id, idKey, label, equivalence)>`, `createdAt`, `updatedAt`);
  - `MongoListOfValuesRepository` (`MongoTemplate`; unique index `lov_name_unique` on `nameKey`
    created in `afterPropertiesSet`; `DuplicateKeyException` → `IllegalStateException`).

  T009 passes.
- [X] T012 [P] Write `TEST/lov/repository/MongoChangeRecordRepositoryIntegrationTest.java`: insert a
  record and read it back in the test with `MongoTemplate`, including all fields from data-model
  "ChangeRecord" and the index `{ listId: 1, at: -1 }`.
- [X] T013 Create, in `MAIN/lov/repository/`:
  - `ChangeRecordRepository` (interface, `insert`);
  - `ChangeRecordDocument` (`@Document("lov_changes")`);
  - `MongoChangeRecordRepository`, which creates the index.

  T012 passes.

### Feature 004 additions (D-3, D-4)

- [X] T014 Add to `MAIN/attribute/service/AttributeService.java`, under its existing lock:
  - `List<Attribute> findLinkedToList(String listOfValuesId)`: LOV attributes whose
    `ListOfValuesProperties.listOfValuesId` equals the argument ignoring case, sorted by display name
    ignoring case;
  - `boolean runIfNoAttributeLinksTo(String listOfValuesId, Runnable action)`: runs `action` and
    returns `true` only if no attribute links to the list; otherwise returns `false` without running
    it. The action runs while the attribute lock is held.

  Add tests for both methods in `TEST/attribute/service/AttributeServiceTest.java` (new test methods
  only; existing assertions unchanged). *Also done (found by T036): `create` now runs its list check
  inside its lock, first step under it, so the race of research R-04 is closed; order of checks and
  responses unchanged.*
- [X] T015 Move the placeholder catalog to the test tree:
  `git mv MAIN/attribute/repository/EmptyListOfValuesCatalog.java
  TEST/attribute/repository/EmptyListOfValuesCatalog.java`, remove its `@Repository`, and update its
  Javadoc to "test fake". The existing tests that use it keep compiling (same package). The production
  build needs T019 in the same commit, so do T015 together with T019.

### Request plumbing

- [X] T016 [P] Create `MAIN/lov/controller/LovRequestReader.java`:
  - permissions from `X-Pim-Permissions` via `AttributePermissions.fromCodes`;
  - manual-ID permissions from `X-Pim-Manual-Id-Permissions` via `ManualIdPermissions.fromCodes`;
  - the optional user from `X-Pim-User` (trimmed, at most 100 characters, blank = `null`).
- [X] T017 Create `MAIN/lov/controller/LovExceptionHandler.java` (`@RestControllerAdvice(basePackageClasses
  = ...)`, highest precedence, no catch-all). Map exceptions to problem bodies with the contract's
  titles:
  - 400: `InvalidListException`, bean validation;
  - 403: `ListChangeNotPermittedException` ("Sin permiso para administrar listas de valores");
  - 404: `ListNotFoundException` ("Lista no encontrada"), `ValueNotFoundException` ("Valor no
    encontrado");
  - 409: `ListNameInUseException` ("Nombre en uso"), `ValueIdInUseException` ("ID en uso"),
    `ListInUseException` ("Lista en uso");
  - 422: `ListTooLargeException`, `InvalidImportException` ("Archivo con errores", with `rowErrors`),
    `InvalidImportFileException`.

  Feature 003's `ManualIdNotPermittedException` (403), `SystemIdInUseException` (409) and
  `InvalidIdentifierRequestException` (400) map as feature 004's handler maps them.

**Checkpoint**: model, storage and plumbing are ready. The build passes once T015 and T019 land
together.

---

## Phase 3: User Story 1 - Create a list and capture its values manually (Priority: P1) 🎯 MVP

**Goal**: create lists, add values one by one, and let feature 004 link LOV attributes to real lists.
**Independent Test**: quickstart API 1–7; UI U2, U3, U8.

- [X] T018 [P] [US1] Write `TEST/lov/service/ListOfValuesServiceTest.java` (US1 part). Use real
  `IdentifierService` and `AttributeService` on the in-memory test fakes, an in-memory
  `ListOfValuesRepository` fake in the test, and a fixed `Clock`.
  - **Create list**:
    - without `ATTRIBUTE_CREATE` → `ListChangeNotPermittedException`;
    - with it → system ID `LOV000001` from feature 003 (type `LIST_OF_VALUES`);
    - a manual `systemId` without feature 003's `LIST_OF_VALUES` permission →
      `ManualIdNotPermittedException`;
    - a duplicate name ignoring case → `ListNameInUseException`.
  - **Add value**: manual ID; generated ID; existing ID ignoring case → `ValueIdInUseException`;
    unknown list → `ListNotFoundException`.
- [X] T019 [US1] Create, in `MAIN/lov/service/`:
  - `ListOfValuesLookup` (`@Service`; `exists(id)` ignoring case, `hasAny()`; repository only, no
    lock);
  - `ListOfValuesCatalogAdapter` (`@Component`, implements
    `com.pim.backend.attribute.repository.ListOfValuesCatalog` by delegating to `ListOfValuesLookup`).

  Do this together with T015.
- [X] T020 [US1] Create `MAIN/lov/service/ListOfValuesService.java`: `@Service`; inject
  `ListOfValuesRepository`, `IdentifierService`, `AttributeService`, `Clock`; one lock.
  `ChangeRecorder` is added to the constructor in T037. Methods:
  - `create(name, requestedSystemId, AttributePermissions, ManualIdPermissions)`: check the name, then
    call `identifierService.assign(ObjectType.LIST_OF_VALUES, requestedSystemId, Map.of(), …)`, then
    insert;
  - `addValue(listId, id, label, equivalence, permissions)`.

  T018 passes.
- [X] T021 [P] [US1] Create DTOs in `MAIN/lov/dto/`, sizes as in the contract:
  - `CreateListOfValuesRequest` (`@NotNull @Size(max = 200) name`, `@Size(max = 40) systemId`);
  - `AddValueRequest` (`@Size(max = 100) id`, `@NotNull @Size(max = 500) label`,
    `@Size(max = 500) equivalence`);
  - `ListValueResponse` (`id`, `label`, `equivalence`);
  - `LinkedAttributeResponse`;
  - `ListOfValuesResponse` (`systemId`, `name`, `equivalencesEnabled`, `valueCount`, `values`,
    `attributes`, `createdAt`).
- [X] T022 [P] [US1] Write `TEST/lov/controller/ListOfValuesControllerIntegrationTest.java` (US1 part):
  - `POST /api/v1/lists-of-values` `{"name":"Color"}` → 201, `Location`, `systemId` `LOV000001`,
    `valueCount` 0;
  - `" color "` → 409 `LIST_NAME_IN_USE`;
  - blank or 81 characters → 400 `INVALID_LIST_NAME`;
  - missing name → 400 `REQUIRED`;
  - no header → 403 `ATTRIBUTE_CREATE_NOT_PERMITTED`;
  - manual `systemId` without `X-Pim-Manual-Id-Permissions: LIST_OF_VALUES` → 403
    `MANUAL_ID_NOT_PERMITTED`;
  - `POST …/values` `{"id":"0001","label":"Azul"}` → 201 with `Location` `…/values/0001`;
  - `{"label":"Rojo"}` → 201 `id` `0002`;
  - `{"id":"0001",…}` → 409 `VALUE_ID_IN_USE`;
  - `{"id":"a b",…}` → 400 `INVALID_VALUE_ID`;
  - no label → 400 `REQUIRED`;
  - 201-character label → 400 `INVALID_LABEL`;
  - unknown list → 404 `LIST_NOT_FOUND`;
  - the same ID `0001` in a second list → 201.

  Storage failure: in a separate class `TEST/lov/controller/ListOfValuesStorageFailureIntegrationTest.java`,
  `@MockitoBean ListOfValuesRepository` throwing `DataAccessResourceFailureException` → `POST` and `GET`
  answer 503 with the existing problem body.
- [X] T023 [P] [US1] Write `TEST/lov/controller/AttributeLinkToListIntegrationTest.java` (FR-026, SC-006):
  - create list "Color";
  - `POST /api/v1/attributes` LOV `MULTIPLE` with `listOfValuesId` `lov000001` → 201;
  - `LOV999999` → 422 `LIST_OF_VALUES_NOT_FOUND`;
  - with no lists at all → 422 `LIST_OF_VALUES_NOT_AVAILABLE` (feature 004 behaviour kept).
- [X] T024 [US1] Create `MAIN/lov/controller/ListOfValuesController.java` (`/api/v1/lists-of-values`:
  `POST`) and `MAIN/lov/controller/ListValueController.java` (`POST …/{listId}/values`, 201 + `Location`).
  T022 and T023 pass.
- [X] T025 [P] [US1] Write `FE/api/listsOfValuesApi.test.ts` (mocked `fetch`, `vi.stubEnv`):
  - `listLists()`, `getList()`, `createList()`, `addValue()`, each with the header
    `X-Pim-Permissions` from `VITE_DEV_PERMISSIONS`;
  - 400/403/404/409/422 → `ListOfValuesApiError` with `kind` (validation, forbidden, notFound,
    conflict, unprocessable), `detail`, `errors`, `rowErrors`;
  - network failure → `unavailable`.
- [X] T026 [P] [US1] Write feature 004 frontend tests (new cases only) in
  `FE/pages/AttributeCreationPage.test.tsx` and `FE/attributes/attributeForm.test.ts`:
  - for type "Lista de valores", a select "Lista de valores" offers the lists from
    `GET /api/v1/lists-of-values` (name and system ID);
  - the request contains `listOfValuesProperties.listOfValuesId`;
  - with no lists, the existing message "Aún no existen listas de valores. Podrás crear este atributo
    cuando exista una lista." is shown, and no select.
- [X] T027 [US1] Implement `FE/api/listsOfValuesApi.ts` (types from the contract; reuse
  `permissionHeaders` and `readProblem` from `FE/api/attributesApi.ts`). T025 passes.
- [X] T028 [US1] Implement the list select in `FE/pages/AttributeCreationPage.tsx` and
  `FE/attributes/attributeForm.ts`:
  - add `listOfValuesId` to the form;
  - load the lists when the type is "Lista de valores";
  - show the info message only when there are none.

  T026 passes.
- [X] T029 [P] [US1] Write `FE/pages/ListsOfValuesPage.test.tsx` (US1), against an in-memory fake
  backend as in `AttributeGroupsPage.test.tsx`:
  - loading, then the empty state "Todavía no hay listas de valores.";
  - with `ATTRIBUTE_CREATE`: a create form ("Nombre", "Crear lista"); without it, no form;
  - creating "Color" lists it and shows "Lista «Color» creada.";
  - selecting it shows the add-value form ("ID (opcional)", "Etiqueta", "Agregar valor"); adding
    `0001` "Azul" and one without ID lists both;
  - a 409 shows the message next to the field;
  - backend unreachable → connection error with "Reintentar".
- [X] T030 [US1] Implement `FE/pages/ListsOfValuesPage.tsx` and `.module.css`:
  - master–detail like `AttributeGroupsPage`, design-system components only;
  - change controls only with the `ATTRIBUTE_CREATE` development permission;
  - add `{ path: '/listas-de-valores', label: 'Listas de valores', icon: 'components' }` to
    `FE/navigation.ts` right after "Grupos de atributos";
  - add the route in `FE/App.tsx` and one case in `FE/navigation.test.ts` (entry order).

  T029 passes.

**Checkpoint**: lists and values can be created, and feature 004 saves LOV attributes linked to them.

---

## Phase 4: User Story 2 - Consult lists, their values and their attributes (Priority: P1)

**Goal**: list collection and detail with linked attributes. **Independent Test**: quickstart API 1, 8;
UI U2.

- [X] T031 [P] [US2] Extend the tests:
  - `TEST/lov/service/ListOfValuesServiceTest.java` (US2): `findAll` sorted by name with value and
    attribute counts; `find(listId)` ignoring case, with `LinkedAttribute`s from
    `AttributeService.findLinkedToList` (one `SINGLE`, one `MULTIPLE`); unknown → `ListNotFoundException`.
  - `TEST/lov/controller/ListOfValuesControllerIntegrationTest.java` (US2):
    - `GET /api/v1/lists-of-values` → 200 `items` with `systemId`, `name`, `valueCount`,
      `attributeCount`, plus `total`; empty → `total` 0;
    - `GET /api/v1/lists-of-values/lov000001` → values in the order added, and `attributes` sorted by
      display name with `selectionMode`;
    - unknown → 404 `LIST_NOT_FOUND`;
    - reading needs no header.
- [X] T032 [US2] Implement:
  - `findAll()` and `find(listId)` in `ListOfValuesService`;
  - `ListOfValuesSummaryResponse` and `ListOfValuesCollectionResponse` in `MAIN/lov/dto/`;
  - `GET` and `GET /{listId}` in `ListOfValuesController`.

  Attribute counts for the collection come from one `AttributeService.findAll()` call grouped by list
  ID, not one call per list. T031 passes.
- [X] T033 [P] [US2] Extend `FE/pages/ListsOfValuesPage.test.tsx` (US2) and implement it in
  `FE/pages/ListsOfValuesPage.tsx`:
  - each list item shows its name, system ID, "N valores" and "N atributos";
  - the detail shows the values table (ID, Etiqueta) and "Atributos que la usan" (display name, system
    ID, "Selección única" / "Selección múltiple");
  - with more than 500 values, only 500 rows show at a time, with a filter box ("Buscar por ID o
    etiqueta") and "Mostrar más" (R-14);
  - everything is visible without the permission.

---

## Phase 5: User Story 3 - Edit and delete values, rename and delete lists (Priority: P1)

**Goal**: maintenance, with safe deletion and change records. **Independent Test**: quickstart API 9–11,
17, 18; UI U4, U5, U9.

- [X] T034 [P] [US3] Write `TEST/lov/service/ChangeRecorderTest.java`:
  - records `VALUE_DELETED` and `LIST_DELETED` with `at` from the clock, `user` (or `null`), `listId`,
    `listName`, `valueIds`;
  - a repository failure is logged and not rethrown (R-11).

  Then create `MAIN/lov/service/ChangeRecorder.java`.
- [X] T035 [P] [US3] Extend `TEST/lov/service/ListOfValuesServiceTest.java` (US3):
  - **Values**:
    - `updateValue` changes only label and equivalence, and the ID is kept;
    - `deleteValue` removes it and records `VALUE_DELETED`; unknown → `ValueNotFoundException`.
  - **Lists**:
    - `rename` keeps `systemId`, values and links; a duplicate name → `ListNameInUseException`;
    - `delete` with a linked attribute → `ListInUseException` (attribute count), with nothing deleted
      and nothing recorded;
    - `delete` with no links → gone and `LIST_DELETED` recorded.
  - Every change without the permission → `ListChangeNotPermittedException`.
- [X] T036 [P] [US3] Write `TEST/lov/service/ListDeletionRaceTest.java` (R-04, SC-005). Use real
  services on MongoDB `pim-test`, and repeat 50 times: in parallel, delete list L and create an LOV
  attribute linked to L. Afterwards, either the list exists and the attribute links to it, or the list
  is gone and the attribute creation failed with `LIST_OF_VALUES_NOT_FOUND`. Never an attribute linked
  to a missing list. No deadlock (timeout 30 s).
- [X] T037 [US3] Inject `ChangeRecorder` into `ListOfValuesService` and implement:
  - `updateValue`, `deleteValue(listId, valueId, user, permissions)`, `update(listId, name,
    equivalencesEnabled, permissions)` (rename part);
  - `delete(listId, user, permissions)`: lov lock → `attributeService.runIfNoAttributeLinksTo(listId,
    () -> repository.delete(listId))` → if `false`, throw `ListInUseException` with the count from
    `findLinkedToList` → record.

  T035 and T036 pass.
- [X] T038 [P] [US3] Extend `TEST/lov/controller/ListOfValuesControllerIntegrationTest.java` (US3):
  - **Values**:
    - `PUT …/values/0001` `{"label":"AZUL"}` → 200 with `id` `0001`; unknown value → 404
      `VALUE_NOT_FOUND`;
    - `DELETE …/values/0002` → 204, and again → 404;
    - with `X-Pim-User: ana`, the `lov_changes` record has `user` "ana".
  - **Lists**:
    - `PUT /api/v1/lists-of-values/{id}` `{"name":"Colores","equivalencesEnabled":false}` → 200;
      a duplicate name → 409;
    - `DELETE` of a list linked to 2 attributes → 409 `LIST_IN_USE` with the message "La lista
      "Color" está vinculada a 2 atributos y no se puede eliminar.";
    - `DELETE` of an unlinked list → 204, then `GET` → 404.
  - All change operations without the header → 403.
- [X] T039 [US3] Create `UpdateListOfValuesRequest` and `UpdateValueRequest` in `MAIN/lov/dto/`, and
  add `PUT`/`DELETE /{listId}` to `ListOfValuesController` and `PUT`/`DELETE …/values/{valueId}` to
  `ListValueController`. T038 passes.
- [X] T040 [P] [US3] Extend `FE/pages/ListsOfValuesPage.test.tsx` (US3) and implement it in
  `FE/pages/ListsOfValuesPage.tsx`, adding `updateList`, `deleteList`, `updateValue`, `deleteValue` to
  `FE/api/listsOfValuesApi.ts` with tests in `FE/api/listsOfValuesApi.test.ts`:
  - "Editar" per value: label editable, ID read-only;
  - "Eliminar" per value opens a confirmation ("¿Eliminar el valor «Azul» (0001)?"); cancelling keeps
    it, confirming removes it;
  - "Editar lista" renames;
  - "Eliminar lista" with confirmation; a 409 `LIST_IN_USE` shows its Spanish message;
  - "Lista «Color» eliminada." after deleting.

---

## Phase 6: User Story 4 - Manage values in bulk (Priority: P2)

**Goal**: CSV template and all-or-nothing import. **Independent Test**: quickstart API 13–16; UI U6.

- [X] T041 [P] [US4] Write `TEST/lov/service/CsvValuesParserTest.java` (R-09):
  - **Encoding**: UTF-8 with and without BOM.
  - **Delimiter**: `,` and `;` detected from the header.
  - **Header**:
    - `ID`, `Etiqueta`, optional `Equivalencia` in any order, ignoring case, accents and spaces
      (`etiqueta`, ` Etiqueta `, `ETIQUETA`);
    - an unknown column or no `Etiqueta` → `InvalidImportFileException(INVALID_IMPORT_FILE)`.
  - **Quoting**: RFC 4180 (`"a, b"`, `"say ""hi"""`).
  - **Empty lines** are ignored.
  - **Row numbers**: the header is row 1.
  - **Size**: more than 50,000 data rows → `IMPORT_TOO_LARGE`.
  - **Bad input**: invalid UTF-8 or an unterminated quote → `INVALID_IMPORT_FILE`.
- [X] T042 [US4] Create `MAIN/lov/service/CsvValuesParser.java` (no library; returns rows
  `(rowNumber, id, label, equivalence)` with raw cell text). T041 passes.
- [X] T043 [P] [US4] Write `TEST/lov/service/ValuesImportTest.java` (FR-011, FR-016, OQ-005):
  - empty `ID` → generated (`0001`… continuing the list counter);
  - an existing `ID` ignoring case → label updated, equivalence replaced only when the cell isn't
    empty;
  - a new `ID` → appended in file order;
  - **all** errors collected, with nothing returned to save:
    - empty `Etiqueta` → `REQUIRED`;
    - `ID` twice in the file → `DUPLICATE_ID_IN_FILE` on both rows;
    - invalid `ID` → `INVALID_VALUE_ID`;
    - 201-character label → `INVALID_LABEL`;
    - 201-character equivalence → `INVALID_EQUIVALENCE`;
  - an existing `ID` with an empty `Etiqueta` → `REQUIRED` (not a deletion);
  - a result over 50,000 values → `ListTooLargeException`;
  - counts `added` / `updated`.
- [X] T044 [US4] Create `MAIN/lov/service/ValuesImport.java` (pure: list + rows → `ImportOutcome` or
  `InvalidImportException`), and add `ListOfValuesService.importValues(listId, InputStream, user,
  permissions)` (under the lock: parse → apply → one `repository.update` → record `VALUES_IMPORTED`
  with `added`, `updated`). T043 passes.
- [X] T045 [P] [US4] Write `TEST/lov/controller/ListOfValuesImportIntegrationTest.java`
  (`MockMultipartFile`):
  - `GET /api/v1/lists-of-values/import-template` → 200 `text/csv`, BOM plus
    `ID,Etiqueta,Equivalencia`, `Content-Disposition` attachment `plantilla-valores.csv`;
  - a 500-row file → 200 `added` 500, `updated` 0, `total` 500;
  - the semicolon update file → `added` 1, `updated` 1;
  - a file with errors → 422 `INVALID_IMPORT` with `rowErrors` (row, column, code, message) and the
    list unchanged (`GET` compares equal);
  - no `Etiqueta` column → 422 `INVALID_IMPORT_FILE`;
  - a file over 5 MB → 422 `IMPORT_TOO_LARGE`, or Spring's multipart error mapped to it;
  - unknown list → 404;
  - no header → 403;
  - 10,000 rows applied in under 5 s (SC-003, local).
- [X] T046 [US4] Create `ImportResultResponse` in `MAIN/lov/dto/` and
  `MAIN/lov/controller/ListOfValuesImportController.java`:
  - `GET /api/v1/lists-of-values/import-template` (UTF-8 BOM + header line, `text/csv`);
  - `POST /api/v1/lists-of-values/{listId}/values/import` (multipart `file`).

  Map `MaxUploadSizeExceededException` to 422 `IMPORT_TOO_LARGE` in `LovExceptionHandler`. T045 passes.
- [X] T047 [P] [US4] Extend `FE/pages/ListsOfValuesPage.test.tsx` (US4) and implement it in
  `FE/pages/ListsOfValuesPage.tsx`, adding `downloadTemplateUrl` / `importValues(file)` to
  `FE/api/listsOfValuesApi.ts` with tests:
  - a "Descargar plantilla" link to the template endpoint;
  - "Cargar archivo CSV" with a file input;
  - success → "Se agregaron N valores y se actualizaron M.";
  - 422 `INVALID_IMPORT` → a table "Fila / Columna / Error" with every row error and the message
    "No se aplicó ningún valor.";
  - `INVALID_IMPORT_FILE` / `IMPORT_TOO_LARGE` → their Spanish detail;
  - controls only with the permission.

---

## Phase 7: User Story 5 - Define equivalences for values (Priority: P3)

**Goal**: the equivalence column, on and off. **Independent Test**: quickstart API 12; UI U7.

- [X] T048 [P] [US5] Extend the service and controller tests:
  - `PUT /{listId}` `equivalencesEnabled` true, then false → the flag changes and the equivalences are
    kept (still in `values[].equivalence`);
  - `PUT …/values/{id}` `{"label":"AZUL","equivalence":"BLUE"}` → saved;
  - `"equivalence":"  "` → removed;
  - two values with "NEGRO" → both saved;
  - 201 characters → 400 `INVALID_EQUIVALENCE`.

  Add the cases to `TEST/lov/service/ListOfValuesServiceTest.java` and
  `TEST/lov/controller/ListOfValuesControllerIntegrationTest.java`. Then make the toggle part of
  `ListOfValuesService.update` pass.
- [X] T049 [P] [US5] Extend `FE/pages/ListsOfValuesPage.test.tsx` (US5) and implement it in
  `FE/pages/ListsOfValuesPage.tsx`:
  - a "Usar columna de equivalencias" switch or checkbox (with permission);
  - when on, the table shows "Equivalencia", and the add/edit forms have an "Equivalencia (opcional)"
    field;
  - turning it off hides the column, and turning it on again shows the saved texts.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T050 [P] Write `TEST/lov/service/ListOfValuesServiceConcurrencyTest.java`:
  - 20 simultaneous creations with the same name → one list;
  - 50 simultaneous additions with generated IDs to one list → 50 distinct IDs `0001`–`0050`;
  - 20 simultaneous manual additions of `0001` → one value.
- [X] T051 [P] Write `TEST/lov/controller/ListOfValuesCorsIntegrationTest.java`: a preflight from
  `http://localhost:5173` for `PUT` and `DELETE` on `/api/v1/lists-of-values/x`, and a multipart `POST`
  on `…/values/import`, are allowed.
- [X] T052 Compare `specs/006-lov-management/contracts/lists-of-values.openapi.yaml` with the
  controllers, DTOs and handler (paths, statuses, field names, error codes, titles). Fix the code or
  report.
- [X] T053 Full builds: backend `spotlessApply` + `clean build`; frontend lint, format, test, build.
  Report counts per class / file.
- [X] T054 Run quickstart API scenarios 1–19 against a running backend on a temporary MongoDB (another
  port), including scenario 19 (restart: lists remain; the next list gets the next unused ID). Record
  results, then stop the temporary processes.
- [X] T055 Owner check: UI scenarios U1–U10 in the browser.
- [X] T056 Scope check:
  - `git diff --stat main…` shows no change to features 002, 003 or 005 code;
  - feature 004 changes are limited to T014, T015, T026 and T028;
  - no new dependency in `backend/build.gradle` or `frontend/package.json`;
  - no product data read or written;
  - `EmptyListOfValuesCatalog` exists only under `TEST`.

---

## Dependencies & Execution Order

- **Setup (1)** → **Foundational (2)** → US1 (3) → US2 (4) → US3 (5) → US4 (6) → US5 (7) → **Polish (8)**.
- **Inside Phase 2**: T004 before every integration test. Model (T005–T008) before storage (T009–T013).
  T014 can run alongside the model. T015 waits for T019, and lands together with it.
- **US1**: T019 and T020 depend on T008, T011 and T014. `ChangeRecorder` (T034) joins
  `ListOfValuesService` in T037.
- **US2–US5** extend the service, controllers, handler, page and their tests created in US1: run them
  in order, and never two tasks on the same file at once. US4's parser and import (T041–T044) are pure
  and can start any time after T008.
- The frontend tasks of each story need that story's backend endpoints only for manual checks; their
  tests use mocks.

### Parallel opportunities

- Setup: T002, T003.
- Phase 2: T005, T006, T007, T009, T012, T016.
- US1: T018, T021, T022, T023, T025, T026, T029.
- US2: T031, T033.
- US3: T034, T035, T036, T038, T040.
- US4: T041, T043, T045, T047.
- US5: T048, T049.
- Polish: T050, T051.

### Parallel example: User Story 1

```text
Together: T018 (service test), T021 (DTOs), T022 (controller test), T023 (feature 004 link test),
          T025 (API client test), T026 (feature 004 page tests), T029 (page test)
Then:     T019 + T015 → T020 → T024; T027 → T028; T030
```

## Requirement coverage

| Requirement | Tasks |
|---|---|
| FR-001 (create, rename, system ID) | T018, T020, T022, T024, T035, T038 |
| FR-002 (permission) | T016–T018, T022, T029, T035, T038, T045, T047 |
| FR-003 (unique name) | T005, T009, T011, T018, T022, T050 |
| FR-004 (delete only unlinked) | T014, T035–T038, T040 |
| FR-005, FR-014 (selection mode, linked attributes) | T014, T031–T033 |
| FR-006–FR-009 (pairs, format, uniqueness, generated IDs) | T006, T007, T018, T022, T050 |
| FR-010 (manual edit; ID immutable) | T007, T035, T038, T040 |
| FR-011, FR-016 (bulk CSV, all or nothing) | T002, T041–T047 |
| FR-012 (label change, identified by ID) | T007, T035, T038 |
| FR-013, FR-017 (delete value with confirmation) | T035, T038, T040 |
| FR-015, FR-018, FR-020–FR-022, FR-024 | deferred to FU-1/FU-2/FU-3; nothing to build (T056 checks) |
| FR-019 (change records) | T012, T013, T034, T038, T044, T054 |
| FR-023 (equivalences) | T007, T048, T049 |
| FR-025 (menu entry, no frontend data) | T030, T055 |
| FR-026 (feature 004 integration) | T015, T019, T023, T026, T028 |
| D-2 (MongoDB, embedded values, cap) | T003, T009, T011, T007, T054 |
| SC-001, SC-006, SC-007 | T055 |
| SC-002 | T007, T022, T043, T045, T050 |
| SC-003 | T045 |
| SC-004 | T038 |
| SC-005 | T036, T038 |

## Implementation Strategy

1. **Phase 2 first**: model and storage tested; the feature 004 service additions in place.
2. **MVP = US1**: create lists and add values; feature 004 can save LOV attributes. Stop and check:
   quickstart 1–7 and U8.
3. **+ US2, US3** (all P1): consult, edit, delete, with safe list deletion.
4. **+ US4**: CSV bulk load.
5. **+ US5**: equivalences.
6. **Polish**: concurrency, CORS, contract, builds, live run with restart, owner UI check, scope check.

Before merge: CF-001 and CF-005 accepted or resolved (Quality Gate 8).
