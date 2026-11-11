# Implementation Plan: Attribute Management – Consult, Edit and Delete

**Branch**: `007-attribute-management` (spec directory; no git branch) | **Date**: 2026-11-11 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-attribute-management/spec.md` (draft, clarified
2026-11-11: Q1 one "Atributos" page, Q2 refuse deleting attributes in groups, Q3 reuse
`ATTRIBUTE_CREATE`)

**Status**: **Approved** by the project owner on 2026-11-11, including decisions D-1 to D-10 and the
changes to features 004, 005 and 006 they list (constitution X).

## Summary

Complete feature 004's attribute domain with **update and delete**, and replace the "Crear Atributos"
page with an **"Atributos"** page modelled on "Lista de Valores":
- backend: `PUT` and `DELETE /api/v1/attributes/{systemId}`; list and detail responses gain the
  attribute's groups, its list's name and its last-change date (contract v1.2.0, additive);
- deletion is refused while the attribute is in any group; the check runs under feature 005's group
  lock so it cannot race with membership changes (R-02, R-05);
- frontend: one master–detail page with search, type filter, detail, "Nuevo atributo", "Editar" and
  "Eliminar"; feature 004's form is **moved** into a reusable editor, not rebuilt (R-10).

No new collection, dependency, permission code or package. Product values and templates don't exist
yet, so there is nothing to count or migrate (spec FR-015).

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript strict, React 19, Vite (frontend)

**Primary Dependencies**: existing only (Spring Boot web, validation, Spring Data MongoDB); **no new
dependency**

**Storage**: MongoDB, existing `attributes` collection; one optional field `updatedAt` (no migration)

**Testing**: JUnit 5, AssertJ, MockMvc against the local `pim-test` MongoDB (collections emptied before
each test by `AttributeAdministrationTestData`); Vitest + Testing Library with mocked `fetch`

**Target Platform**: local development, one backend instance

**Project Type**: web application (separate frontend and backend)

**Performance Goals**: list of 500 attributes loads and filters without noticeable delay locally;
single-attribute operations under 200 ms locally

**Constraints**:
- Spanish (es-MX) messages; permissions only through the existing development header and
  `VITE_DEV_PERMISSIONS`.
- Lock order lov → attribute and group → attribute (R-06).
- Feature 004–006 behavior, responses and messages unchanged (spec FR-019).

**Scale/Scope**:
- Backend: about 12 changed and 8 new classes, mostly in `attribute`; 2 small additions in
  `attributegroup` and `lov`.
- Frontend: 1 new page, 1 moved form component, API client and form helpers extended, 1 navigation
  entry replaced.
- API: 2 new operations, 2 extended responses (contract v1.2.0).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec clarified (Q1–Q3) and approved with decisions D-1 to D-10 on 2026-11-11 |
| II | Technology stack | PASS | No new library |
| III | Modular monolith | PASS | `attribute` stays independent: it owns the interfaces `AttributeGroupDirectory` (new) and `ListOfValuesCatalog`; `attributegroup` and `lov` implement them on top of their own services (R-02, R-03). No domain reads another's collection |
| IV | Business scope | PASS | ERF 1.1 §3.1, §3.2 ¶1, ¶3, §3.2.1; deferred items recorded (OQ-001–OQ-004, FR-015) |
| V | Code quality | PASS | Constructor injection, records, Spotless, ESLint, Prettier |
| VI | Testing | PASS | Unit, service (rules, order, races), repository, integration per status, frontend per story; existing suites kept |
| VII | API design | PASS | `/api/v1`, `PUT` full replace, `DELETE` 204, RFC 9457 with `errors[]`, 200/204/400/403/404/409/422/503, OpenAPI v1.2.0 (additive) |
| VIII | Security | PASS (risk recorded) | Same development header as before (anyone can send it); backend authoritative for every rule |
| IX | Development scope | PASS | MongoDB already allowed for attribute administration (constitution 2.4.0); no product data, no integration |
| X | AI governance | PASS | Decisions D-1 to D-10 approved by the project owner on 2026-11-11 |
| XI | Sources | PASS | Tier 2 sources cited; CF-001 (type change) and CF-002 (roles, audit) recorded, not resolved by assumption |
| XII | Package structure | PASS | Existing `attribute.{controller,service,repository,model,dto}`; one adapter in `attributegroup.service`; nothing in the root package |

**Gate result**: PASS. **Post-design re-check**: PASS.

## Recorded Decisions (approved 2026-11-11)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | Where the code lives | Extend `com.pim.backend.attribute`; no new domain | R-01 |
| D-2 | Groups for list, detail and deletion | New interface `AttributeGroupDirectory` in `attribute.repository`, implemented by `attributegroup.service.AttributeGroupDirectoryAdapter` (via `ObjectProvider`, no bean cycle); `AttributeGroupService.runIfInNoGroup` added. **Changes features 004 and 005 (additive)** | R-02 |
| D-3 | List names | `ListOfValuesCatalog.namesById()`, implemented in feature 006's adapter and lookup. **Changes features 004 and 006 (additive)** | R-03 |
| D-4 | Edit API | `PUT /api/v1/attributes/{id}`, creation fields; system ID and data type must not differ (422); same validations; check order fixed | R-04 |
| D-5 | Delete API | `DELETE /api/v1/attributes/{id}` → 204; 409 `ATTRIBUTE_IN_GROUPS` with the groups; list kept; ID never reused | R-05 |
| D-6 | Locks | Keep lov → attribute and group → attribute; never call a directory while holding the attribute lock | R-06 |
| D-7 | Responses | `groups`, `listOfValuesName`, `updatedAt` added; contract v1.2.0 merged into feature 004's file | R-07 |
| D-8 | Model and storage | `Attribute.updatedAt`, `Attribute.updated(…)`, repository `update`, `delete`, `existsWithDisplayName(name, except)`; 4 new error codes; old documents readable. **Changes feature 004 (additive)** | R-08 |
| D-9 | Permission | Reuse `ATTRIBUTE_CREATE`; new message code `ATTRIBUTE_CHANGE_NOT_PERMITTED` ("No tienes permiso para modificar atributos.") | R-09 |
| D-10 | Frontend | `AttributesPage` at `#/atributos` replaces `/crear-atributo` in the navigation; `AttributeCreationPage` moved to `attributes/AttributeEditor.tsx` (create and edit modes) with its tests; layout and confirmation copied from "Lista de Valores"; search and filter in the browser. **Changes feature 004's page** | R-10 |

