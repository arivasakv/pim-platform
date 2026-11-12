# Tasks: Attribute Organization by Attribute Groups

**Input**: Design documents from `specs/009-attribute-group-organization/` (spec, plan, research,
data-model, contracts/browse-ui.md, quickstart)

**Prerequisites**: spec and plan decisions D-1 to D-9 approved by the project owner on 2026-11-12.

**Tests**: required by constitution VI. Within each phase, write the tests first and see them fail.
Texts come from [contracts/browse-ui.md](contracts/browse-ui.md) exactly.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 browse by group, US2 find attributes and groups, US3 groups from the attribute,
  US4 safe group management

## Path Conventions

- `FE/` = `frontend/src/`

---

## Phase 1: Setup

- [X] T001 Check the starting point on branch `009-attribute-group-organization`: working tree holds
  only this feature's specs; frontend `npm test`, `npm run lint`, `npm run build` pass; record the test
  count in this file's notes. (FR-017)

---

## Phase 2: Foundational (blocks every story)

**Purpose**: the pure browsing rules and loading the groups on "Atributos" (research R-02, R-05, R-07).

### Tests first

- [X] T002 [P] New `FE/attributes/attributeBrowsing.test.ts`: `matchesFilters` keeps feature 007's
  behavior (name or system ID, accents and letter case ignored, type filter, blank search matches);
  `inEntry` for `all`, `ungrouped` (empty or missing `groups`) and `group` (by `groups[].id`);
  `ungroupedCount`; `browse` returns `visible` in input order and `elsewhere` = matches outside the
  entry (0 for `all`); an attribute in two groups is visible under both; `filterGroups` (accent and
  case insensitive, blank returns all); `effectiveEntry` returns `all` for a group missing from a ready
  list, and the entry unchanged while groups are loading or failed. (FR-002, FR-004 to FR-008)

### Implementation

- [X] T003 New `FE/attributes/attributeBrowsing.ts`: type `BrowseEntry` (`{ kind: 'all' } | { kind:
  'ungrouped' } | { kind: 'group'; groupId: string }`), `normalized` and `matchesFilters` moved
  unchanged from `FE/pages/AttributesPage.tsx`, plus `inEntry`, `ungroupedCount`, `browse`,
  `filterGroups`, `effectiveEntry` (data-model.md). `AttributesPage.tsx` imports `matchesFilters`
  instead of its local `matches`; behavior unchanged. T002 passes; existing page tests pass.
- [X] T004 `FE/pages/AttributesPage.tsx`: groups state `loading | error | ready` loaded with
  `listGroups()` in the same effect run as attributes and types (keyed by `listRequest`), settled
  independently (a groups failure leaves the list ready); `reloadList` reloads both. Update the fake
  backend in `FE/pages/AttributesPage.test.tsx` to answer `GET /api/v1/attribute-groups` (items with
  `attributeCount` consistent with the fixtures' `groups`); all existing page tests pass. (FR-003,
  FR-008, FR-018)

**Checkpoint**: rules tested; the page has groups data; nothing visible changed yet.

---

## Phase 3: User Story 1 – Browse attributes by group (P1) 🎯 MVP

**Goal**: panel with "Todos los atributos", "Sin grupo" and groups with counts; choosing filters the
list. **Independent test**: quickstart B1–B5.

### Tests first

- [X] T005 [P] [US1] New `FE/attributes/AttributeGroupPanel.test.tsx`: renders card "Grupos" with link
  "Administrar grupos" (`href="#/grupos-de-atributos"`); entries in order Todos los atributos, Sin
  grupo, groups by given order, each with its count and accessible name "{entrada}, {n} atributo(s)";
  chosen entry has `aria-pressed="true"`; clicking an entry calls `onSelect` with the right
  `BrowseEntry`; states: "Cargando grupos…", error "No se pudieron cargar los grupos." with
  "Reintentar" calling `onRetry` (fixed entries still listed), no groups "Todavía no hay grupos de
  atributos." with link "Crear grupos en «Grupo de Atributos»"; toggle button "Grupo: {entrada} ({n})"
  with `aria-expanded` switching on click. (FR-001, FR-002, FR-003, FR-008, FR-010, FR-014)
- [X] T006 [P] [US1] `FE/pages/AttributesPage.test.tsx` new tests: panel counts from fixtures (Todos,
  Sin grupo, each group); choosing a group lists only its members and titles the list card with the
  group name; "Sin grupo" lists only ungrouped; an attribute in two groups appears under both and opens
  the same detail; empty group shows "Este grupo todavía no tiene atributos." and the hint; "Sin grupo"
  empty shows "Todos los atributos pertenecen al menos a un grupo."; an open detail stays open when the
  entry changes; groups request failing → list still works, panel error with "Reintentar"; a chosen
  group missing after a reload falls back to "Todos los atributos". (FR-001 to FR-004, FR-008, FR-009)

