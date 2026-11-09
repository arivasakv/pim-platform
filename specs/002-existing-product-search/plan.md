# Implementation Plan: Search for an Existing Product (EAN)

**Branch**: `002-existing-product-search` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-existing-product-search/spec.md` (approved and
amended 2026-11-08: EAN search only; amended 2026-11-09: catalog stored in MongoDB)

> **Amendment 2026-11-09 (MongoDB catalog)**: the sample catalog moves from backend memory to a
> local MongoDB collection (constitution 2.2.0, Principle IX). Decisions D-6 to D-9 below; details in
> research R-17 to R-20 and [ADR 0002](../../docs/decisions/0002-mongodb-product-catalog.md).
> Search behavior, validation and the 200/400 contract are unchanged; a 503 response is added.

## Summary

A Mercaderías supplier types an EAN on a new "Buscar producto" page and presses "Buscar". The
frontend calls a new versioned REST endpoint, `GET /api/v1/products?ean=…`. The Spring Boot backend
normalizes and validates the EAN, looks it up in a MongoDB collection holding a sample catalog of 531
products (loaded when the collection is empty), and returns only exact matches from Liverpool and Suburbia; Marketplace products never
appear.

- **Frontend**: holds no product data. It validates the EAN only for instant feedback, and shows
  the results, a "not found, you can continue" message or a Spanish error, using feature 001's
  components.
- **Backend**: a new `product` domain module. The search rule lives in a service separate from the
  controller, the catalog sits behind a repository interface, and errors use RFC 9457 Problem
  Details.
- **Persistence**: MongoDB for the product catalog only, through Spring Data MongoDB behind
  `ProductRepository` (amended 2026-11-09, D-6). No other database.

Details: [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 (Gradle toolchain; local JDK 21.0.12). TypeScript ~6.0.3 (strict)
with React 19.3.1.

**Primary Dependencies**:

| Side | Existing (kept) | Added by this feature |
|---|---|---|
| Backend | Spring Boot 4.1.2 (`spring-boot-starter-webmvc`, `-webmvc-test`), dependency-management 1.1.7, Gradle 9.8.2 | `spring-boot-starter-validation` (version managed by Boot; R-06, **D-3**). Spotless Gradle plugin 8.11.0 with palantir-java-format 2.103.0 (R-11, **D-2**). `spring-boot-starter-data-mongodb` (Boot-managed: Spring Data MongoDB 5.1.2, Java driver 5.8.1; R-17, **D-6**, amendment 2026-11-09) |
| Frontend | React 19.3.1, Vite 8.3.6, ESLint, Prettier, Vitest, Testing Library (feature 001) | None |

**Storage** (amended 2026-11-09): MongoDB, database `pim`, collection `products`, one document per
product and business. Unique indexes on `{ean, business}` and on `sku` (R-18). At startup the
backend creates the indexes and, when the collection is empty, loads `SampleProductCatalog`
(R-19). There is no in-memory fallback. No JPA, Hibernate, migrations or SQL database. The
original in-memory design (R-03) is superseded.

**Testing**:
- **Backend**: JUnit 5. Unit tests for `Ean`, `ProductSearchService` and `SampleProductCatalog`.
  API integration tests with `@SpringBootTest` and MockMvc: success, empty, Marketplace exclusion,
  every validation error with status and payload, and CORS (R-10).
- **Backend, MongoDB (amended 2026-11-09, D-9)**: Spring tests run against the developer's local
  MongoDB, in a separate database `pim-test` that the tests empty and reload; they never use
  `pim`. Repository tests (exact match, both businesses, unique indexes, read-only search),
  catalog loading (indexes, 531 products, no duplicates on restart), and store unavailable
  (exception translation and the 503 Problem response) (R-20).
- **Frontend**: Vitest with `fetch` stubbed. Tests for the EAN rule, the API client and the page:
  success, no results, validation errors, connection errors, Enter submits, stale responses ignored
  (R-15).

**Target Platform**: Backend on the JVM (local `bootRun` on port 8080). Frontend in desktop and
tablet browsers (feature 001).

**Project Type**: Web application with separate `frontend/` and `backend/` projects.

**Configuration** (constitution VIII):
- Frontend: `VITE_API_BASE_URL` (feature 001).
- Backend: `pim.cors.allowed-origins`, overridable with `PIM_CORS_ALLOWED_ORIGINS`, default
  `http://localhost:5173` in `application.yaml` (R-08).
- Backend (amended 2026-11-09): `spring.mongodb.uri`, overridable with `PIM_MONGODB_URI`, default
  `mongodb://localhost:27017/pim?serverSelectionTimeoutMS=2000` (no credentials; a URI with
  credentials is set only in the environment). `pim.catalog.seed-sample-data`, overridable with
  `PIM_CATALOG_SEED_SAMPLE_DATA`, default `true` (D-8).

