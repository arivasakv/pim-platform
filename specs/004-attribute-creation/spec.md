# Feature Specification: Attribute Management – Attribute Creation (Administración de Atributos – Creación de Atributos)

**Feature Branch**: `004-attribute-creation`

**Created**: 2026-11-09

**Status**: Approved

**Approved**: 2026-11-09, by the project owner, together with the plan's decisions D-1 to D-12
(plan.md); FR-006 amended the same day (decision D-4, option A; see Clarifications)

**Amended**: 2026-11-09, storage moved from memory to MongoDB (constitution 2.3.0, Principle IX;
ADR 0003), requested and approved by the project owner while planning feature 005; attribute definitions are persisted; no
change to behaviour, rules, API or messages (see Clarifications)

**Input**: User request of 2026-11-09 (referred to below as *the feature request*): create a new,
independent feature that lets an authorized user create product attribute definitions: define an
attribute, select its data type, configure its applicable properties and save it. Requirements
source: `ERF 1.1 Plantillas.docx` §3.2 Administración de atributos and §3.2.1 Tipos de Atributos.
New attributes must use the system identifier rules of feature 003. Attribute editing and
deletion, LOV administration, business rules, SAP, mass import, product value capture, template
and taxonomy administration are out of scope.

## Context

Section 3.2 asks for "un motor de atributos altamente flexible, gobernable y estructurado" [Source:
p360:ERF - HUs/ERF 1.1 Plantillas.docx §3.2, introduction]. Section 3.2.1 says that the data type
of an attribute is defined when it is created («Al momento de la creación (manual o masiva), se debe
definir el tipo de dato subyacente») and lists seven types [§3.2.1, introduction].

This feature delivers **only the creation of attribute definitions**, one at a time, by an
authorized user. An attribute definition describes a characteristic that products can have later
(for example "Color" or "Peso"), its data type and the rules its values must follow. Entering
values into products is a different, future capability.

The feature builds on feature 003 (Identifier Management): every new attribute is an object of
type "attribute" and receives its system ID under feature 003's rules. It does not create a second
identifier mechanism.

### Attribute definition versus attribute value

- **Attribute definition** (this feature): display name, data type, type-specific properties
  and scope of one attribute.
- **Attribute value** (not this feature): the value a given product has for that attribute (for
  example, "Azul" for "Color").

## Clarifications

### Session 2026-11-09

- **Amendment (after approval)**: Q: Where are attribute definitions kept? → A: **In MongoDB**
  (collection `attributes`, unique index on the display name ignoring case), instead of memory, so they
  survive restarts together with their system IDs (feature 003) and attribute groups (feature 005).
  Constitution 2.3.0 allows it; ADR 0003 records the design. Decision D-1 is superseded and DEP-07 is
  resolved; every rule, response and message stays the same. Requested and approved by the project
  owner on 2026-11-09 (constitution X).

- Q: Which fields must a user fill in to create an attribute, besides the system ID that feature 003
  assigns? → A: **Display name and data type are required; a description is optional** (OQ-001,
  Option A). FR-003, User Story 1, the edge cases and Key Entities were updated.
- Q: Must an attribute's display name be unique, and if so, in what scope? → A: **Unique across all
  attributes, ignoring letter case and surrounding spaces; the display name is the attribute's key
  field for feature 003** (OQ-002, Option A). FR-006, new FR-023, User Story 1, the edge cases,
  Success Criteria and OQ-012 were updated.
- Q: No feature creates attribute groups yet. How should this feature handle linking a new
  attribute to groups? → A: **Deferred: attributes are created without groups; linking to groups
  arrives with a future attribute group feature** (OQ-005, Option A). FR-017, User Story 5, the edge
  cases, Key Entities, Dependencies and Out of Scope were updated.
- Q: No feature creates templates yet. How should this feature handle global versus local
  (template-specific) scope? → A: **Global only: every attribute created here is global; local
  scope and inheritance arrive with a future template feature** (OQ-006, OQ-007, Option A).
  FR-018 to FR-020, User Story 5, the edge cases, Key Entities, Dependencies and Out of Scope were
  updated.
- Q: No feature creates lists of values yet. How should a list-of-values attribute be created? →
  A: **An existing list is required; the type is offered, but until the list feature exists saving
  it is refused with a message that no lists exist yet** (OQ-009, Option A; §3.2.1 does not allow
  an LOV attribute without a list). FR-012, User Story 3, the edge cases and DEP-03 were updated.
