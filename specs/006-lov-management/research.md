# Research: List of Values Management

**Feature**: `006-lov-management` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

Each section gives a decision, its rationale and the alternatives considered. Sections marked
**Provisional (owner to confirm)** settle a non-blocking open question or assumption as a recorded
decision (constitution I). **All were approved by the project owner on 2026-11-11** together with
plan.md decisions D-1 to D-12, including the feature 004 changes of R-03 and R-04.

## R-01 Domain module

- **Decision**: new backend domain `com.pim.backend.lov` with the layers of constitution XII. The
  constitution itself uses `...lov` as its example package name (Principle III). It reads attributes
  only through feature 004's `AttributeService`, and it gets list system IDs only through feature 003's
  `IdentifierService` (constitution III).
- **Rationale**: lists have their own lifecycle and data and are reused by many attributes (FR-014,
  FR-025), so they don't belong inside `attribute`.
- **Alternatives**: inside `attribute` (rejected: a second responsibility in feature 004's package,
  and the request asks for an independent module and menu entry).

## R-02 Storage: one document per list, values embedded

- **Decision**: MongoDB (constitution 2.4.0), collection `lists_of_values`, **one document per list
  with its values embedded** as an ordered array. A list may hold at most **50,000 values** (R-08).
- **Rationale**:
  - FR-016 requires bulk loads and deletions to be all-or-nothing. The local MongoDB is a standalone
    server, so multi-document transactions aren't available (they need a replica set). An update of a
    single document is atomic, so embedding gives all-or-nothing for free.
  - P360's largest list has about 33 K values (OEB-002, `ItemGroupProductLOV` 32,849). At 50,000
    values of up to about 300 bytes each, a document stays near 15 MB in the worst case, under
    MongoDB's 16 MB limit; typical lists are far smaller.
  - FU-1 will reference values by list and ID (FR-012), which works with either layout.
- **Alternatives**: a separate `lov_values` collection with a unique `(listId, idKey)` index (rejected
  for now: bulk all-or-nothing would need transactions or a compensating rollback; to revisit if a list
  ever needs more than 50,000 values). Storing values in memory (rejected: the constitution now allows
  MongoDB, and lists must survive restarts together with attributes, ADR 0003).
- **ADR**: the decision extends ADR 0003 (attribute administration data). Plan task: add the
  `lists_of_values` collection (and `lov_changes`, R-11) to ADR 0003's table with a dated amendment
  note, rather than a new ADR. **Provisional (owner to confirm)**.

## R-03 Feature 004's `ListOfValuesCatalog`: real implementation — **changes feature 004**

- **Finding**: feature 004 checks lists through the interface `attribute.repository.ListOfValuesCatalog`
  (`hasAny()`, `exists(id)`). Today it's implemented by `EmptyListOfValuesCatalog`, which always says
  "no lists" (feature 004 D-12, "replaced by the LOV feature").
- **Decision**:
  - Add `lov.service.ListOfValuesLookup`, a small public read service over the lov repository.
  - Add `lov.service.ListOfValuesCatalogAdapter`, which implements feature 004's `ListOfValuesCatalog`
    by calling `ListOfValuesLookup`.
  - Move `EmptyListOfValuesCatalog` to the test tree as a fake (without `@Repository`), as was done
    for the in-memory repositories in feature 005.
  - `exists` compares the list system ID ignoring case, as feature 003 does.
- **Rationale**: this keeps feature 004's code unchanged apart from removing the placeholder. It also
  avoids a bean cycle: `AttributeService → ListOfValuesCatalog → ListOfValuesLookup → repository`,
  while `ListOfValuesService → AttributeService`; nothing points back.
- **Alternatives**: making `ListOfValuesService` implement the catalog (rejected: bean cycle with
  `AttributeService`); changing `AttributeService` to call the lov domain directly (rejected: feature
  004 would depend on feature 006).

## R-04 Linked attributes and safe list deletion — **changes feature 004 (additive)**

- **Finding**:
  - FR-014 needs the attributes linked to a list.
  - FR-004 must refuse deleting a list that an attribute uses (SC-005: no attribute linked to a missing
    list).
  - A plain "check, then delete" leaves a race: an attribute created between the check and the delete
    would point to a deleted list.
- **Decision**: add two methods to `AttributeService`, under its existing lock:
  - `List<Attribute> findLinkedToList(String listOfValuesId)`: LOV attributes whose list matches,
    ignoring case, sorted by display name.
  - `boolean runIfNoAttributeLinksTo(String listOfValuesId, Runnable action)`: if no attribute uses the
    list, runs `action` (the list deletion) **while still holding the attribute lock** and returns
    `true`; otherwise returns `false` without running it.
