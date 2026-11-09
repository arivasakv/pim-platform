# Quickstart & Validation: Search for an Existing Product (EAN)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-08

All commands are for **Windows PowerShell**. Sample EANs are listed in
[data-model.md](./data-model.md#sample-catalog). The API contract is
[contracts/product-search.openapi.yaml](./contracts/product-search.openapi.yaml).

## Prerequisites

- Java 21 (the Gradle toolchain), and Node.js `^22.13.0 || ^24.0.0 || >=26.0.0` with npm
  (feature 001).
- `frontend\.env` created from `frontend\.env.example` (`VITE_API_BASE_URL=http://localhost:8080`).
- Optional: `PIM_CORS_ALLOWED_ORIGINS` if the frontend does not run on `http://localhost:5173`
  (default in `backend\src\main\resources\application.yaml`).
- *Amended 2026-11-09*: **MongoDB running locally** on `localhost:27017` without authentication
  (development: mongod 9.0.2). The backend uses database `pim`; the tests use `pim-test`. Both
  are created automatically. If your MongoDB needs credentials or another address, set them only
  in your shell, for example
  `$env:PIM_MONGODB_URI = "mongodb://user:password@localhost:27017/pim?authSource=admin"`, and
  `$env:PIM_TEST_MONGODB_URI` (database `pim-test`) for tests. Never commit them.
- Starting MongoDB, if it is not running (adjust the path to your installation):

  ```powershell
  New-Item -ItemType Directory -Force "$env:USERPROFILE\mongodb-data"
  & "<MongoDB folder>\bin\mongod.exe" --dbpath "$env:USERPROFILE\mongodb-data" --bind_ip 127.0.0.1
  ```

- Sample data: on startup the backend creates the indexes and loads the 531 sample products
  only if the `products` collection is empty. To reload it, drop the collection (for example in
  MongoDB Compass) and restart. To start without loading, set
  `$env:PIM_CATALOG_SEED_SAMPLE_DATA = "false"`.

## Automated checks

Backend, from `backend\`:

```powershell
.\gradlew.bat build     # compiles, spotlessCheck, unit, API and MongoDB tests (local mongod must be running): BUILD SUCCESSFUL
.\gradlew.bat test      # tests only, if needed
```

Frontend, from `frontend\`:

```powershell
npm run lint; npm run format:check; npm test; npm run build   # all pass with 0 errors
```

Scope checks, from the repository root:

```powershell
git diff --stat main -- .specify/memory/constitution.md                        # prints nothing
Select-String -Path backend\build.gradle -Pattern 'jpa|hibernate|flyway|liquibase|postgres|mysql|h2|testcontainers|flapdoodle'   # no output (MongoDB is allowed since 2026-11-09)
Get-ChildItem frontend\src -Recurse -Include *.ts,*.tsx -Exclude *.test.* | Select-String -Pattern '7501234567890|7502000000017'   # no output: no product data in the frontend
```

## API checks (backend running)

Start the backend from `backend\` with `.\gradlew.bat bootRun`, then in a second window:

| # | Request | Expected |
|---|---|---|
| A1 | `Invoke-RestMethod "http://localhost:8080/api/v1/products?ean=7501234567890"` | `total` 1, one item, business `LIVERPOOL` |
| A2 | `…?ean=7502000000017` | `total` 2: `LIVERPOOL` first, then `SUBURBIA` |
| A3 | `…?ean=7509999999999` | `items` empty, `total` 0 (status 200) |
| A4 | `…?ean=7501112223334` | `items` empty, `total` 0: the Marketplace product is not returned |
| A5 | `…?ean=750%201234-567890` | Same result as A1 |
| A6 | `…?ean=12345670` and `…?ean=17501234567893` | One item each (EAN-8 and GTIN-14 accepted) |
| A7 | `Invoke-WebRequest "http://localhost:8080/api/v1/products?ean=licuadora" -SkipHttpErrorCheck` (or catch the error in Windows PowerShell 5.1) | 400, `application/problem+json`, `errors[0].code` = `INVALID_CHARACTERS` |
| A8 | `…?ean=750123456` (9 digits) | 400, `INVALID_LENGTH` |
| A9 | `…/api/v1/products` (no `ean`) | 400, `REQUIRED` |
| A10 | Stop MongoDB, then repeat A1 (amendment 2026-11-09) | 503 within about 2 seconds, `application/problem+json`, title "Servicio no disponible"; no sample data returned. Start MongoDB again and A1 works without restarting the backend |
| A11 | Restart the backend, then repeat A2 | Still `total` 2: restarts do not insert duplicates |

## End-to-end checks (browser)

Backend running (`.\gradlew.bat bootRun`) and frontend running (`npm run dev` in `frontend\`).
Open `http://localhost:5173/#/buscar-producto`.

| # | Scenario | Steps | Expected | Covers |
|---|---|---|---|---|
| 1 | Navigation | Click "Buscar producto" in the side menu | Page with title, EAN field and "Buscar" button; menu item active | FR-001, FR-002 |
| 2 | Found in Liverpool | Type `7501234567890`, click "Buscar" | Warning that the product already exists; one card: Licuadora…, SKU 1000001, MARCA DEMO, Liverpool | US1-AS1, FR-006, FR-008 |
| 3 | Two businesses | Type `7502000000017`, press Enter | Two cards, Liverpool then Suburbia | US1-AS3, FR-002 |
| 4 | Not found | Type `7509999999999` | "No encontramos un producto con este EAN. Puedes continuar con una propuesta nueva." | US1-AS2, FR-007 |
| 5 | Marketplace excluded | Type `7501112223334` | Same "not found" message; no Marketplace product shown | US1-AS4, FR-014 |
| 6 | Validation | Try empty, `licuadora`, `750123456`, and 21+ characters | Field error message in Spanish, nothing sent (Network tab shows no request); the field stops at 20 characters | US1-AS5, FR-003, FR-010, SC-005 |
| 7 | Separators | Type `750 1234-567890` | Same result as scenario 2 | Edge cases |
| 8b | MongoDB down (amendment 2026-11-09) | Stop MongoDB, search any valid EAN | Same error notification with "Reintentar"; after starting MongoDB, "Reintentar" works | FR-011 |
| 8 | Backend down | Stop the backend (Ctrl+C), search any valid EAN | Error notification with "Reintentar"; no old results left on screen; after restarting the backend, "Reintentar" works | FR-011, edge cases |
| 9 | Response time | With the 531-product catalog, search several EANs | Result appears in under 2 seconds | SC-004 |
| 10 | Shared identity | Review the page at 1440 and 768 px | Feature 001 components and colors; Spanish text; no horizontal scroll; no image or other search option offered | FR-009, FR-013 |
| 11 | Time to answer | From opening the page, check an EAN | Answer visible in under 10 seconds | SC-001 |

SC-006 (at least 4 of 5 users can tell whether the product exists) is checked in a walkthrough
after implementation.
