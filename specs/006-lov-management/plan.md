# Implementation Plan: List of Values Management

**Branch**: `006-lov-management` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-lov-management/spec.md` (approved 2026-11-11 after
three scope decisions and five clarifications; storage allowed by constitution 2.4.0)

## Summary

Add a backend domain `com.pim.backend.lov` and a frontend page "Listas de valores" so a user with
`ATTRIBUTE_CREATE` can:
- create lists (feature 003 system IDs `LOV000001`…);
- rename lists and turn the equivalence column on or off;
- delete lists that no attribute uses;
- manage values as ID–label pairs with an optional equivalence, one at a time or from a CSV file,
  all or nothing.

Each list is **one MongoDB document with its values embedded**, so every change is atomic without
transactions (R-02).

The feature plugs into feature 004 in two ways:
- the real implementation of feature 004's `ListOfValuesCatalog` replaces the empty placeholder, so LOV
  attributes can finally be saved (R-03);
- two additive `AttributeService` methods show which attributes use a list and make list deletion
  race-free (R-04).

Feature 004's creation page gains a list select (R-14). Product values, unlinking and attribute
migration are not part of this feature (FU-1, FU-2, FU-3).

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript strict, React 19, Vite (frontend)

**Primary Dependencies**: existing only (Spring Boot web, validation, Spring Data MongoDB); CSV parsed by a
small in-house parser (R-09); **no new dependency**

**Storage**: MongoDB (constitution 2.4.0): `lists_of_values` (one document per list, values embedded,
unique `nameKey` index) and `lov_changes` (R-11); ADR 0003 amended to list them

**Testing**: JUnit 5, AssertJ, MockMvc against the local `pim-test` MongoDB (collections emptied before
each test, extending `AttributeAdministrationTestData`); multipart tests with `MockMultipartFile`;
Vitest + Testing Library with mocked `fetch`

**Target Platform**: local development, one backend instance

**Project Type**: web application (separate frontend and backend)

**Performance Goals**: single-value operations under 200 ms locally; CSV import of 10,000 rows validated
and stored in under 5 s locally (SC-003 requires under 1 minute)

**Constraints**:
- Spanish messages; identity and permissions only in development headers.
- One lock per change, with lock order lov → attribute (R-04, R-12).
- At most 50,000 values per list, and 5 MB / 50,000 rows per file (R-08).
- No list data in the frontend (FR-025).

**Scale/Scope**:
- Backend: about 30 classes in `lov`. Feature 004: 2 service methods, and 1 class moved to the test
  tree. `application.yaml`: multipart limits.
- Frontend: 1 page, 1 API client, 1 navigation entry, 1 select added to feature 004's page.
- API: 10 REST operations on 6 paths (contract v1.0.0).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | No `[NEEDS CLARIFICATION]`; spec and decisions D-1 to D-12 approved by the project owner on 2026-11-11 |
| II | Technology stack | PASS | No new library; CSV parsed in-house (R-09) |
| III | Modular monolith | PASS | New domain `lov` (the constitution's own example name). Feature 004 is used only through `AttributeService` and the public types `AttributePermissions`, `Attribute`, `ListOfValuesProperties`; feature 003 only through `IdentifierService`. Feature 004's `ListOfValuesCatalog` interface is implemented in `lov` on top of `lov`'s own lookup service (R-03) |
| IV | Business scope | PASS | From ERF §3.2.1 (tier 2); deferred rules recorded as FU-1 to FU-3 |
| V | Code quality | PASS | Constructor injection, records, Spotless, ESLint, Prettier |
| VI | Testing | PASS | Unit (model, CSV parser, ID generation), service (rules, locks, race R-04), integration per endpoint and status, storage failure (503), frontend per story |
| VII | API design | PASS | `/api/v1`, plural nouns, sub-resource `…/values`, collection envelope, RFC 9457, 200/201/204/400/403/404/409/422, OpenAPI |
| VIII | Security | PASS (risk recorded) | Development headers `X-Pim-Permissions` and new `X-Pim-User` (R-11) are stand-ins anyone can send; upload limited to 5 MB; CSV treated as data only |
| IX | Development scope | PASS | MongoDB for lists of values and their values (constitution 2.4.0); `lov_changes` treated as list data (D-10, owner to confirm); no product data, no integration |
| X | AI governance | PASS | Decisions recorded here for approval |
| XI | Sources | PASS (merge condition) | CF-001 and CF-005 accepted or resolved before merge (Quality Gate 8); CF-002, CF-004 and CF-006 move to FU features; CF-003 settled |
| XII | Package structure | PASS | `lov.{controller, service, repository, model, dto}`; nothing in the root package; `config` untouched except `application.yaml` properties |

**Gate result**: PASS. **Post-design re-check**: PASS. No dependency, database or integration added;
two additive changes to feature 004 (D-3, D-4) are listed for approval.

## Recorded Decisions (approved)

Approved by the project owner on 2026-11-11, including the feature 004 changes D-3 and D-4.

| ID | Question | Decision | Research |
|---|---|---|---|
| D-1 | Where the code lives | New domain `com.pim.backend.lov` | R-01 |
| D-2 | Storage | MongoDB `lists_of_values`, one document per list with embedded values; ADR 0003 amended | R-02 |
| D-3 | Feature 004's catalog | `ListOfValuesCatalogAdapter` (in `lov.service`) implements `ListOfValuesCatalog`; `EmptyListOfValuesCatalog` moves to the test tree. **Changes feature 004** | R-03 |
| D-4 | Linked attributes, safe deletion | `AttributeService.findLinkedToList` and `runIfNoAttributeLinksTo`; lock order lov → attribute. **Changes feature 004 (additive)** | R-04 |
| D-5 | List IDs | Feature 003, type `LIST_OF_VALUES`, default `LOV` + 6 digits | R-05 |
| D-6 | List name | 1–80, unique ignoring case (A-002) | R-06 |
| D-7 | Value fields | ID 1–40 of `A–Z a–z 0–9 - _ .`, compared ignoring case (A-004); label 1–200; equivalence ≤ 200; order added | R-07 |
| D-8 | Generated IDs | Per-list counter, zero-padded to 4 digits, skipping taken IDs, never reused | R-07 |
| D-9 | Limits | 50,000 values per list; 5 MB / 50,000 rows per file | R-08 |
| D-10 | Change records | Collection `lov_changes`, user from new optional header `X-Pim-User` | R-11 |
| D-11 | CSV details | UTF-8 (BOM optional), `,` or `;`, header names ignoring case and accents, RFC 4180 quotes, template from the backend | R-09 |
| D-12 | Permission | `ATTRIBUTE_CREATE` (A-008) | R-10 |

Assumptions accepted as recorded at approval (they can still be revisited without changing the design):
- OQ-002 (name uniqueness, D-6);
- OQ-004 (refuse deleting a linked list);
- OQ-011 (repeated labels allowed);
- OQ-012 (selection mode stays on the attribute);
- OQ-014 (source location);
- OQ-015 (list-level validation settings, not included).

## Project Structure

### Documentation (this feature)

```text
specs/006-lov-management/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/lists-of-values.openapi.yaml
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
docs/decisions/0003-mongodb-attribute-administration.md   # amended: lists_of_values, lov_changes
```

### Source Code (repository root)

```text
backend/src/main/resources/application.yaml        # multipart limits 5MB / 6MB (R-08)
backend/src/main/java/com/pim/backend/
├── attribute/                                      # feature 004 — additive (D-3, D-4)
│   ├── service/AttributeService.java               # + findLinkedToList, runIfNoAttributeLinksTo
│   └── repository/EmptyListOfValuesCatalog.java     # → moved to the test tree as a fake
└── lov/                                            # NEW domain
    ├── controller/ ListOfValuesController, ListValueController, ListOfValuesImportController,
    │               LovRequestReader (permissions, manual-ID permissions, user), LovExceptionHandler
    ├── service/    ListOfValuesService (rules, lock, IdentifierService, AttributeService),
    │               ListOfValuesLookup, ListOfValuesCatalogAdapter, CsvValuesParser, ValuesImport,
    │               ChangeRecorder
    ├── repository/ ListOfValuesRepository, MongoListOfValuesRepository, ListOfValuesDocument,
    │               ChangeRecordRepository, MongoChangeRecordRepository, ChangeRecordDocument
    ├── model/      ListOfValues, ListName, ListValue, ValueId, GeneratedValueIds, LovErrorCode,
    │               ImportOutcome, ImportRowError, ChangeRecord, LinkedAttribute,
    │               exceptions (InvalidListException, ListNotFoundException, ValueNotFoundException,
    │               ListNameInUseException, ValueIdInUseException, ListInUseException,
    │               ListTooLargeException, InvalidImportException, ListChangeNotPermittedException)
    └── dto/        CreateListOfValuesRequest, UpdateListOfValuesRequest, AddValueRequest,
                    UpdateValueRequest, ListOfValuesResponse, ListOfValuesSummaryResponse,
                    ListOfValuesCollectionResponse, ListValueResponse, LinkedAttributeResponse,
                    ImportResultResponse
