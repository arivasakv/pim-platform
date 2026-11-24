# Data Model: Unlink a List of Values from an Attribute

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Date**: 2026-11-23

No new collection and no new field. The unlink changes one stored attribute (`attributes`, features
004/007) and deletes documents of feature 012's `taxonomy_value_filters`.

## Attribute (features 004/007) — what the unlink changes

| Field | Before | After |
|---|---|---|
| `systemId`, `displayName`, `scope`, `createdAt` | — | unchanged (FR-004) |
| `dataType` | `LIST_OF_VALUES` | any other type (FR-002) |
| properties | `listOfValuesProperties` (selection mode, list ID) | the new type's properties, feature 004 rules (FR-003); `null` for `BOOLEAN` |
| `updatedAt` | — | the unlink time (FR-004) |

New model method: `Attribute.withDataType(dataType, properties, at)` (R-05). Group memberships (attribute
groups) and taxonomy assignments (nodes) reference the system ID and do not change.

## List-value filter (feature 012) — deleted

Every `taxonomy_value_filters` document whose `attributeId` is the attribute's ID (compared ignoring
letter case) is deleted with the unlink (FR-008). New repository method:
`ValueFilterRepository.deleteByAttribute(attributeId)` and `findByAttribute(attributeId)`.

## Unlink review (response, not stored)

| Field | Meaning |
|---|---|
| `attributeId`, `attributeName` | The attribute |
| `listOfValuesId`, `listOfValuesName` | The list that will be detached |
| `filteredNodes` | `[{id, name}]`, nodes whose filters for the attribute will be deleted, sorted by name |
| `reviewToken` | SHA-256 (hex) of the attribute's definition and the filtered node IDs (R-02) |

## Unlink request

| Field | Rule |
|---|---|
| `dataType` | Required; any data type except `LIST_OF_VALUES` (`UNLINK_TO_LIST_NOT_ALLOWED`) |
| properties object | Exactly feature 004's creation rules for that type (FR-003); other objects ignored (004 A-004) |
| `reviewToken` | Required (`REVIEW_TOKEN_REQUIRED`); must equal the current review's token (`UNLINK_OUTDATED`) |

## Operation order (apply)

1. Request and new properties (feature 004 rules), review token present → 400.
2. Permission (`ATTRIBUTE_CREATE`) → 403.
3. Taxonomy lock (through the port), then attribute lock:
   attribute exists → 404; is a list of values → 422; new type not list → 422; token matches → 409;
   store the attribute with the new type and properties.
4. Still under the taxonomy lock: delete the attribute's filters; on failure, restore the attribute and
   fail (R-04).
5. Return the attribute details (groups, no list name).

## Error codes (attribute domain)

| Code | Status | When |
|---|---|---|
| `ATTRIBUTE_NOT_LIST_OF_VALUES` | 422 | Review or unlink of an attribute that has no list (new) |
| `UNLINK_TO_LIST_NOT_ALLOWED` | 422 | New data type is list of values (new) |
| `UNLINK_OUTDATED` | 409 | The attribute or its filters changed since the review (new) |
| `REVIEW_TOKEN_REQUIRED` | 400 | Unlink without `reviewToken` (new) |
| feature 004 property codes, `ATTRIBUTE_NOT_FOUND`, `ATTRIBUTE_CHANGE_NOT_PERMITTED` | 400 / 404 / 403 | As today |
