# Feature Specification: List-Value Filters per Taxonomy Level (Filtros contextuales por nivel)

**Feature Branch**: `012-taxonomy-list-value-filters`

**Created**: 2026-11-19

**Status**: Approved

**Approved**: 2026-11-19, by the project owner

**Input**: User request of 2026-11-19 (referred to below as *the feature request*): on a node of the
product hierarchy, an authorized user restricts which values of a list-of-values attribute are allowed
there; the restriction is inherited by every node below; a lower node may narrow it further but never
allow a value excluded above; the node shows, for each list attribute, the allowed values and where the
restriction comes from; removing a filter restores the inherited values; a moved node follows the
filters of its new branch. Business references (official `ERF - HUs` copies, project owner decision of
2026-11-19): `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2.3 and §3.3, and
`p360:ERF - HUs/HU_New PIM HEADLESS_Plantillas.docx` HU2.3 and HU3.

## Context

### What exists today

| Item | In pim-platform | Consequence |
|---|---|---|
| Lists of values | Feature 006: lists with values (ID + label, up to 50,000 per list) | The values a filter chooses from |
| List-of-values attributes | Features 004/007: an attribute of type "Lista de valores" uses one list, single or multiple selection; the list cannot be changed after creation | The attributes a filter applies to |
| Product hierarchy | Features 010/011: nodes, direct attribute assignments, inheritance computed from the current ancestors, moving nodes | Where filters are set and how they are inherited |
| Field preview | Feature 008: shows how an attribute's input field looks, with its real list values | Can show the field as it would be captured at a node |
| Templates, products | Not built | Filters are set on nodes; their effect on product capture comes with products |

### What the sources say (tier 2)

- ERF §3.2.3 «Filtros Contextuales por Nivel: Se utilizan para acotar los valores de una LOV global
  dependiendo del nivel de la taxonomía. Por ejemplo, la LOV "Color" puede tener 1000 valores
  globalmente, pero en la plantilla de "Zapatos" se filtra localmente para mostrar solo 20 colores
  pertinentes.»
- ERF §3.3 «Se debe poder aplicar filtros de los atributos en cualquier nivel según se requiera.»
- ERF §3.3 «Sobreescritura Restrictiva: Las plantillas de nivel inferior pueden sobrescribir las
  configuraciones de sus plantillas padre, siempre y cuando se respeten o endurezcan las restricciones
  superiores. (Ej. Si el padre filtra la Lista de Valores a 5 opciones, el hijo puede filtrar a 3, pero
  nunca ampliarla a 6).»
- ERF §3.3 «Do not use local filter no inhabilita filtros de niveles superiores, sólo inhabilita que se
  definan nuevos filtros en el nivel actual» and «Los filtros también son heredados a niveles y
  productos inferiores; si un nivel superior determinó que un valor está fuera de lo permitido, … dicha
  limitante debe respetarse pero puede limitar aún más los valores en objetos que se encuentren
  debajo sobre escribiendo el filtro.»
- HU2.3 «Filtros Contextuales por Nivel»: a dialog configures a list attribute «dentro de una plantilla
  o categoría específica» with two lists: «Lista de Valores Heredados/Globales» (all options) and «Lista
  de Valores Incluidos en Filtro» (the values moved in as allowed); «Las pantallas de catalogación
  asociadas a dicha plantilla únicamente desplegarán los valores acotados en la lista de incluidos.»
- HU3: «Sobrescritura Restrictiva de Filtros: … únicamente para endurecer o restringir más las
  opciones»; «Opción "Do Not Use Local Filter": Al activarse en un nivel, inhabilita la definición de
  nuevos filtros locales en ese nivel, pero mantiene activos los filtros heredados de niveles
  superiores.»

## Clarifications

### Session 2026-11-19

- Q: Filters are new data not allowed in MongoDB by constitution IX. Amend it? → A: Yes. A constitution
  amendment (2.6.0) adds list-value filters on taxonomy nodes to attribute administration data, stored
  with the taxonomy nodes, and ADR 0003 is amended, as for feature 010 (DEP-02).
- Q: Templates are not defined yet. Set filters on taxonomy nodes now? → A: Yes, per node now; the
  template question stays open (feature 010 OQ-001) (FR-015).
- Q: Is «Do Not Use Local Filter» in scope now? → A: No, later, as a small follow-up feature (FR-018;
  User Story 5 moved to Deferred Scope).

## User Scenarios & Testing *(mandatory)*

Users: anyone can view nodes, their list attributes and allowed values. Defining, changing and removing
filters uses the **same development permission as the other hierarchy changes** (`ATTRIBUTE_CREATE`,
feature 010 FR-017); no new permission.

Example used below (feature 010 example plus lists):

```text
List "Colores": Rojo, Azul, Verde, Negro, Blanco, Amarillo, Rosa  (7 values)
Attribute "Color" (Lista de valores, "Colores")

