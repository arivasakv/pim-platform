# Research: Live Attribute Field Preview

**Feature**: `008-attribute-field-preview` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Each section records a decision, why, and the alternatives rejected.

## R-01 Where the code lives

- **Decision**: Two new frontend modules next to the attribute form (feature 004/007):
  - `frontend/src/attributes/fieldPreview.ts`: pure functions that turn the form state
    (`AttributeForm`) into a preview description: label, help text, field kind, limits, step, case
    transformation, compiled capture mask, date pattern, currency symbol, and the list of preview
    messages; plus the value checks (length, mask, range, decimals, date).
  - `frontend/src/attributes/AttributeFieldPreview.tsx` (+ `.module.css`): the panel that renders that
    description with existing design-system components.
  - `AttributeEditor.tsx` renders the panel above its action buttons (one added element).
- **Rationale**: Keeps the logic testable without rendering; keeps the editor change minimal
  (spec FR-013); no backend (FR-014).
- **Alternatives**: Logic inside the editor — rejected: the editor is already ~500 lines and its
  tests must stay unchanged. A new design-system component — rejected by decision 3.

## R-02 Inputs of the panel

- **Decision**: `AttributeFieldPreview({ form, options })`, where `form` is the editor's current
  `AttributeForm` and `options` the option lists of the chosen data type from
  `GET /api/v1/attribute-types` (already loaded by the editor), used for Spanish labels of the
  reference target and the "selection mode" names. The panel never writes to `form`.
- **Rationale**: Everything shown comes from the form or the backend (feature 004 FR-022).

## R-03 Temporary values and reset

- **Decision**: The interactive field is an inner component keyed by a string of the
  preview-relevant configuration (`previewKey(form)`, the JSON of the form fields). Any configuration
  change gives a new key, so React drops the inner component and its typed value (FR-011). The
  values live only in that component's state; nothing is sent or stored (FR-012). Closing or
  cancelling the form unmounts the editor and the panel.
- **Rationale**: Resetting by key needs no effect that sets state (the project's ESLint rule forbids
  set-state-in-effect) and cannot touch the configuration fields.
- **Alternatives**: Clearing the value in an effect on change — rejected (lint rule, extra render).

## R-04 List values for LOV attributes

- **Decision**: The outer panel (not the keyed inner field) loads the chosen list once per list ID
  with the existing `getList` (feature 006 API), ignoring stale answers; states: none chosen, loading,
  error, ready. Ready shows `values.slice(0, 50)` and, when `valueCount > 50`, "Vista previa con los
  primeros 50 de N valores." Labels are shown in list order (feature 006 keeps insertion order).
  Single selection → `SelectField` with placeholder "Selecciona una opción"; multiple → one
  `CheckboxField` per value inside a `fieldset` with the attribute name as legend. No selection mode
  yet → single, with a note.
- **Rationale**: One request per chosen list, not per keystroke (the inner field is re-created on every
  change, the loader is not).
- **Alternatives**: A native `<select multiple>` — rejected: poor usability and `SelectField` takes a
  single value; checkboxes reuse `CheckboxField`.

## R-05 Text fields

