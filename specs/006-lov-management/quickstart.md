# Quickstart: List of Values Management

**Feature**: `006-lov-management` | **Spec**: [spec.md](spec.md) |
**Contract**: [contracts/lists-of-values.openapi.yaml](contracts/lists-of-values.openapi.yaml) |
**Data model**: [data-model.md](data-model.md)

Commands are for Windows PowerShell.

## Prerequisites

- As for features 004 and 005: Java 21, Node, local MongoDB, `frontend/.env` with `VITE_API_BASE_URL`
  and `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`.
- Automated suites: backend `.\gradlew.bat clean build`; frontend `npm run lint`,
  `npm run format:check`, `npm test`, `npm run build`.
- Manual run: `.\gradlew.bat bootRun` and `npm run dev`. Lists are stored in MongoDB and survive
  restarts; use a fresh database to repeat the scenarios exactly (list IDs then start at `LOV000001`).
- Test files (create them locally; they aren't committed):
  - `colores.csv`: header `ID,Etiqueta,Equivalencia`, then 500 rows `0001,Color 1,` … `0500,Color 500,`.
  - `colores-errores.csv`: the same, with row 4 repeating the ID of row 9 and row 7 without Etiqueta.
  - `colores-update.csv`: `ID;Etiqueta;Equivalencia` then `0001;AZUL;` and `;Verde;VERDE` (semicolon delimiter).

## API scenarios

Use `X-Pim-Permissions: ATTRIBUTE_CREATE` for changes unless stated.

| # | Story / requirement | Request | Expected |
|---|---|---|---|
| 1 | US2 | `GET /api/v1/lists-of-values` | 200, `total` 0 |
| 2 | US1, FR-001 | `POST /api/v1/lists-of-values` `{"name":"Color"}` | 201, `Location`, `systemId` `LOV000001`, `valueCount` 0 |
| 3 | FR-003, FR-002 | `POST` `{"name":" color "}`; `{"name":"  "}`; `{"name":"Talla"}` without header | 409 `LIST_NAME_IN_USE`; 400 `INVALID_LIST_NAME`; 403 `ATTRIBUTE_CREATE_NOT_PERMITTED` |
| 4 | US1, FR-006–FR-009 | `POST …/LOV000001/values` `{"id":"0001","label":"Azul"}`; then `{"label":"Rojo"}` | 201 `0001`; 201 with generated `id` `0002` |
| 5 | FR-008 | `POST …/values` `{"id":"0001","label":"Otro"}`; `{"id":"a b","label":"X"}`; `{"id":"9"}` | 409 `VALUE_ID_IN_USE`; 400 `INVALID_VALUE_ID`; 400 `REQUIRED` (label) |
| 6 | FR-008 | Create list "Talla"; add `{"id":"0001","label":"Chica"}` | 201 (same ID in another list is allowed) |
| 7 | FR-026 | Feature 004: `POST /api/v1/attributes` LOV type, `MULTIPLE`, `listOfValuesId` `LOV000001`; then another with `SINGLE`; then one with `LOV999999` | 201; 201; 422 `LIST_OF_VALUES_NOT_FOUND` |
| 8 | US2, FR-005, FR-014 | `GET /api/v1/lists-of-values/lov000001` | 200; values `0001`, `0002`; `attributes` lists both attributes with their selection modes |
| 9 | US3, FR-010, FR-012 | `PUT …/values/0001` `{"label":"AZUL"}` | 200; `id` still `0001` |
| 10 | US3, FR-004 | `DELETE /api/v1/lists-of-values/LOV000001` | 409 `LIST_IN_USE` naming 2 attributes |
| 11 | US3, FR-013 | `DELETE …/LOV000001/values/0002`; repeat | 204; 404 `VALUE_NOT_FOUND` |
| 12 | US5, FR-023 | `PUT …/LOV000001` `{"name":"Color","equivalencesEnabled":true}`; `PUT …/values/0001` `{"label":"AZUL","equivalence":"BLUE"}`; turn off; `GET` | Equivalence saved; after turning off, `equivalencesEnabled` false and `equivalence` still `BLUE` |
| 13 | US4, FR-011 | `GET /api/v1/lists-of-values/import-template` | 200 `text/csv`, header `ID,Etiqueta,Equivalencia` |
| 14 | US4, FR-016 | Upload `colores-errores.csv` to list "Talla" (`POST …/values/import`, multipart `file`) | 422 `INVALID_IMPORT`, `rowErrors` for rows 4, 9 (`DUPLICATE_ID_IN_FILE`) and 7 (`REQUIRED`); "Talla" unchanged |
| 15 | US4, SC-003 | Upload `colores.csv` to a new empty list | 200, `added` 500, `updated` 0, in well under a minute |
| 16 | US4, OQ-005 | Upload `colores-update.csv` to that list | 200, `added` 1, `updated` 1; `0001` is "AZUL"; the new value got the next generated ID |
| 17 | US3, FR-004 | Create list "Temporal", delete it | 204; `GET` → 404 `LIST_NOT_FOUND` |
| 18 | FR-019 | In `mongosh`: `db.lov_changes.find().sort({at:-1})` | Records for the deletions (11, 17) and the imports (15, 16) |
| 19 | Persistence | Restart the backend; `GET /api/v1/lists-of-values`; create a list | Lists and values still there; the new list gets the next unused ID |

## UI scenarios (browser, owner check)

| # | Steps | Expected |
|---|---|---|
| U1 | Open the side navigation | "Listas de valores" right after "Grupos de atributos" |
| U2 | Open it with no lists | Empty state with a create form |
| U3 | Create "Color", add `0001` "Azul" and a value without ID | Both listed; the second has ID `0001`+1 |
| U4 | Edit "Azul" → "AZUL" | Label changes; the ID field isn't editable |
| U5 | Delete a value | Confirmation dialog; cancelling keeps it; confirming removes it |
| U6 | Download the template, upload a file with errors | Report per row and column; no value added |
| U7 | Turn on equivalences, enter one, turn off | Column shown, then hidden; turning on shows it again |
| U8 | Feature 004 "Crear atributo", type "Lista de valores" | A "Lista de valores" select offers "Color"; the attribute saves; "Color" then shows it among its attributes |
| U9 | Try to delete "Color" | Spanish message: used by attributes |
| U10 | Without `ATTRIBUTE_CREATE` | Lists and values visible; no create, edit, delete or upload controls |

## Scope checks

- No change to features 002, 003 or 005 code.
- Feature 004 changes are limited to:
  - `AttributeService.findLinkedToList` and `runIfNoAttributeLinksTo`, and its list check moved
    inside its lock (R-04);
  - `EmptyListOfValuesCatalog` moved to the test tree (R-03);
  - the list select in `AttributeCreationPage` (R-14).
  Feature 004's existing tests keep their assertions; the page test's fetch stub also answers
  `GET /api/v1/lists-of-values`.
- No new dependency in `backend/build.gradle` or `frontend/package.json`.
- No product data read or changed (FU-1); no attribute type or list changed (FU-2).