- **Amendment (plan decision D-4, option A, approved by the project owner)**: Q: Should this feature
  declare the display name as the attribute key field in feature 003, given that doing so changes
  feature 003's defaults and breaks 5 of its tests? → A: **No, not in this feature.** FR-006 now
  defers the declaration; the behaviour is unchanged, because FR-023 refuses a duplicate display
  name before feature 003 is called, so feature 003's "same object" outcome is never reached for
  attributes (plan decision D-5). OQ-012 is resolved the same way. Requested and approved on
  2026-11-09 (constitution X).

## User Scenarios & Testing *(mandatory)*

The user is an **authorized user**: a user holding the permission to create attributes (FR-002).
The source names example profiles inconsistently (CF-001), so no role is fixed here.

### User Story 1 - Create an attribute and choose its data type (Priority: P1)

An authorized user opens the attribute creation page, enters the attribute's identifying
information, chooses one of the seven data types and saves. The attribute is stored with a system
ID and can be found afterwards.

**Why this priority**: Defining the data type at creation is the core of §3.2.1 and the minimum
that makes an attribute usable later. Every other story adds configuration to it.

**Independent Test**: As an authorized user, create a "Boolean" attribute (no type-specific
properties) without giving an ID; it is saved with a generated system ID and its type. As a user
without the permission, the creation is refused.

**Acceptance Scenarios**:

1. **Given** an authorized user, **When** they enter a display name, choose the data type
   "Booleano" and save, **Then** the attribute is saved with that type and a system ID
   generated under feature 003's rules, and a confirmation in Spanish shows the ID.
2. **Given** an authorized user who also holds the manual-ID permission for attributes (feature
   003, FR-003), **When** they give an unused ID and save, **Then** the attribute is saved with
   exactly that ID.
3. **Given** a user without the permission to create attributes, **When** they try to create one,
   **Then** the creation is refused with an explanatory message and nothing is saved.
4. **Given** the user has not chosen a data type or has left the display name empty, **When**
   they try to save, **Then** saving is refused and the page says which field is required.
5. **Given** the user gives an ID that another object already uses, **When** they save, **Then**
   feature 003's duplicate rule applies and the attribute is not created with that ID.
6. **Given** an attribute named "Color" exists, **When** the user creates another attribute named
   " color ", **Then** saving is refused with a message saying the display name is already used.
7. **Given** the attribute creation page, **When** it lists the data types, **Then** all seven types
   of §3.2.1 are offered: Texto, Numérico, Booleano, Lista de valores, Fecha y hora, Moneda,
   Referencia.

---

### User Story 2 - Configure the properties of a text, numeric, date or currency attribute (Priority: P1)

When creating an attribute of type text, numeric, date/time or currency, the authorized user sets
the properties §3.2 and §3.2.1 list for that type. Contradictory settings are refused.

**Why this priority**: §3.2 requires these properties («longitud máxima, rangos numéricos válidos,
máscaras de captura (expresiones regulares)»), and without them the type definition has no
validation rules.

**Independent Test**: Create a text attribute with minimum length 2 and maximum length 50, an
integer attribute with range 0–100, a decimal attribute with 2 decimals, a date attribute with
format DD/MM/AAAA and a currency attribute with a currency; each is saved with exactly those
properties. A text attribute with minimum 10 and maximum 5 is refused.

**Acceptance Scenarios**:

1. **Given** type "Texto", **When** the user sets minimum and maximum length, a format mask
   (uppercase, lowercase or mixed capitalization), a capture mask as a regular expression, or
   marks it as rich text (HTML), **Then** those properties are saved with the attribute.
2. **Given** type "Numérico", **When** the user chooses integer or decimal, sets the precision
   (number of decimals) for a decimal, and a minimum and maximum value, **Then** those properties
   are saved.
3. **Given** type "Fecha y hora", **When** the user chooses a regional date format (e.g.
   DD/MM/AAAA), **Then** it is saved with the attribute.
4. **Given** type "Moneda", **When** the user sets the currency as an ISO code (e.g. MXN) and the
   number of decimals (plan decision D-10), **Then** they are saved and the attribute shows the
   currency symbol derived from the code.
5. **Given** a minimum greater than its maximum (length or value), **When** the user saves,
   **Then** saving is refused with a message naming the property (FR-016).
6. **Given** a capture mask that is not a valid regular expression, **When** the user saves,
   **Then** saving is refused with a message (FR-016).
7. **Given** properties of another type (for example text properties sent with a Boolean
   attribute), **When** the attribute is saved, **Then** they are ignored and not stored (A-004).
8. **Given** a precision for an integer number, **When** the user saves, **Then** saving is refused
   (precision applies only to decimals, plan decision D-8).

---

### User Story 3 - Create a list-of-values attribute (Priority: P2)

The authorized user creates an attribute of type "Lista de valores", chooses single or multiple
selection and links it to a list of values.

