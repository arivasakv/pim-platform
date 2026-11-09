# Feature Specification: Identifier Management (Administración de Identificadores)

**Feature Branch**: `003-identifier-management`

**Created**: 2026-11-09

**Status**: Approved

**Approved**: 2026-11-09, by the project owner, together with the plan's provisional decisions
D-1 to D-10 (plan.md)

**Input**: User request of 2026-11-09 (referred to below as *the feature request*): create a
feature for system identifier management, using only section **3.1 Administración de
Identificadores** of `ERF 1.1 Plantillas.docx` as the business-requirements source. Scope: unique
system IDs for products, attributes, lists of values, templates, classifications and interfaces;
manual ID at creation for users with the appropriate role; automatic generation with a numeric or
alphanumeric sequence configurable per object type; immutability after save; duplicate-ID
handling on create and import. Sections 3.2 onwards are not requirements for this feature.

## Context

Section 3.1 asks the PIM to manage identifiers under "un esquema de estricto control de unicidad
para garantizar la integridad referencial en todo el modelo de datos" [Source: p360:ERF -
HUs/ERF 1.1 Plantillas.docx §3.1, introduction]. Every object of the six listed types gets a
system ID that never changes, so that other objects can refer to it safely.

This feature covers **only the identifier rules**: how a system ID is assigned, validated, kept
unchanged and checked for duplicates. It does not deliver the creation, editing or administration
of products, attributes, lists of values, templates, classifications or interfaces. Those
features will apply these rules when they create objects.

### System IDs versus business identifiers

- A **system ID** is the identifier this feature manages. It identifies one object of one of the
  six types and never changes after the object is saved.
- **Business identifiers**, such as a product's **EAN** or **SKU**, are product data with their own
  rules. They are not system IDs and this feature does not change their rules. An EAN cannot serve
  as a system-wide unique ID in any case: feature 002 already allows the same EAN in Liverpool and
  in Suburbia (`specs/002-existing-product-search/spec.md`, FR-014 and User Story 1 scenario 3).
- Whether a product's SKU is, or is related to, its system ID is not stated in section 3.1 (see
  OQ-009).

## Clarifications

### Session 2026-11-09

- Q: Must a system ID be unique across all six object types together, or only within each object
  type? → A: **Globally, across all six object types together** (OQ-001, Option A). A product and
  an attribute can never share an ID. FR-001, FR-005, the edge cases, Key Entities and SC-002
  were updated.
- Q: How is this feature delivered and verified, given that no feature yet creates the six object
  types? → A: **The identifier rules in the backend plus a small, versioned REST API** to assign
  an ID to a new object (manual or automatic), apply the duplicate-ID rule, and read and change
  each type's sequence configuration. **No frontend screen** in this feature (OQ-007, Option B).
  FR-012, A-001, TD-001, the User Scenarios introduction and Out of Scope were updated.
- Q: When an object is created or imported with an existing ID, how does the system decide it is
  the same object? → A: **Same object type and the same values in that type's key fields**;
  anything else is a different object and gets the FR-009(b) error (OQ-004, Option B). Each object
  type declares its key fields (which fields they are is OQ-005a). FR-009, User Story 4, the edge
  cases and Key Entities were updated.