### Implementation

- [X] T007 [US1] New `FE/attributes/AttributeGroupPanel.tsx` and `AttributeGroupPanel.module.css`:
  props `{ groupsState, totalCount, ungroupedCount, entry, onSelect, onRetry }` (group search added in
  US2); `Card` "Grupos" with action link; entry buttons in a `ul` styled like feature 007's list buttons
  (selected state tokens); count right-aligned (`--text-sm`, secondary color); toggle button shown only
  below 90rem (CSS), entry list hidden there unless expanded, always shown at ≥ 90rem; entry list
  `max-height` with `overflow-y: auto`. T005 passes.
- [X] T008 [US1] `FE/pages/AttributesPage.tsx` and `AttributesPage.module.css`: `entry` state
  (`{ kind: 'all' }`), `effectiveEntry` in render, `browse(...)` replaces the local filter; render
  `AttributeGroupPanel` as the first grid area; list card title = entry label; empty-entry messages per
  contract; selecting an entry resets `shown` to 50 and does not touch the right column. Layout per
  research R-03: ≥ 90rem three columns `minmax(13rem, 16rem) minmax(18rem, 1fr) minmax(0, 2fr)` (panel
  sticky); 64–90rem panel `grid-column: 1 / -1` above two columns; < 64rem one column. T006 passes.

**Checkpoint**: US1 works on its own (MVP).

---

## Phase 4: User Story 2 – Find attributes and groups quickly (P1)

**Goal**: filters within the entry, matches elsewhere, group search. **Independent test**: quickstart
B6–B8.

### Tests first

- [X] T009 [P] [US2] `FE/attributes/AttributeGroupPanel.test.tsx`: "Buscar grupo" (`id="buscar-grupo"`)
  shown when groups exist; typing narrows group entries (accents/case ignored) while "Todos los
  atributos" and "Sin grupo" stay; no match shows "Ningún grupo coincide con «{texto}»." (FR-007)
- [X] T010 [P] [US2] `FE/pages/AttributesPage.test.tsx`: search and type filter apply within the chosen
  group; switching entry keeps search and type and resets paging (60 fixtures → "Mostrar más" back to
  50); no match in a group shows "Ningún atributo de «{grupo}» coincide con la búsqueda.", "{n}
  coincidencia(s) en otros grupos." and "Buscar en todos los atributos", which selects "Todos los
  atributos" and shows the match with the search kept; same for "Sin grupo" with its text; no match
  anywhere shows feature 007's text and no "otros grupos" line. (FR-005, FR-006)

### Implementation

- [X] T011 [US2] `FE/attributes/AttributeGroupPanel.tsx`: `groupSearch` local state, `TextField`
  "Buscar grupo" (type search), `filterGroups`, no-match text. T009 passes.
- [X] T012 [US2] `FE/pages/AttributesPage.tsx`: entry-specific no-match texts and the "otros grupos"
  line with its button (uses `browse(...).elsewhere`). T010 passes.

**Checkpoint**: US1 + US2 complete the browsing experience.

---

## Phase 5: User Story 3 – Change an attribute's groups from the attribute (P2)

**Goal**: "Grupos" section in the detail. **Independent test**: quickstart B9–B13.

### Tests first

