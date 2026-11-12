# Research: Attribute Organization by Attribute Groups

**Feature**: `009-attribute-group-organization` | **Plan**: [plan.md](plan.md)

## R-01 Extend, don't duplicate (D-1, D-6)

- **Findings**:
  - Feature 005 already implements group CRUD (delete only when empty), membership add/remove (all or
    nothing, duplicates reported), `attributeCount`, groups per attribute (`GET
    /attribute-groups?attributeId=`), and a page "Grupo de Atributos".
  - Feature 007 already lists every attribute **with its groups** (`AttributeResponse.groups`), sorted
    by name, with search (name/ID, accents ignored), type filter, 50-row paging, detail, editor,
    delete (refused while in groups) and feature 008's preview.
  - Missing: browsing by group, an ungrouped view, membership changes from the attribute, a safe group
    delete and a scalable picker.
- **Decision**: new feature 009 referencing 005 and 007; group management stays on its page, linked
  from the panel.
- **Alternatives**: amend 005 (its scope is the groups page and API, not the attribute browser);
  amend 007 (would hide the group-side changes); move group CRUD into "Atributos" and remove "Grupo de
  Atributos" (duplicates or removes working functionality, against the request).

## R-02 Filtering in the browser (D-2)

- **Findings**: `AttributesPage` loads `GET /api/v1/attributes` once (every attribute with groups and
  list name) and filters in memory. `GET /api/v1/attribute-groups` returns all groups sorted by name
  with `attributeCount`. A 2,000-attribute response is roughly 1 MB; filtering 5,000 objects by a few
  string comparisons takes milliseconds.
- **Decision**: choose entry → filter in memory by `attribute.groups[].id` ("Sin grupo": empty
  `groups`). Group counts from `attributeCount`; "Todos" and "Sin grupo" counted from the attribute
  list. Both responses are loaded in the same effect run, so they reflect the same moment.
- **Alternatives**: query parameters `groupId`, `ungrouped`, `q`, `page` on `GET /attributes` (backend
  change and contract v1.3.0; right answer once the catalog is much larger, recorded as spec FR-019 and
  OQ-003); computing group counts from the attributes' `groups` (equivalent when both loads succeed;
  `attributeCount` is feature 005's documented value, so it stays the source for group counts).

## R-03 Layout (D-3)

- **Findings**: `AttributesPage.module.css` uses two columns `minmax(18rem,1fr) minmax(0,2fr)`,
  stacked under 64rem. The app shell's side navigation takes part of the width, so at 1440 px the
  content is about 1,100 px wide.
- **Decision**:
  - ≥ 90rem: `grid-template-columns: minmax(13rem, 16rem) minmax(18rem, 1fr) minmax(0, 2fr)`; the
    panel is sticky with its group list scrolling (`max-height` relative to the viewport).
  - 64–90rem: panel spans the first row (`grid-column: 1 / -1`) as a compact block: a toggle button
    "Grupo: {entrada} ({n})" (`aria-expanded`) shows or hides the entry list; list and detail below in
    the existing two columns.
  - ≤ 64rem: one column; the panel as in the middle range.
  - The toggle is hidden and the list always shown at ≥ 90rem by CSS; one DOM for all sizes.
- **Alternatives**: a select for narrow screens beside a list for wide ones (two controls, duplicate
  labels in tests); keeping two columns with the panel stacked above the list card at all sizes (long
  group lists push the attributes down).

## R-04 Membership from the attribute detail (D-5, D-9)

- **Findings**: `attributeGroupsApi.addAttributes(groupId, ids)` returns the group and
  `alreadyInGroup`; `removeAttribute(groupId, id)` returns 204 or 404/409 problems with Spanish
  `detail`. `canChangeGroups()` and `canChangeAttributes()` read the same development setting.
- **Decision**: `AttributeGroupsSection` receives the attribute, the group summaries (or their load
  state) and `canChange`; it adds/removes, then calls `getAttribute(systemId)` and hands the fresh
  attribute to the page, which shows it, reloads list and groups, and shows the confirmation. On error
  it shows the backend's `detail` and still re-reads the attribute so the section shows the current
  state. Removal uses an inline confirmation (pattern of feature 007's delete). The details list's
  "Grupos" row is replaced by this section so groups appear once. The delete refusal text points to
  the section (D-9).
- **Alternatives**: a multi-group checkbox editor saved at once (several requests, partial failures);
  editing memberships inside the attribute form (amends features 004/007 and their save request).

## R-05 Counts, filters and matches elsewhere (D-4)

- **Decision**: pure functions in `attributeBrowsing.ts`:
  - `entryOf` / `BrowseEntry = { kind: 'all' } | { kind: 'ungrouped' } | { kind: 'group', groupId }`;
  - `inEntry(attribute, entry)`; `ungroupedCount(attributes)`;
  - `matchesFilters(attribute, search, dataType)` (moved unchanged from the page);
  - `browse(attributes, entry, search, dataType)` → `{ visible, elsewhere }`, where `elsewhere` counts
    matches outside the entry (only meaningful when the entry is not "all");
  - `filterGroups(groups, query)` with the same accent-insensitive normalization.
  Counts in the panel never depend on search (spec A-002).
- **Alternatives**: counts filtered by the current search (numbers jump while typing; harder to read).

## R-06 "Grupo de Atributos" changes (D-7)

- **Findings**: "Eliminar grupo" calls `deleteGroup` immediately; for a non-empty group the backend
  refuses with 409 (`GROUP_NOT_EMPTY`). The picker renders one checkbox per available attribute.
- **Decision**: `confirmingDelete` state: empty group → warning notification with "¿Eliminar el grupo
  «…»? Ningún atributo se elimina." and "Cancelar" / "Sí, eliminar grupo"; group with attributes →
  explanation with "Cancelar" only, no request. Picker: `TextField` "Buscar atributo", filter with
  `matchesFilters`, show first 50 + "Mostrar más"; `chosen` stays a list of IDs, so checked attributes
  stay checked when hidden by the search; "Agregar seleccionados (N)" shows how many are chosen.
- **Alternatives**: a modal dialog (no modal exists in the design system; features 006/007 use inline
  confirmations).

## R-07 State and effects (D-8)

- **Decision**: one effect keyed by `listRequest` loads `listAttributes` + `fetchAttributeTypes` (as
  today) and, independently, `listGroups` into its own state (`loading | error | ready`), so a groups
  failure leaves the list usable (spec FR-008). The effective entry is computed in render: a group
  entry whose ID is not in the ready group list becomes "all". Setting state happens only in promise
  callbacks and event handlers (ESLint `set-state-in-effect`).
- **Alternatives**: URL hash parameter for the chosen group (changes `navigation.ts` matching; OQ-005).

## R-08 Tests affected

- **Decision**:
  - `AttributesPage.test.tsx`: its fake backend also answers `GET /api/v1/attribute-groups` and the
    membership endpoints; assertions on the details row "Grupos" and on the delete refusal text move
    to the new section and D-9 text; the list card title becomes the entry label (contract).
  - `AttributeGroupsPage.test.tsx`: the delete test confirms first; picker tests type in the search.
  - Features 004, 006, 008 tests: unchanged (no shared file changes).
- **Alternatives**: none; each change is required by a spec FR (FR-002, FR-013, FR-015).