**Performance Goals**: Results in under 2 seconds with at least 500 products (SC-004). The lookup is
an equality query served by the `{ean, business}` index, so the network dominates. An unreachable
store fails after about 2 seconds (server selection timeout) instead of the driver's 30-second
default.

**Constraints**:
- Exact EAN match only; Liverpool and Suburbia only (FR-004, FR-014).
- No image, title or any other search, and no AI (FR-009).
- The search never writes anything (FR-012).
- Spanish UI (FR-013).
- Backend validation is authoritative (constitution VIII).

**Scale/Scope**:
- 1 endpoint and 1 page.
- Backend: about 9 production classes (≈7 domain, 2 web).
- Frontend: about 4 modules (API config, products API, EAN rule, page).
- 531 sample products.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-Driven | ✅ Pass | Spec approved and amended explicitly. Every planned element traces to FR or SC items. No clarification markers |
| II | Technology Stack | ✅ Pass (D-6 approved, ADR 0002) | React + TypeScript + Vite; Java 21 + Spring Boot + Gradle; REST/JSON. The Spotless plugin is a formatter inside Gradle (D-2). Amendment 2026-11-09: Spring Data MongoDB added, recorded in ADR 0002 |
| III | Separated Apps & Modular Monolith | ✅ Pass | Frontend calls the backend only through the documented REST API. New package `com.pim.backend.product` by domain. Controller only maps HTTP; the rule lives in `ProductSearchService` |
| IV | PIM Business Scope | ✅ Pass | First business domain (`product`), introduced by spec 002 (A-002). Terminology is consistent: "product", "EAN", "business" |
| V | Code Quality | ✅ Pass (D-2 approved) | Constructor injection, records, single responsibility. The duplicated URL helper is consolidated (R-12). New dependencies are justified here. The backend formatter and static checks do not exist yet; D-2 (approved) adds them |
| VI | Testing | ✅ Pass | Unit tests for business logic. API integration tests cover the success and error paths, and every validation rule's accepted and rejected cases with status and Problem payload (R-10). Frontend logic and page tests (R-15) |
| VII | API Design | ✅ Pass (D-1, D-3, D-4 approved) | Plural noun, GET, `/api/v1`, DTOs, `{items,total}` envelope, RFC 9457 errors in one format built by `common.ApiProblems` (EAN errors in `product.controller.ProductExceptionHandler`, unreachable store in `common.DataAccessExceptionHandler`; ADR 0001), Bean Validation plus a domain rule, correct statuses (200, 400 and 503). OpenAPI contract in `contracts/` (v1.1.0) |
| VIII | Security | ✅ Pass | No secrets. CORS origins and the backend URL come from configuration, not code. The backend re-validates everything. Only non-sensitive data is exposed to the bundle |
| IX | Development Scope | ✅ Pass (amended 2026-11-09) | Product catalog in MongoDB behind `ProductRepository`, as constitution 2.2.0 allows. Sample data only; connection from configuration, no credentials in the repository. No other database, integrations, auth or AI. "Own specification" handled as an amendment of this feature (Complexity Tracking) |
| XII | Backend Package Structure | ✅ Pass | `product.{controller,service,repository,model,dto,config}`; Spring Data document and repository stay package-private in `product.repository`; the 503 handler is domain-neutral in `common` |
| X | AI Governance | ✅ Pass | Decisions and alternatives in research.md; the items needing approval are listed below. Feature 001 files change only where justified (HomePage URL helper, R-12; one-time Java reformat, R-11) |
| XI | Source of Truth | ✅ Pass | Spec cites its sources and tiers. No source-of-truth or reference-repository changes |

**Gate result**: PASS. D-1 to D-5 approved by the project owner on 2026-11-08; D-6 to D-9 on
2026-11-09. One recorded deviation (Complexity Tracking).

**Post-design re-check (after Phase 1)**: PASS. The data model, contract
and quickstart add no database, no external integration and no frontend dataset.

**Post-design re-check (amendment 2026-11-09)**: PASS. The data model, contract and quickstart add
MongoDB for the product catalog only (constitution IX 2.2.0), with the connection from
configuration and no credentials in the repository. They add no other database, no external
integration and no frontend dataset. The contract change is additive (503, version 1.1.0).

## Decisions (approved by the project owner, 2026-11-08)

