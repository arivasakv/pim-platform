# Tasks: List-Value Filters per Taxonomy Level (Filtros contextuales por nivel)

**Input**: Design documents from `specs/012-taxonomy-list-value-filters/` (spec, plan, research,
data-model, contracts/taxonomy-value-filters.openapi.yaml, contracts/taxonomy-value-filters-ui.md,
quickstart)

**Prerequisites**: spec approved 2026-11-19 (with clarifications); plan decisions D-1 to D-9 approved
2026-11-19. Constitution amendment 2.6.0 (T001) before any storage code.

**Tests**: required by constitution VI. Within each phase, write the tests first and see them fail. API
shapes from `contracts/taxonomy-value-filters.openapi.yaml`; texts from
`contracts/taxonomy-value-filters-ui.md`, exactly.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 define a filter, US2 inheritance and restriction, US3 change/remove and moves, US4
  preview

## Path Conventions

- `BE/` = `backend/src/main/java/com/pim/backend/`; `BT/` = `backend/src/test/java/com/pim/backend/`
- `FE/` = `frontend/src/`
- Example = quickstart "Example data": list «Colores» (Rojo 0001, Azul 0002, Verde 0003, Negro 0004,
  Blanco 0005, Amarillo 0006, Rosa 0007); attribute «Color» (list, single); «Largo de manga» (text);
  Ropa (Color) › Ropa de hombre › Camisas (Largo de manga) › Camisas formales; Ropa de hombre ›
  Pantalones; Ropa › Ropa de mujer; Calzado (Color)

---

## Phase 1: Setup, governance and contracts

- [X] T001 Run `/speckit-constitution` for D-1: constitution **2.6.0** (MINOR), Principle IX adds
  «list-value filters on taxonomy nodes (the list values included per node and attribute)» to attribute
  administration data; amend `docs/decisions/0003-mongodb-attribute-administration.md` (new "Amended
  2026-11-19 (constitution 2.6.0, feature 012)" line and a `taxonomy_value_filters` row: `_id`
  `{nodeId}:{attributeId}`, fields, unique index `taxonomy_value_filter_unique` on (`nodeId`,
  `attributeId`)). Approved by the owner on 2026-11-19 (spec DEP-02). (D-1)
- [X] T002 Check the starting point on branch `012-taxonomy-list-value-filters`: backend
  `.\gradlew.bat clean build`, frontend `npm test`, `npm run lint`, `npm run build` pass; record counts
  in this file's notes. (SC-006)
- [X] T003 [P] Merge `contracts/taxonomy-value-filters.openapi.yaml` into
  `specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml`: path
  `/api/v1/taxonomy-nodes/{nodeId}/value-filters/{attributeId}` (GET/PUT/DELETE), schemas
  `ValueFilterRequest` (`valueIds` 1–50,000, items maxLength 40), `ListValue`, `FilterOnPath`,
  `ExcludedValue`, `ListAttribute`, `ValueFilterDetail`; `TaxonomyNode.listAttributes` (array of
  `ListAttribute`, required); error enum + `INVALID_FILTER_VALUES`, `ATTRIBUTE_NOT_LIST`,
  `VALUE_NOT_IN_LIST`, `VALUE_EXCLUDED_ABOVE`, `FILTER_NOT_FOUND`; version 1.1.0 → **1.2.0** with a
  description line; mark the 012 file as the reviewed delta. Check it parses (Prettier YAML). (D-7, D-9)
- [X] T004 [P] Feature 010 notes (D-9): in `specs/010-taxonomy-attribute-inheritance/spec.md` add to FR-006
  «*Since feature 012, deleting a node also deletes its list-value filters.*» and to FR-018 «*Since
  feature 012, the node detail also lists its list-of-values attributes with their allowed values
  (specs/012-…, FR-013).*»; in `…/contracts/taxonomy-ui.md` a "Listas de valores en este nodo" line
  pointing to `specs/012-taxonomy-list-value-filters/contracts/taxonomy-value-filters-ui.md`.

---

## Phase 2: Foundational (blocks every story)

**Purpose**: filter model, allowed-values rules, storage, list values lookup.

