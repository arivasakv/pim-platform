# Quickstart: List-Value Filters per Taxonomy Level

**Feature**: [spec.md](spec.md) | **Contracts**: [API](contracts/taxonomy-value-filters.openapi.yaml),
[UI](contracts/taxonomy-value-filters-ui.md)

Validation guide for the finished feature. Windows PowerShell from the repository root.

## Prerequisites

- Local MongoDB; backend `cd backend; .\gradlew.bat bootRun`; frontend with
  `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`, `npm run dev` → http://localhost:5173/#/taxonomia
- For API-only runs that must not touch `pim`: `$env:PIM_MONGODB_URI='mongodb://localhost:27017/pim-quickstart-012'`
  and `$env:SERVER_PORT='8091'` before `bootRun`; drop that database afterwards.

## Automated checks

```powershell
cd backend; .\gradlew.bat clean build
cd ..\frontend; npm run lint; npm run format:check; npm test; npm run build
```

## Example data

- List **Colores**: Rojo (0001), Azul (0002), Verde (0003), Negro (0004), Blanco (0005), Amarillo (0006),
  Rosa (0007).
- Attribute **Color**: Lista de valores, "Colores", selección única. Attribute **Largo de manga**: texto.
- Hierarchy: Ropa (Color) › Ropa de hombre › Camisas (Largo de manga) › Camisas formales; Ropa de hombre ›
  Pantalones; Ropa › Ropa de mujer; Calzado (Color).

## Scenarios

| # | Steps | Expected |
|---|---|---|
| 1 | Open "Ropa de hombre" | Section «Listas de valores en este nodo»: Color «7 de 7», «Sin filtro»; Largo de manga not listed |
| 2 | **Filtrar valores de Color** → include Azul, Negro, Blanco, Rojo → **Guardar filtro** | «Filtro de «Color» guardado en «Ropa de hombre»: 4 de 7 valores permitidos.»; row «4 de 7», «Filtro en este nodo» (US1) |
| 3 | Open "Camisas formales", "Pantalones", "Ropa de mujer" | 4 de 7, «Filtro heredado de «Ropa de hombre»»; Ropa de mujer 7 de 7 (US2 sc. 1) |
| 4 | On "Camisas", **Filtrar valores** | Left column offers only Rojo, Azul, Negro, Blanco; inherited note «Arriba se permiten 4 de 7 valores…» (US2 sc. 2) |
| 5 | Include Azul and Blanco → save; open "Camisas formales" | 2 de 7, «Filtro heredado de «Camisas»» (US2 sc. 3) |
| 6 | API: PUT filter on Camisas with `["0003"]` | 409 `VALUE_EXCLUDED_ABOVE` naming «Verde» and «Ropa de hombre» (US2 sc. 4) |
| 7 | Narrow "Ropa de hombre" to Azul, Negro; open "Camisas" filter | Camisas 1 de 7 (Azul); Blanco shown «Excluido por «Ropa de hombre»» (US2 sc. 5) |
| 8 | Narrow "Ropa de hombre" to Negro only; open "Camisas" | «Ningún valor permitido»; preview warns naming the filters (US2 sc. 6) |
| 9 | Remove the filter of "Camisas" | Camisas and Camisas formales follow «Ropa de hombre» again (US3 sc. 2) |
| 10 | Move "Camisas" under "Ropa de mujer" | 7 de 7 (or its own filter only) (US3 sc. 3) |
| 11 | "Calzado" filter Negro, Blanco; Camisas own filter Azul, Blanco; move Camisas under Calzado | Camisas 1 de 7 (Blanco) (US3 sc. 4) |
| 12 | **Vista previa de Color** on "Camisas formales" | Select with only the allowed values (US4) |
| 13 | API: PUT with `[]`, with `["9999"]`, on Largo de manga, on a node without Color | 400 `INVALID_FILTER_VALUES`, 422 `VALUE_NOT_IN_LIST`, 422 `ATTRIBUTE_NOT_LIST`, 404 `ATTRIBUTE_NOT_ON_NODE` |
| 14 | Delete a leaf node with its own filter (no children, no direct attributes) | Node deleted; its filter gone (research R-06) |
| 15 | Without `VITE_DEV_PERMISSIONS`; API without header | No **Filtrar valores**; PUT/DELETE → 403 |
| 16 | A list of 50,000 values: open the filter dialog, search | Results in under 2 s (SC-005) |
| 17 | 768 px width | Columns stack; no horizontal scroll |
