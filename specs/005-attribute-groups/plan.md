# Implementation Plan: Attribute Management – Attribute Groups

**Branch**: `005-attribute-groups` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-attribute-groups/spec.md` (approved 2026-11-09
after five clarifications; storage set to MongoDB by the owner, constitution 2.3.0)

## Summary

Add a backend domain `com.pim.backend.attributegroup` and a frontend page "Grupos de atributos" so a
user with `ATTRIBUTE_CREATE` can create flat attribute groups, rename them, delete them when empty, and
add or remove existing attributes (feature 004) without ever changing those attributes. Groups start
empty, get a UUID that survives renames, and are **stored in MongoDB**. Because groups hold attribute
system IDs, feature 004's attributes and feature 003's identifier registry move to MongoDB in the same
feature (constitution 2.3.0, ADR 0003; amendments of 003 and 004), so everything survives restarts
consistently. Reading groups is open to everyone. One
additive endpoint, `GET /api/v1/attributes`, is added to feature 004 so the page can offer attributes to
choose from (R-03).

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript strict, React 19, Vite (frontend)

**Primary Dependencies**: existing only (including `spring-boot-starter-data-mongodb`, already used by
feature 002); **no new dependency**

**Storage**: MongoDB (R-02, ADR 0003): collection `attribute_groups` for this feature; `attributes`
(feature 004) and `system_ids` / `object_type_configurations` (feature 003) replace their in-memory
repositories behind the same interfaces

**Testing**: JUnit 5, AssertJ, MockMvc against the local `pim-test` MongoDB (collections emptied before
each test); Vitest + Testing Library with mocked `fetch`

**Target Platform**: local development, one backend instance

**Project Type**: web application (separate frontend and backend)

**Performance Goals**: each operation under 200 ms locally; SC-001 (create a group with three
attributes in under 1 minute) checked in the UI walkthrough

**Constraints**: Spanish messages; identity only in the development header; one lock per change
(R-12); no frontend group or attribute data (FR-013)

**Scale/Scope**: about 25 backend classes in `attributegroup`; feature 003: MongoDB repositories and
documents; feature 004: MongoDB repository and document plus 3 small additions (R-03);
`CorsConfiguration`: `PUT`, `DELETE`; frontend: 1 page, 1 API client, 1 navigation entry; 7 REST
operations on 4 paths (contract v1.0.0) plus 1 in feature 004

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | No markers; decisions D-1 to D-9 approved by the project owner on 2026-11-09 (D-1 changed to MongoDB); features 003 and 004 amended in their specs and plans before any code |
| II | Technology stack | PASS | No new library or tool |
| III | Modular monolith | PASS | New domain; feature 004 used only through `AttributeService` and its public model type `AttributePermissions`; the attribute list endpoint lives in feature 004's own domain (D-3) |
| IV | Business scope | PASS | From ERF §3.2 ¶3 and §3.2.2 (tier 2) |
| V | Code quality | PASS | Constructor injection, records, Spotless, ESLint, Prettier |
| VI | Testing | PASS | Unit, integration (every endpoint and error status), validation rules with status and body, concurrency; frontend per story |
| VII | API design | PASS | `/api/v1`, plural nouns, sub-resource `…/attributes`, collection envelope, RFC 9457, 200/201/204/400/403/404/409/422, OpenAPI |
| VIII | Security | PASS (risk recorded) | Same development header as feature 004; identity not in paths or bodies |
| IX | Development scope | PASS | MongoDB for attribute administration data, allowed by constitution 2.3.0 (ADR 0003); no integration; inheritance, behaviours and metadata deferred (FR-014) |
| X | AI governance | PASS | Decisions recorded here for approval |
| XI | Sources | PASS (merge condition) | CF-001 to CF-003 resolved or accepted before merge (Quality Gate 8) |
| XII | Package structure | PASS | `attributegroup.{controller, service, repository, model, dto}`; `config.CorsConfiguration` gains `PUT`, `DELETE`; nothing in the root package |

**Gate result**: PASS. **Post-design re-check**: PASS; no dependency,
database or integration added.

## Recorded Decisions (approved)

Approved by the project owner on 2026-11-09; D-1 changed from "in memory" to MongoDB at approval.

| ID | Question | Decision | Research |
|---|---|---|---|
| D-1 | Storage | **MongoDB** for groups (`attribute_groups`), and with them attributes (`attributes`, feature 004) and system IDs (`system_ids`, `object_type_configurations`, feature 003); constitution 2.3.0, ADR 0003 | R-02 |
| D-2 | Where the code lives | New domain `attributegroup`, using `AttributeService` | R-01 |
| D-3 | Attributes to choose from | Add `GET /api/v1/attributes` (list, sorted by name) to feature 004: repository, service and controller additions; feature 004 contract → v1.1.0 | R-03 |
| D-4 | Group identifier | Random UUID | R-04 |
| D-5 | Limits (OQ-007) | Name 1–80, description ≤ 500 | R-05 |
| D-6 | Adding attributes | All-or-nothing; unknown ID → 422 `ATTRIBUTE_NOT_FOUND`; already-members reported | R-07 |
| D-7 | Removing a non-member | 404 `ATTRIBUTE_NOT_IN_GROUP` | R-08 |
| D-8 | Groups of an attribute | `GET /api/v1/attribute-groups?attributeId=…` | R-10 |
| D-9 | CORS | Add `PUT` and `DELETE` | R-11 |

OQ-005 (at least one group per attribute), OQ-008 (conflicts between group settings) and OQ-009 (groups
in the creation form) stay open; none affects this feature's behaviour (ungrouped attributes are
allowed, A-003).

## Project Structure

### Documentation (this feature)

```text
specs/005-attribute-groups/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/attribute-groups.openapi.yaml
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/
├── config/CorsConfiguration.java              # + PUT, DELETE (D-9)
├── identifier/repository/                     # feature 003 — MongoDB implementations replace in-memory (D-1)
│   └── MongoSystemIdRegistry, MongoObjectTypeConfigurationRepository, documents + Spring Data repos
├── identifier/config/DefaultObjectTypeConfigurations.java   # insert defaults only where missing
├── attribute/                                 # feature 004 — MongoDB storage (D-1) and list endpoint (D-3)
│   ├── repository/AttributeRepository.java (+ findAll()), MongoAttributeRepository, AttributeDocument
│   ├── service/AttributeService.java          # + findAll()
│   └── controller/AttributeController.java    # + GET /api/v1/attributes
└── attributegroup/                            # NEW domain
    ├── controller/ AttributeGroupController, GroupPermissionsReader, AttributeGroupExceptionHandler
    ├── service/    AttributeGroupService      # rules, lock, AttributeService calls
    ├── repository/ AttributeGroupRepository, MongoAttributeGroupRepository, AttributeGroupDocument
    ├── model/      AttributeGroup, GroupName, AttributeGroupErrorCode, AddAttributesOutcome,
    │               exceptions (InvalidGroupException, GroupNotFoundException, GroupNameInUseException,
    │               GroupNotEmptyException, AttributeNotInGroupException, UnknownAttributeException,
    │               GroupChangeNotPermittedException)
    └── dto/        AttributeGroupRequest, AddAttributesRequest, AttributeGroupResponse,
                    AttributeGroupSummaryResponse, AttributeGroupCollectionResponse, GroupMemberResponse,
                    AddAttributesResponse