### Tests first

- [X] T005 [P] `BT/taxonomy/model/ValueFilterTest.java`: `ValueFilter.create(nodeId, attributeId,
  valueIds, at)` upper-cases node and attribute IDs, keeps value IDs once (repeats ignoring case dropped),
  refuses 0 or more than 50,000 IDs with `InvalidFilterException` (`INVALID_FILTER_VALUES`, «Incluye de 1 a
  50000 valores en el filtro. Para permitir todos los valores, quita el filtro.»); `withValues` replaces
  values and sets `updatedAt`; `id()` is `{NODE}:{ATTRIBUTE}`. (FR-002)
- [X] T006 [P] `BT/taxonomy/model/AllowedValuesTest.java` (pure): with the 7 «Colores» values and chains
  of 1–4 nodes: no filter → all 7 in list order; one filter → its values in list order; filters on two
  levels → intersection; `availableAbove` ignores the node's own filter; `excludedAbove` lists own IDs not
  allowed above with the nearest excluding ancestor; IDs compared ignoring case; IDs not in the list
  ignored; empty intersection → `noneAllowed` with the filters involved; `involved` top → node with
  included counts (counting only IDs still in the list). (FR-008 to FR-011, D-3)
- [X] T007 [P] `BT/taxonomy/repository/InMemoryValueFilterRepository.java` (test double) and
  `BT/taxonomy/repository/MongoValueFilterRepositoryIntegrationTest.java` (local MongoDB, collection
  emptied per test): save/replace by (node, attribute), `find`, `findByNodeIds`, `delete`,
  `deleteByNode`; the unique index exists. (D-2)
- [X] T008 [P] `BT/lov/service/ListOfValuesLookupTest.java` (or the existing lookup test): `values(listId)`
  returns the list's values in order for an existing list (ID ignoring case) and empty for an unknown
  one. (D-4)

### Implementation

- [X] T009 `BE/taxonomy/model/ValueFilter.java` (record: `nodeId`, `attributeId`, `List<String>
  valueIds`, `createdAt`, `Optional<Instant> updatedAt`; `create`, `withValues`, `id()`, `MAX_VALUES =
  50_000`) and `BE/taxonomy/model/InvalidFilterException.java`. (FR-002)
- [X] T010 `BE/taxonomy/model/AllowedValues.java` (pure; inputs: chain, list values as `(id, label)`,
  filters by node ID; outputs: `allowed`, `availableAbove`, `ownFilter`, `excludedAbove` with excluding
  node, `involved`, `noneAllowed`). (D-3)
- [X] T011 [P] `BE/taxonomy/repository/ValueFilterRepository.java` (interface, no rules),
  `ValueFilterDocument.java` (package-private record, collection `taxonomy_value_filters`) and
  `MongoValueFilterRepository.java` (creates the unique index `taxonomy_value_filter_unique` at start;
  `MongoTemplate`; 503 path unchanged). (D-2)
- [X] T012 [P] `BE/lov/service/ListOfValuesLookup.java`: add `Optional<List<ListValue>> values(String
  listOfValuesId)` (repository read, no lock; doc explains why). (D-4)
- [X] T013 [P] `BE/taxonomy/model/TaxonomyErrorCode.java`: add `INVALID_FILTER_VALUES`,
  `ATTRIBUTE_NOT_LIST`, `VALUE_NOT_IN_LIST`, `VALUE_EXCLUDED_ABOVE`, `FILTER_NOT_FOUND` with the messages
  of the contract examples; exceptions `AttributeNotListException`, `ValueNotInListException`,
  `ValueExcludedAboveException`, `FilterNotFoundException` in `BE/taxonomy/model/`. (D-5)

**Checkpoint**: T005–T008 pass; build compiles with `-Werror`.

---

## Phase 3: User Story 1 - Restrict the values of a list attribute on a node (Priority: P1) 🎯 MVP

**Goal**: an administrator saves a filter on a node and sees «{n} de {total}» and «Filtro en este nodo».

**Independent Test**: on "Ropa de hombre", filter Color to Azul, Negro, Blanco, Rojo → «4 de 7 · Filtro en
este nodo».