| ID | Decision | Approved choice | Why it needed approval | Research |
|---|---|---|---|---|
| **D-1** | Endpoint shape | `GET /api/v1/products?ean=…`, always scoped to Liverpool and Suburbia; empty result = 200 with `items: []` | First real endpoint; sets the URL style. The alternatives are a `business` parameter or a `/mercaderias/` path | R-05 |
| **D-2** | Backend formatter and static checks | Spotless 8.11.0 + palantir-java-format 2.103.0, run by `./gradlew build`, plus `-Xlint:all -Werror` on main sources; reformat the 3 existing Java files once | Constitution V requires them; adds a build plugin and changes existing files' whitespace. Alternative: google-java-format (2-space) | R-11 |
| **D-3** | Validation dependency | Add `spring-boot-starter-validation` (Boot-managed version) | New dependency; needed for constitution VII's "Bean Validation plus domain rules" | R-06 |
| **D-4** | Project-wide API conventions | RFC 9457 Problem Details with `errors[{field,code,message}]` and Spanish messages; `{items,total}` collection envelope; recorded in a new `docs/decisions/0001-api-conventions.md` | Binds every future endpoint | R-07 |
| **D-6** | Catalog store (amendment 2026-11-09) | MongoDB through `spring-boot-starter-data-mongodb`; Spring Data repository wrapped by `MongoProductRepository implements ProductRepository`; the in-memory repository moves to test sources as a fake | Constitution IX amendment 2.2.0 and a new framework (II) | R-17 |
| **D-7** | Document and indexes | Collection `products`; fields `sku`, `ean` (normalized digits, string), `title`, `brand`, `business` (enum name); unique `{ean:1, business:1}` and unique `{sku:1}`; no global unique EAN | Defines stored data | R-18 |
| **D-8** | Sample data loading | At startup: create indexes, then insert `SampleProductCatalog` only if the collection is empty; on by default (`PIM_CATALOG_SEED_SAMPLE_DATA`); startup fails if MongoDB is unreachable | Writes into a database on startup | R-19 |
| **D-9** | Test database and unavailable store | Tests use the local MongoDB, database `pim-test` (not Testcontainers: Docker is not running in development). Unreachable store → 503 Problem Details from a handler in `common` | Build depends on a running mongod; new error status | R-20 |
| **D-5** | Refactor of feature 001 code | Move `getHelloUrl` from `HomePage.tsx` into a shared `src/api/apiConfig.ts` (`buildApiUrl`); behaviour and tests unchanged | Touches a completed feature; required by constitution V (no duplicated logic) | R-12 |

## Specification gaps (assumptions confirmed by the project owner, 2026-11-08)

| ID | Gap in spec.md | Confirmed assumption |
|---|---|---|
| G-1 | Result order is not specified | Liverpool first, then Suburbia, then by SKU |
| G-2 | How results are presented (FR-006 lists only the fields) | One compact card per product with a neutral business badge; no table (the Table component was deferred in feature 001, and there are at most 2 results) |
| G-3 | Exact Spanish texts for found, not found, errors and validation | Proposed in research R-13 and in the contract examples |
| G-4 | Page path | `#/buscar-producto` (label "Buscar producto", spec A-002) |
| G-5 | How SC-004 (under 2 s with ≥500 products) is verified | Manually in the quickstart (scenario 9) and through the catalog-size test; no timing assertion in automated tests (would be flaky) |
| G-6 | What the supplier sees if the backend rejects an EAN the frontend accepted | The same field message, taken from the returned error code |

None of these change requirements, so the approved spec stays as it is. They are planning
assumptions, confirmed by the project owner on 2026-11-08.

## Project Structure

### Documentation (this feature)

```text
specs/002-existing-product-search/
├── spec.md
├── plan.md                       # This file
├── research.md                   # Phase 0
├── data-model.md                 # Phase 1: entities, EAN rule, view states, sample catalog
├── quickstart.md                 # Phase 1: API and end-to-end validation
├── contracts/
│   └── product-search.openapi.yaml   # Phase 1: OpenAPI 3.1 contract
├── checklists/
│   └── requirements.md
└── tasks.md                      # Phase 2 (/speckit-tasks, not created here)

docs/decisions/
├── 0001-api-conventions.md       # [new] Problem Details + collection envelope (D-4)
└── 0002-mongodb-product-catalog.md   # [new, amendment 2026-11-09] MongoDB catalog store (D-6 to D-9)
```

### Source Code

Legend: **[new]**, **[update]**, **[keep]**.

