# Tasks: Live Attribute Field Preview

**Input**: Design documents from `specs/008-attribute-field-preview/` (spec, plan, research, data-model,
contracts/preview-ui.md, quickstart)

**Prerequisites**: plan decisions D-1 to D-8, approved by the project owner on 2026-11-12.

**Tests**: required by constitution VI. Within each phase, write the tests first and see them fail.
Texts come from [contracts/preview-ui.md](contracts/preview-ui.md) exactly.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 text/number/boolean/date/currency, US2 lists of values, US3 reference and notes

## Path Conventions

- `FE/` = `frontend/src/`

---

## Phase 1: Setup

- [X] T001 Check the starting point: `git status` clean on `main`; frontend `npm test`, `npm run lint`,
  `npm run build` pass; record the test count (184) in this file's notes. (FR-013)

---

## Phase 2: Foundational (blocks every story)

**Purpose**: the pure module's core and the empty panel in the form (research R-01–R-03, R-09).

### Tests first

- [X] T002 [P] New `FE/attributes/fieldPreview.test.ts` (core): `describePreview(form, options)` returns
  label "{name} (opcional)" and "Nombre del atributo (opcional)" for an empty name (FR-004); the trimmed
  description as the start of the hint; `kind: 'none'` with the note "Elige un tipo de dato para ver el
  campo." without a data type; `previewKey(form)` changes when any form field changes and stays equal
  for equal forms (FR-011).
- [X] T003 [P] New `FE/attributes/AttributeFieldPreview.test.tsx` (shell): renders a region with heading
  (h3) "Vista previa del campo" and the note "Así se verá el campo en un formulario de producto. Lo que
  escribas aquí no se guarda."; without a data type shows the "Elige un tipo de dato…" note and no
  input; makes no request (fetch mock not called). (FR-001, FR-012)

### Implementation

- [X] T004 `FE/attributes/fieldPreview.ts`: types `FieldPreview` and `PreviewKind` (data-model.md),
  `describePreview(form: AttributeForm, options: Record<string, AttributeOption[]>)`, `previewKey(form)`
  (JSON of the form), label and hint building (hint parts joined with " · "). T002 passes.
- [X] T005 `FE/attributes/AttributeFieldPreview.tsx` and `AttributeFieldPreview.module.css`: props
  `{ form, options }`; `section` with `aria-labelledby` → h3 "Vista previa del campo", note, info
  `Notification` "Vista previa con ajustes:" listing `notes` when any; inner `PreviewField` component
  rendered with `key={previewKey(form)}`; panel background `--color-background`, border
  `--color-border-subtle`, radius and spacing tokens as in the form CSS; all input ids prefixed
  `vista-previa-`. T003 passes.
- [X] T006 `FE/attributes/AttributeEditor.tsx`: render `<AttributeFieldPreview form={form}
  options={optionsOf(typesState.types, form.dataType)} />` inside the form, after `TypeFields` and before
  the actions `div`; nothing else changes. Update the fetch stub in `FE/attributes/AttributeEditor.test.tsx`
  so `GET …/api/v1/lists-of-values/{id}` answers a list (name and values from the stubbed lists) without
  using up a creation answer; run the existing editor tests: all pass with unchanged assertions.
  (FR-001, FR-003, FR-013)

**Checkpoint**: the form shows the panel; existing tests pass.

---

## Phase 3: User Story 1 – Text, numeric, boolean, date and currency fields (P1) 🎯 MVP

**Goal**: the field kinds and rules of FR-005, FR-006, FR-007 and FR-009 (date, currency), live and
reset on change.

**Independent test**: spec US1; quickstart P2–P9, P16.

### Tests first