### Tests first

- [X] T014 [P] [US1] `BT/taxonomy/service/TaxonomyServiceFilterTest.java` (new; setup like
  `TaxonomyServiceMoveTest` plus a list service fake or the real lov repository double, building the
  example): `saveFilter(RopaDeHombre, Color, [0002,0004,0005,0001])` → detail with 4 allowed in list
  order (Rojo, Azul, Negro, Blanco), `includedValueIds` in list order, `filters` = [Ropa de hombre: 4];
  saving again replaces; `findFilter` without a filter returns all 7, `includedValueIds` null; refusals in
  data-model order with nothing stored: no permission, `[]`, unknown node, unknown attribute, attribute
  not applying (e.g. Color on a new top-level node without it), «Largo de manga» (not a list), `9999`
  (not in list). (FR-001 to FR-004, FR-006, FR-017)
- [X] T015 [P] [US1] `BT/taxonomy/service/TaxonomyServiceFilterTest.java`: node details `listAttributes`
  for "Ropa de hombre": Color with list name, `SINGLE`, total 7, allowed 7, `hasOwnFilter` false, no
  filters; after the save: allowed 4, `hasOwnFilter` true, filters [Ropa de hombre: 4]; «Largo de manga»
  never listed. (FR-013)
- [X] T016 [P] [US1] `BT/taxonomy/controller/TaxonomyNodeControllerIntegrationTest.java` (or a new
  `ValueFilterControllerIntegrationTest.java`, local MongoDB): GET/PUT on
  `/api/v1/taxonomy-nodes/{id}/value-filters/{attributeId}` return the contract shape; PUT persists
  (re-read); `GET /taxonomy-nodes/{id}` contains `listAttributes`; problems with status, code, field and
  Spanish detail for 400/403/404/422 cases of T014. (FR-001 to FR-004, contract)
- [X] T017 [P] [US1] `FE/api/taxonomyApi.test.ts`: `getValueFilter`, `saveValueFilter(nodeId,
  attributeId, valueIds)` (PUT, permission header, body `{valueIds}`), `removeValueFilter` (DELETE);
  problems → `TaxonomyApiError` with kind and detail. (D-7)
- [X] T018 [P] [US1] `FE/taxonomy/NodeListAttributes.test.tsx`: heading «Listas de valores en este nodo»,
  intro, table «Listas de valores de «{nodo}»» with columns; cells «Lista «Colores» · ATR…», «4 de 7»,
  restriction texts for no filter, own, inherited (link-button to the node), own + «También limitado por
  «{nodo}»»; «Ningún valor permitido» badge; **Filtrar valores de Color** only with permission;
  **Vista previa de Color** always; section absent without list attributes. (FR-013, UI contract)
- [X] T019 [P] [US1] `FE/taxonomy/ValueFilterPanel.test.tsx`: legend «Filtrar «Color» en «Ropa de
  hombre»»; two columns with searches `buscar-valores-disponibles` / `buscar-valores-incluidos` (label or
  ID, accents/case ignored); **Incluir {label}** moves a value right, **Quitar {label}** moves it back;
  **Incluir los mostrados ({n})** and **Quitar los mostrados ({n})** act on all search matches; paging 50
  with «Mostrando {m} de {n}.»; empty texts; counter «{k} valores incluidos»; **Guardar filtro** disabled
  at 0, calls `onSave(ids in list order)`; **Cancelar** calls `onCancel`. (FR-014, UI contract)
- [X] T020 [US1] `FE/pages/TaxonomyPage.test.tsx` (fake backend extended with list attributes and filter
  routes computing intersections as the backend does): open "Ropa de hombre" → section; **Filtrar valores
  de Color** → include 4 → **Guardar filtro** → success «Filtro de «Color» guardado en «Ropa de hombre»: 4
  de 7 valores permitidos.»; row shows «4 de 7» and «Filtro en este nodo»; a 422 shows the backend detail
  and keeps the panel; without permission no **Filtrar valores**. (FR-013, FR-014, FR-017)

### Implementation

