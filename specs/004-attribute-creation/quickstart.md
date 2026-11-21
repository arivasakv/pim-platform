# Quickstart: Attribute Management – Attribute Creation

**Feature**: `004-attribute-creation` | **Spec**: [spec.md](spec.md) |
**Contract**: [contracts/attributes.openapi.yaml](contracts/attributes.openapi.yaml) |
**Data model**: [data-model.md](data-model.md)

Validation guide for the finished feature. Commands are for Windows PowerShell.

## Prerequisites

- Java 21, Node (as for features 001–002), and the local MongoDB on `localhost:27017` (still needed
  by feature 002's product catalog; attribute data itself is in memory, research R-02).
- Automated suites (cover the scenarios below):

  ```powershell
  cd backend;  .\gradlew.bat clean build
  cd ..\frontend;  npm run lint;  npm run format:check;  npm test;  npm run build
  ```

- Manual run: backend `.\gradlew.bat bootRun`; frontend `npm run dev` with `frontend/.env`
  containing `VITE_API_BASE_URL=http://localhost:8080`, and for the permission stand-in (R-07)
  `VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE` and optionally `VITE_DEV_MANUAL_ID_PERMISSIONS=ATTRIBUTE`.
  **Restarting the backend clears all attributes and system IDs.**

## API scenarios

Use `X-Pim-Permissions: ATTRIBUTE_CREATE` unless the scenario says otherwise.

| # | Story / requirement | Request | Expected |
|---|---|---|---|
| 1 | FR-007, FR-022 | `GET /api/v1/attribute-types` | 200, `total` 7, in order TEXT, NUMERIC, BOOLEAN, LIST_OF_VALUES, DATE_TIME, CURRENCY, REFERENCE, with Spanish labels and options |
| 2 | US1 | `POST /api/v1/attributes` `{"displayName":"Apto para regalo","dataType":"BOOLEAN"}` | 201, `systemId` `ATR000001`, `scope` `GLOBAL`, `Location` set; `GET /api/v1/system-ids/ATR000001` (feature 003) shows an `ATTRIBUTE` registration |
| 3 | US1, FR-002 | Same as 2 with another name, **no** `X-Pim-Permissions` | 403 `ATTRIBUTE_CREATE_NOT_PERMITTED`; no attribute and no system ID created |
| 4 | US1, FR-003 | Missing `displayName`, blank `displayName`, missing `dataType`, `dataType` `COLOR` | 400 `REQUIRED` / `INVALID_DISPLAY_NAME` / `REQUIRED` / `UNKNOWN_DATA_TYPE` |
| 5 | US1, FR-023 | `{"displayName":" apto PARA regalo ","dataType":"BOOLEAN"}` | 409 `DISPLAY_NAME_IN_USE`; the next successful creation still gets `ATR000002` (no ID consumed) |
| 6 | US1, FR-004 | `systemId` `ATR-COLOR` with header `X-Pim-Manual-Id-Permissions: ATTRIBUTE` | 201, `systemId` `ATR-COLOR` |
| 7 | US1, FR-005 | `systemId` `ATR-TALLA` without the manual-ID header; `systemId` `A B` with it; `systemId` `atr-color` with it | 403 `MANUAL_ID_NOT_PERMITTED`; 400 `INVALID_SYSTEM_ID`; 409 `SYSTEM_ID_IN_USE` — feature 003's messages |
| 8 | US2, FR-008/009 | TEXT with `minLength` 2, `maxLength` 50, `caseFormat` `UPPERCASE`, `captureMask` `^[A-Z ]+$`, `richText` false | 201 with exactly those properties |
| 9 | US2, FR-016 | TEXT with `minLength` 10, `maxLength` 5; `captureMask` `[a-`; `caseFormat` `TITLE` | 400 `INVALID_LENGTH_RANGE`; `INVALID_CAPTURE_MASK`; `UNKNOWN_CASE_FORMAT` |
| 10 | US2, FR-010 | NUMERIC `DECIMAL` precision 2, 0–999.99; `INTEGER` 0–100; `DECIMAL` without precision; `INTEGER` with precision; minimum 5 maximum 1; `INTEGER` minimum 1.5 | 201; 201; 400 `INVALID_PRECISION` ×2; 400 `INVALID_VALUE_RANGE`; 400 `INVALID_NUMBER` |
| 11 | US2, FR-013 | DATE_TIME `DD/MM/AAAA`; `dateFormat` `YYYY` | 201; 400 `UNKNOWN_DATE_FORMAT` |
| 12 | US2, FR-014 | CURRENCY `MXN` (decimals omitted); `currencyCode` `XYZ`; decimals 5 | 201 with `currencySymbol` and `decimals` 2; 400 `UNKNOWN_CURRENCY`; 400 `INVALID_DECIMALS` |
| 13 | US3, FR-012 | LIST_OF_VALUES `SINGLE` with any `listOfValuesId`, and without one | 422 `LIST_OF_VALUES_NOT_AVAILABLE` both times; nothing saved |
| 14 | US4, FR-015 | REFERENCE `PRODUCT` `MULTIPLE`; REFERENCE without target; target `WIDGET` | 201; 400 `REQUIRED`; 400 `UNKNOWN_TARGET_OBJECT_TYPE` |
| 15 | US5, FR-017/018 | Any 201 above; also a body with `"scope":"LOCAL"` and `"groups":["X"]` | `scope` `GLOBAL`, no groups in the response (unknown fields ignored) |
| 16 | A-004 | BOOLEAN with `textProperties` present | 201; response has no `textProperties` |
| 17 | FR-021 | `GET /api/v1/attributes/{id}` for each 201; `GET /api/v1/attributes/ATR999` | 200 with the same data; 404 |

## UI scenarios (browser, owner check)

| # | Steps | Expected |
|---|---|---|
| U1 | Open "Atributos" from the side navigation and choose "Nuevo atributo" (feature 007 replaced the "Crear Atributos" entry) | Form with display name, optional ID, data type (7 options from the backend) |
| U2 | Choose each type in turn | Only that type's fields appear; Booleano shows none |
| U3 | Create "Peso neto", Numérico, Decimal, 2 decimals, 0–999.99 | Spanish confirmation showing the new ID; form ready for another attribute |
| U4 | Create a second "peso neto" | Spanish message that the name is already used; entered data kept |
| U5 | Choose Lista de valores and save | Message that no lists of values exist yet |
| U6 | Remove `VITE_DEV_PERMISSIONS`, restart `npm run dev`, try to save | Message that you may not create attributes |
| U7 | Stop the backend and save | Connection error message; entered data kept |

## Scope checks

- `git diff --stat main` shows no change to `backend/src/main/java/com/pim/backend/identifier/`,
  `backend/src/main/java/com/pim/backend/product/`, their tests, `specs/002-*`, `specs/003-*`, the
  constitution or CLAUDE.md (decision D-4, option A).
- `backend/build.gradle` and `frontend/package.json` have no new dependency.
- No MongoDB collection besides feature 002's `products`.
