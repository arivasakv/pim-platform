# Implementation Plan: Attribute Organization by Attribute Groups

**Branch**: `009-attribute-group-organization` | **Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/009-attribute-group-organization/spec.md` (approved 2026-11-12)

**Status**: **Approved** by the project owner on 2026-11-12, including decisions D-1 to D-9 (constitution X).

## Summary

Turn the "Atributos" page (feature 007) into a group-organized browser: a **group panel** ("Todos los
atributos", "Sin grupo", every group with its count, "Buscar grupo", link to "Grupo de Atributos")
beside the existing list, whose search and type filter now apply within the chosen entry and point to
matches elsewhere. The attribute detail gets a **"Grupos" section** to add the attribute to a group or
remove it, through feature 005's existing membership endpoints. On "Grupo de Atributos" (feature 005),
**group deletion gets an inline confirmation** and **"Agregar atributos" gets a search and 50-row
paging**.

**Frontend only.** Every piece of data already exists in the backend's responses: `GET
/api/v1/attributes` returns each attribute with its groups (feature 007), `GET
/api/v1/attribute-groups` returns each group with `attributeCount` (feature 005), and adding or
removing a membership uses feature 005's `POST`/`DELETE …/attribute-groups/{id}/attributes`. No
backend, API contract, data model or stored data change.

## Technical Context

**Language/Version**: TypeScript strict, React 19, Vite (frontend only); backend Java 21 untouched

**Primary Dependencies**: existing only (design-system components in `components/ui`, API clients
`attributesApi` and `attributeGroupsApi`); **no new dependency**

**Storage**: none new (MongoDB data of features 004–007 read and changed only through existing
endpoints)

**Testing**: Vitest + Testing Library with mocked `fetch` (no jest-dom: `toBeTruthy` / `toBeNull`);
backend `.\gradlew.bat clean build` must stay green and unchanged

**Target Platform**: desktop and tablet browsers supported by feature 001 (768 px and up)

**Project Type**: web application (frontend part only)

**Performance Goals**: with 2,000 attributes and 150 groups, choosing an entry or typing re-filters in
one render (< 0.5 s, SC-001); one load of attributes, types and groups per page open and per change

**Constraints**:
- Spanish (es-MX) texts fixed in [contracts/browse-ui.md](contracts/browse-ui.md).
- ESLint `set-state-in-effect`: state set only in callbacks; the fallback of a vanished group is
  derived during render (research R-07).
- Existing assertions of features 004–008 unchanged except the ones listed in research R-08.
- Feature 005's rules (only empty groups deleted; no duplicate membership) enforced by the backend as
  today; the UI only avoids pointless requests.

**Scale/Scope**: 1 pure module, 2 new components (+ CSS), changes to 2 pages (+ CSS) and their tests;
assumed volume up to ~5,000 attributes and ~300 groups (spec A-001).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec and decisions D-1 to D-9 approved by the project owner on 2026-11-12 |
| II | Technology stack | PASS | React/TypeScript, existing components; no library |
| III | Modular monolith | PASS | No backend change; frontend uses only documented REST endpoints of features 005 and 007; membership rules stay in `AttributeGroupService` |
| IV | Business scope | PASS | Attribute administration (§3.2); no templates, products or exports |
| V | Code quality | PASS | Filtering rules in a pure module; small components; ESLint, Prettier |
| VI | Testing | PASS | Unit tests for every browsing rule; component tests for panel and groups section; page tests for combined flows; existing suites stay green |
| VII | API design | PASS | No API change; existing contracts of features 005 (v1) and 004/007 (v1.2.0) reused as published |
| VIII | Security | PASS | No new data handling; permission header sent by the existing clients; no new permission rule (spec FR-016) |
| IX | Development scope | PASS | No storage or integration added |
| X | AI governance | PASS | D-1 to D-9 approved by the project owner on 2026-11-12 |
| XI | Sources | PASS | ERF §3.2 ¶3, §3.2.2 cited (tier 2); missing tier 1 copy recorded as OQ-001; open business rules left as OQ-002 to OQ-006 |
| XII | Package structure | PASS | Backend untouched; frontend files beside the attribute page and form |

**Gate result**: PASS. **Post-design re-check**: PASS.

## Recorded Decisions (approved 2026-11-12)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | New feature or amendment | New feature 009 that **extends** 005 and 007 (no re-specification); their specs stay as they are and are cited | R-01 |
| D-2 | Where filtering happens | **In the browser**, over the list feature 007 already loads (every attribute with its groups), as feature 007 does for search and type; counts per group from feature 005's `attributeCount`. Server-side paging deferred (spec FR-019, OQ-003) | R-02 |
| D-3 | Layout | Wide (≥ 90rem viewport): three columns *panel · list · detail*; 64–90rem: panel as a collapsible block above *list · detail*; ≤ 64rem: everything stacked, panel collapsible. Panel's group list scrolls on its own; one DOM, CSS decides | R-03 |
| D-4 | Counts and filters | Panel counts are totals (not filtered by search); the list shows matches. Search and type filter kept when switching entry; paging reset. "N coincidencias en otros grupos" + "Buscar en todos los atributos" when the chosen entry has no match | R-05 |
| D-5 | Membership from the attribute | New "Grupos" section in the detail (replaces the "Grupos" row of the details list): one line per group with "Quitar" (inline confirmation), plus "Agregar a un grupo" (select of groups it is not in) and "Agregar". Calls feature 005's `addAttributes(groupId, [systemId])` / `removeAttribute(groupId, systemId)`, then re-reads the attribute and reloads list and groups | R-04 |
| D-6 | Group management placement | Stays on "Grupo de Atributos"; the panel links to it ("Administrar grupos"). No create/rename/delete of groups on "Atributos" | R-01 |
| D-7 | "Grupo de Atributos" changes | Inline delete confirmation like features 006/007 (empty group: confirm; group with attributes: explanation and "Cancelar" only, no request); picker gets "Buscar atributo" and 50-row paging, choices kept across searches | R-06 |
| D-8 | State | Chosen entry, search, type filter and panel expansion are page state only (lost on leaving the page, spec A-004); a chosen group missing after a reload is treated as "Todos los atributos" during render | R-07 |
| D-9 | Delete refusal text (changes feature 007's text) | "…Quítalo de esos grupos en la sección «Grupos» de este atributo antes de eliminarlo." instead of pointing to "Grupo de Atributos"; the rule is unchanged | R-04 |

## Project Structure

### Documentation (this feature)

```text
specs/009-attribute-group-organization/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/browse-ui.md           # exact texts and behavior (no API contract change)
├── checklists/requirements.md
└── tasks.md                         # /speckit-tasks
```

### Source Code (repository root)

```text
frontend/src/attributes/
├── attributeBrowsing.ts (+ attributeBrowsing.test.ts)          # NEW: pure rules (entries, counts, filters, matches elsewhere, group search)
├── AttributeGroupPanel.tsx (+ .module.css, .test.tsx)           # NEW: the group panel
└── AttributeGroupsSection.tsx (+ .module.css, .test.tsx)        # NEW: "Grupos" section of the detail
frontend/src/pages/
├── AttributesPage.tsx (+ .module.css, .test.tsx)                # panel, combined filters, groups section, 3-area layout, D-9 text
└── AttributeGroupsPage.tsx (+ .module.css, .test.tsx)           # delete confirmation, searchable paged picker
```

No change to `backend/`, `frontend/src/api/`, `frontend/src/components/ui/`, `navigation.ts`, the
attribute form and preview (`AttributeEditor`, `AttributeFieldPreview`, `fieldPreview`,
`attributeForm`), LOV Management files or `package.json`.

**Structure Decision**: frontend-only extension beside the page it serves; the search helpers now in
`AttributesPage.tsx` (`normalized`, `matches`) move to `attributeBrowsing.ts` unchanged so the page,
the panel and the picker share them.

## Implementation Order (for /speckit-tasks)

1. Foundation: `attributeBrowsing.ts` with tests first (moved search helpers + entries, counts,
   matches elsewhere, group search); page loads groups beside attributes and types.
2. US1: group panel component (states, counts, selection, link, collapse) and page integration with
   the three-area layout.
3. US2: combined filters within the entry, matches elsewhere, group search, paging reset.
4. US3: "Grupos" section in the detail (add, remove with confirmation, errors, refresh), D-9 text.
5. US4: "Grupo de Atributos" delete confirmation and picker search/paging.
6. Polish: full frontend suite, lint, format, build; backend clean build unchanged; quickstart (owner
   browser check); scope check.

## Complexity Tracking

No deviation from a MUST rule.

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| Client-side filtering (D-2) | Data already loaded by feature 007; instant switching; no API change | Server-side paging needs new query parameters in two contracts and backend work, not needed at the assumed volume (A-001); recorded as FR-019 |
| Collapsible panel by CSS (D-3) | One component, one DOM, tests independent of screen size | Two controls (list + select) for wide/narrow would duplicate state and labels |
| Membership section in the detail (D-5) | Closes gap 2 with existing endpoints | Sending the user to the group page keeps today's friction; a bulk tool is out of scope |