- Q: Who may set a manual ID when creating an object, and what happens when someone without that
  permission sends an ID? → A: **One permission per object type** (for example "may set manual
  product IDs"); a request that sends an ID from a user without that type's permission is
  **rejected**, not given an automatic ID (OQ-005b, Option B). Which roles hold each permission is
  decided outside this feature. FR-003, User Story 2, A-003 and Key Entities were updated.
- Q: Does this feature let each object type declare its own key fields, or must the business name
  them for all six types before planning? → A: **Each object type declares its key fields as part
  of its configuration**; the real lists come later from the features or the business that own
  each type, and this feature is tested with example declarations (OQ-005a, Option A). FR-013,
  FR-009(a), User Story 4, Key Entities and OQ-005 were updated.

## User Scenarios & Testing *(mandatory)*

The stories below say "an object is created" without naming the screen or process that creates
it, because object administration is out of scope. In this feature, "creating an object" means a
request to the identifier API to assign a system ID to a new object of a given type (FR-012); the
features that later create real objects will make that request. Every scenario is verified
through that API.

### User Story 1 - Every new object gets an automatic system ID (Priority: P1)

When an object of one of the six types is created and no ID is given, the system assigns it a new,
never-used ID from the sequence configured for that object type.

**Why this priority**: Without it, an object can be saved with no identifier. It is the default
path in section 3.1 («Si el usuario no especifica un ID, el motor del PIM generará uno de forma
automática») and the minimum for referential integrity.

**Independent Test**: Create several objects of one type without an ID; each gets a different ID
that follows that type's sequence. Do the same for a second type; its IDs follow its own sequence.

**Acceptance Scenarios**:

1. **Given** the attribute sequence is configured, **When** an attribute is created without an ID,
   **Then** it is saved with a new ID generated from the attribute sequence.
2. **Given** two objects of the same type are created one after the other without an ID, **When**
   both are saved, **Then** their IDs are different.
3. **Given** a template and a list of values are created without an ID, **When** both are saved,
   **Then** each ID comes from its own type's sequence.
4. **Given** an ID that the sequence would produce next is already in use, **When** an object is
   created without an ID, **Then** the object is never saved with the ID already in use: the
   generator skips to the next free value (plan decision D-5).

---

### User Story 2 - An authorized user sets the ID when creating an object (Priority: P1)

A user holding the manual-ID permission for an object type may give the ID of a new object of that
type. The system accepts it if no other object uses it. The ID can be given only while the object
is being created.

**Why this priority**: Section 3.1 grants this explicitly, and it decides who controls IDs at
creation time.

**Independent Test**: As an authorized user, create an object with an unused ID; it is saved with
exactly that ID. As a user without that type's permission, a request that gives an ID is
rejected.

**Acceptance Scenarios**:

1. **Given** a user with the classification manual-ID permission, **When** they create a
   classification with an unused ID they give, **Then** it is saved with exactly that ID.
2. **Given** a user without a type's manual-ID permission, **When** they create an object of that
   type, **Then** they cannot choose its ID: if the request gives one, it is rejected with an explanatory message and
   nothing is saved; without an ID, the system generates one (User Story 1).
3. **Given** an object already saved, **When** any user asks to set or change its ID, **Then** the
   request is rejected (User Story 3).
4. **Given** a user with a type's manual-ID permission gives an ID that another object already
   uses, **When** they save, **Then** the duplicate-ID rule of User Story 4 applies.

---

### User Story 3 - A saved object's ID never changes (Priority: P1)

Once an object is saved, its system ID cannot be modified by anyone, through any path.

**Why this priority**: Section 3.1 states it with no exception («bajo ninguna circunstancia»).
References from other objects depend on it.

**Independent Test**: Save an object, then try to change its ID through every path this feature
offers (as a regular user and as the most privileged role); every attempt is rejected and the ID
stays the same.

**Acceptance Scenarios**:

1. **Given** a saved product with a system ID, **When** someone tries to change that ID, **Then**
   the change is rejected with an explanatory message and the product keeps its ID.
2. **Given** a user with the manual-ID permission for that type, **When** they try to change the ID
   of a saved object, **Then** the change is rejected in the same way.
3. **Given** an update of a saved object that changes other data, **When** it is saved, **Then**
   its system ID is unchanged.

---

### User Story 4 - Creating or importing with an existing ID (Priority: P2)

When an object is created or imported with an ID that already exists, the system does not create
a second object with that ID. If the ID belongs to the same object, that object is updated,
following its key-field rules. If it belongs to a different object, the operation is rejected with
an explicit error asking for a new, unused ID.

**Why this priority**: It prevents duplicate IDs on every creation path, . An incoming object is the same
object only if it has the same type and the same key-field values as the object that holds the ID
(clarification 2026-11-09). Each type declares its key fields (FR-013); this feature is tested with
example declarations.

**Independent Test**: With an existing object, submit a creation with its ID, its type and the same
key-field values: the existing object is updated and no new one appears. Submit a creation with
that ID but a different type or different key-field values: it is rejected with the explicit error, and
nothing is created or changed.

**Acceptance Scenarios**:

1. **Given** an attribute with ID `A-100` exists, **When** an attribute is created with ID `A-100`
   and the same key-field values, **Then** the existing attribute is updated, its key-field values
   stay the same, and no second attribute is created.
2. **Given** an attribute with ID `A-100` exists, **When** an attribute with different key-field
   values, or an object of another type, is created with ID `A-100`, **Then** the operation is rejected with an explicit message saying the ID is already
   used by another object and asking for a new, unused ID, and nothing is created or changed.
3. **Given** the same situations arise during an import, **When** the import processes the object,
   **Then** the same two outcomes apply.
4. **Given** an attribute with ID `A-100` exists, **When** a creation or import with ID `A-100`
   changes one of its key-field values, **Then** it is treated as a different object and rejected
   as in scenario 2; the existing attribute is not changed.

---

### User Story 5 - Configure the ID sequence of each object type (Priority: P3)

Each of the six object types has its own automatic ID sequence, which can be numeric or
alphanumeric.

**Why this priority**: Section 3.1 requires a sequence configured per object type
(«auto-secuenciador configurado por tipo de objeto»). Generation (User Story 1) works with an
initial configuration; changing it is less urgent.

**Independent Test**: Configure one type with a numeric sequence and another with an alphanumeric
sequence; create one object of each without an ID; each ID follows its type's configuration.

**Acceptance Scenarios**:

1. **Given** products are configured with a numeric sequence, **When** a product is created
   without an ID, **Then** its ID is numeric.
2. **Given** templates are configured with an alphanumeric sequence, **When** a template is created
   without an ID, **Then** its ID follows the alphanumeric format configured.
3. **Given** a sequence configuration is changed, **When** later objects of that type are created,
   **Then** they use the new configuration, and IDs already saved do not change (FR-008).

---

### Edge Cases

- **Empty or blank manual ID**: treated as "no ID given", and the ID is generated (User Story 1).
  *Assumption A-004.*
- **Manual ID that differs from an existing ID only by letter case or by spaces** (`a-100` vs
  `A-100`, `" A-100"`): they are the same ID: surrounding spaces are trimmed and letter case is
  ignored when comparing (plan decision D-6).
- **Manual ID with characters or a length the system cannot accept**: rejected as invalid; a manual
  ID is 1–40 letters, digits, `-`, `_` or `.` (plan decision D-6).
- **Manual ID that matches the format of a type's automatic sequence** (for example, a future
  number of the numeric sequence): the manual ID is accepted, and the generator later skips that
  value (FR-005; plan decision D-5).
