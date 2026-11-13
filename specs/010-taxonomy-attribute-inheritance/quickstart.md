# Quickstart: Attribute Inheritance through the Product Taxonomy

**Feature**: `010-taxonomy-attribute-inheritance` | **Spec**: [spec.md](spec.md) |
**API**: [contracts/taxonomy.openapi.yaml](contracts/taxonomy.openapi.yaml) |
**UI**: [contracts/taxonomy-ui.md](contracts/taxonomy-ui.md)

Commands are for Windows PowerShell.

## Prerequisites

- Constitution 2.5.0 and ADR 0003 amendment in place (plan D-2).
- Local MongoDB on 27017 (as features 005/006); backend `.\gradlew.bat bootRun` in `backend/`;
  frontend `npm run dev` in `frontend/` with `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`.
- Attributes (create on "Atributos"): Color, Material, Marca, Género, Largo de manga, Talla de cintura,
  Temporada.
- Automated suites: backend `.\gradlew.bat clean build`; frontend `npm run lint`,
  `npm run format:check`, `npm test`, `npm run build`.

## API scenarios (PowerShell)

```powershell
$api = 'http://localhost:8080/api/v1/taxonomy-nodes'
$h = @{ 'X-Pim-Permissions' = 'ATTRIBUTE_CREATE' }
$ropa = Invoke-RestMethod $api -Method Post -Headers $h -ContentType 'application/json' -Body '{"name":"Ropa"}'
$hombre = Invoke-RestMethod $api -Method Post -Headers $h -ContentType 'application/json' -Body (@{ name = 'Ropa de hombre'; parentId = $ropa.systemId } | ConvertTo-Json)
Invoke-RestMethod "$api/$($hombre.systemId)" | Select-Object systemId, level, path, effectiveAttributes
```

| # | Requirement | Steps | Expected |
|---|---|---|---|
| A1 | FR-002, FR-003 | Create "Ropa" | 201, `systemId` `CLS…`, `level` 1, empty `path` and `effectiveAttributes` |
| A2 | FR-004 | Create " ropa " at top level | 409 `NODE_NAME_IN_USE` |
| A3 | FR-008 | `POST $api/{Ropa}/attributes` with Color, Material, Marca IDs | 200, `added` = 3 |
| A4 | FR-012–FR-016 | Build the example tree and assignments; `GET` Camisas | Color, Material, Marca (INHERITED, origin Ropa), Género (INHERITED, origin Ropa de hombre), Largo de manga (DIRECT) in that order |
| A5 | FR-010 | `DELETE $api/{Camisas}/attributes/{Color}` | 409 `ATTRIBUTE_NOT_DIRECT` naming Ropa |
| A6 | FR-006 | `DELETE $api/{Ropa}` | 409 `NODE_HAS_CHILDREN` |
| A7 | FR-021 | `DELETE /api/v1/attributes/{Color}` | 409 `ATTRIBUTE_ON_TAXONOMY_NODES` with `nodes` |
| A8 | FR-017 | Any change without the header | 403 `TAXONOMY_CHANGE_NOT_PERMITTED` |
| A9 | Persistence | Restart the backend; `GET $api` | Same nodes and assignments |

## Browser scenarios

Open "Taxonomía".

| # | Story / requirement | Steps | Expected |
|---|---|---|---|
| B1 | US1 | Empty hierarchy | "Todavía no hay nodos en la jerarquía." and the hint |
| B2 | US1 | "Nuevo nodo de primer nivel" → Ropa; select it → "Agregar nodo hijo" → Ropa de hombre; under it Camisas and Pantalones | Tree nested; confirmations with `CLS…` IDs; "Nivel 3" on Camisas; path "Ropa › Ropa de hombre" |
| B3 | US2 | Ropa → "Agregar atributos" → search, pick Color, Material, Marca → "Asignar seleccionados (3)" | "3 atributos asignados a «Ropa»."; table with three "Directo" rows; tree count 3 |
| B4 | US2 | Género on Ropa de hombre; Largo de manga on Camisas; Talla de cintura on Pantalones | Counts 1, 1, 1 |
| B5 | US3 | Open Camisas | Table as in spec US3; summary "1 directo · 4 heredados"; badges with words; origins as links |
| B6 | US3 | Open Pantalones | No "Largo de manga" |
| B7 | US3 | Click origin "Ropa" in a row; click "Ropa de hombre" in the path | Those nodes open |
| B8 | US3 | Create "Camisas formales" under Camisas and open it | "Este nodo no tiene atributos asignados directamente; hereda 5 de sus niveles superiores." |
| B9 | US4 | Assign Temporada to Ropa; open Camisas formales | Temporada inherited from Ropa |
| B10 | US4 | Assign Género directly to Camisas | One "Género" row, "Directo", "También se hereda de «Ropa de hombre»" |
| B11 | US2 | On Ropa, "Quitar" Temporada → confirm | Gone from Ropa and from every descendant |
| B12 | US1 | Rename Ropa to "Ropa y accesorios"; open Camisas | Origins and path show the new name |
| B13 | US1 | "Eliminar" on Ropa de hombre | Explanation (children), only "Cancelar" |
| B14 | US1 | "Eliminar" on Camisas formales → confirm | Deleted |
| B15 | FR-021 | "Atributos" → Color → "Eliminar" → confirm | Refusal naming "Ropa y accesorios" |
| B16 | FR-017 | Restart the frontend without `VITE_DEV_PERMISSIONS` | Tree and tables visible; no change controls |
| B17 | FR-018 | 1440 px and 768 px | Two columns, then one; no horizontal scroll |

## Scope checks

- Backend changes only in the new `taxonomy` package and the `attribute` items listed in plan.md.
- Frontend changes only in `api/taxonomyApi*`, `taxonomy/`, `pages/TaxonomyPage*`, `navigation.ts`,
  `App.tsx` and their tests.
- No change in attribute groups, LOV, product search, the attribute form or preview, design-system
  components, `package.json`, `build.gradle`.
