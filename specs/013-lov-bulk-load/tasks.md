---
description: "Task list for feature 013 — Bulk Load of Lists of Values (Carga Masiva de LOV)"
---

# Tasks: Bulk Load of Lists of Values (Carga Masiva de LOV)

**Input**: Design documents from `specs/013-lov-bulk-load/` — [spec.md](spec.md), [plan.md](plan.md)
(approved 2026-11-23, decisions D-1 to D-7), [research.md](research.md), [data-model.md](data-model.md),
[contracts/lov-bulk-load.openapi.yaml](contracts/lov-bulk-load.openapi.yaml),
[contracts/bulk-load-ui.md](contracts/bulk-load-ui.md), [quickstart.md](quickstart.md)

**Tests**: Required by constitution VI (unit tests for business logic, API integration tests for every
endpoint, a test per validation rule). Test tasks come before the code they cover.

**Organization**: By user story. US1 and US2 are both P1 and ship together (the check that reports
errors is the same step that builds the summary); US3 (P2) adds the page actions and templates.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: US1, US2, US3 from spec.md
- Paths: backend `backend/src/main/java/com/pim/backend/lov/...` (tests under `backend/src/test/java/...`),
  frontend `frontend/src/...`

---

## Phase 1: Setup

**Purpose**: The new dependency and the shared CSV tokenizer.

- [X] T001 Add `org.apache.poi:poi-ooxml` with an exact 5.4.x version to `backend/build.gradle` (D-1, research R-01); run `.\gradlew.bat dependencies --configuration runtimeClasspath` to confirm it resolves and `.\gradlew.bat build` stays green with `-Werror`
- [X] T002 Extract the RFC 4180 tokenizer of `CsvValuesParser` into `backend/src/main/java/com/pim/backend/lov/service/CsvRecords.java` (UTF-8 with or without BOM, comma or semicolon by the header line, quotes, records with their row numbers, header = row 1; research R-02) and make `CsvValuesParser` use it with unchanged behaviour; add `backend/src/test/java/com/pim/backend/lov/service/CsvRecordsTest.java`; feature 006's `CsvValuesParserTest` and import tests MUST stay green

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Reading both formats into the same rows, and the model types every story uses.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Write `backend/src/test/java/com/pim/backend/lov/service/XlsxRecordsTest.java` with workbooks built by POI in the test: first sheet only; text, inline-string and number cells; a number cell formatted `0000` read as `0001`, a plain `12` as `12`; a formula read as its cached value; blank rows skipped with later row numbers kept (header = row 1); an empty workbook; a corrupt zip → `InvalidImportFileException` (research R-01, R-03)
- [X] T004 Implement `backend/src/main/java/com/pim/backend/lov/service/XlsxRecords.java`: POI streaming reader (`OPCPackage` + `XSSFReader` + `XSSFSheetXMLHandler`) over the first sheet, `DataFormatter` texts, returning the same record shape as `CsvRecords`; stop with `InvalidImportFileException.tooLarge()` past 50,000 non-blank data rows (T003 green)
- [X] T005 [P] Add the model records in `backend/src/main/java/com/pim/backend/lov/model/`: `BulkLoadRow` (row number, list, id, label, equivalence), `PlannedListChange` (name, systemId optional, action CREATE/UPDATE, valuesAdded, valuesUpdated), `BulkLoadSummary` (lists, totals, checkToken), `BulkLoadResult` (lists with system IDs, totals) and `BulkLoadOutdatedException` (data-model "Bulk load plan", "Summary", "Result")
- [X] T006 [P] Extend `backend/src/main/java/com/pim/backend/lov/model/LovErrorCode.java`: add `BULK_LOAD_OUTDATED` («Las listas cambiaron desde la revisión. Vuelve a revisar el archivo.»), `CHECK_TOKEN_REQUIRED` and `INVALID_FORMAT`; change `INVALID_IMPORT_FILE` to «No se pudo leer el archivo: %s. No se aplicó ningún valor.» (no "CSV"); add a per-list duplicate message «El ID "%s" de la lista «%s» está repetido en el archivo (filas %s).» (research R-10); update feature 006 tests that assert the old file message
- [X] T007 Write `backend/src/test/java/com/pim/backend/lov/service/BulkLoadFileReaderTest.java`: zip signature → Excel, anything else → CSV; OLE2 signature (`.xls`) refused with «Guarda el archivo como Excel (.xlsx) o CSV.»; header columns Lista, ID, Etiqueta required, Equivalencia optional, any order, ignoring case/accents/spaces; unknown or repeated column refused; no data rows refused; blank rows skipped; 50,000-row limit; the same rows from a CSV and an XLSX give equal `BulkLoadRow` lists (SC-004; research R-02–R-04, R-09)
- [X] T008 Implement `backend/src/main/java/com/pim/backend/lov/service/BulkLoadFileReader.java`: detect the format by content, read with `CsvRecords` or `XlsxRecords`, map the four columns, return `BulkLoadRow`s with the row numbers the user sees (T007 green)