- **Two objects created at the same moment**, both without an ID or both with the same manual ID:
  never two objects with the same ID (FR-001, FR-005). Generated IDs may have gaps; of two
  identical manual IDs, the first registered wins and the other gets the FR-009 outcome (plan
  decision D-9).
- **The same ID used by objects of different types** (a product and an attribute both `100`):
  not allowed (FR-001). Creating the second one with that ID gets the FR-009(b) error, and
  automatic sequences of different types must never produce the same ID (FR-005): each type has its own default prefix, and the
  generator skips values already used (plan decisions D-3, D-5).
- **An ID change attempted through an import** of an object that already exists: rejected like any
  other change (FR-007).
- **Existing ID, same type, but a key-field value differs**: treated as a different object and
  rejected with the FR-009(b) error; the existing object is not changed (FR-009a).
- **Sequence exhausted**: numbers grow past the zero padding, so a sequence ends only at its
  largest value (18 digits); after that, creating an object of that type without an ID is
  rejected with an explanatory message (plan decision D-2).

## Requirements *(mandatory)*

### Explicit Requirements

Source: section 3.1 only, cited as [3.1 §n], where n is the bullet number in the section (1 =
unique ID for every element, 2 = manual ID at creation, 3 = automatic generation, 4 =
immutability, 5 = duplicate validation). No tier 1 document in `docs/source-of-truth/` covers
this feature. Full citation: `p360:ERF - HUs/ERF 1.1 Plantillas.docx §3.1 Administración de
Identificadores`.

