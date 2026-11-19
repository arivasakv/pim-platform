# Feature Specification: Taxonomy Node Relocation (Reubicación de Nodos)

**Feature Branch**: `011-taxonomy-node-relocation`

**Created**: 2026-11-19

**Status**: Approved

**Approved**: 2026-11-19, by the project owner

**Input**: User request of 2026-11-19 (referred to below as *the feature request*): move a node of
the product hierarchy, with everything under it, to another place in the hierarchy from the
"Taxonomía" page. The node keeps its system ID and its direct attributes; moving it under itself or
under one of its descendants is refused; the user confirms the move and sees how many nodes move;
levels and paths update; the moved branch immediately stops inheriting from its old ancestors and
inherits from its new ones. Business reference: `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.3
«Reubicación de Nodos» (official copy of ERF 1.1, project owner decision of 2026-11-19).

## Context

### What exists today

| Item | In pim-platform | Consequence |
|---|---|---|
| Product hierarchy | Feature 010: nodes with one parent each (none at the top), create, rename, delete, direct attribute assignments, effective attributes computed from the current ancestors | Reused. Feature 010 FR-005 fixes a node's parent at creation and lists moving nodes as out of scope; this feature lifts that limit |
| Inheritance | Feature 010 FR-012 to FR-016: effective attributes are always computed from the **current** tree | A move needs no recalculation step: the next read already reflects the new ancestors (feature 010 OQ-005) |
| Templates, products | Not built | Nothing else moves with a node; the ERF's «restricciones» of the destination are, today, only its attributes |

### What the source says

ERF §3.3, «Algunos comportamientos esperados de la taxonomía» (tier 2):

- «Reubicación de Nodos: Si un producto o subplantilla es movido a una nueva rama de la taxonomía,
  debe ignorar inmediatamente las configuraciones de su rama de origen y adoptar automáticamente las
  restricciones y atributos de su nueva plantilla destino.»
- «Actualmente la jerarquía de producto cuenta con un nivel raíz y con hasta cuatro niveles. Cada uno
  con el prefijo EU+el número de nivel.» (feature 010 OQ-002, OQ-003, still open)

Observed behavior (tier 3, evidence only, not requirements):

- **OEB-001**: The current Omega catalog tool moves a group of its structure tree under a new parent,
  or to the top when no parent is given; it refuses a node as its own parent and any cycle, and allows
  at most 100 levels. [Evidence:
  p360:P360-repositorios/nuevo-pim/docs/arquitectura/omega-a-pim-headless/como-funciona/plantillas-atributos-y-tablas.md:391-395]
- **OEB-002**: P360 does not let a node move while its descendants hold products, until they are
  unlinked. [Evidence: p360:P360-repositorios/nuevo-pim/trazabilidad/altas-S27-S38.md:66 (S31-RN-10, AS-IS)]
  Not applicable yet: there are no products in nodes.

### What this feature delivers

1. A **move** action on a node: choose a new parent; the node and all its descendants move together.
2. Safety rules: no move under itself or a descendant, no sibling name clash at the destination,
   confirmation showing what moves.
3. Immediate effect on levels, paths and effective attributes of the whole moved branch.

## Clarifications

### Session 2026-11-19

- Q: Can a node be moved to the top level (no parent)? → A: Yes. Feature 010 already allows several
  top-level nodes (A-003) and the Omega tool allows it (OEB-001) (FR-004, User Story 4).
- Q: Must a move be refused when part of the branch would end deeper than the ERF's four levels? →
  A: No limit for now, as in feature 010. A depth limit, if decided (feature 010 OQ-003), will apply to
  creating and moving nodes together (FR-008).
- Q: The moved node has an attribute directly and a new ancestor has it directly too: what happens? →
  A: Follow feature 010 FR-014: listed once as **Directo** with «También se hereda de «…»». Nothing is
  removed or flagged (FR-011).

## User Scenarios & Testing *(mandatory)*

Users: anyone can view the hierarchy. Moving a node uses the **same development permission as the
other hierarchy changes** (`ATTRIBUTE_CREATE`, feature 010 FR-017); no new permission.

Example hierarchy used below (from feature 010):

```text
Ropa                      (direct: Color, Material, Marca)
├── Ropa de hombre        (direct: Género)
│   ├── Camisas           (direct: Largo de manga)
│   │   └── Camisas formales
│   └── Pantalones        (direct: Talla de cintura)
└── Ropa de mujer         (direct: Género, Corte)
Calzado                   (direct: Material de suela)
```

### User Story 1 - Move a node with its branch to another parent (Priority: P1)

An administrator selects a node, chooses "Mover", picks the new parent, reviews what will move and
confirms. The node and everything under it appear under the new parent.

**Why this priority**: The core of the request and of ERF §3.3 «Reubicación de Nodos». Without it the
hierarchy can only be fixed by deleting and recreating nodes, which loses their system IDs and
assignments.

**Independent Test**: Move "Camisas" from "Ropa de hombre" to "Ropa de mujer". The tree shows
"Camisas" and "Camisas formales" under "Ropa de mujer"; "Ropa de hombre" keeps only "Pantalones".

**Acceptance Scenarios**:

1. **Given** the example hierarchy and the permission, **When** the user opens "Camisas", chooses
   "Mover", picks "Ropa de mujer" and confirms, **Then** "Camisas" is a child of "Ropa de mujer" and
   "Camisas formales" is still its child.
2. **Given** the move dialog for "Camisas", **Then** it shows the current path, lets the user search
   the destination by name or system ID, and before confirming shows the new path ("Ropa › Ropa de
   mujer › Camisas") and the number of nodes that move ("Se moverán 2 nodos: «Camisas» y 1
   descendiente.").
3. **Given** the moved node, **Then** its system ID, name, description, children and direct attributes
   are exactly as before the move.
4. **Given** the move dialog, **When** the user cancels, **Then** nothing changes.
5. **Given** a user without the permission, **Then** no "Mover" control is shown, and the backend
   refuses a move request with a Spanish message.

---

### User Story 2 - Moves that would break the hierarchy are refused (Priority: P1)

The system refuses any move that would create a loop or a name clash, and says why.

**Why this priority**: A loop would make levels, paths and inheritance undefined; feature 010 relied on
loops being impossible (feature 010, Edge Cases «Loops»).

**Independent Test**: Try to move "Ropa de hombre" under "Camisas" (its own descendant); it is refused
and the tree is unchanged.

**Acceptance Scenarios**:

1. **Given** "Ropa de hombre", **When** the user tries to move it under itself or under "Camisas" or
   "Camisas formales", **Then** the dialog does not offer those destinations, and a request for them is
   refused with a message saying a node cannot be moved under itself or its descendants.
2. **Given** "Camisas", **When** the user picks its current parent "Ropa de hombre", **Then** the move is
   refused as having no effect, and nothing changes.
3. **Given** "Ropa de mujer" already has a child named "Camisas" (ignoring letter case and surrounding
   spaces), **When** the user moves "Ropa de hombre › Camisas" under "Ropa de mujer", **Then** the move
   is refused naming the clash; nothing changes (feature 010 FR-004).
4. **Given** a destination or node that no longer exists (deleted meanwhile), **When** the move is
   confirmed, **Then** it is refused naming it, and nothing changes.

---

### User Story 3 - The moved branch inherits from its new ancestors at once (Priority: P1)

After a move, the node and all its descendants stop inheriting the attributes of their old ancestors
and inherit those of the new ones, with no further action.

**Why this priority**: ERF §3.3: «debe ignorar inmediatamente las configuraciones de su rama de origen y
adoptar automáticamente las restricciones y atributos de su nueva plantilla destino».

**Independent Test**: Before the move, "Camisas formales" inherits Color, Material, Marca (Ropa),
Género (Ropa de hombre) and Largo de manga (Camisas). Move "Camisas" under "Calzado". "Camisas
formales" now inherits Material de suela (Calzado) and Largo de manga (Camisas) only.

**Acceptance Scenarios**:

1. **Given** the move of "Camisas" under "Calzado", **When** the user opens "Camisas", **Then** its
   effective attributes are Material de suela (Heredado, origin Calzado) and Largo de manga (Directo).
2. **Given** the same move, **When** the user opens "Camisas formales", **Then** it shows level 3, path
   "Calzado › Camisas › Camisas formales", and Material de suela (from Calzado) and Largo de manga (from
   Camisas) as inherited; Color, Material, Marca and Género no longer appear.
3. **Given** the same move, **Then** "Ropa de hombre" and "Pantalones" keep their effective attributes
   unchanged, and no attribute's own data changes.
4. **Given** "Camisas" has "Género" directly and is moved under "Ropa de mujer", which also has
   "Género" directly, **Then** "Género" appears once on "Camisas", as **Directo**, with «También se
   hereda de «Ropa de mujer»», and the direct assignment is kept (FR-011).

---

### User Story 4 - Move a node to the top level (Priority: P2)

An administrator turns a node into a top-level node, keeping its branch.

**Why this priority**: Useful to split a branch out; the current Omega tool allows it (OEB-001) and the
owner confirmed it (Clarifications, FR-004).

**Independent Test**: Move "Ropa de mujer" to the top level. It appears next to "Ropa" and "Calzado",
at level 1, and inherits nothing.

**Acceptance Scenarios**:

1. **Given** the move dialog, **When** the user chooses "Nivel superior" and confirms, **Then** the node is
   a top-level node with level 1, its descendants' levels decrease accordingly, and it has no inherited
   attributes.
2. **Given** a top-level node already named like the moved node, **Then** the move is refused (US2
   scenario 3).

---

### Edge Cases

- **Moving a node with many descendants**: the whole branch moves as one change; levels and paths of
  every descendant update (SC-002).
- **Concurrent changes**: two users moving nodes at the same time must never produce a loop; each move
  is applied whole or not at all, and a move that would create a loop with the other change is refused.
- **Moving a top-level node under another node**: allowed; it then inherits from its new ancestors.
- **Depth**: a move can make the branch deeper than any existing node; it is allowed (FR-008).
- **Origin labels**: after a move, the "Origen" column and the path links show the new ancestors.
- **A node with no direct attributes**: moves like any other; only its inherited attributes change.
- **The backend cannot be reached**: Spanish error, nothing half-moved, "Reintentar".
- **Attribute deletion (feature 007)**: still refused for attributes assigned to nodes; a move does not
  change assignments.

## Requirements *(mandatory)*

### Functional Requirements

Sources: the feature request (FRQ) and ERF 1.1 §3.3 (tier 2). No tier 1 document in
`docs/source-of-truth/` covers this feature.

**Moving**

- **FR-001 – Move**: An authorized user MUST be able to move a node under another node of the product
  hierarchy. The node's **whole branch** (all descendants at every level) MUST move with it, keeping
  its internal structure. [FRQ; ERF §3.3 «Reubicación de Nodos»]
- **FR-002 – Preserved data**: A move MUST NOT change the system ID, name, description, children or
  direct attribute assignments of any moved node, nor any attribute's own data. Only the parent of the
  moved node changes; its modification date is updated. [FRQ; feature 010 FR-002, FR-011]
- **FR-003 – One change**: A move MUST be applied completely or not at all. After any refused or failed
  move, the hierarchy MUST be exactly as before.
- **FR-004 – Top level**: An authorized user MUST be able to move a node to the **top level** (no
  parent), with its branch. It then inherits nothing. [Clarification 2026-11-19; feature 010 A-003;
  OEB-001]

**Rules**

- **FR-005 – No loops**: Moving a node under itself or under any of its descendants MUST be refused with
  a Spanish message. The destination picker MUST NOT offer those nodes.
- **FR-006 – No-op**: Choosing the node's current parent MUST be refused as having no effect.
- **FR-007 – Sibling names**: A move MUST be refused when the destination already has a child with the
  same name, compared ignoring letter case and surrounding spaces (feature 010 FR-004), naming the
  clash.
- **FR-008 – Depth**: A move MUST NOT be refused because of the resulting depth; as in feature 010, no
  depth limit applies. If the ERF's four-level rule is confirmed (feature 010 OQ-003), it will be added
  to creating and moving together. [Clarification 2026-11-19]
- **FR-009 – Existence**: A move naming a node or destination that does not exist MUST be refused naming
  it.

**Effect on inheritance**

- **FR-010 – Immediate inheritance**: Right after a move, the effective attributes, levels and paths of
  the moved node and of every descendant MUST reflect the new ancestors only: attributes inherited from
  former ancestors that are not ancestors anymore MUST disappear, and those of the new ancestors MUST
  appear with their new origin, on the next read, with no extra step. [ERF §3.3 «debe ignorar
  inmediatamente las configuraciones de su rama de origen y adoptar automáticamente … atributos de su
  nueva plantilla destino»; feature 010 FR-015]
- **FR-011 – Attribute on both the moved node and a new ancestor**: When a moved node has an attribute
  directly and a new ancestor has it directly too, it MUST be listed once as **Directo** with «También se
  hereda de «…»» naming the nearest such ancestor (feature 010 FR-014). The direct assignment MUST be
  kept; nothing is flagged or removed. [Clarification 2026-11-19]
- **FR-012 – Nodes outside the branch**: Nodes outside the moved branch MUST keep their effective
  attributes, levels and paths.

**Interface**

- **FR-013 – Move dialog**: The node detail on the "Taxonomía" page MUST offer "Mover" to users with the
  permission. The dialog MUST show the current path, a destination picker searchable by name or system
  ID (showing each candidate's path), and, before confirming, the new path and the number of nodes that
  will move (the node plus its descendants). Confirmation MUST be explicit; cancelling changes nothing.
  It MUST follow the design system and the patterns of the existing page (feature 010 FR-018), in
  Spanish (es-MX), usable from 768 px without horizontal scroll.
- **FR-014 – Result**: After a successful move, the page MUST show a success notification, keep the moved
  node selected, show it expanded in its new place in the tree, and show its updated level, path and
  effective attributes.
- **FR-015 – Permission**: Moving MUST use the existing development permission `ATTRIBUTE_CREATE`;
  without it the control MUST be hidden and the backend MUST refuse with a Spanish message. No new
  permission. [feature 010 FR-017]
- **FR-016 – Data from the backend**: The rules of FR-003 and FR-005 to FR-009 MUST be enforced by the
  backend; the frontend may hide invalid choices but MUST NOT be the only check. [Constitution III]

**Effect on existing features**

- **FR-017 – Feature 010 amendment**: Feature 010 FR-005 («The parent MUST NOT change») is replaced, for
  moves made through FR-001, by this feature's rules. Creating, renaming and deleting nodes, assignments
  and effective attributes otherwise keep their behavior and API.
- **FR-018 – Other features unchanged**: Attributes (004, 007, 008), groups (005, 009), lists of values
  (006), identifiers (003) and product search (002) MUST keep their behavior and APIs.

### Key Entities

- **Taxonomy node** (feature 010): unchanged fields; its parent can now change through a move.
- **Move** (an action, not stored): the node to move, the destination parent or the top level (FR-004),
  the number of nodes in the branch, the resulting path.
- **Effective attribute** (feature 010, derived): recomputed from the new ancestors after a move.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the example hierarchy, after each move in the user stories, the effective attributes,
  level and path of every node in the moved branch match the expected values exactly (no missing, no
  extra, no duplicate), and every node outside the branch is unchanged.
- **SC-002**: A branch of 200 nodes moves in one confirmation, and 100 % of its nodes show their new level
  and path on the next read.
- **SC-003**: 0 moves create a loop, a sibling name clash or a partly moved branch, including two moves
  confirmed at the same time.
- **SC-004**: 100 % of moved nodes keep their system ID, name, description, children and direct
  attributes.
- **SC-005**: An administrator moves a node to a new parent, found by searching its name, in under 1
  minute.
- **SC-006**: All existing tests of features 001 to 010 still pass, changed only where FR-017 lifts the
  fixed-parent rule.

## Assumptions

- **A-001**: Only the product hierarchy of feature 010 exists; secondary taxonomies are out of scope.
- **A-002**: Moving a whole branch is the only kind of move. Moving a node without its children (children
  re-attached to the old parent) is not offered.
- **A-003**: Moves are made one node at a time from the user interface; bulk moves are out of scope.
- **A-004**: A move takes effect immediately; there is no approval workflow yet (ERF §3.4 is out of
  scope).
- **A-005**: Placing a moved node among its new siblings follows the tree's existing ordering; no manual
  ordering of siblings is introduced.
- **A-006**: Volume as feature 010 (up to about 5,000 nodes); a single branch can hold a large share of
  them.

## Dependencies

| ID | Depends on | Status | Effect |
|---|---|---|---|
| DEP-01 | Feature 010 – Taxonomy and inheritance | Implemented | Nodes, assignments, effective attributes reused; FR-005 amended (FR-017) |
| DEP-02 | Constitution IX – storage | Allowed (2.5.0) | Taxonomy nodes are already stored in MongoDB; no new collection |
| DEP-03 | Feature 010 OQ-003 – depth limit | Open | Not blocking: no limit for now (FR-008) |
| DEP-04 | Templates, products | Not built | Nothing besides nodes moves; OEB-002 applies once products exist |

## Out of Scope

- Moving products or templates; rules for nodes whose descendants hold products (OEB-002).
- Restrictive overwrite, list-value filters, required-ness, metadata and other configurations a node
  could inherit (ERF §3.3); only attributes are inherited today.
- Bulk moves, Excel or API imports of moves, drag and drop in the tree.
- Approval workflow and audit trail of moves (ERF §3.4, §3.5).
- Manual ordering of siblings; copying instead of moving; moving between different taxonomies.
- New permissions or roles.

## Open Questions

OQ-001 to OQ-003 were answered on 2026-11-19 (Clarifications). Still open, not blocking:

- **OQ-004**: Once products exist, must moving a branch with products be refused, as P360 does
  (OEB-002), or allowed with the products adopting the new branch, as the ERF suggests («Si un producto
  … es movido»)? Not needed now.

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-19) | Request | Scope, rules, open points |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.3 «Reubicación de Nodos» and «nivel raíz … hasta cuatro niveles» | 2 | FR-001, FR-010; context for FR-004, FR-008 |
| `p360:P360-repositorios/nuevo-pim/rag-as-is-to-be/reglas-erf/03-erf-1-1-plantillas-taxonomia-creacion-y-cambios.md` RE-PLT-061 | 2 (analysis of the ERF) | Confirms the reading; same text in both ERF copies |
| `p360:…/omega-a-pim-headless/como-funciona/plantillas-atributos-y-tablas.md` §10 (lines 387-413) | 3 | OEB-001 |
| `p360:P360-repositorios/nuevo-pim/trazabilidad/altas-S27-S38.md:66` (S31-RN-10) | 3 | OEB-002 |
| `p360:docs/arquitectura/modulo-plantillas/specs/005-taxonomia-y-metadatos-de-plantilla/spec.md` FR-010, FR-026 | 2 (proposal, Draft) | Proposes moving to the root and refusing moves below level `EU4`; not used as requirements |
| `p360:…/reglas-erf/05-erf-1-2-categorias.md` RE-CAT-011 | 2 (analysis) | ERF 1.2 allows category relocation only in bulk; applies to the web tree (ERF 1.2), not to this hierarchy |
| `specs/010-taxonomy-attribute-inheritance/spec.md` | Project | FR-004, FR-005, FR-012 to FR-018; OQ-003 to OQ-005 |
| `.specify/memory/constitution.md` v2.5.0 (III, IX, XII) | Governance | Storage allowed; backend enforces rules |
| `docs/source-of-truth/` | 1 | Only a README |
