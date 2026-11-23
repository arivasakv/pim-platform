# Implementation Plan: Bulk Load of Lists of Values (Carga Masiva de LOV)

**Branch**: `013-lov-bulk-load` | **Date**: 2026-11-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-lov-bulk-load/spec.md` (approved 2026-11-23)

**Status**: **Approved** by the project owner on 2026-11-23, including decisions D-1 to D-7 (constitution X).

## Summary

Add a top-level bulk load to the lists-of-values page: one CSV or Excel (.xlsx) file with the columns
Lista, ID, Etiqueta and optional Equivalencia creates the lists it names that do not exist and adds or
updates values in those that do, with feature 006's value rules. The load has two steps: `/check` reads
and checks the whole file and returns a summary and a `checkToken` without saving or assigning IDs; the
apply call sends the same file and token, re-checks, and saves every list or none. Excel is read with
Apache POI's streaming reader (new dependency, D-1); CSV reuses feature 006's tokenizer. All-or-nothing
over several MongoDB documents is achieved under the lov service lock with compensation on a storage
failure (no transactions on a standalone MongoDB). The page header gets «Crear LOV» and «Carga Masiva de
LOV»; a new `BulkLoadPanel` shows templates, the error table, the summary with confirm/cancel, and the
result. The per-list import of feature 006 stays.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2 (Gradle, `-Werror`, Spotless); TypeScript strict,
React 19, Vite

**Primary Dependencies**: existing (`MongoTemplate`, lov and identifier services, design-system
components) plus **`org.apache.poi:poi-ooxml` 5.4.x** (exact version pinned in `build.gradle`; D-1,
research R-01) for reading `.xlsx` and writing the Excel template

**Storage**: MongoDB, feature 006's `lists_of_values` collection, unchanged schema; no new collection

**Testing**: JUnit 5 unit tests (CSV/XLSX readers with generated workbooks, `BulkLoadPlan`, check
token, compensation with a failing in-memory repository), service tests with in-memory repositories,
Spring integration tests against the local MongoDB (controller: template, check, apply, 403/409/422);
Vitest + Testing Library with mocked `fetch` (API client, `BulkLoadPanel`, page actions)

**Target Platform**: backend on the JVM; frontend in desktop and tablet browsers (768 px and up)

**Project Type**: web application (backend + frontend)

**Performance Goals**: SC-002 — a 50,000-row file checked, and applied, in under 30 s each: streaming
read, one pass grouping rows by list, feature 006's linear `ValuesImport` per list, one write per list

**Constraints**:
- Lock: the whole check and apply run under `ListOfValuesService`'s existing lock; IDs are assigned
  through `IdentifierService` (its own lock, already taken from inside the lov lock by list creation),
  so no new lock order.
- Business rules in `lov.service`/`lov.model`; controllers only map HTTP (constitution III).
- RFC 9457 problems through `LovExceptionHandler`, Spanish messages; reuse of `INVALID_IMPORT` with
  `rowErrors` (R-10).
- Files are never stored (A-006); 5 MB and 50,000 rows per file; 50,000 values per list.
- ESLint `set-state-in-effect`; Prettier; no jest-dom.

**Scale/Scope**: backend: 1 dependency, ~8 new classes in `lov` (readers, plan, summary/result, token,
outdated exception, controller, DTOs), 3 endpoints, 2 new error codes, CSV tokenizer extracted;
frontend: 3 API functions, 1 panel component, page header actions; docs: contract, UI contract,
feature 006 notes, README row.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec approved 2026-11-23 with clarifications (OQ-001, OQ-002); tasks will cite FR/US |
| II | Technology stack | PASS | Apache POI is a library (no new language, framework, build tool or runtime); approved as D-1, recorded in Complexity Tracking |
| III | Modular monolith | PASS | Owned by `lov`; uses `IdentifierService` (public); controller HTTP only; rules in service/model |
| IV | Business scope | PASS | ERF 1.1 §3.2.1, bulk import paragraph, §3.1; HU2.8 (tier 2); owner's requirement |
| V | Code quality | PASS | New dependency justified (R-01); pure `BulkLoadPlan` and readers; CSV tokenizer shared, not copied |
| VI | Testing | PASS | Unit, service, integration and frontend tests for every rule (columns, rows, duplicates, limits, token, compensation); full suites green |
| VII | API design | PASS | Contract first (`contracts/lov-bulk-load.openapi.yaml`); plural resource path under `/api/v1/lists-of-values`; DTOs; 200/400/403/409/422 |
| VIII | Security | PASS | Same development permission; backend checks authoritative; POI zip-bomb guard and 5 MB limit; files not stored |
| IX | Development scope | PASS | No new storage: lists stay in `lists_of_values` (already allowed) |
| X | AI governance | PASS | D-1 to D-7 approved by the project owner on 2026-11-23 |
| XI | Sources | PASS | ERF/HU cited (tier 2); owner's requirement source open (OQ-003) |
| XII | Package structure | PASS | `lov.{controller,service,model,dto}`; nothing in the root package |

**Gate result**: PASS (D-1 to D-7 approved 2026-11-23).
**Post-design re-check**: PASS — design adds one dependency (D-1) and no storage, domain or lock order.

## Recorded Decisions (approved 2026-11-23)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | Reading Excel | Add **Apache POI `poi-ooxml`** (5.4.x, pinned); streaming reader for the first sheet; `DataFormatter` gives the text the user sees (leading zeros, formulas' values); POI also writes the Excel template | R-01 |
| D-2 | Check, then confirm | Two stateless calls: `POST …/bulk-load/check` → summary + `checkToken` (SHA-256 of the file, the planned changes and the touched lists' content); `POST …/bulk-load` with file + token → re-check, 409 `BULK_LOAD_OUTDATED` if the token differs, else apply. No upload stored | R-05 |
| D-3 | All or nothing over several lists | Under the lov lock: check and build every list in memory → assign IDs → insert new lists, replace changed ones; on a storage failure, delete the inserted lists and restore the replaced ones. A process crash mid-write may leave a partial load (accepted risk for the POC) | R-06 |
| D-4 | System IDs of new lists | Feature 003's automatic sequence only, assigned after the check and token pass, in file order; an exhausted sequence saves nothing (IDs already assigned in that request stay registered without a list) | R-07 |
| D-5 | New lists | Name as written in its first row (trimmed); equivalence column **on** if any of its rows has an equivalence; existing lists keep their setting | R-08 |
| D-6 | Formats and limits | Excel detected by the zip signature, else CSV (feature 006 rules); `.xls` refused; first sheet only; 5 MB, 50,000 data rows, 50,000 values per list; blank rows skipped | R-02–R-04, R-09 |
| D-7 | UI | Header actions «Crear LOV» / «Carga Masiva de LOV» opening one card at a time; `BulkLoadPanel`: templates (Excel, CSV), file, «Revisar archivo», errors table or summary table with «Confirmar carga» / «Cancelar», result message and overview reload | R-13, [UI contract](contracts/bulk-load-ui.md) |

## Project Structure

### Documentation (this feature)

```text
specs/013-lov-bulk-load/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/lov-bulk-load.openapi.yaml   # new endpoints (extends feature 006's contract)
├── contracts/bulk-load-ui.md              # exact Spanish texts and behaviour
├── checklists/requirements.md
└── tasks.md                               # /speckit-tasks
```

### Source Code (repository root)

```text
backend/build.gradle                                   # + org.apache.poi:poi-ooxml (D-1)
backend/src/main/java/com/pim/backend/lov/
├── service/CsvRecords.java              # NEW: RFC 4180 tokenizer extracted from CsvValuesParser
├── service/CsvValuesParser.java         # uses CsvRecords; behaviour unchanged (feature 006)
├── service/XlsxRecords.java             # NEW: first sheet via POI streaming, DataFormatter texts
├── service/BulkLoadFileReader.java      # NEW: detects format, maps Lista/ID/Etiqueta/Equivalencia,
│                                        #      row numbers, limits → BulkLoadRow list
├── service/BulkLoadPlan.java            # NEW: pure; groups rows by list name, reuses ValuesImport,
│                                        #      collects every row error, builds new/changed lists
├── service/BulkLoadTemplates.java       # NEW: CSV and XLSX template bytes (cached)
├── service/ListOfValuesService.java     # + checkBulkLoad, applyBulkLoad (lock, token, IDs,
│                                        #   writes with compensation, change records)
├── model/BulkLoadRow.java, PlannedListChange.java, BulkLoadSummary.java,
│         BulkLoadResult.java, BulkLoadOutdatedException.java          # NEW
├── model/LovErrorCode.java              # + BULK_LOAD_OUTDATED, CHECK_TOKEN_REQUIRED; file message
│                                        #   without "CSV"; duplicate message names the list
├── dto/BulkLoadSummaryResponse.java, BulkLoadResultResponse.java,
│       PlannedListChangeResponse.java                                  # NEW
├── controller/ListOfValuesBulkLoadController.java   # NEW: template, check, apply (HTTP only)
└── controller/LovExceptionHandler.java  # + 409 outdated, 400 token required, 400 invalid format
backend/src/test/java/com/pim/backend/lov/...  # CsvRecordsTest, XlsxRecordsTest (workbooks built
                                               # with POI in the test), BulkLoadFileReaderTest,
                                               # BulkLoadPlanTest, ListOfValuesServiceBulkLoadTest
                                               # (token, IDs, compensation), BulkLoadControllerIntegrationTest