- [X] T021 [US1] `BE/taxonomy/service/TaxonomyService.java`: inject `ValueFilterRepository` and
  `ListOfValuesLookup`; `ValueFilterDetails findFilter(nodeId, attributeId)` and `ValueFilterDetails
  saveFilter(nodeId, attributeId, valueIds, permissions)` under the taxonomy lock with the checks of
  data-model "Rules on save" (1–8); `details()` adds `List<ListAttributeSummary>` for effective
  LIST_OF_VALUES attributes (one `findByNodeIds(chain)` per call, one list read per list attribute). New
  model records `ValueFilterDetails`, `ListAttributeSummary` in `BE/taxonomy/model/`; `NodeDetails` +
  `listAttributes`. Update `BE/taxonomy/service/TaxonomyService` Javadoc. (FR-001 to FR-004, FR-013, D-3,
  D-5)
- [X] T022 [US1] DTOs in `BE/taxonomy/dto/`: `ValueFilterRequest` (`@NotNull @Size(min = 1, max =
  50000) List<@NotNull @Size(max = 40) String> valueIds`), `ValueFilterResponse`, `ListAttributeResponse`,
  `ListValueResponse`, `FilterOnPathResponse`, `ExcludedValueResponse`; `TaxonomyNodeResponse` +
  `listAttributes`. Controller `BE/taxonomy/controller/TaxonomyNodeController.java`: `GET`/`PUT
  /{nodeId}/value-filters/{attributeId}`. `TaxonomyExceptionHandler`: map the four new exceptions
  (statuses, titles «Atributo sin lista de valores», «Valor no encontrado», «Valor excluido arriba»,
  «Filtro no encontrado») and the `Size` constraint on `valueIds` to `INVALID_FILTER_VALUES`. (D-5, D-7)
- [X] T023 [P] [US1] `FE/api/taxonomyApi.ts`: types `ListValue`, `FilterOnPath`, `ExcludedValue`,
  `ListAttribute`, `ValueFilterDetail`, `TaxonomyNode.listAttributes`; functions `getValueFilter`,
  `saveValueFilter`, `removeValueFilter`; header comment → contract v1.2.0. (D-7)
- [X] T024 [P] [US1] `FE/taxonomy/NodeListAttributes.tsx` + `.module.css` (NEW): the section of the UI
  contract; props `node`, `canChange`, `onFilter(attribute)`, `onPreview(attribute)`, `onOpenNode(id)`.
  (FR-013)
- [X] T025 [US1] `FE/taxonomy/ValueFilterPanel.tsx` + `.module.css` (NEW): props `detail:
  ValueFilterDetail`, `busy`, `onSave(valueIds)`, `onRemove()`, `onCancel()`; working copy of included IDs
  initialized from `includedValueIds`; two columns (one under 48rem), search, paging, per-row and bulk
  moves, counter, buttons. Texts exactly as the UI contract; design tokens only. (FR-014, D-8)
- [X] T026 [US1] `FE/pages/TaxonomyPage.tsx`: render `NodeListAttributes` after `NodeAttributesTable`;
  state for the open filter (`getValueFilter` on **Filtrar valores**), `saveValueFilter` → success
  message, refresh the node (`getNode`) and the list; errors keep the panel open; `resetPanelState`
  closes it. (FR-013, FR-014)

**Checkpoint**: quickstart scenarios 1–2 and 13 behave as described; T014–T020 pass.

---

## Phase 4: User Story 2 - Filters are inherited and can only be narrowed below (Priority: P1)

**Goal**: descendants follow filters above; a lower filter can only narrow; later narrowing above is
shown; no-value-allowed is explained.

**Independent Test**: Ropa de hombre 4 values → Camisas formales shows 4 from «Ropa de hombre»; Camisas
filtered to Azul, Blanco → Camisas formales shows 2 from «Camisas».

### Tests first