- **Decision**:
  - `TextField` (single line) or `TextAreaField` (rich text, with the note "Acepta texto enriquecido
    (HTML).").
  - `maxLength` set from the maximum length; hint "Entre {min} y {max} caracteres" / "Hasta {max}" /
    "Al menos {min}".
  - Case format: `UPPERCASE` → `toLocaleUpperCase('es-MX')`, `LOWERCASE` → `toLocaleLowerCase('es-MX')`
    on every change; `MIXED_CASE` → unchanged (spec A-003).
  - Capture mask: compiled as `new RegExp('^(?:' + mask + ')$', 'u')` inside `try/catch`; a value that
    does not match gets "No cumple la máscara de captura."; an invalid mask gives the preview message
    "La máscara de captura no es una expresión regular válida; la vista previa no la aplica." The
    whole value must match (as Java's `Pattern.matches`), the usual meaning of a capture mask.
  - A non-empty value shorter than the minimum gets "Escribe al menos {min} caracteres."
- **Note**: The `u` flag accepts JavaScript syntax; a mask valid in the backend (Java) but not in
  JavaScript is reported as "no válida para la vista previa" without blocking saving.

## R-06 Numeric and currency fields

- **Decision**:
  - `TextField type="number"` with `min`, `max` and `step`: integer → `1`; decimal → `10^-precision`
    (precision 1–10; missing or invalid → 2 with the message "Sin decimales configurados: la vista previa
    usa 2."). Currency → `10^-decimals` (0–4; missing → 2, feature 004 D-10 default).
  - Value messages: "Escribe un número entero.", "Usa como máximo {n} decimales.", "El valor debe estar
    entre {min} y {max}." (or only the side that is set).
  - Limits that are not numbers, or a minimum above the maximum, are ignored with a message.
  - Currency symbol: `Intl.NumberFormat('es-MX', { style: 'currency', currency: code,
    currencyDisplay: 'narrowSymbol' }).formatToParts(0)`; a `RangeError` or a code not of 3 letters →
    "Moneda {code} no reconocida; se muestra solo el código." Hint "Moneda: MXN ($) · 2 decimales".
- **Rationale**: Reuses `TextField` (type number already supported); no symbol table in the frontend
  (spec A-004).

## R-07 Date and time fields

- **Decision**: `TextField` with `placeholder` = the configured format (e.g. `DD/MM/AAAA HH:mm`) and
  hint "Formato: …". The format is turned into a pattern (`DD`, `MM`, `HH`, `mm` → two digits, `AAAA` →
  four digits; separators literal); a value that does not match gets "Usa el formato {format}."; a value
  that matches but is not a real date or time (31/02/2026, 25:00) gets "La fecha no existe." No format
  yet → message "Elige un formato de fecha para ver el campo." and the first offered format is not
  assumed.
- **Rationale**: Shows the configured format exactly (spec A-002); browser date pickers display the
  browser's locale format instead.

## R-08 Boolean and reference fields

- **Decision**: Boolean → `CheckboxField` labelled with the name. Reference → `SelectField`
  `disabled`, placeholder "Selecciona un objeto: {target label}" (single) or "Selecciona uno o varios:
  {target label}" (multiple, or single when not set, feature 004 D-11), with the note "Los objetos se
  podrán elegir cuando existan los formularios de producto." No target yet → message.

## R-09 Label, optional state, help text and messages

- **Decision**:
  - Label "{name} (opcional)", following the form's own "ID (opcional)" convention; empty name →
    "Nombre del atributo (opcional)".
  - The description (trimmed) is the field's `hint`; limits and formats are appended to it.
  - Value problems use the component's `error` prop (same look as form errors).
  - Configuration problems (FR-010) are listed in one `Notification variant="info"` inside the panel,
    above the field, titled "Vista previa con ajustes:".
  - Panel: a `section` with heading (h3) "Vista previa del campo", the page background
    token `--color-background` (the form card uses `--color-surface`), a `--color-border-subtle` border, and the note "Así se verá el
    campo en un formulario de producto. Lo que escribas aquí no se guarda."
- **IDs**: preview inputs use ids prefixed `vista-previa-` so they never collide with the form's ids.

## R-10 Testing

- `fieldPreview.test.ts`: every rule of R-05–R-09 as pure functions (each type, defaults, invalid
  settings, value checks).
- `AttributeFieldPreview.test.tsx`: rendering per type, LOV states (none, loading, error, empty, 50+),
  reset on configuration change, nothing sent except the list read.
- `AttributeEditor.test.tsx`: two tests (panel present in create and edit mode, above the buttons;
  saving sends no preview value). Existing assertions unchanged; the shared fetch stub learns to answer
  `GET /api/v1/lists-of-values/{id}` so the preview's read does not consume an answer meant for
  creation (fixture change only, as feature 006 did).
- `AttributesPage.test.tsx`: its fake backend answers the list detail the same way; no assertion change.