backend/src/test/java/com/pim/backend/attributegroup/   # model, service, controller tests
backend/src/test/java/com/pim/backend/attribute/controller/AttributeListIntegrationTest.java   # D-3

frontend/src/
├── api/attributeGroupsApi.ts (+ test), api/attributesApi.ts (+ listAttributes, test)
├── pages/AttributeGroupsPage.tsx (+ .module.css, test)
└── navigation.ts, App.tsx                     # "Grupos de atributos" entry and route
```

**Structure Decision**: web application; one new backend domain and one new page, existing layouts
(constitution III, XII).

## Implementation Order (for /speckit-tasks)

1. CORS `PUT`/`DELETE`; MongoDB storage for feature 003 and feature 004 behind their interfaces, with
   their tests updated to clean collections (existing assertions unchanged); feature 004 list endpoint
   (D-3) with its test.
2. Group model (name, group) and repository, tests first.
3. Service with real `AttributeService`; per story: US1 create → US2 add → US3 remove → US4 view →
   US5 rename/delete; integration tests per story.
4. Frontend API client, page, navigation; tests first.
5. Full builds, quickstart API and UI scenarios, contract and scope checks.

## Complexity Tracking

No deviation from a MUST rule. For explicit approval:

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| Additive change to feature 004 (D-3) | The page must offer existing attributes | Typing IDs by hand is unusable; listing in the group domain would read another domain's data |
| Features 003 and 004 change storage in this feature (D-1) | Groups hold attribute IDs; all three must persist together (ADR 0003) | Persisting groups alone breaks after a restart (IDs reused) |
