# Data Model: List of Values Management

**Feature**: `006-lov-management` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md)

Stored in MongoDB (constitution 2.4.0; research R-02, ADR 0003 amendment). Model types live in
`com.pim.backend.lov.model`; Spring Data documents stay in `lov.repository` (constitution XII).

## Collections

| Collection | Document | Indexes |
|---|---|---|
| `lists_of_values` | `{ _id: <list system ID upper-cased>, systemId, name, nameKey, equivalencesEnabled, nextGeneratedNumber, values: [ { id, idKey, label, equivalence } ], createdAt, updatedAt }` | `_id`; unique `lov_name_unique` on `nameKey` |
| `lov_changes` | `{ _id: <UUID>, at, user, operation, listId, listName, valueIds, added, updated }` | `_id`; `{ listId: 1, at: -1 }` |

`nameKey` is the name lower-cased (`Locale.ROOT`); `idKey` is the value ID upper-cased (`Locale.ROOT`),
as feature 003 compares system IDs.

## ListOfValues (entity)

| Field | Type | Rule |
|---|---|---|
| `systemId` | text | Feature 003 system ID of type `LIST_OF_VALUES` (default `LOV000001`…); manual with feature 003's permission; never changes (FR-001, R-05) |
| `name` | ListName | Required; trimmed; 1–80 characters; unique among lists ignoring case (FR-003, R-06) |
| `equivalencesEnabled` | boolean | Default `false`; turning it off keeps the equivalences (FR-023) |
| `values` | ordered list of ListValue | At most 50,000 (R-08); IDs unique within the list ignoring case (FR-008); order = order added (R-07) |
| `nextGeneratedNumber` | integer ≥ 1 | Counter for generated IDs; never decreases (R-07) |
| `createdAt`, `updatedAt` | timestamp (UTC, millisecond) | Set by the service clock |

- Starts with no values.
- Renaming keeps `systemId`, values and links (User Story 3).
- Deletable only when no attribute is linked (FR-004, R-04); deleting removes its values.
- Linked attributes aren't stored here: they're read from feature 004 (`AttributeService.findLinkedToList`).

## ListName (value object)

Trimmed text, 1–80 characters, case kept; compared ignoring case. Absent → 400 `REQUIRED`; blank or too
long → 400 `INVALID_LIST_NAME`; in use → 409 `LIST_NAME_IN_USE`.

## ListValue (value object inside the list)

| Field | Type | Rule |
|---|---|---|
| `id` | ValueId | Trimmed; 1–40 characters of `A–Z a–z 0–9 - _ .`; case and leading zeros kept; unique in the list ignoring case; immutable (FR-007–FR-010, R-07) |
| `label` | text | Required; trimmed; 1–200 characters; may repeat within the list (A-005) |
| `equivalence` | text, optional | Trimmed; up to 200 characters; blank = none; may repeat (FR-023) |

Errors:
- 400 `INVALID_VALUE_ID` (bad characters or length), `REQUIRED` (label), `INVALID_LABEL`,
  `INVALID_EQUIVALENCE`;
- 409 `VALUE_ID_IN_USE` (manual add with an existing ID);
- 404 `VALUE_NOT_FOUND`.

## Generated value IDs (R-07)

`format(n) = n zero-padded to at least 4 digits`. When generating, `n = nextGeneratedNumber`. While
`format(n)` is taken in the list, `n++`. The ID `format(n)` is used and `nextGeneratedNumber = n + 1`.

## CSV import (FR-011, R-09)

Input: UTF-8 file (BOM optional), delimiter `,` or `;` from the header, header `ID`, `Etiqueta`,
optional `Equivalencia` (any order, case and accents ignored).

| Row | Result |
|---|---|
| `ID` empty | New value with a generated ID |
| `ID` exists in the list (ignoring case) | Label replaced; equivalence replaced if the cell isn't empty |
| `ID` new | New value appended |
| `Etiqueta` empty, `ID` invalid, a field too long, `ID` twice in the file | Error |

The whole file is validated first. If there's any error, 422 `INVALID_IMPORT` with `errors[]` of
`{ row, column, code, message }` and nothing is stored. Otherwise there's one document update, and the
response is `{ added, updated, total }`. A file over 5 MB or 50,000 rows, or one that would push the list
past 50,000 values, is refused as a whole (422 `IMPORT_TOO_LARGE` / `LIST_TOO_LARGE`). An unreadable
file, or one with no `Etiqueta` header, gets 422 `INVALID_IMPORT_FILE`.

## LinkedAttribute (feature 004, read only)

From `AttributeService.findLinkedToList(listId)`: system ID, display name, selection mode (`SINGLE` /
`MULTIPLE`). Shown in the list detail (FR-005, FR-014).

## ChangeRecord (R-11)

`operation` is one of `VALUE_DELETED`, `LIST_DELETED`, `VALUES_IMPORTED`. `user` comes from the
optional development header `X-Pim-User` (`null` when absent). `valueIds` is the deleted value ID, or
empty. `added` and `updated` apply to imports. The record is written after the change; a write failure
is logged and doesn't undo the change.

## State and operation order

```text
create list:     permission → name valid → name free → feature 003 assigns ID → insert
rename / toggle: permission → list exists → name valid → name free (other lists) → update
delete list:     permission → list exists → [attribute lock] no attribute linked → delete → record
add value:       permission → list exists → fields valid → ID free (or generated) → size ≤ 50,000 → update
edit value:      permission → list exists → value exists → label/equivalence valid → update (ID unchanged)
delete value:    permission → list exists → value exists → update (value removed) → record
import:          permission → list exists → file readable → all rows valid → size ≤ 50,000 → one update → record
```
