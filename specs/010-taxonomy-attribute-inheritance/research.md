# Research: Attribute Inheritance through the Product Taxonomy

**Feature**: `010-taxonomy-attribute-inheritance` | **Plan**: [plan.md](plan.md)

## R-01 Is there a taxonomy to extend? (D-1)

- **Findings**:
  - Backend packages: `attribute`, `attributegroup`, `common`, `config`, `identifier`, `lov`,
    `product`. None models a hierarchy.
  - Frontend pages: Inicio, Buscar Producto, Lista de Valores, Atributos, Grupo de Atributos.
  - Specs 001–009: taxonomy administration is out of scope in 003, 004, 005, 006.
  - Feature 003 defines `ObjectType.CLASSIFICATION` with default prefix `CLS`
    (`DefaultObjectTypeConfigurations`); no feature creates classifications.
  - P360 (reference analysis, tier 2): the template tree `PrimaryProductTaxonomy` has a root and
    levels `EU1`–`EU4`, 4,086 nodes; in practice no inheritance.
- **Decision**: add the minimum hierarchy in this feature, in a new domain `taxonomy`, identified with
  `CLASSIFICATION` IDs. Keep templates out (OQ-001).
- **Alternatives**: a separate "taxonomy management" feature first (cleaner split, but the request is
  only usable with both; the node part is small); modelling inheritance on attribute groups (rejected
  by the request: groups are administrative).

## R-02 Storage (D-2)

- **Findings**: constitution 2.4.0 IX allows MongoDB only for the product catalog and "attribute
  administration data" (attributes, groups, system ID registry, lists of values); everything else
  must be in memory until a spec and an amendment extend it. ADR 0003 explains why attribute data is
  stored together: references across restarts.
- **Decision**: amend IX (MINOR, 2.5.0) to add "taxonomy nodes and their direct attribute
  assignments", with the same rationale (nodes reference attribute and classification system IDs);
  amend ADR 0003 with a `taxonomy_nodes` row. Done through `/speckit-constitution` after approval.
- **Alternatives**: in-memory repository (nodes vanish on restart while their `CLS` IDs stay
  registered and attributes persist: inconsistent).

## R-03 Node document, names, IDs (D-3, D-6)

- **Decision**:
  - `taxonomy_nodes`: `{ _id: <system ID upper-cased>, systemId, name, nameKey, parentId|null,
    description|null, attributeIds: [<attribute system ID upper-cased>…], createdAt, updatedAt|null }`.
  - Unique compound index `taxonomy_node_sibling_name_unique` on `{ parentId: 1, nameKey: 1 }`
    (top-level nodes share `parentId: null`); index on `attributeIds` for the deletion check.
  - `nameKey` = trimmed, lower-cased name (as `GroupName`); name 1–80 characters after trimming;
    description ≤ 500.
  - IDs: `identifierService.assign(ObjectType.CLASSIFICATION, null, Map.of(), ManualIdPermissions…)`
    returning the generated `CLS000001`… (same call shape as `AttributeService.create`).
  - Service-level lock (`synchronized (lock)`, as `AttributeGroupService`) around every change; the
    unique index is the second line of defence.
- **Alternatives**: materialized path or ancestors array per node (faster subtree queries, but parents
  cannot change in this feature and the whole tree is small; adds update logic); separate assignment
  collection (more joins; the node document is the natural owner, as group memberships are).

## R-04 Effective attributes (D-4, D-5)

- **Decision**: pure class `EffectiveAttributes.of(TaxonomyNode node, Map<String, TaxonomyNode>
  nodesById)`:
  1. Build the chain `[top, …, parent, node]` by following `parentId` (guard against a missing parent:
     stop the chain, log a warning).
  2. Walk from the top down; for each node and each of its `attributeIds` in order, record or replace
     the attribute's entry: origin = that node (so the nearest one wins), and remember earlier
     (higher) origins.
  3. Result entries: `attributeId`, `assignment` = DIRECT if origin is the node itself, else
     INHERITED; `origin` (`NodeReference` systemId + name); `alsoInheritedFrom` = nearest ancestor
     with a direct assignment, only for DIRECT entries.
  4. Order: INHERITED entries grouped by origin depth (top first), DIRECT last; within one origin,
     the origin node's assignment order.
  The service then resolves attribute display name and data type through `AttributeService.findAll()`
  (one call); an ID with no attribute (should not happen given D-7) is skipped and logged.
- **Performance**: 5,000 nodes × small documents ≈ well under 1 MB per read; chain length ≤ depth.
- **Alternatives**: stored effective view updated on writes (reference DEC-PLT-04; needed at P360's
  production volume with value inheritance, not for this basic scope); computing in the frontend
  (forbidden by constitution III / spec FR-020).

## R-05 Attribute deletion refusal (D-7)

- **Findings**: `AttributeService.delete` calls `groupDirectory.runIfInNoGroup(id, () ->
  repository.delete(id))` and throws `AttributeInGroupsException` (409 `ATTRIBUTE_IN_GROUPS`, property
  `groups`). The interface lives in `attribute.repository`; the adapter in `attributegroup.service` is
  wired through `ObjectProvider` to avoid a bean cycle.