- [X] T027 [P] [US2] `BT/taxonomy/service/TaxonomyServiceFilterTest.java`: US2 scenarios 1–6 on the
  example: inherited counts and origins for Camisas, Camisas formales, Pantalones, Ropa de mujer, Ropa;
  `availableValues` on Camisas = 4; saving Verde on Camisas → `ValueExcludedAboveException`
  (`VALUE_EXCLUDED_ABOVE`, «El valor «Verde» (0003) no está permitido por el filtro de «Ropa de hombre».
  Solo puedes incluir valores permitidos arriba.»), nothing stored; narrowing Ropa de hombre after
  Camisas → Camisas allowed = Azul, `excludedAboveValues` = Blanco by Ropa de hombre, stored Camisas
  filter unchanged; narrowing to Negro only → allowed empty, filters involved listed. (FR-007 to FR-011,
  SC-001, SC-002)
- [X] T028 [P] [US2] Controller integration test: 409 `VALUE_EXCLUDED_ABOVE` with field `valueIds`; GET
  on a descendant returns `filters` top → node and `excludedAboveValues`. (FR-009, FR-010, contract)
- [X] T029 [P] [US2] `FE/taxonomy/ValueFilterPanel.test.tsx` + `NodeListAttributes.test.tsx`: inherited
  note «Arriba se permiten 4 de 7 valores (filtro de «Ropa de hombre»).»; only available values offered;
  excluded-above rows marked «Excluido por «Ropa de hombre»» with the info notification «1 valor de este
  filtro ya no está permitido arriba y no se aplica.» (singular/plural per UI contract); the
  «Ningún valor permitido» badge. (FR-010, FR-011)

### Implementation

- [X] T030 [US2] Make T027–T029 pass in `TaxonomyService` (check 8 «allowed above»; detail fields) and
  `ValueFilterPanel`/`NodeListAttributes`; no change to `EffectiveAttributes`. (FR-007 to FR-011)

**Checkpoint**: quickstart scenarios 3–8 behave as described.

---

## Phase 5: User Story 3 - Change or remove a filter; moved nodes follow their new branch (Priority: P2)

**Goal**: filters can be edited and removed; moves and node deletion behave as designed.

**Independent Test**: remove Camisas' filter → Camisas formales follows «Ropa de hombre»; move Camisas
under Ropa de mujer → 7 values.

### Tests first

- [X] T031 [P] [US3] `BT/taxonomy/service/TaxonomyServiceFilterTest.java`: change values within the
  available ones; `removeFilter` → inherited values again; removing a missing filter →
  `FilterNotFoundException` (`FILTER_NOT_FOUND`); US3 scenarios 3–4 with `move` (feature 011); deleting a
  leaf node with its own filter deletes the filter (`findByNodeIds` empty); removing Color's assignment
  from Ropa leaves descendants' filters stored but `listAttributes` without Color; re-assigning makes them
  apply again. (FR-005, FR-012, D-6, OQ-002 default)
- [X] T032 [P] [US3] Controller integration test: `DELETE …/value-filters/{attributeId}` → 204, then 404
  `FILTER_NOT_FOUND`; 403 without permission; after `POST …/move` the moved node's `listAttributes`
  reflect the new branch. (FR-005, FR-012)
- [X] T033 [P] [US3] `FE/taxonomy/ValueFilterPanel.test.tsx` + `FE/pages/TaxonomyPage.test.tsx`: **Quitar
  filtro** only with an own filter; inline confirmation «¿Quitar el filtro de «Color» en «Camisas»? Este
  nodo y sus niveles inferiores permitirán los valores heredados de arriba.» → **Sí, quitar filtro** →
  «Filtro de «Color» quitado de «Camisas».»; the row returns to the inherited restriction. (FR-005)

### Implementation

- [X] T034 [US3] `TaxonomyService.removeFilter(nodeId, attributeId, permissions)` and `delete` also calling
  `filters.deleteByNode` after deleting the node; controller `DELETE /{nodeId}/value-filters/{attributeId}`
  (204); `FilterNotFoundException` mapping; page and panel remove flow. (FR-005, D-6)

**Checkpoint**: quickstart scenarios 9–11 and 14 behave as described.

---

## Phase 6: User Story 4 - See the field as it will be captured at a node (Priority: P3)

**Goal**: preview the list field with only the allowed values.

**Independent Test**: on Camisas formales, «Vista previa de Color» offers the 2 allowed values.