frontend/src/api/listsOfValuesApi.ts (+ test)          # checkBulkLoad, applyBulkLoad, bulkLoadTemplateUrl
frontend/src/listsOfValues/BulkLoadPanel.tsx (+ .module.css, test)   # NEW
frontend/src/pages/ListsOfValuesPage.tsx (+ test)      # header actions, create card «Crear LOV»

specs/006-lov-management/spec.md, contracts/lists-of-values.openapi.yaml   # dated pointers to 013
README.md                                              # feature row
```

**Structure Decision**: Web application (`backend/`, `frontend/`), as features 001–012. Everything new
on the backend lives in the existing `lov` domain (constitution XII); the frontend adds one folder for
the lists-of-values components, like `attributes/` and `taxonomy/`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New library: Apache POI `poi-ooxml` (D-1) | The owner requires Excel files (2026-11-23); reading what users see in Excel (number formats, leading zeros, shared and inline strings, formulas' values) and writing an `.xlsx` template | A hand-written `.xlsx` reader/writer would re-implement number formats and zip-bomb protection with more code and risk; asking for CSV only contradicts the owner's decision |
| Compensation instead of a transaction (D-3) | FR-011 needs all lists or none; the local MongoDB is standalone (no multi-document transactions) | A replica set changes every developer's setup and the operating model (ADR-level); one document for all lists breaks feature 006's storage |
