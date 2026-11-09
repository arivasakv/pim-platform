# Research: Search for an Existing Product (EAN)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-08

Each entry gives the decision, the rationale and the alternatives. Entries marked **Needs
approval** change project-wide conventions or tooling. They are listed together in plan.md,
"Decisions needing approval".

## Baseline findings (inspected 2026-11-08)

### Backend (`backend/`)

| Finding | Location | Impact |
|---|---|---|
| Spring Boot **4.1.2**, Java toolchain **21** (local JDK 21.0.12), Gradle wrapper **9.8.2**, dependency-management plugin 1.1.7 | `backend/build.gradle`, `gradle/wrapper/gradle-wrapper.properties` | Stack matches constitution II |
| Only dependencies: `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test` (Boot 4 modular starters) | `backend/build.gradle` | No validation starter: Bean Validation (constitution VII) needs one → R-06 |
| Only code: `BackendApplication` and `HelloController` (`GET /api/v1/hello`, `@CrossOrigin("http://localhost:5173")` hard-coded) | `backend/src/main/java/com/pim/backend/` | No domain modules yet; this feature creates the first (`product`) → R-02 |
| Only test: `BackendApplicationTests.contextLoads` | `backend/src/test/...` | API integration tests needed (constitution VI) → R-10 |
| `application.yaml` holds only `spring.application.name` | `backend/src/main/resources/` | CORS origins can be externalized here → R-08 |
| **No Java formatter or static checks** configured | `backend/build.gradle` | Constitution V requires them to pass with zero errors → R-11 (D-2, approved 2026-11-08) |
| Existing Java files indent with tabs (Spring Initializr style) | `BackendApplication.java`, `BackendApplicationTests.java` | A formatter would reformat them once |
| No OpenAPI contract for the existing hello endpoint | — | Out of scope (analysis note N1 of feature 001); this feature documents its own endpoint |

### Frontend (`frontend/`)

| Finding | Location | Impact |
|---|---|---|
| React 19.3.1, Vite 8.3.6, TypeScript ~6.0.3 (strict), ESLint + Prettier, Vitest + Testing Library | `frontend/package.json` | Ready; no new dependencies needed |
| Backend URL from `VITE_API_BASE_URL` with a tracked `.env.example` | `frontend/src/pages/HomePage.tsx` (`getHelloUrl`), `frontend/.env.example` | Reuse; extract to a shared helper → R-12 |
| Reusable components: TextField (search variant, hint, error), Button, Card, PageHeader, StatusBadge, Notification, Icon (`search` exists) | `frontend/src/components/ui/` | Enough for the page; no Table component (deferred in 001) → R-13 |
| Hash navigation (`navigation.ts`), page switch in `App.tsx` | `frontend/src/` | Add one entry and one case → R-14 |
| Tests stub `fetch` with `vi.stubGlobal` | `frontend/src/pages/HomePage.test.tsx` | Same technique for mocked HTTP; no MSW dependency → R-15 |

## R-01 Architecture: real frontend-to-backend integration

- **Decision**: The frontend sends the typed EAN to a backend REST endpoint. The backend
  normalizes and validates it, searches its in-memory sample catalog, applies the Mercaderías
  business scope, and returns the matches. The frontend holds no product data and implements no
  matching. Its own EAN check only gives instant feedback (constitution VIII: backend validation
  is authoritative).
- **Rationale**: User instruction for this plan; spec TD-002 (backend owns the rule so the
  proposal flow and bulk upload can reuse it); constitution III and VIII.
- **Alternatives**: Frontend dataset (rejected by the user and by TD-002).

## R-02 Backend module and layering

- **Decision**: A new domain package `com.pim.backend.product` (constitution III: package per
  domain), with:
  - `Product` (record) and `Business` (enum `LIVERPOOL`, `SUBURBIA`, `MARKETPLACE`): domain model;
  - `Ean` (value object): normalization and validation rules, throws `InvalidEanException`;
  - `ProductRepository` (interface) and `InMemoryProductRepository`: persistence behind an
    interface (constitution IX);
  - `SampleProductCatalog`: builds the sample products;
  - `ProductSearchService`: the search rule (exact EAN, Liverpool and Suburbia only, ordering),
    with no HTTP types, so future features can reuse it;
  - `ProductController` plus response DTOs: HTTP only (constitution III: no business rules in
    controllers).
  
  Cross-cutting web concerns go in `com.pim.backend.web`: `ApiExceptionHandler`
  (`@RestControllerAdvice`) and `CorsConfiguration`.
