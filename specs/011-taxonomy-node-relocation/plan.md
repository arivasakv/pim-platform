# Implementation Plan: Taxonomy Node Relocation (Reubicación de Nodos)

**Branch**: `011-taxonomy-node-relocation` | **Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-taxonomy-node-relocation/spec.md` (approved 2026-11-19)

**Status**: **Approved** by the project owner on 2026-11-19, including decisions D-1 to D-7 (constitution X).

## Summary

Add a **move** action to the existing taxonomy domain (`com.pim.backend.taxonomy`, feature 010). A move
changes one stored value, the moved node's `parentId`; its descendants follow because levels, paths and
effective attributes are already derived from the parent chain on every read. `TaxonomyService.move`
checks, under the existing taxonomy lock, that the node and destination exist, that the destination is
not the node or one of its descendants, that it is not the current parent, and that no sibling at the
destination has the same name; then it saves the node and returns its detail with the number of moved
nodes. API: `POST /api/v1/taxonomy-nodes/{nodeId}/move` with `{ "parentId": … | null }`. The
"Taxonomía" page gets a **Mover** button that opens an inline, searchable destination panel and a
confirmation showing the new path and how many nodes move. No new collection, dependency, permission or
domain.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2 (Gradle, `-Werror`, Spotless); TypeScript strict,
React 19, Vite

**Primary Dependencies**: existing only (`MongoTemplate`, feature 010 taxonomy domain, design-system
components); **no new dependency**

**Storage**: MongoDB collection `taxonomy_nodes` (feature 010); no new field, index or collection;
one-document update per move (research R-01)

**Testing**: JUnit 5 unit tests (model, service with the in-memory repository
`InMemoryTaxonomyNodeRepository`), Spring integration tests against the local MongoDB (controller,
repository), concurrency test (as feature 010's `TaxonomyServiceConcurrencyTest`); Vitest + Testing
Library with mocked `fetch` (API client, `nodeTree` helpers, page)

**Target Platform**: backend on the JVM; frontend in desktop and tablet browsers (768 px and up)

**Project Type**: web application (backend + frontend)

**Performance Goals**: a move of a 200-node branch in one request (SC-002); move = one `findAll` (≤ 5,000
small documents, as every taxonomy read) + one document save; destination search filters the already
loaded flat list in the browser

**Constraints**:
- Taxonomy lock and lock order unchanged: group → taxonomy → attribute (feature 010 research R-05).
- Business rules in service/model; controller maps HTTP only (constitution III); the frontend only
  hides invalid choices (FR-016).
- RFC 9457 problems through `common.ApiProblems`, `errors[]` `{field, code, message}`, Spanish messages.
- Feature 010's `PUT` keeps its meaning (rename only); its contract becomes v1.1.0 with the new
  operation only.
- ESLint `set-state-in-effect`; Prettier; no jest-dom.

**Scale/Scope**: backend: 1 service method, 1 model method, 2 exceptions, 2 error codes, 2 DTOs, 1
controller endpoint, 3 handler mappings; frontend: 1 API function, 3 pure helpers, 1 page panel and
confirmation; docs: 2 contracts, feature 010 notes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec approved 2026-11-19 with clarifications; every task will cite FR/US |
| II | Technology stack | PASS | Existing Java/Spring/MongoDB and React/TypeScript; no new library |
| III | Modular monolith | PASS | Change stays inside `taxonomy`; no new cross-domain call; rules in the service; frontend hides choices only (FR-016) |
| IV | Business scope | PASS | ERF 1.1 §3.3 «Reubicación de Nodos» (tier 2, official `ERF - HUs` copy) |
| V | Code quality | PASS | Small pure helpers (`TaxonomyNode.movedTo`, `nodeTree` helpers) with unit tests; Spotless, ESLint, Prettier |
| VI | Testing | PASS | Unit, service, controller integration, concurrency and frontend tests; tests for each refusal and for inheritance after a move; all existing suites stay green (SC-006) |
| VII | API design | PASS | New versioned operation documented in `contracts/taxonomy-move.openapi.yaml` before code and merged into feature 010's contract as v1.1.0 (D-7) |
| VIII | Security | PASS | Development permission header reused (`ATTRIBUTE_CREATE`); backend validation authoritative; no secrets |
| IX | Development scope | PASS | Taxonomy nodes already allowed in MongoDB (constitution 2.5.0); no new data; no integrations, products or AI |
| X | AI governance | PASS | D-1 to D-7 approved by the project owner on 2026-11-19 |
| XI | Sources | PASS | ERF §3.3 cited (tier 2); Omega and P360 behavior as tier 3 evidence (OEB-001, OEB-002); reference proposals not used as requirements |
| XII | Package structure | PASS | Only `com.pim.backend.taxonomy.{controller,service,model,dto}`; nothing in the root package |

**Gate result**: PASS.
**Post-design re-check**: PASS (no new dependency, storage or domain introduced by the design).

## Recorded Decisions (approved 2026-11-19)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | What a move writes | Only the moved node: `parentId` and `updatedAt`. Descendants follow through their parent links; levels, paths and effective attributes are read-time values (no recalculation step). One-document update = atomic | R-01 |
| D-2 | Checks and concurrency | `TaxonomyService.move` under the existing taxonomy lock: exists → destination exists → not own branch → not current parent → name free at destination → save. Descendants found from one `findAll` with a visited set. Unique index stays as backstop | R-02 |
| D-3 | API | `POST /api/v1/taxonomy-nodes/{nodeId}/move`, body `{ "parentId": string \| null }` (`null`/absent = top level, as in create). 200 `{ node, movedNodeCount }`. `PUT` (rename) unchanged | R-03 |
| D-4 | Errors | New 409 codes `NODE_MOVE_INTO_OWN_BRANCH`, `NODE_ALREADY_THERE`; reused 409 `NODE_NAME_IN_USE` (field `parentId`, message naming the destination), 404 `NODE_NOT_FOUND` (field `nodeId` or `parentId`), 403 `TAXONOMY_CHANGE_NOT_PERMITTED` | R-04 |
| D-5 | Inheritance and depth | No change to `EffectiveAttributes`; FR-011 is feature 010's existing rule; no depth check (clarification) | R-05, R-06 |
| D-6 | UI | **Mover** in the node card actions → inline panel: current location, searchable destination list (buttons with `aria-pressed`, path + ID, 50 at a time, "Primer nivel (sin nodo padre)" first), invalid destinations hidden → **Continuar** → inline warning confirmation with new path, node count and inheritance note → **Sí, mover nodo**. Candidates, paths and counts computed in the browser from the loaded list (display only) | R-07 |
| D-7 | Feature 010 documents | Merge the operation into `specs/010-…/contracts/taxonomy.openapi.yaml` (v1.1.0); dated notes in 010 spec FR-005 and Edge Cases «Loops», and in 010 `taxonomy-ui.md` (pointer to this UI contract) | R-08 |

## Project Structure

### Documentation (this feature)

```text
specs/011-taxonomy-node-relocation/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/taxonomy-move.openapi.yaml   # new operation (merged into 010's contract as v1.1.0)
├── contracts/taxonomy-move-ui.md          # exact Spanish texts and UI behavior
├── checklists/requirements.md
└── tasks.md                               # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/taxonomy/
├── model/TaxonomyNode.java                     # + movedTo(newParentId, at); parentId doc
├── model/TaxonomyErrorCode.java                # + NODE_MOVE_INTO_OWN_BRANCH, NODE_ALREADY_THERE
├── model/NodeMoveNotAllowedException.java      # NEW: own branch / already there (code + field parentId)
├── model/NodeNameInUseException.java           # + factory/message for the destination case
├── model/MoveOutcome.java                      # NEW: NodeDetails + movedNodeCount
├── service/TaxonomyService.java                # + move(systemId, parentId, permissions); descendant walk
├── dto/MoveNodeRequest.java                    # NEW: parentId (nullable, @Size max 40)
├── dto/MoveNodeResponse.java                   # NEW: node + movedNodeCount
├── controller/TaxonomyNodeController.java      # + POST /{nodeId}/move
└── controller/TaxonomyExceptionHandler.java    # + 409 mapping for NodeMoveNotAllowedException; name-in-use field
backend/src/test/java/com/pim/backend/taxonomy/
├── model/TaxonomyNodeTest.java                 # + movedTo
├── service/TaxonomyServiceTest.java            # + move rules and inheritance after a move
├── service/TaxonomyServiceConcurrencyTest.java # + crossing moves never make a loop
└── controller/TaxonomyNodeControllerIntegrationTest.java  # + endpoint, problems, persistence

