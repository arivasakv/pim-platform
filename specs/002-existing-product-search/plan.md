# Implementation Plan: Search for an Existing Product (EAN)

**Branch**: `002-existing-product-search` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-existing-product-search/spec.md` (approved and
amended 2026-11-08: EAN search only)

## Summary

A Mercaderías supplier types an EAN on a new "Buscar producto" page and presses "Buscar". The
frontend calls a new versioned REST endpoint, `GET /api/v1/products?ean=…`. The Spring Boot backend
normalizes and validates the EAN, looks it up in an in-memory sample catalog of 531 products built
at startup, and returns only exact matches from Liverpool and Suburbia; Marketplace products never
appear.

- **Frontend**: holds no product data. It validates the EAN only for instant feedback, and shows
  the results, a "not found, you can continue" message or a Spanish error, using feature 001's
  components.
- **Backend**: a new `product` domain module. The search rule lives in a service separate from the
  controller, the catalog sits behind a repository interface, and errors use RFC 9457 Problem
  Details.
- **Persistence**: no database of any kind.

Details: [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 (Gradle toolchain; local JDK 21.0.12). TypeScript ~6.0.3 (strict)
with React 19.3.1.

**Primary Dependencies**:

| Side | Existing (kept) | Added by this feature |
|---|---|---|
| Backend | Spring Boot 4.1.2 (`spring-boot-starter-webmvc`, `-webmvc-test`), dependency-management 1.1.7, Gradle 9.8.2 | `spring-boot-starter-validation` (version managed by Boot; R-06, **D-3**). Spotless Gradle plugin 8.11.0 with palantir-java-format 2.103.0 (R-11, **D-2**) |
| Frontend | React 19.3.1, Vite 8.3.6, ESLint, Prettier, Vitest, Testing Library (feature 001) | None |

**Storage**: None. Products live in an immutable in-memory index (`Map<String, List<Product>>`),
built at startup from `SampleProductCatalog`. A restart resets it (R-03). No JPA, Hibernate,
migrations, MongoDB or SQL database.

**Testing**:
- **Backend**: JUnit 5. Unit tests for `Ean`, `ProductSearchService` and `SampleProductCatalog`.
  API integration tests with `@SpringBootTest` and MockMvc: success, empty, Marketplace exclusion,
  every validation error with status and payload, and CORS (R-10).
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

**Performance Goals**: Results in under 2 seconds with at least 500 products (SC-004). The lookup is
O(1) by EAN, so the network dominates.

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
| II | Technology Stack | ✅ Pass | React + TypeScript + Vite; Java 21 + Spring Boot + Gradle; REST/JSON. No new language, framework or build tool. The Spotless plugin is a formatter inside Gradle (D-2) |
| III | Separated Apps & Modular Monolith | ✅ Pass | Frontend calls the backend only through the documented REST API. New package `com.pim.backend.product` by domain. Controller only maps HTTP; the rule lives in `ProductSearchService` |
| IV | PIM Business Scope | ✅ Pass | First business domain (`product`), introduced by spec 002 (A-002). Terminology is consistent: "product", "EAN", "business" |
| V | Code Quality | ✅ Pass (D-2 approved) | Constructor injection, records, single responsibility. The duplicated URL helper is consolidated (R-12). New dependencies are justified here. The backend formatter and static checks do not exist yet; D-2 (approved) adds them |
| VI | Testing | ✅ Pass | Unit tests for business logic. API integration tests cover the success and error paths, and every validation rule's accepted and rejected cases with status and Problem payload (R-10). Frontend logic and page tests (R-15) |
| VII | API Design | ✅ Pass (D-1, D-3, D-4 approved) | Plural noun, GET, `/api/v1`, DTOs, `{items,total}` envelope, RFC 9457 errors from one handler, Bean Validation plus a domain rule, correct statuses (200 and 400). OpenAPI contract in `contracts/` |
| VIII | Security | ✅ Pass | No secrets. CORS origins and the backend URL come from configuration, not code. The backend re-validates everything. Only non-sensitive data is exposed to the bundle |
| IX | Development Scope | ✅ Pass | In-memory data behind `ProductRepository`. No persistence, integrations, auth or AI |
| X | AI Governance | ✅ Pass | Decisions and alternatives in research.md; the items needing approval are listed below. Feature 001 files change only where justified (HomePage URL helper, R-12; one-time Java reformat, R-11) |
| XI | Source of Truth | ✅ Pass | Spec cites its sources and tiers. No source-of-truth or reference-repository changes |

**Gate result**: PASS. D-1 to D-5 approved by the project owner on 2026-11-08. No violations.

**Post-design re-check (after Phase 1)**: PASS. The data model, contract
and quickstart add no database, no external integration and no frontend dataset.

## Decisions (approved by the project owner, 2026-11-08)

| ID | Decision | Approved choice | Why it needed approval | Research |
|---|---|---|---|---|
| **D-1** | Endpoint shape | `GET /api/v1/products?ean=…`, always scoped to Liverpool and Suburbia; empty result = 200 with `items: []` | First real endpoint; sets the URL style. The alternatives are a `business` parameter or a `/mercaderias/` path | R-05 |
| **D-2** | Backend formatter and static checks | Spotless 8.11.0 + palantir-java-format 2.103.0, run by `./gradlew build`, plus `-Xlint:all -Werror` on main sources; reformat the 3 existing Java files once | Constitution V requires them; adds a build plugin and changes existing files' whitespace. Alternative: google-java-format (2-space) | R-11 |
| **D-3** | Validation dependency | Add `spring-boot-starter-validation` (Boot-managed version) | New dependency; needed for constitution VII's "Bean Validation plus domain rules" | R-06 |
| **D-4** | Project-wide API conventions | RFC 9457 Problem Details with `errors[{field,code,message}]` and Spanish messages; `{items,total}` collection envelope; recorded in a new `docs/decisions/0001-api-conventions.md` | Binds every future endpoint | R-07 |
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
└── 0001-api-conventions.md       # [new] Problem Details + collection envelope (D-4)
```

