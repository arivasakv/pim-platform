# Implementation Plan: Attribute Inheritance through the Product Taxonomy

**Branch**: `010-taxonomy-attribute-inheritance` | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-taxonomy-attribute-inheritance/spec.md` (approved 2026-11-12)

**Status**: **Approved** by the project owner on 2026-11-12, including decisions D-1 to D-10 (constitution
X). D-2: constitution amendment 2.5.0 made in task T001.

## Summary

Add a new backend domain **`com.pim.backend.taxonomy`** (controller, service, repository, model, dto)
holding the product hierarchy: nodes with a parent, a name, a description and the system IDs of their
directly assigned attributes, stored in a MongoDB collection `taxonomy_nodes`. Node IDs come from
feature 003 (`CLASSIFICATION`, `CLS…`). A pure domain class, **`EffectiveAttributes`**, walks a node's
ancestor chain and returns each attribute once with its assignment type (DIRECT / INHERITED), its
origin (the node or nearest ancestor), and any ancestor that also has a direct attribute. It is computed
on every read from the current tree, so ancestor changes show at once. A REST API
`/api/v1/taxonomy-nodes` exposes the tree, node detail with effective attributes, node CRUD and
assignments. Attribute deletion (feature 007) is refused for attributes assigned to nodes, through a
new attribute-owned interface implemented by the taxonomy domain (the pattern feature 007 uses for
groups). The frontend adds a "Taxonomía" page: a tree on the left and, on the right, the node detail
with an attribute table whose rows carry a **Directo** or **Heredado** badge and the origin node.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2 (Gradle, `-Werror`, Spotless); TypeScript strict,
React 19, Vite

**Primary Dependencies**: existing only (`spring-boot-starter-data-mongodb` with `MongoTemplate`,
feature 003 `IdentifierService`, feature 004/007 `AttributeService`, design-system components);
**no new dependency**

**Storage**: MongoDB collection `taxonomy_nodes` (needs constitution IX amendment, D-2)

**Testing**: JUnit 5 + Spring Boot tests (unit tests with in-memory repositories, integration tests
against the local MongoDB as features 005/006 do); Vitest + Testing Library with mocked `fetch`

**Target Platform**: backend on the JVM; frontend in desktop and tablet browsers (768 px and up)

**Project Type**: web application (backend + frontend)

**Performance Goals**: node detail with effective attributes < 1 s with 500 nodes and 2,000 attributes
(SC-005): one read of all nodes (small documents) plus one attribute lookup per request, computed in
memory

**Constraints**:
- Lock order (feature 007 research R-06, extended): **group → taxonomy → attribute**; list of values →
  attribute. The taxonomy service never calls the group service; attribute deletion runs the taxonomy
  check inside the group check (D-7).
- Business rules in the service and model; controllers map HTTP only (constitution III).
- RFC 9457 problems through `common.ApiProblems` with `errors[]` entries `{field, code, message}`;
  Spanish messages.
- ESLint `set-state-in-effect` (state set only in callbacks); Prettier; no jest-dom.

**Scale/Scope**: 1 backend domain (~20 classes), 1 change in `attribute` (deletion check, 1 interface,
1 exception, 1 error code), 1 OpenAPI contract, 1 frontend page with 3 components, 1 API client, 1
navigation entry; up to ~5,000 nodes (spec A-007).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec and D-1 to D-10 approved by the project owner on 2026-11-12 |
| II | Technology stack | PASS | Java/Spring/MongoDB, React/TypeScript; no new library |
| III | Modular monolith | PASS | New domain `taxonomy`; it uses `AttributeService` and `IdentifierService` (public services); attribute deletion depends on an attribute-owned interface, implemented by `taxonomy` (as with groups); inheritance computed in the taxonomy service/model, not in the frontend |
| IV | Business scope | PASS | Product hierarchy and attribute inheritance are ERF §3.3 business scope; no products, templates or integrations |
| V | Code quality | PASS | Pure `EffectiveAttributes` with unit tests; Spotless, ESLint, Prettier |
| VI | Testing | PASS | Unit tests (model, service, inheritance), repository and controller integration tests, contract-shaped assertions, frontend component and page tests; existing suites stay green |
| VII | API design | PASS | New versioned resource `/api/v1/taxonomy-nodes` documented in `contracts/taxonomy.openapi.yaml` before code; attributes contract gains one documented 409 code (v1.3.0) |
| VIII | Security | PASS | Development permission header reused; no secrets; backend validation authoritative |
| IX | Development scope | PASS | Constitution amended to 2.5.0 on 2026-11-12 (T001): taxonomy nodes and their direct attribute assignments are attribute administration data; ADR 0003 amended |
| X | AI governance | PASS | D-1 to D-10 approved by the project owner on 2026-11-12 |
| XI | Sources | PASS | ERF §3.3 cited (tier 2); missing taxonomy recorded; open rules as OQ-001 to OQ-008 |
| XII | Package structure | PASS | `com.pim.backend.taxonomy.{controller,service,repository,model,dto}`; nothing in the root package |

**Gate result**: PASS (IX amended to 2.5.0 in T001).
**Post-design re-check**: PASS.

## Recorded Decisions (approved 2026-11-12)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | No taxonomy exists | This feature adds the **minimum product hierarchy** (nodes; create, rename, delete when empty; tree view) as part of the same feature, in a new domain `taxonomy`. Not a parallel mechanism: it is the first one, and it uses feature 003's reserved `CLASSIFICATION` type | R-01 |
| D-2 | Storage | MongoDB collection `taxonomy_nodes`, after a **constitution amendment 2.5.0** (IX: add taxonomy nodes and their attribute assignments to attribute administration data) and an ADR 0003 amendment. Alternative: in-memory (lost on restart) | R-02 |
| D-3 | Node model | One document per node: `_id` = system ID upper-cased, `systemId`, `name`, `nameKey`, `parentId` (null at top), `description`, `attributeIds` (direct, in order), `createdAt`, `updatedAt`; unique index on (`parentId`, `nameKey`); index on `attributeIds` | R-03 |
| D-4 | Inheritance computation | **On read**, in the service: load all nodes (one query), walk the chain from the node to the top, build the effective list with a pure `EffectiveAttributes` class; attribute names and types from `AttributeService` (current data). No stored effective view (reference DEC-PLT-04's precomputation is not needed at this volume) | R-04 |
| D-5 | Duplicates and origin | Each attribute once; direct wins; inherited origin = nearest ancestor; a direct attribute also on an ancestor carries `alsoInheritedFrom` (nearest such ancestor). Redundant direct assignment allowed (OQ-004) | R-04 |
| D-6 | IDs and names | Generated `CLS…` IDs via `IdentifierService.assign(CLASSIFICATION, null, {}, …)`; no manual IDs in this feature; names unique among siblings ignoring case/spaces; 80/500 character limits | R-03 |
| D-7 | Attribute deletion | New attribute-domain interface `TaxonomyAssignmentDirectory.runIfOnNoNode(attributeId, action)` implemented by `taxonomy` (`ObjectProvider` to avoid a bean cycle). `AttributeService.delete` runs it inside the group check; refusal 409 `ATTRIBUTE_ON_TAXONOMY_NODES` with a `nodes` property. **Changes feature 007's delete** (additive) | R-05 |
| D-8 | Permissions | Same development permission `ATTRIBUTE_CREATE` and header as features 004–007 (reused `AttributePermissions`); reading free | R-06 |
| D-9 | API | `GET/POST /api/v1/taxonomy-nodes`; `GET/PUT/DELETE /api/v1/taxonomy-nodes/{id}`; `POST /api/v1/taxonomy-nodes/{id}/attributes`; `DELETE /api/v1/taxonomy-nodes/{id}/attributes/{attributeId}`. The list returns a flat array (the client builds the tree); the detail returns path, children and effective attributes | R-07 |
| D-10 | UI | Page **"Taxonomía"** at `#/taxonomia`, nav entry after "Grupo de Atributos". Left: card "Jerarquía de productos" with an expandable tree (name + direct count), "Nuevo nodo de primer nivel". Right: node card (path as links, ID, level, description, "Agregar nodo hijo", "Editar", "Eliminar"), "Atributos del nodo" summary and table (Atributo · Tipo de dato · Asignación badge · Origen link · Quitar for direct rows), searchable picker (50 at a time). Badges: `StatusBadge` **info "Directo"**, **neutral "Heredado"** | R-08 |