**Object types and uniqueness**

- **FR-001**: Every product, attribute, list of values (LOV), template, classification and
  interface MUST have a system ID that is unique across all six object types together: no two objects, of
  the same type or of different types, MAY share a system ID. [3.1 introduction, §1; scope:
  clarification 2026-11-09 (OQ-001)]
- **FR-002**: No object of the six types MAY be saved without a system ID. [3.1 §1]

**Manual assignment**

- **FR-003**: Each of the six object types MUST have its own permission to set manual IDs (for
  example "may set manual product IDs"). A user holding that type's permission MUST be able to
  give the ID of an object of that type while creating it. A creation request that gives an ID
  from a user without that type's permission MUST be rejected with an explanatory message, and
  nothing MAY be saved; it MUST NOT be given an automatic ID instead. [3.1 §2 «usuarios con el
  rol adecuado»; permission model: clarification 2026-11-09 (OQ-005b)]
- **FR-004**: A manual ID MUST be accepted only at creation time. After the object is saved, no
  user MAY give or change its ID (FR-007). [3.1 §2, §4]

**Automatic generation**

- **FR-005**: When no ID is given at creation, the system MUST generate one automatically from the
  sequence configured for that object type. A generated ID MUST be one never used before
  («inédita») and MUST NOT equal the ID of any existing object of any type (FR-001).
  [3.1 §3]
- **FR-006**: Each of the six object types MUST have its own sequence configuration, and each
  sequence MUST be configurable as numeric or alphanumeric. [3.1 §3] Format, starting value and
  defaults: plan decisions D-2 and D-3; in this feature any caller may change a configuration
  (D-4).

**Immutability**

- **FR-007**: Once an object is saved, its system ID MUST NOT be modified under any circumstances:
  by any user or role, by an update, by an import, or by the duplicate-ID rule (FR-009). An attempt
  MUST be rejected with an explanatory message, and the object MUST keep its ID. [3.1 §4]
- **FR-008**: Changing a type's sequence configuration MUST NOT change any ID already saved.
  [3.1 §4]

**Duplicate IDs on create or import**

- **FR-009**: When an object is created or imported with an ID that already exists, the system
  MUST NOT create a new object with that ID. Instead: [3.1 §5]
  - **(a)** if the ID belongs to the same object, the system MUST update the existing object. The
    incoming object is the same object only if it has the same object type **and** the same values
    in every key field declared for that type; key-field values are therefore never changed by
    this update. [3.1 §5; rule: clarification 2026-11-09 (OQ-004)] Which fields are key for each
    type is declared in that type's configuration (FR-013);
  - **(b)** otherwise (a different type, or any key-field value different), the ID belongs to a
    different object: the system MUST reject the operation, MUST
    NOT create or change any object, and MUST show an explicit error message stating that the ID
    is already used by another object and asking for a new, unused ID.
- **FR-010**: FR-009 MUST apply in the same way to every path that creates objects, including
  imports, so that no path can produce two objects with the same ID. [3.1 §5]

**Messages**

- **FR-011**: Messages for the rejections in FR-003, FR-007 and FR-009(b) MUST be in Spanish and MUST name
  the ID concerned. [Language: project convention from features 001 and 002; content: 3.1 §5
  «mensaje de error explícito y solicitar un ID inédito»]

**Delivery**

- **FR-012**: The rules in FR-001 to FR-011 and FR-013 MUST be available to other features
  through one versioned interface that can: assign a system ID to a new object of a given type
  (manually or automatically); apply the duplicate-ID rule (FR-009); and read and change each
  type's configuration, that is, its sequence (FR-006) and its key-field declaration (FR-013).
  This feature MUST NOT add a user screen. [Clarification 2026-11-09 (OQ-007)]

**Key fields**

- **FR-013**: Each of the six object types MUST have a key-field declaration in its configuration,
  listing the fields that FR-009(a) compares. This feature MUST NOT fix the key fields of any type:
  the lists are supplied by the features or the business that own each type. [Clarification
  2026-11-09 (OQ-005a)]

### Observed Existing Behavior

