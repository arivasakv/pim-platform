# Data Model: Attribute Organization by Attribute Groups

**Feature**: `009-attribute-group-organization` | **Plan**: [plan.md](plan.md)

## Stored data

**No change.** No MongoDB collection, document field, index or migration is added or modified.

| Stored entity | Owner | Used here as |
|---|---|---|
| Attribute (`attributes`) | Features 004/007 | Read through `GET /api/v1/attributes` and `GET /api/v1/attributes/{id}`; each response carries `groups: [{ id, name }]` sorted by name |
| Attribute group (`attribute_groups`) with member attribute IDs | Feature 005 | Read through `GET /api/v1/attribute-groups` (`id`, `name`, `description`, `attributeCount`); membership changed through `POST /api/v1/attribute-groups/{id}/attributes` and `DELETE /api/v1/attribute-groups/{id}/attributes/{attributeId}` |

The rules on these entities stay where they are: one membership per attribute and group, the attribute
never changed or deleted by a membership change, only empty groups deletable (feature 005
`AttributeGroupService`); attributes in groups not deletable (feature 007 `AttributeService`).

## Page state (frontend only, not stored)

| Name | Shape | Rules |
|---|---|---|
| `BrowseEntry` | `{ kind: 'all' } \| { kind: 'ungrouped' } \| { kind: 'group'; groupId: string }` | Starts as `all`. A `group` entry whose ID is missing from the loaded groups is treated as `all` (spec FR-008) |
| `GroupsState` | `{ status: 'loading' } \| { status: 'error' } \| { status: 'ready'; groups: AttributeGroupSummary[] }` | Loaded with the attributes on each reload; independent failure |
| `groupSearch` | string | Narrows the groups listed in the panel; never hides "Todos los atributos" or "Sin grupo" |
| `search`, `typeFilter`, `shown` | as in feature 007 | Kept when the entry changes, except `shown`, reset to 50 |
| `panelExpanded` | boolean | Only affects narrow layouts (research R-03) |

## Derived values (pure functions, `attributeBrowsing.ts`)

| Function | Result |
|---|---|
| `inEntry(attribute, entry)` | `all`: true; `ungrouped`: `groups` empty or missing; `group`: some `groups[].id === groupId` |
| `ungroupedCount(attributes)` | Number of attributes with no group |
| `matchesFilters(attribute, search, dataType)` | Feature 007's rule, unchanged: type equal (or all), and normalized name or system ID contains the normalized search |
| `browse(attributes, entry, search, dataType)` | `{ visible, elsewhere }`: `visible` = attributes in the entry matching the filters, in list order; `elsewhere` = matching attributes not in the entry |
| `filterGroups(groups, query)` | Groups whose normalized name contains the normalized query (all when the query is blank) |
| `effectiveEntry(entry, groupsState)` | `entry`, or `all` when it names a group missing from a ready group list |

Normalization: Unicode NFD, combining marks removed, lower case (feature 007's `normalized`).
