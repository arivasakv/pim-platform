# Research: Bulk Load of Lists of Values (Carga Masiva de LOV)

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-23

Each decision lists what was chosen, why, and what was rejected. Decisions marked **D-n** need the
project owner's approval with the plan (constitution X).

## R-01 Reading Excel files: Apache POI (D-1)

- **Decision**: Add `org.apache.poi:poi-ooxml` (5.4.x, pinned to an exact version in `build.gradle`) to the
  backend, used only in the `lov` domain to read the first sheet of an `.xlsx` file and to write the
  Excel template. Reading uses POI's **streaming** reader (`XSSFReader` + `XSSFSheetXMLHandler`), so a
  50,000-row sheet is read row by row without loading the workbook into memory. Cell text is what the
  user sees in Excel (`DataFormatter`): a number cell formatted as `0000` gives `0001`, a plain number
  `12` gives `12`, a formula gives its last calculated value.
- **Why**: The owner chose Excel and CSV (2026-11-23). `.xlsx` is a zip of XML parts with shared
  strings, inline strings, number formats and styles; reading what the user *sees* (leading zeros,
  dates, formulas) needs number-format handling that POI already implements and tests. POI also
  guards against zip bombs (`ZipSecureFile` inflate-ratio check). It is the standard Java library for
  Office files and is Apache-licensed.
- **Alternatives**: (a) A hand-written `.xlsx` reader over `java.util.zip` and the JDK XML parser
  (rejected: shared strings, inline strings, rich text runs and number formats would each need code
  and tests, and leading-zero IDs depend on number formats; zip-bomb protection would be ours to
  build). (b) FastExcel / other small readers (rejected: less known, and the template writer would
  still need a second library). (c) Ask users to save as CSV (rejected: contradicts the owner's
  decision).
- **Constitution**: II and V — a library, not a framework or runtime; justified here and in the plan's
  Complexity Tracking. Only `poi-ooxml` is declared; its transitive dependencies are POI's own.

## R-02 One reader for both formats, detected by content

- **Decision**: A file is Excel when its first bytes are the zip signature (`PK\x03\x04`), otherwise it
  is read as CSV with feature 006's rules (UTF-8 with or without BOM, comma or semicolon, RFC 4180
  quotes). The file name is not trusted. The CSV tokenizer of `CsvValuesParser` is extracted into a
  shared `CsvRecords` class so feature 006's per-list import and this load read CSV identically; a new
  `XlsxRecords` class gives the same output (records of cell texts with their row numbers) for Excel.
  A legacy `.xls` file (OLE2 signature) is refused with «Guarda el archivo como Excel (.xlsx) o CSV.».
- **Why**: SC-004 requires the same result for the same data in both formats; one record shape after
  reading makes the rules format-independent.
- **Alternatives**: trust the extension or the multipart content type (rejected: easy to get wrong
  when users rename files).

## R-03 Row numbers and blank rows

- **Decision**: Row numbers are the row numbers the user sees: the header is row 1 in both formats; in
  Excel the sheet's own row index + 1 is used, so a blank row skipped in the middle keeps the later
  numbers right. Blank rows (every cell empty) are skipped and do not count toward the 50,000 limit.
  The header is the first non-blank row.
- **Why**: FR-012 asks for the row «as seen in the file».

## R-04 Columns

- **Decision**: Header cells are matched ignoring letter case, accents and surrounding spaces:
  `Lista`, `ID`, `Etiqueta` required; `Equivalencia` optional; any other column, or a repeated one,
  refuses the file (as feature 006 does). Column order is free.
- **Why**: Same tolerance as feature 006 (users already know it); unknown columns usually mean a wrong
  file.

## R-05 Check first, then confirm: two stateless calls with a check token (D-2)

- **Decision**: Two endpoints (contract [lov-bulk-load.openapi.yaml](contracts/lov-bulk-load.openapi.yaml)):
  1. `POST /api/v1/lists-of-values/bulk-load/check` (multipart `file`): reads and checks the whole
     file against the current lists and returns the **summary** and a `checkToken`; nothing is saved
     and no system ID is assigned.
  2. `POST /api/v1/lists-of-values/bulk-load` (multipart `file` + `checkToken`): reads and checks the
     same file again, recomputes the token, and applies it only if the token is unchanged; otherwise
     409 `BULK_LOAD_OUTDATED` («Las listas cambiaron desde la revisión. Vuelve a revisar el archivo.»).
  The token is a SHA-256 over the file bytes, the planned changes (lists to create, per list values to
  add and update) and the full content of every existing list the file touches (name, equivalence
  setting, ID counter and values). *Changed during implementation (2026-11-23): the lists' `updatedAt`
  was first proposed, but two changes within the same instant keep it, so the content is used.* The browser keeps the
  chosen `File` and sends it again on confirmation.
- **Why**: FR-010 needs a summary before saving and a re-check on apply; A-006 says files are not kept.
  A stateless token needs no server storage, survives restarts, and detects both a different file and
  any change to the lists involved (another user's edit, a deleted list, a new list with a name the
  file would create).
- **Alternatives**: (a) store the parsed file server-side under a load ID (rejected: new storage and
  expiry handling, against A-006); (b) one call with a `dryRun` flag (rejected: same behaviour, less
  clear contract); (c) apply without a token and just re-check (rejected: the user could confirm a
  summary that no longer matches what is applied).

## R-06 All or nothing across several lists without transactions (D-3)