```text
backend/
├── build.gradle                              [update] + validation (D-3); + Spotless/palantir, -Xlint (D-2); + data-mongodb (D-6)
└── src/
    ├── main/
    │   ├── resources/application.yaml        [update] problemdetails, pim.cors.allowed-origins; + spring.mongodb.uri, pim.catalog.seed-sample-data (D-6, D-8)
    │   └── java/com/pim/backend/             layout per constitution XII (backend refactor e8fba29)
    │       ├── BackendApplication.java       [keep]
    │       ├── common/
    │       │   ├── ApiFieldError.java        errors[] item {field, code, message}
    │       │   ├── ApiProblems.java          builds every Problem Details body (+ 503, D-9)
    │       │   ├── DataAccessExceptionHandler.java   [new, D-9] store unreachable → 503
    │       │   └── HelloController.java      feature 001 endpoint
    │       ├── config/
    │       │   └── CorsConfiguration.java    allowed origins from configuration
    │       └── product/
    │           ├── controller/  ProductController (GET /api/v1/products), ProductExceptionHandler (400)
    │           ├── service/     ProductSearchService: EAN rule, Liverpool/Suburbia only, ordering
    │           ├── repository/  ProductRepository (read-only interface)
    │           │                MongoProductRepository [new, D-6] implements ProductRepository
    │           │                ProductDocument, ProductDocumentRepository [new, package-private Spring Data types]
    │           │                MongoProductCatalogLoader [new, D-7, D-8] indexes + insert when empty
    │           ├── model/       Business, Product, Ean, EanErrorCode, InvalidEanException
    │           ├── dto/         ProductSummaryResponse, ProductCollectionResponse
    │           └── config/      SampleProductCatalog (531 products)
    │                            ProductCatalogInitializer [new, D-8] replaces ProductCatalogConfiguration
    └── test/
        ├── resources/config/application.yaml [new, D-9] database pim-test
        └── java/com/pim/backend/             mirrors main
            ├── BackendApplicationTests.java
            ├── common/  HelloControllerIntegrationTest
            └── product/
                ├── config/      SampleProductCatalogTest, ProductCatalogInitializerIntegrationTest [new]
                ├── controller/  ProductControllerIntegrationTest (now on MongoDB), ProductControllerUnavailableIntegrationTest [new]
                ├── model/       EanTest
                ├── repository/  InMemoryProductRepository [moved from main: test fake], MongoProductRepositoryIntegrationTest [new], MongoProductRepositoryUnavailableTest [new]
                └── service/     ProductSearchServiceTest

frontend/src/
├── api/                                      [new]
│   ├── apiConfig.ts                          buildApiUrl() (moved from HomePage, D-5) (+ test)
│   └── productsApi.ts                        searchProductsByEan(); types; error mapping (+ test)
├── products/
│   └── ean.ts                                [new] normalizeEan / validateEan, same rule as backend (+ test)
├── pages/
│   ├── HomePage.tsx                          [update] uses buildApiUrl (D-5); behaviour unchanged
│   ├── ProductSearchPage.tsx                 [new] form, states, results (+ .module.css, + test)
├── navigation.ts                             [update] + { path: '/buscar-producto', label: 'Buscar producto', icon: 'search' }
├── navigation.test.ts                        [update] + '#/buscar-producto' case
└── App.tsx                                   [update] + route case
```

**Structure Decision**:
- **Backend**: follows constitution III and XII. The first domain package, `product`, has one
  subpackage per layer. Cross-cutting code sits in `common` and `config`. The search service has
  no HTTP or storage types, so proposal creation and bulk upload can reuse it.
- **Frontend**: keeps feature 001's layout and adds two small folders. `api/` holds HTTP code and
  `products/` holds domain helpers, so the page component only handles UI state.

## Implementation Order (guidance for `/speckit-tasks`)

1. **Backend tooling**: Spotless and lint (D-2); the validation starter (D-3). Then confirm
   `./gradlew build` is green before any new code.
2. **API conventions**: write ADR 0001 (D-4).
3. **Backend, tests first**:
   - `Ean`, then the catalog and repository, then the service, then the controller and exception
     handler;
   - CORS and `application.yaml`;
   - API integration tests against the contract.
4. **Frontend, tests first**:
   - `apiConfig` refactor (D-5), checking that HomePage's tests still pass;
   - `ean.ts`, then `productsApi.ts`, then `ProductSearchPage`;
   - navigation entry and route.
5. **Validation**: quickstart API checks A1–A9, end-to-end scenarios 1–11, and lint, format, test
   and build on both projects.
6. **Amendment 2026-11-09, MongoDB catalog** (tasks T037–T047): docs and ADR 0002; dependency and
   configuration; document, Spring Data repository and `MongoProductRepository`; catalog loader
   and initializer; in-memory repository to test sources; 503 handler; tests; build and live check
   against the local MongoDB.

## Complexity Tracking

| Deviation | Why it is needed | Simpler alternative rejected because |
|---|---|---|
| Principle IX says each new capability comes "through its own specification"; MongoDB storage is added as an amendment of feature 002 instead of a new feature | The capability changes only where feature 002's catalog is stored; behavior, contract (except an added 503) and UI are unchanged, so the amendment keeps everything about the search in one place. Approved by the project owner on 2026-11-09 | A separate feature 003 would duplicate feature 002's requirements and leave its documents describing storage that no longer exists |
| Backend tests need a running local MongoDB (D-9) | Docker is not running in development, so Testcontainers cannot be used now | An in-memory fake for API tests would not prove the real query, indexes or loading; an embedded MongoDB is forbidden by Principle IX |
