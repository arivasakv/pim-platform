# Quickstart: Identifier Management

**Feature**: `003-identifier-management` | **Spec**: [spec.md](spec.md) |
**Contract**: [contracts/identifiers.openapi.yaml](contracts/identifiers.openapi.yaml) |
**Data model**: [data-model.md](data-model.md)

Validation guide for the finished feature. The feature has no screen (FR-012); every scenario
uses the REST API. Commands are for Windows PowerShell.

## Prerequisites

- Java 21. The backend starts as for feature 002, which needs the local MongoDB on
  `localhost:27017` for the product catalog (see `specs/002-existing-product-search/quickstart.md`).
  Identifier data itself is in memory and needs no database (research R-02).
- Run the automated suite (it covers every scenario below):

  ```powershell
  cd backend
  .\gradlew.bat clean build
  ```

- For the manual run: `.\gradlew.bat bootRun`, then use `Invoke-RestMethod` /
  `Invoke-WebRequest` against `http://localhost:8080`. **Restarting the backend clears all
  registered IDs and restores the default configurations.**

Helper for the examples:

```powershell
$api = 'http://localhost:8080/api/v1'
function Post-Id($body, $perm) {
  $h = @{}; if ($perm) { $h['X-Pim-Manual-Id-Permissions'] = $perm }
  Invoke-WebRequest -Method Post -Uri "$api/system-ids" -Headers $h -ContentType 'application/json' `
    -Body ($body | ConvertTo-Json -Compress) -SkipHttpErrorCheck
}
```

(`-SkipHttpErrorCheck` needs PowerShell 7; in Windows PowerShell 5.1 read the status from the
exception's response.)

## Scenarios

| # | Story / requirement | Steps | Expected |
|---|---|---|---|
| 1 | US5, FR-006 | `GET $api/object-types` | 200, `total` 6; each type `ALPHANUMERIC`, `minimumDigits` 6, `nextValue` 1, prefixes PRD, ATR, LOV, PLT, CLS, INT; `keyFields` empty |
| 2 | US1, FR-005 | `Post-Id @{objectType='ATTRIBUTE'; keyFieldValues=@{}}` twice | 201 both; IDs `ATR000001` and `ATR000002`, `assignment` `AUTOMATIC`; `Location` header set |
| 3 | US1, FR-001 | `Post-Id @{objectType='TEMPLATE'; keyFieldValues=@{}}` | 201, `PLT000001` (own sequence) |
| 4 | US2, FR-003 | `Post-Id @{objectType='CLASSIFICATION'; systemId='CLS-ROPA'; keyFieldValues=@{}} 'CLASSIFICATION'` | 201, `CLS-ROPA`, `MANUAL` |
| 5 | US2, FR-003 | Same as 4 with `systemId='CLS-OTRA'` and no header (or header `PRODUCT`) | 403, `MANUAL_ID_NOT_PERMITTED`, Spanish message naming `CLS-OTRA` (FR-011); `GET $api/system-ids/CLS-OTRA` → 404 |
| 6 | Edge (A-004) | `Post-Id @{objectType='PRODUCT'; systemId='   '; keyFieldValues=@{}}` | 201, generated `PRD000001` (blank = no ID; no permission needed) |
| 7 | FR-001 (global) | `Post-Id @{objectType='PRODUCT'; systemId='cls-ropa'; keyFieldValues=@{}} 'PRODUCT'` | 409 `SYSTEM_ID_IN_USE`; message names the ID (other type, case ignored) |
| 8 | US4, FR-013 | `Invoke-RestMethod -Method Put "$api/object-types/ATTRIBUTE" -ContentType application/json -Body '{"sequence":{"kind":"ALPHANUMERIC","prefix":"ATR","minimumDigits":6,"nextValue":3},"keyFields":["code"]}'` | 200 with `keyFields` `["code"]` |
| 9 | US4, FR-009a | `Post-Id @{objectType='ATTRIBUTE'; systemId='A-100'; keyFieldValues=@{code='COLOR'}} 'ATTRIBUTE'`, then repeat it | 201, then 200 `SAME_OBJECT`; `GET` still shows one registration |
| 10 | US4, FR-009b | Repeat 9 with `keyFieldValues=@{code='TALLA'}` | 409 `SYSTEM_ID_IN_USE`, Spanish detail asks for a new, unused ID; registration of `A-100` unchanged |
| 11 | R-09 | `Post-Id @{objectType='TEMPLATE'; systemId='PLT000001'; keyFieldValues=@{}} 'TEMPLATE'` | 409 (templates declare no key fields, so sameness cannot be confirmed) |
| 12 | US3, FR-007 | `Invoke-WebRequest -Method Put "$api/system-ids/ATR000001" -SkipHttpErrorCheck`; same with `-Method Patch` | 405, `Allow: GET`, Spanish detail; `GET` shows the ID unchanged |
| 13 | US5, FR-008 | `PUT $api/object-types/PRODUCT` with `{"sequence":{"kind":"NUMERIC","minimumDigits":8,"nextValue":1000},"keyFields":[]}`, then `Post-Id @{objectType='PRODUCT'; keyFieldValues=@{}}` | New ID `00001000`; `GET $api/system-ids/PRD000001` unchanged |
| 14 | R-06 | Manual `00001001` for PRODUCT (header `PRODUCT`), then an automatic PRODUCT | Automatic ID is `00001002` (1001 skipped) |
| 15 | 400 rules | `Post-Id` with `objectType='WIDGET'`; with `systemId='A B'`; for ATTRIBUTE without `code`; `PUT` a sequence with `kind` NUMERIC and a prefix | 400, codes `UNKNOWN_OBJECT_TYPE`, `INVALID_SYSTEM_ID`, `MISSING_KEY_FIELD`, `INVALID_PREFIX` |
| 16 | SC-002 | Automated: 100 parallel assignments for one type plus 1,000 sequential across types | No duplicate IDs (covered by the build) |

## Scope checks

- `git diff --stat main` lists no file under `frontend/`, `backend/src/main/java/com/pim/backend/product/`
  or `specs/002-existing-product-search/`.
- `backend/build.gradle` has no new dependency.
- No MongoDB collection other than feature 002's `products` is created (identifier data is in
  memory).
