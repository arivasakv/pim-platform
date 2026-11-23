# Quickstart: Bulk Load of Lists of Values (Carga Masiva de LOV)

**Feature**: [spec.md](spec.md) | **Contracts**: [API](contracts/lov-bulk-load.openapi.yaml),
[UI](contracts/bulk-load-ui.md) | **Data model**: [data-model.md](data-model.md)

Validation guide for the finished feature. Windows PowerShell from the repository root.

## Prerequisites

- Local MongoDB; backend `cd backend; .\gradlew.bat bootRun`; frontend with
  `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`, `npm run dev` → http://localhost:5173/#/listas-de-valores
- For API-only runs that must not touch `pim`:
  `$env:PIM_MONGODB_URI='mongodb://localhost:27017/pim-quickstart-013'` and `$env:SERVER_PORT='8091'`
  before `bootRun`; drop that database afterwards.

## Automated checks

```powershell
cd backend; .\gradlew.bat clean build
cd ..\frontend; npm run lint; npm run format:check; npm test; npm run build
```

## Example data

- Existing list **Color** (created with «Crear LOV»): ROJ «Rojo», AZU «Azul».
- File `listas.xlsx` (first sheet) and the same rows as `listas.csv`:

  | Lista | ID | Etiqueta | Equivalencia |
  |---|---|---|---|
  | Color | AZU | Azul marino | |
  | Color | VER | Verde | GREEN |
  | Talla | 0001 | Chica | S |
  | talla | 0002 | Mediana | M |
  | Talla | | Grande | |

  In Excel, type `0001` and `0002` in a column formatted as text (as in the template).

## Scenarios

| # | Steps | Expected |
|---|---|---|
| 1 | Open the page with the permission | Header buttons **Crear LOV** and **Carga Masiva de LOV**; without the permission neither (US3 sc. 1, US2 sc. 5) |
| 2 | **Crear LOV** → name «Color» → **Crear LOV** | List Color created as before (US3 sc. 2) |
| 3 | **Carga Masiva de LOV** → **Descargar plantilla Excel** / **CSV** | Files with Lista, ID, Etiqueta, Equivalencia and the row Color, ROJ, Rojo, RED; in Excel the ID column is text (US3 sc. 3) |
| 4 | Choose `listas.xlsx` → **Revisar archivo** | Summary: Color «Actualizar», 1 nuevo, 1 actualizado; Talla «Crear», 3 nuevos; «Se creará 1 lista y se actualizará 1. Se agregarán 4 valores y se actualizará 1.»; «Nada se ha guardado todavía.» Lists unchanged (FR-010) |
| 5 | **Cancelar** | Summary gone; lists unchanged |
| 6 | Again **Revisar archivo** → **Confirmar carga** | «Carga aplicada: 1 lista creada, 1 actualizada; 4 valores agregados y 1 actualizado.»; overview shows Talla with 3 values; Color: ROJ, AZU «Azul marino», VER «Verde» (US1 sc. 1–4) |
| 7 | Open Talla | Values 0001 Chica, 0002 Mediana (leading zeros kept), a generated ID «Grande»; one list although rows wrote «Talla» and «talla»; equivalence column on (R-08) |
| 8 | Load `listas.csv` with the same rows into a fresh database | Same lists and values as scenario 6 (SC-004) |
| 9 | Load a file with a row without Etiqueta, two «Color» rows with ID ROJ, and a 90-character list name | Table with every problem: Etiqueta REQUIRED, both ROJ rows DUPLICATE_ID_IN_FILE (naming «Color»), Lista INVALID_LIST_NAME; nothing saved (US2 sc. 1–2) |
| 10 | Check a file, then rename list Color in another tab, then **Confirmar carga** | «Las listas cambiaron desde la revisión. Vuelve a revisar el archivo.»; nothing saved (FR-010) |
| 11 | Upload a `.xls`, a PDF, a file without the Lista column, a 6 MB file | «Archivo no válido» messages saying what to fix; nothing saved (US2 sc. 4) |
| 12 | Open list Color → its own «Carga masiva» with a CSV of ID, Etiqueta | Works as in feature 006 (FR-004) |

## API checks (port 8091)

```powershell
# check: 200 with summary and checkToken; nothing saved
curl.exe -s -H "X-Pim-Permissions: ATTRIBUTE_CREATE" -F "file=@listas.csv" http://localhost:8091/api/v1/lists-of-values/bulk-load/check
# apply with the token: 200 result; with a wrong token: 409 BULK_LOAD_OUTDATED; without permission: 403
curl.exe -s -H "X-Pim-Permissions: ATTRIBUTE_CREATE" -F "file=@listas.csv" -F "checkToken=<token>" http://localhost:8091/api/v1/lists-of-values/bulk-load
```
