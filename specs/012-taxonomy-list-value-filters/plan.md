# Implementation Plan: List-Value Filters per Taxonomy Level (Filtros contextuales por nivel)

**Branch**: `012-taxonomy-list-value-filters` | **Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-taxonomy-list-value-filters/spec.md` (approved 2026-11-19)

**Status**: **Approved** by the project owner on 2026-11-19, including decisions D-1 to D-9 (constitution X). D-1: constitution amendment 2.6.0 made in task T001.

## Summary

Let administrators restrict, on a taxonomy node, which values of a list-of-values attribute are allowed.
Filters are stored in a new MongoDB collection `taxonomy_value_filters` owned by the taxonomy domain (one
document per node and attribute, holding the included value IDs), after a constitution amendment
(2.6.0). The values allowed at a node are computed on every read: the list's values included in every
filter on the node's chain of ancestors (pure class `AllowedValues`), so inheritance, later narrowing
above and moves (feature 011) need no recalculation step. Saving a filter checks that the attribute is a
list that applies to the node and that every value is allowed above (restrictive overwrite). The API adds
`listAttributes` to the node detail and `GET/PUT/DELETE /api/v1/taxonomy-nodes/{nodeId}/value-filters/{attributeId}`.
The "Taxonomía" node card gets a section "Listas de valores en este nodo" with a two-column filter panel
(available / included values) and a field preview limited to the allowed values.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2 (Gradle, `-Werror`, Spotless); TypeScript strict,
React 19, Vite

**Primary Dependencies**: existing only (`MongoTemplate`; taxonomy, attribute and lov domain services;
design-system components; feature 008's preview helpers); **no new dependency**

**Storage**: MongoDB, new collection `taxonomy_value_filters` (needs constitution 2.6.0, D-1); feature
010's `taxonomy_nodes` unchanged

**Testing**: JUnit 5 unit tests (`AllowedValues`, `ValueFilter`, service with in-memory repositories),
Spring integration tests against the local MongoDB (repository, controller), concurrency test; Vitest +
Testing Library with mocked `fetch` (API client, panel, preview, page)

**Target Platform**: backend on the JVM; frontend in desktop and tablet browsers (768 px and up)

**Project Type**: web application (backend + frontend)

**Performance Goals**: filter dialog with a 50,000-value list < 2 s (SC-005): one list read, one filter
query for the chain, set intersections in memory, one JSON response (~3 MB worst case); node detail adds
one list read per list attribute and one filter query

**Constraints**:
- Taxonomy lock and lock order unchanged (group → taxonomy → attribute); list values read through the
  lock-free `ListOfValuesLookup` (research R-04), so no new lock order.
- Business rules in the taxonomy service and model; controller maps HTTP; the frontend displays what the
  backend returns (FR-019).
- RFC 9457 problems through `common.ApiProblems`, Spanish messages; taxonomy contract v1.2.0, additive.
- ESLint `set-state-in-effect`; Prettier; no jest-dom.

**Scale/Scope**: backend: 1 collection + repository, ~6 model classes, 4 exceptions/codes, 3 endpoints, 1
detail field, 1 lookup method in `lov`; frontend: 1 API module section, 2 components, page section;
docs: constitution 2.6.0, ADR 0003, 2 contracts, feature 010 notes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec approved 2026-11-19 with clarifications; tasks will cite FR/US |
| II | Technology stack | PASS | Existing stack; no new library |
| III | Modular monolith | PASS | Filters owned by `taxonomy`; it uses `AttributeService` (public) and the lov domain's public `ListOfValuesLookup` (one additive read method); rules in the taxonomy service/model, not in the frontend |
| IV | Business scope | PASS | ERF 1.1 §3.2.3, §3.3; HU2.3, HU3 (tier 2) |
| V | Code quality | PASS | Pure `AllowedValues` with unit tests; Spotless, ESLint, Prettier |
| VI | Testing | PASS | Unit, service, repository and controller integration, concurrency, frontend component and page tests; existing suites stay green (SC-006) |
| VII | API design | PASS | Contract written first (`contracts/taxonomy-value-filters.openapi.yaml`), merged into the taxonomy contract as v1.2.0, additive |
| VIII | Security | PASS | Development permission reused; backend validation authoritative |
| IX | Development scope | **PASS after T001** | Amendment 2.6.0 approved by the owner on 2026-11-19 (spec DEP-02); made before any storage code |
| X | AI governance | PASS | D-1 to D-9 approved by the project owner on 2026-11-19 |
| XI | Sources | PASS | ERF and HU cited (tier 2); open points recorded (OQ-002, OQ-003) |
| XII | Package structure | PASS | `taxonomy.{controller,service,repository,model,dto}`; one method in `lov.service`; nothing in the root package |

**Gate result**: PASS (IX satisfied by the 2.6.0 amendment in T001).
**Post-design re-check**: PASS (one new collection as approved; no new dependency or domain).

## Recorded Decisions (approved 2026-11-19)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | Governance | Constitution **2.6.0** (IX: «list-value filters on taxonomy nodes» added to attribute administration data) and ADR 0003 amended, via `/speckit-constitution`, first task | R-01 |
| D-2 | Storage | New collection **`taxonomy_value_filters`**: `_id` `{nodeId}:{attributeId}`, `nodeId`, `attributeId`, `valueIds` (1–50,000, list order), `createdAt`, `updatedAt`; unique (`nodeId`, `attributeId`). Not embedded in nodes (16 MB limit, node schema unchanged) | R-02 |
| D-3 | Allowed values | Computed **on read** by a pure `AllowedValues` from the chain, the list values and the chain's filters: intersection of all filters (FR-008); `availableAbove`, `excludedAbove`, `involved`; IDs compared ignoring case; IDs no longer in the list ignored | R-03 |
| D-4 | List values | New lock-free `ListOfValuesLookup.values(listId)` in the lov domain; no new lock order | R-04 |
| D-5 | Save rules and errors | Order: permission → 1–50,000 IDs → node → attribute → applies → is list → IDs in list → IDs allowed above → upsert. New codes `INVALID_FILTER_VALUES` (400), `ATTRIBUTE_NOT_LIST` (422), `VALUE_NOT_IN_LIST` (422), `VALUE_EXCLUDED_ABOVE` (409), `FILTER_NOT_FOUND` (404); reused `NODE_NOT_FOUND`, `ATTRIBUTE_NOT_FOUND`, `ATTRIBUTE_NOT_ON_NODE`, `TAXONOMY_CHANGE_NOT_PERMITTED` | R-05 |
| D-6 | Existing operations | Deleting a node also deletes its filters; removing assignments, moving and deleting attributes leave filters in place, ignored while the attribute does not apply (OQ-002) | R-06 |
| D-7 | API | `TaxonomyNode.listAttributes[]` (additive); `GET/PUT/DELETE /api/v1/taxonomy-nodes/{nodeId}/value-filters/{attributeId}` returning `ValueFilterDetail` with labels; contract v1.2.0 | R-07 |
| D-8 | UI | Section «Listas de valores en este nodo» (table + **Filtrar valores**, **Vista previa**); inline `ValueFilterPanel` with two searchable, paged columns, per-row **Incluir/Quitar**, bulk on search matches, excluded-above marks, save/remove/cancel; `NodeListFieldPreview` reusing feature 008's helpers | R-08 |
| D-9 | Feature 010 documents | Merge into the taxonomy OpenAPI (v1.2.0); pointer in 010's UI contract; dated note in 010 spec (node detail gains list attributes) | R-09 |

## Project Structure

### Documentation (this feature)

```text
specs/012-taxonomy-list-value-filters/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/taxonomy-value-filters.openapi.yaml   # reviewed delta → taxonomy contract v1.2.0
├── contracts/taxonomy-value-filters-ui.md          # exact Spanish texts and behavior
├── checklists/requirements.md
└── tasks.md                                        # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/taxonomy/
├── model/ValueFilter.java, AllowedValues.java, ListAttributeSummary.java, ValueFilterDetails.java   # NEW
├── model/InvalidFilterException.java, AttributeNotListException.java, ValueNotInListException.java,
│         ValueExcludedAboveException.java, FilterNotFoundException.java                            # NEW
├── model/TaxonomyErrorCode.java          # + 5 codes
├── model/NodeDetails.java                # + listAttributes
├── repository/ValueFilterRepository.java, MongoValueFilterRepository.java, ValueFilterDocument.java # NEW
├── service/TaxonomyService.java          # + findFilter, saveFilter, removeFilter; delete removes filters;
│                                         #   details() adds list attributes
├── dto/ValueFilterRequest.java, ValueFilterResponse.java, ListAttributeResponse.java,
│       ListValueResponse.java, FilterOnPathResponse.java                                         # NEW
├── dto/TaxonomyNodeResponse.java         # + listAttributes
├── controller/TaxonomyNodeController.java    # + 3 endpoints
└── controller/TaxonomyExceptionHandler.java  # + mappings
backend/src/main/java/com/pim/backend/lov/service/ListOfValuesLookup.java   # + values(listId), lock-free
backend/src/test/java/com/pim/backend/taxonomy/...   # AllowedValuesTest, ValueFilterTest,
                                                     # InMemoryValueFilterRepository, Mongo…IntegrationTest,
                                                     # TaxonomyServiceFilterTest, controller tests, concurrency