### Tests first

- [X] T035 [P] [US4] `FE/taxonomy/NodeListFieldPreview.test.tsx`: heading «Vista previa de «Color» en
  «Camisas formales»», note text; single → select with the allowed values in order; multiple → checkboxes;
  more than 50 → first 50 and feature 008's summary text; none allowed → warning naming the nodes; **Cerrar
  vista previa**. (FR-016, UI contract)

### Implementation

- [X] T036 [US4] `FE/taxonomy/NodeListFieldPreview.tsx` (NEW), reusing `PREVIEW_VALUE_LIMIT` and
  `listSummary` from `FE/attributes/fieldPreview.ts` and the `SelectField`/`CheckboxField` components;
  wire **Vista previa** in `TaxonomyPage.tsx` (loads `getValueFilter`, shows `allowedValues`). (FR-016,
  D-8)

**Checkpoint**: quickstart scenario 12 behaves as described.

---

## Phase 7: Polish and validation

- [X] T037 [P] `BT/taxonomy/service/TaxonomyServiceConcurrencyTest.java`: 20 simultaneous `saveFilter`
  calls on the same node and attribute with different value sets leave exactly one stored filter equal to
  one of the requests; a save on Camisas racing a narrowing of Ropa de hombre never stores a value
  excluded above at the moment of saving. (edge case "Concurrent changes", SC-002)
- [X] T038 Run `.\gradlew.bat spotlessApply; .\gradlew.bat clean build` and `npm run format; npm run lint;
  npm run format:check; npm test; npm run build`; record counts against T002. (SC-006)
- [X] T039 [P] Scope check: `git diff --stat main -- backend/src/main/java` lists only `taxonomy/` files and
  `lov/service/ListOfValuesLookup.java`; no change in `attribute/`, `attributegroup/`, `identifier/`,
  `product/`, `frontend/src/navigation.ts`, `frontend/src/components/`. (FR-020)
- [X] T040 [P] `README.md`: add "012 | List-value filters per taxonomy level (restrict list values on a node;
  inherited and only narrowed below)" to the features table.
- [X] T041 Run quickstart scenarios that need no browser through the API on a separate database
  (`pim-quickstart-012`, port 8091), plus a 50,000-value list timing (SC-005); report pass/fail; drop the
  database afterwards. (SC-001 to SC-005)
- [X] T042 Owner browser check: quickstart scenarios 1–5, 7–10, 12, 15 and 17 at desktop and 768 px.
  (SC-004, FR-014) Confirmed by the project owner on 2026-11-19 ("looks good").

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)**: T001 first (storage needs the amendment); T002 after it; T003, T004 in parallel.
- **Foundational (2)**: after Setup; blocks every story.
- **US1 (3)**: after Foundational. MVP.
- **US2 (4)**: after US1 (uses save, detail and panel).
- **US3 (5)**: after US1; independent of US2.
- **US4 (6)**: after US1 (needs `getValueFilter`); independent of US2 and US3.
- **Polish (7)**: last.

### Task dependencies

- T005 → T009; T006 → T010; T007 → T011; T008 → T012; T013 any time in Phase 2.
- US1: T014–T020 first. Backend T021 → T022. Frontend T023, T024 in parallel → T025 → T026.
- US2: T027–T029 → T030. US3: T031–T033 → T034. US4: T035 → T036.
- T037 after T034; T038 → T041 → T042; T039, T040 any time after T036.

### Parallel opportunities

- Setup: T003, T004. Foundational tests T005–T008; implementation T011, T012, T013 next to T009/T010.
- US1 tests T014–T019; frontend T023, T024 alongside the backend tasks.
- US2, US3 and US4 can be worked on at the same time after US1.

## Parallel Example: User Story 1

```text
Task: "T014 TaxonomyServiceFilterTest – save and refusals"
Task: "T016 Controller integration – value-filters endpoints"
Task: "T017 taxonomyApi.test.ts – filter calls"
Task: "T018 NodeListAttributes.test.tsx – section"
Task: "T019 ValueFilterPanel.test.tsx – two columns"
```

---

## Implementation Strategy

