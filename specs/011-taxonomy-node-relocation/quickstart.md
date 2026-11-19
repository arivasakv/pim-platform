# Quickstart: Taxonomy Node Relocation

**Feature**: [spec.md](spec.md) | **Contracts**: [API](contracts/taxonomy-move.openapi.yaml),
[UI](contracts/taxonomy-move-ui.md)

Validation guide for the finished feature. Commands are Windows PowerShell from the repository root.

## Prerequisites

- Local MongoDB on `localhost:27017` (root README, "Local setup").
- Backend: `cd backend; .\gradlew.bat bootRun` → http://localhost:8080
- Frontend: `frontend\.env` with `VITE_API_BASE_URL=http://localhost:8080` and
  `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`; `cd frontend; npm run dev` → http://localhost:5173/#/taxonomia

## Automated checks

```powershell
cd backend; .\gradlew.bat build          # compile (-Werror), Spotless, all tests (needs MongoDB)
cd ..\frontend; npm run lint; npm run format:check; npm test; npm run build
git status --short -- backend/src/main/java/com/pim/backend/attribute backend/src/main/java/com/pim/backend/attributegroup backend/src/main/java/com/pim/backend/lov
# prints nothing: no other domain changed (FR-018)
```

## Example hierarchy

Build it on the page (or with the API of feature 010), then assign the attributes shown:

```text
Ropa                      (Color, Material, Marca)
├── Ropa de hombre        (Género)
│   ├── Camisas           (Largo de manga)
│   │   └── Camisas formales
│   └── Pantalones        (Talla de cintura)
└── Ropa de mujer         (Género, Corte)
Calzado                   (Material de suela)
```

## Scenarios

| # | Steps | Expected |
|---|---|---|
| 1 | Open "Camisas" → **Mover** → search "mujer" → choose "Ropa de mujer" → **Continuar** | Confirmation: «¿Mover «Camisas» a «Ropa de mujer»?», «Nueva ubicación: Ropa › Ropa de mujer › Camisas», «Se moverán 2 nodos: «Camisas» y 1 descendiente.» (US1 sc. 2) |
| 2 | **Sí, mover nodo** | «Nodo «Camisas» movido a «Ropa de mujer». Se movieron 2 nodos.»; tree shows Camisas › Camisas formales under Ropa de mujer; same system ID, description, children, direct attributes (US1 sc. 1, 3; SC-004) |
| 3 | Open "Camisas" | Inherited: Color, Material, Marca (from Ropa), Género, Corte (from Ropa de mujer); direct: Largo de manga. Nothing from "Ropa de hombre" (US3; SC-001) |
| 4 | Assign "Género" directly to "Camisas"; open it | Género once, **Directo**, «También se hereda de «Ropa de mujer»» (US3 sc. 4; FR-011) |
| 5 | Move "Camisas" under "Calzado"; open "Camisas formales" | Level 3, path «Calzado › Camisas › Camisas formales»; inherited: Material de suela (Calzado), Género and Largo de manga (Camisas); Color, Material, Marca, Corte gone (US3 sc. 2) |
| 6 | Open "Ropa de hombre" and "Pantalones" | Unchanged effective attributes (US3 sc. 3; FR-012) |
| 7 | Open "Ropa" → **Mover** | Its descendants and itself are not offered; «Primer nivel» not offered (it is top level) (US2 sc. 1) |
| 8 | API: `POST /api/v1/taxonomy-nodes/{Ropa}/move` with `{ "parentId": "{Camisas formales}" }` | 409 `NODE_MOVE_INTO_OWN_BRANCH`; tree unchanged (US2 sc. 1; FR-016) |
| 9 | API: move "Pantalones" to its current parent | 409 `NODE_ALREADY_THERE` (US2 sc. 2) |
| 10 | Create "Camisas" under "Ropa de hombre" again; try to move it under "Calzado" (which already has "Camisas") | 409 `NODE_NAME_IN_USE`: «Ya existe un nodo «Camisas» en «Calzado». …» (US2 sc. 3) |
| 11 | Move "Ropa de mujer" → **Primer nivel (sin nodo padre)** | Top-level, level 1, inherits nothing; its descendants' levels decrease by 1 (US4) |
| 12 | Remove `VITE_DEV_PERMISSIONS`, restart `npm run dev` | No **Mover** button; API without the header → 403 (US1 sc. 5) |
| 13 | Stop MongoDB, try a move | Connection message; after restarting, the tree is as before (FR-003) |
| 14 | At 768 px width | Move panel and confirmation fit without horizontal scroll (FR-013) |

## API examples

```powershell
$h = @{ 'X-Pim-Permissions' = 'ATTRIBUTE_CREATE'; 'Content-Type' = 'application/json' }
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/taxonomy-nodes/CLS000003/move -Headers $h -Body '{"parentId":"CLS000006"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/taxonomy-nodes/CLS000006/move -Headers $h -Body '{"parentId":null}'
```

Replace the IDs with the ones shown on the page.