- [X] T013 [P] [US3] New `FE/attributes/AttributeGroupsSection.test.tsx` (fetch mocked): heading (h3)
  "Grupos"; "Sin grupos" when none; one line per group with "Quitar" (accessible name "Quitar del
  grupo {grupo}") only when `canChange`; "Quitar" shows the confirmation text, "Cancelar" sends nothing,
  "Sí, quitar del grupo" sends `DELETE /api/v1/attribute-groups/{id}/attributes/{systemId}` with the
  permission header, then `GET /api/v1/attributes/{systemId}`, and calls `onChanged(attribute,
  message)` with "Atributo «…» quitado del grupo «…»."; "Agregar a un grupo" lists only groups the
  attribute is not in, "Agregar" disabled until chosen, sends `POST …/attributes` with `{ attributeIds:
  [systemId] }`, then re-reads and reports "Atributo «…» agregado al grupo «…»."; texts for "already in
  every group", "no groups exist", "groups not loaded"; a 404/409 shows the backend `detail` and still
  re-reads the attribute (`onChanged` with no message). (FR-011, FR-012, FR-016)
- [X] T014 [P] [US3] `FE/pages/AttributesPage.test.tsx`: fake backend handles membership POST/DELETE
  (updates fixture groups and counts); adding from the detail updates the section, the panel counts
  and the success notification; removing the open attribute from the chosen group removes it from the
  list and keeps the detail; the details list has no "Grupos" row any more; the delete refusal shows
  the D-9 text; without permission the section has no "Agregar"/"Quitar". (FR-011 to FR-013, FR-016)

### Implementation

- [X] T015 [US3] New `FE/attributes/AttributeGroupsSection.tsx` and `AttributeGroupsSection.module.css`:
  props `{ attribute, groupsState, canChange, onChanged(attribute, message?), onError(message) }`; uses
  `addAttributes`, `removeAttribute` from `FE/api/attributeGroupsApi.ts` and `getAttribute` from
  `FE/api/attributesApi.ts`; inline warning confirmation as in feature 007; `SelectField`
  `id="agregar-a-grupo"`; busy state disables buttons. T013 passes.
- [X] T016 [US3] `FE/pages/AttributesPage.tsx`: remove the "Grupos" row from `AttributeDetail`; render
  `AttributeGroupsSection` after the details list and before the field preview (`canChange` from
  `canChangeGroups()`); `onChanged` sets `selected`, shows the confirmation, reloads list and groups;
  `onError` sets `panelError`; change `DeleteConfirmation`'s text to the D-9 wording. T014 passes; the
  remaining existing page tests pass (update only the assertions listed in research R-08).

**Checkpoint**: memberships manageable from the attribute; features 005 rules enforced by the backend.

---

## Phase 6: User Story 4 – Manage groups safely at scale (P3)

**Goal**: delete confirmation and searchable picker on "Grupo de Atributos". **Independent test**:
quickstart B17–B19.

### Tests first

- [X] T017 [US4] `FE/pages/AttributeGroupsPage.test.tsx`: "Eliminar grupo" on an empty group shows
  "¿Eliminar el grupo «…»? Ningún atributo se elimina."; "Cancelar" sends nothing; "Sí, eliminar grupo"
  sends `DELETE` and shows feature 005's confirmation; on a group with attributes shows "El grupo «…»
  tiene {n} atributo(s)…" with only "Cancelar" and sends no request; picker "Buscar atributo"
  (`id="buscar-para-agregar"`) narrows by name or ID; with 60 available attributes shows 50 and
  "Mostrando 50 de 60 atributos." + "Mostrar más"; chosen attributes stay chosen across searches;
  "Agregar seleccionados (2)" sends both IDs. Adapt the existing delete test to confirm first. (FR-015)

### Implementation

- [X] T018 [US4] `FE/pages/AttributeGroupsPage.tsx` (+ `AttributeGroupsPage.module.css` if needed):
  `confirmingDelete` state reset when another group opens or editing starts; inline warning
  `Notification` per contract; picker `search` and `shown` state, filter with `matchesFilters` from
  `FE/attributes/attributeBrowsing.ts`, paging of 50, add button label with the chosen count. T017
  passes; other feature 005 page tests pass unchanged.

**Checkpoint**: all stories complete.

---

## Phase 7: Polish & Cross-Cutting

- [X] T019 Run in `frontend/`: `npm run lint`, `npm run format:check` (fix with `npm run format`),
  `npm test` (all pass; record the count), `npm run build`. (SC-007)
- [X] T020 [P] Run `.\gradlew.bat clean build` in `backend/`: passes with no backend file changed.
  (FR-017)
- [X] T021 [P] Scope check per quickstart: `git diff --stat main` lists only the files named in plan.md.
- [X] T022 Owner browser check: quickstart B1–B20 at 1440, 1024 and 768 px. (SC-001, SC-006, FR-014)

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → US1 (Phase 3) → US2 (Phase 4) → US3 (Phase 5); US4 (Phase 6) depends only on
  T003 (shared `matchesFilters`) and can run after Phase 2 in parallel with US1–US3.
- Within a phase: tests first, then implementation; tasks touching `AttributesPage.tsx` or its test run
  in order (T004 → T006/T008 → T010/T012 → T014/T016).
- Polish after all stories.

## Parallel Opportunities

- T002 alone in Phase 2 (T003, T004 follow).
- T005 ∥ T006; T009 ∥ T010; T013 ∥ T014 (different files).
- Phase 6 (T017–T018) ∥ Phases 3–5 once T003 is done.
- T020 ∥ T021.

## Implementation Strategy

- **MVP**: Phases 1–3 (browse by group with counts and "Sin grupo").
- Then US2 (search within entry), US3 (memberships from the attribute), US4 (group page safety).
- Each checkpoint leaves every existing suite green.

## Notes

- No backend, API client, design-system, navigation, form or preview file changes (plan "Project
  Structure").
- Test count at start (T001): 224 frontend tests. After implementation (T019): 266 (42 new: 14 rules, 7 panel, 9 groups section, 10 page, 2 group page picker; group page delete test adapted). Backend clean build unchanged (T020).
- The "Grupos" section is a `role="group"` named by its h3, so it does not compete with the panel's "Grupos" region.