- [X] T007 [P] [US1] Extend `FE/attributes/fieldPreview.test.ts`:
  - Texto: kind `text`/`textarea` (rich text, with "Acepta texto enriquecido (HTML)."); hints "Entre 2 y
    5 caracteres", "Hasta 5 caracteres", "Al menos 2 caracteres", "Se escribe en mayúsculas." /
    "…minúsculas."; `MIXED_CASE` gives no transformation and no hint; `applyCase('ab', 'UPPERCASE')` →
    "AB"; mask compiled as a full match (`[A-Z]+` matches "AB", not "AB1"); `checkText` messages
    "Escribe al menos 2 caracteres." and "No cumple la máscara de captura."; an empty value gets no
    message.
  - Numérico: integer → step `1`, hint "Número entero"; decimal precision 3 → step `0.001`, hint "Hasta
    3 decimales"; range hints "Entre 0 y 100" / "Desde 0" / "Hasta 100"; `checkNumber` messages "Escribe
    un número entero.", "Usa como máximo 3 decimales.", "El valor debe estar entre 0 y 100." / "…ser al
    menos 0." / "…ser como máximo 100.".
  - Fecha y hora: `datePattern('DD/MM/AAAA HH:mm')` accepts "01/02/2026 13:05"; `checkDate` gives "Usa
    el formato DD/MM/AAAA." for "2026-02-01" and "La fecha no existe." for "31/02/2026" and "01/01/2026
    25:00" (with an hour format); all five formats of feature 004 D-9 covered.
  - Moneda: MXN → symbol "$", hint "Moneda: MXN ($) · 2 decimales" (decimals default 2), step `0.01`;
    decimals 0 → step `1`.
- [X] T008 [P] [US1] Extend `FE/attributes/AttributeFieldPreview.test.tsx`: per type renders the right
  control (text input with `maxLength`, textarea, number input with `min`/`max`/`step`, checkbox labelled
  with the name, date text input with the format as placeholder, currency number input); typing "ab1"
  with uppercase and mask `[A-Z]+` shows "AB1" and the mask message; typing beyond the maximum is cut;
  typing 150 with range 0–100 shows the range message; re-rendering with a changed configuration
  clears the typed value while the form object passed in is unchanged; no `fetch` call for these types.
- [X] T009 [P] [US1] Extend `FE/attributes/AttributeEditor.test.tsx` (2 tests): in create mode, choosing
  "Numérico" and typing a name updates the preview label and shows a number input; in edit mode the
  preview shows the attribute's saved configuration (e.g. decimal step); saving sends exactly the same
  request body as before (no preview value in it). (FR-002, FR-003, FR-012)

### Implementation

- [X] T010 [US1] `FE/attributes/fieldPreview.ts`: text rules (`applyCase`, `compileMask`, `checkText`),
  number rules (`step`, range, `checkNumber`), date rules (`datePattern`, `checkDate`), currency
  (`currencySymbol` with `Intl.NumberFormat('es-MX', { style: 'currency', currency, currencyDisplay:
  'narrowSymbol' })`, step from decimals). T007 passes.
- [X] T011 [US1] `FE/attributes/AttributeFieldPreview.tsx`: `PreviewField` renders `TextField`,
  `TextAreaField`, `TextField type="number"`, `CheckboxField`, date `TextField` with placeholder, using
  `hint` and the value message as `error`; the value is local state. T008 and T009 pass.

**Checkpoint**: US1 works in create and edit mode.

---

## Phase 4: User Story 2 – List-of-values field (P2)

**Goal**: FR-008 with the list's real values, at most 50.

**Independent test**: spec US2; quickstart P10–P13.

### Tests first

- [X] T012 [P] [US2] Extend `FE/attributes/AttributeFieldPreview.test.tsx` with a fetch stub for
  `GET /api/v1/lists-of-values/{id}`: no list → "La vista previa necesita una lista de valores asociada
  al atributo." and no request; loading → "Cargando valores de la lista…"; single selection → select
  with placeholder "Selecciona una opción" and the labels in list order, hint "Lista: Color"; multiple →
  one checkbox per value in a fieldset whose legend is the label, several checkable; 80 values → 50
  options and "Vista previa con los primeros 50 de 80 valores."; no values → "La lista Color todavía no
  tiene valores."; failed read → "No se pudieron cargar los valores de la lista para la vista previa.";
  no selection mode → single selection and the note "Tipo de selección sin elegir: la vista previa usa
  selección única."; changing another setting does not read the list again; switching lists while the
  first read is pending shows only the second list.

### Implementation

- [X] T013 [US2] `FE/attributes/fieldPreview.ts`: LOV description (selection mode, note when missing,
  `PREVIEW_VALUE_LIMIT = 50`, summary text). `FE/attributes/AttributeFieldPreview.tsx`: list loader in
  the outer panel keyed by list ID (callbacks set state; stale answers ignored), `SelectField` for single,
  `fieldset` + `CheckboxField`s for multiple. T012 passes.

**Checkpoint**: US1 and US2 work.

---

## Phase 5: User Story 3 – Reference field and configuration notes (P3)

