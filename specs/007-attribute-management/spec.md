# Feature Specification: Attribute Management – Consult, Edit and Delete Attributes (Administración de Atributos – Consulta, Edición y Eliminación)

**Feature Branch**: `007-attribute-management` (spec directory; no git branch created, work stays
uncommitted on `main` until the project owner asks)

**Created**: 2026-11-11

**Status**: Approved

**Approved**: 2026-11-11, by the project owner, after clarifications Q1–Q3, together with the plan's
decisions D-1 to D-10 (plan.md)

**Input**: User request of 2026-11-11 (referred to below as *the feature request*): complete the
existing Attribute Management so users can consult, search, edit and delete existing attributes, with
LOV Management (feature 006) as the primary UI/UX reference. Attribute creation (feature 004) already
works and must not be rebuilt. Permissions and Data Stewardship are explicitly out of scope: no new
permission checks, roles or matrices, and existing authorization behavior unchanged.

## Context

Feature 004 delivered the **creation** of attribute definitions and left "editing, deleting,
deactivating or copying attributes; changing an attribute's type" out of scope (feature 004, Out of
Scope; DEP-08). Features 005 and 006 then added groups of attributes and lists of values, both of
which point at attributes. This feature completes the attribute life cycle: **consult, search, edit
and delete**.

ERF 1.1 §3.2 names the four operations together: creating, editing, consulting and deleting
attributes are controlled by permission matrices [Source: p360:ERF - HUs/ERF 1.1 Plantillas.docx §3.2
¶1, as analysed in p360:…/reglas-erf/01-erf-1-1-plantillas-identificadores-y-atributos.md
RE-PLT-005; HU2 RE-HPL-003 «crean, editan, eliminan»]. The permission matrices themselves are out of
scope (feature request §4).

### What already exists and what this feature adds

