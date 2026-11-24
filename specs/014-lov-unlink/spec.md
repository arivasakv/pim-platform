# Feature Specification: Unlink a List of Values from an Attribute (Desvincular LOV de un atributo)

**Feature Branch**: `014-lov-unlink`

**Created**: 2026-11-23

**Status**: Approved

**Approved**: 2026-11-23, by the project owner

**Input**: Project owner's request of 2026-11-23 (referred to below as *the feature request*), from a
requirement on «Modificación, Desvinculación y Eliminación con Análisis de Impacto»: «Si se desvincula
una LOV de un atributo, el sistema exige redefinir el tipo de dato subyacente para no dejar campos
huérfanos.» This is feature 006's deferred follow-up FU-2 (006 FR-015). Business references (official
`ERF - HUs` copies): `p360:ERF - HUs/HU_New PIM HEADLESS_Plantillas.docx` HU2.1 and
`p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2.1.

## Context

### What exists today

| Item | In pim-platform | Consequence |
|---|---|---|
| List-of-values attributes | Feature 004 FR-012: an attribute of type «Lista de Valores (LOV's)» uses one existing list, single or multiple selection | The attributes this feature unlinks |
| Attribute editing | Feature 007: display name and type properties change; the **data type never changes** (FR-010), except a number switching integer/decimal (amendment 2026-11-20) | This feature is the first, controlled exception |
| Lists of values | Feature 006: the list detail shows its linked attributes (FR-014); unlinking was deferred (FR-015, FU-2); a list linked to attributes cannot be deleted (FR-004) | Unlinking the last attribute makes the list deletable |
| Groups, taxonomy | Features 005/009 and 010/011: attributes belong to groups and are assigned to taxonomy nodes by system ID | Not affected by the attribute's type |
| List-value filters | Feature 012: per taxonomy node and list-of-values attribute, the list values allowed there | Have no meaning once the attribute is not a list (FR-008) |
| Product values | Not built: products hold no attribute values | Nothing to migrate when the type changes |

### What the sources say (tier 2)

- HU2.1 «Desvinculación: Si se desvincula una LOV de un atributo, el PIM exigirá redefinir el nuevo tipo
  de dato para dicho atributo.»
- ERF §3.2.1: «en el caso de la desvinculación debe quedar indicado el tipo de atributo diferente a LOV en
  caso de que ya no se ligue a otra».
- The feature request (owner, 2026-11-23; not found in the reference repository): «… el sistema exige
  redefinir el tipo de dato subyacente para no dejar campos huérfanos.»

### Deferred parts of the same requirement (owner decision, 2026-11-23)

The same requirement also asks that (a) changing a value's label updates every product that holds it,
keeping the history, and (b) deleting a value first shows «El valor seleccionado está asignado a [X]
productos. La eliminación afectará dichos registros. ¿Desea proceder con la baja o realizar una
migración?». Both need products that hold attribute values, which do not exist yet. The owner confirmed
on 2026-11-23 that they **stay deferred** to feature 006's FU-1 and are **not** part of this feature.
Feature 006 already identifies values by list and ID, never by label, so (a) will need no copying.

## Clarifications

### Session 2026-11-23

- Q: Which data types can an unlinked attribute take? → A: **Any of the other seven choices** (OQ-001,
  Option A; FR-002).
- Q: Where is the unlink offered? → A: **Both**: in the attribute's edit form, and from the list's linked
  attributes opening that form in unlink mode (OQ-002, Option C; FR-006).
- Q: What happens to the attribute's list-value filters on taxonomy nodes? → A: **Deleted with the unlink**,
  after the review shows which nodes lose them (OQ-003, Option A; FR-008).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Unlink a list and give the attribute a new data type (Priority: P1)

An authorized user decides that an attribute should no longer use a list of values, for example «Talla
de calzado», which used the list «Tallas», should become a number. The user starts the unlink, chooses
the attribute's new data type and its properties, sees a field preview, and confirms. From then on the
attribute has the new type and no list; its system ID, name, groups and taxonomy assignments stay.

**Why this priority**: This is the feature request; without it a list can never be detached from an
attribute, and the list can never be deleted.

**Independent Test**: With «Talla de calzado» (list «Tallas», single selection), unlink it choosing
«Numérico Decimal (Float)» with 1 decimal; afterwards the attribute is numeric with 1 decimal, keeps its
system ID, name, groups and taxonomy assignments, and «Tallas» no longer lists it.

**Acceptance Scenarios**:

1. **Given** a list-of-values attribute, **When** the user unlinks its list choosing a new data type and
   valid properties for it, **Then** the attribute has the new data type and properties, no list, and the
   same system ID, display name, scope, creation date, groups and taxonomy assignments; its last-change
   date is updated.
2. **Given** the unlink is started, **When** the user has not chosen a new data type, or the properties
   are invalid for the chosen type, **Then** nothing changes and the problem is shown in Spanish, with
   the same rules and messages as creating an attribute of that type (feature 004 FR-008–FR-016).
3. **Given** the list «Tallas» was linked only to «Talla de calzado», **When** the attribute is
   unlinked, **Then** the list's detail shows no linked attributes and the list can now be deleted
   (feature 006 FR-004).
4. **Given** the unlink is started, **When** the user cancels, **Then** nothing changes.

---

### User Story 2 - See what the unlink affects before confirming (Priority: P2)

*The unlink can also be started from the list: each attribute in the list's «Atributos que la usan» offers
it and opens that attribute's edit form in unlink mode (FR-006).*

Before confirming, the user sees which list is detached, what the attribute becomes, and what else
changes (the list-value filters of the attribute on taxonomy nodes, FR-008), so the change is not a
surprise.

**Why this priority**: The requirement is about «Análisis de Impacto»; showing the effect avoids silent
loss of filters. The unlink itself (P1) delivers the value.

**Independent Test**: With filters on «Color» at two taxonomy nodes, start unlinking «Color»; the
confirmation names the list and the two nodes before anything changes.

**Acceptance Scenarios**:

1. **Given** the unlink is started, **When** the user reviews it, **Then** the page states the list that
   will be detached, the new data type, and the number and names of the taxonomy nodes whose value filters
   for the attribute will be deleted; **When** the user confirms, **Then** those filters are deleted with
   the unlink and filters of other attributes stay.
2. **Given** no filters exist for the attribute, **When** the user reviews it, **Then** the page says so.

### Edge Cases

- **Attribute that is not a list of values**: there is nothing to unlink; the action is not offered and a
  direct request is refused.
- **New data type «Lista de Valores (LOV's)»**: refused; this feature does not change one list for
  another (out of scope).
- **The list or the attribute was changed or deleted by someone else meanwhile**: the unlink is refused
  with a message and nothing changes.
- **Attribute used as a list in a taxonomy node's field preview (feature 012)**: after the unlink it is
  shown with its new type wherever it appears.
- **Display name**: unchanged by the unlink; the user may rename the attribute separately (feature 007).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 – Unlink with a new type**: An authorized user MUST be able to unlink the list of values from a
  list-of-values attribute. The unlink MUST be done together with choosing the attribute's new data type
  and that type's properties, in one change: the attribute MUST never be left without a data type or as a
  list-of-values attribute without a list. [HU2.1 «exigirá redefinir el nuevo tipo de dato»; ERF §3.2.1;
  feature request]
- **FR-002 – Allowed new types**: The new data type MUST be any of the other seven choices of feature 004
  FR-007 (amended 2026-11-20): Texto (String), Numérico Entero (Integer), Numérico Decimal (Float), Booleano
  (Sí/No), Fecha y Hora (Date/Time), Moneda (Currency), Referencias / Relaciones (OQ-001, Option A).
  «Lista de Valores (LOV's)» MUST NOT be offered (re-linking is out of scope).
- **FR-003 – Same rules as creation**: The new type's properties MUST follow feature 004's rules for that
  type (FR-008–FR-016, A-003, A-004), checked by the backend, with the same Spanish messages.
- **FR-004 – What stays**: The system ID, display name, scope, creation date, group memberships (features
  005/009) and taxonomy assignments (features 010/011) MUST stay unchanged. The last-change date MUST be
  updated (feature 007).
- **FR-005 – The list**: The list of values itself MUST NOT change (name, values, equivalences). It MUST
  stop showing the attribute among its linked attributes (feature 006 FR-014), and becomes deletable when
  no attribute is linked any more (feature 006 FR-004).
- **FR-006 – Where it is done**: The unlink MUST be offered in two places that lead to one form (OQ-002,
  Option C): in the attribute's edit form (feature 007), as an action of a list-of-values attribute; and
  on each attribute in the list's «Atributos que la usan» (feature 006), which opens that attribute's edit
  form already in unlink mode. Choosing the new type, its properties, the field preview, the review and
  the confirmation happen in that form.
- **FR-007 – Permission**: Unlinking MUST require the same permission as editing attributes and lists
  today (`ATTRIBUTE_CREATE`, features 004/006/007); without it the action is not offered and a direct
  request is refused.
- **FR-008 – List-value filters**: The attribute's list-value filters on taxonomy nodes (feature 012) MUST
  be deleted together with the unlink (OQ-003, Option A), after the review (FR-009) has shown which nodes
  lose them. Filters of other attributes MUST NOT change.
- **FR-009 – Review before confirming**: Before the unlink is applied the user MUST see the list that will
  be detached, the new data type, and the taxonomy nodes whose value filters are affected (FR-008); the
  user MUST confirm explicitly or cancel. [«Análisis de Impacto», feature request]
- **FR-010 – Field preview**: While choosing the new type, the field preview of feature 008 MUST show the
  field as it will be with the new type and properties.
- **FR-011 – All or nothing**: The unlink, the new type and properties and the FR-008 effect on filters
  MUST be applied together or not at all; if the attribute or its list changed since the review, nothing
  is applied and the user is told to review again.
- **FR-012 – Not an attribute of type list**: Unlinking an attribute that is not of type list of values,
  or choosing «Lista de Valores (LOV's)» as the new type, MUST be refused with a Spanish message; nothing
  changes.
- **FR-013 – Feature 007 FR-010 amended**: Feature 007's rule that the data type never changes MUST be
  amended to allow exactly this change: from list of values to another type, through the unlink.

### Key Entities *(include if feature involves data)*

- **Attribute** (features 004/007): system ID, display name, data type, type properties, scope, dates.
  The unlink changes its data type and properties only.
- **List of values** (feature 006): unchanged; loses one linked attribute.
- **List-value filter** (feature 012): per taxonomy node and list-of-values attribute; affected per FR-008.
- **Unlink review**: not stored. The list to detach, the new type and properties, and the affected
  filters, shown before confirming.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user unlinks a list from an attribute and gives it a new type in under 2 minutes, without
  leaving the page.
- **SC-002**: In 100% of attempts, an attribute is never left without a data type or as a list-of-values
  attribute without a list, including when the new properties are invalid or the user cancels.
- **SC-003**: After an unlink, the attribute's system ID, name, groups and taxonomy assignments are
  identical to before in 100% of cases.
- **SC-004**: Before confirming, users see every taxonomy node whose filters the unlink affects.

## Assumptions

- **A-001**: Products hold no attribute values yet, so the type change needs no product migration; when
  product values exist, an unlink will need its own impact rules (tied to 006 FU-1).
- **A-002**: The permission is `ATTRIBUTE_CREATE`, the one used for attributes and lists today (feature 006
  A-008, feature 007).
- **A-003**: Re-linking the attribute to another list (changing its list) is out of scope; it can be added
  later as a separate change.
- **A-004**: Taxonomy assignments stay; an attribute's type does not decide which nodes it is assigned to.
- **Out of scope**: product values, label propagation and deletion impact counts (deferred, see Context),
  value or attribute migration, merge, changing a list for another.

## Dependencies

- **DEP-01**: Features 004 and 007 (attributes, editing) — implemented, on `main`; FR-013 amends 007 FR-010.
- **DEP-02**: Feature 006 (lists of values) — implemented, on `main`; delivers its FU-2.
- **DEP-03**: Feature 012 (list-value filters) — implemented, on `main`.
- **DEP-04**: Feature 008 (field preview) — implemented, on `main`.

## Open Questions

- **OQ-001** (FR-002): *Resolved 2026-11-23 (Option A): any of the other seven data type choices.*
- **OQ-002** (FR-006): *Resolved 2026-11-23 (Option C): from the attribute's edit form and from the list's
  linked attributes, both leading to the same form.*
- **OQ-003** (FR-008): *Resolved 2026-11-23 (Option A): the attribute's filters are deleted with the unlink,
  after the review shows them.*
- **OQ-004**: Source document of the feature request: the text was not found in the reference repository.
