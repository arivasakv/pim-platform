---

description: "Task list for feature 002: search for an existing product by EAN"
---

# Tasks: Search for an Existing Product (EAN)

**Input**: Design documents from `specs/002-existing-product-search/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md) (approved, amended to EAN only),
[research.md](./research.md), [data-model.md](./data-model.md),
[contracts/product-search.openapi.yaml](./contracts/product-search.openapi.yaml),
[quickstart.md](./quickstart.md)

**Tests**: Included. Constitution VI requires them, and the user listed the cases for this plan.
Each test task comes before the code it checks. Write it first and confirm it fails (a compile
failure counts as failing).

**Organization**: The spec has one user story (US1, P1: find an existing product by EAN). Backend
and frontend work for US1 are separate sub-sections, so they can proceed in parallel.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story the task belongs to (US1)
- Paths are relative to the repository root. Commands are **Windows PowerShell**. Gradle runs in
  `backend\` (`.\gradlew.bat …`); npm runs in `frontend\`.

## Ground rules for every task

- **MongoDB for the product catalog only** (constitution IX 2.2.0; amended 2026-11-09, Phase 6):
  accessed through Spring Data MongoDB behind `ProductRepository`. No JPA, Hibernate, migrations,
  SQL, embedded or other database dependency or configuration. No P360, SAP or external catalog
  (plan "Storage"). Phases 1–5 were implemented with an in-memory catalog, before this amendment.
- **The frontend holds no product data and does no matching.** Its EAN check gives instant
  feedback only; the backend's check is authoritative (research R-01).
- **Do not modify** `.specify/memory/constitution.md`, or `HelloController`'s behaviour. It is
  reformatted in T003, and its hard-coded `@CrossOrigin` is removed in T035 (finding C1); its
  response stays the same.
- **Java**: Java 21, records for data, constructor injection only, no field injection
  (constitution V). Packages follow constitution XII: `com.pim.backend.product.{controller,
  service, repository, model, dto, config}`; cross-cutting code only in `com.pim.backend.config`
  and `com.pim.backend.common`; nothing new in the root package. Phases 1–5 used the earlier flat
  `product` and `web` packages (their task paths are kept as history); the backend refactor
  (commit e8fba29) moved that code into the XII layout.
- **Frontend**: TypeScript only, feature 001 components and tokens only, Spanish (es-MX) text,
  Prettier style (no semicolons, single quotes).
- **Spanish texts**, used verbatim by both backend and frontend:

  | Key | Text |
  |---|---|
  | `REQUIRED` | "Escribe un EAN." |
  | `TOO_LONG` | "El EAN no puede tener más de 20 caracteres." |
  | `INVALID_CHARACTERS` | "El EAN solo puede contener números." |
  | `INVALID_LENGTH` | "El EAN debe tener 8, 12, 13 o 14 dígitos." |
  | found (FR-008) | "Ya existe un producto con este EAN. En Mercaderías no se crea otra vez un producto existente." |
  | not found (FR-007) | "No encontramos un producto con este EAN. Puedes continuar con una propuesta nueva." |
  | connection error (FR-011) | "No se pudo conectar con el servidor. Inténtalo de nuevo." (button "Reintentar") |
  | searching | "Buscando…" |
  | Problem `title` | "EAN no válido" |
  | Problem `detail` | "La solicitud tiene datos no válidos." |

---

## Phase 1: Setup (backend tooling and API conventions: decisions D-2, D-3, D-4)

**Purpose**: Bring the backend in line with constitution V and VII before writing feature code.

- [X] T001 From the repository root, record the baseline commit with `git rev-parse HEAD | Out-File -Encoding utf8 "$env:TEMP\pim-002-baseline.txt"` (expected `92b191e…`). Confirm `git status --short -- backend/ frontend/` prints nothing before starting
- [X] T002 Update `backend\build.gradle` (research R-06, R-11; plan D-2, D-3):
  - add the plugin `id 'com.diffplug.spotless' version '8.11.0'`;
  - add the dependency `implementation 'org.springframework.boot:spring-boot-starter-validation'`, with no version (managed by Spring Boot 4.1.2);
  - add `spotless { java { target 'src/*/java/**/*.java'; palantirJavaFormat('2.103.0'); removeUnusedImports(); trimTrailingWhitespace(); endWithNewline() } }`;
  - add `tasks.named('compileJava') { options.compilerArgs += ['-Xlint:all', '-Werror'] }`.
  
  Do not add any persistence dependency. If `-Xlint:all -Werror` fails on a warning that does not come from project code, stop and report it instead of weakening the flag
- [X] T003 In `backend\`, run `.\gradlew.bat spotlessApply`. This reformats `BackendApplication.java`, `HelloController.java` and `BackendApplicationTests.java`, whitespace only. Then confirm `.\gradlew.bat build` reports BUILD SUCCESSFUL, which proves `spotlessCheck` and the existing `contextLoads` test pass. `git diff -w -- backend/src` must show no change other than import order
- [X] T004 [P] Create `docs\decisions\0001-api-conventions.md` (research R-07, plan D-4). This ADR records:
  - **Status**: Accepted, 2026-11-08, approved by the project owner.
  - **Context**: constitution VII; this is the first real endpoint.
  - **Decision**:
    - every API error is RFC 9457 Problem Details (`type`, `title`, `status`, `detail`, `instance`, plus `errors[]` of `{ field, code, message }`), with messages in Spanish;
    - errors are produced by one `@RestControllerAdvice`;
    - `spring.mvc.problemdetails.enabled: true` covers framework errors;
    - every collection response is `{ "items": [...], "total": n }`;
    - a filtered collection with no match is 200 with `items: []`, never 404.
  - **Consequences**, and **alternatives**: custom error JSON, plain-text errors.

**Checkpoint**: `.\gradlew.bat build` passes with the formatter, lint flags and validation starter
in place.

---

## Phase 2: Foundational (shared configuration and the frontend URL helper: decisions D-5 and R-08)

**Purpose**: Cross-cutting pieces the US1 backend and frontend both rely on.

**⚠️ CRITICAL**: Complete before starting US1.

- [X] T005 Update `backend\src\main\resources\application.yaml` (research R-07, R-08). Keep `spring.application.name: backend` and add:
  - `spring.mvc.problemdetails.enabled: true`;
  - `pim.cors.allowed-origins: ${PIM_CORS_ALLOWED_ORIGINS:http://localhost:5173}`, a comma-separated list.
- [X] T006 [P] Create `backend\src\main\java\com\pim\backend\web\CorsConfiguration.java`: a `@Configuration` implementing `WebMvcConfigurer`. It reads `pim.cors.allowed-origins` through constructor injection of `@Value("${pim.cors.allowed-origins}") String`, splits it on commas, and trims each entry. `addCorsMappings` maps `/api/**` with `allowedOrigins(...)`, `allowedMethods("GET")` and `allowedHeaders("*")`. No origin is hard-coded in Java (constitution VIII). `HelloController`'s `@CrossOrigin` stays as is (research R-08)
- [X] T007 [P] Write `frontend\src\api\apiConfig.test.ts` first (research R-12):
  - with `vi.stubEnv('VITE_API_BASE_URL', 'http://servidor.ejemplo:9090')`, `buildApiUrl('/api/v1/hello')` returns `'http://servidor.ejemplo:9090/api/v1/hello'`;
  - a trailing slash in the base URL is removed;
  - with `''` or only spaces, it returns `null`;
  - reset with `vi.unstubAllEnvs()`.
- [X] T008 Create `frontend\src\api\apiConfig.ts` exporting `buildApiUrl(path: string): string | null`. Read `import.meta.env.VITE_API_BASE_URL`, `trim()` it, remove trailing slashes, and return `null` when empty, otherwise `base + path`. Then refactor `frontend\src\pages\HomePage.tsx` to use `buildApiUrl('/api/v1/hello')` and remove its local `getHelloUrl`. Behaviour, messages and the `console.error` hint stay identical (plan D-5). Run `npm test` and confirm that `apiConfig.test.ts` and the unchanged `HomePage.test.tsx` all pass

**Checkpoint**: CORS and Problem Details are configured. The frontend has one URL helper, and
feature 001's tests are still green.

---

## Phase 3: User Story 1: Find an existing product by EAN (Priority: P1) 🎯 MVP

**Goal**: A supplier types an EAN on "Buscar producto" and sees the existing Liverpool and
Suburbia products with that exact EAN, or a "not found, you can continue" message. Marketplace
products never appear; invalid input never reaches the search (FR-001–FR-004, FR-006–FR-014).

**Independent Test**: With backend and frontend running, the quickstart API checks A1–A9 and
end-to-end scenarios 1–8 pass ([quickstart.md](./quickstart.md)).

### Backend tests for US1 (write first, confirm they fail)

- [X] T009 [P] [US1] Write `backend\src\test\java\com\pim\backend\product\EanTest.java` (data-model §Ean, research R-06), a plain JUnit 5 test:
  - **accepted**: `7501234567890` (value `7501234567890`), `750 1234-567890` (normalized to `7501234567890`), `12345670` (8 digits), `123456789012` (12), `17501234567893` (14);
  - **rejected**, as `InvalidEanException` with `code()`:
    - `null`, `""` and `"   "` → `REQUIRED`;
    - a 21-character value → `TOO_LONG`;
    - `licuadora` and `750#123` → `INVALID_CHARACTERS`;
    - `750123456` (9 digits) and `7501234567890123` (16 digits) → `INVALID_LENGTH`.
  
  The check digit is not validated (spec A-004), so `7501234567891` is accepted.
- [X] T010 [P] [US1] Write `backend\src\test\java\com\pim\backend\product\SampleProductCatalogTest.java` (data-model §Sample catalog):
  - `SampleProductCatalog.create()` returns at least 500 products, exactly 531;
  - the six scenario products exist with the exact SKU, EAN, title, brand and business in the data-model table;
  - no product has EAN `7509999999999`;
  - EAN `7501112223334` belongs only to a `MARKETPLACE` product;
  - "The same EAN may appear in different businesses but must not repeat within one business";
  - every SKU is unique and matches `^[0-9]{7}$`;
  - titles are 1–120 characters and brands 1–60 characters.
- [X] T011 [P] [US1] Write `backend\src\test\java\com\pim\backend\product\ProductSearchServiceTest.java`, with a small fixture list passed to `new InMemoryProductRepository(List.of(...))`, not the sample catalog:
  - an exact match returns only that product (a product whose EAN merely starts with the same digits is not returned);
  - the same EAN in `SUBURBIA` (SKU 2000002) and `LIVERPOOL` (SKU 2000001) returns both, Liverpool first;
  - two Liverpool products with the same EAN make the repository constructor throw `IllegalStateException` (uniqueness per business);
  - an EAN held only by `MARKETPLACE` returns an empty list;
  - an absent EAN returns an empty list;
  - the repository content is unchanged after searching (FR-012).
- [X] T012 [P] [US1] Write `backend\src\test\java\com\pim\backend\product\ProductControllerIntegrationTest.java`: `@SpringBootTest` with MockMvc (`@AutoConfigureMockMvc` from Spring Boot 4's `spring-boot-webmvc-test` module), using the real sample catalog. Assert against the contract `contracts\product-search.openapi.yaml`:
  - **200** responses:
    - `ean=7501234567890` → `total` 1, `items[0]` = `{sku:"1000001", ean:"7501234567890", title:"Licuadora 10 velocidades 600 W", brand:"MARCA DEMO", business:"LIVERPOOL"}`;
    - `ean=7502000000017` → `total` 2, `items[0].business` `LIVERPOOL`, `items[1].business` `SUBURBIA`;
    - `ean=7509999999999` → `{items:[], total:0}`;
    - `ean=7501112223334` → `{items:[], total:0}`;
    - `ean=750 1234-567890` → same as the first case;
    - `ean=12345670` and `ean=17501234567893` → one item each.
  - **400** responses, each with content type `application/problem+json`, `status` 400, `title` "EAN no válido", and `errors[*].field` `ean` with `errors[*].code` containing the expected code:
    - no `ean` parameter → `REQUIRED`;
    - `ean=` → `REQUIRED`;
    - `ean=licuadora` → `INVALID_CHARACTERS`;
    - 21 digits → `TOO_LONG`;
    - `ean=750123456` → `INVALID_LENGTH`.
  - **CORS**: an `OPTIONS /api/v1/products` preflight with `Origin: http://localhost:5173` and `Access-Control-Request-Method: GET` returns `Access-Control-Allow-Origin: http://localhost:5173`; the same preflight from `http://otro.ejemplo` is rejected.
  
  Run `.\gradlew.bat test` and confirm these tests fail.

### Backend implementation for US1

- [X] T013 [P] [US1] Create the domain types in `backend\src\main\java\com\pim\backend\product\`:
  - `Business.java`: enum `LIVERPOOL`, `SUBURBIA`, `MARKETPLACE`;
  - `Product.java`: `record Product(String sku, String ean, String title, String brand, Business business)`. Its compact constructor rejects null or blank fields, titles over 120 characters and brands over 60 characters with `IllegalArgumentException`.
- [X] T014 [P] [US1] Create the EAN rule in `backend\src\main\java\com\pim\backend\product\`:
  - `EanErrorCode.java`: enum `REQUIRED`, `TOO_LONG`, `INVALID_CHARACTERS`, `INVALID_LENGTH`, each with its Spanish message from the ground-rules table;
  - `InvalidEanException.java`: an unchecked exception carrying an `EanErrorCode code()`;
  - `Ean.java`: `record Ean(String value)` with `static Ean parse(String raw)`. It applies the data-model rules in order:
    1. missing or blank → `REQUIRED`;
    2. more than 20 characters → `TOO_LONG`;
    3. any character other than digits, spaces and dashes → `INVALID_CHARACTERS`;
    4. remove spaces and dashes;
    5. length not in {8, 12, 13, 14} → `INVALID_LENGTH`.
    
    No check-digit validation. The canonical constructor only accepts already-normalized digits of a valid length. T009 must pass.
- [X] T015 [US1] Create the persistence layer in `backend\src\main\java\com\pim\backend\product\`:
  - `ProductRepository.java`: interface with `List<Product> findByEan(Ean ean)`;
  - `InMemoryProductRepository.java`: implements it with constructor `InMemoryProductRepository(List<Product> products)`. It builds an unmodifiable `Map<String, List<Product>>` keyed by EAN, and throws `IllegalStateException` naming the EAN and business when an EAN repeats within the same business. `findByEan` returns an unmodifiable list, empty when there is no match. No write methods (FR-012).
  
  Depends on T013, T014.
- [X] T016 [US1] Create `backend\src\main\java\com\pim\backend\product\SampleProductCatalog.java`: a final class with `static List<Product> create()`, deterministic with no randomness (data-model §Sample catalog).
  - **Scenario products**: the six rows exactly as in the data-model table.
  - **Generated Liverpool and Suburbia products (500)**: SKUs `1100001`–`1100500`; EAN `"7503" + String.format("%09d", n)`; businesses alternating `LIVERPOOL` (odd n) and `SUBURBIA` (even n); title from neutral Spanish product words plus a size or capacity (for example "Sartén antiadherente 24 cm"), with no prices or shopping terms; brand from a fixed list of "… DEMO" names.
  - **Generated Marketplace products (25)**: SKUs `1200001`–`1200025`; EAN `"7504" + String.format("%09d", n)`; business `MARKETPLACE`.
  
  T010 must pass.
- [X] T017 [US1] Create `backend\src\main\java\com\pim\backend\product\ProductCatalogConfiguration.java`: a `@Configuration` with a `@Bean ProductRepository productRepository()` returning `new InMemoryProductRepository(SampleProductCatalog.create())`. The catalog is built once when the application context starts (research R-03)
- [X] T018 [US1] Create `backend\src\main\java\com\pim\backend\product\ProductSearchService.java`: a `@Service` with constructor injection of `ProductRepository`. The method `List<Product> findExistingMercaderiasProducts(Ean ean)` keeps only `business` in `{LIVERPOOL, SUBURBIA}` (FR-014) and orders by business (Liverpool, then Suburbia), then by `sku` (gap G-1). No HTTP types. T011 must pass
- [X] T019 [US1] Create the API in `backend\src\main\java\com\pim\backend\product\`:
  - `ProductSummaryResponse.java`: record `sku, ean, title, brand, business`, with `business` serialized as the enum name;
  - `ProductCollectionResponse.java`: record `List<ProductSummaryResponse> items, int total`;
  - `ProductController.java`: `@RestController` with `@RequestMapping("/api/v1/products")` and a `@GetMapping` method taking `@RequestParam(name = "ean", required = false) @NotBlank(message = "REQUIRED") @Size(max = 20, message = "TOO_LONG") @Pattern(regexp = "^[0-9 -]*$", message = "INVALID_CHARACTERS") String ean`. It calls `Ean.parse(ean)`, then `ProductSearchService`, maps to DTOs, and returns 200. HTTP mapping only, no business rules (constitution III).
  
  Bean Validation messages carry the error code; the Spanish text comes from `EanErrorCode` in T020.
- [X] T020 [US1] Create `backend\src\main\java\com\pim\backend\web\ApiExceptionHandler.java`: a `@RestControllerAdvice` that builds Spring `ProblemDetail` responses (status 400, `title` "EAN no válido", `detail` "La solicitud tiene datos no válidos.", `instance` = request path). It sets the property `errors` to a list of `{field, code, message}` for:
  - `InvalidEanException` → one error from its code;
  - `HandlerMethodValidationException` and `ConstraintViolationException` (Bean Validation on the parameter) → one error per violation, with the code from the constraint message and the Spanish text from `EanErrorCode`;
  - `MissingServletRequestParameterException` for `ean` → `REQUIRED`.
  
  The content type is `application/problem+json`. T012 must pass.
- [X] T021 [US1] In `backend\`, run `.\gradlew.bat build` (spotlessCheck, `-Werror` compile and all tests). It must report BUILD SUCCESSFUL, with T009–T012 and `contextLoads` passing

### Frontend tests for US1 (write first, confirm they fail)

- [X] T022 [P] [US1] Write `frontend\src\products\ean.test.ts` for `validateEan(raw)` from `frontend\src\products\ean.ts`. It mirrors the backend rule (data-model §Ean):
  - `'7501234567890'` → `{ ok: true, ean: '7501234567890' }`;
  - `'750 1234-567890'` → `{ ok: true, ean: '7501234567890' }`;
  - `'12345670'` and `'17501234567893'` → ok;
  - `''` and `'   '` → `{ ok: false, code: 'REQUIRED' }`;
  - 21 characters → `TOO_LONG`;
  - `'licuadora'` → `INVALID_CHARACTERS`;
  - `'750123456'` → `INVALID_LENGTH`;
  - `EAN_MESSAGES[code]` equals the Spanish texts in the ground rules.
- [X] T023 [P] [US1] Write `frontend\src\api\productsApi.test.ts` for `searchProductsByEan(ean, signal?)`, stubbing `fetch` with `vi.stubGlobal` and setting `vi.stubEnv('VITE_API_BASE_URL', 'http://servidor.ejemplo:9090')`:
  - calls `http://servidor.ejemplo:9090/api/v1/products?ean=7501234567890` with the signal;
  - a 200 body `{items:[…], total:1}` is returned typed as `ProductCollection`;
  - a 400 Problem body with `errors[{code:'INVALID_LENGTH'}]` throws `ProductSearchError` with `kind: 'validation'` and `codes: ['INVALID_LENGTH']`;
  - a rejected `fetch`, a 500, and a missing base URL (no fetch call) each throw `ProductSearchError` with `kind: 'unavailable'`;
  - an `AbortError` is rethrown unchanged.
- [X] T024 [P] [US1] Write `frontend\src\pages\ProductSearchPage.test.tsx`, stubbing `fetch` and `VITE_API_BASE_URL`:
  - **Layout**: heading level 1 "Buscar producto"; field labelled "EAN"; "Buscar" button.
  - **Found, one business**: typing `7501234567890` and clicking "Buscar" calls fetch once and shows the found warning, a card with "Licuadora 10 velocidades 600 W", "1000001", "MARCA DEMO" and the badge "Liverpool".
  - **Found, two businesses**: two cards in order Liverpool, Suburbia.
  - **Not found**: `{items:[], total:0}` shows the not-found text.
  - **Validation**: `''`, `'licuadora'` and `'750123456'` each show their field error (`aria-invalid="true"`), and fetch is not called.
  - **Enter key**: pressing Enter in the field submits.
  - **Connection error**: a rejected fetch shows the connection error and "Reintentar"; clicking it repeats the request; no previous results remain on screen.
  - **Backend validation**: a 400 Problem shows the field message for its code.
  - **Stale response**: a slow first response that arrives after a second search does not replace the second search's results.
  - **No other search types**: the page has no file input and no text mentioning image or title search (FR-009).

### Frontend implementation for US1

- [X] T025 [P] [US1] Create `frontend\src\products\ean.ts`, exporting:
  - `type EanErrorCode = 'REQUIRED' | 'TOO_LONG' | 'INVALID_CHARACTERS' | 'INVALID_LENGTH'`;
  - `EAN_MESSAGES: Record<EanErrorCode, string>`, with the Spanish texts;
  - `validateEan(raw: string): { ok: true; ean: string } | { ok: false; code: EanErrorCode }`, applying the same five ordered rules as the backend (data-model §Ean).
  
  T022 must pass.
- [X] T026 [P] [US1] Create `frontend\src\api\productsApi.ts`, exporting:
  - types `Business = 'LIVERPOOL' | 'SUBURBIA'`, `ProductSummary` and `ProductCollection` (contract schemas);
  - `class ProductSearchError extends Error`, with `kind: 'validation' | 'unavailable'` and `codes: EanErrorCode[]`;
  - `searchProductsByEan(ean: string, signal?: AbortSignal): Promise<ProductCollection>`. It uses `buildApiUrl(`/api/v1/products?ean=${encodeURIComponent(ean)}`)`. With no URL configured, it throws `unavailable` without calling fetch. A 400 with a Problem body maps `errors[].code` to `validation`; any other non-OK status, or a network failure, is `unavailable`; an `AbortError` is rethrown.
  
  T023 must pass.
- [X] T027 [US1] Create `frontend\src\pages\ProductSearchPage.tsx` and `ProductSearchPage.module.css` (research R-13, R-14; data-model §Frontend view state):
  - **Header**: `PageHeader` with title "Buscar producto" and description "Revisa si el producto ya existe antes de proponerlo. Busca por EAN.".
  - **Form**: a `<form noValidate onSubmit>` containing `TextField` (`id="ean"`, `label="EAN"`, `type="search"`, `inputMode="numeric"`, `maxLength={20}`, hint "8, 12, 13 o 14 dígitos", `error` from `EAN_MESSAGES`) and `Button type="submit"` "Buscar". The button stays **enabled** while searching, so a new search can start and replace a running one (spec edge case; corrected after analysis finding I1).
  - **Searching**: on submit, `validateEan`; when invalid, show the field error and do not call the API. When valid, abort any previous request through an `AbortController`, clear previous results, show "Buscando…", and call `searchProductsByEan`. Ignore responses from aborted requests.
  - **Results**:
    - `total > 0`: a `Notification variant="warning"` with the found text, then one `Card` per item (title as card title; SKU, EAN and brand as labelled text; a `StatusBadge variant="neutral"` showing "Liverpool" or "Suburbia").
    - `total = 0`: a `Notification variant="success"` with the not-found text.
    - `validation` error: the field error for the first code.
    - `unavailable` error: a `Notification variant="error"` with the connection-error text and a `Button variant="secondary"` "Reintentar" that repeats the last valid search.
  - **Styling**: only `var(--…)` tokens in the CSS; no product data in the file.
  
  T024 must pass.
- [X] T028 [US1] Add `{ path: '/buscar-producto', label: 'Buscar producto', icon: 'search' }` to `NAVIGATION_ENTRIES` in `frontend\src\navigation.ts` (after "Componentes"). Add `getPathFromHash('#/buscar-producto')` → `'/buscar-producto'` to `frontend\src\navigation.test.ts`. Add `case '/buscar-producto': return <ProductSearchPage />` to the switch in `frontend\src\App.tsx`
- [X] T029 [US1] In `frontend\`, run `npm run lint`, `npm run format:check`, `npm test` and `npm run build`. All must pass with 0 errors, including feature 001's tests

**Checkpoint (MVP)**: The backend endpoint and the frontend page work end to end for every US1
scenario.

---

## Phase 4: Polish & Validation

- [X] T030 Run the quickstart API checks A1–A9 against a running backend (`.\gradlew.bat bootRun` in `backend\`; `Invoke-RestMethod` / `Invoke-WebRequest` as in [quickstart.md](./quickstart.md)). Confirm each response matches `contracts\product-search.openapi.yaml`: fields, status codes, `application/problem+json`, error codes. Fix any mismatch in the code, not in the contract, unless the contract is wrong; in that case update the contract and report it
- [X] T031 Run the scope checks from the repository root:
  - `git diff --stat (Get-Content "$env:TEMP\pim-002-baseline.txt") -- .specify/memory/constitution.md` prints nothing;
  - `Select-String -Path backend\build.gradle -Pattern 'jpa|hibernate|mongo|flyway|liquibase|postgres|mysql|h2'` prints nothing;
  - `Get-ChildItem frontend\src -Recurse -Include *.ts,*.tsx -Exclude *.test.* | Select-String -Pattern '7501234567890|7502000000017|MARCA DEMO'` prints nothing (no product data in the frontend);
  - in `frontend\`, `npm ls --depth=0` shows no new dependency;
  - `git diff -w (Get-Content "$env:TEMP\pim-002-baseline.txt") -- backend/src/main/java/com/pim/backend/HelloController.java` shows no behaviour change.
- [X] T032 Run the quickstart end-to-end browser scenarios 1–11 with backend and frontend running. Scenarios that need a browser are confirmed by the project owner; report them as pending until confirmed
- [X] T033 Report the results directly in the implementation summary: pass or fail for each automated check (Gradle build, npm lint, format, test and build), each API check (A1–A9), each scope check and each end-to-end scenario, with error output for any failure

---

## Phase 5: Analysis remediation (findings C1, I1, I2)

**Purpose**: Resolve the `/speckit-analyze` findings selected by the user. Order follows the
constitution: documents, then tests, then code.

- [X] T034 Update the documents (no code):
  - `research.md` R-08: remove `HelloController`'s hard-coded `@CrossOrigin` (C1);
  - `plan.md` source tree: `HelloController.java` marked [update] (C1);
  - `tasks.md` ground rules: point to T035 (C1); T027: the button stays enabled while searching (I1);
  - `data-model.md` view state `searching`: button stays enabled (I1); Problem fields: `type` omitted when `about:blank` (I2);
  - `research.md` R-07: the same `type` note (I2).
- [X] T035 Write `backend\src\test\java\com\pim\backend\HelloControllerIntegrationTest.java` (`@SpringBootTest` + MockMvc). It checks two things:
  - `GET /api/v1/hello` still returns 200 with "Hello from PIM Backend!";
  - an `OPTIONS /api/v1/hello` preflight with `Origin: http://localhost:5173` still returns `Access-Control-Allow-Origin: http://localhost:5173`.
  
  This test guards behaviour, so it passes before and after the change. Then remove `@CrossOrigin(origins = "http://localhost:5173")` and its import from `backend\src\main\java\com\pim\backend\HelloController.java`, and confirm the test still passes: the origin now comes only from `pim.cors.allowed-origins` (constitution VIII).
- [X] T036 Verify:
  - `.\gradlew.bat build` passes (all tests, format check, warnings as errors);
  - `git grep -n "localhost:5173" -- backend/src/main/java` prints nothing (no origin in Java code);
  - no frontend file changed.

**After implementation (not a task)**: the SC-006 walkthrough (at least 4 of 5 users tell
correctly whether the product exists), then `/speckit-analyze`.

---

## Phase 6: Amendment 2026-11-09: MongoDB product catalog (decisions D-6 to D-9)

**Purpose**: Store the sample catalog in the local MongoDB instead of backend memory
(constitution 2.2.0, Principle IX; spec Clarifications 2026-11-09; research R-17 to R-20). Search
behavior, validation, messages and the 200/400 responses stay the same; a 503 response is added.
No frontend change. The in-memory repository must not remain a production implementation, and
nothing may fall back to sample data when MongoDB is unavailable.

- [X] T037 Update the documents first: spec (Clarifications 2026-11-09, TD-001, Out of Scope), plan (Storage, dependencies, Constitution Check II/IX/XII, D-6 to D-9, source tree, Complexity Tracking), research (R-03 superseded; R-16; R-17 to R-20), data-model (stored document and indexes), quickstart (MongoDB prerequisite, A10, A11, scenario 8b), the contract (503 response; description), `docs/decisions/0002-mongodb-product-catalog.md` (new) and `docs/decisions/0001-api-conventions.md` (503).
- [X] T038 Add `implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'` to `backend\build.gradle` (Boot-managed version). In `backend\src\main\resources\application.yaml` add `spring.mongodb.uri: ${PIM_MONGODB_URI:mongodb://localhost:27017/pim?serverSelectionTimeoutMS=2000}` and `pim.catalog.seed-sample-data: ${PIM_CATALOG_SEED_SAMPLE_DATA:true}`. Create `backend\src\test\resources\config\application.yaml` pointing tests at database `pim-test` (`PIM_TEST_MONGODB_URI` overrides).
- [X] T039 [P] Write `backend\src\test\java\com\pim\backend\product\repository\MongoProductRepositoryIntegrationTest.java` (`@SpringBootTest`, local MongoDB, database `pim-test`; empties the collection before and after each test): exact EAN only (a similar EAN is not returned); the same EAN in Liverpool and Suburbia returns both; a Marketplace document is returned by the repository (the service filters it); an absent EAN returns an empty list; the unique index rejects the same EAN twice in one business and a repeated SKU (`DuplicateKeyException`); a search does not change the collection.
- [X] T040 [P] Write `backend\src\test\java\com\pim\backend\product\config\ProductCatalogInitializerIntegrationTest.java`: on an empty collection the initializer creates `ean_business_unique` and `sku_unique` and loads 531 products; running it again inserts nothing; with seeding off it creates the indexes and inserts nothing.
- [X] T041 [P] Write the unavailable-store tests: `backend\src\test\java\com\pim\backend\product\repository\MongoProductRepositoryUnavailableTest.java` (a repository on an unreachable address throws `DataAccessResourceFailureException` within a few seconds) and `backend\src\test\java\com\pim\backend\product\controller\ProductControllerUnavailableIntegrationTest.java` (`@MockitoBean ProductRepository` throwing that exception; `GET /api/v1/products?ean=7501234567890` returns 503 `application/problem+json`, title "Servicio no disponible", Spanish detail, `instance`, no `errors`).
- [X] T042 Create in `backend\src\main\java\com\pim\backend\product\repository\`: package-private `ProductDocument` (`@Document("products")`; `id`, `sku`, `ean`, `title`, `brand`, `business`; `from(Product)`, `toProduct()`), package-private `ProductDocumentRepository extends MongoRepository<ProductDocument, String>` with `List<ProductDocument> findByEan(String ean)`, and public `MongoProductRepository implements ProductRepository` (`@Repository`). Update the `ProductRepository` Javadoc.
- [X] T043 Create `backend\src\main\java\com\pim\backend\product\repository\MongoProductCatalogLoader.java` (`createIndexes()`: unique `ean_business_unique` `{ean:1, business:1}` and unique `sku_unique` `{sku:1}`; `int loadIfEmpty(List<Product>)`) and `backend\src\main\java\com\pim\backend\product\config\ProductCatalogInitializer.java` (`ApplicationRunner`: always creates the indexes; loads `SampleProductCatalog.create()` when `pim.catalog.seed-sample-data` is true). Delete `backend\src\main\java\com\pim\backend\product\config\ProductCatalogConfiguration.java`.
- [X] T044 Move `InMemoryProductRepository` from main to `backend\src\test\java\com\pim\backend\product\repository\` (test fake for `ProductSearchServiceTest`; that test is unchanged). In `ProductControllerIntegrationTest` load the sample catalog into `pim-test` before each test if it is empty; its assertions stay unchanged.
- [X] T045 Add `ApiProblems.serviceUnavailable(request)` in `backend\src\main\java\com\pim\backend\common\ApiProblems.java` (503, title "Servicio no disponible", detail "No se pudieron consultar los datos en este momento. Inténtalo de nuevo más tarde.") and `backend\src\main\java\com\pim\backend\common\DataAccessExceptionHandler.java` (`@RestControllerAdvice` for `DataAccessResourceFailureException`; logs a warning).
- [X] T046 Verify: `.\gradlew.bat test` and `.\gradlew.bat build` pass with the local MongoDB running; quickstart A1–A11 against `bootRun` on the local MongoDB (A10: 503 with MongoDB unreachable); `git status` shows no frontend change.
- [X] T047 Browser check by the project owner: quickstart end-to-end scenarios 2–5 and 8b on the MongoDB-backed backend.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)**: T001 first; then T002 → T003. T004 is independent.
- **Foundational (2)**: after Setup. T005 → T006 (T006 reads the property). T007 → T008.
- **US1 (3)**: after Foundational.
  - Backend: T009–T012 (tests) → T013, T014 → T015 → T016 → T017 → T018 → T019 → T020 → T021.
  - Frontend: T022–T024 (tests) → T025, T026 → T027 → T028 → T029.
  - The backend and frontend tracks are independent until the end-to-end checks.
- **Polish (4)**: T030 needs T021; T031 and T032 need T021 and T029; T033 is last.
- **Amendment (6)**: T037 first (documents), then T038; tests T039–T041 [P] before code T042 → T043 → T044 → T045; T046 verifies; T047 is the owner's browser check.

### Parallel opportunities

- **Setup**: T004 next to T002 and T003.
- **Foundational**: T006 next to T007 and T008.
- **US1**:
  - all four backend tests (T009–T012) and all three frontend tests (T022–T024) can be written at once;
  - T013 with T014, and T025 with T026;
  - the whole backend track runs alongside the whole frontend track.

## Parallel Example: User Story 1

```text
Task: "T009 EanTest.java"                      Task: "T022 ean.test.ts"
Task: "T010 SampleProductCatalogTest.java"     Task: "T023 productsApi.test.ts"
Task: "T011 ProductSearchServiceTest.java"     Task: "T024 ProductSearchPage.test.tsx"
Task: "T012 ProductControllerIntegrationTest.java"
```

---

## Implementation Strategy

1. **Setup and foundation** (T001–T008): backend tooling and conventions, CORS, and the shared
   frontend URL helper. Both projects stay green.
2. **Backend first** (T009–T021): the endpoint works on its own and can be checked with
   `Invoke-RestMethod` (quickstart A1–A9) before any UI exists.
3. **Frontend** (T022–T029): the page against the real endpoint.
4. **Validation** (T030–T033): contract, scope, and the end-to-end run, reported in the summary.
5. **Analysis remediation** (T034–T036): documents first, then the guarding test, then the
   `HelloController` change.
6. **Amendment 2026-11-09, MongoDB catalog** (T037–T047): documents and ADR 0002 first, then
   configuration, tests, the MongoDB repository, loader and 503 handler, verification against
   the local MongoDB, and the owner's browser check.

Each step leaves `.\gradlew.bat build` and the npm checks green (constitution X). Commits only when
the user asks.

## Notes

- **Requirement traceability**:

  | Requirement | Tasks |
  |---|---|
  | FR-001, FR-002 | T024, T027, T028 |
  | FR-003, FR-010 | T009, T012, T014, T019, T020, T022, T024, T025, T027 |
  | FR-004, FR-014 | T010, T011, T012, T015, T016, T018, T039, T042, T046, T047 |
  | FR-006 | T012, T019, T024, T027 |
  | FR-007, FR-008 | T024, T027 |
  | FR-009 | T024, T027, T031 |
  | FR-011 | T023, T024, T026, T027, T041, T045, T046, T047 |
  | FR-012 | T011, T015, T039, T042, T043 |
  | FR-013 | T027, T029 |
  | SC-001, SC-004, SC-006 | T032, T046, T047; after implementation |
  | SC-002 | T010, T012, T030, T040, T046 |
  | SC-005 | T009, T012, T022, T024 |
  | Constitution V and VII (D-2, D-3, D-4) | T002–T004, T020, T030 |
  | Constitution VIII (R-08) | T005, T006, T012 |
  | Plan D-5 | T007, T008 |

- [P] tasks touch different files and do not depend on unfinished tasks.
