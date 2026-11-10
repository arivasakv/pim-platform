# Data Model: Attribute Management – Attribute Creation

**Feature**: `004-attribute-creation` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md)

In memory (research R-02). Model types live in `com.pim.backend.attribute.model` and depend on no
Spring, HTTP or DTO type (constitution XII).

## Attribute (entity)

| Field | Type | Rule |
|---|---|---|
| `systemId` | text | From feature 003 (FR-004); identity |
| `displayName` | text | Required; trimmed; 1–120 characters; unique among attributes ignoring letter case (FR-003, FR-023, R-08) |
| `description` | text, optional | Trimmed; up to 1,000 characters; blank = absent (R-08) |
| `dataType` | AttributeDataType | Required; fixed at creation (FR-007) |
| `scope` | `GLOBAL` | Always `GLOBAL` in this feature (FR-018) |
| `properties` | one of the property types below | Only the type matching `dataType`; none for `BOOLEAN` (A-004, R-15) |
| `createdAt` | timestamp (UTC) | Set once |

No group links (FR-017). Never changed after creation in this feature.

## AttributeDataType (enum)

| Code | Spanish label | Properties |
|---|---|---|
| `TEXT` | Texto | TextProperties |
| `NUMERIC` | Numérico | NumericProperties |
| `BOOLEAN` | Booleano | none |
| `LIST_OF_VALUES` | Lista de valores | ListOfValuesProperties |
| `DATE_TIME` | Fecha y hora | DateTimeProperties |
| `CURRENCY` | Moneda | CurrencyProperties |
| `REFERENCE` | Referencia | ReferenceProperties |

## TextProperties (FR-008, FR-009; R-09)

| Field | Rule |
|---|---|
| `minLength` | Optional; whole number 0–100,000 |
| `maxLength` | Optional; whole number 1–100,000; ≥ `minLength` when both given |
| `caseFormat` | Optional; `UPPERCASE` (Mayúsculas), `LOWERCASE` (Minúsculas), `MIXED_CASE` (Capitalización mixta) |
| `captureMask` | Optional; regular expression, 1–500 characters, must compile |
| `richText` | true/false; default false |

## NumericProperties (FR-010; R-10)

| Field | Rule |
|---|---|
| `numberFormat` | Required; `INTEGER` (Entero) or `DECIMAL` (Decimal) |
| `precision` | `DECIMAL`: required, 1–10. `INTEGER`: must be absent |
| `minimum`, `maximum` | Optional (either or both); decimal, ≤ 18 significant digits; whole for `INTEGER`; ≤ `precision` decimals for `DECIMAL`; `minimum ≤ maximum` |

## ListOfValuesProperties (FR-012; R-14)

| Field | Rule |
|---|---|
| `selectionMode` | Required to create; `SINGLE` (Selección única) or `MULTIPLE` (Selección múltiple) |
| `listOfValuesId` | Required to create; an existing list in `ListOfValuesCatalog` |

Order of checks: (1) no lists at all → 422 `LIST_OF_VALUES_NOT_AVAILABLE`, whatever was sent;
(2) missing selection mode or list ID → 400 `REQUIRED`; (3) unknown list → 422
`LIST_OF_VALUES_NOT_FOUND`. An unknown selection-mode text is a format error (400
`UNKNOWN_SELECTION_MODE`) found while reading the request. While the catalog is empty (no LOV
feature yet), every well-formed LOV attribute gets (1).

## DateTimeProperties (FR-013; R-11)

| Field | Rule |
|---|---|
| `dateFormat` | Required; `DD/MM/AAAA`, `MM/DD/AAAA`, `AAAA-MM-DD`, `DD/MM/AAAA HH:mm`, `AAAA-MM-DD HH:mm` |

## CurrencyProperties (FR-014; R-12)

| Field | Rule |
|---|---|
| `currencyCode` | Required; ISO 4217 code known to the runtime (e.g. `MXN`) |
| `decimals` | Optional; 0–4; default 2 |

The symbol is derived from the code (returned in responses as `currencySymbol`).

## ReferenceProperties (FR-015; R-13)

| Field | Rule |
|---|---|
| `targetObjectType` | Required; one of feature 003's object types: `PRODUCT`, `ATTRIBUTE`, `LIST_OF_VALUES`, `TEMPLATE`, `CLASSIFICATION`, `INTERFACE` |
| `cardinality` | Optional; `SINGLE` (default) or `MULTIPLE` |

## AttributePermissions (value object)

The requesting user's permission codes from the development header `X-Pim-Permissions` (R-06).
`ATTRIBUTE_CREATE` allows creating attributes (FR-002, FR-019). Unknown codes ignored; codes trimmed
and compared ignoring case.

## ListOfValuesCatalog (interface, R-14)

`boolean hasAny()`, `boolean exists(String listOfValuesId)`. Implementation in this feature: empty
(no lists). Replaced by the LOV feature (DEP-03).

## Creation flow (one atomic step, R-18)

```text
request + headers
  ├─ Bean Validation (required fields, sizes)              → 400 (before the controller runs)
  ├─ data type known; properties of that type valid         → 400 (codes below)
  ├─ no ATTRIBUTE_CREATE permission                        → 403 ATTRIBUTE_CREATE_NOT_PERMITTED
  ├─ display name and description                          → 400
  ├─ LOV: no lists at all                                  → 422 LIST_OF_VALUES_NOT_AVAILABLE
  │       selection mode or list ID missing                → 400 REQUIRED
  │       list ID unknown                                  → 422 LIST_OF_VALUES_NOT_FOUND
  ├─ display name already used (ignoring case)             → 409 DISPLAY_NAME_IN_USE
  ├─ feature 003 assign(ATTRIBUTE, systemId?, declared key  → 403 / 400 / 409 from feature 003
  │   fields only: {} today, FR-006)
  └─ save attribute with the returned ID                   → 201 + Location
```

## Error codes (400 unless stated)

`REQUIRED`, `INVALID_DISPLAY_NAME`, `INVALID_DESCRIPTION`, `UNKNOWN_DATA_TYPE`, `INVALID_LENGTH`,
`INVALID_LENGTH_RANGE`, `UNKNOWN_CASE_FORMAT`, `INVALID_CAPTURE_MASK`, `UNKNOWN_NUMBER_FORMAT`,
`INVALID_PRECISION`, `INVALID_NUMBER`, `INVALID_VALUE_RANGE`, `UNKNOWN_SELECTION_MODE`,
`UNKNOWN_DATE_FORMAT`, `UNKNOWN_CURRENCY`, `INVALID_DECIMALS`, `UNKNOWN_TARGET_OBJECT_TYPE`,
`UNKNOWN_CARDINALITY`; 403 `ATTRIBUTE_CREATE_NOT_PERMITTED`; 409 `DISPLAY_NAME_IN_USE`; 422
`LIST_OF_VALUES_NOT_AVAILABLE`, `LIST_OF_VALUES_NOT_FOUND`. Feature 003 errors keep their codes
(`MANUAL_ID_NOT_PERMITTED`, `INVALID_SYSTEM_ID`, `SYSTEM_ID_IN_USE`, `SEQUENCE_EXHAUSTED`).