backend/src/test/java/com/pim/backend/lov/...        # lookup values test

frontend/src/api/taxonomyApi.ts (+ test)             # types + getValueFilter, saveValueFilter, removeValueFilter
frontend/src/taxonomy/ValueFilterPanel.tsx (+ .module.css, test)        # NEW
frontend/src/taxonomy/NodeListAttributes.tsx (+ .module.css, test)      # NEW: section table
frontend/src/taxonomy/NodeListFieldPreview.tsx (+ test)                 # NEW
frontend/src/pages/TaxonomyPage.tsx (+ test)        # section wiring

.specify/memory/constitution.md                      # 2.6.0 (D-1)
docs/decisions/0003-mongodb-attribute-administration.md   # amended (D-1)
specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml, taxonomy-ui.md, spec.md   # D-9
README.md                                            # feature row
```

No change to attributes, attribute groups, identifiers, product search, navigation or design-system
components; one additive read method in the lov domain.

**Structure Decision**: filters live in the taxonomy domain (they belong to nodes); the lov domain only
exposes values through its existing cross-domain lookup (constitution III, XII).

## Implementation Order (for /speckit-tasks)

1. Governance and contracts: constitution 2.6.0 + ADR 0003 (D-1); merge the contract (v1.2.0) and 010
   notes (D-9).
2. Foundation: `ValueFilter`, `AllowedValues` (tests first), repository (in-memory + Mongo, tests),
   `ListOfValuesLookup.values`.
3. US1: save/get filter (service, endpoints, errors) + node `listAttributes` + section and panel.
4. US2: inheritance, restrictive overwrite, later narrowing, none-allowed (service tests on the example;
   panel marks).
5. US3: change/remove (DELETE, confirmation), moves, node deletion removes filters.
6. US4: preview.
7. Concurrency and polish: full suites, scope check, README, quickstart (API scenarios on a separate
   database), owner browser check.

## Complexity Tracking

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| New collection (D-2) + amendment (D-1) | Filters must persist and can hold up to 50,000 IDs | Embedding in nodes risks the 16 MB limit and changes 010's schema; in memory loses filters on restart |
| Labels returned with every filter detail (D-7) | Frontend must not compute allowed values (FR-019) | Returning IDs only would make the frontend join and filter lists itself |