None recorded. Section 3.1 was the only source requested for requirements, and no implementation
evidence (tier 3) was used. How P360 assigns IDs today was not analyzed for this feature; see
"Conflicts found in related documents" and OQ-010.

### Conflicts Found in Related Documents

Constitution XI requires inspecting the relevant parts of the reference repository and reporting
contradictions. The reference repository's own analysis of section 3.1 was read for that purpose.
It records the following conflicts with **other** documents. They are reported here and **not**
used as requirements; none was resolved by assumption.

- **CF-001 - Manual IDs for products**: section 3.1 allows a manual ID for every object type,
  products included. According to the analysis, `ERF 1.3 Productos.docx` §3.3.1 makes the product
  ID always automatic, with a prefix per business. [p360:P360-repositorios/nuevo-pim/rag-as-is-to-be/reglas-erf/01-erf-1-1-plantillas-identificadores-y-atributos.md:29-30
  (RE-PLT-002, citing RE-PRD-030)] (OQ-011)
- **CF-002 - Automatic generation for templates only**: according to the same analysis, User Story
  1 of the Templates HU limits automatic generation to templates («aplicable solo para
  plantillas»), while section 3.1 applies it to all six types. [same file:29] (OQ-011)
- **CF-003 - Template ID editable in bulk**: section 3.1 says a saved ID never changes. According to
  the analysis, User Story 5 of the Templates HU lists «ID Plantilla» among the fields that can be
  changed in bulk (RE-HPL-020). [same file:39] (OQ-011)
- **CF-004 - Identifier policy still open**: the reference repository lists the identifier policy
  (a business prefix plus the P360 `Identifier`, or a new sequence with equivalence tables) as a
  proposal, not a decision. [p360:P360-repositorios/nuevo-pim/docs/arquitectura/pim-headless/decisiones.md:101,188
  (DEC-PRD-01, PD-16); dudas.md:336-341] (OQ-010)

### Technical Decisions

- **TD-001**: FR-012 is delivered as a small, versioned REST API in the backend, with no frontend
  change (clarification 2026-11-09, OQ-007). It follows the existing API conventions (ADR 0001);
  its exact contract belongs to the plan.

Other technology and design choices belong to the plan, which must follow
the constitution and the existing stack (React + TypeScript + Vite frontend, Java 21 + Spring Boot
backend, MongoDB for the product catalog only under Principle IX, layered packages under Principle
XII). Storing IDs or sequences for object types other than products may need a constitution
amendment or in-memory storage under Principle IX; the plan must check this.

### Key Entities

- **System ID**: the identifier of one object. Assigned once, at creation, manually or
  automatically; never changed afterwards. Unique across all six object types (FR-001).
- **Object type**: one of product, attribute, list of values (LOV), template, classification,
  interface. Each has its own sequence configuration.
- **Object type configuration** (one per object type): its ID sequence, numeric or alphanumeric,
  plus its prefix, minimum digits and next value (plan decision D-2); and its key-field declaration (FR-013).
- **Identified object**: any object of the six types, as seen by this feature: its type, its
  system ID and, for the duplicate rule, the key fields that identify it (FR-009a, FR-013). The rest of each object's data belongs to the features that manage it.
- **Manual-ID permission** (one per object type): allows its holder to give the ID of a new object
  of that type (FR-003).
- **Business identifier** (for example EAN, SKU): product data with its own rules; not a system
  ID (see Context).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of objects saved, of all six types, have a system ID.
- **SC-002**: Across at least 1,000 objects created without an ID, including at least 100 created
  at the same moment for one type, no two objects, of any type, share an ID.
- **SC-003**: 100% of attempts to change a saved object's ID, through every path the feature
  offers and by every role, are rejected, and the ID is unchanged afterwards.
- **SC-004**: 100% of creations or imports that reuse another object's ID are rejected with a
  message that names the ID and asks for a new one, and nothing is created or changed.
- **SC-005**: 100% of creations or imports that reuse the same object's ID update that object and
  create no new object.
- **SC-006**: After a sequence configuration is changed, the next object of that type created
  without an ID follows the new configuration, and no existing ID changes.
