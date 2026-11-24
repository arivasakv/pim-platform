# Research: Unlink a List of Values from an Attribute

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-23

Each decision lists what was chosen, why, and what was rejected. Decisions marked **D-n** need the
project owner's approval with the plan (constitution X).

## R-01 Where the rule lives (D-1)

- **Decision**: The unlink is an attribute operation: `AttributeService.unlinkList(...)` in the
  `attribute` domain. The list of values (`lov` domain) does not change (spec FR-005); it only stops
  appearing among the list's linked attributes because the attribute no longer references it (feature 006
  FR-014 already computes linked attributes from the attributes).
- **Why**: The attribute's data type and properties change; the list does not. Feature 007's editing
  already lives in `AttributeService` (constitution III, XII).
- **Alternatives**: an operation of `ListOfValuesService` (rejected: it would have to change attributes,
  which belong to another domain).

## R-02 Two calls: review, then unlink with a review token (D-2)

- **Decision**:
  1. `GET /api/v1/attributes/{systemId}/list-unlink` → the **review**: the list to detach (ID and name),
     the taxonomy nodes whose value filters for the attribute will be deleted (ID and name, sorted by
     name), and a `reviewToken`. Nothing changes. 422 `ATTRIBUTE_NOT_LIST_OF_VALUES` for another type.
  2. `POST /api/v1/attributes/{systemId}/list-unlink` with `dataType`, the matching properties object
     (feature 004 shapes) and `reviewToken` → 200 with the attribute as stored. 409 `UNLINK_OUTDATED` when
     the token no longer matches.
  The token is a SHA-256 over the attribute's current definition (data type, properties, display name,
  last change) and the IDs of the nodes with filters for it.
- **Why**: FR-009 asks for a review before confirming and FR-011 for refusing if anything changed since;
  the same stateless pattern as feature 013 (check token), no storage.
- **Alternatives**: `PUT /attributes/{id}` with a `dataType` change and a flag (rejected: feature 007's
  PUT refuses type changes by contract, and mixing a rename with an unlink in one call blurs the review);
  no token (rejected: filters added after the review would be deleted unseen).

## R-03 Validation of the new type (D-3)

- **Decision**: The body uses feature 004's request shapes (`dataType` + one properties object). Order:
  request format and properties (400, feature 004 codes; also `REVIEW_TOKEN_REQUIRED`) → permission (403)
  → attribute exists (404) →
  attribute is a list of values (422 `ATTRIBUTE_NOT_LIST_OF_VALUES`) → new type is not
  `LIST_OF_VALUES` (422 `UNLINK_TO_LIST_NOT_ALLOWED`) → token matches (409 `UNLINK_OUTDATED`) → apply.
  *Order corrected during implementation (2026-11-23): the request is checked before the permission, as
  feature 004's creation does.*
  The display name is not part of the body and never changes here (spec edge case).
- **Why**: FR-003 reuses feature 004's rules and messages; the order matches feature 007's update
  (request, permission, existence, then business rules).

## R-04 Deleting the filters together with the type change (D-4)

- **Decision**: Extend the port `attribute.repository.TaxonomyAssignmentDirectory` (implemented by the
  taxonomy domain's adapter, feature 010 D-7) with:
  - `List<TaxonomyNodeReference> nodesFilteringValuesOf(String attributeId)` — for the review;
  - `void runRemovingValueFilters(String attributeId, Runnable action)` — under the taxonomy lock, runs
    `action` (which takes the attribute lock and stores the new definition), then deletes every filter of
    the attribute (`ValueFilterRepository.deleteByAttribute`, new). If deleting the filters fails, the
    adapter rethrows and `AttributeService` restores the previous definition (compensation, as feature 013
    R-06: no multi-document transactions on a standalone MongoDB).
  Lock order stays taxonomy → attribute (feature 010 R-05); the list-of-values lock is not taken.
- **Why**: FR-008 and FR-011: the filters go with the unlink, all or nothing, and no node can add a filter
  for the attribute in between (the taxonomy lock is held).
- **Alternatives**: the attribute domain reading `taxonomy_value_filters` directly (rejected: constitution
  III); deleting filters first (rejected: a refused or failed type change would have lost them).

## R-05 Feature 007's rule and the attribute model

- **Decision**: `Attribute.withDataType(AttributeDataType, AttributeProperties, Instant)` (new) changes the
  type and properties and sets the last-change date; system ID, display name, scope and creation date are
  kept. Feature 007's `update` keeps refusing type changes (`ImmutableFieldException`); only `unlinkList`
  calls the new method. Spec FR-013 amends 007 FR-010 accordingly.
- **Why**: The exception stays narrow and testable.

## R-06 Frontend: one form, two entry points (D-5)

- **Decision**:
  - The attribute edit form (feature 007 `AttributeEditor`, edit mode) gets, for a list-of-values
    attribute, a button **«Desvincular lista»**. It switches the form to **unlink mode**: the name field is
    read only; «Tipo de dato» offers the seven non-list choices (feature 004 FR-007, numeric shown as
    Entero/Decimal); the type's property fields and the field preview (feature 008) follow the choice; a
    **review** box (from the GET) shows the list to detach and the nodes whose filters will be deleted; the
    buttons are **«Desvincular y guardar»** and **«Cancelar»** (back to the normal edit form).
  - The list page (feature 006) shows, next to each linked attribute, a button **«Desvincular»** that
    navigates to `#/atributos?desvincular={systemId}`; the attributes page reads that parameter once,
    opens the attribute's edit form in unlink mode and clears the parameter.
  - `getPathFromHash` ignores a `?query` after the path, so the route still matches.
- **Why**: Owner decision Q2 = C (spec FR-006): one form, two entry points; reuses the editor, its
  validation messages and the preview.

## R-07 Change history

- **Decision**: No new history. Attributes have none today (feature 007); the unlink updates the
  attribute's last-change date. Feature 006's list change records are not written (the list does not
  change).

## R-08 Contract

- **Decision**: The two operations are added to the attributes contract as **v2.2.0** (additive) in
  `specs/004-attribute-creation/contracts/attributes.openapi.yaml`, with this feature's delta in
  `contracts/list-unlink.openapi.yaml`. New codes: `ATTRIBUTE_NOT_LIST_OF_VALUES`,
  `UNLINK_TO_LIST_NOT_ALLOWED`, `UNLINK_OUTDATED`, `REVIEW_TOKEN_REQUIRED`.
