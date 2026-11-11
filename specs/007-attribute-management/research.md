# Research: Attribute Management – Consult, Edit and Delete

**Feature**: `007-attribute-management` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Each section records a decision, why it was chosen, and the alternatives rejected. Sections marked
**changes feature 00N** touch code of an earlier feature; they are listed in the plan for approval.

## R-01 Where the code lives

- **Decision**: Extend the existing domain `com.pim.backend.attribute` (feature 004): the new update and
  delete operations, the read model with groups and list names, and the new error codes go in its
  existing `controller`, `service`, `repository`, `model` and `dto` packages. No new domain package.
- **Rationale**: Editing and deleting attributes is the attribute domain's own behavior (constitution
  III, XII); feature 004 left them out only for scope.
- **Alternatives**: A new `attributemanagement` package — rejected: it would split one aggregate across
  two domains and need the attribute repository from outside its domain.

## R-02 Reading group memberships without a package cycle — **changes features 004 and 005 (additive)**

- **Decision**:
  - `attribute.repository` gets an interface `AttributeGroupDirectory` (same pattern as feature 004's
    `ListOfValuesCatalog`, which feature 006 implements):
    - `Map<String, List<GroupReference>> groupsByAttribute()`: every membership, keyed by the
      attribute's system ID upper-cased;
    - `List<GroupReference> runIfInNoGroup(String attributeId, Runnable action)`: under the group
      lock, runs `action` only if no group contains the attribute; returns the groups that contain it
      (empty when the action ran).
  - `attributegroup.service.AttributeGroupDirectoryAdapter` implements it. It reaches
    `AttributeGroupService` through Spring's `ObjectProvider` (resolved at call time), so the bean graph
    `AttributeService → adapter ⇢ AttributeGroupService → AttributeService` has no construction cycle.
  - `AttributeGroupService` gets one additive method, `runIfInNoGroup(String attributeId, Runnable)`;
    its existing methods, constructor and lock are unchanged.
  - `GroupReference(String id, String name)` is a record in `attribute.model`.
- **Rationale**: `attributegroup` already depends on `attribute` (members are looked up through
  `AttributeService`); `attribute` must not depend on `attributegroup` (constitution III). Only the
  group service holds the lock that membership changes use, so the deletion check must run there
  (R-05).
- **Alternatives**:
  - `attribute` reads the `attribute_groups` collection directly — rejected: crosses a domain boundary
    and bypasses the group lock.
  - The frontend joins `/attribute-groups` and `/attributes` — rejected: group summaries carry no
    member IDs, so it would need one request per group; the deletion rule must be enforced in the
    backend anyway (constitution VIII).
  - Extracting the group lock into a shared bean — rejected: changes feature 005's constructor and its
    tests for no behavior gain.

## R-03 List names for LOV attributes — **changes features 004 and 006 (additive)**

- **Decision**: `ListOfValuesCatalog` (feature 004 interface) gets `Map<String, String> namesById()`
  (keys upper-cased). `ListOfValuesCatalogAdapter` (feature 006) implements it through
  `ListOfValuesLookup` with a new read method `namesById()`; no lock (reads one collection). The test
  fake `EmptyListOfValuesCatalog` returns an empty map.
  - *Implementation note (2026-11-11)*: `ListOfValuesRepository` also gained `namesById()`, which reads
    only the system ID and name of each list (MongoDB projection), so the attribute list never loads
    up to 50,000 embedded values per list just to show names.
- **Rationale**: The list and detail show the linked list's name (FR-004, FR-006) without the
  frontend loading every list.
- **Alternatives**: The frontend loads `/lists-of-values` and maps IDs to names — workable, but one
  more request and one more join in the UI for something the backend already knows; rejected for
  consistency with R-02.

## R-04 Edit API and what may change

- **Decision**: `PUT /api/v1/attributes/{systemId}` with body `AttributeUpdateRequest`: the same fields as
  `AttributeCreationRequest` (feature 004), where `systemId` is optional and `dataType` required.
  *Implementation note*: because the fields are identical, the controller reads the body into
  feature 004's `AttributeCreationRequest` record and its mapper; the contract keeps the name
  `AttributeUpdateRequest` only in this feature's change record.
  - `systemId`, when present, must equal the path ID ignoring letter case, else 422
    `SYSTEM_ID_IMMUTABLE`.
  - `dataType` must equal the stored type, else 422 `DATA_TYPE_IMMUTABLE` (spec FR-010, A-002).
  - Scope and creation date are not request fields (unknown fields are ignored, feature 004 contract).
  - Properties are mapped and validated by feature 004's `AttributePropertiesMapper` and property
    records, exactly as for creation (FR-007).
  - Response 200 with the updated attribute (R-07 shape).