- **SC-007**: In a review, at least 4 of 5 readers shown the duplicate-ID error message can say,
  from the message alone, why the operation failed and what to do next.

## Assumptions

- **A-001**: This feature defines and delivers the identifier rules as a shared capability.
  Object-creating features (product creation, attribute, LOV, template, classification and
  interface administration, imports) are separate features that will apply these rules through
  the API in FR-012. Until they exist, the feature is exercised through that API only.
- **A-002**: "Import" in section 3.1 means any bulk or file-based creation path. This feature
  covers only the identifier behavior of such a path (FR-009, FR-010), not the import itself.
- **A-003**: User and role administration is out of scope. Which roles or users hold each
  type's manual-ID permission (FR-003) is decided outside this feature, by the mechanism that
  provides users and roles. pim-platform has none yet, so the plan must decide how the API learns
  whether the requesting user holds the permission.
- **A-004**: A manual ID field left empty or blank counts as "no ID given" and the ID is generated.
  To confirm with the business owner.
- **A-005**: The six object type names are taken as written in section 3.1. What an "interfaz"
  (interface) and a "clasificación" (classification) are as objects is not defined in section 3.1
  (OQ-012); only their identifier rules are in scope.
- **A-006**: Feature 002 is not modified by this specification. The sample products it stores have
  no system ID today; whether and how they receive one is OQ-009.

## Out of Scope

- Product search or product creation workflows.
- Administration of products, attributes, LOVs, templates, classifications (taxonomy) or interfaces,
  beyond their identifier rules.
- SAP or any other external integration (constitution IX).
- User or role administration.
- Import and export functionality, beyond the identifier behavior in FR-009 and FR-010.
- Rules for business identifiers (EAN, SKU).
- Any frontend screen, including one for configuring sequences (FR-012).
- Anything in sections 3.2 onwards of `ERF 1.1 Plantillas.docx`.

## Open Questions

The six questions requested in the feature request are OQ-001 to OQ-006. OQ-007 to OQ-012 were
found while writing the spec. None of the remaining open questions blocks planning; each must be answered or recorded as an
assumption in the plan.

- **OQ-001**: *Resolved 2026-11-09: globally, across all six object types (FR-001).* Does uniqueness apply **globally across all six object types** (no
  product and attribute may share an ID), or **independently within each object type**? Section
  3.1 says «único (ID) a nivel sistema» and also «auto-secuenciador configurado por tipo de
  objeto»; the first suggests global, the second allows either. (FR-001)
- **OQ-002**: *Resolved 2026-11-09 by plan decisions D-2 (format: prefix + zero-padded number,
  minimum digits, next value; exhaustion), D-3 (defaults) and D-4 (anyone may change a
  configuration in this feature), approved by the project owner.* How are sequence **formats and starting values** configured: prefix, length, padding,
  allowed characters for alphanumeric sequences, starting value, step, what happens when a
  sequence runs out, and **who** may change the configuration? Because IDs are unique across all
  types (FR-001), the configuration must also keep the six sequences from producing the same ID
  (for example, a prefix or a number range per type). Section 3.1 says only "numeric or
  alphanumeric" and "configured per object type". (FR-006)
- **OQ-003**: *Resolved 2026-11-09 by plan decision D-5: skip to the next free value; never fail,
  never reuse.* What happens when an **automatically generated ID collides** with an existing ID (for
  example, one given manually earlier)? Section 3.1 says the sequence produces an «inédita»
  (never used) ID, so a collision must never be saved (FR-005); whether the system skips to the
  next free value, rejects the creation, or stops manual IDs from using the sequence's format is
  not stated.
- **OQ-004**: *Resolved 2026-11-09: same object type and the same key-field values (FR-009a).*
  How does the system decide that an incoming object with an existing ID
  is **the same object** (update) and not a different one (error)? For example: same object type
  only; same type and same key fields; or a rule per object type. (FR-009a)