**Checkpoint**: Both formats read into the same rows; feature 006 unchanged.

---

## Phase 3: User Story 1 + User Story 2 — Load several lists, every error before saving (Priority: P1) 🎯 MVP

**Goal**: A file creates the lists it names that do not exist and adds/updates values in those that do;
the whole file is checked first, every problem is reported, a valid file shows a summary and is saved
on confirmation, all lists or none (FR-005–FR-015).

**Independent Test**: quickstart scenarios 4–10 (summary, cancel, confirm, leading zeros, CSV = Excel,
error table, outdated token).

### Tests for User Stories 1 and 2 ⚠️

> Write these first; they must fail before the implementation tasks below.

- [X] T009 [P] [US1] [US2] Write `backend/src/test/java/com/pim/backend/lov/service/BulkLoadPlanTest.java`: rows grouped by list name ignoring case and surrounding spaces, in order of first appearance; a new list named as its first row, equivalence column on only if a row has an equivalence (D-5); an existing list gets feature 006's add/update rules (label replaced, equivalence only when the cell is not empty, empty ID generated, values not in the file kept); every row error of every list collected and sorted (Lista, ID, Etiqueta, Equivalencia); blank or >80-character list name → `INVALID_LIST_NAME` on Lista; duplicate (list, ID) → `DUPLICATE_ID_IN_FILE` on every duplicate row, message naming the list; the same ID in two lists is fine; a list over 50,000 values → `ListTooLargeException` naming it (FR-006–FR-009, FR-012, FR-014)
- [X] T010 [P] [US1] [US2] Write `backend/src/test/java/com/pim/backend/lov/service/ListOfValuesServiceBulkLoadTest.java` with in-memory repositories: check returns the summary and saves nothing and assigns no ID; apply with the check's token creates new lists (feature 003 IDs in file order), updates existing ones, records one import change record per list, returns the result with system IDs; apply with a different token, after a list was renamed or deleted, or after a list with a new list's name was created → `BulkLoadOutdatedException`, nothing saved; without `ATTRIBUTE_CREATE` → `ListChangeNotPermittedException` for check and apply; an exhausted list sequence → nothing written; a repository that fails on the second write → the first inserted list deleted and replaced lists restored (D-2–D-4, FR-010, FR-011, FR-015)
- [X] T011 [P] [US1] [US2] Write `backend/src/test/java/com/pim/backend/lov/controller/ListOfValuesBulkLoadControllerIntegrationTest.java` against the local MongoDB: `POST /api/v1/lists-of-values/bulk-load/check` with a CSV and with an XLSX → 200 summary matching the contract, lists unchanged; `POST /api/v1/lists-of-values/bulk-load` with file + token → 200 result, lists stored (leading-zero IDs kept); invalid rows → 422 `INVALID_IMPORT` with `rowErrors`; `.xls`, missing Lista column, 6 MB file → 422 file problems; no file → 400 `REQUIRED`; no token → 400 `CHECK_TOKEN_REQUIRED`; stale token → 409 `BULK_LOAD_OUTDATED`; no permission → 403 (contract `lov-bulk-load.openapi.yaml`)

### Implementation for User Stories 1 and 2

