# Tasks: Attribute Inheritance through the Product Taxonomy

**Input**: Design documents from `specs/010-taxonomy-attribute-inheritance/` (spec, plan, research,
data-model, contracts/taxonomy.openapi.yaml, contracts/taxonomy-ui.md, quickstart)

**Prerequisites**: spec and plan decisions D-1 to D-10 approved by the project owner on 2026-11-12;
constitution amendment of D-2 made (T001) before any storage code.

**Tests**: required by constitution VI. Within each phase, write the tests first and see them fail.
API shapes from `contracts/taxonomy.openapi.yaml`; texts from `contracts/taxonomy-ui.md`, exactly.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 hierarchy, US2 assignments, US3 effective attributes, US4 propagation and deletion

## Path Conventions

- `BE/` = `backend/src/main/java/com/pim/backend/`; `BT/` = `backend/src/test/java/com/pim/backend/`
- `FE/` = `frontend/src/`

---

## Phase 1: Setup and governance

- [X] T001 Run `/speckit-constitution` for the D-2 amendment: constitution 2.5.0, Principle IX adds
  "taxonomy nodes and their direct attribute assignments" to attribute administration data (MINOR);
  amend `docs/decisions/0003-mongodb-attribute-administration.md` (new "Amended" line and a
  `taxonomy_nodes` row with the indexes of data-model.md). Owner approval of the amendment text.
  (DEP-03)
- [X] T002 Check the starting point on branch `010-taxonomy-attribute-inheritance`: backend
  `.\gradlew.bat clean build` and frontend `npm test`, `npm run lint`, `npm run build` pass; record
  test counts in this file's notes. (FR-022)
- [X] T003 [P] Contracts: add `contracts/taxonomy.openapi.yaml` as reviewed (validate with
  `npx prettier --parser yaml --check`); bump `specs/004-attribute-creation/contracts/attributes.openapi.yaml`
  to v1.3.0: DELETE 409 also `ATTRIBUTE_ON_TAXONOMY_NODES` with property `nodes` `[{systemId, name}]`
  and the enum value. (FR-021, constitution VII)

---

## Phase 2: Foundational (blocks every story)

**Purpose**: domain model, inheritance rules, repository, service skeleton.

### Tests first

- [X] T004 [P] `BT/taxonomy/model/NodeNameTest.java`: trims; 1–80 characters after trimming else
  `InvalidNodeException` (`INVALID_NODE_NAME`, «El nombre del nodo debe tener de 1 a 80 caracteres.»);
  `key()` lower-cased trimmed. `BT/taxonomy/model/TaxonomyNodeTest.java`: `withAttributes` appends
  new IDs upper-cased in order and reports already present ones; `withoutAttribute`; `renamed` keeps
  ID, parent, attributes, createdAt and sets updatedAt; description trimmed, blank → empty, > 500 →
  `INVALID_NODE_DESCRIPTION`. (FR-001, FR-004, FR-009)
- [X] T005 [P] `BT/taxonomy/model/EffectiveAttributesTest.java`: the feature request example (Ropa →
  Ropa de hombre → Camisas/Pantalones) gives exactly the lists of spec US3 with types, origins and
  order; top-level node has only DIRECT; sibling assignments not shared; four-level chain inherits from
  every ancestor; same attribute on two ancestors → once, origin nearest; same attribute direct and on
  an ancestor → once, DIRECT, `alsoInheritedFrom` nearest ancestor; level and path; missing parent stops
  the chain without failing. (FR-012 to FR-016, SC-001)
- [X] T006 [P] `BT/taxonomy/repository/InMemoryTaxonomyNodeRepository.java` (test double) and
  `BT/taxonomy/repository/MongoTaxonomyNodeRepositoryIntegrationTest.java`: save/find by ID ignoring
  case, findAll, delete, `findAllListing(attributeId)`; unique sibling-name index refuses a duplicate
  under the same parent and allows it under another parent and at top level vs child; documents as in
  data-model.md. (D-3)

### Implementation

