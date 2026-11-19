# Research: Taxonomy Node Relocation

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-19

The spec has no open [NEEDS CLARIFICATION] (answered 2026-11-19). This file records the technical
decisions, each read against the existing code of feature 010 (`com.pim.backend.taxonomy`,
`frontend/src/pages/TaxonomyPage.tsx`).

## R-01 What a move changes in storage

**Decision**: A move changes **one document**: the `parentId` (and `updatedAt`) of the moved node in
`taxonomy_nodes`. Descendants are not written.

**Rationale**: Feature 010 stores only each node's parent (`TaxonomyNodeDocument.parentId`); level, path
and effective attributes are derived on every read by walking the parent chain
(`EffectiveAttributes.chain`, feature 010 D-3, D-4). Descendants point to the moved node, not to its
old ancestors, so they follow it without any write. A single-document update in MongoDB is atomic,
which gives FR-003 (all or nothing) and SC-003 (no partly moved branch) without transactions.

**Alternatives considered**:
- Store ancestors or a materialized path on every node (the reference proposal
  `p360:docs/arquitectura/modulo-plantillas/modelo-datos-mongo.md:264` recalculates `ancestors` of the
  branch): would need a multi-document update and a transaction for a branch of up to thousands of
  nodes. Not needed: feature 010 computes paths on read and meets SC-005 at this volume.

## R-02 Rule checks and concurrency

**Decision**: `TaxonomyService.move` runs entirely under the service's existing lock (feature 010
research R-05): load all nodes once, check the node and the destination exist, compute the moved
node's descendants from the parent links, refuse a destination that is the node or a descendant
(FR-005), refuse the current parent (FR-006), refuse a sibling name clash at the destination
(FR-007, `existsWithName(destination, name, node.id)`), then save the node with its new parent.

**Rationale**: All taxonomy changes already serialize on this lock, so two moves cannot interleave: the
second one sees the first one's result and its loop check is exact (edge case "Concurrent changes",
SC-003). The unique index on (`parentId`, `nameKey`) stays as the storage-level backstop for names.
Lock order is unchanged (group → taxonomy → attribute); a move never calls the attribute or group
services, except `AttributeService.findAll` inside `details`, as every taxonomy read already does.

**Alternatives considered**:
- Optimistic check plus MongoDB conditional update (`parentId` unchanged): protects multi-instance
  deployments, but the project runs one backend instance and every other taxonomy change already
  relies on the JVM lock; mixing two concurrency models would be harder to reason about.

**Descendant count**: computed by a breadth-first walk over a parent → children map built from the
single `findAll()` (≈5,000 nodes, A-006: milliseconds). The walk keeps a visited set, like `chain`, so a
damaged store cannot loop.

## R-03 API shape

**Decision**: `POST /api/v1/taxonomy-nodes/{nodeId}/move` with body `{ "parentId": "CLS000002" }`;
`parentId` `null` or absent moves the node to the top level. Response 200 `MoveNodeResult`:
`{ node: TaxonomyNode, movedNodeCount: int }`.

**Rationale**:
- A separate action keeps feature 010's `PUT` (rename) unchanged (FR-017, FR-018): its contract says the
  parent stays, and clients that send only name/description must not move nodes by accident.
- `null`/absent = top level mirrors `POST /api/v1/taxonomy-nodes` (create), where the same field means
  the same thing. The UI only sends it after an explicit "Nivel superior" choice and confirmation.
- Returning the node detail lets the page show the new path and effective attributes without another
  request (FR-014); `movedNodeCount` gives the success message (US1 scenario 2) the server's count.
- `POST …/{id}/attributes` already uses a sub-resource for an action on a node.