**Why this priority**: LOV attributes are a required type in §3.2.1, but they need an existing list
of values, which no feature provides yet (DEP-03). Until it does, this story can only show the
refusal of scenario 2.

**Independent Test**: With no lists available, choosing "Lista de valores" and saving is refused
with the "no lists exist yet" message. Once lists exist (DEP-03), create a multiple-selection LOV
attribute linked to one; it is saved with the selection mode and the link.

**Acceptance Scenarios**:

1. **Given** type "Lista de valores", **When** the user chooses single or multiple selection and a
   list of values, **Then** the attribute is saved with the selection mode and the linked list.
2. **Given** type "Lista de valores" and no list chosen (or no list exists yet), **When** the user
   saves, **Then** saving is refused with a Spanish message saying that a list of values is
   required, or that no lists of values exist yet, and nothing is saved.
3. **Given** a list of values already linked to another attribute, **When** it is chosen for a new
   attribute, **Then** the link is accepted («Una Lista de Valor puede vincularse a más de un
   atributo», §3.2.1).

---

### User Story 4 - Create a reference attribute (Priority: P2)

The authorized user creates an attribute of type "Referencia" that will link a product to another
object of the system (for example a spare part, or a cross-sell or up-sell product).

**Why this priority**: Required type in §3.2.1; its fields were settled by plan decision D-11.

**Independent Test**: Create a reference attribute that targets products, with multiple
cardinality; it is saved with both.

**Acceptance Scenarios**:

1. **Given** type "Referencia", **When** the user chooses the kind of object it references (one of
   the system's object types) and whether one or several objects can be referenced, **Then** the
   attribute is saved with both (cardinality defaults to one).
2. **Given** type "Referencia" and no target chosen, **When** the user saves, **Then** saving is
   refused: the target is required (plan decision D-11).

---

### User Story 5 - Every new attribute is global and ungrouped (Priority: P3)

Every attribute created in this feature has global scope (it applies to the whole system) and no
attribute groups. Local (template-specific) scope, inheritance and group links arrive with future
features (clarifications 2026-11-09; FR-017, FR-018).

**Why this priority**: It records how §3.2 ¶3 and ¶4 apply while templates and groups do not exist,
and it makes the global-structure permission of §3.2 ¶1 apply to every creation.

**Independent Test**: Create an attribute; it is saved as global, without groups. A user who may
not affect the global structure cannot create it.

**Acceptance Scenarios**:

1. **Given** the attribute creation page, **When** the user creates an attribute, **Then** neither a
   group nor a scope can be chosen, and the attribute is saved as global without groups.
2. **Given** a user who may not affect the global structure («Solo perfiles autorizados … podrán
   afectar la estructura global», §3.2), **When** they try to create an attribute, **Then** the
   creation is refused and nothing is saved (FR-019).
3. **Given** a saved attribute, **When** it is read back, **Then** it shows global scope.

---

### Edge Cases

- **Display name already used by another attribute**, even with other letter case or extra
  spaces: refused (FR-023).
- **Display name of only spaces**: treated as empty and refused (FR-003).
- **ID given manually together with the display name of an existing attribute**: refused as a
  duplicate display name (FR-023) before feature 003 is asked, so no "update" happens (FR-006).
- **Data type changed after saving**: not offered by this feature (editing is out of scope);
  whether it is ever allowed is OQ-004b.
- **Text attribute marked as rich text with a capture mask or a case mask**: allowed; the masks are
  stored as given (plan decision D-6).
- **Numeric range with only a minimum or only a maximum**: allowed (plan decision D-8).
- **Decimal precision of 0 or more than 10**: refused; a decimal's precision is 1–10 (plan decision
  D-8).
- **Date "logical validations"** such as "end date not before start date" compare two attributes:
  not configured on the attribute; they are business rules of §3.2.4, out of scope (plan decision
  D-9).
- **LOV attribute when no list of values exists yet**: offered but cannot be saved; the user is
  told no lists exist yet (FR-012).
- **LOV attribute linked to a list that does not exist** (for example an unknown list through the
  API): refused (FR-012).
- **Attribute created with no group**: the normal case in this feature; groups are linked later
  by the attribute group feature (FR-017).
- **Request for local scope** (for example through the API): refused; only global attributes can
  be created in this feature (FR-018).
- **User without the manual-ID permission gives an ID**: feature 003 rejects the request (403);
  the attribute is not created and no ID is generated instead (feature 003 FR-003).
- **Save fails because the system cannot store the attribute**: an error message in Spanish; the
  form keeps the entered data so the user can retry (A-007).

## Requirements *(mandatory)*

### Explicit Requirements

