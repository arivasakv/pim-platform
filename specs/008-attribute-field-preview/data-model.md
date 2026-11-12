# Data Model: Live Attribute Field Preview

**Feature**: `008-attribute-field-preview` | **Research**: [research.md](research.md)

No stored data. No collection, field, endpoint or request changes. The preview only reads the form state
and, for LOV attributes, one list through the existing API.

## Inputs (read only)

| Input | Source | Used for |
|---|---|---|
| `AttributeForm` | the editor's state (feature 004 `attributeForm.ts`) | every preview rule |
| Option lists of the chosen data type | `GET /api/v1/attribute-types`, already loaded by the editor | Spanish labels (reference target, selection mode) |
| List of values (`values`, `valueCount`) | `GET /api/v1/lists-of-values/{id}` via `getList` (feature 006) | LOV preview |

## FieldPreview (derived, in memory)

Computed by `fieldPreview.ts` from the inputs on every render; never stored.

| Field | Meaning |
|---|---|
| `kind` | `none`, `text`, `textarea`, `number`, `checkbox`, `select`, `checkboxes`, `date`, `reference` |
| `label` | "{name} (opcional)" or "Nombre del atributo (opcional)" |
| `hint` | description plus limits/format/currency text |
| `maxLength`, `minLength` | text limits that are usable |
| `min`, `max`, `step` | number limits and step |
| `caseFormat` | `UPPERCASE`, `LOWERCASE` or none |
| `mask` | compiled capture mask, or none |
| `datePattern`, `dateFormat` | for date fields |
| `currency` | `{ code, symbol? }` |
| `reference` | `{ targetLabel, multiple }` |
| `notes` | preview messages for incomplete or invalid settings (spec FR-010) |

## PreviewValue (temporary)

The value typed or chosen in the preview (`string`, `boolean` or `string[]`), held in the state of the
inner field component, which is keyed by `previewKey(form)`. Discarded when any configuration field
changes (new key) or when the form closes (unmount). Never sent or stored (spec FR-011, FR-012).

## ListValuesState (temporary)

`none` (no list chosen) · `loading` · `error` · `ready { values (first 50), total }`. Held by the outer
panel per chosen list ID; a stale answer for a previous list is ignored.
