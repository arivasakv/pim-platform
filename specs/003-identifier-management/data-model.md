# Data Model: Identifier Management

**Feature**: `003-identifier-management` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md)

All data is held in memory (research R-02). Model types live in `com.pim.backend.identifier.model`
and depend on no Spring, HTTP or DTO type (constitution XII).

## ObjectType (enum)

The six object types of section 3.1 (FR-001).

| Code (API value) | Meaning | Default prefix (R-05) |
|---|---|---|
| `PRODUCT` | Product | `PRD` |
| `ATTRIBUTE` | Attribute | `ATR` |
| `LIST_OF_VALUES` | List of values (LOV) | `LOV` |
| `TEMPLATE` | Template | `PLT` |
| `CLASSIFICATION` | Classification | `CLS` |
| `INTERFACE` | Interface | `INT` |

An unknown code in a request → 400 `UNKNOWN_OBJECT_TYPE` (or 404 when it is a path segment).

## SystemId (value object)

| Field | Rule |
|---|---|
| `value` | Trimmed; 1–40 characters from `A–Z a–z 0–9 - _ .` (R-07) |

- Equality and the uniqueness check ignore letter case (`a-100` = `A-100`); the value is kept as
  given (R-07).
- Unique across all six object types together (FR-001).
- Immutable: no operation changes the `SystemId` of a registration (FR-007).
- Invalid manual value → 400 `INVALID_SYSTEM_ID`. Blank value → treated as absent (A-004).

## SequenceConfiguration (value object)

| Field | Type | Rule |
|---|---|---|
| `kind` | `NUMERIC` \| `ALPHANUMERIC` | Required (FR-006) |
| `prefix` | text | `ALPHANUMERIC`: required, 1–10 letters A–Z. `NUMERIC`: must be absent |
| `minimumDigits` | integer | 1–18 (zero padding) |
| `nextValue` | integer | 1 to 999,999,999,999,999,999 |

- Generated ID = `prefix` + `nextValue` left-padded with zeros to `minimumDigits` (R-05).
- After each generation `nextValue` advances past the value used. Values already used by any
  object are skipped (R-06).
- Past the maximum → 409 `SEQUENCE_EXHAUSTED`.
- Violations → 400 with codes `INVALID_SEQUENCE_KIND`, `INVALID_PREFIX`, `INVALID_MINIMUM_DIGITS`,
  `INVALID_NEXT_VALUE`.

## KeyFieldDeclaration (value object)

| Field | Rule |
|---|---|
| `fieldNames` | Ordered list, 0–20 names, each 1–60 characters of letters, digits or `_`, starting with a letter; no repeats |

- Starts empty for every type (FR-013, R-09).
- Changing it does not change any registration already saved.

## ObjectTypeConfiguration (entity, one per ObjectType)

| Field | Type |
|---|---|
| `objectType` | ObjectType (identity) |
| `sequence` | SequenceConfiguration |
| `keyFields` | KeyFieldDeclaration |

- Seeded at startup with the defaults of R-05 by `identifier.config` (FR-006).
- Replaced as a whole by `PUT /object-types/{objectType}`. Replacing it never changes saved IDs
  (FR-008). Lowering `nextValue` is allowed: used values are skipped (R-06).

## SystemIdRegistration (entity)

One per object that has received a system ID.

| Field | Type | Rule |
|---|---|---|
| `systemId` | SystemId | Identity; unique across all types (FR-001) |
| `objectType` | ObjectType | Fixed at registration |
| `keyFieldValues` | map name → text | Exactly the names declared for the type at registration; values trimmed, 1–200 characters |
| `assignment` | `MANUAL` \| `AUTOMATIC` | How the ID was given (FR-003, FR-005) |
| `registeredAt` | timestamp (UTC) | Set once |

- Created only by an assignment request; never changed afterwards (FR-007).
- "Same object" (FR-009a, R-04): same `objectType` and, with a non-empty declaration, identical
  `keyFieldValues`. With an empty declaration, never the same object (R-09).

## ManualIdPermissions (value object)

The set of object types for which the requesting user may give manual IDs (FR-003). Built from the
development header `X-Pim-Manual-Id-Permissions` (R-08). Empty when the header is absent. Codes are
trimmed and compared ignoring letter case; unknown codes in the header are ignored.

## Assignment outcome

| Outcome | When | HTTP |
|---|---|---|
| `ASSIGNED` | New registration, manual or automatic | 201 |
| `SAME_OBJECT` | ID exists, same object (FR-009a) | 200 |
| *rejected: different object* | ID exists, different type or key values (FR-009b) | 409 `SYSTEM_ID_IN_USE` |
| *rejected: no permission* | Manual ID without the type's permission (FR-003) | 403 `MANUAL_ID_NOT_PERMITTED` |

## Assignment flow (one atomic step, R-10)

```text
request(objectType, systemId?, keyFieldValues)
  ├─ validate type, key fields against the declaration          → 400
  ├─ systemId given (not blank)?
  │    ├─ no permission for objectType                         → 403
  │    ├─ invalid format                                       → 400
  │    ├─ not registered (ignoring case)  → register MANUAL     → 201 ASSIGNED
  │    └─ registered → same object?  yes                        → 200 SAME_OBJECT
  │                                  no                         → 409 SYSTEM_ID_IN_USE
  └─ no systemId → next free value of the type's sequence → register AUTOMATIC → 201 ASSIGNED
```

## Relation to business identifiers

EAN and SKU are product data of feature 002 and are not modelled here. No link between a system ID
and a product's SKU is created (spec OQ-009 open).