### Source Code

Legend: **[new]**, **[update]**, **[keep]**.

```text
backend/
├── build.gradle                              [update] + spring-boot-starter-validation (D-3); + Spotless/palantir, -Xlint (D-2)
└── src/
    ├── main/
    │   ├── resources/application.yaml        [update] + spring.mvc.problemdetails.enabled, pim.cors.allowed-origins
    │   └── java/com/pim/backend/
    │       ├── BackendApplication.java       [keep] (reformatted only, D-2)
    │       ├── HelloController.java          [keep] (reformatted only, D-2)
    │       ├── product/                      [new] domain module
    │       │   ├── Business.java             enum LIVERPOOL, SUBURBIA, MARKETPLACE
    │       │   ├── Product.java              record
    │       │   ├── Ean.java                  value object: parse, normalize, validate
    │       │   ├── InvalidEanException.java  carries the error code
    │       │   ├── ProductRepository.java    interface (constitution IX)
    │       │   ├── InMemoryProductRepository.java   index by EAN; per-business uniqueness check
    │       │   ├── SampleProductCatalog.java scenario + generated products (531)
    │       │   ├── ProductCatalogConfiguration.java  @Configuration: builds the repository at startup
    │       │   ├── ProductSearchService.java exact EAN, Liverpool/Suburbia only, ordering
    │       │   ├── ProductController.java    GET /api/v1/products (HTTP only)
    │       │   ├── ProductSummaryResponse.java      DTO record
    │       │   └── ProductCollectionResponse.java   DTO record {items, total}
    │       └── web/                          [new] cross-cutting
    │           ├── ApiExceptionHandler.java  @RestControllerAdvice → Problem Details
    │           └── CorsConfiguration.java    allowed origins from configuration
    └── test/java/com/pim/backend/
        ├── BackendApplicationTests.java      [keep]
        └── product/                          [new]
            ├── EanTest.java
            ├── ProductSearchServiceTest.java
            ├── SampleProductCatalogTest.java
            └── ProductControllerIntegrationTest.java   MockMvc: 200, 400 (each code), CORS

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
- **Backend**: follows constitution III. The first domain package, `product`, holds model,
  rules, repository, service and API. Cross-cutting HTTP concerns sit in `web`. The search service
  has no HTTP types, so proposal creation and bulk upload can reuse it.
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

## Complexity Tracking

No constitution violations. D-1 to D-5 were approved on 2026-11-08, so nothing needs to be
recorded here.