**Alternatives considered**: `PUT` with `parentId` (changes 010's contract semantics); `PATCH
/{nodeId}` with `{parentId}` (no PATCH elsewhere in this resource; a merge-patch cannot tell "absent"
from "move to top"); `PUT /{nodeId}/parent` (reads well but a parent of `null` as a resource is odd).

## R-04 Error codes

**Decision**: Two new codes in `TaxonomyErrorCode`; existing ones reused where the meaning is the same.

| Situation | HTTP | Code | Field | Spanish message |
|---|---|---|---|---|
| Destination is the node or a descendant (FR-005) | 409 | `NODE_MOVE_INTO_OWN_BRANCH` (new) | `parentId` | No puedes mover «{nodo}» dentro de sí mismo ni de uno de sus nodos descendientes. |
| Destination is the current parent / already top level (FR-006) | 409 | `NODE_ALREADY_THERE` (new) | `parentId` | «{nodo}» ya está en «{padre}». / «{nodo}» ya está en el primer nivel. |
| Sibling name clash at destination (FR-007) | 409 | `NODE_NAME_IN_USE` (reused) | `parentId` | Ya existe un nodo «{nombre}» en «{destino}». Cambia el nombre de uno de los dos antes de mover. / … en el primer nivel. … |
| Node does not exist (FR-009) | 404 | `NODE_NOT_FOUND` | `nodeId` | No existe un nodo con el ID {id}. |
| Destination does not exist (FR-009) | 404 | `NODE_NOT_FOUND` | `parentId` | No existe un nodo con el ID {id}. |
| No permission (FR-015) | 403 | `TAXONOMY_CHANGE_NOT_PERMITTED` | `permissions` | No tienes permiso para administrar la taxonomía. |
| `parentId` longer than 40 | 400 | `NODE_NOT_FOUND` | `parentId` | El ID del nodo padre no es válido. (as create) |

**Rationale**: 409 for "conflicts with the current tree" matches feature 010 (name in use, node not
empty); 404 for unknown IDs matches create with an unknown parent. Reusing `NODE_NAME_IN_USE` keeps one
code for the same rule; the message names the destination because the user did not type a name here.

## R-05 Effect on inheritance (FR-010 to FR-012)

**Decision**: No change to `EffectiveAttributes`. After the parent changes, the next read of any node in
the branch walks the new chain; attributes of former ancestors disappear, those of new ancestors appear
with their origin, and an attribute direct on the moved node and on a new ancestor is listed once as
DIRECT with `alsoInheritedFrom` (feature 010 D-5; clarification FR-011). Nodes outside the branch are
not read or written by the move (FR-012).

**Rationale**: Feature 010 OQ-005 anticipated this: read-time computation already "supports it".
Tests must prove it for the moved node, a grandchild, the old parent and a sibling (SC-001).

## R-06 Depth

**Decision**: No depth check (clarification FR-008). `EffectiveAttributes.chain` already handles any
depth; the UI shows whatever level results.

## R-07 Frontend: move panel

**Decision**: A **"Mover"** button in the node card actions (next to "Agregar nodo hijo", "Editar",
"Eliminar") opens an inline panel in the card, built like the "Agregar atributos" picker:
- current location; a search field (name or system ID, accents and case ignored);
- destination options as buttons with `aria-pressed` (the tree's selection pattern), each with its
  path; first option **"Primer nivel (sin nodo padre)"**; 50 at a time with "Mostrar más";
- the node itself, its descendants and its current parent are not offered (the top-level option is not
  offered for a top-level node);
- choosing a destination shows an inline warning confirmation (like delete and "Quitar") with the new
  path and the number of nodes that move, and **Cancelar** / **Sí, mover nodo**.

The candidate list, paths and descendant count are computed **in the browser** from the flat list the
page already loads (`listNodes`), with pure helpers in `frontend/src/taxonomy/nodeTree.ts`. This is
display only: the backend enforces every rule (FR-016) and computes inheritance (feature 010 FR-020).

**Rationale**: No radio or combobox component exists in the design system (feature 001 deferred
selection controls); the button-with-`aria-pressed` list is already used by `TaxonomyTree` and is
keyboard accessible. A native `<select>` with thousands of nodes and no search fails SC-005.

**Alternatives considered**: drag and drop in the tree (out of scope: accessibility and touch work);
choosing the destination by clicking the tree (ambiguous with normal selection); a modal dialog (the
page uses inline panels everywhere).

## R-08 Feature 010 documents

**Decision**: Feature 010's API contract gets the new operation (version 1.1.0) and its UI contract a
pointer to this feature's UI contract; feature 010 spec FR-005 gets a dated note that moves follow
feature 011 (FR-017). Done as the first implementation tasks (contracts before code, constitution VII).