- [X] T007 `BE/taxonomy/model/`: `NodeName`, `TaxonomyNode`, `NodeReference`, `AssignmentType`,
  `EffectiveAttribute`, `EffectiveAttributes`, `NodeDetails`, `AddAttributesOutcome`,
  `TaxonomyErrorCode` (contract enum), exceptions `InvalidNodeException`, `NodeNotFoundException`,
  `NodeNameInUseException`, `NodeNotEmptyException` (children or attributes, with counts),
  `UnknownAttributeException`, `AttributeNotDirectException` (origin), `AttributeNotOnNodeException`,
  `TaxonomyChangeNotPermittedException`. T004, T005 pass.
- [X] T008 `BE/taxonomy/repository/`: `TaxonomyNodeRepository` interface, `TaxonomyNodeDocument`
  (fields of data-model.md), `MongoTaxonomyNodeRepository` with `MongoTemplate`, index creation
  (`taxonomy_node_sibling_name_unique`, `taxonomy_node_attribute_ids`) as features 005/006 do; storage
  failures mapped as in the attribute group repository. T006 passes.
- [X] T009 `BE/taxonomy/service/TaxonomyService.java` skeleton: constructor (repository,
  `AttributeService`, `IdentifierService`, `Clock`), private `lock`, `findAll()`, `find(id)` building
  `NodeDetails` (chain, path, level, children sorted by name, effective attributes resolved with
  `AttributeService.findAll()`; unknown IDs skipped with a warning). Unit test
  `BT/taxonomy/service/TaxonomyServiceTest.java` (in-memory repository, real `AttributeService` with
  in-memory repositories or a stub as `AttributeGroupServiceTest` does): find returns details; unknown
  node → `NodeNotFoundException`. (FR-007, FR-013)

**Checkpoint**: inheritance rules tested; nodes can be stored and read.

---

## Phase 3: User Story 1 – Build the product hierarchy (P1) 🎯 MVP

**Goal**: create, rename, delete nodes; tree page. **Independent test**: quickstart A1, A2, A6, A8,
A9, B1, B2, B12–B14.

### Tests first

- [X] T010 [P] [US1] `BT/taxonomy/service/TaxonomyServiceTest.java`: create top-level and child
  (generated `CLS` ID via `IdentifierService`, level), unknown parent → not found, sibling name
  duplicate (ignoring case/spaces) refused, same name under another parent allowed; update renames
  and replaces description, keeps parent/children/attributes; delete refused with children
  (`NODE_HAS_CHILDREN`) or direct attributes (`NODE_HAS_ATTRIBUTES`), succeeds when empty; every change
  without `ATTRIBUTE_CREATE` → `TaxonomyChangeNotPermittedException` and nothing changes. (FR-001 to
  FR-006, FR-017)
- [X] T011 [P] [US1] `BT/taxonomy/controller/TaxonomyNodeControllerIntegrationTest.java` (MockMvc +
  local MongoDB, as `AttributeGroupControllerIntegrationTest`): `GET /api/v1/taxonomy-nodes` (flat,
  sorted, `level`, `directAttributeCount`, `childCount`), `POST` 201 + `Location`, 400/403/404/409
  problems with the contract codes and Spanish messages, `GET/{id}`, `PUT`, `DELETE` 204/409; problem
  shape `errors[]` (field, code, message). Plus `TaxonomyStorageFailureIntegrationTest` (503
  `STORAGE_UNAVAILABLE` as group/LOV tests). (FR-001 to FR-007, contract)
- [X] T012 [P] [US1] `FE/api/taxonomyApi.test.ts`: `listNodes`, `getNode`, `createNode`, `updateNode`,
  `deleteNode` request URLs, methods, bodies, permission header; problem → `TaxonomyApiError` kinds
  (validation, forbidden, notFound, conflict, unprocessable, unavailable) with `detail` and `errors`;
  `canChangeTaxonomy()` reads `VITE_DEV_PERMISSIONS`.