- [X] T012 [US1] [US2] Implement `backend/src/main/java/com/pim/backend/lov/service/BulkLoadPlan.java` (pure, no storage or clock): group rows, validate list names with `ListName`, reuse `ValuesImport` per existing list and for new draft lists, collect every row error into one `InvalidImportException`, expose the planned changes and the lists to insert/replace (T009 green)
- [X] T013 [US1] [US2] Add `checkBulkLoad(byte[] file, AttributePermissions)` and `applyBulkLoad(byte[] file, String checkToken, String user, AttributePermissions)` to `backend/src/main/java/com/pim/backend/lov/service/ListOfValuesService.java` under the existing lock: permission → read (T008) → plan (T012) → token (SHA-256 hex of the file bytes, the planned changes and each touched list's content, research R-05) → on apply: compare tokens, assign IDs via `IdentifierService` (`LIST_OF_VALUES`, automatic), insert new lists then replace changed ones with compensation on failure, record `ChangeRecorder.valuesImported` per list (data-model "State and operation order"; T010 green)
- [X] T014 [P] [US1] [US2] Add DTOs `BulkLoadSummaryResponse`, `BulkLoadResultResponse`, `PlannedListChangeResponse` in `backend/src/main/java/com/pim/backend/lov/dto/` matching the contract schemas
- [X] T015 [US1] [US2] Add `backend/src/main/java/com/pim/backend/lov/controller/ListOfValuesBulkLoadController.java` (HTTP only): `POST /api/v1/lists-of-values/bulk-load/check` and `POST /api/v1/lists-of-values/bulk-load` (multipart `file`, `checkToken`), 5 MB check → `IMPORT_TOO_LARGE`, permissions and user from `LovRequestReader`; map `BulkLoadOutdatedException` → 409 and missing `checkToken` → 400 `CHECK_TOKEN_REQUIRED` in `LovExceptionHandler.java` (T011 green)
- [X] T016 [P] [US1] [US2] Add `checkBulkLoad(file)`, `applyBulkLoad(file, checkToken)` and their types (`BulkLoadSummary`, `BulkLoadResult`, `PlannedListChange`) to `frontend/src/api/listsOfValuesApi.ts`, reusing the existing problem/rowErrors parsing; tests in `frontend/src/api/listsOfValuesApi.test.ts` (multipart body, permission header, 200, 409, 422 with `rowErrors`)
- [X] T017 [US1] [US2] Write `frontend/src/listsOfValues/BulkLoadPanel.test.tsx` first, then implement `frontend/src/listsOfValues/BulkLoadPanel.tsx` (+ `.module.css`) per [bulk-load-ui.md](contracts/bulk-load-ui.md): file input `.xlsx,.csv`, «Revisar archivo», «Revisando archivo…», errors table «Fila · Columna · Problema», file-level error notification, summary «Resumen de la carga» with the singular/plural sentence, table «Lista · Acción · Valores nuevos · Valores actualizados», «Nada se ha guardado todavía.», «Confirmar carga» / «Cancelar», «Aplicando carga…», success message, 409 warning back to the file step, choosing another file clears the summary, `onApplied` callback

**Checkpoint**: Through the API and the panel, a file loads several lists with a summary and
confirmation; every error is reported and nothing is saved on error or on a stale token.

---

## Phase 4: User Story 3 — Two actions and templates (Priority: P2)

**Goal**: The page header offers «Crear LOV» and «Carga Masiva de LOV»; the bulk card offers templates
in Excel and CSV (FR-001–FR-004).

**Independent Test**: quickstart scenarios 1–3 and 12.

### Tests for User Story 3 ⚠️

- [X] T018 [P] [US3] Write `backend/src/test/java/com/pim/backend/lov/service/BulkLoadTemplatesTest.java` (CSV with BOM and header `Lista,ID,Etiqueta,Equivalencia` + example row; XLSX read back with `XlsxRecords` gives the same header and row, ID cell text-formatted) and add template cases to `ListOfValuesBulkLoadControllerIntegrationTest.java`: `GET /api/v1/lists-of-values/bulk-load/template?format=xlsx|csv` → 200 attachment with the content types of the contract, no format → CSV, unknown format → 400 `INVALID_FORMAT` (research R-12)
- [X] T019 [P] [US3] Update `frontend/src/pages/ListsOfValuesPage.test.tsx`: with `ATTRIBUTE_CREATE` the header shows «Crear LOV» and «Carga Masiva de LOV», each opening its card (one at a time); the create card is titled «Crear LOV» and creates a list as before; the bulk card shows «Descargar plantilla Excel» / «Descargar plantilla CSV» links; without the permission no button or card; a successful load reloads the overview; the per-list «Carga masiva» still works (FR-004)

### Implementation for User Story 3

- [X] T020 [US3] Implement `backend/src/main/java/com/pim/backend/lov/service/BulkLoadTemplates.java` (CSV bytes; XLSX built once with POI and cached: sheet «Listas de valores», bold header, example row `Color, ROJ, Rojo, RED`, ID column text format) and the `GET /api/v1/lists-of-values/bulk-load/template` endpoint in `ListOfValuesBulkLoadController.java` with `plantilla-listas-de-valores.csv|xlsx` file names (T018 green)
- [X] T021 [US3] Add `bulkLoadTemplateUrl(format)` to `frontend/src/api/listsOfValuesApi.ts` and update `frontend/src/pages/ListsOfValuesPage.tsx`: header actions in `PageHeader`, the create card titled and buttoned «Crear LOV» with «Cancelar», the `BulkLoadPanel` card with the template links and intro text, reload of the overview on `onApplied` (T019 green)

**Checkpoint**: All three stories work; feature 006's screens unchanged apart from the create card's
labels.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T022 [P] Add a dated pointer to feature 013 in `specs/006-lov-management/spec.md` (FR-011: the per-list import stays; bulk load of several lists in feature 013; «Crear lista» relabelled «Crear LOV») and in `specs/006-lov-management/contracts/lists-of-values.openapi.yaml` (description: see `specs/013-lov-bulk-load/contracts/lov-bulk-load.openapi.yaml`; file message no longer says CSV)
- [X] T023 [P] Add the feature 013 row to `README.md` (features table) and mention Excel/CSV bulk load
- [X] T024 Performance check (SC-002): a 50,000-row CSV and XLSX over several lists checked and applied in under 30 s each on the local machine; record the timings in this file. *Measured 2026-11-23 on the local machine through the API (5 lists × 10,000 rows): CSV check 0.51 s, apply 1.14 s (5 lists created); Excel check 2.82 s, apply 3.03 s (5 lists updated, 50,000 values).*
- [X] T025 Run the full checks: `cd backend; .\gradlew.bat clean build`; `cd frontend; npm run lint; npm run format:check; npm test; npm run build` — all green
- [X] T026 Run [quickstart.md](quickstart.md) scenarios 1–12 against a separate database (`pim-quickstart-013`, port 8091) and in the browser; drop the database afterwards. *Done 2026-11-23: API scenarios 9–11 and the templates on port 8091 (database dropped); page layout checked in the browser. The browser load end to end needs the backend on 8080 restarted with this code.*

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none. T002 before T004/T008 (shared record shape).
- **Foundational (Phase 2)**: after Setup; blocks every story. T003 → T004; T007 → T008 (needs T004);
  T005, T006 in parallel.
- **US1 + US2 (Phase 3)**: after Phase 2. Tests T009–T011 first; T012 → T013 → T015; T014 in parallel
  with T012/T013; T016 → T017 (frontend can start once the contract is fixed).
- **US3 (Phase 4)**: after Phase 2; T020 needs T004 (template read back in tests) and the controller of
  T015; T021 needs T017 (panel) and T016.
- **Polish (Phase 5)**: after the stories.

### User Story Dependencies

- **US1 / US2 (P1)**: one phase — the check that reports errors is the one that builds the summary.
- **US3 (P2)**: uses the panel and controller from Phase 3; independently testable through the page
  actions and templates.

### Parallel Opportunities

- T003, T005, T006 (different files) in Phase 2.
- T009, T010, T011 (tests) together; T014 and T016 alongside the service work.
- T018 and T019 together; T022 and T023 together.

## Parallel Example: Phase 3 tests

```text
Task: "BulkLoadPlanTest in backend/src/test/java/com/pim/backend/lov/service/BulkLoadPlanTest.java"
Task: "ListOfValuesServiceBulkLoadTest in backend/src/test/java/com/pim/backend/lov/service/ListOfValuesServiceBulkLoadTest.java"
Task: "ListOfValuesBulkLoadControllerIntegrationTest in backend/src/test/java/com/pim/backend/lov/controller/"
```

## Implementation Strategy

### MVP First (Phase 3)

1. Phase 1 → Phase 2 → Phase 3 (US1 + US2): a file loads several lists with summary, confirmation and
   the full error report — the feature request's core.
2. Validate with quickstart scenarios 4–11 through the API and the panel.

### Incremental Delivery

1. Setup + Foundational → both formats read identically.
2. US1 + US2 → bulk load works end to end (MVP).
3. US3 → page actions «Crear LOV» / «Carga Masiva de LOV» and templates.
4. Polish → docs, performance, full checks, quickstart.

## Notes

- Feature 006's per-list import, its CSV parser behaviour and its tests must stay green throughout
  (FR-004); only the `INVALID_IMPORT_FILE` message text changes (T006).
- Nothing in the root package; everything backend in `com.pim.backend.lov` (constitution XII).
- Commit only when the project owner asks.