Ropa                      (direct: Color)
├── Ropa de hombre
│   ├── Camisas
│   │   └── Camisas formales
│   └── Pantalones
└── Ropa de mujer
Calzado                   (direct: Color)
```

### User Story 1 - Restrict the values of a list attribute on a node (Priority: P1)

An administrator opens a node, chooses a list attribute that applies to it and picks the values
allowed there. The node then shows that only those values are allowed, and that the restriction is set
on this node.

**Why this priority**: The core of ERF §3.2.3 «Filtros Contextuales por Nivel» and HU2.3; without it
nothing else exists.

**Independent Test**: On "Ropa de hombre", filter "Color" to Azul, Negro, Blanco and Rojo. The node shows
"Color: 4 de 7 valores · Filtro en este nodo".

**Acceptance Scenarios**:

1. **Given** the example and the permission, **When** the user opens "Ropa de hombre", chooses
   "Filtrar valores" on "Color", moves Azul, Negro, Blanco and Rojo to "Valores incluidos en el filtro"
   and saves, **Then** "Color" shows 4 of 7 values allowed, with the origin "Este nodo".
2. **Given** the filter dialog, **Then** it shows two lists: "Valores disponibles" (the values allowed
   at this node before its own filter: all 7 here) and "Valores incluidos en el filtro"; both can be
   searched by label or value ID, and values move between them one by one or all at once.
3. **Given** the dialog, **When** the user tries to save with no included value, **Then** saving is
   refused with a message asking for at least one value (to allow every value, remove the filter).
4. **Given** a list attribute that does not apply to the node (neither direct nor inherited), or an
   attribute that is not a list of values, **When** a filter for it is requested, **Then** it is refused
   and nothing changes.
5. **Given** a user without the permission, **Then** values and filters are shown but no "Filtrar
   valores" control, and the backend refuses changes.

---

### User Story 2 - Filters are inherited and can only be narrowed below (Priority: P1)

Every node below a filtered node allows only the filtered values. A lower node may narrow them further,
never widen them.

**Why this priority**: ERF §3.3 «Sobreescritura Restrictiva» and «Los filtros también son heredados»;
without inheritance a filter on "Zapatos" would not reach its sub-levels.

**Independent Test**: With "Color" filtered to 4 values on "Ropa de hombre", "Camisas formales" shows
"Color: 4 de 7 valores · Filtro heredado de «Ropa de hombre»". Filtering "Camisas" to Azul and Blanco
makes "Camisas formales" show 2 values from «Camisas».

**Acceptance Scenarios**:

1. **Given** the filter of US1, **When** the user opens "Camisas", "Camisas formales" or "Pantalones",
   **Then** each shows the 4 values, with the origin "Ropa de hombre"; "Ropa de mujer" and "Ropa" still
   show 7.
2. **Given** the filter dialog on "Camisas", **Then** "Valores disponibles" offers only the 4 values
   allowed above; Verde, Amarillo and Rosa are not offered.
3. **Given** "Camisas" filtered to Azul and Blanco, **Then** "Camisas" and "Camisas formales" allow 2
   values, origin "Camisas"; "Pantalones" still allows 4.
4. **Given** a request that includes on "Camisas" a value excluded above (e.g. Verde), **Then** it is
   refused naming the value, and nothing changes.
5. **Given** "Camisas" filtered to Azul and Blanco, **When** "Ropa de hombre" is later narrowed to Azul and
   Negro, **Then** "Camisas" allows only Azul (the values allowed by every filter above it and its own),
   and its filter shows that Blanco is excluded by «Ropa de hombre».
6. **Given** the narrowing in scenario 5 leaves no value common to all filters on the way, **Then** the
   node shows that no value is allowed, naming the filters that conflict, so an administrator can fix
   them.

---

### User Story 3 - Change or remove a filter; moved nodes follow their new branch (Priority: P2)

An administrator changes the values of a filter or removes it; the node and its descendants then allow
the values inherited from above. A node moved to another branch follows the filters of that branch.

**Why this priority**: Filters must be maintainable; feature 011 requires moved nodes to adopt the
restrictions of their destination («adoptar automáticamente las restricciones… de su nueva plantilla
destino»).

**Independent Test**: Remove the filter of "Camisas": "Camisas formales" allows 4 values again, from
«Ropa de hombre». Move "Camisas" under "Ropa de mujer": it allows 7 values (only its own filter, if any,
still applies).

**Acceptance Scenarios**:

1. **Given** a filter on "Camisas", **When** the user changes its values (within those allowed above),
   **Then** the node and its descendants reflect the new values on their next read.
2. **Given** a filter on "Camisas", **When** the user removes it (after confirming), **Then** "Camisas" and
   "Camisas formales" allow the values inherited from above.
3. **Given** "Ropa de hombre" filters "Color" to 4 values, **When** "Camisas" (no filter of its own) is
   moved under "Ropa de mujer", **Then** "Camisas" and "Camisas formales" allow all 7 values.
4. **Given** "Camisas" has its own filter (Azul, Blanco) and is moved under "Calzado", where "Color" is
   filtered to Negro and Blanco, **Then** "Camisas" allows only Blanco.

---

### User Story 4 - See the field as it will be captured at a node (Priority: P3)

From a node, an administrator previews a list attribute's input field with only the values allowed at
that node.

**Why this priority**: HU2.3 «Las pantallas de catalogación … únicamente desplegarán los valores
acotados»; there is no product capture yet, so the preview of feature 008 is the way to see the effect.

**Independent Test**: On "Camisas formales", preview "Color": the field offers the 2 allowed values only.

**Acceptance Scenarios**:

1. **Given** a node and a list attribute that applies to it, **When** the user chooses "Vista previa",
   **Then** the field of feature 008 is shown with only the values allowed at the node, in list order.
2. **Given** no filter applies, **Then** the preview offers every value of the list, as in feature 008.

---

### Edge Cases

- **Many values**: a list can have up to 50,000 values; both lists in the dialog are searchable and
  shown in pages, and "Mover todos" moves the values that match the search.
- **A value is added to the list later** (feature 006): filters are lists of *included* values, so the
  new value is not allowed on filtered nodes until it is added to their filters; unfiltered nodes allow
  it at once.
- **A value is deleted from the list** (feature 006): it disappears from every filter's effect; a filter
  left with no remaining value behaves like scenario US2-6 (no value allowed) and is shown as such.
- **A value's label changes**: filters show the current label (they keep only value IDs).
- **The attribute stops applying to a node** (its assignment above is removed, feature 010, or the node
  moves, feature 011): filters for it on that node are kept but have no effect and are not shown among
  the node's list attributes; they take effect again if the attribute applies again (OQ-002).
- **The same attribute assigned on several levels**: one set of filters per attribute per node; the
  allowed values combine all filters on the way down, wherever the attribute is assigned.
- **Including every available value**: allowed (it records an explicit choice), and the node shows the
  filter as set on this node.
- **Attribute deletion** (feature 007): still refused while the attribute is assigned to nodes (feature
  010 FR-021); a filter alone never blocks or changes attribute deletion.
- **List value deletion**: not blocked by filters (feature 006 behavior unchanged).
- **Concurrent changes**: each filter change is applied whole or not at all.
- **The backend cannot be reached**: Spanish error, nothing half-saved, "Reintentar".

## Requirements *(mandatory)*

### Functional Requirements

Sources: the feature request (FRQ), ERF 1.1 §3.2.3 and §3.3, HU2.3 and HU3 (all tier 2). No tier 1
document in `docs/source-of-truth/` covers this feature.

**Filters**

- **FR-001 – Filter**: An authorized user MUST be able to define, on a node, a **filter** for a
  list-of-values attribute that applies to the node (direct or inherited, feature 010): the set of the
  list's value IDs **included** (allowed) there. [ERF §3.2.3 «Filtros Contextuales por Nivel»; §3.3
  «filtros … en cualquier nivel»; HU2.3]
- **FR-002 – One per attribute and node**: A node MUST have at most one filter per attribute; saving
  replaces it. A filter MUST include at least one value.
- **FR-003 – Only list attributes that apply**: A filter for an attribute that is not of type "Lista de
  valores", that does not exist, or that does not apply to the node MUST be refused naming it, and
  nothing MAY change.
- **FR-004 – Only existing values**: A filter MUST only include value IDs of the attribute's list
  (compared ignoring letter case); an unknown ID MUST be refused naming it.
- **FR-005 – Remove**: An authorized user MUST be able to remove a node's filter (after confirming); the
  node then allows the values inherited from above.
- **FR-006 – Unchanged data**: Defining, changing or removing a filter MUST NOT change the attribute, the
  list, its values, the node's other data or any assignment.

**Inheritance and restriction**

- **FR-007 – Inheritance**: A filter MUST apply to the node and to **all its descendants**, at every
  level. [ERF §3.3 «Los filtros también son heredados»]
- **FR-008 – Allowed values**: The values allowed for a list attribute at a node MUST be the list's values
  that are included in **every** filter for that attribute on the node and on its ancestors; with no
  filter on the way, all the list's values. [ERF §3.3 «dicha limitante debe respetarse pero puede limitar
  aún más»]
- **FR-009 – Restrictive overwrite**: When defining or changing a filter, the user MUST only be able to
  include values allowed by the filters above the node; a request including a value excluded above MUST
  be refused naming the value. [ERF §3.3 «Sobreescritura Restrictiva … nunca ampliarla a 6»; HU3]
- **FR-010 – Later narrowing above**: When a filter above is narrowed after a lower filter was saved, the
  lower filter MUST keep its stored values, but only those also allowed above are allowed (FR-008); its
  detail MUST show which of its values are excluded above and by which node. (US2 scenario 5)
- **FR-011 – No value allowed**: When no value is allowed for an attribute at a node, the node MUST say so
  and name the filters involved. (US2 scenario 6)
- **FR-012 – Always current**: Allowed values MUST always reflect the current hierarchy, assignments,
  filters and list values: any change MUST be visible on the next read of every affected node, with no
  extra step, including after a move (feature 011 FR-010). [ERF §3.3 «Reubicación de Nodos … adoptar
  automáticamente las restricciones»]

**Interface**

- **FR-013 – List attributes on the node**: The node detail on "Taxonomía" MUST show, for each list
  attribute that applies to the node, the number of allowed values out of the list's total, and where the
  restriction comes from: «Sin filtro», «Filtro en este nodo» or «Filtro heredado de «{nodo}»» (the
  nearest filter on the way up; with filters on several levels, the node shows each one involved).
- **FR-014 – Filter dialog**: "Filtrar valores" MUST open a dialog with two searchable, paged lists,
  "Valores disponibles" (allowed above, not included yet) and "Valores incluidos en el filtro", moving
  values one by one or all matching ones, with "Guardar filtro", "Quitar filtro" (when one exists) and
  "Cancelar". Spanish (es-MX), design system, usable from 768 px without horizontal scroll. [HU2.3]
- **FR-015 – Scope of filters**: Filters MUST be set on **taxonomy nodes**. How they relate to templates
  is decided when templates are specified (feature 010 OQ-001). [Clarification 2026-11-19]
- **FR-016 – Preview**: For each list attribute on the node, "Vista previa" MUST show feature 008's field
  with only the allowed values. (US4)
- **FR-017 – Permission**: Changing filters MUST use the existing development permission
  `ATTRIBUTE_CREATE`; without it the controls MUST be hidden and the backend MUST refuse. No new
  permission. [feature 010 FR-017]
- **FR-018 – "Do Not Use Local Filter"**: Not part of this feature; deferred to a follow-up feature
  (Deferred Scope). [Clarification 2026-11-19]
- **FR-019 – Rules in the backend**: Allowed values, inheritance and FR-002 to FR-011 MUST be computed and
  enforced by the backend; the frontend MUST NOT compute allowed values. [Constitution III; feature 010
  FR-020]

**Effect on existing features**

- **FR-020 – Unchanged features**: Lists of values (006), attributes (004, 007, 008), groups (005, 009),
  identifiers (003), product search (002), node CRUD, assignments and moves (010, 011) MUST keep their
  behavior and APIs; feature 010's node detail gains the list-attribute information of FR-013.

### Key Entities

- **List-value filter** (new): a node, a list-of-values attribute, and the included value IDs (at least
  one), with creation and modification dates.
- **Allowed values** (derived, not stored): for a node and a list attribute, the list values included in
  every filter on the node and its ancestors, with the filters involved and their nodes.
- **Taxonomy node**, **attribute**, **list of values** (features 010, 004, 006): referenced, never changed
  by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the example and every scenario of US1 to US3, the allowed values of "Color" at every
  node match the expected set exactly (no missing, no extra value).
- **SC-002**: 0 filters can be saved that include a value excluded above.
- **SC-003**: After any filter change, assignment change, value change or move, 100 % of the affected
  nodes show the new allowed values on their next read, at any depth.
- **SC-004**: An administrator restricts a 1,000-value list to 20 values found by search, on a node, in
  under 3 minutes.
- **SC-005**: With a 50,000-value list, opening the filter dialog and searching shows results in under 2
  seconds.
- **SC-006**: All existing tests of features 001 to 011 still pass.

## Assumptions

- **A-001**: A filter is an **inclusion list** («Lista de Valores Incluidos en Filtro», HU2.3): values
  added to the list later are not allowed on filtered nodes until included.
- **A-002**: Filters apply to list-of-values attributes only; other attribute properties (ranges, masks,
  required-ness, business) are not filtered per level in this feature.
- **A-003**: Global "Fuera de alcance" values (§3.2.3 Filtros Generales) are a separate feature; when it
  arrives, out-of-scope values are excluded everywhere in addition to these filters.
- **A-004**: Filters take effect immediately; there is no approval workflow yet (ERF §3.4).
- **A-005**: Single and multiple selection attributes are filtered the same way; the selection mode does
  not change.

## Dependencies

| ID | Depends on | Status | Effect |
|---|---|---|---|
| DEP-01 | Features 006, 004/007, 008, 010, 011 | Implemented | Lists, list attributes, preview, nodes, inheritance, moves |
| DEP-02 | Constitution IX – storage | **Amendment approved 2026-11-19**, to be made before storage code | Constitution 2.6.0 adds list-value filters on taxonomy nodes to attribute administration data; ADR 0003 amended (as for feature 010) |
| DEP-03 | Templates (feature 010 OQ-001) | Not specified | See FR-015 |
| DEP-04 | Products | Not built | The effect on product capture (HU2.3 «pantallas de catalogación») comes with products; until then US4 shows it |

## Deferred Scope

- **«Do Not Use Local Filter»** (ERF §3.3, HU3), by owner decision of 2026-11-19: a per-node option that
  prevents filters on that node while keeping inherited ones. Planned as a small follow-up feature; it
  will need a rule for a node that already has its own filters when the option is turned on.

## Out of Scope

- Global "Fuera de alcance" values (§3.2.3 Filtros Generales), cascading lists (Filtros de Dependencia)
  and transformations (Filtros con transformación).
- Filtering by business («Negocio»), suppliers or any other dimension; required-ness, conditional
  attributes and other per-level attribute settings (ERF §3.3 list).
- Products, templates, approvals and audit trail; bulk or Excel import of filters.
- Changing the list linked to an attribute.

## Open Questions

The three clarification points (FR-015, FR-018, DEP-02) were answered on 2026-11-19. Still open, not
blocking:

- **OQ-002**: Filters for an attribute that stops applying to a node (edge case): kept with no effect (this
  spec), or removed automatically?
- **OQ-003**: Should P360's existing filters be migrated later? The reference repository has not been
  checked for how P360 stores them; not needed now.

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-19) | Request | Scope, rules, open points |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2.3 «Filtros (generales y específicos)», §3.3 (filtros en cualquier nivel, Sobreescritura Restrictiva, Do not use local filter, filtros heredados, Reubicación de Nodos) | 2 | FR-001, FR-007 to FR-012; Deferred Scope |
| `p360:ERF - HUs/HU_New PIM HEADLESS_Plantillas.docx` HU2.3 «Filtros Contextuales por Nivel», HU3 | 2 | Dialog with two lists (FR-014), inclusion list (A-001), restrictive overwrite; Do Not Use Local Filter (deferred) |
| `p360:P360-repositorios/nuevo-pim/rag-as-is-to-be/reglas-erf/02-…` and `03-…` (RE-PLT-049, RE-PLT-060) | 2 (analysis of the ERF) | Confirms the reading |
| `specs/006`, `specs/004`, `specs/007`, `specs/008`, `specs/010`, `specs/011` | Project | Reused behavior and limits (50,000 values per list) |
| `.specify/memory/constitution.md` v2.5.0 (III, IX) | Governance | DEP-02 amendment |
| `docs/source-of-truth/` | 1 | Only a README |