- [X] T013 [P] [US1] `FE/taxonomy/nodeTree.test.ts`: `buildTree(items)` nests by `parentId` keeping
  API order, tolerates an unknown parent (shown at top level). (`ancestorsOf` dropped: the node detail already returns its path.)
- [X] T014 [P] [US1] `FE/taxonomy/TaxonomyTree.test.tsx`: renders nested lists; expand/collapse buttons
  with accessible names and `aria-expanded`; node buttons with "{nodo}, {n} atributo(s) directo(s)" and
  `aria-pressed`; clicking calls `onSelect(id)`; top level expanded by default; `expanded` prop
  respected.
- [X] T015 [US1] `FE/pages/TaxonomyPage.test.tsx` (fake backend like `AttributesPage.test.tsx`): loading,
  error + "Reintentar", empty texts; create first-level node and child with confirmations; name
  required; name in use shown on the field; edit; delete confirmation texts for deletable / with
  children / with attributes (no request in the last two); path and children links open nodes; no
  change controls without permission. Update `FE/navigation.test.ts` / `FE/App.test.tsx` for the
  entry "Taxonomía" `#/taxonomia` after "Grupo de Atributos". (FR-003 to FR-007, FR-017, FR-018)

### Implementation

- [X] T016 [US1] `BE/taxonomy/service/TaxonomyService.java`: `create`, `update`, `delete` under the
  lock with permission check, ID assignment (`ObjectType.CLASSIFICATION`, generated), sibling-name
  check, timestamps. T010 passes.
- [X] T017 [US1] `BE/taxonomy/dto/` (`TaxonomyNodeCreateRequest`, `TaxonomyNodeUpdateRequest`,
  `TaxonomyNodeSummaryResponse`, `TaxonomyNodeCollectionResponse`, `TaxonomyNodeResponse`,
  `NodeReferenceResponse`, `EffectiveAttributeResponse`), `BE/taxonomy/controller/`
  (`TaxonomyNodeController`, `TaxonomyPermissionsReader`, `TaxonomyExceptionHandler` using
  `common.ApiProblems`). T011 passes.
- [X] T018 [P] [US1] `FE/api/taxonomyApi.ts` (types from the contract, client functions, error class,
  `canChangeTaxonomy`). T012 passes.
- [X] T019 [P] [US1] `FE/taxonomy/nodeTree.ts`, `FE/taxonomy/TaxonomyTree.tsx` and
  `TaxonomyTree.module.css` (list-button styles of feature 007, indentation per level with tokens,
  wraps long names). T013, T014 pass.
- [X] T020 [US1] `FE/pages/TaxonomyPage.tsx` + `TaxonomyPage.module.css` (two-column layout of
  `ListsOfValuesPage`), `FE/navigation.ts` entry, `FE/App.tsx` route. Node card with path, facts,
  children, inline create/edit forms and delete confirmation per UI contract. T015 passes.

**Checkpoint**: the hierarchy can be built, browsed and maintained.

---

## Phase 4: User Story 2 – Assign attributes to a node (P1)

**Goal**: direct assignments. **Independent test**: quickstart A3, B3, B4, B11.

### Tests first

- [X] T021 [P] [US2] `BT/taxonomy/service/TaxonomyServiceTest.java`: `addAttributes` all or nothing
  (unknown attribute → `UnknownAttributeException`, nothing added), duplicates reported in
  `alreadyAssigned`, IDs upper-cased, order kept, attribute data unchanged (read the attribute before
  and after); `removeAttribute` removes a direct one, refuses an inherited-only one with
  `AttributeNotDirectException` naming the origin, `AttributeNotOnNodeException` otherwise; permission
  checks. (FR-008 to FR-011)
- [X] T022 [P] [US2] `TaxonomyNodeControllerIntegrationTest`: `POST …/attributes` 200 (`node`,
  `added`, `alreadyAssigned`), 400 empty list, 422 `ATTRIBUTE_NOT_FOUND`; `DELETE
  …/attributes/{attributeId}` 204, 409 `ATTRIBUTE_NOT_DIRECT`, 404 `ATTRIBUTE_NOT_ON_NODE`. (contract)