Sources: the feature request and `p360:ERF - HUs/ERF 1.1 Plantillas.docx`, cited as
[3.2 ¶n] (paragraph n of §3.2: 1 = roles, 2 = properties, 3 = groups, 4 = local vs. global) and
[3.2.1 <type>]. No tier 1 document in `docs/source-of-truth/` covers this feature.

**Creation and permissions**

- **FR-001**: The application MUST let an authorized user create a new attribute definition, one at
  a time, through a page of the application that uses the existing design system and talks to the
  backend only through its REST API. [Source: feature request; individual creation: 3.2.1
  introduction «manual o masiva» (mass creation out of scope)]
- **FR-002**: Creating an attribute MUST require a **permission to create attributes**. A user
  without it MUST be refused with an explanatory Spanish message, and nothing MAY be saved. No
  specific role is fixed by this feature. [3.2 ¶1 «La capacidad de crear … atributos estará
  estrictamente controlada por matrices de permisos»; roles: CF-001]
- **FR-003**: Besides its system ID (FR-004), an attribute MUST have a **display name** and a **data
  type** (FR-007); a **description** is optional. An attribute with an empty or blank display name
  MUST NOT be saved. [Clarification 2026-11-09 (OQ-001); the only source hint is the HU2.8 load
  layout «ID, friendlyName»; §3.2.8 «(definir campos mínimos)»]
  *Amended 2026-11-20 by the project owner:* an attribute has **no description**. Its general
  properties are only the display name («Nombre del Atributo») and the data type («Tipo de Dato»), as in
  the owner's requirement of 2026-11-20 («El usuario captura las propiedades generales del atributo:
  Nombre del Atributo (Texto). Tipo de Dato: …»). The description field is removed from the form, the
  detail, the field preview (feature 008) and the API (contract v2.0.0); a `description` sent in a
  request is ignored. Descriptions already stored are no longer read or shown (they stay in the stored
  documents, unused). The owner's requirement text was not found in the reference repository (open
  question: its source document).
- **FR-023**: The display name MUST be **unique across all attributes**, compared ignoring letter
  case and surrounding spaces ("Color" and " color " are the same name). Surrounding spaces are
  removed before saving. Creating an attribute whose display name is already used MUST be refused
  with a Spanish message naming the display name, and nothing MAY be saved. [Clarification
  2026-11-09 (OQ-002)]

**Identifier (feature 003)**

- **FR-004**: Every new attribute MUST receive its system ID through feature 003's identifier
  rules, as an object of type "attribute": generated from the attribute sequence when no ID is
  given, or the ID given by a user holding the manual-ID permission for attributes. This feature
  MUST NOT generate, validate or store system IDs by any other mechanism. [Feature request §3.E;
  feature 003 FR-001, FR-003, FR-005, FR-012]
- **FR-005**: When feature 003 rejects the ID (no manual-ID permission, invalid format, ID used by
  another object), the attribute MUST NOT be created, and the user MUST see feature 003's Spanish
  message. [Feature 003 FR-003, FR-009b, FR-011]
- **FR-006**: The display name is the field that identifies an attribute (clarification 2). When it
  requests an ID from feature 003, this feature MUST send exactly the key-field values feature 003
  declares for attributes: the display name (as stored) under `displayName` if that key field is
  declared, and no values otherwise (feature 003 rejects undeclared key fields, its decision D-8).
  It MUST NOT change feature 003's configuration: declaring
  `displayName` as the attribute key field in feature 003 is deferred until feature 003's test
  fixtures can be updated or attribute editing is specified (DEP-08). Until then feature 003 declares
  no key field for attributes, so it never answers "same object" for them; duplicates are refused
  by FR-023 first. [Feature 003 FR-013, A-001; clarification 2026-11-09 (OQ-002); amendment D-4]

**Data type**

- **FR-007**: The data type MUST be chosen at creation and MUST be one of: **Texto (String)**,
  **Numérico (Integer / Float)**, **Booleano (Sí / No)**, **Lista de valores (LOV)**, **Fecha y hora
  (Date/Time)**, **Moneda (Currency)**, **Referencia (Reference / Relationship)**. An attribute
  without a data type MUST NOT be saved. [3.2.1 introduction and type list]

**Type-specific properties** (set at creation; properties that do not apply to the chosen type are
not stored, A-004)

- **FR-008 – Texto**: minimum length, maximum length, a format mask that forces the case
  (uppercase, lowercase or mixed capitalization), and a rich text (HTML) option for marketing
  descriptions. [3.2.1 Texto]
- **FR-009 – Capture mask**: a text attribute MUST accept a capture mask written as a regular
  expression. [3.2 ¶2 «máscaras de captura (expresiones regulares)»] The case mask of FR-008 and
  this capture mask are two separate properties (plan decision D-6).
