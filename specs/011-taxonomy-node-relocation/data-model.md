# Data Model: Taxonomy Node Relocation

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-19

No new collection, document field or index. The feature changes how one existing field may change.

## Taxonomy node (feature 010, collection `taxonomy_nodes`)

Unchanged fields (feature 010 data-model.md): `_id`, `systemId`, `name`, `nameKey`, `parentId`,
`description`, `attributeIds`, `createdAt`, `updatedAt`. Indexes unchanged: unique
`taxonomy_node_sibling_name_unique` (`parentId`, `nameKey`); `taxonomy_node_attribute_ids`.

| Field | Before (feature 010) | After (feature 011) |
|---|---|---|
| `parentId` | Fixed at creation (010 FR-005) | Changes only through a move (FR-001, FR-004): the new parent's `_id`, or `null` for the top level |
| `updatedAt` | Name, description or assignments changed | Also set when the node is moved (FR-002). Descendants' `updatedAt` does not change: they are not written (research R-01) |

All other fields of the moved node and of every descendant stay exactly as they were (FR-002).

### Domain model change

`TaxonomyNode.movedTo(String newParentId, Instant at)`: a copy with the new parent (`null` → top level)
and `updatedAt = at`; ID, name, description and `attributeIds` unchanged. The record's doc comment for
`parentId` changes from "fixed at creation" to "changes only by a move (feature 011)".

## Move (action, not stored)

| Element | Rule | Source |
|---|---|---|
| Node | Must exist (404 `NODE_NOT_FOUND`, field `nodeId`) | FR-009 |
| Destination | A node's system ID that exists (404 `NODE_NOT_FOUND`, field `parentId`), or `null`/absent for the top level | FR-001, FR-004, FR-009 |
| Not into own branch | Destination ≠ node and ∉ descendants(node) (409 `NODE_MOVE_INTO_OWN_BRANCH`) | FR-005 |
| Not a no-op | Destination ≠ current parent (both `null` counts as equal) (409 `NODE_ALREADY_THERE`) | FR-006 |
| Name free at destination | No child of the destination (or top-level node) other than the node has the same `nameKey` (409 `NODE_NAME_IN_USE`) | FR-007 |
| Depth | Not checked | FR-008 |
| Branch size | `movedNodeCount` = 1 + number of descendants | US1 scenario 2 |

Checks run in that order, under the taxonomy lock (research R-02); the first failure stops the move and
nothing is written (FR-003).

### State after a successful move

- `node.parentId` = destination (or `null`); `node.updatedAt` = now.
- Level of the node = level(destination) + 1 (1 at the top); every descendant's level shifts by the
  same amount. Paths derive from the new chain (feature 010 FR-007).
- Effective attributes of the node and descendants are recomputed from the new chain on the next read
  (feature 010 FR-012 to FR-016; research R-05). Nodes outside the branch are unaffected (FR-012).

## Frontend display helpers (`frontend/src/taxonomy/nodeTree.ts`, pure, display only)

| Helper | Input → output | Used for |
|---|---|---|
| `descendantIds(items, nodeId)` | flat list → set of IDs under the node (visited set, no loops) | Hiding invalid destinations; "Se moverán {n} nodos" |
| `pathOf(items, nodeId)` | flat list → names from the top down to the node | Destination labels; "Nueva ubicación" |
| `moveDestinations(items, node)` | flat list → candidate nodes: not the node, not a descendant, not its current parent; sorted by path | Destination options |

The server's `movedNodeCount` is the value shown after the move; the client count is only the preview.