- [X] T023 [P] [US2] `FE/api/taxonomyApi.test.ts`: `assignAttributes`, `removeAttribute`.
- [X] T024 [US2] `FE/pages/TaxonomyPage.test.tsx`: picker opens, searches (name/ID, accents), excludes
  direct attributes, marks inherited ones "— heredado de «…»", pages 50 + "Mostrar más", keeps choices
  across searches, "Asignar seleccionados (n)" sends the IDs and shows the confirmation (with repeats
  text); "Quitar" confirmation and success; tree count updates; refusal shows the backend message.

### Implementation

- [X] T025 [US2] `TaxonomyService.addAttributes` / `removeAttribute` (validate IDs through
  `AttributeService.find`, under the taxonomy lock: taxonomy → attribute order). `dto/AssignAttributesRequest`,
  `dto/AssignAttributesResponse`, controller endpoints, exception mapping. T021, T022 pass.
- [X] T026 [US2] `FE/api/taxonomyApi.ts` functions; picker and remove flow in `FE/pages/TaxonomyPage.tsx`
  (reuse `matchesFilters` from `FE/attributes/attributeBrowsing.ts` and `listAttributes` from
  `FE/api/attributesApi.ts`). T023, T024 pass.

---

## Phase 5: User Story 3 – Effective attributes and their origin (P1)

**Goal**: the attribute table with badges and origins. **Independent test**: quickstart A4, A5, B5–B8.

### Tests first

- [X] T027 [P] [US3] `TaxonomyNodeControllerIntegrationTest`: example hierarchy → `GET` Camisas and
  Pantalones return `effectiveAttributes` exactly as data-model.md (systemId, displayName, dataType,
  assignment, origin, `alsoInheritedFrom` only when relevant, order); attribute renamed through the
  attributes API shows the new name. (FR-012 to FR-016, SC-001)
- [X] T028 [P] [US3] `FE/taxonomy/NodeAttributesTable.test.tsx`: columns; badges "Directo"/"Heredado"
  with words; origin "Este nodo" or a button "Ir al nodo {origen}" calling `onOpenNode`; "También se
  hereda de «…»"; "Quitar {atributo} de este nodo" only on direct rows and only with `canChange`;
  summary "{d} directo(s) · {h} heredado(s)"; the three empty/only-inherited texts; inherited note.
  (FR-019)
- [X] T029 [US3] `FE/pages/TaxonomyPage.test.tsx`: opening Camisas shows the US3 table; origin click
  opens Ropa and expands the tree path; type labels come from `fetchAttributeTypes`. (US3)

### Implementation

- [X] T030 [US3] `TaxonomyNodeResponse` / `EffectiveAttributeResponse` mapping complete (if not done in
  T017). T027 passes.
- [X] T031 [US3] `FE/taxonomy/NodeAttributesTable.tsx` + `.module.css` (table styles with tokens;
  `StatusBadge` info/neutral); used in `TaxonomyPage.tsx`. T028, T029 pass.

---

## Phase 6: User Story 4 – Propagation and attribute deletion (P2)

**Goal**: ancestor changes reach descendants; attributes on nodes cannot be deleted. **Independent
test**: quickstart A7, B9, B10, B15.

### Tests first

- [X] T032 [P] [US4] `TaxonomyServiceTest`: assign on the top node → every descendant (3 levels) lists
  it as inherited on the next `find`; remove → gone; rename → origin name updated; direct + ancestor →
  one DIRECT entry with `alsoInheritedFrom`, after removing the direct one → INHERITED.
  `BT/taxonomy/service/TaxonomyServiceConcurrencyTest.java`: concurrent assigns/removes/deletes leave a
  consistent tree (pattern of `AttributeGroupServiceConcurrencyTest`). (FR-015, SC-002)