Assumptions carried from the spec: last write wins (A-004, R-11); no audit trail (A-005); data type
fixed (A-002).

## Project Structure

### Documentation (this feature)

```text
specs/007-attribute-management/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/attributes-v1.2.openapi.yaml   # changes only; merged into feature 004's contract
├── checklists/requirements.md
└── tasks.md                                 # /speckit-tasks
specs/004-attribute-creation/contracts/attributes.openapi.yaml   # → v1.2.0 during implementation
```

### Source Code (repository root)

```text
backend/src/main/java/com/pim/backend/
├── attribute/                                 # feature 004 — extended
│   ├── controller/ AttributeController (+ PUT, DELETE), AttributeExceptionHandler (+ 4 codes)
│   ├── service/    AttributeService (+ update, delete, findAllDetails, findDetails)
│   ├── repository/ AttributeRepository, MongoAttributeRepository, AttributeDocument (+ updatedAt),
│   │               ListOfValuesCatalog (+ namesById), AttributeGroupDirectory (NEW)
│   ├── model/      Attribute (+ updatedAt, updated), AttributeErrorCode (+ 4), AttributeDetails (NEW),
│   │               GroupReference (NEW), AttributeChangeNotPermittedException (NEW),
│   │               ImmutableFieldException (NEW), AttributeInGroupsException (NEW)
│   └── dto/        AttributeUpdateRequest (NEW), AttributeResponse (+ groups, updatedAt, list name),
│                   GroupReferenceResponse (NEW)
├── attributegroup/service/ AttributeGroupService (+ runIfInNoGroup),
│                           AttributeGroupDirectoryAdapter (NEW)          # feature 005 — additive
└── lov/service/ ListOfValuesLookup (+ namesById), ListOfValuesCatalogAdapter (+ namesById)  # 006 — additive
backend/src/test/java/com/pim/backend/
├── attribute/...            # new tests; fakes InMemoryAttributeRepository (+ update, delete),
│                            # EmptyListOfValuesCatalog (+ namesById), NoAttributeGroups (NEW)
├── attributegroup/...       # adapter and race tests; setups pass NoAttributeGroups
└── lov/...                  # namesById test; setups pass NoAttributeGroups

frontend/src/
├── pages/AttributesPage.tsx (+ .module.css, test)          # NEW
├── attributes/AttributeEditor.tsx (+ test)                 # moved from pages/AttributeCreationPage.*
├── attributes/attributeForm.ts (+ test)                    # + fromAttribute
├── api/attributesApi.ts (+ test)                           # + get, update, delete, canChangeAttributes
└── navigation.ts (+ test), App.tsx                         # "Atributos" replaces "Crear Atributos"
```

**Structure Decision**: web application; existing domains and page layout (constitution III, XII).

## Implementation Order (for /speckit-tasks)

1. Foundation: model (`updatedAt`, `updated`), document, repository `update`/`delete`/name check,
   error codes, directories (interfaces, adapters, fakes), test setups updated; tests first.
2. US1 consult: details read model, extended responses, contract v1.2.0; frontend API types, page with
   list, search, filter, detail, empty/error states, navigation; the editor moved for "Nuevo atributo".
3. US2 edit: service `update` with check order, `PUT`, integration tests, LOV relink race test;
   frontend edit mode, `fromAttribute`, page wiring.
4. US3 delete: `runIfInNoGroup`, service `delete`, `DELETE`, ID-not-reused test, delete-vs-group race
   test; frontend confirmation.
5. Polish: full builds, quickstart API and UI scenarios, contract merge, scope checks.

## Complexity Tracking

No deviation from a MUST rule.

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| `AttributeGroupDirectory` + adapter with `ObjectProvider` (D-2) | Deletion must check groups under the group lock; `attribute` must not depend on `attributegroup` | Direct collection read breaks constitution III and bypasses the lock; a shared lock bean changes feature 005's constructor and tests |
| Moving feature 004's page into an editor component (D-10) | The same form serves create and edit on one page (Q1) | A second copy of the form duplicates its validations and tests |
| `AttributeService` constructor gains a parameter | Needed by D-2 | Six test setups in features 004–006 add one fake argument; assertions unchanged |
