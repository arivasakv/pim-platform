# Quickstart: Live Attribute Field Preview

**Feature**: `008-attribute-field-preview` | **Spec**: [spec.md](spec.md) |
**UI contract**: [contracts/preview-ui.md](contracts/preview-ui.md)

Commands are for Windows PowerShell.

## Prerequisites

- As for feature 007: backend running (`.\gradlew.bat bootRun`), frontend (`npm run dev`),
  `frontend/.env` with `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`.
- Data: list "Color" with values Azul and Rojo; a list "Grande" with 80 values (CSV import, feature
  006); one existing numeric attribute "Peso" (decimal, 2 decimals, 0–100).
- Automated suites: frontend `npm run lint`, `npm run format:check`, `npm test`, `npm run build`;
  backend `.\gradlew.bat clean build` (must be unchanged: no backend file changes).

## UI scenarios (browser)

Open "Atributos" → "Nuevo atributo" unless stated.

| # | Story / requirement | Steps | Expected |
|---|---|---|---|
| P1 | FR-001 | Open the form | "Vista previa del campo" below the type fields, above "Crear atributo", with its own background and the "no se guarda" note; "Elige un tipo de dato para ver el campo." |
| P2 | US1, FR-004, FR-005 | Name "Código", type Texto, min 2, max 5, Mayúsculas, mask `[A-Z]+` | Field "Código (opcional)"; typing "ab1" shows "AB1" and "No cumple la máscara de captura."; a 6th character is not accepted; "A" shows "Escribe al menos 2 caracteres." |
| P3 | FR-011 | With a typed preview value, change max to 6 | Preview value cleared; configuration kept |
| P4 | FR-005 | Check "Texto enriquecido (HTML)" | Multi-line field; "Acepta texto enriquecido (HTML)." |
| P5 | FR-006 | Numérico decimal, 2 decimals, 0–100; type 150, then 1.234 | Range message; "Usa como máximo 2 decimales." |
| P6 | FR-006 | Switch to Entero; type 1.5 | "Escribe un número entero." |
| P7 | FR-007 | Booleano | Checkbox with the name |
| P8 | FR-009 | Fecha y hora `DD/MM/AAAA`; type 31/02/2026, then 2026-02-01 | "La fecha no existe."; "Usa el formato DD/MM/AAAA." |
| P9 | FR-009 | Moneda MXN, 2 decimals; then code XYZ | "Moneda: MXN ($) · 2 decimales"; then the unknown-currency note |
| P10 | US2, FR-008 | Lista de valores, single, list "Color" | Drop-down Azul, Rojo |
| P11 | FR-008 | Switch to multiple | Two checkboxes; both can be checked |
| P12 | FR-008 | Choose list "Grande" | 50 values; "Vista previa con los primeros 50 de 80 valores." |
| P13 | FR-008 | No list chosen | "La vista previa necesita una lista de valores asociada al atributo." |
| P14 | US3, FR-009 | Referencia, Producto, Varias | Disabled "Selecciona uno o varios: Producto" and the note |
| P15 | FR-010 | Texto with mask `[A-Z` | Invalid-mask note; saving still shows the form's own error (feature 004) |
| P16 | FR-003 | Open "Peso" → "Editar" | Preview of the saved configuration (decimal, 0–100) |
| P17 | FR-012 | Type in the preview, then "Cancelar" and reopen | Preview empty; in the browser's network tab no request carried the typed value |
| P18 | FR-013 | Create and edit attributes as in features 004 and 007 | Same requests, responses and messages as before |
| P19 | FR-015 | Browser at 1440 px and 768 px | Panel full width under the form; no horizontal scroll |

## Consultation scenarios (amendment 2026-11-12)

Open "Atributos" and select an attribute in the list (detail view, no editing).

| # | Story / requirement | Steps | Expected |
|---|---|---|---|
| P20 | US4, FR-016 | Open "Peso" (decimal, 2 decimals, 0–100) | After "Modificado"/"Creado", the "Vista previa del campo" panel with the same look as in the form; number field "Peso (opcional)", step 0.01 |
| P21 | FR-017 | Type 150 in the preview | Range message; nothing saved; no save action for it |
| P22 | FR-017 | Open an LOV attribute (multiple selection) whose list has 80 values | 50 checkboxes; "Vista previa con los primeros 50 de 80 valores." |
| P23 | FR-017 | Type in the preview, open another attribute, come back | Preview empty again |
| P24 | FR-016 | Without `ATTRIBUTE_CREATE` | The preview is shown in the detail as well |
| P25 | US4 sc. 6 | "Editar", change the decimals, save | The detail's preview shows the new decimals |

## Scope checks

- `git diff --stat` shows changes only under `frontend/src/attributes/` (new preview files, one added
  element in `AttributeEditor.tsx`, test fixture updates) and `frontend/src/pages/AttributesPage.test.tsx`
  (fixture only).
- Amendment: additionally only `frontend/src/pages/AttributesPage.tsx` (imports and one element in
  the detail), a spacing class in `AttributesPage.module.css` and new tests in `AttributesPage.test.tsx`; `AttributeFieldPreview.tsx` and
  `fieldPreview.ts` unchanged.
- No change in `backend/`, `frontend/src/components/ui/`, `frontend/src/api/`, LOV Management files,
  `package.json`.
