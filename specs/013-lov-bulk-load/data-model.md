# Data Model: Bulk Load of Lists of Values (Carga Masiva de LOV)

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Date**: 2026-11-23

No new collection and no change to stored documents: lists are created and changed in feature 006's
`lists_of_values` collection, with feature 006's `ListOfValues` / `ListValue` rules. Everything below
lives only for the duration of a request.

## Bulk load file (input, not stored)

| Part | Rule | Source |
|---|---|---|
| Format | `.xlsx` (zip signature) or CSV (anything else); legacy `.xls` refused | R-02 |
| Size | ≤ 5 MB; ≤ 50,000 non-blank data rows | FR-014, R-09 |
| Sheet (Excel) | First sheet only | A-004 |
| Header | First non-blank row; columns `Lista`, `ID`, `Etiqueta` required, `Equivalencia` optional; any order; matched ignoring case, accents and surrounding spaces; unknown or repeated column refuses the file | FR-005, R-04 |
| Row number | As the user sees it (header = row 1); blank rows skipped, not counted | FR-012, R-03 |

### Bulk load row

| Field | Rule | Error (column, code) |
|---|---|---|
| `list` | Required; trimmed; 1–80 characters (feature 006 FR-003); names a list ignoring case and surrounding spaces | Lista, `REQUIRED` / `INVALID_LIST_NAME` |
| `id` | Optional; empty → generated; otherwise feature 006 value ID rules (text, ≤ 40, allowed characters) | ID, feature 006 codes |
| `label` | Required; feature 006 label rules (≤ 200) | Etiqueta, `REQUIRED` / feature 006 codes |
| `equivalence` | Optional; feature 006 rules (≤ 200); empty → unchanged on update, none on add | Equivalencia, feature 006 codes |
| (list, id) pair | Unique in the file, ignoring case; every duplicate row is reported | ID, `DUPLICATE_ID_IN_FILE` |

## Bulk load plan (computed by the check; pure, in `lov.service`)

`BulkLoadPlan.from(rows, existingListsByNameKey)` groups the checked rows by list name key
(`ListName` key: trimmed, lower case) in order of first appearance, then for each group:

- **existing list** → feature 006's `ValuesImport.apply(list, rows, now)` gives the changed list and
  its added/updated counts (same rules as the per-list import);
- **new list** → a draft list (no system ID yet) whose name is the first row's text, equivalence
  column on if any row has an equivalence (R-08), with the rows applied as additions.

Every row problem of every group is collected before failing (`InvalidImportException`, FR-012). A
group that would exceed 50,000 values fails the whole file (`ListTooLargeException`, list named).

### Planned list change

| Field | Meaning |
|---|---|
| `name` | The list's name (existing) or the name it will get (new) |
| `systemId` | Existing list's ID; empty for a new list until applied |
| `action` | `CREATE` or `UPDATE` |
| `valuesAdded` | Values that will be added |
| `valuesUpdated` | Existing values whose label (and equivalence) will be replaced |
| `basedOn` | Existing list's content: name, equivalence setting, ID counter, values (input of the check token) |

### Summary (response of the check)

| Field | Meaning |
|---|---|
| `lists` | Planned list changes, in file order |
| `listsToCreate`, `listsToUpdate` | Counts |
| `valuesToAdd`, `valuesToUpdate` | Totals |
| `checkToken` | SHA-256 (hex) of the file bytes + every planned change + every `basedOn` (R-05) |

### Result (response of the apply)

Same shape as the summary with past-tense meaning (`listsCreated`, `listsUpdated`, `valuesAdded`,
`valuesUpdated`) and each list's `systemId` filled in.

## State and operation order (apply)

All under `ListOfValuesService`'s lock (feature 006 R-12):

1. Permission check (`ATTRIBUTE_CREATE`, feature 006 A-008).
2. Read the file (R-02) → rows; file problems → 422.
3. Build the plan against the current lists → row problems → 422.
4. Recompute the token; different from the request's → 409 `BULK_LOAD_OUTDATED`.
5. Assign a feature 003 system ID to each new list, in plan order (R-07); exhausted → 409, nothing
   written.
6. Insert each new list, then replace each changed list; on a storage failure, undo what was written
   (delete inserted, restore replaced) and fail (R-06).
7. Record one import change record per list (R-11).
8. Return the result.

The check endpoint runs steps 1–3 and returns the summary with the token.

## Error codes (lov domain)

| Code | Status | When |
|---|---|---|
| `INVALID_IMPORT` | 422 | One or more rows invalid; `rowErrors` lists them (existing) |
| `INVALID_IMPORT_FILE` | 422 | Unreadable file, wrong header, `.xls`, empty (existing; message no longer says "CSV") |
| `IMPORT_TOO_LARGE` | 422 | Over 5 MB or 50,000 rows (existing) |
| `LIST_TOO_LARGE` | 422 | A list would exceed 50,000 values (existing; message names the list) |
| `BULK_LOAD_OUTDATED` | 409 | Token mismatch on apply (new) |
| `CHECK_TOKEN_REQUIRED` | 400 | Apply without `checkToken` (new) |
| `ATTRIBUTE_CREATE_NOT_PERMITTED` | 403 | No permission (existing) |
| `SEQUENCE_EXHAUSTED` | 409 | Feature 003 has no free list ID (existing, feature 003) |
