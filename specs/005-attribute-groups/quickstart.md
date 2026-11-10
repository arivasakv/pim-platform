# Quickstart: Attribute Management – Attribute Groups

**Feature**: `005-attribute-groups` | **Spec**: [spec.md](spec.md) |
**Contract**: [contracts/attribute-groups.openapi.yaml](contracts/attribute-groups.openapi.yaml) |
**Data model**: [data-model.md](data-model.md)

Commands are for Windows PowerShell.

## Prerequisites

- As for feature 004: Java 21, Node, local MongoDB (feature 002's catalog), `frontend/.env` with
  `VITE_API_BASE_URL` and `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`.
- Automated suites: backend `.\gradlew.bat clean build`; frontend `npm run lint`,
  `npm run format:check`, `npm test`, `npm run build`.
- Manual run: `.\gradlew.bat bootRun` and `npm run dev`. Attributes, groups and system IDs are stored in MongoDB and survive restarts (ADR 0003); use a fresh
  database to repeat the scenarios exactly. First create three attributes with feature 004 (e.g. "Peso", "Volumen", "Precio base";
  they get `ATR000001`–`ATR000003`).

## API scenarios

Use `X-Pim-Permissions: ATTRIBUTE_CREATE` for changes unless stated.

| # | Story / requirement | Request | Expected |
|---|---|---|---|
| 1 | FR-015 | `GET /api/v1/attribute-groups` | 200, `total` 0 |
| 2 | R-03 | `GET /api/v1/attributes` | 200, the three attributes sorted by display name |
| 3 | US1 | `POST /api/v1/attribute-groups` `{"name":"Logísticos"}` | 201, `Location`, `attributeCount` 0, an `id` |
| 4 | US1, FR-003 | `POST` `{"name":" logísticos "}`; `{"name":"  "}`; no header | 409 `GROUP_NAME_IN_USE`; 400 `INVALID_GROUP_NAME`; 403 `ATTRIBUTE_CREATE_NOT_PERMITTED` |
| 5 | US2 | `POST …/{logisticos}/attributes` `{"attributeIds":["ATR000001","ATR000002"]}` | 200, `added` both, `attributeCount` 2 |
| 6 | US2, FR-007 | Create "Compra Ventas"; add `ATR000001` to it | 200; `GET /api/v1/attribute-groups?attributeId=ATR000001` lists both groups |
| 7 | US2, FR-008 | Add `atr000001` again to "Logísticos" | 200, `alreadyInGroup` `["ATR000001"]`, count still 2 |
| 8 | US2, FR-011 | Add `["ATR000003","ATR999"]` | 422 `ATTRIBUTE_NOT_FOUND`, `ATR000003` not added |
| 9 | US2, FR-010 | `GET /api/v1/attributes/ATR000001` before and after 5–7 | Same data |
| 10 | US3 | `DELETE …/{logisticos}/attributes/ATR000001` | 204; still in "Compra Ventas"; `GET /api/v1/attributes/ATR000001` 200 |
| 11 | US3 | Repeat 10 | 404 `ATTRIBUTE_NOT_IN_GROUP` |
| 12 | US5 | `PUT …/{logisticos}` `{"name":"Logística","description":"Peso y volumen"}` | 200, same `id`, still 1 attribute |
| 13 | US5, FR-005 | `DELETE …/{logisticos}` (has 1 attribute) | 409 `GROUP_NOT_EMPTY` |
| 14 | US5 | Remove its last attribute, then `DELETE` | 204; group gone |
| 15 | US4 | `GET /api/v1/attribute-groups/{compraVentas}` | 200 with members (display name, data type, system ID) |
| 16 | 404 | `GET /api/v1/attribute-groups/unknown` | 404 `GROUP_NOT_FOUND` |
| 17 | D-1 | Restart the backend; `GET /api/v1/attribute-groups/{compraVentas}`; create a new attribute | Group and members still there; the new attribute gets the next unused ID (not `ATR000001`) and is in no group |

## UI scenarios (browser, owner check)

| # | Steps | Expected |
|---|---|---|
| U1 | Open "Grupos de atributos" | Empty state with a create form |
| U2 | Create "Logísticos" | It appears in the list with 0 attributes |
| U3 | Open it, add "Peso" and "Volumen" from the picker | Both listed; count 2; picker no longer offers them |
| U4 | Remove "Peso" | Gone from the group; "Peso" still exists (visible in the picker again) |
| U5 | Rename to "Logística" | Name updated, attributes kept |
| U6 | Try to delete while it has "Volumen" | Spanish message: remove the attributes first |
| U7 | Remove "Volumen", delete the group | Group gone from the list |

## Scope checks

- `git diff --stat main…` shows no change to feature 002 code. Feature 003 and 004 changes are limited
  to their storage (MongoDB implementations and documents; the in-memory classes moved to the test tree
  as fakes), `ObjectTypeConfigurationRepository.contains`, defaults inserted only where missing,
  `AttributeRepository`/`AttributeService.findAll`, the additive list endpoint `GET /api/v1/attributes`
  (R-03) with its contract bump to v1.1.0, and collection cleanup in their integration tests (no
  assertion changes). See ADR 0003 and tasks T053.
- No new dependency in `backend/build.gradle` or `frontend/package.json`.
