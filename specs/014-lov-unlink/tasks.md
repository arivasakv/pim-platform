---
description: "Task list for feature 014 — Unlink a List of Values from an Attribute"
---

# Tasks: Unlink a List of Values from an Attribute (Desvincular LOV de un atributo)

**Input**: Design documents from `specs/014-lov-unlink/` — [spec.md](spec.md), [plan.md](plan.md) (approved
2026-11-23, decisions D-1 to D-5), [research.md](research.md), [data-model.md](data-model.md),
[contracts/list-unlink.openapi.yaml](contracts/list-unlink.openapi.yaml),
[contracts/list-unlink-ui.md](contracts/list-unlink-ui.md), [quickstart.md](quickstart.md)

**Tests**: Required by constitution VI. Test tasks come before the code they cover.

**Organization**: By user story. US1 (P1) is the unlink itself from the attribute's edit form; US2 (P2)
adds the impact review with filter deletion shown, and the entry point from the list.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: US1, US2 from spec.md
- Paths: backend `backend/src/main/java/com/pim/backend/...` (tests under `backend/src/test/java/...`),
  frontend `frontend/src/...`

---

## Phase 1: Setup

**Purpose**: Record the amendment the feature relies on.

- [X] T001 Add the dated amendment to `specs/007-attribute-management/spec.md` FR-010 (the data type may change only through feature 014's unlink, list of values → another type) and dated notes to `specs/006-lov-management/spec.md` FR-015 (delivered by feature 014; FU-1 label propagation and deletion impact count still deferred, owner decision 2026-11-23) and `specs/012-taxonomy-list-value-filters/spec.md` (an attribute's filters are deleted when its list is unlinked)

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Model method, error codes and the taxonomy side that both stories use.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Write tests in `backend/src/test/java/com/pim/backend/attribute/model/AttributeTest.java` for `Attribute.withDataType(dataType, properties, at)`: type and properties replaced, `updatedAt` set, system ID, display name, scope and creation date kept; properties that do not match the type refused (R-05)
- [X] T003 Implement `Attribute.withDataType` in `backend/src/main/java/com/pim/backend/attribute/model/Attribute.java` (T002 green)
- [X] T004 [P] Add `ATTRIBUTE_NOT_LIST_OF_VALUES`, `UNLINK_TO_LIST_NOT_ALLOWED`, `UNLINK_OUTDATED` («El atributo o sus filtros cambiaron desde la revisión. Vuelve a revisar la desvinculación.») and `REVIEW_TOKEN_REQUIRED` to `backend/src/main/java/com/pim/backend/attribute/model/AttributeErrorCode.java`, with exceptions `AttributeNotListOfValuesException` and `UnlinkOutdatedException` and the record `ListUnlinkReview` (attribute ID and name, list ID and name, filtered nodes, token) in `backend/src/main/java/com/pim/backend/attribute/model/` (data-model "Error codes", "Unlink review")
- [X] T005 [P] Write tests for `findByAttribute` and `deleteByAttribute` (ignoring letter case; other attributes' filters kept) in the in-memory and MongoDB value-filter repository tests under `backend/src/test/java/com/pim/backend/taxonomy/repository/`, then add both methods to `ValueFilterRepository.java` and `MongoValueFilterRepository.java` in `backend/src/main/java/com/pim/backend/taxonomy/repository/` and to the in-memory test repository
- [X] T006 Write tests in `backend/src/test/java/com/pim/backend/taxonomy/service/TaxonomyAssignmentDirectoryAdapterTest.java` for the new port methods, then add to `backend/src/main/java/com/pim/backend/attribute/repository/TaxonomyAssignmentDirectory.java` `nodesFilteringValuesOf(attributeId)` (node references sorted by name) and `runRemovingValueFilters(attributeId, action)` (under the taxonomy lock: run the action, then delete the attribute's filters), implemented in `TaxonomyService.java` and `TaxonomyAssignmentDirectoryAdapter.java` under `backend/src/main/java/com/pim/backend/taxonomy/service/`; add the two methods to the attribute tests' `NoTaxonomyAssignments` double (R-04)

**Checkpoint**: The attribute can change type in the model, and the taxonomy can list and delete an
attribute's filters under its lock.

---

## Phase 3: User Story 1 — Unlink a list and give the attribute a new data type (Priority: P1) 🎯 MVP

**Goal**: From the attribute's edit form, detach the list, choose a new type and properties, confirm;
system ID, name, groups and taxonomy assignments stay; filters are deleted (FR-001–FR-008, FR-010–FR-013).

**Independent Test**: quickstart scenarios 1–3, 6, 7, 9, 10.

### Tests for User Story 1 ⚠️

- [X] T007 [P] [US1] Write `backend/src/test/java/com/pim/backend/attribute/service/AttributeServiceUnlinkTest.java` with in-memory repositories and a recording taxonomy double: review returns the list ID/name, filtered nodes and a 64-character token, and changes nothing; unlink with the token changes type and properties, keeps ID, name, scope, creation date and groups, updates `updatedAt`, removes the attribute from `findLinkedToList` (so the list becomes deletable), and asks the taxonomy to delete the attribute's filters; a stale or different token → `UnlinkOutdatedException`, nothing changes; non-list attribute → `AttributeNotListOfValuesException`; new type `LIST_OF_VALUES` → refused; invalid new properties → feature 004's `InvalidAttributeException`; no permission → `AttributeChangeNotPermittedException`; unknown attribute → `AttributeNotFoundException`; a failing filter deletion → the previous definition is restored and the error passed on; feature 007's `update` still refuses a type change (FR-013)
- [X] T008 [P] [US1] Write `backend/src/test/java/com/pim/backend/attribute/controller/AttributeListUnlinkIntegrationTest.java` against the local MongoDB: GET review → 200 per contract; POST with the token → 200 attribute with the new type and no list, same groups; a filter on a taxonomy node for the attribute is gone and another attribute's filter stays; POST without token → 400 `REVIEW_TOKEN_REQUIRED`; bad properties → 400 feature 004 code; stale token → 409 `UNLINK_OUTDATED`; text attribute → 422 `ATTRIBUTE_NOT_LIST_OF_VALUES`; `dataType: LIST_OF_VALUES` → 422 `UNLINK_TO_LIST_NOT_ALLOWED`; no permission → 403; unknown → 404 (contract `list-unlink.openapi.yaml`)

### Implementation for User Story 1

- [X] T009 [US1] Implement `reviewListUnlink(systemId)` and `unlinkList(systemId, dataType, properties, reviewToken, permissions)` in `backend/src/main/java/com/pim/backend/attribute/service/AttributeService.java` in the order of data-model "Operation order": permission → properties (feature 004 rules) → taxonomy port `runRemovingValueFilters` wrapping the attribute lock (exists, is list, new type not list, token, store with `withDataType`) → compensation on failure → details (T007 green)
- [X] T010 [P] [US1] Add DTOs `ListUnlinkRequest` (dataType, feature 004 properties objects, reviewToken), `ListUnlinkReviewResponse` and `NodeReferenceResponse` in `backend/src/main/java/com/pim/backend/attribute/dto/`, reusing `AttributePropertiesMapper`
- [X] T011 [US1] Add `GET` and `POST /api/v1/attributes/{systemId}/list-unlink` to `backend/src/main/java/com/pim/backend/attribute/controller/AttributeController.java` (HTTP only) and the mappings of the new exceptions and `REVIEW_TOKEN_REQUIRED` in `AttributeExceptionHandler.java` (T008 green)
- [X] T012 [P] [US1] Add `reviewListUnlink(systemId)` and `unlinkList(systemId, request)` with their types to `frontend/src/api/attributesApi.ts`; tests in `frontend/src/api/attributesApi.test.ts` (paths, body without displayName, permission header, 409 and 422 kinds)
- [X] T013 [US1] Write tests first in `frontend/src/attributes/AttributeEditor.test.tsx`, then add unlink mode to `frontend/src/attributes/AttributeEditor.tsx` per [list-unlink-ui.md](contracts/list-unlink-ui.md): «Desvincular lista» only for a list-of-values attribute in edit mode; unlink mode heading and intro, ID and name read only, «Tipo de dato» with the seven non-list choices and placeholder «Elige el nuevo tipo de dato», the type's property fields, field preview for the new type, review fetched on entry («Revisando la desvinculación…»), «Desvincular y guardar» sends the type, properties and token, property errors under fields, «Cancelar» back to the normal form, 409 shows the warning and reloads the review; `onUnlinked` callback with the stored attribute
- [X] T014 [US1] Update `frontend/src/pages/AttributesPage.tsx` (+ test): after an unlink show the detail with «Lista desvinculada de «{nombre}». Ahora es de tipo {tipo}.» and reload the list

**Checkpoint**: From the edit form, a list-of-values attribute can be unlinked to any other type; its
filters are deleted; nothing else changes.

---

## Phase 4: User Story 2 — See what the unlink affects, and start it from the list (Priority: P2)

**Goal**: The review shows the list and the nodes that lose filters before confirming (FR-009), and the
list detail starts the unlink of each linked attribute (FR-006).

**Independent Test**: quickstart scenarios 4, 5, 8.

### Tests for User Story 2 ⚠️

- [X] T015 [P] [US2] Write `frontend/src/attributes/UnlinkReview.test.tsx`: «Qué cambiará» with «Se desvinculará la lista «Colores».», the node names and count («Se eliminarán los filtros de valores de este atributo en 2 nodos de taxonomía:»), singular for one node, and «Este atributo no tiene filtros de valores en la taxonomía.» without nodes
- [X] T016 [P] [US2] Write tests: `frontend/src/navigation.test.ts` (a path with `?desvincular=…` still resolves to «Atributos»); `frontend/src/pages/ListsOfValuesPage.test.tsx` («Desvincular {nombre}» per linked attribute, only with the permission, navigating to `#/atributos?desvincular={id}`); `frontend/src/pages/AttributesPage.test.tsx` (that address opens the attribute's edit form in unlink mode and clears the parameter)

### Implementation for User Story 2

- [X] T017 [US2] Implement `frontend/src/attributes/UnlinkReview.tsx` (+ `.module.css`) and use it in the editor's unlink mode (T015 green)
- [X] T018 [US2] Make `getPathFromHash` in `frontend/src/navigation.ts` ignore a `?query`; add the «Desvincular» button per linked attribute in `frontend/src/pages/ListsOfValuesPage.tsx`; read `desvincular` once in `frontend/src/pages/AttributesPage.tsx` to open the attribute in unlink mode, then clear it (T016 green)

**Checkpoint**: Both entry points work; the user sees the impact before confirming.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T019 [P] Merge the two operations into `specs/004-attribute-creation/contracts/attributes.openapi.yaml` as v2.2.0 (paths, schemas, codes; version history line)
- [X] T020 [P] Add the feature 014 row to `README.md`
- [X] T021 Run the full checks: `cd backend; .\gradlew.bat clean build`; `cd frontend; npm run lint; npm run format:check; npm test; npm run build` — all green
- [X] T022 Run [quickstart.md](quickstart.md) API scenarios on a separate database (`pim-quickstart-014`, port 8091) and check the screens in the browser; drop the database afterwards. *Done 2026-11-23 on `pim-quickstart-014` (port 8091, dropped afterwards): API scenarios 1–3 and 5–10 as expected; in the browser (second frontend on port 5174) the list's «Desvincular» opened unlink mode, the review named the list and the filtered node, and the unlink to Texto showed its message.*

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: T002 → T003; T004, T005 in parallel; T006 after T005. Blocks both stories.
- **US1 (Phase 3)**: tests T007, T008 first; T009 → T011; T010 in parallel with T009; T012 → T013 → T014.
- **US2 (Phase 4)**: after T013 (the editor's unlink mode); T015 → T017; T016 → T018.
- **Polish (Phase 5)**: after the stories; T019, T020 in parallel.

### Parallel Opportunities

- T002, T004, T005 in Phase 2.
- T007, T008 (tests) together; T010 and T012 alongside the service work.
- T015 and T016 together; T019 and T020 together.

## Implementation Strategy

### MVP First (Phase 3)

1. Phases 1–3: unlink from the attribute's edit form with a new type; filters deleted; all or nothing.
2. Validate with quickstart scenarios 1–3, 6, 7, 9, 10.

### Incremental Delivery

1. Foundation → model, codes, taxonomy port.
2. US1 → the unlink works end to end (MVP).
3. US2 → impact review box and the entry point from the list.
4. Polish → contract v2.2.0, README, full checks, quickstart.

## Notes

- Feature 007's `PUT` keeps refusing type changes (T007 asserts it).
- Lock order taxonomy → attribute; the list-of-values lock is never taken by the unlink.
- Commit only when the project owner asks.
