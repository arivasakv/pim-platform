# Feature Specification: Attribute Organization by Attribute Groups (Organización de Atributos por Grupos)

**Feature Branch**: `009-attribute-group-organization`

**Created**: 2026-11-12

**Status**: Approved

**Approved**: 2026-11-12, by the project owner, together with the plan's decisions D-1 to D-9

**Input**: User request of 2026-11-12, "Enhancement: Attribute Organization Using Attribute Groups"
(referred to below as *the feature request*): organize, browse and find attributes through attribute
groups on the "Atributos" page, so the module stays comfortable with hundreds or thousands of
attributes. Business reference: `ERF 1.1 Plantillas.docx` §3.2. The request names
`docs/source-of-truth/ERF 1.1 Plantillas.docx`; that folder holds only a README. The document read is
`p360:ERF - HUs/ERF 1.1 Plantillas.docx` (OQ-001).

## Context

### What already exists (not rebuilt here)

| Capability asked for | Where it exists today | Status |
|---|---|---|
| Create, consult, rename, edit the description of, and delete groups (only when empty) | Feature 005, page "Grupo de Atributos" (`#/grupos-de-atributos`), API `/api/v1/attribute-groups` | Implemented |
| Number of attributes per group | Feature 005 FR-012 (`attributeCount` in the group list) | Implemented |
| An attribute in one, several or no groups; add and remove without changing or deleting the attribute; no duplicate membership | Feature 005 FR-006 to FR-011 | Implemented, from the group's side only |
| One attribute record however many groups contain it | Feature 005 data model: memberships are stored on the group as attribute IDs | Implemented |
| List, search by name or ID, filter by type, consult, edit, delete attributes; groups shown per attribute | Feature 007, page "Atributos" (`#/atributos`) | Implemented |
| Deleting an attribute that is in groups is refused | Feature 007 FR-014 | Implemented |
| Field preview in the form and the detail | Feature 008 | Implemented |

### What is missing (this feature)

1. The "Atributos" page shows **one flat list**: there is no way to browse by group, to see only the
   attributes without a group, or to see how many attributes each group holds while browsing.
2. Groups of an attribute can be changed **only from the group page**; from an attribute, the user
   sees its groups but must go to another page, find the group, and find the attribute again among
   all attributes.
3. On "Grupo de Atributos", **"Agregar atributos" lists every attribute as a checkbox with no
   search**, which does not work with hundreds of attributes, and **"Eliminar grupo" acts at once,
   without a confirmation**.

This feature closes those three gaps by **extending** features 005 and 007. It adds no new kind of
data: groups, memberships and attributes stay as they are, and every change still goes through the
existing backend services and their rules.

### Groups are not templates

As in feature 005: attributes define product characteristics; attribute groups organize attributes;
templates (not built yet) decide which attributes describe a type of product; products hold values.
Nothing here links groups to templates or products (§3.2.2 rule 3 stays deferred, feature 005 FR-014).

## User Scenarios & Testing *(mandatory)*

The user is anyone who uses the "Atributos" page. Changing memberships keeps feature 005's current
behavior (the controls appear with the development permission `ATTRIBUTE_CREATE` and the backend
checks it); this feature adds no permission check (FR-016).

### User Story 1 - Browse attributes by group (Priority: P1)

On "Atributos", a group panel lists **"Todos los atributos"**, **"Sin grupo"** and every group, each
with its number of attributes. Choosing an entry shows only the attributes it covers; the user moves
between groups with one click and never leaves the page.

**Why this priority**: This is the core of the request: §3.2 ¶3 says groups exist «para facilitar su
organización visual en las interfaces de usuario», and §3.2.2 «Es recomendable poder agrupar los
atributos para facilitar la administración».

**Independent Test**: With groups "Logísticos" (Peso, Volumen) and "Compra Ventas" (Peso, Costo) and an
ungrouped attribute "Color", the panel shows "Todos los atributos 4", "Sin grupo 1", "Compra Ventas 2",
"Logísticos 2"; choosing "Logísticos" lists Peso and Volumen; choosing "Sin grupo" lists Color.