- **How the race is closed**:
  - Attribute creation runs entirely under the attribute lock, including the catalog check and the
    save.
  - List deletion runs under the lov lock and then takes the attribute lock.
  - So either the attribute is saved first (and the deletion sees it and refuses), or the list is
    deleted first (and creation's catalog check fails with 422 `LIST_OF_VALUES_NOT_FOUND`).
  - Lock order is always lov → attribute. `ListOfValuesCatalogAdapter` takes **no** lov lock (it reads
    one document), so there's no deadlock.
- **Alternatives**: a shared lock bean in `common` (rejected: bigger change to feature 004); a
  "deleting" flag on the list (rejected: still racy without a common lock).
- **Implementation note (2026-11-11, T036)**: the race test showed that feature 004's
  `AttributeService.create` ran its list check *before* taking its lock, so the race above was still
  possible. The check was moved inside the lock, as the first step under it; the order of the
  checks and every response stayed the same, and feature 004's tests pass unchanged.

## R-05 List system IDs

- **Decision**: lists get IDs from `IdentifierService.assign(ObjectType.LIST_OF_VALUES, requestedId,
  {}, manualIdPermissions)`. The default sequence is `LOV` + 6 digits (feature 003 defaults:
  `LOV000001`). An optional manual ID needs feature 003's manual-ID permission for `LIST_OF_VALUES`
  (header `X-Pim-Manual-Id-Permissions`). The ID never changes (FR-001).
- **Rationale**: reuses feature 003 as the request asks; nothing new.

## R-06 List name — **Provisional** (A-002, OQ-002)

- **Decision**: trimmed, **1–80 characters**, unique ignoring letter case (`Locale.ROOT`), enforced
  also by a unique index on `nameKey` (the name lower-cased), as for groups (feature 005 R-05).

## R-07 Value ID, label and equivalence rules — **Provisional** (A-003, A-004, A-011)

- **Value ID**:
  - Trimmed, **1–40 characters**, only letters `A–Z a–z`, digits and `- _ .`. This keeps IDs safe in
    URLs (`…/values/{valueId}`) and in CSV files.
  - Kept as text with its case and leading zeros (`0001`).
  - Compared ignoring case (A-004): `idKey` = the ID upper-cased with `Locale.ROOT`, unique within the
    list.
  - Can't be changed after saving (FR-010).
- **Generated ID**:
  - The list keeps a counter `nextGeneratedNumber`, starting at 1.
  - A generated ID is the counter as a number padded with zeros to at least 4 digits (`0001`, …,
    `9999`, `10000`), as in HU2.1's examples «ej. 0001, 0002».
  - If that ID is already taken (entered by hand), the counter moves on until a free one is found.
  - The counter never goes back, so a deleted generated ID isn't reused.
- **Label**: trimmed, **1–200 characters**, required. Repeated labels are allowed (A-005).
- **Equivalence**: optional, trimmed, **up to 200 characters**; blank = none (FR-023).
- **Order**: values are shown in the order they were added; a bulk load appends new values in file
  order.
- **Alternatives**: IDs with any character (rejected: they'd need encoding in URLs and quoting in CSV);
  a global generator (rejected: IDs are per list, A-007).

## R-08 Limits — **Provisional**

- At most 50,000 values per list (R-02).
- At most 50,000 data rows and **5 MB** per CSV file. The import endpoint checks the 5 MB itself
  and answers 422 `IMPORT_TOO_LARGE`. Spring's hard multipart limit is set higher
  (`spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 11MB`; its default is 1 MB),
  because a request rejected by Spring never reaches this domain's error handler; above 10 MB Spring
  answers 413. (Implementation note of 2026-11-11; the plan first said 5 MB / 6 MB.)
- A load that would push a list past 50,000 values is refused as a whole (422 `LIST_TOO_LARGE`).

## R-09 CSV format and parsing — no new dependency

- **Decision**:
  - **Encoding**: UTF-8, with or without BOM ("CSV UTF-8" from Excel).
  - **Delimiter**: comma or semicolon, detected from the header line. Excel uses the comma in Mexico,
    but semicolon files are common.
  - **Header** required: `ID`, `Etiqueta`, and optional `Equivalencia`, matched ignoring case, accents
    and surrounding spaces, in any order. Other columns are reported as an error.
  - **Quoting**: RFC 4180 (double quotes; `""` inside quotes).
  - **Empty lines** are ignored.
  - A small parser lives in `lov.service` (about 80 lines, unit-tested), so no library is added
    (constitution II).
- **Row rules** (FR-011, OQ-005):
  - empty `ID` → generate one;
  - existing `ID` (ignoring case) → update its label, and its equivalence when the cell isn't empty;
  - new `ID` → add;
  - empty `Etiqueta` → error;
  - the same `ID` twice in the file → error on both rows;
  - an invalid `ID` or values too long → error.
- **All-or-nothing**: all rows are validated first. If there's any error, the response is 422 with
  every error (row number as in the file, counting the header as row 1; column; code; Spanish message)
  and nothing is stored. Otherwise the new value list is saved in one document update.
- **Template**: `GET /api/v1/lists-of-values/import-template` returns `text/csv` with the header line
  `ID,Etiqueta,Equivalencia` and a UTF-8 BOM. It comes from the backend, so the frontend holds no list
  data (FR-025).
- **Alternatives**: Apache Commons CSV (rejected: a new dependency for a small grammar); a JSON body
  built by the frontend (rejected: CSV parsing would be business logic in the frontend).

## R-10 Permission — **Provisional** (A-008, OQ-009)

- **Decision**: changes need feature 004's `ATTRIBUTE_CREATE`. It's read through the public
  `AttributePermissions` model type and the development header `X-Pim-Permissions`, as in feature 005
  R-06. Reading needs no permission.

## R-11 Change records (FR-019) — **Provisional (owner to confirm)**

- **Finding**:
  - FR-019 requires recording who deleted a value or list, when, and what.
  - pim-platform has no user identity yet; only permission codes arrive, in development headers.
- **Decision**:
  - Collection `lov_changes`: `{ _id: UUID, at, user, operation: VALUE_DELETED | LIST_DELETED |
    VALUES_IMPORTED, listId, listName, valueIds, counts }`. Write-only in this feature; there's no read
    endpoint.
  - Treated as part of "lists of values with their values" under constitution 2.4.0.
  - `user` comes from an optional development header `X-Pim-User` and is stored as `null` when absent.
    It stays a development stand-in, like the permission header, until login exists (DEP-06).
  - Bulk loads are recorded too, because they may update labels (OQ-005).
- **Ordering**: written after the list change succeeds. If writing the record fails, the change stays
  and the failure is logged. FR-016 concerns the data change, and a lost record is preferable to a
  refused deletion.
- **Alternatives**: only an application log line (rejected: not queryable later, and A-010 says the
  record is kept with the data); keeping the record inside the list document (rejected: lost when the
  list is deleted).

## R-12 Concurrency

- **Decision**: one lock in `ListOfValuesService` for every change, as in features 004 and 005 (single
  backend instance). R-04 adds the lov → attribute lock order for list deletion. A change to a value
  that no longer exists returns 404 `VALUE_NOT_FOUND`.

## R-13 API shape

- **Decision**: resources `/api/v1/lists-of-values` and `/api/v1/lists-of-values/{listId}/values` (plural
  nouns, constitution VII), RFC 9457 errors, collection envelope `{ items, total }`. The list detail
  includes its values and linked attributes in one response, at up to about 15 MB (50,000 values).
  Paging the values is deferred until real lists need it; recorded as a risk.
- **Status codes**:
  - 201 + `Location` on creation;
  - 204 on deletions;
  - 400 for invalid fields;
  - 403 without permission;
  - 404 for an unknown list or value;
  - 409 for a name in use, a value ID in use, or a list in use;
  - 422 for an invalid CSV or a list that would become too large.
- **CORS**: `GET`, `POST`, `PUT`, `DELETE` are already allowed (feature 005). A multipart upload is a
  simple CORS request; no change needed.

## R-14 Frontend

- **Decision**:
  - A new page `ListsOfValuesPage` at `#/listas-de-valores`, with the navigation entry "Listas de
    valores" right after "Buscar producto", before the attribute pages (FR-025; icon
    `components`, as for the other administration pages).
  - Master–detail layout like `AttributeGroupsPage`: the lists on the left; the selected list on the
    right, with its values table (ID, Etiqueta, Equivalencia when on), the add-value form, edit and
    delete per value, CSV upload with the error report, the template download link, and the linked
    attributes.
  - Change controls only when `VITE_DEV_PERMISSIONS` contains `ATTRIBUTE_CREATE`.
  - API client `api/listsOfValuesApi.ts`, reusing `permissionHeaders` and `readProblem` from
    `attributesApi.ts`.
- **Feature 004 page (FR-026)**: `AttributeCreationPage` has a selection-mode field for the "Lista de
  valores" type but no list field. Add a "Lista de valores" select fed by `GET /api/v1/lists-of-values`,
  sending `listOfValuesProperties.listOfValuesId`. When no lists exist, it shows feature 004's existing
  "no lists exist yet" message. **Changes feature 004's frontend (additive)**.
- **Large lists**: the values table shows at most 500 rows at a time, with a filter box (ID or label)
  and "Mostrar más". This is client-side over the loaded detail.