## Project Structure

### Documentation (this feature)

```text
specs/010-taxonomy-attribute-inheritance/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/taxonomy.openapi.yaml     # new API
├── contracts/taxonomy-ui.md            # exact Spanish texts and UI behavior
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/taxonomy/
├── controller/  TaxonomyNodeController, TaxonomyExceptionHandler, TaxonomyPermissionsReader
├── service/     TaxonomyService, TaxonomyAssignmentDirectoryAdapter
├── repository/  TaxonomyNodeRepository, MongoTaxonomyNodeRepository, TaxonomyNodeDocument
├── model/       TaxonomyNode, NodeName, EffectiveAttributes, EffectiveAttribute, AssignmentType,
│                NodeReference, NodeDetails, AddAttributesOutcome, TaxonomyErrorCode,
│                NodeNotFoundException, NodeNameInUseException, NodeNotEmptyException,
│                InvalidNodeException, UnknownAttributeException, AttributeNotDirectException,
│                TaxonomyChangeNotPermittedException
└── dto/         TaxonomyNodeRequest, TaxonomyNodeSummaryResponse, TaxonomyNodeCollectionResponse,
                 TaxonomyNodeResponse, EffectiveAttributeResponse, NodeReferenceResponse,
                 AddAttributesRequest, AddAttributesResponse
backend/src/main/java/com/pim/backend/attribute/
├── repository/TaxonomyAssignmentDirectory.java          # NEW interface (attribute-owned)
├── model/AttributeOnTaxonomyNodesException.java, TaxonomyNodeReference.java   # NEW
├── model/AttributeErrorCode.java                        # + ATTRIBUTE_ON_TAXONOMY_NODES
├── service/AttributeService.java                        # delete: taxonomy check inside group check
└── controller/AttributeExceptionHandler.java            # 409 with `nodes`
backend/src/test/java/com/pim/backend/taxonomy/...       # unit + integration tests (mirrors attributegroup)
backend/src/test/java/com/pim/backend/attribute/...      # deletion refusal tests

frontend/src/api/taxonomyApi.ts (+ .test.ts)             # NEW client
frontend/src/taxonomy/TaxonomyTree.tsx (+ .module.css, .test.tsx)          # NEW
frontend/src/taxonomy/NodeAttributesTable.tsx (+ .module.css, .test.tsx)   # NEW
frontend/src/taxonomy/nodeTree.ts (+ .test.ts)           # NEW pure: flat list → tree (not taxonomyTree.ts: same name as TaxonomyTree.tsx on a case-insensitive disk)
frontend/src/pages/TaxonomyPage.tsx (+ .module.css, .test.tsx)             # NEW page
frontend/src/navigation.ts, App.tsx (+ tests)            # nav entry and route

specs/004-attribute-creation/contracts/attributes.openapi.yaml   # v1.3.0: 409 ATTRIBUTE_ON_TAXONOMY_NODES
docs/decisions/0003-mongodb-attribute-administration.md          # amended (D-2)
.specify/memory/constitution.md                                   # 2.5.0 via /speckit-constitution (D-2)
docs/architecture/… / README                                       # nav/docs mention of the new page, if listed there
```