backend/src/test/java/com/pim/backend/lov/          # model, parser, service, controller tests
backend/src/test/java/com/pim/backend/common/AttributeAdministrationTestData.java  # + lists_of_values, lov_changes

frontend/src/
├── api/listsOfValuesApi.ts (+ test)
├── pages/ListsOfValuesPage.tsx (+ .module.css, test)
├── pages/AttributeCreationPage.tsx (+ test)       # feature 004: "Lista de valores" select (R-14)
├── attributes/attributeForm.ts (+ test)           # feature 004: listOfValuesId in the request
└── navigation.ts (+ test), App.tsx                # "Listas de valores" entry and route
```

**Structure Decision**: web application; one new backend domain and one new page in the existing layouts
(constitution III, XII).

## Implementation Order (for /speckit-tasks)

1. Foundation:
   - `application.yaml` multipart limits;
   - test-data reset extended;
   - model (`ListName`, `ValueId`, `ListValue`, `ListOfValues`, generated IDs) and Mongo repository,
     tests first;
   - ADR 0003 amendment.
2. US1, create and add values:
   - service create and add, using `IdentifierService`;
   - the catalog adapter (D-3), so feature 004 accepts lists;
   - controller and integration tests, including feature 004's LOV creation against a real list.
3. US2, consult: list collection and detail with `AttributeService.findLinkedToList` (D-4).
4. US3, edit and delete: value edit and delete, list rename and delete with `runIfNoAttributeLinksTo`
   (D-4), change records, and a race test.
5. US4, bulk: CSV parser (unit tests first), import service, template endpoint, multipart controller.
6. US5, equivalences: toggle and per-value equivalence.
7. Frontend: API client, page per story, navigation entry, feature 004's list select; tests first.
8. Full builds, quickstart API and UI scenarios, contract and scope checks.

## Complexity Tracking

No deviation from a MUST rule. Approved 2026-11-11:

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| Additive change to feature 004's service (D-4) | List detail needs linked attributes; deletion must not race with attribute creation (SC-005) | Reading the attributes collection from `lov` breaks constitution III; check-then-delete without the attribute lock can leave an attribute linked to a deleted list |
| Feature 004's catalog placeholder replaced (D-3) | Planned by feature 004 (its D-12) | Keeping it would leave LOV attributes unsavable |
| Values embedded in one document with a 50,000 cap (D-2, D-9) | All-or-nothing changes without transactions on a standalone MongoDB | A separate values collection needs a replica set or a compensating rollback |
| List detail returns all values at once (R-13) | Simple contract; typical lists are small | Paging adds API and UI work before any list needs it; the page shows 500 rows at a time (R-14) |