- **Rationale**: Matches constitution III and V (single responsibility, constructor injection).
  The Mercaderías scope lives in the service, not the controller (user requirement: reusable
  search logic).
- **Alternatives**: One controller with logic inside (violates III); a generic "matching" module
  now (premature: DUD-32 is open, spec OQ-005).

## R-03 In-memory catalog and startup initialization

- **Decision**:
  - `InMemoryProductRepository` receives the product list in its constructor and builds an
    immutable index `Map<String, List<Product>>` keyed by normalized EAN (lookup O(1)).
  - A `@Configuration` class creates the repository bean from `SampleProductCatalog.create()`, so
    the catalog is built once when the application context starts.
  - The repository verifies on construction that no EAN repeats within the same business (spec
    FR-014 and the source rule "EAN unique per business"). If it does, startup fails with a clear
    message.
  - Restarting the backend rebuilds the same catalog (deterministic data).
- **Rationale**: Java collections only, no database (user instruction; constitution IX).
  Constructor injection of the list keeps tests simple (small fixtures). The uniqueness check
  guards the sample data itself.
- **Alternatives**:
  - JSON file loaded at startup (adds a parsing step, and the data is easier to read but harder
    to keep consistent with tests);
  - `CommandLineRunner` filling a mutable map (mutable shared state for no benefit);
  - an embedded database (forbidden).

## R-04 Sample catalog content

