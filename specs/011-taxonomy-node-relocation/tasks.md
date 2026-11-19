# Tasks: Taxonomy Node Relocation (Reubicación de Nodos)

**Input**: Design documents from `specs/011-taxonomy-node-relocation/` (spec, plan, research,
data-model, contracts/taxonomy-move.openapi.yaml, contracts/taxonomy-move-ui.md, quickstart)

**Prerequisites**: spec approved 2026-11-19 (with clarifications); plan decisions D-1 to D-7 approved
2026-11-19. No constitution amendment needed (taxonomy nodes already allowed in MongoDB, 2.5.0).

**Tests**: required by constitution VI. Within each phase, write the tests first and see them fail.
API shapes from `contracts/taxonomy-move.openapi.yaml`; texts from `contracts/taxonomy-move-ui.md`,
exactly.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 move under another parent, US2 refusals, US3 inheritance after a move, US4 top level

## Path Conventions

- `BE/` = `backend/src/main/java/com/pim/backend/`; `BT/` = `backend/src/test/java/com/pim/backend/`
- `FE/` = `frontend/src/`
- Example hierarchy = spec "User Scenarios" (Ropa, Ropa de hombre, Camisas, Camisas formales,
  Pantalones, Ropa de mujer, Calzado, with their direct attributes)

---

## Phase 1: Setup and contracts

- [X] T001 Check the starting point on branch `011-taxonomy-node-relocation`: backend
  `.\gradlew.bat clean build` and frontend `npm test`, `npm run lint`, `npm run build` pass; record the
  test counts in this file's notes. (SC-006)
- [X] T002 [P] Merge the operation of `specs/011-taxonomy-node-relocation/contracts/taxonomy-move.openapi.yaml`
  into `specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml`: path
  `/api/v1/taxonomy-nodes/{nodeId}/move` (POST `moveTaxonomyNode`), schemas `MoveNodeRequest`
  (`parentId`: `type: [string, 'null']`, `maxLength: 40`) and `MoveNodeResult` (`node`,
  `movedNodeCount` integer ≥ 1), the 404/409 examples; `info.version` 1.0.0 → **1.1.0**; add "Feature
  011: move" to `info.description`; in the 011 file keep only a note pointing to the merged contract or
  keep it as the reviewed delta (both must say the same). Validate with
  `npx prettier --parser yaml --check`. (D-3, D-7, constitution VII)
- [X] T003 [P] Feature 010 notes (D-7): in `specs/010-taxonomy-attribute-inheritance/spec.md` add to
  FR-005 «*Amended 2026-11-19 by feature 011: the parent changes only through a move
  (specs/011-taxonomy-node-relocation, FR-001 to FR-009).*», to Edge Cases «Loops» «*Since feature 011,
  moves are checked so a loop cannot be created (011 FR-005).*», and to OQ-005 «*Answered by feature
  011 (FR-010).*»; in `specs/010-taxonomy-attribute-inheritance/contracts/taxonomy-ui.md` add a "Mover
  nodo" line under "Node card" pointing to `specs/011-taxonomy-node-relocation/contracts/taxonomy-move-ui.md`.
  (FR-017)

---

## Phase 2: Foundational (blocks every story)

**Purpose**: model method, error codes, exceptions and result type used by all stories.

### Tests first

- [X] T004 [P] `BT/taxonomy/model/TaxonomyNodeTest.java`: `movedTo("cls000005", at)` returns a node
  with `parentId` `CLS000005` (upper-cased), `updatedAt` = `at`, and the same `systemId`, `name`,
  `description`, `attributeIds` and `createdAt`; `movedTo(null, at)` gives an empty `parentId`.
  (FR-002, FR-004)

### Implementation

- [X] T005 `BE/taxonomy/model/TaxonomyNode.java`: add `movedTo(String newParentId, Instant at)` (null →
  `Optional.empty()`, otherwise `key(newParentId)`; `updatedAt = Optional.of(at)`); change the
  `parentId` doc from "fixed at creation (FR-005)" to "changes only through a move (feature 011)".
  (FR-001, FR-002)