- **Decision**: Lists are one MongoDB document each (feature 006 R-02) and the local MongoDB is a
  standalone server, so multi-document transactions are not available. The load is applied under
  `ListOfValuesService`'s lock (feature 006 R-12), which every list change already takes, in this
  order: (1) check everything and build every new and changed list in memory; (2) assign the system
  IDs of the new lists (R-07); (3) write: insert each new list, then replace each changed list. If a
  write fails, the lists already written are **compensated** before the error is returned: inserted
  lists are deleted and replaced lists are written back with their previous content. Nothing else can
  change lists meanwhile (same lock, single deployable — constitution III).
- **Why**: FR-011 requires every list or none. Within the single application, the lock makes the
  write phase exclusive; a failure there can only be a storage error, which compensation undoes.
- **Alternatives**: (a) enable a replica set to get transactions (rejected: changes every developer's
  local setup and the operating model; ADR-level change); (b) one document holding all lists
  (rejected: contradicts feature 006's storage); (c) apply list by list without compensation
  (rejected: violates FR-011).
- **Residual risk**: if the process dies during the write phase, some lists may be written and others
  not. Recorded in the plan; acceptable for this POC (no restart-safe journal).

## R-07 System IDs for new lists (D-4)

- **Decision**: New lists get their ID from feature 003's automatic sequence for `LIST_OF_VALUES`
  (`IdentifierService.assign`, no manual ID, A-003), only after the whole file passed the check and
  the token matched, in file order of first appearance. If the sequence runs out, nothing is written
  and feature 003's message is returned (409 `SEQUENCE_EXHAUSTED`, as list creation does today). IDs
  already registered for earlier new lists of the same file stay registered without a list (feature
  003 has no release operation; the same happens today when a list insert fails after assignment).
- **Why**: No IDs are consumed by checks or by refused files; the rare exhausted-sequence case leaves
  only unused IDs, never partial data.
- **Alternatives**: assign during the check (rejected: every summary would burn IDs).

## R-08 New lists: name and equivalence column (D-5)

- **Decision**: A new list takes the name as written in the first row that names it, trimmed (feature
  006 FR-003, at most 80 characters); other rows may write it with different letter case or spaces
  (same list). Its equivalence column is **on** when any of its rows has a non-empty Equivalencia
  cell, otherwise off. Existing lists keep their equivalence setting; their values receive
  equivalences as in feature 006's import.
- **Why**: A list created with equivalences in the file should show them; an existing list's setting
  is the owner's choice and the file must not change it silently.

## R-09 Limits

- **Decision**: At most **5 MB** per file and **50,000 data rows** (feature 006 R-08, same for both
  formats); a list may not exceed **50,000 values** after the load (`ListTooLargeException`, reported
  with the list name). Over a limit, the whole file is refused (422). Spring's multipart limits stay
  as set by feature 006 (10 MB / 11 MB) so the 5 MB check answers 422.
- **Why**: FR-014 and A-005; the existing limits are known to perform (SC-002).

## R-10 Error report

- **Decision**: Row problems reuse feature 006's problem format: 422 `INVALID_IMPORT` with `rowErrors`
  `[{row, column, code, message}]`, sorted by row and column (Lista, ID, Etiqueta, Equivalencia). New
  row codes: `INVALID_LIST_NAME` for a blank or too long list name (column Lista); duplicates are
  per list (`DUPLICATE_ID_IN_FILE`, message names the list). File problems reuse
  `INVALID_IMPORT_FILE` (message no longer says "CSV": «No se pudo leer el archivo: …») and
  `IMPORT_TOO_LARGE`. All messages are in Spanish.
- **Why**: The frontend already renders this format for feature 006's import (FR-012).

## R-11 Change records

- **Decision**: Applying a file records, per list it creates or changes, feature 006's import change
  record (`ChangeRecorder.valuesImported`, with added/updated counts and the user), so the existing
  change history covers bulk loads.
- **Why**: Feature 006 FR-019 records imports; a bulk load is the same operation over several lists.

## R-12 Templates

- **Decision**: `GET /api/v1/lists-of-values/bulk-load/template?format=csv|xlsx` returns
  `plantilla-listas-de-valores.csv` (UTF-8 with BOM, header `Lista,ID,Etiqueta,Equivalencia` and one
  example row `Color,ROJ,Rojo,RED`) or `plantilla-listas-de-valores.xlsx` (sheet «Listas de valores»,
  bold header, the same example row, the ID column formatted as text so leading zeros stay). The Excel
  bytes are built once and cached.
- **Why**: FR-003; an example row shows the expected shape; text-formatted IDs avoid the most common
  Excel mistake (lost leading zeros).

## R-13 Frontend flow

- **Decision**: The page header gets two actions, **«Crear LOV»** and **«Carga Masiva de LOV»**, shown
  only with the permission; each opens its card in the left column (one at a time): the existing
  create form (card title «Crear LOV», button «Crear LOV») or a new `BulkLoadPanel`. The panel: template
  links (CSV, Excel), file input (`.csv,.xlsx`), «Revisar archivo» → errors table (Fila, Columna,
  Problema) or summary table (Lista, Acción «Crear»/«Actualizar», Valores nuevos, Valores
  actualizados, plus totals) with «Confirmar carga» and «Cancelar» → result message and the list
  overview reloaded. Choosing another file clears the summary. A 409 shows its message and returns to
  the file step.
- **Why**: FR-001–FR-003, FR-010, FR-012, FR-013 with the existing design-system components; the
  backend decides everything (constitution VIII), the frontend only shows it.
