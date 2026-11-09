# Data Model: Search for an Existing Product (EAN)

**Feature**: [spec.md](./spec.md) | **Date**: 2026-11-08

All data lives in backend memory (constitution IX). Nothing is persisted, and restarting the
backend rebuilds the same sample catalog (research R-03).

## Business (enum)

| Value | Display name (UI) | Searchable in this feature |
|---|---|---|
| `LIVERPOOL` | Liverpool | Yes |
| `SUBURBIA` | Suburbia | Yes |
| `MARKETPLACE` | Marketplace | **No**: never returned (FR-014) |

## Product (domain record)

| Field | Type | Rules |
|---|---|---|
| `sku` | string | 7 digits, unique across the whole catalog |
| `ean` | string | Normalized EAN: 8, 12, 13 or 14 digits. The same EAN may appear in different businesses but **must not repeat within one business** (source rule; checked when the catalog is built, R-03) |
| `title` | string | Non-empty, at most 120 characters, Spanish, no prices or shopping terms (feature 001, FR-006) |
| `brand` | string | Non-empty, at most 60 characters |
| `business` | Business | One value |

## Ean (value object)

Built only through `Ean.parse(raw)`. The same rule is applied in the frontend for instant feedback
(`src/products/ean.ts`), but the backend's check is authoritative.

1. Missing or blank raw value → `REQUIRED`.
2. Raw value longer than 20 characters → `TOO_LONG`.
3. Any character other than digits, spaces and dashes → `INVALID_CHARACTERS`.
4. Remove spaces and dashes.
5. Result length not in {8, 12, 13, 14} → `INVALID_LENGTH`.
6. Otherwise valid. The normalized digits are the value. The check digit is **not** validated
   (spec A-004).

| Raw input | Result |
|---|---|
| `7501234567890` | valid, `7501234567890` |
| `750 1234-567890` | valid, `7501234567890` |
| `12345670` | valid (EAN-8) |
| ` ` (spaces only) | `REQUIRED` |
| `licuadora` | `INVALID_CHARACTERS` |
| `750123456789` + 9 more characters (21 characters) | `TOO_LONG` |
| `75012345` + `6` (9 digits) | `INVALID_LENGTH` |

## Search rule (ProductSearchService)

Input: a valid `Ean`. Output: an ordered list of products. The rule:

- keep products whose `ean` equals the normalized EAN exactly (FR-004);
- keep only `business ∈ {LIVERPOOL, SUBURBIA}` (FR-014);
- order by business (Liverpool, then Suburbia), then by `sku` (research R-05, gap G-1);
- never create, modify or reserve anything (FR-012).

## API shapes (DTOs)

Details and examples: [contracts/product-search.openapi.yaml](./contracts/product-search.openapi.yaml).

- **ProductSummary**: `sku`, `ean`, `title`, `brand`, `business` (`"LIVERPOOL"` or
  `"SUBURBIA"`).
- **ProductCollection**: `items` (ProductSummary[]) and `total` (integer, equals `items.length` in
  this feature). This is the project-wide collection envelope (R-07).
- **Problem** (RFC 9457): `type`, `title`, `status`, `detail`, `instance`, `errors[]` with
  `{ field, code, message }`; codes as in the Ean rules above.

## Frontend view state (ProductSearchPage)

| State | Shown | Triggered by |
|---|---|---|
| `idle` | Field and button only | Page opened |
| `invalid` | TextField error message (code → Spanish text) | Frontend check fails; no request sent |
| `searching` | Button disabled, status "Buscando…" | Valid EAN submitted |
| `found` | Warning notification (FR-008) and one card per product | 200 with `total > 0` |
| `notFound` | Success notification "puedes continuar" (FR-007) | 200 with `total = 0` |
| `error` | Error notification with "Reintentar" (FR-011) | Network failure, non-200 other than 400, or no base URL configured |

A 400 from the backend despite the frontend check shows the same message as `invalid`, using the
returned `errors[].code`. Starting a new search clears the previous results and cancels any request
still running (R-14).

## Sample catalog

Built by `SampleProductCatalog.create()` when the application starts (R-03, R-04).

### Scenario products (fixed)

| SKU | EAN | Title | Brand | Business | Scenario |
|---|---|---|---|---|---|
| 1000001 | 7501234567890 | Licuadora 10 velocidades 600 W | MARCA DEMO | LIVERPOOL | US1-AS1: found in Liverpool |
| 1000002 | 7502000000017 | Batidora de mano 300 W | HOGAR DEMO | LIVERPOOL | US1-AS3: same EAN in two businesses |
| 1000003 | 7502000000017 | Batidora de mano 300 W | HOGAR DEMO | SUBURBIA | US1-AS3 |
| 1000004 | 7501112223334 | Audífonos inalámbricos | AUDIO DEMO | MARKETPLACE | US1-AS4: Marketplace only, never returned |
| 1000005 | 12345670 | Termo de acero 500 ml | CASA DEMO | LIVERPOOL | EAN-8 accepted |
| 1000006 | 17501234567893 | Caja con 6 vasos de vidrio | CASA DEMO | SUBURBIA | GTIN-14 accepted |
| — | 7509999999999 | — | — | — | US1-AS2: absent from the catalog |

### Generated products

- **Liverpool and Suburbia**: 500 products. SKUs `1100001`–`1100500`. EANs `7503` followed by a
  9-digit zero-padded sequence (13 digits). Businesses alternate Liverpool and Suburbia. Titles
  are built from neutral product words (for example "Sartén antiadherente 24 cm"); brands come from
  a short list of `… DEMO` names.
- **Marketplace**: 25 products. SKUs `1200001`–`1200025`. EANs `7504…`, which never collide with
  any other range.
- **Total**: 531 products, which meets the "at least 500 products" scenario (SC-004).