- [X] T006 [P] `BE/taxonomy/model/TaxonomyErrorCode.java`: add `NODE_MOVE_INTO_OWN_BRANCH("No puedes
  mover «%s» dentro de sí mismo ni de uno de sus nodos descendientes.")` and
  `NODE_ALREADY_THERE("«%s» ya está en «%s».")`; add the message templates for the move name clash:
  «Ya existe un nodo «%s» en «%s». Cambia el nombre de uno de los dos antes de mover.» and ««%s» ya
  está en el primer nivel.» / «Ya existe un nodo «%s» en el primer nivel. Cambia el nombre de uno de
  los dos antes de mover.» (as constants or as separate enum-backed messages; codes stay
  `NODE_ALREADY_THERE` and `NODE_NAME_IN_USE`). (research R-04)
- [X] T007 [P] `BE/taxonomy/model/NodeMoveNotAllowedException.java` (NEW): carries a `TaxonomyErrorCode`
  (`NODE_MOVE_INTO_OWN_BRANCH` or `NODE_ALREADY_THERE`) and the Spanish message, with factories
  `intoOwnBranch(TaxonomyNode node)`, `alreadyUnder(TaxonomyNode node, TaxonomyNode parent)`,
  `alreadyTopLevel(TaxonomyNode node)`. In `BE/taxonomy/model/NodeNameInUseException.java` add a
  factory for a move: `atDestination(NodeName name, TaxonomyNode destination)` (null destination →
  top-level message) exposing the field `parentId` (default stays `name`). (FR-005 to FR-007)
- [X] T008 [P] `BE/taxonomy/model/MoveOutcome.java` (NEW record): `NodeDetails details`, `int
  movedNodeCount` (≥ 1). `BE/taxonomy/dto/MoveNodeRequest.java` (NEW record): `@Size(max = 40) String
  parentId` (nullable). `BE/taxonomy/dto/MoveNodeResponse.java` (NEW record): `TaxonomyNodeResponse
  node`, `int movedNodeCount`, with `from(MoveOutcome)`. (D-3)

**Checkpoint**: T004 passes; build compiles with `-Werror`.

---

## Phase 3: User Story 1 - Move a node with its branch to another parent (Priority: P1) 🎯 MVP

**Goal**: an authorized user moves a node and its whole branch under another node from the page.

**Independent Test**: move "Camisas" from "Ropa de hombre" to "Ropa de mujer"; the tree shows
"Camisas › Camisas formales" under "Ropa de mujer"; IDs, names, descriptions, children and direct
attributes unchanged.

### Tests first

- [X] T009 [P] [US1] `BT/taxonomy/service/TaxonomyServiceTest.java` (nested class or section "move"):
  on the example hierarchy, `move(Camisas, RopaDeMujer, permitted)` → `MoveOutcome` with
  `movedNodeCount` 2, details path [Ropa, Ropa de mujer], level 3; the stored "Camisas" has the new
  parent, same system ID, name, description, `attributeIds`, `createdAt`, and `updatedAt` = clock;
  "Camisas formales" is still its child and was not rewritten (its `updatedAt` unchanged); parent ID
  compared ignoring letter case (`cls…`); without `ATTRIBUTE_CREATE` → `TaxonomyChangeNotPermittedException`
  and nothing changes. (FR-001 to FR-003, FR-015, SC-004)
- [X] T010 [P] [US1] `BT/taxonomy/controller/TaxonomyNodeControllerIntegrationTest.java` (local
  MongoDB): `POST /api/v1/taxonomy-nodes/{id}/move` with header `X-Pim-Permissions: ATTRIBUTE_CREATE`
  and `{"parentId":"…"}` → 200 with `node` (`TaxonomyNode` shape: new `path`, `level`) and
  `movedNodeCount`; a following `GET /api/v1/taxonomy-nodes` shows the new `parentId` and levels for
  the node and its child; without the header → 403 `TAXONOMY_CHANGE_NOT_PERMITTED`; `parentId` of 41
  characters → 400 field `parentId`. (FR-001, FR-015, contract)