1. **MVP**: Setup (with the amendment) → Foundational → US1. Validate quickstart 1–2, 13.
2. **Rules**: US2 (inheritance and restrictive overwrite). Validate 3–8.
3. **Maintenance**: US3. Validate 9–11, 14.
4. **Preview**: US4. Validate 12.
5. **Polish**: concurrency, suites, scope, README, quickstart API run, owner browser check.

Each step leaves lint, format check, tests and build green (constitution X). Commits only when the user
asks.

## Notes

- Requirement traceability:

  | Requirements | Tasks |
  |---|---|
  | FR-001 to FR-004, FR-006 | T005, T009, T013, T014, T016, T021, T022 |
  | FR-005 | T031–T034 |
  | FR-007 to FR-011 | T006, T010, T027–T030 |
  | FR-012 | T031, T032 |
  | FR-013 | T015, T018, T024, T026 |
  | FR-014 | T019, T020, T025, T026 |
  | FR-015 | by design (filters keyed by node) |
  | FR-016 | T035, T036 |
  | FR-017 | T014, T016, T020, T032 |
  | FR-018 | deferred (spec Deferred Scope) |
  | FR-019 | T021, T023 (frontend displays backend data only) |
  | FR-020 | T039 |
  | SC-001 to SC-006 | T002, T027, T037, T038, T041, T042 |
  | DEP-02 (storage) | T001 |

- Starting test counts (T002, 2026-11-19): backend `clean build` passes with **535** tests; frontend
  **324** tests in 29 files (unchanged since feature 011's merge).
- Final counts (T038): backend **568** tests (+33: `ValueFilterTest` 3, `AllowedValuesTest` 6,
  `MongoValueFilterRepositoryIntegrationTest` 4, `ListOfValuesLookupTest` 1, `TaxonomyServiceFilterTest` 14,
  `ValueFilterControllerIntegrationTest` 5); frontend **344** tests in 32 files (+20: API 2,
  `NodeListAttributes` 4, `ValueFilterPanel` 6, `NodeListFieldPreview` 3, page 5); lint, format check and
  build pass.
- T001: constitution amended to **2.6.0** (Principle IX) with `/speckit-constitution`; no Sync Impact Report
  (removed from the constitution on 2026-11-11). ADR 0003 amended separately, as part of the task.
- Deviations from the task text, all within the plan's decisions:
  - T012: `ListOfValuesLookup.find(listId)` returns the whole list (name and values) instead of only the
    values, because the service also needs the list name for messages and the node detail.
  - T013/T021: a fifth exception, `FilterAttributeNotFoundException`, gives the contract's 404
    `ATTRIBUTE_NOT_FOUND` with field `attributeId` (feature 010's `UnknownAttributeException` answers 422
    for assignments).
  - T016, T028, T032: controller tests in a new class `ValueFilterControllerIntegrationTest`.
  - T037: concurrency tests in `TaxonomyServiceFilterTest` (it has the list attribute set up), not in
    `TaxonomyServiceConcurrencyTest`.
  - Frontend test data shared in `FE/taxonomy/filterFixtures.ts`.
- Contract adjustments during implementation: a request without `valueIds` answers 400 `REQUIRED` like
  every other taxonomy request (both OpenAPI files); the panel drops values excluded above when saving,
  says so («Se quitará(n) al guardar.»), and the counter counts only values allowed above (UI contract).
- T041 (2026-11-19), backend on port 8091 with database `pim-quickstart-012` (dropped afterwards; `pim`
  untouched): API scenarios 1–11 and 13–15 **PASS**. Scenario 16 (50,000-value list) first **FAILED**:
  saving 20,000 values took 16 s on the server, because each value was checked against the allowed list
  one by one. Fixed in `AllowedValues`/`TaxonomyService` (sets built once); measured again with curl:
  save 20,000 values 0.49–0.81 s, filter dialog data 0.31–0.34 s (2.8 MB), node detail 0.28 s — all
  under SC-005's 2 s. The first script's longer read times came from PowerShell 5.1 parsing the JSON, not
  from the server. Scenarios 12 and 17 are browser checks (T042).
