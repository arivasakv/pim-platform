# Quickstart: Unlink a List of Values from an Attribute

**Feature**: [spec.md](spec.md) | **Contracts**: [API](contracts/list-unlink.openapi.yaml),
[UI](contracts/list-unlink-ui.md) | **Data model**: [data-model.md](data-model.md)

Validation guide for the finished feature. Windows PowerShell from the repository root.

## Prerequisites

- Local MongoDB; backend `cd backend; .\gradlew.bat bootRun`; frontend with
  `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`, `npm run dev` → http://localhost:5173
- For API-only runs: `$env:PIM_MONGODB_URI='mongodb://localhost:27017/pim-quickstart-014'`,
  `$env:SERVER_PORT='8091'`; drop that database afterwards.

## Automated checks

```powershell
cd backend; .\gradlew.bat clean build
cd ..\frontend; npm run lint; npm run format:check; npm test; npm run build
```

## Example data

- List **Tallas** (CH, MD, GR) and list **Colores** (Rojo, Azul).
- Attribute **Talla de calzado**: Lista de Valores (LOV's), «Tallas», selección única; in group «Calzado»;
  assigned to taxonomy node «Calzado».
- Attribute **Color**: Lista de Valores (LOV's), «Colores»; filters on nodes «Ropa de hombre» and «Camisas».

## Scenarios

| # | Steps | Expected |
|---|---|---|
| 1 | Attributes → «Talla de calzado» → Editar → **Desvincular lista** | Unlink mode: ID and name read only; «Tipo de dato» without the list choice; «Qué cambiará» names «Tallas» and says there are no filters |
| 2 | Choose «Numérico Decimal (Float)», 1 decimal, 20–32 → **Desvincular y guardar** | «Lista desvinculada de «Talla de calzado». Ahora es de tipo Numérico Decimal (Float).»; same ID, name, group «Calzado», node «Calzado» (US1 sc. 1, FR-004) |
| 3 | Open list «Tallas» | No linked attributes; **Eliminar lista** now works (US1 sc. 3) |
| 4 | Lists → «Colores» → **Desvincular Color** | Attributes page opens «Color» in unlink mode (FR-006) |
| 5 | Review | «Se eliminarán los filtros de valores de este atributo en 2 nodos de taxonomía: Camisas, Ropa de hombre» (US2) |
| 6 | Choose «Texto (String)», confirm; open «Ropa de hombre» | «Color» is text; the node no longer lists filters for it; other attributes' filters unchanged (FR-008) |
| 7 | Unlink mode, choose «Moneda (Currency)» without a currency → confirm | Message under «Moneda»; attribute unchanged (US1 sc. 2) |
| 8 | Start unlink; in another tab add a filter for the attribute; confirm | «El atributo o sus filtros cambiaron desde la revisión…»; review reloads (FR-011) |
| 9 | **Cancelar** in unlink mode | Back to the edit form; nothing changed (US1 sc. 4) |
| 10 | API: GET review of a text attribute; POST with `dataType: LIST_OF_VALUES`; POST without permission | 422 ATTRIBUTE_NOT_LIST_OF_VALUES; 422 UNLINK_TO_LIST_NOT_ALLOWED; 403 (FR-012, FR-007) |
