# Research: List-Value Filters per Taxonomy Level

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-19

The spec has no open [NEEDS CLARIFICATION] (answered 2026-11-19). Decisions below are read against the
existing code: `com.pim.backend.taxonomy` (features 010, 011), `com.pim.backend.lov` (006),
`com.pim.backend.attribute` (004, 007) and `frontend/src/attributes/AttributeFieldPreview.tsx` (008).

## R-01 Governance: storage

**Decision**: Constitution amendment **2.6.0** (MINOR): Principle IX adds «list-value filters on taxonomy
nodes» to attribute administration data; ADR 0003 gets an "Amended 2026-11-19 (feature 012)" line and a
`taxonomy_value_filters` row. Done first (task T001), with `/speckit-constitution`.

**Rationale**: Approved by the owner on 2026-11-19 (spec Clarifications, DEP-02). Same path as feature 010
(2.5.0). Filters reference nodes, attributes and list values, all already in MongoDB.

## R-02 Where filters are stored

**Decision**: A new collection **`taxonomy_value_filters`**, owned by the taxonomy domain: one document
per (node, attribute) with the included value IDs. Unique index on (`nodeId`, `attributeId`); index on
`nodeId` is covered by it (prefix).

**Rationale**:
- A filter can include up to 50,000 value IDs (a list's maximum, feature 006). Embedding filters in the
  node document (feature 010's `taxonomy_nodes`) could push a node with several large filters toward
  MongoDB's 16 MB document limit, and would make every node read carry filter data.
- A separate collection leaves feature 010's node documents and indexes unchanged (FR-020).
- One filter = one document, so saving or removing a filter is atomic (edge case "Concurrent changes").

**Alternatives considered**: embedded array in the node (size and coupling, above); one document per
included value (50,000 inserts per save; no atomic replace).

## R-03 Computing allowed values

**Decision**: On read, in the taxonomy service, with a pure class **`AllowedValues`**:
1. The node's chain (top → node) from `EffectiveAttributes.chain` (feature 010).
2. The attribute must be an effective attribute of the node (feature 010) and of type LIST_OF_VALUES;
   its list ID comes from `ListOfValuesProperties.listOfValuesId` via `AttributeService.find`.
3. The list's values, in list order, from the lov domain (R-04).
4. The filters for that attribute on the chain nodes (one query `nodeId in chain`).
5. Allowed = list values whose ID (compared ignoring letter case) is in **every** filter on the chain
   (FR-008). For the node's own filter: `availableAbove` = values allowed by the ancestors' filters;
   `excludedAbove` = own included IDs not in `availableAbove` (FR-010). Filter IDs no longer in the list
   are ignored (edge case "value deleted").

**Rationale**: Same model as feature 010 (read-time computation, always current, FR-012); moves (feature
011) need nothing extra because filters are keyed by node and the chain is read fresh. Volume: chain ≤ a
few levels × filters of ≤ 50,000 IDs → set intersections in memory, well under SC-005's 2 s.

**Node detail summary (FR-013)**: for every effective LIST_OF_VALUES attribute of the node, the same
computation without the value labels: total, allowed count, own filter yes/no, and the filters involved
(node + included count). One filter query for the whole chain, one list read per list attribute.

## R-04 Reading list values without new lock risks

**Decision**: Add a lock-free read method to the lov domain's existing **`ListOfValuesLookup`**:
`Optional<ListValues> values(String listId)` (or returning the `ListOfValues` model), reading the
repository directly, like its other methods.

**Rationale**: `ListOfValuesLookup` already exists for other domains and takes no lock (feature 006
research R-03, R-04), so the taxonomy service can call it while holding the taxonomy lock without
creating a new lock order. Calling `ListOfValuesService.find` would add taxonomy → lov → attribute to
the lock graph. The method is additive; lists-of-values behavior and APIs are unchanged (FR-020).

## R-05 Saving a filter (FR-002 to FR-004, FR-009)

**Decision**: `TaxonomyService.saveFilter(nodeId, attributeId, valueIds, permissions)` under the taxonomy
lock: node exists → attribute exists → attribute applies to the node → attribute is a list → IDs
non-empty, each ID in the list → each ID in `availableAbove` → upsert the filter document (IDs stored
in the list's own spelling, list order, no duplicates). Returns the filter detail. `removeFilter`
deletes it (404 if none).

**Errors** (409/422/404 as in features 010/011):

| Situation | HTTP | Code | Field |
|---|---|---|---|
| No value IDs / more than 50,000 | 400 | `INVALID_FILTER_VALUES` | `valueIds` |
| Node not found | 404 | `NODE_NOT_FOUND` | `nodeId` |
| Attribute not found | 404 | `ATTRIBUTE_NOT_FOUND` | `attributeId` |
| Attribute does not apply to the node | 404 | `ATTRIBUTE_NOT_ON_NODE` (reused) | `attributeId` |
| Attribute is not a list of values | 422 | `ATTRIBUTE_NOT_LIST` | `attributeId` |
| Value ID not in the list | 422 | `VALUE_NOT_IN_LIST` | `valueIds` |
| Value excluded by a filter above (FR-009) | 409 | `VALUE_EXCLUDED_ABOVE` | `valueIds` |
| Removing a filter that does not exist | 404 | `FILTER_NOT_FOUND` | `attributeId` |
| No permission | 403 | `TAXONOMY_CHANGE_NOT_PERMITTED` | `permissions` |

## R-06 Effects on existing taxonomy operations

**Decision**:
- **Delete node** (feature 010 FR-006): also deletes the node's own filters, after the node, under the
  taxonomy lock. A failure between the two leaves filters of a missing node, which are ignored on every
  read (no chain reaches them).
- **Remove assignment / move / attribute deletion**: no change. Filters of an attribute that no longer
  applies (or no longer exists) are kept and ignored (spec edge case, OQ-002).
- **Assign attributes / rename**: no change.

## R-07 API shape

**Decision** (additive to the taxonomy API, contract v1.2.0):
- `GET /api/v1/taxonomy-nodes/{nodeId}` gains `listAttributes[]` (FR-013).
- `GET /api/v1/taxonomy-nodes/{nodeId}/value-filters/{attributeId}`: the filter detail for the dialog and
  the preview — attribute (name, list, selection mode), `totalValues`, `availableValues` (allowed above,
  `{id, label}`, list order), `includedValueIds` (own filter or `null`), `excludedAboveValueIds`,
  `allowedValues` (`{id, label}`), `filters` involved.
- `PUT …/value-filters/{attributeId}` with `{ "valueIds": [...] }` (1–50,000) → the same detail.
- `DELETE …/value-filters/{attributeId}` → 204.

**Rationale**: the detail returns labels so the frontend only displays (FR-019). A 50,000-value list is
≈ 3 MB of JSON once per dialog opening; acceptable locally and within SC-005. Server-side paging of two
interdependent lists would complicate "move all matching" without need at this volume.

**Alternatives considered**: filters as a field of `PUT /taxonomy-nodes/{id}` (mixes concerns, huge
bodies); `PATCH` add/remove value operations (more requests per edit, no atomic whole-filter replace).

## R-08 Frontend

**Decision**:
- Node card: new section **"Listas de valores en este nodo"** after "Atributos del nodo": a table
  (Atributo · Valores permitidos «{n} de {total}» · Restricción · actions **Filtrar valores**,
  **Vista previa**). Shown only when the node has list attributes.
- **Filtrar valores** opens an inline panel (`ValueFilterPanel`, like the move panel of feature 011) with
  two columns: «Valores disponibles» and «Valores incluidos en el filtro»; each searchable (label or ID,
  accents and case ignored), 50 rows at a time, a button per row (**Incluir** / **Quitar**), and
  **Incluir los mostrados** / **Quitar los mostrados** for the current search matches. Included values
  excluded above are listed with «Excluido por «{nodo}»». Buttons **Guardar filtro**, **Quitar filtro**
  (with inline confirmation), **Cancelar**.
- **Vista previa** shows a small `NodeListFieldPreview`: feature 008's select (single) or checkboxes
  (multiple) with the allowed values, the first 50 and the same summary rule (`PREVIEW_VALUE_LIMIT`,
  `listSummary` from `attributes/fieldPreview.ts`).

**Rationale**: HU2.3's two-list dialog; the design system has no transfer-list component, so rows with
buttons follow the patterns already used (picker, move panel). `AttributeFieldPreview` reads an unsaved
attribute form and loads the whole list itself, so the node preview reuses its rules and helpers rather
than the component.

## R-09 Contracts and feature 010 documents

**Decision**: The new operations and the `listAttributes` field are merged into
`specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml` as **v1.2.0** (as feature 011
did for v1.1.0); feature 010's UI contract gets a pointer to this feature's UI contract.