**Acceptance Scenarios**:

1. **Given** the page opens, **When** the attributes and groups load, **Then** the panel shows "Todos
   los atributos" selected, then "Sin grupo", then every group sorted by name, each with its count, and
   the list shows every attribute (feature 007's list, unchanged).
2. **Given** the panel, **When** the user chooses "Logísticos", **Then** the list shows only the
   attributes in that group, the list heading names the group, and the count says how many there are.
3. **Given** "Peso" is in "Logísticos" and "Compra Ventas", **When** the user chooses each group,
   **Then** "Peso" appears in both, as the same attribute (same system ID, same detail).
4. **Given** the user chooses "Sin grupo", **Then** only attributes that belong to no group are listed.
5. **Given** a group with no attributes, **When** the user chooses it, **Then** the list says the group
   has no attributes yet and explains how to add them.
6. **Given** an attribute is open in the detail, **When** the user chooses another group, **Then** the
   detail stays open (it is not lost by browsing).
7. **Given** no group exists yet, **Then** the panel still shows "Todos los atributos" and "Sin grupo"
   and says there are no groups yet, with a link to "Grupo de Atributos".
8. **Given** the groups cannot be loaded, **Then** the attribute list still works with "Todos los
   atributos", and the panel shows an error with "Reintentar".

---

### User Story 2 - Find attributes and groups quickly (Priority: P1)

The user searches by name or system ID and filters by data type **within the chosen group**; when
nothing matches there but something matches elsewhere, the page says so and offers to search in all
attributes. With many groups, the user can type to find a group in the panel.

**Why this priority**: Browsing alone does not scale to thousands of attributes; search and filter
must combine with the group choice (feature request §4.C).

**Independent Test**: In "Logísticos", searching "costo" shows no match and the message "1 coincidencia
en otros grupos" with "Buscar en todos los atributos"; pressing it selects "Todos los atributos" and
shows "Costo", keeping the search text.

**Acceptance Scenarios**:

1. **Given** "Logísticos" is chosen, **When** the user types "pes", **Then** only "Peso" is listed (name
   or system ID, ignoring accents and letter case, as in feature 007).
2. **Given** "Logísticos" is chosen, **When** the user also picks the data type "Numérico", **Then**
   only numeric attributes of the group are listed; the group, search and type filters combine.
3. **Given** a search with no match in the chosen group but N matches in other attributes, **Then** the
   page says "Ningún atributo de «Logísticos» coincide" and "N coincidencias en otros grupos", with a
   button "Buscar en todos los atributos" that selects "Todos los atributos" and keeps the search and
   type filter.
4. **Given** 30 groups, **When** the user types "log" in "Buscar grupo", **Then** the panel lists only
   groups whose name contains it; "Todos los atributos" and "Sin grupo" stay visible.
5. **Given** a group chosen and a search typed, **When** the user chooses another group, **Then** the
   search text and type filter are kept and applied to the new group, and the list goes back to its
   first page of results.
6. **Given** more than 50 matches, **Then** the list shows 50 and "Mostrar más", as in feature 007.

---

### User Story 3 - Change an attribute's groups from the attribute (Priority: P2)

From the attribute detail, the user adds the attribute to a group and removes it from a group, without
going to "Grupo de Atributos".

**Why this priority**: Feature 005 offers membership changes only from the group; from the attribute,
the user has to switch pages and search again (gap 2). It also lets the user prepare an attribute for
deletion (feature 007 FR-014) in place.

**Independent Test**: Open "Color" (no groups), add it to "Características"; the detail lists
"Características", the panel counts change ("Sin grupo" −1, "Características" +1). Remove it; the detail
says "Sin grupos" and "Color" still exists.

**Acceptance Scenarios**:

1. **Given** an attribute is open and the user may change groups, **When** they choose a group in
   "Agregar a un grupo" and press "Agregar", **Then** the attribute is added to that group, the detail
   lists it, the panel counts and the list update, and a Spanish confirmation is shown.
2. **Given** the attribute is in "Logísticos", **When** the user presses "Quitar" next to "Logísticos"
   and confirms, **Then** the attribute is removed from that group only, stays in the system and in its
   other groups, and its data (name, type, properties, system ID, list of values) is unchanged.
3. **Given** the group choice, **Then** it offers only the groups the attribute is not in yet; if it is
   in every group, the page says so.
4. **Given** "Logísticos" is chosen in the panel, **When** the user removes the open attribute from
   "Logísticos", **Then** it leaves the list, the detail stays open showing its remaining groups.
5. **Given** the group was deleted or the attribute was added to it meanwhile by someone else, **When**
   the user adds or removes, **Then** the backend's Spanish message is shown and the detail and panel
   show the current state.
6. **Given** the user may not change groups, **Then** the detail lists the groups without "Agregar" or
   "Quitar".
7. **Given** an attribute in groups whose deletion is refused (feature 007 FR-014), **Then** the message
   says to remove it from its groups, and the user can do it from the same detail.

---

### User Story 4 - Manage groups safely at scale (Priority: P3)

On "Grupo de Atributos", deleting a group asks for confirmation, and "Agregar atributos" can be
searched and shows at most 50 attributes at a time.

**Why this priority**: The group page works but does not scale (gap 3) and deletes without asking.
Group creation, renaming and the "only empty groups" rule (feature 005 FR-005) are unchanged.

**Independent Test**: "Eliminar grupo" on an empty group asks "¿Eliminar el grupo «Temporal»?" and
deletes only after "Sí, eliminar grupo"; on a group with 3 attributes it explains that they must be
removed first and offers only "Cancelar". In "Agregar atributos" with 300 attributes, typing "peso"
leaves only matching attributes.

**Acceptance Scenarios**:

1. **Given** an empty group, **When** the user presses "Eliminar grupo", **Then** an inline
   confirmation names the group and states that no attribute is deleted; the group is deleted only
   after "Sí, eliminar grupo"; "Cancelar" changes nothing.
2. **Given** a group with attributes, **When** the user presses "Eliminar grupo", **Then** the page says
   the group has N attributes that must be removed first (feature 005 FR-005), offers only "Cancelar",
   and sends no request.
3. **Given** "Agregar atributos" is open, **When** the user types in "Buscar atributo", **Then** only
   attributes not yet in the group whose name or system ID match are listed, at most 50 at a time with
   "Mostrar más", and already checked attributes stay checked while the search changes.
4. **Given** the group page, **When** the user creates, renames or opens a group, **Then** it behaves
   as in feature 005 (unchanged).

---

### Edge Cases

- **An attribute in several groups**: listed under each of them and under "Todos los atributos" once;
  it is always the same record (one system ID, one detail).
- **Counts while searching**: panel counts are totals per entry and do not change with the search or
  type filter; the list shows the number of matches (D-4 of the plan).
- **Groups with long names or many groups (P360 has 125, feature 005 OEB-002)**: the panel scrolls on
  its own and has "Buscar grupo"; names wrap.
- **The chosen group is deleted or renamed on the other page or by someone else**: on the next load,
  a deleted group falls back to "Todos los atributos"; a renamed one keeps being chosen (same group ID).
- **Attribute created while a group is chosen**: it is created without groups (feature 004 FR-017);
  the page shows it in the detail and the user can add it to groups from there (US3). Choosing groups
  inside the creation form stays out of scope (feature 005 OQ-009).
- **Attribute deleted while a group is chosen**: counts and list refresh.
- **Search with no match anywhere**: "Ningún atributo coincide con la búsqueda." (feature 007 text).
- **Group panel and attribute list loaded at different moments**: both are reloaded together after
  every change made on the page, so counts and list agree.
- **Small screens**: the panel turns into a compact block above the list that shows the chosen entry
  and opens on demand; nothing scrolls sideways (FR-014).
- **The backend cannot be reached**: Spanish error, nothing half-done, "Reintentar" (features 005, 007).

## Requirements *(mandatory)*

### Functional Requirements

Sources: the feature request; `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2 ¶3 ("Asignación por
Grupos") and §3.2.2 ("Es recomendable poder agrupar los atributos para facilitar la administración";
"Algunas consideraciones" rules 1, 2, 6); features 005 and 007 as built.

**Browsing by group ("Atributos" page)**

- **FR-001 – Group panel**: The "Atributos" page MUST show a group panel with, in this order:
  **"Todos los atributos"**, **"Sin grupo"**, and every attribute group sorted by name ignoring letter
  case, each with its number of attributes. "Todos los atributos" is chosen when the page opens.
  [§3.2 ¶3 «organización visual»; §3.2.2 «facilitar la administración»; feature request §3]
- **FR-002 – Choosing an entry**: Choosing a group MUST list only the attributes in that group;
  "Sin grupo" MUST list only attributes in no group; "Todos los atributos" MUST list every attribute.
  The chosen entry MUST be visibly marked and announced to assistive technology, and the list heading
  MUST name it. [Feature request §4.C]
- **FR-003 – Counts**: Each group's number MUST be the group's number of attributes from the backend
  (feature 005 FR-012); "Todos los atributos" MUST show the number of attributes and "Sin grupo" the
  number of attributes that belong to no group. Counts MUST refresh after every create, edit, delete
  or membership change made on the page. [Feature request §3, §4.A]
- **FR-004 – One attribute, many groups**: An attribute in several groups MUST appear under each of
  them as the same attribute (same system ID and detail); no copy of an attribute is created or shown.
  [§3.2.2 rule 2; feature request §5]
- **FR-005 – Combined filters**: Search by display name or system ID and the data type filter (feature
  007 FR-005, unchanged rules) MUST apply within the chosen entry. Changing the entry MUST keep the
  search text and type filter and restart the "Mostrar más" paging. [Feature request §4.C]
- **FR-006 – Matches elsewhere**: When the search or type filter matches nothing in the chosen group or
  in "Sin grupo" but matches other attributes, the page MUST say how many match elsewhere and offer
  "Buscar en todos los atributos", which chooses "Todos los atributos" and keeps the filters.
- **FR-007 – Finding a group**: The panel MUST offer "Buscar grupo", which narrows the listed groups by
  name (ignoring accents and letter case); "Todos los atributos" and "Sin grupo" always stay visible.
- **FR-008 – Panel states**: The panel MUST show a loading text while groups load; when there are no
  groups, a message with a link to "Grupo de Atributos"; when groups cannot be loaded, an error with
  "Reintentar", while the attribute list keeps working with "Todos los atributos" and "Sin grupo"
  computed from the attributes. A chosen group that no longer exists after a reload MUST fall back to
  "Todos los atributos". An empty group MUST show "Este grupo todavía no tiene atributos." with how to
  add them.
- **FR-009 – Detail kept**: Choosing another entry MUST NOT close or change the open detail, form or
  confirmation on the right.
- **FR-010 – Manage groups link**: The panel MUST link to "Grupo de Atributos" ("Administrar grupos")
  for creating, renaming and deleting groups. Group creation, renaming and deletion are NOT duplicated
  on "Atributos". [Feature request §2 "Do not duplicate existing functionality"]

**Membership from the attribute ("Atributos" page)**

- **FR-011 – Add and remove from the detail**: In the attribute detail, the groups of the attribute
  MUST be listed; when the user may change groups (feature 005's existing control rule), the detail
  MUST offer "Agregar a un grupo" (a choice among the groups the attribute is not in, plus "Agregar")
  and "Quitar" for each group, with a confirmation naming the attribute and the group before removing.
  Both MUST use feature 005's existing operations and rules (FR-006 to FR-011 there): no duplicate
  membership, the attribute is never changed or deleted, nothing changes on error. [§3.2.2 rules 2 and
  6; feature request §4.B]
- **FR-012 – After a change**: After adding or removing, the detail MUST show the attribute's current
  groups, the panel counts and the list MUST refresh, and a Spanish confirmation MUST be shown; if the
  backend refuses (group or attribute no longer exists, nothing to remove), its Spanish message MUST be
  shown and the current state displayed.
- **FR-013 – Delete message**: Feature 007's refusal to delete an attribute in groups MUST point to the
  "Quitar" controls of the same detail instead of sending the user to "Grupo de Atributos". The rule
  itself (feature 007 FR-014) is unchanged.

**Layout**

- **FR-014 – Layout and responsiveness**: The page MUST follow the design system and the LOV
  Management layout patterns (cards, list buttons, notifications, inline confirmations). On wide
  screens the group panel MUST be a column beside the attribute list and detail; on narrower screens it
  MUST collapse into a block above the list that shows the chosen entry and can be opened. No
  horizontal page scroll from 768 px up; the panel's group list scrolls on its own when long.
  [Feature request §6; feature 001]

**"Grupo de Atributos" page (feature 005)**

- **FR-015 – Safe group deletion and scalable picker**: "Eliminar grupo" MUST ask for an inline
  confirmation that names the group and says its attributes are not deleted; for a group with
  attributes it MUST instead explain that they must be removed first (feature 005 FR-005, unchanged)
  and offer only "Cancelar". "Agregar atributos" MUST offer "Buscar atributo" (name or system ID) and
  show at most 50 attributes at a time with "Mostrar más", keeping chosen attributes chosen.
  [Feature request §4.A "Delete groups safely"; scale]

**Unchanged behavior**

- **FR-016 – Permissions unchanged**: No permission check, role or rule is added or changed; change
  controls appear under the same development setting as today and the backend checks as today.
  [Feature request §8]
- **FR-017 – Existing operations unchanged**: Attribute creation, consultation, editing, deletion and
  field preview (features 004, 007, 008), group rules and API (feature 005) and LOV Management (feature
  006) MUST keep their behavior; attribute IDs, configurations, memberships and list-of-values links
  MUST NOT change. No backend API, data model or stored data changes. [Feature request §5, §10]
- **FR-018 – Data from the backend**: Every attribute, group, count and membership shown MUST come from
  the backend's existing REST API; the frontend holds no attribute or group data (feature 007 FR-003,
  constitution III).

**Deferred (recorded, not built here)**

- **FR-019**: Server-side paging and filtering of the attribute list (needed when the catalog outgrows
  loading every attribute at once, OQ-003), links to a chosen group (OQ-005), bulk assignment of many
  attributes from the "Atributos" list, nested groups (feature 005 FR-004: groups are flat), ordering
  of groups or attributes, group behaviours and metadata (feature 005 FR-014), and templates.

### Key Entities

No entity is added or changed.

- **Attribute** (features 004/007): one record per attribute, with its groups as returned by the
  backend.
- **Attribute group** (feature 005): ID, name, description, number of attributes, member attribute IDs.
- **Membership** (feature 005): one attribute in one group, at most once.
- **Browsing entry** (page state only, not stored): "Todos los atributos", "Sin grupo" or one group ID.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With 2,000 attributes and 150 groups, a user lists the attributes of a named group in
  under 10 seconds from opening the page, and choosing another entry or typing in a search updates the
  list in under 0.5 seconds.
- **SC-002**: A user finds every attribute that belongs to no group in one action ("Sin grupo").
- **SC-003**: For every group, the attributes listed under it are exactly its members and its count
  equals that number; an attribute in N groups is listed under each of the N.
- **SC-004**: 100% of membership changes made from the attribute detail leave the attribute's own data
  and its other memberships unchanged, and no attribute record is duplicated or deleted.
- **SC-005**: 100% of group deletions are preceded by a confirmation; 0 groups with attributes are
  deleted.
- **SC-006**: In a walkthrough, at least 4 of 5 users who know "Lista de Valores" find the attributes of
  a group, find the ungrouped attributes and move an attribute from one group to another on their first
  attempt, without help.
- **SC-007**: All existing tests of features 004 to 008 still pass, changed only where this spec changes
  a text (FR-013) or a layout element they locate.

## Assumptions

- **A-001**: Attribute volume for this feature: up to about 5,000 attributes and 300 groups, loaded at
  once as feature 007 does today (its list already returns every attribute with its groups). Beyond
  that, server-side paging is a later change (FR-019, OQ-003).
- **A-002**: Counts in the panel are totals, not filtered by the search (simpler to read; the list
  shows the matches).
- **A-003**: Group order is by name (feature 005); manual ordering is out of scope.
- **A-004**: The chosen entry, search and filters live only while the page is open; leaving and coming
  back starts again at "Todos los atributos" (OQ-005).
- **A-005**: The "only empty groups can be deleted" rule of feature 005 stays (OQ-002).

## Dependencies

| ID | Depends on | Status | Effect |
|---|---|---|---|
| DEP-01 | Feature 005 – Attribute groups (API, rules, page) | Implemented on `main` | Groups, counts and membership operations reused; its page gets FR-015 |
| DEP-02 | Feature 007 – Attribute management (page, list with groups) | Implemented on `main` | The page is extended; its list, search, filter, detail, editor and delete are reused |
| DEP-03 | Feature 008 – Field preview | Implemented on `main` | Unchanged; stays in the detail |
| DEP-04 | Feature 004 – Attribute creation | Implemented | Unchanged; new attributes start without groups |
| DEP-05 | Feature 006 – LOV management | Implemented | Unchanged; UI reference only |
| DEP-06 | Template management | Not specified, not built | No dependency; groups are not linked to templates (§3.2.2 rule 3 deferred) |
| DEP-07 | Permissions / Data Stewardship | Out of scope | Development stand-in of features 004/005 kept as is |

## Out of Scope

- Rebuilding attribute creation, editing, deletion or preview; redesigning the attribute form.
- Creating, renaming or deleting groups on "Atributos" (stays on "Grupo de Atributos").
- Templates, products, inheritance through groups, group behaviours or metadata.
- Permissions, roles, Data Stewardship.
- Backend or API changes; data migration.
- Nested groups, group ordering, bulk assignment from the attribute list, bulk import.

## Open Questions

None blocks planning; each is recorded, not resolved by assumption beyond what A-001 to A-005 state.

- **OQ-001**: The request cites `docs/source-of-truth/ERF 1.1 Plantillas.docx`, which is not in this
  repository; the reference repository's copy was used. Should the document be added to
  `docs/source-of-truth/` (tier 1) by the owner?
- **OQ-002**: Should deleting a group that still has attributes **remove its memberships and delete the
  group** (attributes kept), instead of being refused as today (feature 005 FR-005)? The request says
  only that attributes must not be deleted; changing this would amend feature 005.
- **OQ-003**: What is the expected **maximum number of attributes** (the request says "hundreds or
  potentially thousands")? Above about 5,000, the list needs server-side paging and filtering (FR-019).
- **OQ-004**: Must every attribute eventually belong to **at least one group** (§3.2 ¶3 «deben
  vincularse», feature 005 OQ-005)? If yes, "Sin grupo" becomes a to-do list and creation may need a
  group (feature 005 OQ-009).
- **OQ-005**: Should a chosen group be **linkable** (an address that opens "Atributos" with that group
  chosen, e.g. from "Grupo de Atributos")? Not needed for this feature (A-004).
- **OQ-006**: Limits for group name and description are still those chosen by feature 005's UI (80 and
  500 characters) without a business source (feature 005 OQ-007).

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-12) | Request | Scope, rules §5, UX §6 |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2 ¶3 "Asignación por Grupos" | 2 | FR-001, FR-002 («organización visual en las interfaces de usuario») |
| Same, §3.2.2 first sentence and "Algunas consideraciones" rules 1, 2, 6 | 2 | FR-001, FR-004, FR-011; rules 3–5 stay deferred |
| `docs/source-of-truth/` | 1 | Only a README; no requirement for this feature (OQ-001) |
| `specs/005-attribute-groups/` and `backend/.../attributegroup/`, `frontend/src/pages/AttributeGroupsPage.tsx` | Project | Existing capabilities, DEP-01, FR-015 |
| `specs/007-attribute-management/`, `frontend/src/pages/AttributesPage.tsx` | Project | DEP-02, FR-005, FR-013 |
| `specs/008-attribute-field-preview/` | Project | DEP-03 |
| `.specify/memory/constitution.md` | Governance | Principles I, III, VI, XI |
