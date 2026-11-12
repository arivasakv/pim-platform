# Implementation Plan: Live Attribute Field Preview

**Branch**: `008-attribute-field-preview` (spec directory; no git branch) | **Date**: 2026-11-12 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-attribute-field-preview/spec.md` (draft; decisions 1–5
approved with the feature request on 2026-11-12)

**Status**: **Approved** by the project owner on 2026-11-12, including decisions D-1 to D-8
(constitution X); implemented for creation and editing. **Amendment of 2026-11-12** (consultation
preview, spec User Story 4, FR-016 to FR-018; decision D-9): approved by the project owner on
2026-11-12.

## Summary

Add a "Vista previa del campo" panel to the shared attribute form (create and edit, features 004 and
007), above its action buttons. A pure module turns the form state into a description of the field
(kind, label, limits, masks, list values, format, currency) plus plain-Spanish notes for settings it
cannot use; a panel renders it with the existing design-system components. Typed preview values live
only in an inner component keyed by the configuration, so any change or closing the form discards
them. The only request is the existing list read for LOV attributes. No backend, design-system, API
client or LOV Management change.

**Amendment (consultation)**: the attribute detail on the "Atributos" page (feature 007) renders the
same `AttributeFieldPreview` after the configuration details, fed with the saved attribute turned into
form state by feature 007's existing `fromAttribute`. No change to the preview component or its rules;
one element added to the page's detail (D-9).

## Technical Context

**Language/Version**: TypeScript strict, React 19, Vite (frontend only)

**Primary Dependencies**: existing only; browser `Intl.NumberFormat` for the currency symbol; **no new
dependency**

**Storage**: none (preview values are component state only)

**Testing**: Vitest + Testing Library with mocked `fetch` (no jest-dom: `toBeTruthy` / `toBeNull`)

**Target Platform**: desktop and tablet browsers supported by feature 001

**Project Type**: web application (frontend part only)

**Performance Goals**: the preview updates in the same render as the configuration change; one list
read per chosen list

**Constraints**:
- Spanish (es-MX) texts fixed in [contracts/preview-ui.md](contracts/preview-ui.md).
- Existing assertions of features 004, 006 and 007 unchanged (spec FR-013, SC-005).
- ESLint rule set-state-in-effect: state changes only in callbacks (reset by key, research R-03).

**Scale/Scope**: 1 pure module, 1 panel component with CSS, 1 added element in `AttributeEditor.tsx`,
tests; lists show at most 50 values.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-driven | PASS | Spec with decisions 1–5 and plan decisions D-1 to D-8 approved on 2026-11-12 |
| II | Technology stack | PASS | React/TypeScript only; no library |
| III | Modular monolith | PASS | No backend; frontend reads the list through feature 006's existing client; business rules stay in the backend (the preview only displays configured settings) |
| IV | Business scope | PASS | Request-level UX feature; no product data; required fields left to Template Management |
| V | Code quality | PASS | Pure functions + small component; ESLint, Prettier |
| VI | Testing | PASS | Unit tests for every rule; component tests per type and state; editor integration; existing suites unchanged |
| VII | API design | PASS | No API change |
| VIII | Security | PASS | Nothing sent or stored; capture masks compiled in `try/catch` with the `u` flag; no `eval` |
| IX | Development scope | PASS | No storage, no integration |
| X | AI governance | PASS | D-1 to D-8 and the amendment's D-9 approved by the project owner on 2026-11-12 |
| XI | Sources | PASS | Feature request is the source; OQ-001 recorded, not resolved by assumption beyond "no transformation" |
| XII | Package structure | PASS | Backend untouched; frontend files next to the attribute form |

**Gate result**: PASS. **Post-design re-check**: PASS.

## Recorded Decisions (approved 2026-11-12)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-1 | Where the code lives | `attributes/fieldPreview.ts` (pure rules) and `attributes/AttributeFieldPreview.tsx` (+ CSS); one element added to `AttributeEditor.tsx` above the actions | R-01 |
| D-2 | Inputs | The editor's `form` and the chosen type's option lists; read only | R-02 |
| D-3 | Temporary values | Inner field keyed by `previewKey(form)`; reset on any change, gone on close; never sent | R-03 |
| D-4 | List values | Outer panel loads the chosen list once per ID with `getList`; first 50 values; select (single) or checkbox group (multiple) | R-04 |
| D-5 | Text rules | maxLength enforced; min/max shown; upper/lower transform; `MIXED_CASE` unchanged; mask as full match, invalid mask ignored with a note | R-05 |
| D-6 | Number, currency | Number field with min/max/step from settings; defaults 2 decimals when missing; symbol from `Intl` (es-MX) | R-06 |
| D-7 | Date | Text field showing the configured format, with format and real-date checks | R-07 |
| D-8 | Look | Panel with page-background token and subtle border, h3 heading, "no se guarda" note, notes in an info notification, ids prefixed `vista-previa-` | R-08, R-09 |

### Amendment decision (approved 2026-11-12)

| ID | Question | Proposal | Research |
|---|---|---|---|
| D-9 | Consultation preview | `AttributesPage`'s detail renders `<AttributeFieldPreview form={fromAttribute(selected)} options={…of selected.dataType} />` after the details list; no component change; values reset by the existing key when another attribute opens; unmounted on edit, delete or leaving the page. **Changes feature 007's page (additive)** | R-11 |

## Project Structure

### Documentation (this feature)

```text
specs/008-attribute-field-preview/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/preview-ui.md          # exact texts and per-type behavior (no API contract)
├── checklists/requirements.md
└── tasks.md                         # /speckit-tasks
```

### Source Code (repository root)

```text
frontend/src/attributes/
├── fieldPreview.ts (+ fieldPreview.test.ts)                         # NEW: pure preview rules
├── AttributeFieldPreview.tsx (+ .module.css, AttributeFieldPreview.test.tsx)  # NEW: panel
├── AttributeEditor.tsx                                              # + <AttributeFieldPreview …/> above actions
└── AttributeEditor.test.tsx                                         # + 2 tests; fetch stub answers list detail
frontend/src/pages/AttributesPage.test.tsx                           # fake backend answers list detail (fixture only)
frontend/src/pages/AttributesPage.tsx (+ test)                       # amendment: preview in the attribute detail (D-9)
```

No change to `backend/`, `frontend/src/api/`, `frontend/src/components/ui/`, LOV Management files or
`package.json`.

**Structure Decision**: frontend-only addition beside the form it serves (decision 3; constitution XII
for placement conventions).

## Implementation Order (for /speckit-tasks)

1. Foundation: `fieldPreview.ts` core (label, help text, kind, notes) with tests first; panel shell in
   the editor (heading, note, "choose a data type").
2. US1: text, numeric, boolean, date, currency rules and fields; reset by key; edit mode.
3. US2: list loading states, first 50 values, select and checkbox group.
4. US3: reference field; every FR-010 note.
5. Polish: fixture updates, full frontend suite, lint, format, build, backend build unchanged, quickstart
   UI scenarios (owner), scope check.
6. Amendment (US4): page tests first, then the preview in the attribute detail; full suites; quickstart
   P20–P25 (owner).

## Complexity Tracking

No deviation from a MUST rule.

| Item | Why | Simpler alternative rejected because |
|---|---|---|
| Separate pure module (D-1) | Every rule is unit-testable and the editor change stays one element | Logic inside the editor would grow a 500-line component and risk its tests |
| Text field for dates (D-7) | Shows the configured format exactly | Browser date pickers ignore the configured format |