| Capability | Status | Where |
|---|---|---|
| Create an attribute of any of the 7 types, with its properties and validations | **Exists** | Feature 004 FR-001–FR-023; page "Crear Atributos" |
| System ID from feature 003, immutable | **Exists** | Feature 004 FR-004–FR-006; feature 003 |
| Read all attributes / one attribute through the API (sorted by display name) | **Exists** (API only; used by feature 005's member picker) | Feature 004 FR-021; feature 005 plan D-3 |
| Attribute groups and membership | **Exists** | Feature 005 |
| Lists of values; a list shows its linked attributes; a linked list cannot be deleted | **Exists** | Feature 006 FR-004, FR-014 |
| A page listing attributes, with search and filter | **New** | User Story 1 |
| An attribute's detail view (full configuration, groups, list of values) | **New** | User Story 1 |
| Editing an attribute, reusing the creation form and its validations | **New** | User Story 2 |
| Deleting an attribute, with confirmation and dependency checks | **New** | User Story 3 |

### Dependencies an attribute can have today

| Object | Exists today? | Effect on edit and delete |
|---|---|---|
| Attribute groups (feature 005) | Yes: a group lists its member attributes by system ID | Edit: membership is kept. Delete: see FR-014 |
| List of values (feature 006) | Yes: an LOV attribute holds the ID of one list | Edit: see FR-009. Delete: the list stays; only the link disappears |
| Product values | **No**: products hold no attribute values yet (feature 006 FU-1) | Nothing to count or change now; DEP-03 |
| Templates | **No**: no template feature exists (feature 004 DEP-05) | Nothing to check now; DEP-04 |
| System ID registry (feature 003) | Yes | The ID never changes and is never reused, even after deletion (FR-006, FR-016) |

## Clarifications

### Session 2026-11-11

- Q: Where does attribute management sit in the side navigation? → A: **One "Atributos" page replaces
  "Crear Atributos"**: the attributes on the left, the selected attribute's detail and edit form on the
  right, and a "Nuevo atributo" action that opens feature 004's creation form on the same page, as
  "Lista de Valores" does for lists (Q1, Option A). FR-001, User Story 1 and DEP-01 were updated.
- Q: What happens when the user deletes an attribute that belongs to groups? → A: **Refused until the
  attribute is removed from every group**; the message names the groups, as feature 005 refuses
  deleting a non-empty group and feature 006 a list in use (Q2, Option A). FR-014, User Story 3 and the
  success criteria were updated.
- Q: Which permission do editing and deleting need? → A: **The existing development check
  `ATTRIBUTE_CREATE`**, which already guards creating attributes, groups and lists; without it the
  change controls are hidden and the backend refuses the change. No new permission code, role or
  matrix (Q3, Option A). FR-018 and A-001 were updated.

## User Scenarios & Testing *(mandatory)*

The user is a **user who can currently access Attribute Management** (feature request §4). This
feature adds no role or permission; how change operations relate to the existing development
permission is FR-018.

### User Story 1 - Consult and search attributes (Priority: P1)

A user opens Attribute Management and sees every attribute with its display name, system ID, data
type, groups and, for list-of-values attributes, the linked list. They search by name or ID, filter
by data type and open an attribute to see its complete configuration.

**Why this priority**: Today attributes can be created but not seen in the application; consulting is
also the entry point for editing and deleting.

**Independent Test**: With three attributes (a text attribute in group "Logísticos", an LOV attribute
linked to list "Color", a Boolean attribute without groups), open the page: all three are listed with
their columns; searching "col" leaves only the LOV attribute; filtering by "Booleano" leaves only the
Boolean one; opening the text attribute shows every property it was created with.

**Acceptance Scenarios**:

1. **Given** existing attributes, **When** the user opens "Atributos", **Then** every
   attribute is listed, sorted by display name ignoring letter case, with display name, system ID,
   data type (Spanish label), group names and, for LOV attributes, the linked list's name.
2. **Given** the list, **When** the user types in the search field, **Then** only attributes whose
   display name or system ID contains the text (ignoring letter case and accents) remain, and the
   number of matches is shown.
3. **Given** the list, **When** the user chooses a data type in the filter, **Then** only attributes
   of that type remain; search and filter combine.
4. **Given** a search with no match, **When** the list is empty, **Then** a Spanish message says no
   attribute matches, and clearing the search shows all attributes again.
5. **Given** an attribute, **When** the user opens it, **Then** the detail shows its system ID,
   display name, description, data type, scope (global), every type-specific property, the groups
   it belongs to, the linked list (LOV attributes), and when it was created and last changed.
6. **Given** no attribute exists, **When** the page opens, **Then** an empty state says so and offers
   "Nuevo atributo".
8. **Given** the page, **When** the user chooses "Nuevo atributo" and saves a valid attribute,
   **Then** it is created exactly as in feature 004, appears in the list and opens in the detail.
9. **Given** a user without `ATTRIBUTE_CREATE`, **When** they open the page, **Then** they can list,
   search and consult attributes but see no create, edit or delete controls (FR-018).
7. **Given** the attributes cannot be loaded, **When** the page opens, **Then** a Spanish error with
   a retry action is shown.

---

### User Story 2 - Edit an attribute (Priority: P1)

The user opens an attribute and edits it in the same form used for creation, filled with its current
values. The system ID and the data type are shown but cannot be changed. The same validations as
creation apply.

**Why this priority**: Correcting a name, a description or a validation rule is the most frequent
maintenance task; without it a mistake can only be fixed by deleting and recreating the attribute,
which changes its system ID.

**Independent Test**: Edit a text attribute: change its display name from "Peso" to "Peso neto" and
its maximum length from 50 to 80; after saving, the detail shows both changes, the same system ID and
the same groups. Renaming it to the name of another attribute is refused.

**Acceptance Scenarios**:

1. **Given** an attribute, **When** the user chooses "Editar", **Then** the creation form opens with
   every current value filled in, the system ID shown read-only and the data type shown read-only.
2. **Given** the form, **When** the user changes the display name, description or type-specific
   properties and saves, **Then** the attribute is saved with the changes, its system ID, data type,
   scope, groups and creation date are unchanged, and a Spanish confirmation is shown.
3. **Given** another attribute named "Color", **When** the user renames this attribute to " color ",
   **Then** saving is refused with feature 004's duplicate-name message; keeping the attribute's own
   name (or changing only its letter case) is allowed.
4. **Given** invalid or contradictory properties (minimum above maximum, invalid regular expression,
   precision for an integer, unknown currency, missing reference target), **When** the user saves,
   **Then** saving is refused with feature 004's message naming the property and nothing changes.
5. **Given** an LOV attribute, **When** the user changes its selection mode or links it to another
   existing list, **Then** the change is saved and both lists' details show the new situation; an
   unknown list is refused.
6. **Given** a request that names another system ID or another data type (for example through the
   API), **When** it is sent, **Then** it is refused with a Spanish message and nothing changes; the
   scope and creation date cannot be changed by any request.
7. **Given** the user cancels the edit, **When** they return to the detail, **Then** nothing changed.
8. **Given** the attribute was deleted by someone else meanwhile, **When** the user saves, **Then** a
   Spanish message says the attribute no longer exists.

---

### User Story 3 - Delete an attribute (Priority: P2)

The user deletes an attribute they no longer need. Before deleting, the application shows what the
attribute is connected to and asks for confirmation. Deletion that would leave inconsistent data is
refused with an explanation.

**Why this priority**: Needed to clean up mistakes and obsolete attributes, but less frequent than
consulting and editing, and it depends on the dependency rules.

**Independent Test**: Delete an attribute with no groups: the confirmation names it, confirming
removes it from the list and its detail is no longer found; its system ID is not given to the next
new attribute. Try to delete an attribute that belongs to a group: the outcome follows FR-014.

**Acceptance Scenarios**:

1. **Given** an attribute, **When** the user chooses "Eliminar", **Then** a confirmation in the style of
   feature 006 names the attribute and states its dependencies: number and names of its groups and,
   for an LOV attribute, the linked list (which is kept).
2. **Given** the confirmation, **When** the user cancels, **Then** nothing changes.
3. **Given** an attribute with no dependency that blocks deletion, **When** the user confirms,
   **Then** the attribute is deleted, a Spanish confirmation is shown, it disappears from the list,
   and reading it afterwards answers that it does not exist.
4. **Given** an attribute that belongs to groups "Logísticos" and "Ventas", **When** the user asks to
   delete it, **Then** the confirmation names both groups and says it must be removed from them first,
   and the deletion cannot be confirmed; a deletion request that still arrives (for example through the
   API, or because a group was added meanwhile) is refused with a Spanish message naming the groups, and
   nothing changes. After removing it from both groups, deletion succeeds (FR-014).
5. **Given** an LOV attribute, **When** it is deleted, **Then** its list of values is not deleted or
   changed, and the list's detail no longer shows the attribute; if it was the last attribute linked
   to that list, the list can now be deleted (feature 006 FR-004).
6. **Given** a deleted attribute, **When** a new attribute is created, **Then** it never receives the
   deleted attribute's system ID (FR-016).
7. **Given** the attribute was already deleted, **When** the user confirms, **Then** a Spanish message
   says it no longer exists.

---

### Edge Cases

- **Search with only spaces**: treated as no search.
- **Search with accents** ("logistico" vs. "Logístico"): matches ignoring accents and letter case.
- **Hundreds of attributes**: the list stays usable; it shows a first page of rows and "Mostrar más",
  as feature 006 does for values (A-006).
- **Editing to the same values**: allowed; saved without error (the last-change date may update).
- **Renaming an attribute only in letter case** ("color" → "Color"): allowed; it is still the same
  attribute (FR-008).
- **Two users edit the same attribute**: the last save wins (A-004).
- **An attribute is deleted while another user has it open**: their next save or delete answers that
  it no longer exists (User Story 2 scenario 8, User Story 3 scenario 7).
- **An attribute is deleted while it is being added to a group** or **an LOV attribute is relinked to
  a list while that list is being deleted**: the operations never interleave; one of them is refused
  (FR-017).
- **An LOV attribute is edited to another list when only one list exists**: the list select offers only
  the existing lists; the current list stays selected.
- **Changing an LOV attribute from multiple to single selection**: allowed while no product holds
  values (DEP-03); once values exist, this change needs a rule (OQ-003).
- **Tightening a rule** (for example lowering a maximum length): allowed while no product holds values
  (DEP-03); once values exist, existing values could stop complying (OQ-003).
- **Deleting an attribute that templates or product values use**: impossible today (neither exists);
  the deletion rules must be extended when they arrive (DEP-03, DEP-04).
- **Save or deletion fails because the system cannot store the change**: a Spanish error; the form
  keeps the entered data (feature 004 A-007).

## Requirements *(mandatory)*

Sources: the feature request; `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.1 (immutable ID), §3.2 ¶1
(create, edit, consult, delete under permission matrices), §3.2 ¶3 (an attribute can leave a group
without being deleted), §3.2.1 (type defined at creation; LOV links); features 003–006. No tier 1
document in `docs/source-of-truth/` covers this feature.

### Functional Requirements

**Navigation and consistency**

- **FR-001 – Place in the navigation**: One side-navigation entry, **"Atributos"**, MUST replace
  "Crear Atributos" in the same position (after "Lista de Valores", before "Grupo de Atributos"). Its
  page lists, consults, edits and deletes attributes, and offers **"Nuevo atributo"**, which opens
  feature 004's creation form on the same page; creating keeps every rule and message of feature 004.
  [Clarification 2026-11-11 (Q1)]
- **FR-002 – Consistency with LOV Management**: The page MUST follow the layout and interaction
  conventions of the "Lista de Valores" page (feature 006): master list with selection on one side and
  the selected item's detail on the other, action buttons in the detail header, inline confirmation
  notifications for deletion, success and error notifications, loading, empty and error states, and
  responsive behavior. It MUST use the existing design system components and Spanish (es-MX) text, and
  MUST NOT introduce a new visual pattern where feature 006 already has one. [Feature request §3]
- **FR-003 – No attribute data in the frontend**: Everything shown (attributes, data types, groups,
  lists) MUST come from the backend. [Feature request §5; feature 004 FR-022]

**Consult and search**

- **FR-004 – List**: The page MUST list every attribute, sorted by display name ignoring letter case,
  showing display name, system ID, data type (Spanish label), the names of its groups, and for LOV
  attributes the name of the linked list. [Feature request §2.A]
- **FR-005 – Search and filter**: The user MUST be able to search by display name or system ID (text
  contained, ignoring letter case and accents) and filter by data type; both combine, and the number of
  matching attributes is shown. [Feature request §2.A]
- **FR-006 – Detail**: Opening an attribute MUST show its complete configuration: system ID, display
  name, description, data type, scope, every type-specific property with Spanish labels (including
  the currency symbol and the reference target), the groups it belongs to, the linked list for LOV
  attributes, its creation date and its last-change date. [Feature request §2.A; feature 004 FR-021]

**Edit**

- **FR-007 – Edit with the creation form**: The user MUST be able to edit an attribute in the form
  used for creation (feature 004), filled with the current values. Every validation of feature 004
  (FR-003, FR-008–FR-016, FR-023, A-003, A-004) MUST apply to the edit exactly as to creation, checked
  by the backend. [Feature request §2.B]
- *Amended 2026-11-20 (feature 004 FR-003 amendment): attributes have no description any more; the
  detail (FR-006) no longer shows one and editing (FR-008) no longer changes one. The mentions of a
  description in User Stories 1–2, FR-006 and FR-008 no longer apply.*
- **FR-008 – What can change**: The display name, the description and the type-specific properties
  MAY change. The display name MUST stay unique ignoring letter case and surrounding spaces among the
  *other* attributes (feature 004 FR-023); keeping the own name, or changing only its letter case, is
  allowed. [Feature request §2.B; feature 004 FR-023]
- **FR-009 – LOV attributes**: The selection mode MAY change, and the attribute MAY be linked to
  another **existing** list of values; an unknown list MUST be refused (feature 004 FR-012). Feature
  006's list detail MUST reflect the change. Unlinking an attribute from every list (and choosing
  another data type) remains feature 006 FU-2. [Feature 004 FR-012; feature 006 FR-014, FR-015]
- **FR-010 – What never changes**: The **system ID** MUST NOT change (feature 003; ERF §3.1 «el ID de
  sistema no podrá ser modificado bajo ninguna circunstancia»). The **data type** MUST NOT change in
  this feature (A-002, OQ-001). The **scope** stays global (feature 004 FR-018). The creation date
  stays. A request that names another system ID or another data type MUST be refused with a Spanish
  message and nothing MAY change; scope and creation date cannot be sent at all. [ERF §3.1; §3.2.1 «Al momento de la creación … se debe definir el tipo de dato»]
- **FR-011 – Memberships kept**: Editing an attribute MUST NOT change its group memberships or any
  other object; the groups and lists that show the attribute show its new name. [Feature 005 FR-010]

**Delete**

- **FR-012 – Confirmation**: Deleting MUST require an explicit confirmation that names the attribute
  and shows its dependencies: the number and names of its groups and, for LOV attributes, the linked
  list and that it will be kept. Cancelling MUST change nothing. [Feature request §2.C]
- **FR-013 – What deletion does**: Deleting an attribute removes its definition. It MUST NOT delete or
  change any list of values, any value of a list, any group, or any product data. [Feature request §2.C
  «Do not silently delete associated product data»]
- **FR-014 – Attributes in groups**: Deleting an attribute that belongs to one or more groups MUST be
  refused with a Spanish message naming the groups and saying it must be removed from them first;
  nothing MAY change. The confirmation (FR-012) already warns about the groups, so the user can remove
  the memberships on "Grupo de Atributos" before confirming. [Clarification 2026-11-11 (Q2); ERF §3.2
  ¶3 «Un atributo debe poder desvincularse del grupo, sin ser eliminado del sistema»; feature 005
  FR-005, feature 006 FR-004]
- **FR-015 – Future dependencies**: Product values and templates do not exist yet (DEP-03, DEP-04).
  The features that introduce them MUST extend the deletion rules (count of affected records, refusal
  or migration) before attributes they use can be deleted; this feature checks only the dependencies
  that exist (groups, lists of values). [Feature request §2.C; ERF §3.2.1 deletion notice for values]
- **FR-016 – System ID after deletion**: The system ID of a deleted attribute MUST stay registered in
  feature 003 and MUST NOT be given to any other object, generated or manual. [ERF §3.1 «inédita»;
  feature 003 OQ-003 "never reuse"]

**Consistency and permissions**

- **FR-017 – No interleaving**: Edits, deletions, group membership changes (feature 005) and list
  deletions (feature 006) MUST NOT interleave in a way that leaves a group pointing at a deleted
  attribute or an attribute linked to a deleted list. [Feature request §2.B «Prevent changes that could
  result in inconsistent or invalid data»]
- **FR-018 – Permissions unchanged**: This feature MUST NOT add roles, permission matrices or new
  permission codes, and MUST NOT change or remove the existing authorization behavior (creation keeps
  requiring the development permission `ATTRIBUTE_CREATE`; consulting stays open). Editing and deleting
  MUST use the same existing `ATTRIBUTE_CREATE` check: without it the edit and delete controls are not
  shown, and the backend refuses the change with a Spanish message. [Clarification
  2026-11-11 (Q3)]
- **FR-019 – Existing features unaffected**: Attribute creation (feature 004), attribute groups
  (feature 005) and LOV Management (feature 006) MUST keep their behavior, responses and messages;
  existing stored attributes MUST remain readable without migration. [Feature request §5, §8]

### Observed Existing Behavior

- **OEB-001**: The attribute API already returns all attributes (`GET`, sorted by display name) and
  one attribute by system ID, case-insensitive; no page uses the list except feature 005's member
  picker. [backend `attribute/controller/AttributeController.java`]
- **OEB-002**: A group's detail looks up each member attribute; a group pointing at a deleted
  attribute would fail to display, which is why FR-014 and FR-017 matter. [backend
  `attributegroup/service/AttributeGroupService.java`, `details`]
- **OEB-003**: Feature 003 declares no key field for attributes (feature 004 FR-006, DEP-08), so
  renaming an attribute does not affect the identifier registry.

### Conflicts Found in Related Documents

- **CF-001 – Type change after creation** (feature 004 CF-002): §3.2.1 defines the type at creation;
  §3.2.1 LOV unlinking and HU2.1 make the user redefine the type when a list is unlinked. This feature
  keeps the type fixed (FR-010) and leaves the unlink case to feature 006 FU-2 (OQ-001).
- **CF-002 – Who may modify**: §3.2 ¶1 and HU2 name "Administrador de Catálogo" / "Manager"; HU5
  names `PIM_Catalog_Admin` or `PIM_Template_Manager` for modifications, «con Audit Trail activo»
  [p360:…/reglas-erf/04-hu-plantillas.md RE-HPL-003, RE-HPL-019]. Roles are out of scope (FR-018);
  the audit trail is OQ-004.

### Dependencies

| ID | Depends on | Status | Effect |
|---|---|---|---|
| DEP-01 | **Feature 004 – Attribute creation** | Implemented, on `main` | Form, validations, data model and API extended; its page becomes the "Nuevo atributo" form of "Atributos" (FR-001); creation rules and messages unchanged (FR-019) |
| DEP-02 | **Feature 005 – Attribute groups** | Implemented, on `main` | Group names in list and detail; deletion rule FR-014; consistency FR-017 |
| DEP-03 | **Product attribute values** (future; feature 006 FU-1) | Not available | Nothing to count now; required before attributes with values can be deleted or tightened (FR-015, OQ-003) |
| DEP-04 | **Templates** (future; feature 004 DEP-05) | Not available | Nothing to check now (FR-015) |
| DEP-05 | **Feature 006 – Lists of values** | Implemented, on `main` | List names in list and detail; relinking (FR-009); list deletion stays consistent (FR-017); UI reference (FR-002) |
| DEP-06 | **Feature 003 – Identifier management** | Implemented, on `main` | ID immutable and never reused (FR-010, FR-016); no change to feature 003 |
| DEP-07 | **Permissions / Data Stewardship** (future) | Out of scope | FR-018 |

### Key Entities

- **Attribute definition** (feature 004): unchanged fields, plus the **date of its last change**
  (absent for attributes never edited).
- **Attribute group membership** (feature 005): read to show groups; affected by deletion only as
  FR-014 decides.
- **List of values link** (feature 004/006): the list ID held by an LOV attribute; may point at another
  existing list after an edit.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user finds a given attribute among 200 by name or ID in under 10 seconds.
- **SC-002**: 100% of saved edits keep the attribute's system ID, data type, scope, creation date and
  group memberships.
- **SC-003**: 100% of edits that break a feature 004 validation or the display name uniqueness are
  refused, with nothing changed.
- **SC-004**: 100% of deletions are preceded by a confirmation that names the attribute and its
  dependencies; cancelling changes nothing.
- **SC-005a**: 100% of attempts to delete an attribute that belongs to a group are refused, naming the
  groups, with nothing changed.
- **SC-005**: After any sequence of edits and deletions, no group refers to a missing attribute and no
  attribute refers to a missing list of values.
- **SC-006**: No deleted attribute's system ID is ever assigned again.
- **SC-007**: In a walkthrough, at least 4 of 5 users who know the "Lista de Valores" page find, edit
  and delete an attribute without help.
- **SC-008**: All existing tests of features 004, 005 and 006 still pass unchanged, except where a test
  fixture must learn about the new page.

## Assumptions

- **A-001**: Consulting needs no permission, as today (`GET` endpoints are open); creating, editing and
  deleting need the existing `ATTRIBUTE_CREATE` development check (FR-018).
- **A-002**: The data type stays fixed after creation (§3.2.1; feature 004 CF-002). Changing it comes
  with feature 006 FU-2 or a later feature (OQ-001).
- **A-003**: While no product holds values (DEP-03), any type-specific property may be changed within
  feature 004's validations, including tightening rules and changing an LOV attribute's selection
  mode.
- **A-004**: Concurrent edits of one attribute: the last save wins, as for groups (feature 005) and
  lists (feature 006); no edit locking or version check.
- **A-005**: No audit history of attribute changes (no project-wide audit requirement exists; OQ-004).
- **A-006**: The list shows a first page of rows with "Mostrar más", like feature 006's values table;
  search and filter run on the attributes already loaded.
- **A-007**: Feature 003's attribute key-field declaration stays deferred (feature 004 FR-006): it is
  not needed for editing because duplicates are refused by display name first.

## Out of Scope

- Roles, permission matrices, Data Stewardship (feature request §4).
- Changing the data type; unlinking an LOV attribute from every list (feature 006 FU-2); migrating
  products between attributes (feature 006 FU-3).
- Product values: counting, migrating or deleting them (DEP-03); templates and local scope (DEP-04).
- Bulk edit or bulk delete, copying or deactivating attributes, audit history.
- Linking attributes to groups from the attribute form (feature 005 OQ-009).

## Open Questions

- **OQ-001**: Can the **data type** ever change after creation, other than when a list is unlinked
  (feature 004 OQ-004b, CF-001)? Not offered here (A-002).
- **OQ-002**: Must deletion ever be **soft** (deactivate and keep for history) rather than removal,
  once products hold values? (feature 006 CF-006 raises the same idea for values)
- **OQ-003**: Once product values exist, which **edits** must be refused or need migration (tightening
  rules, multiple → single selection, relinking to another list)? (A-003)
- **OQ-004**: Is an **audit trail** of attribute changes required («con Audit Trail activo», HU5)?
  (A-005, CF-002)

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-11) | Request | Scope, UI reference, permissions out of scope, tests |
| `p360:…/reglas-erf/01-erf-1-1-plantillas-identificadores-y-atributos.md` RE-PLT-003, RE-PLT-005, RE-PLT-007 (ERF 1.1 §3.1, §3.2 ¶1, ¶3) | 2 (analysis of the ERF) | Immutable ID (FR-010); the four operations under permissions (out of scope); leaving a group without deletion |
| `p360:…/reglas-erf/04-hu-plantillas.md` RE-HPL-003, RE-HPL-004, RE-HPL-019 | 2 (analysis of the HU) | CF-001, CF-002 |
| `specs/003-identifier-management/spec.md` | Project | ID never changes; never reused (FR-016) |
| `specs/004-attribute-creation/` and `backend/.../attribute/`, `frontend/.../AttributeCreationPage.tsx` | Project | Existing creation, validations, API (OEB-001, OEB-003) |
| `specs/005-attribute-groups/` and `backend/.../attributegroup/` | Project | Membership by system ID (OEB-002) |
| `specs/006-lov-management/` and `frontend/.../ListsOfValuesPage.tsx` | Project | UI reference; FU-1, FU-2, FU-3 |
| `.specify/memory/constitution.md` v2.4.0 | Governance | Principles I, III, VII, VIII, IX, XI, XII |