- **FR-010 – Numérico**: integer or decimal format; for a decimal, the precision (number of decimals
  allowed); a minimum and a maximum value. [3.2.1 Numéricos; 3.2 ¶2 «rangos numéricos válidos»]
- **FR-011 – Booleano**: no type-specific property; values are yes/no (shown as a checkbox or switch
  when values are captured, which is out of scope). [3.2.1 Booleanos]
- **FR-012 – Lista de valores**: single selection or multiple selection (multivalue), and the
  **existing** list of values it uses, which is **required**. An LOV attribute without a list, or
  with a list that does not exist, MUST NOT be saved; while no list of values exists, the type is
  offered but saving it MUST be refused with a Spanish message saying that no lists of values exist
  yet. This feature MUST NOT create lists of values. A list of values MAY be linked to more than one
  attribute. [3.2.1 Listas de Valores; «en el caso de la desvinculación debe quedar indicado el tipo
  de atributo diferente a LOV»; clarification 2026-11-09 (OQ-009)]
- **FR-013 – Fecha y hora**: a regional date format (e.g. DD/MM/AAAA). [3.2.1 Fecha y Hora]
  The format is one of a fixed list (plan decision D-9); validations between dates are business
  rules, out of scope.
- **FR-014 – Moneda**: a numeric attribute that is associated with a currency symbol ($, €, …) and
  financial formatting rules. [3.2.1 Moneda] The currency is fixed per attribute as an ISO 4217
  code, with 0–4 decimals (default 2); the symbol is derived from the code (plan decision D-10).
- **FR-015 – Referencia**: the kind of system object the attribute links a product to (for example a
  spare part, or a cross-sell or up-sell product). [3.2.1 Referencias o Relaciones] The target is one of feature 003's
  object types, with single (default) or multiple cardinality (plan decision D-11).
- **FR-016**: Settings that contradict each other or are malformed MUST be refused with a Spanish
  message that names the property, and nothing MAY be saved (A-003 lists the checks).
  [Derived from FR-008–FR-015; checks approved as plan decisions D-7 to D-11, A-003]

**Groups and scope**

- **FR-017**: Attributes are created **without attribute groups** in this feature. Linking an
  attribute to one or more groups («Los atributos deben vincularse a uno o varios Grupos de
  Atributos», 3.2 ¶3) is deferred to a future attribute group feature (DEP-04), which must also let
  existing attributes be linked. This feature MUST NOT create, edit, delete or link attribute
  groups. [3.2 ¶3; scope: clarification 2026-11-09 (OQ-005)]
- **FR-018**: Every attribute created in this feature MUST have **global** scope (applies to the
  whole system), and the saved attribute MUST show it. Local scope (specific to a template, 3.2 ¶4)
  is deferred to a future template feature (DEP-05); a request for local scope MUST be refused.
  [3.2 ¶4; scope: clarification 2026-11-09 (OQ-006, OQ-007)]
- **FR-019**: Because every new attribute is global, creating any attribute MUST be allowed only to
  users who may affect the global structure. Whether this is the same permission as FR-002 or a
  separate one: one permission, `ATTRIBUTE_CREATE`,
  covers both while all attributes are global (plan decision D-2; OQ-004a). [3.2 ¶1 «Solo perfiles autorizados (ej. Administrador de Catálogo)
  podrán afectar la estructura global»]
- **FR-020**: Inheritance of configuration to lower hierarchies («Estas configuraciones son
  heredables a las jerarquías inferiores», 3.2 ¶4) is NOT part of this feature: it applies to local
  configurations and template hierarchies, which arrive with future features (DEP-05). [3.2 ¶4;
  clarification 2026-11-09]

**Result**

- **FR-021**: After a successful save, the user MUST see a Spanish confirmation with the attribute's
  system ID, and the saved attribute MUST be retrievable through the API with every property it was
  created with. [Feature request "save it in the system"]
- **FR-022**: The application MUST NOT contain attribute data (types, groups, lists, templates) in
  the frontend; everything it shows comes from the backend. [Feature request §5; constitution III]

### Observed Existing Behavior

From the reference repository's analysis of the ERF (tier 2 analysis of tier 3 evidence); not
requirements, for context only.

- **OEB-001**: In P360 the attribute properties of §3.2 ¶2 (length, ranges, masks) exist as columns
  but are not used. [Evidence (as summarized):
  p360:P360-repositorios/nuevo-pim/rag-as-is-to-be/reglas-erf/01-erf-1-1-plantillas-identificadores-y-atributos.md
  §RE-PLT-006 «En P360 las columnas existen pero no se usan (módulo 01 §2, C-03)»]