No change to attribute groups, lists of values, product search, the attribute form or preview, or
the design-system components.

**Structure Decision**: one new domain following `attributegroup`'s structure; the only cross-domain
change is the attribute deletion check, done through an attribute-owned interface (constitution III,
XII).

## Implementation Order (for /speckit-tasks)

0. Governance: constitution 2.5.0 + ADR 0003 amendment (after approval).
1. Contracts first (taxonomy OpenAPI, attributes v1.3.0).
2. Foundation: model (`NodeName`, `TaxonomyNode`, `EffectiveAttributes`) with unit tests; repository
   (in-memory test double + Mongo) with tests; service skeleton with ID assignment and lock.
3. US1: node CRUD (service, controller, problems) + tree page.
4. US2: assignments (service, controller) + picker and "Quitar".
5. US3: effective attributes in the detail + table with badges, origin links, summaries.
6. US4: propagation tests across levels; attribute deletion refusal (attribute domain + frontend
   message); concurrency test.
7. Polish: full suites, lint/format, clean build, docs, quickstart (owner).

## Complexity Tracking

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| New domain and hierarchy (D-1) | No taxonomy exists; inheritance cannot work without nodes | Inheritance over attribute groups would confuse groups with taxonomy, which the request forbids |
| Constitution amendment (D-2) | Nodes must survive restarts and reference persisted attributes | In-memory nodes would be lost on restart while attributes persist, leaving the feature unusable (ADR 0003's reasoning) |
| Read-time computation (D-4) | Always current, no consistency risk | A stored effective view needs updates on every ancestor change and attribute rename |
