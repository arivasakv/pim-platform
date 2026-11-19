# Data Model: List-Value Filters per Taxonomy Level

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-19

## List-value filter (new; collection `taxonomy_value_filters`)

Stored by the taxonomy domain (research R-02), after the constitution 2.6.0 amendment (R-01).

| Field | Type | Rules |
|---|---|---|
| `_id` | string | `{nodeId}:{attributeId}` (both upper-cased); one filter per node and attribute (FR-002) |
| `nodeId` | string | A taxonomy node's system ID, upper-cased |
| `attributeId` | string | A LIST_OF_VALUES attribute's system ID, upper-cased |
| `valueIds` | array of string | 1–50,000 IDs of the attribute's list, in list order, no duplicates (compared ignoring letter case), stored in the list's own spelling (FR-002, FR-004) |
| `createdAt` | date-time | When the filter was first saved |
| `updatedAt` | date-time, nullable | When its values last changed |

**Indexes**: unique `taxonomy_value_filter_unique` on (`nodeId`, `attributeId`).

**Domain model**: `ValueFilter(nodeId, attributeId, List<String> valueIds, createdAt, Optional updatedAt)`
in `taxonomy.model`; `withValues(ids, at)` replaces the values. Repository interface
`ValueFilterRepository`: `findByNodeIds(ids)`, `find(nodeId, attributeId)`, `save(filter)`,
`delete(nodeId, attributeId)`, `deleteByNode(nodeId)`; implementations `MongoValueFilterRepository` and,
for tests, `InMemoryValueFilterRepository`.

**Rules on save** (checked in this order under the taxonomy lock; the first failure stops it, nothing is
written):

| # | Rule | Error | Source |
|---|---|---|---|
| 1 | Permission `ATTRIBUTE_CREATE` | 403 `TAXONOMY_CHANGE_NOT_PERMITTED` | FR-017 |
| 2 | 1–50,000 value IDs | 400 `INVALID_FILTER_VALUES` | FR-002 |
| 3 | Node exists | 404 `NODE_NOT_FOUND` (`nodeId`) | — |
| 4 | Attribute exists | 404 `ATTRIBUTE_NOT_FOUND` (`attributeId`) | FR-003 |
| 5 | Attribute applies to the node (direct or inherited, feature 010) | 404 `ATTRIBUTE_NOT_ON_NODE` | FR-003 |
| 6 | Attribute is LIST_OF_VALUES | 422 `ATTRIBUTE_NOT_LIST` | FR-003 |
| 7 | Every ID is a value of the list | 422 `VALUE_NOT_IN_LIST` (names the first) | FR-004 |
| 8 | Every ID is allowed by the filters above | 409 `VALUE_EXCLUDED_ABOVE` (names the first and its node) | FR-009 |

**Lifecycle**: removed by `DELETE …/value-filters/{attributeId}` (FR-005) or when its node is deleted
(research R-06). Kept, with no effect, when its attribute stops applying to the node or is deleted
(edge case, OQ-002).

## Allowed values (derived, not stored)

Pure class `AllowedValues` in `taxonomy.model` (research R-03). Inputs: the node's chain (top → node), the
list's values in order, and the filters for the attribute on the chain nodes.

| Output | Definition |
|---|---|
| `allowed` | List values whose ID is in every filter on the chain (all values if none) — FR-008 |
| `availableAbove` | Same, using only the ancestors' filters (what the node's own filter may include) — FR-009 |
| `ownFilter` | The node's own filter, if any |
| `excludedAbove` | IDs of the own filter not in `availableAbove`, each with the nearest ancestor filter that excludes it — FR-010 |
| `involved` | The filters on the chain, top → node, each with its node and included count — FR-013 |
| `none allowed` | `allowed` is empty while filters exist — FR-011 |

IDs are compared ignoring letter case; filter IDs that are not in the list any more are ignored.

## Node detail addition (`TaxonomyNode.listAttributes[]`)

For each effective attribute of type LIST_OF_VALUES, in effective-attribute order:

| Field | Meaning |
|---|---|
| `systemId`, `displayName` | The attribute |
| `listOfValuesId`, `listName` | Its list |
| `selectionMode` | `SINGLE` or `MULTIPLE` (feature 004) |
| `totalValues` | Values in the list |
| `allowedCount` | Values allowed at this node |
| `hasOwnFilter` | Whether this node has its own filter |
| `filters` | The filters involved, top → node: `{node: {systemId, name}, includedCount}` |

## Frontend

No stored data. `ValueFilterPanel` keeps the working copy of included IDs while editing; nothing is
computed beyond searching and paging what the backend returned (FR-019).