- **OEB-002**: The types "Moneda" and "Referencia" do not exist in P360 today. [same file,
  §RE-PLT-009 «Moneda y referencia no existen hoy en P360 (C-04)»]

### Conflicts Found in Related Documents

Reported per constitution XI; not used as requirements and not resolved by assumption.

- **CF-001 – Role names**: §3.2 ¶1 gives "Administrador de Catálogo" as an example profile; the
  Templates HU2 calls the profile "Manager"; the Templates HU5 names the technical roles
  `PIM_Catalog_Admin` (create) and `PIM_Template_Manager`. [p360:…/reglas-erf/04-hu-plantillas.md
  §RE-HPL-003 (lines 35–42), §RE-HPL-019 (lines 181–188); 01-erf-1-1…md §RE-PLT-005] This spec uses
  a permission (FR-002) and fixes no role.
- **CF-002 – Type changes after creation**: §3.2.1 says the type is defined at creation; §3.2.1 (LOV
  unlinking) and HU2.1 say that when a list is unlinked the attribute's type changes and the user
  must choose a new one («el PIM exigirá redefinir el nuevo tipo de dato»). So the type can change
  later in at least one case. [3.2.1 Listas de Valores; 04-hu-plantillas.md §RE-HPL-004] (OQ-004b)
- **CF-003 – Two kinds of mask**: §3.2 ¶2 speaks of capture masks as regular expressions; §3.2.1
  Texto speaks of a mask that forces the case (uppercase, lowercase, mixed). [3.2 ¶2; 3.2.1 Texto]
  Settled provisionally by plan decision D-6 (two properties); still to be confirmed before merge.

### Dependencies

| ID | Depends on | Status | Effect on this feature |
|---|---|---|---|
| DEP-01 | **Feature 003 – Identifier Management** (`com.pim.backend.identifier`, `POST /api/v1/system-ids`, object type `ATTRIBUTE`) | Implemented, merged | System IDs (FR-004–FR-006). Feature 003 keeps its data in memory (decision D-1) and reads permissions from a development header (D-7); attribute creation inherits both limits |
| DEP-02 | **Permissions / users and roles** | Not available (no authentication) | FR-002 and FR-019 need a way to know the user's permissions; feature 003 uses a development header for the same need |
| DEP-03 | **List-of-values administration** (future feature) | Not available | Until it exists, LOV attributes cannot be saved (FR-012, User Story 3 scenario 2); it must offer the lists this feature links to |
| DEP-04 | **Attribute group administration** (future feature) | Not available | Not needed by this feature: group links are deferred to it (FR-017); it must cover linking attributes created here |
| DEP-05 | **Template administration** and template hierarchy (future features) | Not available | Not needed by this feature: local scope and inheritance are deferred to them (FR-018, FR-020) |
| DEP-06 | **Target objects for references** (products exist in feature 002; others do not) | Partial | A reference attribute only names its target object type (D-11); the referenced objects are needed later, when values are captured |
| DEP-07 | **Storage of attribute definitions** | Resolved 2026-11-09 | MongoDB, allowed by constitution 2.3.0 for attribute administration data (ADR 0003) |
| DEP-08 | **Attribute editing** (future feature) | Not available | Needed for type changes (CF-002); the point at which the attribute key field may be declared in feature 003 (FR-006) |

### Key Entities

- **Attribute definition**: system ID (feature 003), display name (required), ~~description
  (optional)~~ (removed 2026-11-20, FR-003), data type, type-specific properties and scope (always global in this feature, FR-018). No group links
  (FR-017).
- **Data type**: one of the seven types of FR-007.
- **Type-specific properties**: text (lengths, case mask, capture mask, rich text), numeric
  (integer/decimal, precision, minimum, maximum), LOV (selection mode, linked list), date/time
  (regional format), currency (symbol and formatting), reference (target kind).
- **List of values** (existing, not managed here): key–value pairs (ID and label) used by LOV
  attributes [3.2.1].

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An authorized user can create an attribute of any of the seven types, with its
  properties, in under 2 minutes from opening the creation page.
- **SC-002**: 100% of created attributes have a system ID assigned under feature 003's rules, and no
  attribute shares an ID with any other object.
- **SC-003**: 100% of creation attempts by users without the permission (FR-002, FR-019) are refused,
  and nothing is saved.
- **SC-004**: 100% of contradictory or malformed settings covered by A-003 are refused with a
  message naming the property.
- **SC-005**: 100% of saved attributes, read back, show exactly the data type and properties they
  were created with.
- **SC-006**: In a walkthrough, at least 4 of 5 authorized users create a text attribute and a
  numeric attribute correctly on their first attempt, without help.
