# Implementation Plan: Unlink a List of Values from an Attribute (Desvincular LOV de un atributo)

**Branch**: `014-lov-unlink` | **Date**: 2026-11-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-lov-unlink/spec.md` (approved 2026-11-23)

**Status**: **Approved** by the project owner on 2026-11-23, including decisions D-1 to D-5 (constitution X).

## Summary

Let an authorized user detach the list of values from a list-of-values attribute while giving the
attribute a new data type and properties, in one all-or-nothing change that also deletes the attribute's
list-value filters on taxonomy nodes. The rule lives in `AttributeService.unlinkList` (attribute domain);
the taxonomy domain deletes the filters through the existing `TaxonomyAssignmentDirectory` port, under
the taxonomy lock then the attribute lock (order unchanged), with compensation if the filter deletion
fails. The API adds `GET` (review with a token) and `POST` (apply) on
`/api/v1/attributes/{systemId}/list-unlink` (attributes contract v2.2.0). The frontend adds an unlink mode
to the attribute edit form, reached from the form itself or from the list's linked attributes.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2 (Gradle, `-Werror`, Spotless); TypeScript strict,
React 19, Vite

**Primary Dependencies**: existing only (`MongoTemplate`, attribute/taxonomy/lov services, feature 004
property mapping, feature 008 preview, design-system components); **no new dependency**

**Storage**: MongoDB — `attributes` (one document updated) and `taxonomy_value_filters` (documents of the
attribute deleted); no new collection or field

**Testing**: JUnit 5: `Attribute.withDataType`, service tests with in-memory repositories (review, token,
validation order, groups/assignments kept, filters deleted, compensation), taxonomy adapter and filter
repository tests, controller integration tests against the local MongoDB (200/400/403/404/409/422); Vitest
+ Testing Library: API client, editor unlink mode, attributes page deep link, list page button

**Target Platform**: backend on the JVM; frontend in desktop and tablet browsers (768 px and up)

**Project Type**: web application (backend + frontend)

**Performance Goals**: SC-001 — review and unlink each answer in well under a second (one attribute, a
filter query by attribute ID)

**Constraints**:
- Lock order group → taxonomy → attribute (feature 010 R-05); the unlink takes taxonomy → attribute and
  never the list lock.
- Business rules in services/models; controllers map HTTP (constitution III); frontend shows what the
  backend returns (VIII).
- Feature 007's `update` keeps refusing type changes; only the unlink may change the type (FR-013).

**Scale/Scope**: backend: 1 model method, 1 service operation + review, 2 port methods + adapter, 2
filter-repository methods, 1 controller pair, 4 error codes, 3 DTOs; frontend: 2 API functions, editor
unlink mode, review box, page deep link, list button; docs: contract v2.2.0, 007/006/012 notes, README.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec approved 2026-11-23 with clarifications (OQ-001–OQ-003) |
| II | Technology stack | PASS | Existing stack; no new library |
| III | Modular monolith | PASS | Rule in `attribute`; filters deleted by `taxonomy` through its port; `lov` untouched |
| IV | Business scope | PASS | HU2.1, ERF §3.2.1 (tier 2); 006 FU-2 |
| V | Code quality | PASS | Narrow model method; reuse of feature 004 validation |
| VI | Testing | PASS | Unit, service, adapter, repository, controller integration and frontend tests for every rule |
| VII | API design | PASS | Contract first (`contracts/list-unlink.openapi.yaml`), sub-resource of the attribute, 200/400/403/404/409/422 |
| VIII | Security | PASS | Same development permission; backend authoritative |
| IX | Development scope | PASS | No new storage |
| X | AI governance | PASS | D-1 to D-5 approved by the project owner on 2026-11-23 |
| XI | Sources | PASS | HU2.1/ERF cited; owner's text source open (OQ-004) |
| XII | Package structure | PASS | `attribute.{controller,service,model,dto,repository}`, `taxonomy.{service,repository}` |

**Gate result**: PASS (D-1 to D-5 approved 2026-11-23).
**Post-design re-check**: PASS — no new dependency, storage, domain or lock order.

## Recorded Decisions (approved 2026-11-23)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | Where the rule lives | `AttributeService.unlinkList` + `Attribute.withDataType`; the list is not changed; feature 007's update still refuses type changes | R-01, R-05 |
| D-2 | Review then apply | `GET …/attributes/{id}/list-unlink` → list, filtered nodes, `reviewToken`; `POST` same path with `dataType`, properties, `reviewToken` → 409 `UNLINK_OUTDATED` if anything changed | R-02 |
| D-3 | Validation | Feature 004 rules and codes for the new properties; new codes `ATTRIBUTE_NOT_LIST_OF_VALUES` (422), `UNLINK_TO_LIST_NOT_ALLOWED` (422), `UNLINK_OUTDATED` (409), `REVIEW_TOKEN_REQUIRED` (400); the display name never changes here | R-03 |
| D-4 | Filters | Taxonomy port gains `nodesFilteringValuesOf` and `runRemovingValueFilters`; under the taxonomy lock the attribute is changed (attribute lock) and then its filters deleted; on failure the attribute is restored | R-04 |
| D-5 | UI | «Desvincular lista» in the edit form → unlink mode (read-only ID/name, seven types, properties, preview, «Qué cambiará» review, «Desvincular y guardar» / «Cancelar»); «Desvincular» on the list's linked attributes → `#/atributos?desvincular={id}` | R-06, [UI contract](contracts/list-unlink-ui.md) |