- [X] T033 [P] [US4] `BT/attribute/service/AttributeServiceTest.java` and
  `BT/attribute/controller/AttributeControllerIntegrationTest.java`: deleting an attribute assigned to
  nodes → 409 `ATTRIBUTE_ON_TAXONOMY_NODES` with `nodes` and the Spanish message, attribute kept;
  after removing the assignments, deletion succeeds; group check still first. Update existing setups
  with a `TaxonomyAssignmentDirectory` that reports no nodes. `BT/taxonomy/service/TaxonomyAssignmentDirectoryAdapterTest.java`:
  runs the action only when no node lists the attribute (ignoring case). (FR-021)
- [X] T034 [P] [US4] `FE/pages/AttributesPage.test.tsx`: a 409 `ATTRIBUTE_ON_TAXONOMY_NODES` on delete
  shows the backend message (no code change expected). (FR-021)

### Implementation

- [X] T035 [US4] `BE/attribute/repository/TaxonomyAssignmentDirectory.java`,
  `BE/attribute/model/TaxonomyNodeReference.java`, `AttributeOnTaxonomyNodesException.java`,
  `AttributeErrorCode.ATTRIBUTE_ON_TAXONOMY_NODES`, `AttributeService.delete` (group → taxonomy →
  delete), `AttributeExceptionHandler` (409 with `nodes`); `BE/taxonomy/service/TaxonomyAssignmentDirectoryAdapter.java`
  (wired through `ObjectProvider` like the group adapter). T032–T034 pass.

**Checkpoint**: all stories complete.

---

## Phase 7: Polish & Cross-Cutting

- [X] T036 Backend `.\gradlew.bat clean build` (Spotless, `-Werror`, all tests); record counts. (SC-007)
- [X] T037 [P] Frontend `npm run lint`, `npm run format:check`, `npm test`, `npm run build`; record
  counts. (SC-007)
- [X] T038 [P] Docs: mention the "Taxonomía" page wherever the README or `docs/` list the pages and
  APIs (only if such lists exist); feature 004 contract already bumped (T003).
- [X] T039 Quickstart API scenarios A1–A9 against a temporary backend on a separate MongoDB port (as
  feature 007's check); stop temporary processes afterwards.
- [X] T040 [P] Scope check per quickstart (`git diff --stat main`).
- [ ] T041 Owner browser check: quickstart B1–B17. (SC-003, SC-004)

---

## Dependencies & Execution Order

- T001 (constitution) before T008 (Mongo repository). T002, T003 any time before implementation.
- Phase 2 → US1 → US2 → US3 → US4 → Polish. US3's backend part is mostly delivered by T009/T017; its
  tasks verify and add the UI. US4's deletion part (T033, T035) depends only on Phase 2 and T016 and
  can run in parallel with US2/US3.
- Within a phase: tests first; tasks touching the same file run in order (`TaxonomyService*`,
  `TaxonomyNodeControllerIntegrationTest`, `TaxonomyPage*`).

## Parallel Opportunities

- T004 ∥ T005 ∥ T006; T010 ∥ T011 ∥ T012 ∥ T013 ∥ T014; T018 ∥ T019.
- T021 ∥ T022 ∥ T023; T027 ∥ T028; T032 ∥ T033 ∥ T034; T037 ∥ T038 ∥ T040.

## Implementation Strategy

- **MVP**: Phases 1–3 (hierarchy) plus Phase 4–5 (assignments and the inheritance table), since
  inheritance is the purpose; US4 hardens it (deletion safety, propagation tests).
- Each checkpoint leaves every existing suite green.

## Notes

- Test counts at start (T002): backend 464, frontend 266. After implementation (T036, T037): backend 517 (+53: 51 taxonomy, 2 attribute deletion), frontend 294 (+28: 5 API client, 2 tree helper, 2 tree, 7 attribute table, 10 page, 1 navigation, 1 attribute page refusal).
- T038: no README or `docs/` file lists pages or APIs; nothing to update.
- T039: quickstart A1–A9 passed against a temporary backend (port 8089) and MongoDB (port 27018), including persistence after a restart; temporary processes stopped and their data removed.
- Contract `REQUIRED` code added to `taxonomy.openapi.yaml` (absent name or unreadable body), as in the groups API.