- **SC-007**: 100% of attempts to create an attribute with a display name already used (ignoring
  letter case and surrounding spaces) are refused, and nothing is saved.

## Assumptions

- **A-001**: Creation is individual (one attribute at a time). Mass creation (§3.2.1 «masiva»,
  §3.2.8) is out of scope.
- **A-002**: The attribute creation page is reached from the side navigation, with Spanish labels,
  like the pages of features 001 and 002.
- **A-003**: Consistency checks, not stated in the source and approved as plan decisions D-7 to
  D-11 on 2026-11-09: minimum ≤ maximum (length and value); lengths are whole numbers within the
  limits of D-7; precision is 1–10 and only for decimals (D-8); a capture mask must be a valid
  regular expression (D-7); a date format must be one of the fixed list (D-9); a currency must be a
  known ISO code (D-10); a reference needs a target (D-11).
- **A-004**: Properties of another data type (for example text properties sent with a Boolean
  attribute) are ignored and not stored. A field of the chosen type that does not apply to the
  chosen variant (precision for an integer) is refused (D-8).
- **A-005**: Type-specific properties are optional at creation except those plan decisions D-8 to
  D-11 make mandatory: number format (and precision for decimals), date format, currency code,
  reference target, and LOV selection mode and list (FR-012).
- **A-006**: The feature reuses feature 003 through its service or REST API; which one is a plan
  decision.
- **A-007**: Error handling follows features 001 and 002: Spanish messages, and the user can retry
  without retyping.
- **A-008**: §3.2 ¶2 "filtrado de valores permitidos sobre las LOv correspondientes o el Negocio"
  (allowed-value filtering by list or by business) is advanced LOV filtering, excluded by the
  feature request; it is recorded as a future capability, not configured here (OQ-013).

## Out of Scope

- Editing, deleting, deactivating or copying attributes; changing an attribute's type.
- List-of-values administration: creating lists, adding, changing, deleting, merging or migrating
  values, equivalences, external IDs, bulk value management (§3.2.1 LOV rules beyond linking).
- Advanced value filtering and cascading lists (§3.2 ¶2 filtering, A-008).
- Attribute groups: creating, editing or deleting groups, linking attributes to groups (FR-017),
  and the group catalogue of §3.2.2.
- Calculated attributes, data quality rules, business-dependent mandatory rules (§3.2.4) and
  mandatory fields per template (§3.2.5).
- Attribute metadata of §3.2.6 (relevance per system, system of record, unique key, validity),
  unless OQ-014 brings some into creation.
- SAP integrations (§3.2.7), mass import and pre-flight checks (§3.2.8 masiva).
- Capturing attribute values in products; template and taxonomy administration; local
  (template-specific) scope and inheritance through hierarchies (FR-018, FR-020).
- Attribute audit history (no project-wide audit requirement exists yet).

## Open Questions

None of the remaining open questions blocks planning; each must be answered or recorded as a
decision in the plan.

- **OQ-001**: *Resolved 2026-11-09: display name and data type required, description optional
  (FR-003).* Which fields are **mandatory** when creating an attribute (for example
  name, label, description, unit of measure)? §3.2 and §3.2.1 list none, and §3.2.8 says «(definir
  campos mínimos) Detallar campos». The Templates HU2.8 bulk layout lists `Atributos_LOVs: ID,
  friendlyName, References (IDLOV, Values)` for loads, which may hint at the answer but is not a
  requirement for this feature. (FR-003)
- **OQ-002**: *Resolved 2026-11-09: unique across all attributes, ignoring case and surrounding
  spaces; the display name is the key field for feature 003 (FR-006, FR-023).* Is the attribute **display name unique**? If so, across the whole system or
  within a scope (for example per template, per group, per language)? The answer also decides the
  attribute **key fields** feature 003 compares (FR-006).
- **OQ-003**: *Resolved 2026-11-09 by plan decisions D-7 to D-11 (A-005).* Which properties are **mandatory for each data type** (for example, is a maximum
  length required for text, a precision for decimals, a currency for currency)? May a range have
  only a minimum or only a maximum? What are the limits (maximum length, maximum precision)?
- **OQ-004**: *(a) resolved 2026-11-09 by plan decision D-2: one permission `ATTRIBUTE_CREATE`;
  (b) still open, does not affect creation.* **(a)** Is "may affect the global structure" (FR-019) the same permission as creating
  attributes (FR-002) or a separate one? **(b)** Can an attribute's **data type be changed** after
  creation (CF-002)? Not offered here either way, but the answer affects the data model.
- **OQ-005**: *Resolved 2026-11-09: deferred; attributes are created without groups (FR-017).* How are **existing attribute groups** selected, given that no feature
  creates groups yet: a prerequisite feature, a fixed initial set, or no groups until that feature
  exists? Must every attribute belong to at least one group? (FR-017)
