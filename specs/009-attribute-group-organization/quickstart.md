# Quickstart: Attribute Organization by Attribute Groups

**Feature**: `009-attribute-group-organization` | **Spec**: [spec.md](spec.md) |
**UI contract**: [contracts/browse-ui.md](contracts/browse-ui.md)

Commands are for Windows PowerShell.

## Prerequisites

- As for features 005 and 007: backend running (`.\gradlew.bat bootRun` in `backend/`), frontend
  (`npm run dev` in `frontend/`), `frontend/.env` with `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`.
- Data (create through the UI or API of features 004 and 005):
  - attributes "Peso" (numérico), "Volumen" (numérico), "Costo" (moneda), "Color" (lista de valores,
    list "Color");
  - groups "Logísticos" (Peso, Volumen), "Compra Ventas" (Peso, Costo), "Temporal" (empty);
  - "Color" in no group.
- Automated suites: frontend `npm run lint`, `npm run format:check`, `npm test`, `npm run build`;
  backend `.\gradlew.bat clean build` (unchanged: no backend file changes).

## Browser scenarios

Open "Atributos" unless stated.

| # | Story / requirement | Steps | Expected |
|---|---|---|---|
| B1 | US1, FR-001, FR-003 | Open the page | Panel "Grupos": Todos los atributos 4 (chosen), Sin grupo 1, Compra Ventas 2, Logísticos 2, Temporal 0; list titled "Todos los atributos" with 4 rows |
| B2 | FR-002, FR-004 | Choose "Logísticos", then "Compra Ventas" | Peso, Volumen; then Costo, Peso; "Peso" opens the same detail (same ID) from both |
| B3 | FR-002 | Choose "Sin grupo" | Only "Color" |
| B4 | FR-008 | Choose "Temporal" | "Este grupo todavía no tiene atributos." with the hint |
| B5 | FR-009 | Open "Peso", then choose "Sin grupo" | The detail of "Peso" stays open |
| B6 | US2, FR-005 | In "Logísticos" type "vol", then type filter "Moneda" | "Volumen"; then no match |
| B7 | FR-006 | In "Logísticos" search "costo" | "Ningún atributo de «Logísticos» coincide…", "1 coincidencia en otros grupos."; the button chooses "Todos los atributos" and shows "Costo" with the search kept |
| B8 | FR-007 | Type "comp" in "Buscar grupo" | Todos, Sin grupo, Compra Ventas only |
| B9 | US3, FR-011, FR-012 | Open "Color" → "Agregar a un grupo": Temporal → "Agregar" | Confirmation; "Grupos" lists Temporal; panel Sin grupo 0, Temporal 1 |
| B10 | FR-011 | "Quitar" next to Temporal → "Sí, quitar del grupo" | Confirmation; "Sin grupos"; "Color" still exists, configuration unchanged; panel back to Sin grupo 1 |
| B11 | FR-012 | With "Logísticos" chosen, open "Volumen", remove it from Logísticos | It leaves the list; detail stays open; Logísticos 1 |
| B12 | FR-013, D-9 | Open "Peso" → "Eliminar" | Refusal names Logísticos and Compra Ventas and points to the "Grupos" section; only "Cancelar" |
| B13 | FR-016 | Restart the frontend without `VITE_DEV_PERMISSIONS` | Panel and groups listed; no "Agregar", "Quitar", "Nuevo atributo", "Editar", "Eliminar" |
| B14 | FR-008 | Stop the backend, reload the page | List and panel show their connection errors with "Reintentar"; after restarting the backend, "Reintentar" recovers |
| B15 | FR-008 | Create group "Prueba"; on "Atributos" choose it; in a second tab delete "Prueba"; in the first tab add "Color" to "Logísticos" (triggers a reload), then remove it again | After the reload the list shows "Todos los atributos" and "Prueba" is gone from the panel |
| B16 | FR-014 | Browser at 1440 px, 1024 px, 768 px | Three columns; then the panel collapsed above list and detail ("Grupo: …" toggle); no horizontal scroll |
| B17 | US4, FR-015 | "Grupo de Atributos" → "Temporal" → "Eliminar grupo" → "Cancelar", then again → "Sí, eliminar grupo" | Nothing changes on "Cancelar"; deleted after confirming |
| B18 | FR-015 | "Logísticos" → "Eliminar grupo" | Explanation with 2 attributes, only "Cancelar"; nothing sent (network tab) |
| B19 | FR-015 | "Compra Ventas" → "Agregar atributos", search "vol", check Volumen, search "col", check Color | Both stay checked; "Agregar seleccionados (2)" adds both |
| B20 | FR-017 | Create, edit and delete an ungrouped attribute; check its field preview | Same behavior and messages as features 004, 007 and 008 |

## Scope checks

- `git diff --stat main` lists only `specs/009-attribute-group-organization/`, the new
  `frontend/src/attributes/attributeBrowsing*`, `AttributeGroupPanel*`, `AttributeGroupsSection*`
  files, and `frontend/src/pages/AttributesPage*` and `AttributeGroupsPage*`.
- No change in `backend/`, `frontend/src/api/`, `frontend/src/components/ui/`, `navigation.ts`, the
  attribute form or preview files, LOV Management files, `package.json`.