- **OQ-005**: **(a)** *Resolved 2026-11-09: each type declares its key fields in its
  configuration; the actual lists come from the owning features or the business (FR-013). A type
  that declares none never matches, so reusing one of its IDs is rejected (plan decision D-8).
  Still open for the owning features: the key fields of each type.* Which are the **key fields** of each object type, and
  what are the "key-field rules" an update must respect («respetando las reglas de campos llave»)?
  (FR-009a) **(b)** *Resolved 2026-11-09: one manual-ID permission per object type; requests
  without it are rejected (FR-003).* Which roles may define manual IDs, and is it the same for
  every object type?
- **OQ-006**: *Resolved 2026-11-09 by plan decision D-9: gaps allowed; of two simultaneous
  identical manual IDs, the first registered wins.* How must ID assignment behave during **concurrent creation**? Uniqueness already
  forbids two objects with the same ID (FR-001); still open: may generated IDs have gaps or be out
  of order, and when two users submit the same manual ID at the same moment, does one succeed and
  the other get the FR-009(b) error?
- **OQ-007**: *Resolved 2026-11-09: backend rules plus a small versioned REST API, no frontend
  (FR-012, TD-001).* How is this feature **delivered and verified** on its own, given that
  no feature yet creates attributes, LOVs, templates, classifications or interfaces? For example:
  identifier rules in the backend only, verified by automated tests; a minimal backend API; or a
  screen to configure sequences (User Story 5). (A-001)
- **OQ-008**: *Resolved 2026-11-09 by plan decision D-6: trimmed; 1–40 of A–Z a–z 0–9 - _ .;
  compared ignoring case; need not follow the sequence format.* What may a **manual ID** contain: allowed characters, minimum and maximum length,
  and is it **case-sensitive** and **trimmed** (`a-100` vs `A-100`)? Must a manual ID follow its
  type's sequence format?
- **OQ-009**: How do system IDs relate to **product business identifiers**? Is the **SKU** the
  product's system ID, derived from it, or unrelated? And must the sample products of feature 002
  receive system IDs (that would change feature 002's data, which this spec does not do)?
- **OQ-010**: Must existing **P360 identifiers** be kept when data is migrated (CF-004)? Migration
  is out of scope, but the answer affects the sequence format and OQ-003.
- **OQ-011**: Section 3.1 conflicts with other documents on **product IDs** (CF-001),
  **generation for templates only** (CF-002) and **editing template IDs in bulk** (CF-003). Which
  one governs? This spec follows section 3.1, as requested, until the business owner decides.
- **OQ-012**: What is an **"interfaz"** (interface) as an object with a system ID (for example, an
  integration definition), and is a **"clasificación"** the same as a category in the
  taxonomy? Only needed to name the types correctly; their administration is out of scope.

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-09) | Request | Scope, boundaries and the six questions in OQ-001 to OQ-006 |
| `p360:ERF - HUs/ERF 1.1 Plantillas.docx` §3.1 Administración de Identificadores (page 7 in the document's index) | 2 | **Only requirements source**: FR-001 to FR-010 |
| `.specify/memory/constitution.md` v2.2.0 | Governance | Principles I, IX, XI, XII applied |
| `docs/source-of-truth/` | 1 | Contains only a README; no requirement for this feature |
| `docs/decisions/0001-api-conventions.md`, `0002-mongodb-product-catalog.md` | Project | Not used by the spec; for the plan |
| `specs/002-existing-product-search/spec.md` | Project | EAN repeats across businesses, so it is not a system ID (Context); feature 002 unchanged |
| `p360:P360-repositorios/nuevo-pim/rag-as-is-to-be/reglas-erf/01-erf-1-1-plantillas-identificadores-y-atributos.md` §3.1 (lines 13-46) | 2 (analysis of the ERF) | Confirms the reading of §3.1; source of CF-001 to CF-003. Not used as requirements |
| `p360:P360-repositorios/nuevo-pim/docs/arquitectura/pim-headless/decisiones.md` (DEC-PRD-01, PD-16) and `dudas.md` (lines 336-341) | 2 (analysis, proposals) | CF-004. Not used as requirements |

Sections 3.2 onwards of `ERF 1.1 Plantillas.docx` were not used. While extracting the document
text, its index and sections 1-2 (Objetivo, Antecedentes) were displayed, but nothing from them is
used in this specification.