**Goal**: FR-009 (reference) and every note of FR-010.

**Independent test**: spec US3; quickstart P14, P15.

### Tests first

- [X] T014 [P] [US3] Extend `FE/attributes/fieldPreview.test.ts`: reference with target PRODUCT (label
  "Producto" from the options) → placeholder "Selecciona un objeto: Producto" (single, and when
  cardinality is empty) / "Selecciona uno o varios: Producto" (multiple); no target → note "Elige el
  objeto referenciado para ver el campo."; every note of the contract's "Configuration notes" table:
  invalid mask, length min > max, value min > max, non-numeric limit, decimal without decimals (step
  `0.01`), unknown currency `XYZ`, no currency code, no date format.
- [X] T015 [P] [US3] Extend `FE/attributes/AttributeFieldPreview.test.tsx`: reference renders a disabled
  select with the placeholder and the note "Los objetos se podrán elegir cuando existan los formularios
  de producto."; an invalid mask shows its note inside "Vista previa con ajustes:" and the field still
  accepts text; with contradictory limits the field has no `maxLength`/`min`/`max`.

### Implementation

- [X] T016 [US3] `FE/attributes/fieldPreview.ts` and `AttributeFieldPreview.tsx`: reference description and
  disabled `SelectField`; all FR-010 notes and fallbacks. T014 and T015 pass.

**Checkpoint**: all stories work.

---

## Phase 6: Polish & Cross-Cutting

- [X] T017 [P] Update the fake backend in `FE/pages/AttributesPage.test.tsx` to answer
  `GET /api/v1/lists-of-values/{id}` (fixture only; no assertion change). (SC-005)
- [X] T018 Run frontend `npm run lint`, `npm run format:check`, `npm test`, `npm run build`; backend
  `.\gradlew.bat clean build` to confirm it is untouched; record counts. (FR-013, FR-014)
- [X] T019 Scope check per quickstart "Scope checks": no change in `backend/`, `FE/api/`,
  `FE/components/ui/`, LOV Management files or `package.json`. (FR-014)
- [X] T020 Owner check: quickstart UI scenarios P1–P19 in the browser (left to the project owner).

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → US1 → US2 → US3 → Polish.
- US2 and US3 depend only on Phase 2, but all stories edit `fieldPreview.ts` and
  `AttributeFieldPreview.tsx`, so their implementation tasks run one at a time.
- Within a phase: [P] tests first, then implementation in ID order.

### Parallel examples

- Phase 2 tests: T002 and T003 together.
- US1 tests: T007, T008, T009 together.
- US2: T012 alone, then T013. US3: T014 and T015 together, then T016.
- Polish: T017 alongside T018 preparation.

## Implementation Strategy

- **MVP**: Phases 1–3 (panel plus text, number, boolean, date and currency).
- Then US2 (lists), then US3 (reference and notes); each checkpoint leaves the form working.
- Stop at T020 for the owner's browser check; no commit until asked.

## Notes

- **Results (2026-11-12)**:
  - T001 baseline: frontend 184 tests.
  - T018: frontend 218 tests (34 new), lint, format check and build pass; backend `clean build` passes
    with no backend file changed.
  - T019: changed files are only `AttributeEditor.tsx` (one import and one element), the new
    `fieldPreview.ts` and `AttributeFieldPreview.tsx` (+ CSS and tests), and fixture-only updates in
    `AttributeEditor.test.tsx` and `AttributesPage.test.tsx`; no change in `backend/`, `FE/api/`,
    `FE/components/ui/`, LOV Management or `package.json`.
  - T020 (browser check of P1–P19): marked done by the project owner on 2026-11-12.
- **Existing assertions**: none changed. The editor test stub and the page fake backend now answer
  `GET /api/v1/lists-of-values/{id}` (T006, T017), so the preview's list read does not consume a reply
  meant for saving.

- Requirement coverage: FR-001 T003/T005/T006 · FR-002 T009/T011 · FR-003 T006/T009 · FR-004 T002/T004 ·
  FR-005 T007/T008/T010/T011 · FR-006 T007/T008/T010/T011 · FR-007 T008/T011 · FR-008 T012/T013 ·
  FR-009 T007/T014/T016 · FR-010 T014/T015/T016 · FR-011 T002/T008 · FR-012 T003/T009 · FR-013
  T001/T006/T018 · FR-014 T018/T019 · FR-015 T005/T020.
