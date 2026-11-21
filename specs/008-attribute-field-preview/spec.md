# Feature Specification: Live Attribute Field Preview (Vista previa del campo de atributo)

**Feature Branch**: `008-attribute-field-preview` (spec directory; no git branch, work stays uncommitted
on `main` until the project owner asks)

**Created**: 2026-11-12

**Status**: Approved

**Approved**: 2026-11-12, by the project owner (decisions 1–5 with the feature request; plan decisions
D-1 to D-8)

**Amended**: 2026-11-12, at the project owner's request: the preview is also shown when consulting an
existing attribute (User Story 4, FR-016 to FR-018). Amendment approved by the project owner on
2026-11-12 together with plan decision D-9.

**Input**: User request of 2026-11-12 (referred to below as *the feature request*): while creating or
editing an attribute, show a live preview of how the attribute's field will look in a product form. It
is a configuration preview only: it creates no product, saves no value and adds no product-management
capability. The owner approved five decisions with the request: the field is always shown as
optional; the panel sits at the bottom of the attribute form, above the action buttons; frontend
only; at most the first 50 list values; preview values are never kept.

## Context

Features 004 and 007 let a user create and edit attribute definitions in one form (the "Nuevo
atributo" and "Editar atributo" form on the "Atributos" page). A user configures properties such as
lengths, masks, ranges, decimals, list of values and date format, but cannot see what a product
field with those settings will look like until product forms exist. This feature adds a read-along
preview inside that form.

### What exists and what this feature adds

| Capability | Status | Where |
|---|---|---|
| One attribute form for creating and editing, with every property of the 7 data types | **Exists** | Feature 004 (form), feature 007 (edit mode, "Atributos" page) |
| Reading a list of values with its values | **Exists** | Feature 006 |
| Product forms that capture attribute values | **Does not exist** | Out of scope (feature 004 "attribute value" is a future capability) |
| A "required" setting on attributes | **Does not exist**, by design | Mandatory fields per template, ERF 1.1 §3.2.5, future Template Management (decision 1) |
| A live preview of the field inside the attribute form | **New** (implemented 2026-11-12) | This feature, User Stories 1–3 |
| The same preview in the attribute detail (consultation) | **New** (amendment) | This feature, User Story 4 |

## Clarifications

### Session 2026-11-12 (decisions approved with the feature request)

- Q: How should the preview show the required/optional state, given attributes have no required
  setting? → A: **Always "Opcional"**; no required property is added to attribute management;
  required fields belong to Template Management (decision 1). FR-004 and A-001.
- Q: Where is the preview shown? → A: **At the bottom of the attribute form, above the action
  buttons** (decision 2). FR-001.
- Q: Which layers change? → A: **Frontend only**, reusing existing components; no backend, database,
  design-system or LOV Management change (decision 3). FR-014.
- Q: How many list values does the preview show? → A: **Up to the first 50**, saying when more exist
  (decision 4). FR-008.
- Q: What happens to values typed into the preview? → A: **Never kept**; they reset when the
  configuration changes or the form closes, without touching the configuration fields (decision 5).
  FR-011, FR-012.

### Session 2026-11-12 (amendment requested by the project owner)

- Q: Is the preview also shown when consulting an attribute? → A: **Yes.** The attribute detail shows
  the same preview, built from the saved configuration, after the configuration details; it reuses the
  same component and rules, with no second implementation (User Story 4, FR-016 to FR-018).
- Q: Can the consultation preview be used? → A: **Yes, as a demonstration only**; values can be typed
  or chosen but are never saved, and there is no save action for them (FR-012, FR-017).

## User Scenarios & Testing *(mandatory)*

The user is anyone who can open the attribute form today (feature 007 FR-018: the form appears only
with the existing development permission `ATTRIBUTE_CREATE`). No new permission.

### User Story 1 - See the field while configuring a text, numeric, boolean, date or currency attribute (Priority: P1)

While creating or editing an attribute, the user sees below the configuration a panel "Vista previa
del campo" that shows the field as a product form would show it, and it changes as soon as they change
the configuration.

**Why this priority**: These five types cover most attributes and need nothing outside the form; this
is the core of the feature.

**Independent Test**: In "Nuevo atributo", type the name "Peso", choose "Numérico", decimal with 2
decimals, minimum 0 and maximum 100: the preview shows a number field labelled "Peso", marked
"Opcional", accepting 2 decimals and warning when 150 is typed. Change the maximum to 200: the typed
value is cleared and 150 is accepted.

**Acceptance Scenarios**:

1. **Given** the attribute form, **When** it opens, **Then** a panel "Vista previa del campo" appears
   below the configuration fields and above the action buttons, visually distinct from them, with a
   note that it is only an example and saves nothing.
2. **Given** a name and a data type, **When** the user changes any configuration field, **Then** the
   preview shows the change at once, without saving.
3. **Given** type "Texto" with minimum and maximum length, a case format and a capture mask, **When**
   the user types in the preview, **Then** it does not accept more characters than the maximum, shows
   the length limits, applies uppercase or lowercase as configured, and says when the text does not
   match the capture mask or is shorter than the minimum.
4. **Given** type "Texto" marked as rich text, **When** the preview is shown, **Then** it shows a
   multi-line field and says the field accepts rich text (HTML).
5. **Given** type "Numérico", **When** it is integer, **Then** the preview accepts whole numbers only;
   **When** it is decimal, **Then** it accepts the configured number of decimals; **and** it says when
   a value is outside the configured minimum and maximum.
6. **Given** type "Booleano", **When** the preview is shown, **Then** it shows a checkbox labelled
   with the attribute name.
7. **Given** type "Fecha y hora" with a format (e.g. `DD/MM/AAAA HH:mm`), **When** the preview is
   shown, **Then** the field shows the format as its example and says when the typed text does not
   follow it or is not a real date.
8. **Given** type "Moneda" with a currency code and decimals, **When** the preview is shown, **Then**
   the field shows the currency symbol and code and accepts the configured decimals.
9. **Given** the form in edit mode for an existing attribute, **When** it opens, **Then** the preview
   shows that attribute's field from its saved configuration.

---

### User Story 2 - See a list-of-values field with its real values (Priority: P2)

For a list-of-values attribute, the preview shows the configured list's values in a single-selection
or multiple-selection field.

**Why this priority**: Lists are the attribute type where seeing the result helps most, but it needs a
read of the list and more states (loading, empty, too many values).

**Independent Test**: Create an LOV attribute, single selection, linked to list "Color" with values
Azul and Rojo: the preview shows a drop-down with Azul and Rojo. Switch to multiple selection: it shows
two checkboxes. Choose a list with 80 values: 50 are shown and the preview says 30 more exist.

**Acceptance Scenarios**:

1. **Given** an LOV attribute with single selection and a list, **When** the preview is shown,
   **Then** it shows a drop-down with the list's values (labels) in their list order.
2. **Given** multiple selection, **When** the preview is shown, **Then** it shows one checkbox per
   value, and several can be checked.
3. **Given** a list with more than 50 values, **When** the preview is shown, **Then** it shows the
   first 50 and says how many values the list has in total.
4. **Given** no list chosen yet (or no lists exist), **When** the preview is shown, **Then** it says
   that the preview needs a list of values associated with the attribute.
5. **Given** a chosen list with no values, **When** the preview is shown, **Then** it says the list has
   no values yet.
6. **Given** the list's values cannot be loaded, **When** the preview is shown, **Then** it says so in
   Spanish; the configuration form keeps working and can still be saved.
7. **Given** no selection mode chosen yet, **When** the preview is shown, **Then** it uses single
   selection and says the selection mode is not chosen yet.

---

### User Story 3 - See a reference field and understandable messages for incomplete settings (Priority: P3)

For a reference attribute, the preview shows a selector for the configured kind of object; for any
type, incomplete or invalid settings produce a message in the preview instead of an error.

**Why this priority**: Completes the coverage of the seven types; references have no objects to pick
yet, so the preview can only show the selector's look.

**Independent Test**: Create a reference attribute targeting products with multiple cardinality: the
preview shows a disabled selector "Selecciona uno o varios: Producto". Type an invalid capture mask on a
text attribute: the preview says the mask is not valid and ignores it; the form still shows its own
validation when saving.

**Acceptance Scenarios**:

1. **Given** type "Referencia" with a target and a cardinality, **When** the preview is shown,
   **Then** it shows a disabled selector naming the kind of object and whether one or several can be
   chosen, and says that objects can be chosen once product forms exist.
2. **Given** no name yet, **When** the preview is shown, **Then** the field is labelled "Nombre del
   atributo".
3. **Given** no data type yet, **When** the preview is shown, **Then** it asks the user to choose a
   data type to see the field.
4. **Given** settings that are incomplete or contradictory for the preview (a capture mask that is not
   a valid regular expression, a minimum greater than the maximum, a non-numeric limit, an unknown
   currency code, a decimal number without decimals), **When** the preview is shown, **Then** it
   ignores that setting and says why in plain Spanish, or uses a stated default (FR-010); the form
   keeps its own validation unchanged.

---

### User Story 4 - See the field when consulting an attribute (Priority: P2)

When the user opens an existing attribute on the "Atributos" page, the detail shows, after the
configuration details, the same "Vista previa del campo" panel built from the attribute's saved
configuration.

**Why this priority**: It lets anyone who consults an attribute understand its effect without opening
the editor (consultation needs no permission, feature 007 A-001); it reuses User Stories 1–3.

**Independent Test**: Open the saved attribute "Peso" (decimal, 2 decimals, 0–100): below its details
the preview shows a number field "Peso (opcional)" with step 0.01 that warns at 150. Open the saved LOV
attribute "Color" (multiple selection, list with 80 values): the preview shows 50 checkboxes and says the
list has 80 values. Open another attribute: the typed value is gone.

**Acceptance Scenarios**:

1. **Given** an attribute opened in the detail, **When** the detail is shown, **Then** a "Vista previa
   del campo" panel appears after the configuration details, with the same look, note and texts as in
   the form, visually distinct from the details.
2. **Given** the detail of a saved attribute of any of the 7 types, **When** the preview is shown,
   **Then** it shows the field that User Stories 1–3 would show for the same configuration, including
   the configuration notes of FR-010 for saved settings the preview cannot use.
3. **Given** a saved list-of-values attribute, **When** the preview is shown, **Then** the list's values
   are loaded with the existing list API and shown with the attribute's selection mode, at most 50 with
   the total when there are more; a list that cannot be loaded gives the FR-008 message and the detail
   stays usable.
4. **Given** the preview in the detail, **When** the user types or chooses values, **Then** nothing is
   saved and no action to save them exists; opening another attribute, editing, deleting or leaving the
   page discards them.
5. **Given** a user without the development permission `ATTRIBUTE_CREATE`, **When** they consult an
   attribute, **Then** they see the preview too (consultation stays open, feature 007 A-001).
6. **Given** the attribute is edited and saved, **When** the detail returns, **Then** the preview shows
   the new saved configuration.

---

### Edge Cases

- **Name changes**: the label follows the name as the user types; a typed preview value is cleared,
  like any configuration change (FR-011).
- **Description**: shown under the preview field as its help text, as product forms usually show
  help; absent when empty.
- **"Capitalización mixta"** case format: no transformation is applied (A-003, OQ-001).
- **Very long names or masks**: the panel wraps text and never scrolls horizontally.
- **Switching data type**: the preview switches field kind immediately; the previous type's typed
  value is gone.
- **Changing list while its values are loading**: only the last chosen list's values are shown.
- **Saving fails or succeeds**: the preview never takes part in saving; after creating, the form resets
  as today (feature 004) and the preview resets with it.
- **Cancelling or closing the form**: the preview and its typed values disappear (FR-012).
- **Narrow screens**: the preview stays below the configuration, full width.
- **Consultation of an attribute whose list was renamed**: the preview shows the list's current name
  and values, read when the detail opens.
- **Detail opened while the list read is pending, then another attribute opened**: only the last
  opened attribute's list is shown (as in the form, FR-008).

## Requirements *(mandatory)*

Sources: the feature request and its approved decisions; features 004, 006 and 007 for the attribute
properties and their options. No ERF section asks for a preview; the feature request is the source
(constitution XI: request-level requirement). No tier 1 document covers this feature.

### Functional Requirements

**Panel**

- **FR-001 – Placement**: The attribute form (create and edit) MUST show a panel "Vista previa del
  campo" below the configuration fields and above the action buttons, visually distinct from the
  configuration (its own background and heading), with a note that it is only an example and saves
  nothing. [Decision 2; feature request §UI/UX]
- **FR-002 – Live**: The preview MUST reflect the current, unsaved configuration and update on every
  change, without saving. [Feature request req. 2]
- **FR-003 – Three scenarios**: The preview MUST work the same for a new attribute (creation), for an
  existing attribute being edited (live, before saving) and for an existing attribute being consulted
  (from its saved configuration, FR-016). [Feature request req. 9; amendment 2026-11-12]
- **FR-004 – Label and state**: The field MUST be labelled with the attribute name ("Nombre del
  atributo" while empty) and marked "Opcional"; the attribute description, when present, MUST be shown
  as the field's help text. [Decision 1; feature request req. 4] *Amended 2026-11-20 (feature 004
  FR-003 amendment): attributes have no description any more, so the help text holds only the rules
  (length, range, format, …).*

**Field per data type** (feature request req. 3 and 4; properties from feature 004 FR-008–FR-015)

- **FR-005 – Texto**: A single-line text field, or a multi-line field when rich text is on (with a
  note that it accepts rich text). The maximum length MUST limit typing; minimum and maximum lengths
  MUST be shown; "Mayúsculas" and "Minúsculas" MUST transform the typed text; the capture mask, when it
  is a valid regular expression, MUST be checked against the typed text with a message when it does not
  match; a text shorter than the minimum MUST get a message.
- **FR-006 – Numérico**: A number field. Integer: whole numbers only. Decimal: the configured number of
  decimals. Minimum and maximum MUST be shown and a value outside them MUST get a message.
- **FR-007 – Booleano**: A checkbox labelled with the attribute name.
- **FR-008 – Lista de valores**: The values (labels) of the configured list, in list order: a drop-down
  for single selection, one checkbox per value for multiple selection. At most the first 50 values are
  shown, with a statement of the total when the list has more. Without a list: a message that the
  preview needs a list of values; list without values: a message that it has no values yet; values
  that cannot be loaded: a message, with the form unaffected; no selection mode yet: single selection
  with a note. [Decision 4; feature request req. 5]
- **FR-009 – Fecha y hora / Moneda / Referencia**:
  - *Fecha y hora*: a text field whose example is the configured format; text that does not follow
    the format or is not a real date or time MUST get a message.
  - *Moneda*: a number field showing the currency symbol and code, with the configured decimals
    (2 while not set, feature 004 D-10 default).
  - *Referencia*: a disabled selector naming the target kind of object and whether one or several can
    be chosen (one while not set, feature 004 D-11 default), with a note that objects can be chosen
    when product forms exist.

**Defaults and messages**

- **FR-010 – Incomplete or invalid settings**: A setting the preview cannot use MUST be ignored or
  replaced by a stated default, with a plain Spanish message in the preview: no data type; capture mask
  that is not a valid regular expression; minimum greater than maximum (length or value); non-numeric
  limit; decimal number without decimals (2 assumed for the preview); unknown currency code (code shown
  without symbol). The preview MUST NOT block, change or replace the form's own validation and
  messages. [Feature request req. 6; owner expectation "understandable preview messages"]

**Temporary values**

- **FR-011 – Reset on change**: Values typed or chosen in the preview MUST be cleared whenever any
  configuration field changes; the configuration fields themselves MUST NOT change. [Decision 5]
- **FR-012 – Never kept**: Preview values MUST NOT be sent to the backend, saved, or kept after the form
  closes or is cancelled. [Decision 5; feature request req. 8]

**Boundaries**

- **FR-013 – Existing behavior unchanged**: Creating and editing attributes MUST keep every rule,
  request, response, message and test of features 004 and 007; the preview adds no field to the
  attribute and no business rule. [Feature request restrictions; owner expectations]
- **FR-014 – Frontend only**: The feature MUST NOT change the backend, the database, the design
  system components or LOV Management; the only request it makes is reading the chosen list's values
  through the existing list API. Everything shown comes from the form or the backend, never from data
  written into the frontend (feature 004 FR-022). [Decision 3]
- **FR-015 – Language and look**: Spanish (es-MX) text, existing design-system components and styles,
  no horizontal scrolling at the supported widths. [Feature request §UI/UX; feature 001]

**Consultation** (amendment 2026-11-12)

- **FR-016 – Preview in the detail**: The attribute detail on the "Atributos" page MUST show the "Vista
  previa del campo" panel after the configuration details, built from the attribute's saved
  configuration as returned by the backend, for every user who can consult attributes. [Amendment]
- **FR-017 – Same behavior, demonstration only**: The consultation preview MUST use the same panel and
  rules as the form preview (FR-004 to FR-012): same field per type, same notes, same list behavior
  (first 50 values, total, messages), values temporary and never saved. It MUST NOT offer any action to
  save values. Its values MUST be discarded when another attribute is opened, the detail changes to
  edit, the attribute is deleted, or the user leaves the page. [Amendment]
- **FR-018 – One implementation**: The consultation preview MUST reuse the form's preview component
  and rules; there MUST NOT be a second implementation of the per-type rendering. [Amendment §Technical
  considerations]

### Key Entities

- **Attribute configuration** (existing, feature 004/007): the form state the preview reads during
  creation and editing, or the saved attribute during consultation; never changed by the preview.
- **Preview value** (new, temporary): what the user types or chooses in the preview; lives only while
  the configuration stays the same and the form is open.
- **List values** (existing, feature 006): read for LOV attributes; never changed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For each of the 7 data types, the preview shows the field kind of FR-005–FR-009 within the
  same screen update as the configuration change (no save, no waiting except for list values).
- **SC-002**: 100% of the properties listed in FR-005–FR-009 that the user sets are visible or
  enforced in the preview.
- **SC-003**: 0 preview values reach the backend or survive a configuration change or closing the form.
- **SC-004**: Every incomplete or invalid setting listed in FR-010 produces a preview message, and the
  form stays usable and savable as before.
- **SC-005**: All existing tests of features 004, 006 and 007 pass without changing their assertions.
- **SC-007**: For 100% of saved attributes, the consultation preview shows the same field as the form
  preview shows when the same attribute is opened for editing.
- **SC-006**: In a walkthrough, at least 4 of 5 users correctly predict from the preview how a product
  field will behave (limit, decimals, list values) without reading the configuration again.

## Assumptions

- **A-001**: Attributes stay without a required setting; the preview always says "Opcional"
  (decision 1).
- **A-002**: The date preview uses a text field showing the configured format, because browser date
  pickers ignore the configured format; real date pickers are a product-form decision.
- **A-003**: "Capitalización mixta" (feature 004) is not defined further in the sources; the preview
  applies no transformation for it (OQ-001).
- **A-004**: The currency symbol is derived in the browser from the ISO code with the platform's
  standard currency formatting (es-MX); no symbol table is written into the frontend.
- **A-005**: Reference objects cannot be chosen yet (no target object lists exist), so the selector is
  disabled.
- **A-006**: The rich text note does not imply an editor exists; product forms will decide the editor.

## Out of Scope

- Product forms, product records and attribute values; saving or sending preview values.
- A required property on attributes; template-level mandatory rules (§3.2.5).
- Backend, database, design-system or LOV Management changes; new permissions.
- Rich text editors, real date pickers, reference object search.

## Open Questions

- **OQ-001**: What exactly does "Capitalización mixta" force (title case, sentence case, nothing)?
  Feature 004 only names it. The preview applies nothing until it is defined (A-003).

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request and approved decisions (user, 2026-11-12) | Request | Scope, decisions 1–5, expectations |
| `specs/004-attribute-creation/` (spec, research, data-model) | Project | Properties, options, defaults (D-6, D-8–D-11), FR-022 |
| `specs/006-lov-management/` | Project | List values, order, API |
| `specs/007-attribute-management/` | Project | Shared create/edit form, permission FR-018 |
| `frontend/src/attributes/AttributeEditor.tsx`, `components/ui/*` | Project | Reusable components and form state |
| `.specify/memory/constitution.md` v2.4.0 | Governance | Principles I, III, V, VI, VIII, XI |