- **Order of checks** (same principle as creation: cheap request checks first):
  1. request shape and properties (400, feature 004 codes) — in the controller, as for creation;
  2. permission `ATTRIBUTE_CREATE` (403 `ATTRIBUTE_CHANGE_NOT_PERMITTED`);
  3. under the attribute lock: attribute exists (404, as feature 004's read), system ID and data type
     unchanged (422), LOV list rules (feature 004 FR-012: 400/422), display name free among the other
     attributes (409 `DISPLAY_NAME_IN_USE`); then save with `updatedAt`.
- **Rationale**: Reusing the creation shape lets the frontend reuse its form and request builder
  (FR-007); a full replace (PUT) matches feature 005 and 006 updates.
- **Alternatives**: `PATCH` with partial fields — rejected: partial property changes would need merge
  rules for every type; the form always sends everything anyway.

## R-05 Delete API and dependency checks

- **Decision**: `DELETE /api/v1/attributes/{systemId}` → 204.
  1. permission `ATTRIBUTE_CREATE` (403 `ATTRIBUTE_CHANGE_NOT_PERMITTED`);
  2. `AttributeGroupDirectory.runIfInNoGroup(id, action)` takes the group lock; if a group contains the
     attribute: 409 `ATTRIBUTE_IN_GROUPS` with the groups (Problem Details extension `groups:
     [{id, name}]`), nothing changes (spec FR-014);
  3. the action takes the attribute lock: attribute exists (404), then the repository deletes it.
  - The list of values of an LOV attribute is not touched (FR-013); feature 006 computes its linked
    attributes from the attributes, so the list stops showing it.
  - The feature 003 registration is not touched, so the ID stays taken: generated IDs skip it and a
    manual ID equal to it is refused by feature 003 as used (FR-016). Feature 003 declares no key field
    for attributes, so it never answers "same object" for it (feature 004 FR-006).
- **Rationale**: Mirrors feature 006 R-04 (`runIfNoAttributeLinksTo`), with the roles reversed.
- **Alternatives**: Remove memberships automatically — rejected by the owner (clarification Q2).
  Soft delete — not requested; OQ-002.

## R-06 Locks and lock order

- **Decision**: Existing order: **lov → attribute** (feature 006) and **group → attribute** (feature
  005: membership changes look attributes up under the group lock). This feature keeps both:
  - update: attribute lock only (the list check runs under it, as for creation, feature 006 R-04);
  - delete: group lock → attribute lock (R-05);
  - reads with groups (R-07): attributes read under the attribute lock, **released**, then the
    directory takes the group lock. `AttributeService` never calls the directory while holding its own
    lock.
- **Rationale**: One global order (lov/group before attribute; lov and group never nested) excludes
  deadlocks; tests prove the two races of spec FR-017 (delete vs. add to group, relink vs. list
  delete).

## R-07 Read model and response

- **Decision**: `AttributeService.findAllDetails()` and `findDetails(id)` return `AttributeDetails(
  Attribute attribute, List<GroupReference> groups, Optional<String> listOfValuesName)`; create and
  update also answer with details. `AttributeResponse` gains three fields, all additive:
  - `updatedAt` (date-time, absent until the first edit);
  - `groups` (array of `{id, name}`, sorted by name ignoring case; empty when none);
  - `listOfValuesProperties.listOfValuesName` (read only; absent if the list cannot be found).
  The contract of feature 004 moves to v1.2.0.
- **Rationale**: One request feeds the list (FR-004) and the detail (FR-006); feature 005's member
  picker and feature 006 ignore the new fields.

## R-08 Model and storage changes — **changes feature 004 (additive)**

- **Decision**:
  - `Attribute` record gains `Optional<Instant> updatedAt` and a method `updated(displayName,
    description, properties, at)` that keeps system ID, data type, scope and creation date.
  - `AttributeDocument` gains a nullable `updatedAt`; documents written by features 004–006 have none
    and read as "never edited" (no migration, FR-019).
  - `AttributeRepository` gains `existsWithDisplayName(DisplayName, String exceptSystemId)`,
    `update(Attribute)` (replace by `_id`; a duplicate display name from a race becomes
    `IllegalStateException`) and `delete(String systemId)`. The unique display-name index stays.
  - `AttributeErrorCode` gains `ATTRIBUTE_CHANGE_NOT_PERMITTED` ("No tienes permiso para modificar
    atributos."), `SYSTEM_ID_IMMUTABLE` ("El ID de sistema no se puede cambiar."),
    `DATA_TYPE_IMMUTABLE` ("El tipo de dato no se puede cambiar.") and `ATTRIBUTE_IN_GROUPS` ("El
    atributo pertenece a %s: %s. Quítalo de esos grupos antes de eliminarlo."). Existing codes and
    messages are unchanged.
- **Rationale**: Smallest change that keeps every stored attribute readable.

## R-09 Permission — clarification Q3

- **Decision**: Update and delete check `AttributePermissions.allowsCreation()` (`ATTRIBUTE_CREATE`), the
  same development header check as creation; a new error code only so the message says "modificar"
  instead of "crear". No new permission code, role or header (FR-018). The frontend shows the "Nuevo
  atributo", "Editar" and "Eliminar" controls only with `VITE_DEV_PERMISSIONS` containing
  `ATTRIBUTE_CREATE`, as the other administration pages do.
- **Alternatives**: Reuse the code `ATTRIBUTE_CREATE_NOT_PERMITTED` ("…crear atributos") — rejected:
  wrong wording for an edit.

## R-10 Frontend page and navigation — clarification Q1, **changes feature 004's page**

- **Decision**:
  - New page `AttributesPage` at `#/atributos`; the navigation entry `{ path: '/atributos', label:
    'Atributos' }` replaces `/crear-atributo` in the same position. An old `#/crear-atributo` link
    opens Inicio (as `#/componentes` does).
  - Layout copied from `ListsOfValuesPage` (feature 006): two columns; left: card "Atributos" with the
    "Nuevo atributo" button (permission only), search field, data type filter, match count, list of
    buttons (name, system ID, type label, groups, list name), "Mostrar más" after 50 rows; right:
    the detail card (title = display name, system ID below, actions "Editar" and "Eliminar") or the
    form card ("Nuevo atributo" / "Editar atributo"). Same CSS module patterns, notifications and
    inline warning confirmation.
  - The form is **moved, not rebuilt**: `pages/AttributeCreationPage.tsx` becomes
    `attributes/AttributeEditor.tsx` (`git mv`), a card with props `mode: 'create' | 'edit'`,
    `attribute?`, `onSaved(attribute)`, `onCancel()`. In edit mode it fills the form from the
    attribute (`attributeForm.fromAttribute`), shows the system ID read-only, disables the data type
    select, and calls `updateAttribute`. Its existing tests move with it (`AttributeEditor.test.tsx`)
    and keep their assertions; only the heading assertion follows the card title.
  - Delete confirmation: inline warning in the detail card. Without groups: "¿Eliminar el atributo
    «X» (ID)?" (+ "La lista de valores «L» se conserva." for LOV attributes) with "Eliminar" and
    "Cancelar". With groups: names them, says it must be removed from them first, and offers only
    "Cancelar" (the backend refuses anyway, R-05).
  - Search and filter run in the browser on the loaded list: display name or system ID contains the
    text after `normalize('NFD')`, accents stripped, lower-cased; filter by data type code; data type
    labels and option labels come from `GET /api/v1/attribute-types` (FR-003).
- **Rationale**: Same look and behavior as "Lista de Valores" (FR-002); keeps feature 004's form and
  tests (feature request §8 "do not rebuild").
- **Alternatives**: Keep `AttributeCreationPage` as a page and add a second page — rejected by Q1.
  Server-side search — not needed at this size (spec A-006).

## R-11 Concurrent edits

- **Decision**: Last write wins (spec A-004); no version field.
- **Alternatives**: Optimistic locking with a version — rejected for now: groups and lists don't have
  it either; can be added later without breaking the contract (an optional field).

## R-12 Testing approach

- Backend: model tests (`Attribute.updated`); service tests with the in-memory repository and fakes
  for both directories (rules, order of checks, permissions, ID never reused after delete);
  concurrency tests for the two races (R-06); Mongo repository tests (update, delete, old document
  without `updatedAt`); adapter tests (`AttributeGroupDirectoryAdapter`, `namesById`); MockMvc
  integration per new status (200, 204, 400, 403, 404, 409, 422, 503).
- Frontend: API client tests; `attributeForm.fromAttribute`; `AttributesPage` tests per story with a
  fake backend; the moved editor tests; navigation test.
- Existing suites of features 004, 005 and 006 run unchanged, except constructor arguments of
  `AttributeService` in their test setup (a new fake `NoAttributeGroups`) and the moved editor test.