- **Decision**: Deterministic data in two parts (details: [data-model.md](./data-model.md#sample-catalog)).
  1. **Scenario products**, with fixed EANs used by the spec, the tests and the quickstart:
     - `7501234567890`: Liverpool only (US1-AS1);
     - `7502000000017`: Liverpool and Suburbia (US1-AS3);
     - `7501112223334`: Marketplace only (US1-AS4);
     - `7509999999999`: absent (US1-AS2);
     - one 8-digit and one 14-digit EAN, to prove all accepted lengths.
  2. **Generated products**: 500 Liverpool and Suburbia products plus 25 Marketplace products,
     with sequential SKUs and EANs in a reserved range (`7503…`) that never collides with the
     scenario EANs. Titles and brands come from small neutral word lists (Spanish), with no prices
     or shopping terms.
- **Rationale**: Covers every acceptance scenario and the "at least 500 products" performance
  scenario (SC-004). Fixed EANs make tests and manual checks reproducible.
- **Alternatives**: Random data (non-reproducible); 500 hand-written entries (noise).

## R-05 REST endpoint (D-1, approved 2026-11-08)

- **Decision**: `GET /api/v1/products?ean={ean}`.
  - **200**: `{ "items": [ProductSummary...], "total": n }`. An empty result is
    `{ "items": [], "total": 0 }`, not 404, because the request is a filter over a collection.
  - **400** (`application/problem+json`): invalid or missing `ean`.
  - The endpoint always applies the Mercaderías scope (Liverpool and Suburbia). The client cannot
    widen it, so Marketplace products can never leak (FR-014; backend authoritative).
  - Results are ordered Liverpool first, then Suburbia, then by SKU (gap G-1).
- **Rationale**: Constitution VII: plural resource noun, HTTP verb for the action, versioned base
  path, filter as a query parameter, DTOs, consistent collection envelope, correct status codes.
- **Alternatives**:
  - `GET /api/v1/products?ean=&business=LIVERPOOL,SUBURBIA`: generic, but puts the business rule
    in the client;
  - `POST /api/v1/product-searches`: a "search" resource adds nothing for one parameter, and GET is
    cacheable;
  - `GET /api/v1/mercaderias/products?ean=`: makes the scope explicit in the path, but introduces a
    business-line path segment before the domain model needs it.
  
  If a later feature needs a non-Mercaderías search, a separate parameter or path can be added then.

## R-06 Validation (D-3, approved 2026-11-08)

- **Decision**: Two layers on the backend.
  1. **Bean Validation** on the request parameter: required, at most 20 characters, only digits,
     spaces and dashes. This needs the `spring-boot-starter-validation` dependency, with its version
     managed by Spring Boot 4.1.2.
  2. **Domain rule** in `Ean.parse`: remove spaces and dashes, then require 8, 12, 13 or 14 digits
     (spec FR-003, A-004; check digit not validated).
  
  Errors map to one Problem Details format (R-07) with a machine-readable code per field:
  `REQUIRED`, `INVALID_CHARACTERS`, `TOO_LONG` or `INVALID_LENGTH`. The frontend applies the same
  normalization and length rule before calling the backend (FR-010: no search runs on invalid
  input), but the backend stays authoritative.
- **Rationale**: Constitution VII ("Bean Validation plus domain rules") and VIII. The domain rule
  sits in `Ean` so future features (proposal creation, bulk upload) reuse it.
- **Alternatives**: Domain validation only (no new dependency, but does not meet VII's "Bean
  Validation" wording); regex only (cannot express the allowed lengths clearly).

## R-07 Error format (D-4, approved 2026-11-08)

- **Decision**: All API errors use RFC 9457 Problem Details (the successor of RFC 7807, which
  constitution VII recommends), produced by one `@RestControllerAdvice` using Spring's built-in
  `ProblemDetail`.
  - Fields: `type` (omitted when it is `about:blank`, RFC 9457 §3.1.1), `title`, `status`, `detail`, `instance`, plus an `errors` array of
    `{ field, code, message }`.
  - `spring.mvc.problemdetails.enabled: true` covers framework errors (for example, an unknown
    route) in the same format.
  - Messages are in Spanish, for consistency with the UI.
  - This is recorded as the project-wide API convention in `docs/decisions/0001-api-conventions.md`,
    together with the `{ items, total }` collection envelope.
- **Rationale**: Constitution VII: single consistent error format and envelope, centralized
  handler. This is the first real endpoint, so it sets the convention.
- **Alternatives**: Custom error JSON (non-standard); plain-text errors (unusable by clients).

## R-08 CORS configured outside source code

- **Decision**: A global CORS configuration for `/api/**`. It allows `GET` from origins listed in
  the property `pim.cors.allowed-origins`, bound to the environment variable
  `PIM_CORS_ALLOWED_ORIGINS`, defaulting in `application.yaml` to `http://localhost:5173`.
  `HelloController`'s hard-coded `@CrossOrigin("http://localhost:5173")` is **removed**: it is
  environment-specific configuration in source code (constitution VIII), and the global
  configuration already covers `/api/v1/hello`. An integration test proves the hello endpoint still
  allows the configured origin. *Revised after analysis finding C1; the original plan had left the
  annotation untouched.*
- **Rationale**: The browser at `localhost:5173` calls the backend at `localhost:8080`, so CORS is
  needed. Constitution VIII: environment-specific configuration outside source code.
- **Alternatives**: `@CrossOrigin` on the new controller (hard-codes the origin again); a Vite dev
  proxy (changes the frontend URL model from feature 001).

## R-09 API contract

- **Decision**: An OpenAPI 3.1 document written by hand in
  [contracts/product-search.openapi.yaml](./contracts/product-search.openapi.yaml), kept in sync
  through the API integration tests, which assert every documented status, field and error code.
- **Rationale**: Constitution VII requires an OpenAPI contract in `contracts/`. Writing it by hand
  avoids a documentation-generation dependency for one endpoint.
- **Alternatives**: springdoc-openapi (new dependency; generated docs reflect the code rather than
  the agreed contract).

## R-10 Backend tests

- **Decision**: JUnit 5 with Spring Boot's test starter (already present). No new test dependency.
  - **Unit tests**:
    - `EanTest`: accepted lengths 8, 12, 13 and 14; spaces and dashes removed; letters, empty input
      and wrong lengths rejected with their codes.
    - `ProductSearchServiceTest`, with a small fixture repository: exact match only; duplicate EAN
      across Liverpool and Suburbia returns both, Liverpool first; Marketplace excluded;
      no results.
    - `SampleProductCatalogTest`: at least 500 products; every scenario EAN as specified;
      `7509999999999` absent; no EAN repeated within a business.
  - **API integration test** `ProductControllerIntegrationTest` (`@SpringBootTest` + MockMvc,
    real sample catalog):
    - 200 for the exact match, the duplicate across businesses, the absent EAN (empty list), the
      Marketplace-only EAN (empty list) and an EAN with spaces and dashes;
    - 400 Problem Details for a missing parameter, letters, more than 20 characters and a wrong
      length, asserting status, content type, `errors[].field` and `errors[].code`;
    - a CORS preflight from the configured origin is allowed.
  - The existing `contextLoads` test stays.
- **Rationale**: Constitution VI: unit tests for business logic, API integration tests for the
  success path and error paths, and every validation rule's accepted and rejected cases with
  status and payload. These cover every case the user listed.
- **Alternatives**: `@WebMvcTest` slices only (would not exercise the real catalog wiring).

## R-11 Backend formatter and static checks (D-2, approved 2026-11-08)

- **Decision**: Add the **Spotless** Gradle plugin (8.11.0) with **palantir-java-format**
  (2.103.0: 4-space indentation, close to Spring's style).
  - `spotlessCheck` runs as part of `./gradlew build`, and `spotlessApply` formats.
  - The three existing Java files are reformatted once (whitespace only).
  - Compiler warnings: `-Xlint:all -Werror` on main sources, as the static check.
- **Rationale**: Constitution V: "a configured Java formatter/static checks for backend MUST pass
  with zero errors". None exists today, and this feature adds most of the backend code.
- **Alternatives**:
  - google-java-format 2.0.0 (2-space style, further from the existing code);
  - Checkstyle (rule configuration to maintain, and it does not format);
  - leave it for later (would be a constitution V deviation needing Complexity Tracking).

## R-12 Frontend backend-URL helper (refactor of feature 001 code; D-5, approved 2026-11-08)

- **Decision**: Move the URL-building logic from `HomePage.tsx` (`getHelloUrl`) into
  `src/api/apiConfig.ts` as `buildApiUrl(path): string | null`. It keeps the same behaviour:
  trailing slash removed; `null` when `VITE_API_BASE_URL` is missing. `HomePage` and the new
  products API client both use it. HomePage's existing tests must pass unchanged.
- **Rationale**: Constitution V: duplicated logic must be consolidated rather than copied; the new
  client needs the same rule.
- **Alternatives**: Copy the function (violates V).

## R-13 Results display

- **Decision**:
  - **Matches**: a warning Notification ("Ya existe un producto con este EAN. En Mercaderías no
    se crea otra vez un producto existente.", FR-008), then one compact Card per product with
    title, SKU, EAN and brand, and a neutral StatusBadge naming the business (FR-006).
  - **No matches**: a success Notification ("No encontramos un producto con este EAN. Puedes
    continuar con una propuesta nueva.", FR-007).
  - **Invalid input**: the TextField's error message (FR-010).
  - **Connection failure**: an error Notification with a "Reintentar" button (FR-011).
  - There is no table: the Table component was deferred in feature 001, and at most two results are
    possible (one per business).
- **Rationale**: Uses only feature 001 components (FR-013). Two results do not justify building a
  table now.
- **Alternatives**: Build the deferred Table now (scope creep).

## R-14 Page, navigation and request handling

- **Decision**:
  - **Page**: `src/pages/ProductSearchPage.tsx` at `#/buscar-producto`, with the navigation entry
    "Buscar producto" and the `search` icon (spec A-002).
  - **Form**: a `<form>`, so Enter and the "Buscar" button behave the same (FR-002). The TextField
    is labelled "EAN", with `inputMode="numeric"`, `maxLength={20}` and the hint "8, 12, 13 o 14
    dígitos".
  - **Requests**: an `AbortController` cancels the previous request when a new search starts, so
    only the latest results are shown (edge case). Previous results are cleared when a new search
    starts or fails (edge case: no stale results).
- **Rationale**: Spec edge cases and FR-002. Hash navigation follows feature 001.
- **Alternatives**: Debounced search-as-you-type (the spec asks for a button).

## R-15 Frontend tests with mocked HTTP

- **Decision**: Stub `fetch` with `vi.stubGlobal` and set `VITE_API_BASE_URL` with `vi.stubEnv`, as
  feature 001 does. No new dependency.
  - **Unit tests**:
    - `ean.test.ts`: normalization and each rejection reason;
    - `productsApi.test.ts`: request URL, mapping of 200, 400 Problem Details, network failure and
      a missing base URL.
  - **Page tests** (`ProductSearchPage.test.tsx`):
    - a successful search with one result and with two businesses;
    - no results;
    - each validation error, with `fetch` not called;
    - connection error and retry;
    - Enter submits;
    - a stale response is ignored.
  - The mocks replace only the network call. The real integration is covered by the backend API
    tests and the quickstart's end-to-end run.
- **Rationale**: User instruction; constitution VI.
- **Alternatives**: MSW (a new dependency for the same result).

## R-16 Unchanged on purpose

- No database, JPA, Hibernate, migrations or MongoDB; no P360, SAP or external catalog
  (constitution IX, user instruction).
- No authentication; the page is open in this phase (spec A-001).
- The constitution, the hello endpoint's behaviour and feature 001's components stay as they are.