- **OQ-006**: *Resolved 2026-11-09: global only in this feature (FR-018).* How is **global versus local scope** selected, and what is the default?
  Can an attribute be global and also have local configurations for some templates? (FR-018)
- **OQ-007**: *Deferred 2026-11-09 to the template feature: no local attributes in this feature
  (FR-018).* What exactly identifies a **template-specific (local) attribute**: a link
  to one template, to several, or to a level of the template hierarchy? Can local attributes be
  created before templates exist? (FR-018, FR-020)
- **OQ-008**: *Resolved 2026-11-09: (a) two separate properties, both allowed on rich text (plan
  decision D-6); (b) date validations are business rules (D-9).* **(a)** Are the "format mask" (case) of §3.2.1 and the "capture mask" (regular
  expression) of §3.2 one property or two (CF-003)? Do masks apply to rich text? **(b)** Are date
  "validaciones lógicas" (e.g. end date not before start date) configured on the attribute, or are
  they business rules of §3.2.4?
- **OQ-009**: *Resolved 2026-11-09: an existing list is required; until lists exist, saving an LOV
  attribute is refused (FR-012).* How should **LOV attributes be created when their list of values does
  not exist yet**: must the list exist first (LOV feature before this one), may the attribute be
  saved without a list and linked later, or is a list created together with the attribute?
  (FR-012)
- **OQ-010**: *Resolved 2026-11-09 by plan decision D-11.* What fields are required for **reference attributes**: the target object type(s)
  (product only, or any of feature 003's object types), single or multiple references, a
  relationship kind (spare part, cross-sell, up-sell)? (FR-015)
- **OQ-011**: *Resolved 2026-11-09 by plan decision D-10.* For **currency attributes**, is the currency fixed per attribute (e.g. MXN), chosen per
  value, or both? Which formatting rules are configured (decimals, separators, symbol position)?
  (FR-014)
- **OQ-012**: *Resolved 2026-11-09 (plan decision D-5, amendment D-4): a duplicate display name is
  refused before feature 003 is asked (FR-006, FR-023).* When feature 003 answers **"same object"** for an ID given at creation (same type and
  same display name), what should attribute creation do: report that the attribute already exists,
  or update it (editing is out of scope)? With FR-023, such a request is also a duplicate display
  name, so refusing it under FR-023 is one possible answer.
- **OQ-013**: Is **allowed-value filtering** by list or by business (§3.2 ¶2) configured at
  creation in a later feature, or never on the attribute itself? (A-008)
- **OQ-014**: Must any **metadata of §3.2.6** (relevant per system, system of record, multivalue,
  unique key, validity) be set at creation? §3.2.6 is not a requirement source for this feature;
  "multivalue" overlaps with LOV multiple selection.

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-09) | Request | Scope, exclusions, the questions in OQ-001–OQ-012 |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2 Administración de atributos | 2 | **Requirements source**: FR-002, FR-009, FR-010, FR-016–FR-020 |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2.1 Tipos de Atributos | 2 | **Requirements source**: FR-007–FR-015 |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.2.2, §3.2.4–§3.2.8 | 2 | Inspected for dependencies only (groups catalogue, business rules, mandatory fields, metadata, SAP, mass and individual creation); not used as requirements. §3.2.8 «(definir campos mínimos)» cited in OQ-001 |
| `p360:P360-repositorios/nuevo-pim/rag-as-is-to-be/reglas-erf/01-erf-1-1-plantillas-identificadores-y-atributos.md` §3.2, §3.2.1 (RE-PLT-005 to RE-PLT-016) | 2 (analysis of the ERF) | Confirms the reading; OEB-001, OEB-002; CF-001 |
| `p360:P360-repositorios/nuevo-pim/rag-as-is-to-be/reglas-erf/04-hu-plantillas.md` §HU2 (RE-HPL-003, RE-HPL-004, RE-HPL-013), §RE-HPL-019 | 2 (analysis of the Templates HU) | CF-001, CF-002; hint in OQ-001. Not used as requirements |
| `specs/003-identifier-management/` (spec, plan, contract) and `backend/.../identifier/` | Project | DEP-01: object type `ATTRIBUTE`, manual-ID permission, duplicate rule, key-field declaration, in-memory storage |
| `specs/002-existing-product-search/spec.md` | Project | UI language and error conventions (A-002, A-007); feature 002 unchanged |
| `.specify/memory/constitution.md` v2.2.0 | Governance | Principles I, III, VII, VIII, IX (DEP-07), XI, XII |
| `docs/source-of-truth/` | 1 | Contains only a README; no requirement for this feature |