- **Decision**: same pattern: `attribute.repository.TaxonomyAssignmentDirectory` with
  `List<TaxonomyNodeReference> runIfOnNoNode(String attributeId, Runnable action)`; adapter
  `taxonomy.service.TaxonomyAssignmentDirectoryAdapter` runs the action under the taxonomy lock only
  when no node lists the attribute. `delete` becomes group check → taxonomy check → repository delete,
  so lock order is group → taxonomy → attribute. New `AttributeOnTaxonomyNodesException` → 409
  `ATTRIBUTE_ON_TAXONOMY_NODES` with property `nodes` `[{systemId, name}]`, message «El atributo
  pertenece a N nodo(s) de la taxonomía: … Quítalo de esos nodos antes de eliminarlo.» Groups are
  checked first, so an attribute in both gets the groups message first.
- **Frontend**: the "Atributos" page already shows the backend's `detail` for a refused deletion; no
  code change needed beyond a test.
- **Alternatives**: deleting the assignments together with the attribute (silent data loss in the
  taxonomy); leaving dangling IDs (effective lists would hide them, but the data would rot).

## R-06 Permissions (D-8)

- **Decision**: `TaxonomyPermissionsReader` reads `X-Pim-Permissions` into `AttributePermissions`
  (as `GroupPermissionsReader`); changes need `ATTRIBUTE_CREATE`, else 403
  `TAXONOMY_CHANGE_NOT_PERMITTED`. Frontend shows change controls only when `VITE_DEV_PERMISSIONS`
  contains it (as `canChangeGroups`).
- **Alternatives**: a new permission code (forbidden by the request).

## R-07 API shape (D-9)

- **Decision** (details in [contracts/taxonomy.openapi.yaml](contracts/taxonomy.openapi.yaml)):
  - `GET /api/v1/taxonomy-nodes` → `{ items: [ { systemId, name, parentId, level,
    directAttributeCount, childCount } ], total }`, sorted by name within each parent; the client
    nests them.
  - `POST` → 201 `TaxonomyNode` detail; `GET /{id}` → detail: `systemId, name, description, parentId,
    level, path: [NodeReference] (top → parent), children: [NodeReference], directAttributeCount,
    effectiveAttributes: [ { systemId, displayName, dataType, assignment, origin, alsoInheritedFrom? }
    ], createdAt, updatedAt? }`.
  - `PUT /{id}` `{ name, description }` → detail; `DELETE /{id}` → 204 or 409
    `NODE_HAS_CHILDREN` / `NODE_HAS_ATTRIBUTES`.
  - `POST /{id}/attributes` `{ attributeIds }` → `{ node, added, alreadyAssigned }` (all or nothing;
    422 `ATTRIBUTE_NOT_FOUND`); `DELETE /{id}/attributes/{attributeId}` → 204, 409
    `ATTRIBUTE_NOT_DIRECT` (inherited only, message names the origin), 404 if neither.
- **Alternatives**: nested tree JSON (harder to page later; flat list is simpler to reload); a
  separate `/effective-attributes` sub-resource (one more request per selection).

## R-08 UI (D-10)

- **Findings**: LOV page and Atributos page use two columns (list card + detail card), inline warning
  confirmations, success notifications, `Card` regions, `StatusBadge` (variants success, warning,
  error, info, neutral; icon except neutral; text always present). Feature 009 picker pattern
  (search + 50 rows + chosen kept) exists on "Grupo de Atributos".
- **Decision**:
  - Tree: nested `ul` with `role="tree"`-free plain lists and buttons (simpler, accessible): each node
    row = expand/collapse button (`aria-expanded`, label "Mostrar hijos de X" / "Ocultar hijos de X")
    + node button (`aria-pressed`, name and direct count). Top level expanded by default; selecting a
    node expands its ancestors. Pure helper `nodeTree.ts` builds the nested structure from the flat
    list.
  - Detail: path as links (buttons) "Ropa › Ropa de hombre", ID, "Nivel n", description, children
    list, actions. Inline forms for create child / edit (name, description) like the group page.
  - Attributes: summary line, explicit empty texts, a table with columns Atributo (name + ID), Tipo de
    dato (label from `fetchAttributeTypes`), Asignación (badge), Origen (button to open the origin, or
    "Este nodo"), action "Quitar" for direct rows; under the table, a note for inherited rows.
  - Badges: info "Directo" (marks what is set here), neutral "Heredado".
- **Alternatives**: a single table mixing all nodes (no "where it comes from" clarity); color-only
  markers (accessibility).

## R-09 Tests affected

- **Decision**: `AttributeServiceTest` / `AttributeControllerIntegrationTest` gain deletion-refusal
  cases; their existing setups need a `TaxonomyAssignmentDirectory` stub (always "no nodes").
  `navigation.test.ts` / `App.test.tsx` expect the new entry. Other suites unchanged.
