# Data Model: Attribute Management – Consult, Edit and Delete

**Feature**: `007-attribute-management` | **Research**: [research.md](research.md)

No new collection. One optional field is added to `attributes`; nothing else in MongoDB changes.

## Collections

| Collection | Owner | Change |
|---|---|---|
| `attributes` | feature 004 | + optional `updatedAt` (date). Documents without it are attributes never edited. Unique index `attribute_display_name_unique` unchanged |
| `attribute_groups` | feature 005 | None. Read through `AttributeGroupService` only (R-02) |
| `lists_of_values` | feature 006 | None. Read through `ListOfValuesLookup` only (R-03) |
| system ID registry | feature 003 | None. A deleted attribute's registration stays (FR-016) |

## Attribute (entity, feature 004) — extended

| Field | Rule | Editable here? |
|---|---|---|
| `systemId` | Feature 003; unique; stored upper-cased as `_id` | **No** (422 `SYSTEM_ID_IMMUTABLE` if another one is sent) |
| `displayName` | 1–120 characters after trimming; unique among attributes ignoring case (feature 004 FR-023) | Yes; must be free among the *other* attributes |
| ~~`description`~~ | Removed 2026-11-20 (feature 004 FR-003 amendment) | — |
| `dataType` | One of 7 | **No** (422 `DATA_TYPE_IMMUTABLE`) |
| `scope` | Always `GLOBAL` | No (not a request field) |
| type properties | Feature 004 FR-008–FR-016, A-003, A-004 | Yes, validated as at creation |
| `createdAt` | Set at creation | No |
| `updatedAt` | **New**; set on every successful edit; absent before | Set by the system |

`Attribute.updated(displayName, properties, at)` returns a copy with those three values (the description was removed on 2026-11-20)
changed and everything else kept.

## AttributeDetails (read model, new)

| Field | Source |
|---|---|
| `attribute` | `attributes` |
| `groups` | `List<GroupReference>` from `AttributeGroupDirectory.groupsByAttribute()`, sorted by name ignoring case |
| `listOfValuesName` | `ListOfValuesCatalog.namesById()` for LOV attributes; absent otherwise or if the list is missing |

## GroupReference (value, new, `attribute.model`)

`id` (feature 005 group identifier), `name`.

## Interfaces owned by `attribute`, implemented elsewhere

| Interface | Methods | Implemented by |
|---|---|---|
| `ListOfValuesCatalog` (feature 004) | `hasAny()`, `exists(id)`, **+ `namesById()`** | `lov.service.ListOfValuesCatalogAdapter` (feature 006) |
| `AttributeGroupDirectory` (**new**) | `groupsByAttribute()`, `runIfInNoGroup(attributeId, action)` | `attributegroup.service.AttributeGroupDirectoryAdapter` (new, feature 005 package) |

## Operation flows

### Update (`PUT /api/v1/attributes/{id}`)

1. Controller maps and validates the request with feature 004's mapper (400).
2. Service: permission (403).
3. Attribute lock: find (404) → system ID unchanged (422) → data type unchanged (422) → LOV checks of
   feature 004 FR-012 (400/422) → display name free among others (409) → `repository.update` with
   `updatedAt = now`.
4. Outside the lock: compose details (groups, list name) → 200.

### Delete (`DELETE /api/v1/attributes/{id}`)

1. Service: permission (403).
2. Group lock (directory): groups containing the attribute? → 409 `ATTRIBUTE_IN_GROUPS` with groups.
3. Attribute lock (inside the action): find (404) → `repository.delete` → 204.

### Lock order (R-06)

`lov → attribute` and `group → attribute`; `lov` and `group` are never held together; `attribute` is
never held while calling a directory.

## State

An attribute is either present or deleted; there is no inactive state (spec OQ-002). After deletion,
its system ID stays registered and is never assigned again.