## Project Structure

### Documentation (this feature)

```text
specs/014-lov-unlink/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/list-unlink.openapi.yaml   # delta → attributes contract v2.2.0
├── contracts/list-unlink-ui.md          # Spanish texts and behaviour
├── checklists/requirements.md
└── tasks.md                             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/attribute/
├── model/Attribute.java                       # + withDataType
├── model/ListUnlinkReview.java, AttributeNotListOfValuesException.java,
│         UnlinkOutdatedException.java                                 # NEW
├── model/AttributeErrorCode.java              # + 4 codes
├── repository/TaxonomyAssignmentDirectory.java  # + nodesFilteringValuesOf, runRemovingValueFilters
├── service/AttributeService.java              # + reviewListUnlink, unlinkList
├── dto/ListUnlinkRequest.java, ListUnlinkReviewResponse.java, NodeReferenceResponse.java   # NEW
├── controller/AttributeController.java        # + GET/POST …/list-unlink
└── controller/AttributeExceptionHandler.java  # + mappings
backend/src/main/java/com/pim/backend/taxonomy/
├── service/TaxonomyAssignmentDirectoryAdapter.java  # + the two port methods
├── service/TaxonomyService.java               # + filtered nodes of an attribute, delete them under the lock
├── repository/ValueFilterRepository.java, MongoValueFilterRepository.java  # + findByAttribute, deleteByAttribute
backend/src/test/java/com/pim/backend/...      # model, service, adapter, repository, controller tests;
                                               # NoTaxonomyAssignments test double gains the two methods

frontend/src/api/attributesApi.ts (+ test)     # reviewListUnlink, unlinkList
frontend/src/attributes/AttributeEditor.tsx (+ test)   # unlink mode
frontend/src/attributes/UnlinkReview.tsx (+ test)      # NEW: «Qué cambiará» box
frontend/src/pages/AttributesPage.tsx (+ test)  # ?desvincular= deep link, success message
frontend/src/pages/ListsOfValuesPage.tsx (+ test)   # «Desvincular» per linked attribute
frontend/src/navigation.ts (+ test)             # path matching ignores ?query

specs/004-attribute-creation/contracts/attributes.openapi.yaml   # v2.2.0
specs/007-attribute-management/spec.md, specs/006-lov-management/spec.md,
specs/012-taxonomy-list-value-filters/spec.md                    # dated notes
README.md                                                        # feature row
```

**Structure Decision**: Web application (`backend/`, `frontend/`), as features 001–013.

## Complexity Tracking

No constitution violations. Compensation instead of a transaction (D-4) follows feature 013 D-3 for the
same reason (standalone MongoDB).
