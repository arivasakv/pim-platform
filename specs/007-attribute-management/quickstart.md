# Quickstart: Attribute Management – Consult, Edit and Delete

**Feature**: `007-attribute-management` | **Spec**: [spec.md](spec.md) |
**Contract**: [contracts/attributes-v1.2.openapi.yaml](contracts/attributes-v1.2.openapi.yaml) |
**Data model**: [data-model.md](data-model.md)

Commands are for Windows PowerShell.

## Prerequisites

- As for features 004–006: Java 21, Node, local MongoDB, `frontend/.env` with `VITE_API_BASE_URL` and
  `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE`.
- Automated suites: backend `.\gradlew.bat clean build`; frontend `npm run lint`,
  `npm run format:check`, `npm test`, `npm run build`.
- Manual run: `.\gradlew.bat bootRun` and `npm run dev`. Use a fresh database to repeat the scenarios
  exactly (attribute IDs then start at the feature 003 default for attributes).
- Setup data (through the existing pages or API): list "Color" with one value; attributes "Peso"
  (Numérico, decimal, 2 decimals), "Color" (Lista de valores, multiple, list "Color"), "Activo"
  (Booleano); group "Logísticos" containing "Peso".

## API scenarios

Use `X-Pim-Permissions: ATTRIBUTE_CREATE` for changes unless stated. `{peso}` etc. are the system IDs
returned at creation.

| # | Story / requirement | Request | Expected |
|---|---|---|---|
| 1 | US1, FR-004 | `GET /api/v1/attributes` | 200, `total` 3, sorted Activo, Color, Peso; Peso has `groups` [Logísticos]; Color has `listOfValuesProperties.listOfValuesName` "Color"; no `updatedAt` |
| 2 | US1, FR-006 | `GET /api/v1/attributes/{peso}` (lower-case ID) | 200, same fields as in 1 |
| 3 | US2, FR-007, FR-008 | `PUT …/{peso}` `{"displayName":"Peso neto","dataType":"NUMERIC","numericProperties":{"numberFormat":"DECIMAL","precision":3}}` | 200; `displayName` "Peso neto", precision 3, same `systemId`, `createdAt`, `groups`; `updatedAt` set |
| 4 | FR-008 | `PUT …/{peso}` with `displayName` " color " | 409 `DISPLAY_NAME_IN_USE`; nothing changed |
| 5 | FR-008 | `PUT …/{peso}` with `displayName` "PESO NETO" | 200 (own name, other letter case) |
| 6 | FR-007 | `PUT …/{peso}` with `precision` 0 | 400 `INVALID_PRECISION` (feature 004 message) |
| 7 | FR-010 | `PUT …/{peso}` with `dataType` "TEXT"; then with `systemId` "OTRO1" | 422 `DATA_TYPE_IMMUTABLE`; 422 `SYSTEM_ID_IMMUTABLE` |
| 8 | FR-018 | `PUT …/{peso}` and `DELETE …/{activo}` without the header | 403 `ATTRIBUTE_CHANGE_NOT_PERMITTED` both; `GET` still 200 without the header |
| 9 | FR-009 | Create list "Talla"; `PUT …/{color}` with `listOfValuesId` of "Talla", `selectionMode` SINGLE | 200; `GET /api/v1/lists-of-values/{talla}` lists the attribute, "Color" list no longer does |
| 10 | FR-009 | `PUT …/{color}` with `listOfValuesId` "LOV999999" | 422 `LIST_OF_VALUES_NOT_FOUND` |
| 11 | US3, FR-014 | `DELETE …/{peso}` | 409 `ATTRIBUTE_IN_GROUPS`, detail names "Logísticos", `groups` [{id, name}]; attribute still there |
| 12 | US3, FR-013 | `DELETE …/{color}` | 204; `GET …/{color}` → 404; list "Talla" still exists with its values and can now be deleted |
| 13 | FR-016 | Create a new attribute without ID | Its ID is not the deleted `{color}` ID |
| 14 | FR-016 | Create an attribute with manual ID `{color}` (`X-Pim-Manual-Id-Permissions: ATTRIBUTE`) | 409 from feature 003 (ID in use) |
| 15 | US3 | Remove "Peso" from "Logísticos" (feature 005), then `DELETE …/{peso}`; repeat | 204; 404 |
| 16 | FR-019 | Restart the backend; `GET /api/v1/attributes` | Edits kept; attributes created before this feature (no `updatedAt`) read normally |

## UI scenarios (browser, owner check)

| # | Steps | Expected |
|---|---|---|
| U1 | Open the side navigation | "Atributos" where "Crear Atributos" was (after "Lista de Valores", before "Grupo de Atributos"); `#/crear-atributo` opens Inicio |
| U2 | Open "Atributos" with no attributes | Empty state with "Nuevo atributo" |
| U3 | "Nuevo atributo", create a text attribute | Feature 004's form and messages; the attribute appears in the list and opens in the detail |
| U4 | Type "pes" in the search field; choose type "Booleano"; clear both | Only matching rows; match count; all rows again |
| U5 | Open "Peso" | Detail with ID, name, description, type, every property, groups, created and changed dates |
| U6 | "Editar", change name and precision, save | Form filled with current values; ID and type read-only; confirmation; detail shows the changes |
| U7 | "Editar", rename to an existing name | Feature 004's duplicate-name message on the name field; nothing saved |
| U8 | "Eliminar" on an attribute in a group | Warning names the group(s), only "Cancelar" offered |
| U9 | "Eliminar" on an ungrouped LOV attribute, cancel, then confirm | Cancel keeps it; confirming removes it; message says the list is kept |
| U10 | Without `ATTRIBUTE_CREATE` in `VITE_DEV_PERMISSIONS` | List, search and detail work; no "Nuevo atributo", "Editar" or "Eliminar" |
| U11 | Compare with "Lista de Valores" at 1440 px and 768 px | Same layout, buttons, notifications and confirmation style |

## Scope checks

- No change to features 001, 002 or 003 code.
- Feature 004 changes: attribute model, document, repository, service, controller, DTOs and error codes
  (additive); its page moves to `attributes/AttributeEditor.tsx` with its tests; navigation entry
  replaced; contract to v1.2.0.
- Feature 005 changes: `AttributeGroupService.runIfInNoGroup` and the new
  `AttributeGroupDirectoryAdapter` only; its behavior, responses and messages unchanged.
- Feature 006 changes: `namesById` in `ListOfValuesRepository` (and its MongoDB and in-memory
  implementations), `ListOfValuesLookup` and `ListOfValuesCatalogAdapter` only (research R-03).
- Existing tests keep their assertions; test setups pass the new `AttributeGroupDirectory` fake.
- No new dependency in `backend/build.gradle` or `frontend/package.json`; no permission code added.
