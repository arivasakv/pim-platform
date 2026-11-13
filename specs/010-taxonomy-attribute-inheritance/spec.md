# Feature Specification: Attribute Inheritance through the Product Taxonomy (Herencia de Atributos por Taxonomía)

**Feature Branch**: `010-taxonomy-attribute-inheritance`

**Created**: 2026-11-12

**Status**: Approved

**Approved**: 2026-11-12, by the project owner ("Looks good"), together with the plan's decisions D-1 to D-10

**Input**: User request of 2026-11-12 (referred to below as *the feature request*): "a basic Attribute
Inheritance feature based on the Taxonomy/Classification hierarchy": attributes assigned to a node
are inherited by all its descendants. A node's effective attributes are its direct ones plus those of
its ancestors, each listed once. The UI must say which attributes are direct and which are inherited,
and from which node. Business reference: `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.3.

## Context

### What exists today

| Asked to reuse | In pim-platform | Consequence |
|---|---|---|
| Taxonomy / classification hierarchy | **Does not exist.** No backend domain, API, collection, page or spec manages taxonomy nodes. Feature 003 only reserves the object type `CLASSIFICATION` (system ID prefix `CLS`) for a future feature; features 003 to 006 list taxonomy administration as out of scope | This feature has to add the **minimum product hierarchy** that inheritance needs: nodes, parent–child links, create, rename and delete (US1). It is the first taxonomy, so it is not parallel to anything. Feature 003's `CLASSIFICATION` type gives the nodes their system IDs |
| Attributes | Features 004/007 (create, consult, edit, delete; global scope only) | Reused as they are: nodes reference attribute system IDs |
| Attribute groups | Feature 005 (+ 009) | Not used and not changed. Groups organize attributes administratively; they are not inherited through the taxonomy here |
| Templates | Not built; not specified | Not created. ERF §3.3 treats templates and product-hierarchy nodes as closely related (in P360 a template *is* a level-4 node); that relationship is open (OQ-001) |

### What the source says

ERF §3.3 «Taxonomías (Niveles)»:
- «La clasificación de la información es el eje central de la herencia de datos.» «Cada nivel
  jerárquico (Nodo) se trata como un objeto independiente.»
- «Jerarquía de Productos (Taxonomía Principal): Es el árbol principal. Determina el "ADN" del
  producto, dictando atributos asociados…» «Actualmente la jerarquía de producto cuenta con un nivel
  raíz y con hasta cuatro niveles. Cada uno con el prefijo EU+el número de nivel.»
- «Los datos maestros y banderas, pueden ligarse en niveles superiores (Raíz o N1) según requiera la
  herencia. Las características … se ligan de preferencia a último nivel pero pueden de igual forma
  agregarse en niveles superiores para aprovechar la herencia.»
- «Herencia Taxonómica: Los objetos heredan automáticamente atributos y valores válidos de sus niveles
  superiores (Parents).»

The reference repository's analysis notes that P360 has **no inheritance in practice** (configuration
is keyed by each level-4 node in 99.9 % of rows): this is new capability, not parity
(`p360:P360-repositorios/nuevo-pim/docs/arquitectura/pim-headless/modulos/01-plantillas.md` C-18).

### What this feature delivers

1. A **product hierarchy** ("Jerarquía de Productos") of nodes with one parent each (none at the top).
2. **Direct assignment** of existing attributes to any node.
3. The **effective attributes** of any node: its direct attributes plus every attribute directly
   assigned to an ancestor, each once, with its origin, always computed from the current tree (a
   change to an ancestor is visible in all descendants at once).
4. A page that shows the tree and, for the chosen node, which attributes are **Directo** and which
   are **Heredado**, and from which node.

It does **not** deliver: exclusions or overrides in children, filters of list values per level,
required-ness, businesses, metadata, values inheritance, moving nodes, products in nodes, secondary
taxonomies, templates, imports or approvals (Out of Scope).

## Clarifications

None yet. The decisions the spec cannot settle from the source are recorded as open questions
OQ-001 to OQ-008; the plan proposes decisions D-1 to D-10 for the owner's approval.

## User Scenarios & Testing *(mandatory)*

Users: anyone can view the hierarchy and the attributes of a node. Changing the hierarchy or its
assignments uses the **same development permission as attributes and groups** (`ATTRIBUTE_CREATE`,
header `X-Pim-Permissions` / setting `VITE_DEV_PERMISSIONS`); no new permission (FR-017).

### User Story 1 - Build the product hierarchy (Priority: P1)

An administrator creates top-level nodes and child nodes at any depth, renames them, and deletes nodes
that are no longer needed.

**Why this priority**: Inheritance needs a hierarchy, and none exists (Context). ERF §3.3: «Las
taxonomías podrán ser creadas y mantenidas mediante la interfaz de usuario».

**Independent Test**: Create "Ropa"; under it "Ropa de hombre"; under that "Camisas" and "Pantalones".
The tree shows the four nodes nested, each with its level and system ID.

**Acceptance Scenarios**:

1. **Given** an empty hierarchy, **When** the user creates "Ropa" as a top-level node, **Then** it gets
   a system ID (`CLS…`), level 1, and appears in the tree.
2. **Given** "Ropa", **When** the user adds the child "Ropa de hombre", **Then** it is created under
   "Ropa" with level 2, and the tree shows it nested and "Ropa" expandable.
3. **Given** "Ropa" already has a child "Ropa de hombre", **When** a second child " ropa de HOMBRE " is
   created under "Ropa", **Then** it is refused because the name is used by a sibling; the same name
   under another parent is allowed (A-002).
4. **Given** a node, **When** the user changes its name or description, **Then** the change is saved;
   its system ID, parent, children and attributes stay.
5. **Given** a node with children or with direct attributes, **When** the user tries to delete it,
   **Then** it is refused with a message naming what must be removed first; nothing changes.
6. **Given** a node with no children and no direct attributes, **When** the user confirms the
   deletion, **Then** it disappears; its attributes (if any were inherited) are untouched.
7. **Given** a user without the permission, **Then** the tree and details are shown without create,
   edit or delete controls, and the backend refuses such changes.

---

### User Story 2 - Assign attributes to a node (Priority: P1)

An administrator assigns existing attributes directly to a node and removes direct assignments.

**Why this priority**: Feature request §1 points 1 and 3; ERF §3.3 «Los datos maestros y banderas,
pueden ligarse en niveles superiores… Las características … se ligan de preferencia a último nivel».

**Independent Test**: Assign Color, Material and Marca to "Ropa", Género to "Ropa de hombre", Largo de
manga to "Camisas", Talla de cintura to "Pantalones" (the feature request's example).

**Acceptance Scenarios**:

1. **Given** the node "Ropa" and existing attributes, **When** the user picks Color, Material and Marca
   in "Agregar atributos" and confirms, **Then** the three are direct attributes of "Ropa".
2. **Given** the picker, **Then** it can be searched by name or system ID, shows at most 50 at a time,
   and does not offer attributes already direct on the node.
3. **Given** "Color" is already direct on "Ropa", **When** a request adds it again, **Then** the node
   keeps it once and the user is told it was already assigned.
4. **Given** "Color" is direct on "Ropa", **When** the user removes it (after confirming), **Then** it is
   no longer direct on "Ropa" and no longer inherited by its descendants; the attribute itself still
   exists, unchanged.
5. **Given** an attribute that does not exist (e.g. deleted meanwhile), **When** it is assigned,
   **Then** the request is refused naming it, and nothing changes.
6. **Given** any assignment change, **Then** the attribute's own data (name, type, properties, list of
   values, groups, system ID) is unchanged.

---

### User Story 3 - See a node's effective attributes and where they come from (Priority: P1)

Viewing a node shows every attribute that applies to it, marked **Directo** or **Heredado**, with the
node it comes from, and a summary of how many are of each kind.

**Why this priority**: The core of the request (§2, "an important requirement").

**Independent Test**: Open "Camisas" in the example hierarchy:

| Atributo | Asignación | Origen |
|---|---|---|
| Color | Heredado | Ropa |
| Material | Heredado | Ropa |
| Marca | Heredado | Ropa |
| Género | Heredado | Ropa de hombre |
| Largo de manga | Directo | Camisas (este nodo) |

**Acceptance Scenarios**:

1. **Given** the example hierarchy, **When** the user opens "Camisas", **Then** the table lists the five
   attributes above, inherited ones first from the top level down, then direct ones, each with its data
   type, an assignment badge ("Directo" / "Heredado") and its origin node.
2. **Given** "Pantalones", **Then** it lists Color, Material, Marca, Género (inherited) and Talla de
   cintura (direct), but **not** Largo de manga (assigned to a sibling).
3. **Given** a node with no direct attributes but inherited ones (e.g. a new child "Camisas formales"
   under "Camisas"), **Then** the page says "Este nodo no tiene atributos asignados directamente;
   hereda 5 de sus niveles superiores." and lists the five as inherited.
4. **Given** a top-level node, **Then** nothing is inherited; the summary says so.
5. **Given** a node with neither direct nor inherited attributes, **Then** the page says it has no
   attributes yet and, with the permission, offers "Agregar atributos".
6. **Given** the node's position, **Then** the detail shows its path ("Ropa › Ropa de hombre ›
   Camisas"); each ancestor in the path and each origin in the table opens that node.
7. **Given** an inherited attribute row, **Then** it has no "Quitar" control; the page explains that it
   is removed in its origin node.
8. **Given** the tree, **Then** each node shows how many attributes are assigned to it directly.

---

### User Story 4 - Ancestor changes reach every descendant at once (Priority: P2)

Assigning or removing an attribute on a node changes the effective attributes of all its descendants,
at every level, without any action on them; renaming a node updates the origin shown below it.

**Why this priority**: Feature request §1 point 7 and §3 «Automatically reflect changes to ancestor
assignments in descendant nodes»; ERF «heredan automáticamente».

**Independent Test**: Assign "Temporada" to "Ropa"; "Camisas" and "Pantalones" list it as inherited
from "Ropa" immediately. Remove it; it disappears from both. Rename "Ropa" to "Ropa y accesorios"; the
origin column of "Camisas" shows the new name.

**Acceptance Scenarios**:

1. **Given** a three-level branch, **When** an attribute is assigned to the top node, **Then** the next
   read of any descendant includes it as inherited from that node.
2. **Given** an attribute direct on "Ropa de hombre" and also direct on "Camisas", **When** the user
   views "Camisas", **Then** it appears **once**, as "Directo", with the note "También se hereda de
   «Ropa de hombre»"; when its direct assignment on "Camisas" is removed, it stays as inherited.
3. **Given** an attribute direct on "Ropa" and on "Ropa de hombre", **When** the user views "Camisas",
   **Then** it appears once, as inherited from the nearest one, "Ropa de hombre".
4. **Given** an attribute that is assigned to any node, **When** someone tries to delete the attribute
   (feature 007), **Then** the deletion is refused naming the nodes, like an attribute in groups; after
   removing those assignments it can be deleted.

---

### Edge Cases

- **Same attribute direct on a node and on an ancestor**: listed once, as direct, with the "también se
  hereda de" note (US4 scenario 2). Allowed, not an error (OQ-004).
- **Same attribute on several ancestors**: listed once; origin = the nearest ancestor (US4 scenario 3).
- **Same attribute on siblings**: each sibling has its own direct assignment; nothing is shared.
- **Deep trees**: inheritance works at any depth; no depth limit is enforced in this feature (OQ-003).
- **Many attributes on a node**: the table shows all of them; the picker shows 50 at a time.
- **Deleting a node with children or direct attributes**: refused (US1 scenario 5).
- **Deleting an attribute assigned to a node**: refused (US4 scenario 4).
- **An attribute renamed or retyped (feature 007)**: nodes show its current name and type (only its
  system ID is stored on the node).
- **Concurrent changes** (two users): each change is applied whole or not at all; reads show the
  current state; a node deleted meanwhile answers "not found".
- **Loops**: impossible, because a node's parent is chosen only at creation and cannot change (moving
  nodes is out of scope, OQ-005).
- **The backend cannot be reached**: Spanish error, nothing half-saved, "Reintentar".

## Requirements *(mandatory)*

### Functional Requirements

Sources: the feature request (FRQ §n) and ERF 1.1 §3.3 (tier 2). No tier 1 document in
`docs/source-of-truth/` covers this feature.

**Product hierarchy**

- **FR-001 – Nodes**: The system MUST keep one product hierarchy made of **nodes**. Each node MUST have
  a system ID, a name, an optional description, at most one **parent** node (none for top-level nodes),
  a creation date and, after changes, a modification date. [ERF §3.3 «Cada nivel jerárquico (Nodo) se
  trata como un objeto independiente»; FRQ §1]
- **FR-002 – System IDs**: Each node MUST receive a generated system ID from feature 003 under the
  existing object type `CLASSIFICATION`, which never changes. [ERF §3.1 (IDs for «clasificaciones»);
  feature 003 FR-001; OQ-002 about the ERF's level prefixes]
- **FR-003 – Create**: An authorized user MUST be able to create a top-level node or a child of any
  existing node, giving a name (required) and an optional description. [ERF §3.3; FRQ §3]
- **FR-004 – Names**: Names MUST be unique **among siblings** (same parent, or top level), compared
  ignoring letter case and surrounding spaces; the same name MAY exist under different parents.
  [A-002]
- **FR-005 – Edit**: An authorized user MUST be able to change a node's name (subject to FR-004) and
  description. The parent MUST NOT change (moving nodes is out of scope, OQ-005).
- **FR-006 – Delete**: A node MUST be deletable only when it has **no children and no direct
  attributes**; otherwise deletion MUST be refused with a Spanish message saying what to remove first.
  Deletion MUST ask for confirmation. [A-005]
- **FR-007 – Level and path**: The application MUST show each node's **level** (1 for top-level nodes)
  and its **path** of ancestors from the top. [ERF §3.3 «Niveles»]

**Direct assignment**

- **FR-008 – Assign**: An authorized user MUST be able to assign one or more existing attributes
  (features 004/007) directly to any node, at any level. [FRQ §1 points 1, 3; ERF §3.3]
- **FR-009 – No duplicates per node**: An attribute MUST be assigned directly to a node at most once;
  repeating it MUST leave the node unchanged and tell the user. An attribute MAY be assigned directly
  to any number of nodes.
- **FR-010 – Remove**: An authorized user MUST be able to remove a direct assignment. Inherited
  attributes MUST NOT be removable from a descendant (exclusion is out of scope); the page MUST point to
  the origin node instead. [FRQ §3 "Do NOT implement … excluding inherited attributes"]
- **FR-011 – Attributes untouched**: Assigning or removing MUST NOT change any data of the attribute
  (name, type, properties, scope, list of values, group memberships, system ID). A request naming an
  attribute or node that does not exist MUST be refused naming it, and nothing MAY change.

**Inheritance and effective attributes**

- **FR-012 – Inheritance**: Every attribute directly assigned to a node MUST be inherited by **all its
  descendants at every level**, without any assignment on them. [FRQ §1 points 2, 5, 7; ERF §3.3
  «Herencia Taxonómica»]
- **FR-013 – Effective attributes**: The effective attributes of a node MUST be its direct attributes
  plus the attributes directly assigned to any of its ancestors, **each attribute once**. [FRQ §1
  points 4, 6]
- **FR-014 – Assignment type and origin**: Each effective attribute MUST carry its assignment type —
  **Directo** if directly assigned to the node, otherwise **Heredado** — and its **origin**: the node
  itself for direct ones; for inherited ones, the **nearest ancestor** that has it directly. A direct
  attribute that an ancestor also has directly MUST say so ("También se hereda de «…»"). [FRQ §2]
- **FR-015 – Always current**: Effective attributes MUST always reflect the current hierarchy and
  assignments: a change to any node's assignments or name MUST be visible in all its descendants on
  their next read, with no extra step. [FRQ §3]
- **FR-016 – Order**: Effective attributes MUST be listed with inherited ones first, grouped by origin
  from the top level down, then direct ones; within one origin, in assignment order.

**Interface**

- **FR-017 – Permissions unchanged**: Viewing needs no permission. Creating, editing and deleting
  nodes and changing assignments MUST use the existing development permission `ATTRIBUTE_CREATE`
  (as features 004 to 007); without it the controls MUST be hidden and the backend MUST refuse with a
  Spanish message. No role, matrix or new permission is introduced. [FRQ §3, §6]
- **FR-018 – Page**: A page "Taxonomía", reachable from the side navigation, MUST show the hierarchy as
  an expandable tree (each node with its name and number of direct attributes) and, for the chosen
  node, its detail: name, system ID, level, path, description, children, and its effective attributes.
  It MUST follow the design system and the layout and interaction patterns of "Lista de Valores" and
  "Atributos" (cards, list buttons, inline confirmations, notifications, loading/empty/error states),
  in Spanish (es-MX), usable from 768 px without horizontal scroll. [FRQ §2; constitution; feature 001]
- **FR-019 – Showing inheritance**: The attribute table MUST show for every row: attribute name and
  system ID, data type, an assignment badge with the words **Directo** or **Heredado** (never color
  alone), and the origin node; a summary line MUST give the number of direct and inherited attributes,
  and a node with no direct attributes but inherited ones MUST say so explicitly. [FRQ §2]
- **FR-020 – Data from the backend**: Nodes, assignments and effective attributes MUST come from the
  backend's REST API, which computes inheritance in its service layer; the frontend MUST NOT compute
  inheritance or hold taxonomy or attribute data. [Constitution III; FRQ §6]

**Effect on existing features**

- **FR-021 – Attribute deletion**: Deleting an attribute that is directly assigned to one or more nodes
  MUST be refused with a Spanish message naming those nodes (amends feature 007 FR-013/FR-014 in the
  same way as groups). No other attribute behavior changes.
- **FR-022 – Unchanged features**: Attribute creation, consultation, editing and preview (features
  004, 007, 008), attribute groups (005, 009), lists of values (006) and product search (002) MUST keep
  their behavior and APIs; feature 003 is used through its existing service.

### Key Entities

- **Taxonomy node** (new): system ID (`CLASSIFICATION`), name, optional description, parent node (or
  none), direct attribute system IDs in assignment order, creation and modification dates. Level and
  path are derived from the parent chain.
- **Direct assignment** (new, stored on the node): one attribute directly on one node, at most once.
- **Effective attribute** (derived, not stored): attribute, assignment type (Directo/Heredado), origin
  node, and "also inherited from" for a direct one.
- **Attribute** (features 004/007): referenced by system ID; never changed by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the feature request's example hierarchy, the effective attributes of every node match
  the expected lists exactly (no missing, no extra, no duplicate), with the right type and origin.
- **SC-002**: After assigning or removing an attribute on a node, 100 % of its descendants show the
  change on their next read, at any depth.
- **SC-003**: In a walkthrough, at least 4 of 5 users correctly say, for three attributes of a level-3
  node, whether each is direct or inherited and from which node, without help.
- **SC-004**: An administrator builds the example hierarchy (4 nodes, 6 assignments) in under 5
  minutes.
- **SC-005**: With 500 nodes and 2,000 attributes, opening a node shows its effective attributes in
  under 1 second.
- **SC-006**: 100 % of assignment changes leave the attribute's own data unchanged; 0 nodes with
  children or direct attributes and 0 attributes assigned to nodes are deleted.
- **SC-007**: All existing tests of features 001 to 009 still pass, changed only where FR-021 changes
  attribute deletion.

## Assumptions

- **A-001**: One product hierarchy only (the ERF's «Taxonomía Principal»); secondary taxonomies are
  out of scope.
- **A-002**: Sibling names are unique ignoring letter case and surrounding spaces, as attribute and
  group names are; different branches may reuse a name (e.g. "Accesorios" under "Ropa" and under
  "Calzado").
- **A-003**: Several top-level nodes are allowed; the ERF's «nivel raíz» is not modelled as a special
  node (OQ-003).
- **A-004**: Nodes start empty; nothing is pre-created or migrated (as feature 005 FR-015).
- **A-005**: Deleting a non-empty node is refused rather than cascading, so nothing is lost by accident
  (as feature 005 FR-005).
- **A-006**: Limits of 80 characters for the name and 500 for the description, as groups (feature 005
  UI), until the business states others (OQ-008).
- **A-007**: Volume for this feature: up to about 5,000 nodes and 2,000 attributes (P360 has 4,086
  nodes in its template tree, reference analysis 01-plantillas).

## Dependencies

| ID | Depends on | Status | Effect |
|---|---|---|---|
| DEP-01 | Feature 003 – Identifier management (`CLASSIFICATION`, prefix `CLS`) | Implemented | System IDs of nodes (FR-002) |
| DEP-02 | Features 004/007 – Attributes | Implemented | Assigned attributes; attribute deletion gains the FR-021 check |
| DEP-03 | Constitution IX – storage | **Amendment needed** | Taxonomy nodes are not in the list of data allowed in MongoDB; without an amendment they would be in memory and lost on restart (plan D-2) |
| DEP-04 | Template management | Not specified | Templates are not created; their relation to nodes is OQ-001 |
| DEP-05 | Products | Not in scope | Products are not placed in nodes (ERF «Solo en el 4o nivel pueden existir productos» is for a later feature) |
| DEP-06 | Permissions / Data Stewardship | Out of scope | Development permission reused (FR-017) |
| DEP-07 | Features 005/009 – Attribute groups | Implemented | Not used, not changed |

## Out of Scope

- Excluding inherited attributes in a child; overriding inherited configurations; restrictive
  overwrite («Sobreescritura Restrictiva»); conflict rules; multiple parents.
- Inheritance of values, list-value filters per level, required-ness, businesses, conditional
  attributes, metadata and template metadata (ERF §3.3 lists).
- Moving nodes («Reubicación de Nodos»); bulk creation, Excel import, API imports from other systems,
  approvals and audit trail (ERF §3.4, §3.5).
- Products in nodes; secondary taxonomies (article groups, web tree, suppliers, Conjunto Look).
- Templates; attribute groups linked to nodes; new permissions.

## Open Questions

None blocks planning; each is recorded and the feature keeps the simplest safe rule meanwhile.

- **OQ-001**: How do **templates** relate to taxonomy nodes? In P360 a template is a level-4 node; the
  ERF speaks of «plantillas de nivel inferior» and «plantilla destino». Is a template a kind of node, or
  a separate object linked to a node? This decides how the Template Management feature reuses these
  nodes.
- **OQ-002**: Must node system IDs follow the ERF's level prefixes (**EU1–EU4**, «Cada uno con el
  prefijo EU+el número de nivel»), which feature 003 cannot generate per level today, instead of `CLS`?
- **OQ-003**: Should the hierarchy enforce **one root and at most four levels below it** («un nivel raíz
  y con hasta cuatro niveles»), or is that only today's shape?
- **OQ-004**: Should assigning directly an attribute that the node already **inherits** be refused as
  redundant, instead of shown once with a note (FR-014)?
- **OQ-005**: When moving nodes arrives, must a moved node immediately adopt its new branch's
  attributes (ERF «Reubicación de Nodos»)? Computing inheritance at read time (FR-015) already supports
  it.
- **OQ-006**: ERF: attributes marked «Únicos» inherit «solo la relación estructural». Is that only about
  values (out of scope here)?
- **OQ-007**: Should the attribute detail ("Atributos") list the nodes an attribute is assigned to? Not
  needed for the requested scope.
- **OQ-008**: Business limits for node name and description (A-006).

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-12) | Request | Scope, behavior, UI, exclusions |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.3 «Taxonomías (Niveles)» | 2 | FR-001, FR-003, FR-007, FR-012; OQ-001 to OQ-006; out-of-scope list |
| Same, §3.1 (IDs for clasificaciones), §3.2 ¶4 (herencia a jerarquías inferiores) | 2 | FR-002; context |
| `p360:…/pim-headless/modulos/01-plantillas.md` C-16, C-18 and §"herencia"; `decisiones.md` DEC-PLT-01, DEC-PLT-04 | 2 (analysis) | No inheritance in P360 (new capability); node counts (A-007); proposals not used as requirements |
| `specs/003-identifier-management/` and `backend/.../identifier/` | Project | `CLASSIFICATION` / `CLS` exists; no classification feature |
| `specs/004` to `009`, `backend/.../attribute`, `attributegroup` | Project | Reused patterns; FR-021 |
| `.specify/memory/constitution.md` v2.4.0 (III, IX, XII) | Governance | DEP-03 storage amendment |
| `docs/source-of-truth/` | 1 | Only a README |