- [X] T011 [P] [US1] `FE/api/taxonomyApi.test.ts`: `moveNode('CLS000003', 'CLS000006')` sends
  `POST …/taxonomy-nodes/CLS000003/move` with the permission header, `Content-Type: application/json`
  and body `{"parentId":"CLS000006"}`, returns `{ node, movedNodeCount }`; `moveNode(id, null)` sends
  `{"parentId":null}`; a 409 problem becomes `TaxonomyApiError` kind `conflict` with its `detail`.
  (D-3)
- [X] T012 [P] [US1] `FE/taxonomy/nodeTree.test.ts`: `descendantIds` (all levels; ignores letter case;
  stops on a damaged loop), `pathOf` (names from the top; top-level → []), `moveDestinations(items,
  node)` (excludes the node, its descendants and its current parent; sorted by path; top-level node →
  its siblings at the top are offered, "Ropa" offers none of its branch). (D-6, data-model "Frontend
  display helpers")
- [X] T013 [P] [US1] `FE/taxonomy/MoveNodePanel.test.tsx`: legend «Mover «Camisas»»; «Ubicación actual:
  Ropa › Ropa de hombre»; «Se moverá junto con su nodo descendiente.» (1) and «Se moverá junto con sus
  3 nodos descendientes.» (3), none for a leaf; search `buscar-nodo-destino` by name or system ID ignoring
  accents and case; options show name and «{ruta} · {systemId}», `aria-pressed` on the chosen one plus a
  visible non-color marker; 50 at a time with «Mostrando {m} de {n} nodos.» + «Mostrar más»; «Ningún
  nodo coincide con la búsqueda.»; **Continuar** disabled until a choice; confirmation texts
  «¿Mover «Camisas» a «Ropa de mujer»?», «Nueva ubicación: Ropa › Ropa de mujer › Camisas», «Se moverán
  2 nodos: «Camisas» y 1 descendiente.», the inheritance note; **Cancelar** in the confirmation returns
  to the panel with the choice kept; **Sí, mover nodo** calls `onMove(parentId)`. (FR-013, UI contract)
- [X] T014 [US1] `FE/pages/TaxonomyPage.test.tsx` (fake backend of feature 010 extended with the move
  route): with permission, the node card shows **Mover** between **Editar** and **Eliminar**; full flow
  Camisas → Ropa de mujer → success notification «Nodo «Camisas» movido a «Ropa de mujer». Se movieron 2
  nodos.»; the node stays selected with its new path; the tree shows it under "Ropa de mujer"; a 409
  shows the backend `detail` and keeps the panel open; without permission there is no **Mover**.
  (FR-013 to FR-015)

### Implementation

- [X] T015 [US1] `BE/taxonomy/service/TaxonomyService.java`: add `public MoveOutcome move(String
  systemId, String parentId, AttributePermissions permissions)`: `checkPermission`; null/blank
  `parentId` → top level; under `synchronized (lock)`: load `nodes.findAll()` once, find the node
  (`NodeNotFoundException(systemId, "nodeId")`) and the destination (`NodeNotFoundException(parentId,
  "parentId")`), run the checks of Phase 4 in the data-model order (own branch, already there, name at
  destination), save `node.movedTo(destination, clock.instant())` with `nodes.update`, return
  `new MoveOutcome(details(moved), 1 + descendants.size())`. Private helper `descendantIds(node, all)`:
  breadth-first over a parent → children map with a visited set. Update the class Javadoc (FR list and
  "a move changes only the node's parent", research R-01, R-02). (FR-001 to FR-003, D-1, D-2)
- [X] T016 [US1] `BE/taxonomy/controller/TaxonomyNodeController.java`: `@PostMapping("/{nodeId}/move")
  MoveNodeResponse move(@PathVariable String nodeId, @Valid @RequestBody MoveNodeRequest request,
  HttpServletRequest httpRequest)` calling `taxonomyService.move(…)`; update the class Javadoc contract
  reference (v1.1.0). (FR-001, D-3)
- [X] T017 [US1] `BE/taxonomy/controller/TaxonomyExceptionHandler.java`: map
  `NodeMoveNotAllowedException` → 409, title «Movimiento no válido» (`NODE_MOVE_INTO_OWN_BRANCH`) or
  «Movimiento sin efecto» (`NODE_ALREADY_THERE`), `errors[0]` `{field: parentId, code, message}`; make
  `NodeNameInUseException` use its field (`name` or `parentId`); `NodeNotFoundException` already maps
  its field. Check the `Size` mapping for `parentId` covers the move request. (D-4)
- [X] T018 [P] [US1] `FE/api/taxonomyApi.ts`: type `MoveNodeResult = { node: TaxonomyNode;
  movedNodeCount: number }` and `moveNode(nodeId: string, parentId: string | null):
  Promise<MoveNodeResult>` (`POST ${nodePath(nodeId)}/move`, `change('POST', { parentId })`); update
  the header comment to contract v1.1.0. (D-3)
- [X] T019 [P] [US1] `FE/taxonomy/nodeTree.ts`: add `descendantIds(items, nodeId): Set<string>`,
  `pathOf(items, nodeId): string[]` and `moveDestinations(items, node): TaxonomyNodeSummary[]` (pure,
  display only, IDs compared upper-cased, visited sets). (D-6)
- [X] T020 [US1] `FE/taxonomy/MoveNodePanel.tsx` + `MoveNodePanel.module.css` (NEW): props `node:
  TaxonomyNode`, `items: TaxonomyNodeSummary[]`, `busy: boolean`, `onCancel()`, `onMove(parentId:
  string | null)`; fieldset with legend, current location, branch note, search (`TextField`
  `id="buscar-nodo-destino"`, `type="search"`), destination buttons (`aria-pressed`, check icon when
  chosen), paging of 50, **Cancelar** / **Continuar**; then an inline `Notification variant="warning"`
  confirmation with the UI contract texts, focus moved to its message, **Cancelar** / **Sí, mover nodo**
  (disabled while `busy`). Design tokens only; usable at 768 px without horizontal scroll. Texts exactly
  as `contracts/taxonomy-move-ui.md`. (FR-013, D-6)
- [X] T021 [US1] `FE/pages/TaxonomyPage.tsx`: `Panel`/state for moving (`moving: boolean`), **Mover**
  button (permission only, before **Eliminar**, hidden while moving or confirming delete, like the
  other actions); render `MoveNodePanel` inside the node card; `async function move(parentId)` →
  `moveNode`, on success `show(result.node)`, close the panel, success message (UI contract, «Se
  movieron {n} nodos.» only when `movedNodeCount > 1`), `reloadList()`; on error `setPanelError(messageOf(error))`
  keeping the panel open, except `notFound` for the node itself → close and reload; `resetPanelState`
  also closes the move panel. (FR-013, FR-014)

**Checkpoint**: US1 works end to end (quickstart scenarios 1–2); T009–T014 pass.

---

## Phase 4: User Story 2 - Moves that would break the hierarchy are refused (Priority: P1)

**Goal**: no loops, no no-op moves, no sibling name clashes, no moves to missing nodes; nothing changes
on refusal.

**Independent Test**: move "Ropa de hombre" under "Camisas" → refused, tree unchanged.

### Tests first

- [X] T022 [P] [US2] `BT/taxonomy/service/TaxonomyServiceTest.java`: destination = the node itself, a
  child and a grandchild → `NodeMoveNotAllowedException` `NODE_MOVE_INTO_OWN_BRANCH` with message «No
  puedes mover «Ropa de hombre» dentro de sí mismo ni de uno de sus nodos descendientes.»; destination
  = current parent → `NODE_ALREADY_THERE` ««Camisas» ya está en «Ropa de hombre».»; top-level node to
  top level → ««Ropa» ya está en el primer nivel.»; destination child named " camisas " (case and
  spaces) → `NodeNameInUseException` field `parentId`, «Ya existe un nodo «Camisas» en «Ropa de
  mujer». Cambia el nombre de uno de los dos antes de mover.»; unknown node → `NodeNotFoundException`
  field `nodeId`; unknown destination → field `parentId`. After every refusal `nodes.findAll()` equals
  the state before. (FR-003, FR-005 to FR-007, FR-009, SC-003)
- [X] T023 [P] [US2] `BT/taxonomy/controller/TaxonomyNodeControllerIntegrationTest.java`: each refusal
  above returns the contract status, title, `code`, `field` and Spanish `detail` (409/409/409/404/404);
  stored documents unchanged. (FR-016, contract)
- [X] T024 [P] [US2] `BT/taxonomy/service/TaxonomyServiceConcurrencyTest.java`: with nodes A and B at
  the top level, run "move A under B" and "move B under A" at the same time many times (fresh data each
  round): exactly one succeeds, the other is refused with `NODE_MOVE_INTO_OWN_BRANCH`, and every node's
  chain reaches the top without repeating a node. (SC-003, edge case "Concurrent changes")
- [X] T025 [P] [US2] `FE/taxonomy/MoveNodePanel.test.tsx` + `FE/pages/TaxonomyPage.test.tsx`: the
  panel for "Ropa de hombre" does not offer itself, "Camisas", "Camisas formales" or its parent "Ropa";
  a 404 for the destination shows «No existe un nodo con el ID …» and keeps the panel open; a 404 for
  the node closes the panel and reloads the tree. (FR-005, FR-009, FR-016)

### Implementation

- [X] T026 [US2] `BE/taxonomy/service/TaxonomyService.java`: in `move`, the checks in data-model order:
  destination equals the node or is in `descendantIds` → `NodeMoveNotAllowedException.intoOwnBranch`;
  destination equals the current parent (both empty counts as equal) → `alreadyUnder` /
  `alreadyTopLevel`; `nodes.existsWithName(destinationId, node.name(), node.id())` →
  `NodeNameInUseException.atDestination`. No write before all checks pass. (FR-003, FR-005 to FR-007)
- [X] T027 [US2] Make T022–T025 pass; check `MongoTaxonomyNodeRepository.update` still turns a
  storage-level duplicate (unique index) into `IllegalStateException` and that cannot happen after the
  service check (no code change expected; add a comment if the reason is not obvious). (FR-007, D-2)

**Checkpoint**: quickstart scenarios 7–10 behave as described.

---

## Phase 5: User Story 3 - The moved branch inherits from its new ancestors at once (Priority: P1)

**Goal**: effective attributes, levels and paths of the moved branch follow the new ancestors on the
next read; the rest of the tree is untouched.

**Independent Test**: move "Camisas" under "Calzado"; "Camisas formales" inherits Material de suela
(Calzado) and Largo de manga (Camisas) only.

### Tests first

- [X] T028 [P] [US3] `BT/taxonomy/service/TaxonomyServiceTest.java`: on the example hierarchy with its
  attributes (in-memory repository and attribute fake of feature 010's tests): after moving "Camisas"
  under "Calzado", `find(Camisas)` lists Material de suela (INHERITED, origin Calzado) and Largo de
  manga (DIRECT) only; `find(Camisas formales)` → level 3, path [Calzado, Camisas], inherited Material
  de suela (Calzado) and Largo de manga (Camisas); Color, Material, Marca, Género absent; `find(Ropa de
  hombre)` and `find(Pantalones)` equal their results before the move; with "Género" direct on
  "Camisas", moving it under "Ropa de mujer" lists Género once, DIRECT, `alsoInheritedFrom` Ropa de
  mujer, and `attributeIds` of "Camisas" still contains it. (FR-010 to FR-012, SC-001)
- [X] T029 [P] [US3] `BT/taxonomy/controller/TaxonomyNodeControllerIntegrationTest.java`: after a move
  through the API, `GET /api/v1/taxonomy-nodes/{grandchild}` returns the new `path`, `level` and
  `effectiveAttributes` with origins (stored in MongoDB, read back fresh). (FR-010, D-1)
- [X] T030 [P] [US3] `FE/pages/TaxonomyPage.test.tsx`: after a successful move the attribute table of
  the moved node shows the effective attributes returned by the move response (inherited rows with the
  new origin links); the path line shows the new ancestors as links. (FR-014)

### Implementation

- [X] T031 [US3] No production change expected (research R-05: `EffectiveAttributes` already computes
  from the current chain). If T028–T030 fail, fix only `TaxonomyService.move`/`details` or the page's
  success handling, not the inheritance rules. Record the result in this task. (D-5)

**Checkpoint**: quickstart scenarios 3–6 behave as described.

---

## Phase 6: User Story 4 - Move a node to the top level (Priority: P2)

**Goal**: a node becomes a top-level node with its branch and inherits nothing.

**Independent Test**: move "Ropa de mujer" to the top level; it appears beside "Ropa" and "Calzado" at
level 1 with no inherited attributes.

### Tests first

- [X] T032 [P] [US4] `BT/taxonomy/service/TaxonomyServiceTest.java` and
  `BT/taxonomy/controller/TaxonomyNodeControllerIntegrationTest.java`: `move(RopaDeMujer, null)` and
  `{"parentId":null}` and `{}` → top level, level 1, no INHERITED entries, its children's levels
  decrease by 1; a top-level node named like it → `NODE_NAME_IN_USE` «Ya existe un nodo «…» en el primer
  nivel. …». (FR-004, US4)
- [X] T033 [P] [US4] `FE/taxonomy/MoveNodePanel.test.tsx` + `FE/pages/TaxonomyPage.test.tsx`: «Primer
  nivel (sin nodo padre)» is the first option for a non-top-level node and absent for a top-level node;
  choosing it shows «¿Mover «Ropa de mujer» al primer nivel?» and «Nueva ubicación: Ropa de mujer
  (primer nivel)»; success «Nodo «Ropa de mujer» movido al primer nivel.»; `moveNode` called with
  `null`. (FR-004, UI contract)

### Implementation

- [X] T034 [US4] Make T032–T033 pass in `BE/taxonomy/service/TaxonomyService.java` (null/blank →
  top level, `existsWithName(null, …)`) and `FE/taxonomy/MoveNodePanel.tsx` (top-level option, texts).
  (FR-004)

**Checkpoint**: quickstart scenario 11 behaves as described.

---

## Phase 7: Polish and validation

- [X] T035 Run `cd backend; .\gradlew.bat spotlessApply; .\gradlew.bat clean build` and `cd frontend;
  npm run format; npm run lint; npm run format:check; npm test; npm run build`; all pass; record test
  counts against T001. (SC-006, constitution V, VI)
- [X] T036 [P] Scope check: `git diff --stat main -- backend/src/main/java` lists only
  `taxonomy/` files; no change under `attribute/`, `attributegroup/`, `lov/`, `identifier/`, `product/`,
  `frontend/src/navigation.ts` or `frontend/src/components/`. (FR-018)
- [X] T037 [P] `README.md` (root): add "011 | Move taxonomy nodes with their branch" to the "Features
  built so far" table. (documentation)
- [X] T038 Run `specs/011-taxonomy-node-relocation/quickstart.md` automated checks and scenarios 1–13
  that do not need a browser (API calls); report pass/fail per scenario in the implementation summary.
  (SC-001 to SC-004)
- [X] T039 Owner browser check: the project owner runs quickstart scenarios 1–7, 11, 12 and 14 in the
  browser at desktop and 768 px widths and confirms. (SC-005, FR-013) Confirmed by the project owner
  on 2026-11-19 ("it looks good to me").

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)**: T001 first; T002 and T003 in parallel after it.
- **Foundational (2)**: after Setup; blocks every story.
- **US1 (3)**: after Foundational. MVP.
- **US2 (4)**: after US1 (adds the checks inside `move` and uses its endpoint and panel).
- **US3 (5)**: after US1 (needs a working move); independent of US2.
- **US4 (6)**: after US1; independent of US2 and US3.
- **Polish (7)**: last.

### Task dependencies

- T004 → T005. T006, T007, T008 in parallel with T005.
- US1: tests T009–T014 first. Backend T015 → T016 → T017. Frontend T018, T019 in parallel → T020 →
  T021.
- US2: T022–T025 first → T026 → T027.
- US3: T028–T030 first → T031.
- US4: T032–T033 first → T034.
- T035 → T038 → T039; T036 and T037 any time after T034.

### Parallel opportunities

- Setup: T002, T003.
- Foundational: T006, T007, T008 (and T004 before T005).
- US1 tests: T009, T010, T011, T012, T013; implementation T018 and T019 alongside the backend tasks.
- US2 tests: T022–T025. US3 tests: T028–T030. US4 tests: T032, T033.
- US3 and US4 can be worked on at the same time after US1.

## Parallel Example: User Story 1

```text
Task: "T009 TaxonomyServiceTest – move success and preserved data"
Task: "T010 TaxonomyNodeControllerIntegrationTest – POST /move"
Task: "T011 taxonomyApi.test.ts – moveNode"
Task: "T012 nodeTree.test.ts – descendantIds, pathOf, moveDestinations"
Task: "T013 MoveNodePanel.test.tsx – panel and confirmation texts"
```

---

## Implementation Strategy

1. **MVP**: Setup → Foundational → US1. Validate quickstart scenarios 1–2.
2. **Safety**: US2. Validate scenarios 7–10.
3. **Inheritance**: US3. Validate scenarios 3–6 (the ERF rule).
4. **Top level**: US4. Validate scenario 11.
5. **Polish**: full suites, scope check, README, quickstart, owner browser check.

Each step leaves lint, format check, tests and build green (constitution X). Commits only when the
user asks.

## Notes

- Requirement traceability:

  | Requirements | Tasks |
  |---|---|
  | FR-001, FR-002 | T004, T005, T009, T010, T015, T016 |
  | FR-003 | T009, T022, T026 |
  | FR-004 | T004, T032–T034 |
  | FR-005 to FR-007 | T006, T007, T022–T026 |
  | FR-008 | no check by design (research R-06); covered by T028 (level 3 after move) |
  | FR-009 | T022, T023, T025 |
  | FR-010 to FR-012 | T028–T031 |
  | FR-013, FR-014 | T013, T014, T020, T021, T030 |
  | FR-015 | T009, T010, T014 |
  | FR-016 | T023, T025 |
  | FR-017 | T002, T003 |
  | FR-018 | T036 |
  | SC-001 to SC-006 | T001, T009, T022, T024, T028, T035, T038, T039 |

- Starting test counts (T001, 2026-11-19): backend `clean build` passes with **517** tests; frontend
  **303** tests in 28 files, lint and build pass.
- Final counts (T035): backend **535** tests (+18: 2 in `TaxonomyNodeTest`, 12 in the new
  `TaxonomyServiceMoveTest`, 1 in `TaxonomyServiceConcurrencyTest`, 3 in
  `TaxonomyNodeControllerIntegrationTest`); frontend **324** tests in 29 files (+21: 2 API, 5
  `nodeTree`, 9 `MoveNodePanel`, 5 page); lint, format check and build pass.
- Where the service tests went (T009, T022, T028, T032): a new class
  `BT/taxonomy/service/TaxonomyServiceMoveTest.java` with the same setup as `TaxonomyServiceTest`, built
  on the spec's example hierarchy, instead of a section of `TaxonomyServiceTest.java` (420 lines
  already).
- T002: both OpenAPI files parse as YAML; Prettier reports style differences only, as it already did
  for feature 010's contract on `main`, so they were not reformatted.
- T027: no code change; `MongoTaxonomyNodeRepository.update` still turns a unique-index duplicate into
  `IllegalStateException`, which the service check prevents.
- T031: no production change needed; `EffectiveAttributes` was not touched (research R-05 confirmed by
  T028–T030).
- Contract adjustments made during implementation: the UI contract now gives the exact singular/plural
  texts of the branch note and count; after a successful move the success notification is announced
  through its `role="status"` (as every other change on the page) instead of receiving focus.
- T038 (2026-11-19), backend on port 8091 with a separate database `pim-quickstart-011b` (the
  development database `pim` was not used): API scenarios 1–6 and 8–12 and an unknown destination
  (FR-009) all **PASS**. Scenario 13 (MongoDB stopped) not run by hand: stopping the shared local
  MongoDB was avoided; a move is one document save, and the existing
  `TaxonomyStorageFailureIntegrationTest` covers the 503 path of the taxonomy API. Scenarios 7 and 14
  are browser checks (T039). The databases `pim-quickstart-011` and `pim-quickstart-011b` can be
  dropped.
