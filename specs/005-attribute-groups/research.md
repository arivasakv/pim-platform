# Research: Attribute Management – Attribute Groups

**Feature**: `005-attribute-groups` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

Each section gives a decision, its rationale and the alternatives considered. Sections marked
**Provisional (owner to confirm)** settle a non-blocking open question as a recorded decision
(constitution I); plan.md lists them for approval. **R-03 changes feature 004** (an additive endpoint)
and needs explicit approval.

## R-01 Domain module

- **Decision**: new backend domain `com.pim.backend.attributegroup` (layers of constitution XII). It
  reads attributes only through feature 004's `AttributeService` (constitution III). Feature 004's
  existing classes are not changed except for R-03.
- **Rationale**: groups have their own lifecycle (create, rename, delete, membership) and their own
  data; keeping them out of `attribute` leaves feature 004 almost untouched.
- **Alternatives**: groups inside `attribute` (rejected: would grow feature 004's package and service
  with a second responsibility).

## R-02 Storage — **Approved: MongoDB (changed by the owner at approval)**

- **Decision**: groups in MongoDB, collection `attribute_groups` (`_id` UUID, unique index on the
  name lower-cased). Because groups hold attribute system IDs, feature 004's attributes and feature
  003's identifier registry and configurations move to MongoDB too, behind their existing repository
  interfaces (constitution 2.3.0; design in ADR 0003).
- **Rationale**: the owner asked for persisted groups. Persisting groups alone would break after a
  restart (the ID sequence restarts and new attributes appear in old groups).
- **Alternatives**: in memory (the original proposal, rejected by the owner); groups only, or groups
  and attributes only (rejected, inconsistent after restarts).

## R-03 Listing attributes to choose from — **Provisional, changes feature 004 (owner to confirm)**

- **Finding**: to add attributes to a group (US2, FR-006) the page must offer the existing attributes.
  Feature 004 can only read one attribute by ID (`GET /api/v1/attributes/{systemId}`); there is no
  list.
- **Decision**: add **`GET /api/v1/attributes`** to feature 004's domain: a collection of every
  attribute (same item shape as `GET /api/v1/attributes/{id}`), sorted by display name ignoring case.
  Additive only: `AttributeRepository.findAll()`, `AttributeService.findAll()`, one controller method;
  feature 004's contract becomes v1.1.0 with the new operation. Required by this spec's FR-006 and
  FR-012.
- **Rationale**: listing attributes belongs to the attribute domain (constitution III). The frontend
  calls the new endpoint for the picker; the group domain itself only needs `AttributeService.find`.
- **Alternatives**: typing attribute system IDs by hand (rejected: unusable, and the user cannot see
  which attributes exist); a list endpoint inside the group domain (rejected: it would read another
  domain's data).

## R-04 Group identifier (FR-016)

- **Decision**: a random UUID generated at creation; never changes. Feature 003 is not used.
- **Rationale**: opaque, unique without coordination, safe in URLs; groups are not a feature 003 object
  type (clarification 5).

## R-05 Name and description limits — **Provisional** (OQ-007)

- **Decision**: name trimmed, **1–80 characters**, unique ignoring letter case (FR-003, `Locale.ROOT`);
  description optional, trimmed, **up to 500 characters**, blank = absent.

## R-06 Permission (FR-002)

- **Decision**: reuse feature 004's model type `AttributePermissions` (public) and the development
  header `X-Pim-Permissions`; code `ATTRIBUTE_CREATE` (clarification 3). The group domain reads the
  header in its own `RequestPermissionsReader`-like class (feature 004's reader is an internal
  controller class). Missing permission → **403 `ATTRIBUTE_CREATE_NOT_PERMITTED`**, the same code and
  message as feature 004. Reading groups needs no permission (A-004).

## R-07 Adding attributes — **Provisional**

- **Decision**: `POST /api/v1/attribute-groups/{groupId}/attributes` with `attributeIds` (1–100
  system IDs). All-or-nothing: if any ID is unknown → **422 `ATTRIBUTE_NOT_FOUND`** naming it, nothing
  added. Known IDs already in the group are reported, not duplicated (FR-008): the 200 response lists
  `added` and `alreadyInGroup`. IDs compared ignoring case (feature 003 rule); the stored ID is the one
  feature 004 returns.
- **Rationale**: one request for several attributes (US2 "one or more"); 422 because the request is
  well formed but refers to something that does not exist, while 404 is kept for the group in the path.

## R-08 Removing an attribute — **Provisional**

- **Decision**: `DELETE /api/v1/attribute-groups/{groupId}/attributes/{attributeId}` → 204. Not a
  member → **404 `ATTRIBUTE_NOT_IN_GROUP`** (US3 scenario 3), nothing changes.

## R-09 Rename, edit and delete (FR-005)

- **Decision**: `PUT /api/v1/attribute-groups/{groupId}` with `name` and `description` replaces both
  (200); duplicate name → **409 `GROUP_NAME_IN_USE`**. `DELETE /api/v1/attribute-groups/{groupId}` →
  204 when empty, **409 `GROUP_NOT_EMPTY`** otherwise.

## R-10 Groups of an attribute (FR-012)

- **Decision**: `GET /api/v1/attribute-groups?attributeId={id}` filters the group list to the groups
  that contain that attribute (unknown attribute → empty list). Keeps the data in the group domain
  instead of adding a sub-resource under feature 004's `/attributes`.

## R-11 CORS

- **Decision**: `config.CorsConfiguration` allows `GET` and `POST`; the group page also needs `PUT` and
  `DELETE`. Add both (cross-cutting, no business rule).

## R-12 Concurrency

- **Decision**: one lock in `AttributeGroupService` around every change, so name uniqueness and
  membership stay consistent.

## R-13 Frontend

- **Decision**: one page "Grupos de atributos" in the side navigation: list of groups with counts and a
  create form; selecting a group shows its details with rename/edit, delete (explained when not
  empty), its attributes with a remove action, and an "add attributes" picker fed by
  `GET /api/v1/attributes` (attributes already in the group are excluded). Existing design-system
  components only; no new dependency.

## R-14 Testing

- **Decision**: backend unit tests (group name, service rules with a real `AttributeService` on
  in-memory repositories), MockMvc integration tests for every endpoint and error status, a
  concurrency test; feature 004's new list endpoint gets its own integration test. Frontend: Vitest +
  Testing Library tests for the API client and the page (each story, 403/404/409/422, connection
  error).