frontend/src/api/taxonomyApi.ts (+ test)        # + moveNode(nodeId, parentId) → MoveNodeResult
frontend/src/taxonomy/nodeTree.ts (+ test)      # + descendantIds, pathOf, moveDestinations
frontend/src/taxonomy/MoveNodePanel.tsx (+ .module.css, test)  # NEW: panel + confirmation
frontend/src/pages/TaxonomyPage.tsx (+ test)    # + Mover action, panel wiring, success handling

specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml   # v1.1.0 (D-7)
specs/010-taxonomy-attribute-inheritance/contracts/taxonomy-ui.md          # pointer (D-7)
specs/010-taxonomy-attribute-inheritance/spec.md                           # dated notes (D-7)
```

No change to attributes, attribute groups, lists of values, identifiers, product search, navigation,
design-system components or storage.

**Structure Decision**: all backend work stays in the existing `taxonomy` domain's subpackages
(constitution XII); the frontend adds one component in `src/taxonomy/` and extends the page, the API
client and the pure tree helpers.

## Implementation Order (for /speckit-tasks)

1. Contracts first: merge the operation into feature 010's contract (v1.1.0) and add the 010 notes (D-7).
2. Model: `TaxonomyNode.movedTo`, error codes, exceptions, `MoveOutcome` (tests first).
3. US1 + US2 backend: `TaxonomyService.move` with all checks (tests first: success, preserved data,
   own branch, no-op, name clash, unknown node/destination, permission); controller endpoint and
   problem mappings (integration tests).
4. US3: inheritance after a move (service tests on the example hierarchy: moved node, grandchild, old
   parent, sibling; FR-011 case).
5. US4: top level (service + controller tests).
6. Concurrency: crossing moves (A under B and B under A at once) leave a tree without loops.
7. Frontend: `moveNode` client, `nodeTree` helpers, `MoveNodePanel`, page wiring (tests first).
8. Polish: full suites, lint/format, build, quickstart (owner browser check).

## Complexity Tracking

No constitution violations. Design choices with a simpler alternative:

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| Separate `/move` action (D-3) | Keeps 010's `PUT` rename-only, so existing clients cannot move nodes by accident | Adding `parentId` to `PUT` changes 010's contract meaning and makes "absent" ambiguous |
| Destination list as buttons (D-6) | No radio/combobox in the design system; same pattern as the tree | A `<select>` cannot search thousands of nodes (SC-005) |
