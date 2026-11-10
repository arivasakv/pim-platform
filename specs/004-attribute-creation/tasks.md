---

description: "Task list for feature 004: attribute creation"
---

# Tasks: Attribute Management – Attribute Creation

**Input**: Design documents from `specs/004-attribute-creation/`

**Prerequisites**: [plan.md](./plan.md) (decisions D-1 to D-12 approved, D-4 option A),
[spec.md](./spec.md) (approved 2026-11-09), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/attributes.openapi.yaml](./contracts/attributes.openapi.yaml),
[quickstart.md](./quickstart.md)

**Tests**: Included. Constitution VI requires unit tests for business logic and frontend logic,
integration tests for every endpoint (success and error paths) and a test for every validation rule
with its HTTP status and error body. Each test task comes before the code it checks: write it first
and confirm it fails (a compile failure counts).

**Organization**: one phase per user story in priority order (US1, US2: P1; US3, US4: P2; US5: P3),
after a foundational phase. Backend and frontend parts of a story are separate sub-sections.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story the task belongs to (US1–US5)
- Commands are **Windows PowerShell**. Gradle runs in `backend\`, npm in `frontend\`.
- Path abbreviations: **`MAIN`** = `backend/src/main/java/com/pim/backend`,
  **`TEST`** = `backend/src/test/java/com/pim/backend`, **`FE`** = `frontend/src`.

## Ground rules for every task

- **Do not modify** feature 002 or 003 code or tests (`MAIN/product/**`, `MAIN/identifier/**`,
  `TEST/product/**`, `TEST/identifier/**`), their specs, `.specify/memory/constitution.md` or
  `CLAUDE.md` (decision D-4, option A). Existing frontend tests must keep passing unchanged.
- **Feature 003 only through `IdentifierService`** (constitution III) and the public model types its
  signature uses (`ObjectType`, `ManualIdPermissions`, `AssignmentResult`, its exceptions). Never call
  its repositories or controllers. **Do not define a second `Clock` bean**: inject the existing one
  (a second bean would make injection ambiguous for `IdentifierService`).
- **Key-field values sent to feature 003** (FR-006 as amended): read
  `identifierService.findConfiguration(ObjectType.ATTRIBUTE).keyFields()`; send
  `{"displayName": <stored display name>}` only if it contains `displayName`, otherwise `{}`. Never
  change feature 003's configuration.
- **In memory only** (D-1); **no new dependency** in `backend/build.gradle` or `frontend/package.json`.
- **Packages** (XII): backend code only in `com.pim.backend.attribute.{controller, service,
  repository, model, dto, config}`, plus `POST` in `config.CorsConfiguration` (T003). `model` uses no
  Spring/HTTP/DTO type; `dto` only in `controller`.
- **Errors**: RFC 9457 via `common.ApiProblems` (ADR 0001), Spanish `title`, `detail` and
  `errors[].message`; codes exactly as in the contract's `Problem.errors[].code` enum. Feature 003's
  errors keep feature 003's codes and Spanish messages (FR-005).
- **Contract**: `specs/004-attribute-creation/contracts/attributes.openapi.yaml` v1.0.0. If code and
  contract disagree, stop and report; do not change the contract silently.
- **Atomicity** (R-18): `AttributeService.create` runs "check display name → assign ID → save" under
  one lock.
- **Integration tests** use `@SpringBootTest` + `@AutoConfigureMockMvc` +
  `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` (attributes and system IDs are in memory),
  and need the local MongoDB `pim-test` like the existing suite.
- **Frontend**: React function components, TypeScript strict, existing design tokens and components;
  Spanish labels; no attribute data hard-coded (FR-022); `fetch` mocked in tests with `vi.stubGlobal`
  as in `FE/pages/ProductSearchPage.test.tsx`.
- After each phase: backend `.\gradlew.bat spotlessApply; .\gradlew.bat build`; frontend
  `npm run lint; npm run format:check; npm test; npm run build`. All pass.

---

## Phase 1: Setup

- [X] T001 Confirm the baselines on branch `004-attribute-creation`: with the local MongoDB running,
  `.\gradlew.bat clean build` in `backend\` passes (163 tests after feature 003), and in `frontend\`
  `npm run lint`, `npm run format:check`, `npm test` and `npm run build` pass. Record both test counts.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: CORS, the attribute model shared by all stories, repositories, the error handler, the
permission reader and the new design-system inputs. No story starts before this phase is complete.

### Backend

- [X] T002 [P] Write `TEST/attribute/controller/AttributeCorsIntegrationTest.java`: an `OPTIONS
  /api/v1/attributes` preflight with `Origin: http://localhost:5173`,
  `Access-Control-Request-Method: POST` and `Access-Control-Request-Headers:
  content-type,x-pim-permissions,x-pim-manual-id-permissions` answers 200 with
  `Access-Control-Allow-Origin: http://localhost:5173` and `POST` in `Access-Control-Allow-Methods`.
- [X] T003 Add `"POST"` to `allowedMethods` in `MAIN/config/CorsConfiguration.java` (keep `"GET"`,
  origins and headers unchanged; update its Javadoc). T002 passes; existing CORS tests unchanged.
- [X] T004 [P] Create enums in `MAIN/attribute/model/`, each with a Spanish `label()`:
  `AttributeDataType` in this order `TEXT` (Texto), `NUMERIC` (Numérico), `BOOLEAN` (Booleano),
  `LIST_OF_VALUES` (Lista de valores), `DATE_TIME` (Fecha y hora), `CURRENCY` (Moneda), `REFERENCE`
  (Referencia); `AttributeScope` (`GLOBAL`); `CaseFormat` (`UPPERCASE` Mayúsculas, `LOWERCASE`
  Minúsculas, `MIXED_CASE` Capitalización mixta); `NumberFormat` (`INTEGER` Entero, `DECIMAL`
  Decimal); `SelectionMode` (`SINGLE` Selección única, `MULTIPLE` Selección múltiple); `DateFormat`
  with codes `DD_MM_YYYY`, `MM_DD_YYYY`, `YYYY_MM_DD`, `DD_MM_YYYY_HH_MM`, `YYYY_MM_DD_HH_MM` whose
  API value and label are exactly `DD/MM/AAAA`, `MM/DD/AAAA`, `AAAA-MM-DD`, `DD/MM/AAAA HH:mm`,
  `AAAA-MM-DD HH:mm` (`fromValue(String)` lookup); `Cardinality` (`SINGLE` Una, `MULTIPLE` Varias);
  `ReferenceTargetType` with the same six codes as feature 003's `ObjectType` (`PRODUCT` Producto,
  `ATTRIBUTE` Atributo, `LIST_OF_VALUES` Lista de valores, `TEMPLATE` Plantilla, `CLASSIFICATION`
  Clasificación, `INTERFACE` Interfaz). Every enum gets `Optional<…> fromCode(String)` (exact match).
- [X] T005 [P] Create `MAIN/attribute/model/AttributeErrorCode.java`: one constant per attribute
  code of the contract (`REQUIRED` … `UNKNOWN_CARDINALITY`, `ATTRIBUTE_CREATE_NOT_PERMITTED`,
  `DISPLAY_NAME_IN_USE`, `LIST_OF_VALUES_NOT_AVAILABLE`, `LIST_OF_VALUES_NOT_FOUND`), each with a
  Spanish message template (`%s` where a value is named), e.g. `DISPLAY_NAME_IN_USE` → "Ya existe un
  atributo con el nombre \"%s\". Usa otro nombre.", `INVALID_LENGTH_RANGE` → "La longitud mínima no
  puede ser mayor que la longitud máxima.", `LIST_OF_VALUES_NOT_AVAILABLE` → "Aún no existen listas
  de valores. Un atributo de tipo lista de valores necesita una lista existente.",
  `ATTRIBUTE_CREATE_NOT_PERMITTED` → "No tienes permiso para crear atributos."
- [X] T006 [P] Create exceptions in `MAIN/attribute/model/` (unchecked, `serialVersionUID`, no
  Spring): `InvalidAttributeException` (field, `AttributeErrorCode`, message arguments; HTTP 400),
  `AttributeCreateNotPermittedException` (403), `DisplayNameInUseException` (display name; 409),
  `ListOfValuesUnavailableException` (code `LIST_OF_VALUES_NOT_AVAILABLE` or
  `LIST_OF_VALUES_NOT_FOUND`; 422), `AttributeNotFoundException` (requested ID; 404).
- [X] T007 [P] Write `TEST/attribute/model/DisplayNameTest.java` (FR-003, FR-023, D-7): "Trimmed;
  1–120 characters"; `null`, `""`, `"   "` and 121 characters → `InvalidAttributeException`
  (field `displayName`, code `REQUIRED` for null, `INVALID_DISPLAY_NAME` otherwise); `" Color "` →
  value `Color`; `sameNameAs` / equality ignores letter case with `Locale.ROOT`
  (`"Color"` = `" color "` = `"COLOR"`), value keeps the case given.
- [X] T008 Implement `MAIN/attribute/model/DisplayName.java` so T007 passes.
- [X] T009 [P] Write `TEST/attribute/model/AttributePermissionsTest.java` (FR-002, D-2):
  `fromCodes(null)` and `fromCodes("")` do not allow creation; `" attribute_create , X "` allows it
  (trimmed, case ignored, unknown codes ignored); `allowsCreation()`.
- [X] T010 Implement `MAIN/attribute/model/AttributePermissions.java` so T009 passes.
- [X] T011 [P] Write `TEST/attribute/model/AttributeTest.java`: `Attribute.create(systemId,
  displayName, description, dataType, properties, createdAt)` keeps the values, `scope()` is always
  `GLOBAL` (FR-018); description "Trimmed; up to 1,000 characters; blank = absent" (1,001 →
  `INVALID_DESCRIPTION`, field `description`; `"   "` → empty `Optional`); `BOOLEAN` requires
  `properties == null`; any other type requires properties of its own type (mismatch →
  `IllegalArgumentException`, a programming error, not a user error).
- [X] T012 Create `MAIN/attribute/model/AttributeProperties.java` (a plain marker interface for now: Java does not compile a sealed type without permitted
  subclasses; T051 seals it once all six property records exist) and `MAIN/attribute/model/Attribute.java` (record: `systemId` String,
  `displayName` DisplayName, `description` Optional<String>, `dataType`, `scope`, `properties`
  nullable, `createdAt` Instant) so T011 passes.
- [X] T013 Create interfaces `MAIN/attribute/repository/AttributeRepository.java`
  (`Optional<Attribute> findBySystemId(String)` ignoring letter case, `boolean
  existsWithDisplayName(DisplayName)` ignoring case, `void save(Attribute)`, `int count()`) and
  `MAIN/attribute/repository/ListOfValuesCatalog.java` (`boolean hasAny()`, `boolean exists(String
  listOfValuesId)`), with Javadoc (no business rules, XII).
- [X] T014 [P] Write `TEST/attribute/repository/InMemoryAttributeRepositoryTest.java`: save then
  find by ID in other letter case; `existsWithDisplayName` ignores case; `count`; saving a second
  attribute with the same ID or display name throws `IllegalStateException` (the service checks
  first).
- [X] T015 Implement `MAIN/attribute/repository/InMemoryAttributeRepository.java` (`@Repository`,
  maps keyed by upper-cased ID and by `DisplayName`'s comparison key; not thread-safe by itself, the
  service locks) so T014 passes, and `MAIN/attribute/repository/EmptyListOfValuesCatalog.java`
  (`@Repository`; `hasAny()` false, `exists` false; Javadoc: replaced by the list-of-values feature,
  DEP-03, D-12).
- [X] T016 [P] Create `MAIN/attribute/controller/RequestPermissionsReader.java` (`@Component`):
  `AttributePermissions attributePermissions(HttpServletRequest)` from header `X-Pim-Permissions`;
  `ManualIdPermissions manualIdPermissions(HttpServletRequest)` from header
  `X-Pim-Manual-Id-Permissions` via `ManualIdPermissions.fromCodes`. Javadoc: development stand-in
  until authentication exists (R-06, D-2).
- [X] T017 Create `MAIN/attribute/controller/AttributeExceptionHandler.java`:
  `@RestControllerAdvice(basePackageClasses = AttributeExceptionHandler.class)`,
  `@Order(Ordered.HIGHEST_PRECEDENCE)`. Map now: `InvalidAttributeException` → 400
  `ApiProblems.invalidRequest(request, "Solicitud no válida", [error])`;
  `MethodArgumentNotValidException` → 400 with `@NotNull` → `REQUIRED` (field = JSON path, e.g.
  `textProperties.maxLength`), `@Size` on `displayName` → `INVALID_DISPLAY_NAME`, `@Size` on
  `description` → `INVALID_DESCRIPTION`, `@Size` on `systemId` → `INVALID_SYSTEM_ID`, any other
  constraint → `IllegalStateException` (no silent fallback); `HttpMessageNotReadableException` →
  400 `REQUIRED` field `body` (covers non-numeric numbers). Later tasks add the other mappings.

### Frontend: design-system inputs (R-20)

- [X] T018 [P] Write `FE/components/ui/FormFields.test.tsx`: `SelectField` renders a labelled
  `<select>` with the given options and an empty placeholder option, calls `onChange` with the
  chosen code, shows `hint` and `error` (with `aria-invalid` and `aria-describedby`) like
  `TextField`; `CheckboxField` renders a labelled checkbox and toggles; `TextAreaField` renders a
  labelled `<textarea>` with `maxLength`, hint and error.
- [X] T019 Implement `FE/components/ui/SelectField.tsx`, `CheckboxField.tsx`, `TextAreaField.tsx`
  with `.module.css` files using only the tokens of `FE/styles/tokens.css`, mirroring
  `TextField.tsx`'s structure and accessibility; export them from `FE/components/ui/index.ts`.
  T018 passes; `Components.test.tsx` unchanged and passing.
- [X] T020 Add the three components to `FE/pages/ComponentGalleryPage.tsx` (one example each, Spanish
  labels) without changing what the page already shows; `ComponentGalleryPage.test.tsx` still passes.
- [X] T021 [P] Declare `VITE_DEV_PERMISSIONS?` and `VITE_DEV_MANUAL_ID_PERMISSIONS?` in
  `FE/vite-env.d.ts` (Javadoc-style comments: development stand-in, R-07) and add both, commented
  out with an explanation, to `frontend/.env.example` (no real values committed in `.env`).

**Checkpoint**: backend and frontend builds pass; nothing user-visible yet except the gallery.

---

## Phase 3: User Story 1 - Create an attribute and choose its data type (Priority: P1) 🎯 MVP

**Goal**: an authorized user creates a Boolean attribute (no type properties) from the "Crear
atributo" page; it gets a system ID from feature 003 and can be read back. Types come from the API.

**Independent Test**: quickstart API scenarios 1–7 and 17, UI scenarios U1, U4, U6, U7.

### Backend tests for User Story 1

- [X] T022 [P] [US1] Write `TEST/attribute/service/AttributeServiceTest.java` (US1 part). Build a
  real `IdentifierService` from feature 003's in-memory repositories seeded with
  `DefaultObjectTypeConfigurations`, a fixed `Clock`, an `InMemoryAttributeRepository` and a test
  `ListOfValuesCatalog`. Cases: BOOLEAN "Apto para regalo" with `ATTRIBUTE_CREATE` → `ATR000001`,
  `GLOBAL`, `createdAt` = clock; `identifierService.find("ATR000001")` is an `ATTRIBUTE`
  registration with `keyFieldValues` `{}` (FR-006, nothing declared); without `ATTRIBUTE_CREATE` →
  `AttributeCreateNotPermittedException`, no attribute and no feature 003 registration; blank or
  121-character name → `INVALID_DISPLAY_NAME`; 1,001-character description → `INVALID_DESCRIPTION`;
  duplicate `" apto PARA regalo "` → `DisplayNameInUseException`, feature 003 registry unchanged and
  the next creation gets `ATR000002`; manual `ATR-COLOR` with manual-ID permission `ATTRIBUTE` → that
  ID; manual ID without it → feature 003's `ManualIdNotPermittedException`, nothing saved; manual ID
  already used → `SystemIdInUseException`; `find("atr000001")` ignores case; unknown ID →
  `AttributeNotFoundException`. Also: when feature 003 declares `displayName` for `ATTRIBUTE`
  (set with `identifierService.replaceConfiguration` in the test), the registration carries
  `{displayName: "…"}`.
- [X] T023 [P] [US1] Write `TEST/attribute/controller/AttributeTypeControllerIntegrationTest.java`:
  `GET /api/v1/attribute-types` → 200 JSON, `total` 7, `items[*].code` in the T004 order, Spanish
  `label`s, options: TEXT `caseFormat` (3), NUMERIC `numberFormat` (2), LIST_OF_VALUES
  `selectionMode` (2), DATE_TIME `dateFormat` (5, codes `DD/MM/AAAA` …), REFERENCE
  `targetObjectType` (6) and `cardinality` (2); BOOLEAN and CURRENCY `options` empty objects.
- [X] T024 [P] [US1] Write `TEST/attribute/controller/AttributeControllerIntegrationTest.java` (US1
  part; header `X-Pim-Permissions: ATTRIBUTE_CREATE` unless stated): BOOLEAN → 201, `Location:
  /api/v1/attributes/ATR000001`, body `systemId`, `displayName`, `description` null, `dataType`,
  `scope` `GLOBAL`, `createdAt`, no properties object; `GET /api/v1/attributes/atr000001` → 200 same
  data; `GET /api/v1/attributes/ATR999` → 404 problem "No encontrado" naming the ID; no permission
  header → 403 `ATTRIBUTE_CREATE_NOT_PERMITTED` and `GET /api/v1/system-ids/ATR000001` → 404;
  missing `displayName` / `dataType` → 400 `REQUIRED`; blank name and a 121-character name →
  `INVALID_DISPLAY_NAME` (field `displayName`); a 1,001-character description → `INVALID_DESCRIPTION`
  (field `description`);
  `dataType` `COLOR` → `UNKNOWN_DATA_TYPE`; duplicate name ignoring case → 409
  `DISPLAY_NAME_IN_USE` (message names the name); manual ID: `ATR-COLOR` with
  `X-Pim-Manual-Id-Permissions: ATTRIBUTE` → 201; without that header → 403
  `MANUAL_ID_NOT_PERMITTED`; `A B` → 400 `INVALID_SYSTEM_ID`; `atr-color` again (other name) → 409
  `SYSTEM_ID_IN_USE` — each with feature 003's Spanish message; sequence exhausted (set the
  `ATTRIBUTE` sequence's `nextValue` to the maximum with `IdentifierService.replaceConfiguration`) →
  second creation 409 `SEQUENCE_EXHAUSTED`. Every error response is `application/problem+json`.

### Backend implementation for User Story 1

- [X] T025 [P] [US1] Create DTO records in `MAIN/attribute/dto/`: `AttributeCreationRequest`
  (`@Size(max = 60) String systemId`, `@NotNull @Size(max = 200) String displayName`,
  `@Size(max = 2000) String description`, `@NotNull String dataType`, and one nullable `@Valid`
  field per type: `TextPropertiesDto textProperties`, `NumericPropertiesDto`,
  `ListOfValuesPropertiesDto`, `DateTimePropertiesDto`, `CurrencyPropertiesDto`,
  `ReferencePropertiesDto`, each a record with the contract's fields as wrapper types/strings and
  `@NotNull` exactly where the contract marks `required`); `AttributeResponse` (common fields plus
  the six nullable property response objects, only the matching one set; `CurrencyPropertiesDto`
  response adds `currencySymbol`), with a static `from(Attribute)` that fills the property object
  for the types already implemented; `OptionResponse` (`code`, `label`), `AttributeTypeResponse`
  (`code`, `label`, `Map<String, List<OptionResponse>> options`), `AttributeTypeCollectionResponse`
  (`items`, `total`).
- [X] T026 [US1] Create `MAIN/attribute/service/AttributeService.java` (`@Service`; constructor
  injection of `AttributeRepository`, `ListOfValuesCatalog`, `IdentifierService`, `Clock`; one lock):
  `Attribute create(AttributeDraft draft, AttributePermissions permissions, ManualIdPermissions
  manualIdPermissions)` where `AttributeDraft` (new record in `MAIN/attribute/model/`) holds
  requested system ID, raw display name, description, data type and the already-built
  `AttributeProperties` (null for BOOLEAN). Order (data-model "Creation flow"; request-format errors from Bean Validation and from the
  controller's type parsing come before, as 400): permission → name and description → LOV checks
  (added in T047) → duplicate name → `identifierService.assign(
  ObjectType.ATTRIBUTE, requestedSystemId, declaredKeyFieldValues, manualIdPermissions)` (ground
  rules) → if outcome is `SAME_OBJECT` throw `DisplayNameInUseException` (D-5, never expected) →
  save. Plus `Attribute find(String systemId)`. T022 passes.
- [X] T027 [US1] Create `MAIN/attribute/controller/AttributeTypeController.java`
  (`GET /api/v1/attribute-types`): builds the collection from the enums of T004 (codes and labels;
  option keys `caseFormat`, `numberFormat`, `selectionMode`, `dateFormat`, `targetObjectType`,
  `cardinality`; DATE_TIME option codes are the API values `DD/MM/AAAA` …). T023 passes.
- [X] T028 [US1] Create `MAIN/attribute/controller/AttributeController.java`
  (`/api/v1/attributes`): `POST` (`@Valid` body; parse `dataType` with `fromCode`, unknown →
  `InvalidAttributeException("dataType", UNKNOWN_DATA_TYPE, value)`; build properties for the chosen
  type through a private `propertiesFor(request)` that returns null for BOOLEAN and, for types not
  yet implemented, throws `InvalidAttributeException("dataType", UNKNOWN_DATA_TYPE, …)` until their
  story replaces it; permissions from `RequestPermissionsReader`; 201 + `Location`), and
  `GET /{systemId}` (200).
- [X] T029 [US1] Extend `MAIN/attribute/controller/AttributeExceptionHandler.java`:
  `AttributeCreateNotPermittedException` → 403, title "Sin permiso para crear atributos", detail and
  error (field `permissions`) `ATTRIBUTE_CREATE_NOT_PERMITTED`; `AttributeNotFoundException` → 404
  "No encontrado", "No existe un atributo con el ID %s.", no errors; `DisplayNameInUseException` →
  409 "Nombre en uso", detail = error message (field `displayName`); feature 003's
  `ManualIdNotPermittedException` → 403 and `SystemIdInUseException` → 409 (field `systemId`),
  `SequenceExhaustedException` → 409 (field `objectType`), `InvalidIdentifierRequestException` →
  400 with its field and code — each with the exception's own Spanish message (FR-005) and the
  titles feature 003 uses ("ID manual no permitido", "ID en uso", "Secuencia agotada", "Solicitud no
  válida"). T024 passes.

### Frontend tests for User Story 1

- [X] T030 [P] [US1] Write `FE/api/attributesApi.test.ts`: `fetchAttributeTypes()` GETs
  `${VITE_API_BASE_URL}/api/v1/attribute-types` and returns the items; `createAttribute(request)`
  POSTs JSON with headers `X-Pim-Permissions` and `X-Pim-Manual-Id-Permissions` taken from
  `VITE_DEV_PERMISSIONS` / `VITE_DEV_MANUAL_ID_PERMISSIONS` (omitted when unset) and returns the
  attribute on 201; 400/403/409/422 throw `AttributeApiError` with `kind` `validation` /
  `forbidden` / `conflict` / `unprocessable`, the problem `detail` and its `errors`
  (`field`, `code`, `message`); network failure, missing base URL or other status → `kind`
  `unavailable`. Use `vi.stubEnv` and `vi.stubGlobal('fetch', …)`.
- [X] T031 [P] [US1] Write `FE/attributes/attributeForm.test.ts`: `emptyForm()`;
  `validateForm(form)` (UX only) requires a non-blank display name and a data type, Spanish
  messages per field; `toRequest(form)` trims display name, description and system ID, sends
  `systemId` only when not blank, sends `description` null when blank, sends only the properties
  object of the chosen type (none for BOOLEAN).
- [X] T032 [P] [US1] Write `FE/pages/AttributeCreationPage.test.tsx`: shows a loading state then a
  form with "Nombre", "Descripción", "ID (opcional)" and "Tipo de dato" whose options are the
  labels returned by the mocked types endpoint; types failure shows the Spanish connection error and
  a retry button; creating a "Booleano" attribute shows a success notification containing the new
  ID and empties the form; a 409 shows the backend message next to "Nombre" and keeps the entered
  data; a 403 shows the backend message in an error notification; a network error shows the
  connection message and keeps the data; saving with an empty name shows the UX message without
  calling the API.

### Frontend implementation for User Story 1

- [X] T033 [P] [US1] Implement `FE/api/attributesApi.ts` (types mirroring the contract's
  `AttributeType`, `AttributeCreationRequest`, `Attribute`; `AttributeApiError`; `buildApiUrl` from
  `apiConfig.ts`) so T030 passes.
- [X] T034 [P] [US1] Implement `FE/attributes/attributeForm.ts` (form state type with common fields
  and one sub-state per type; `emptyForm`, `validateForm`, `toRequest`) so T031 passes.
- [X] T035 [US1] Implement `FE/pages/AttributeCreationPage.tsx` and `.module.css` with
  `PageHeader`, `Card`, `TextField`, `TextAreaField`, `SelectField`, `Button`, `Notification`; type
  options from `fetchAttributeTypes`; field errors from `AttributeApiError.errors` matched by
  `field`; success notification "Atributo creado con el ID {id}." so T032 passes.
- [X] T036 [US1] Add `{ path: '/crear-atributo', label: 'Crear atributo', icon: 'components' }` to
  `NAVIGATION_ENTRIES` in `FE/navigation.ts` and the route in `FE/App.tsx`; add one case for
  `#/crear-atributo` to a new `describe` block in `FE/navigation.test.ts` without changing the
  existing cases.

**Checkpoint**: US1 works end to end (Boolean attributes). Both builds pass.

---

## Phase 4: User Story 2 - Text, numeric, date/time and currency properties (Priority: P1)

**Goal**: configure and validate the properties of four types (FR-008–FR-010, FR-013, FR-014,
FR-016; D-6–D-10).

**Independent Test**: quickstart API scenarios 8–12, 16; UI scenarios U2, U3.

### Backend tests for User Story 2

- [X] T037 [P] [US2] Write `TEST/attribute/model/TextPropertiesTest.java`: "`minLength` Optional;
  whole number 0–100,000"; "`maxLength` Optional; whole number 1–100,000; ≥ `minLength` when both
  given"; "`captureMask` Optional; regular expression, 1–500 characters, must compile";
  `richText` default false; codes `INVALID_LENGTH` (fields `textProperties.minLength` /
  `.maxLength`), `INVALID_LENGTH_RANGE` (field `textProperties.maxLength`), `INVALID_CAPTURE_MASK`
  (`[a-` and 501 characters); case format and capture mask allowed together and with rich text (D-6).
- [X] T038 [P] [US2] Write `TEST/attribute/model/NumericPropertiesTest.java`: "`numberFormat`
  Required"; "`precision` `DECIMAL`: required, 1–10. `INTEGER`: must be absent" (`INVALID_PRECISION`
  for missing, 0, 11, and any precision on INTEGER); "`minimum`, `maximum` Optional (either or both);
  decimal, ≤ 18 significant digits; whole for `INTEGER`; ≤ `precision` decimals for `DECIMAL`;
  `minimum ≤ maximum`" (`INVALID_NUMBER` for 19 digits, 1.5 on INTEGER, 1.234 with precision 2;
  `INVALID_VALUE_RANGE` for 5 > 1); one-sided ranges accepted.
- [X] T039 [P] [US2] Write `TEST/attribute/model/DateTimePropertiesTest.java` and
  `TEST/attribute/model/CurrencyPropertiesTest.java`: date format required (null → `REQUIRED`);
  currency "`currencyCode` Required; ISO 4217 code known to the runtime" (`XYZ`, `mxn` →
  `UNKNOWN_CURRENCY`), "`decimals` Optional; 0–4; default 2" (5, -1 → `INVALID_DECIMALS`),
  `currencySymbol()` for `MXN` equals `Currency.getInstance("MXN").getSymbol(new Locale("es",
  "MX"))`.
- [X] T040 [P] [US2] Extend `TEST/attribute/controller/AttributeControllerIntegrationTest.java`
  (US2): each valid example of quickstart 8, 10, 11, 12 → 201 echoing exactly the properties (and
  `currencySymbol`, default `decimals` 2); `minLength` -1, `maxLength` 0 and `maxLength` 100001 → 400 `INVALID_LENGTH` (fields
  `textProperties.minLength` / `.maxLength`); each invalid example of quickstart 9, 10, 11, 12 → 400 with
  the stated code and field; unknown enum texts → `UNKNOWN_CASE_FORMAT` (`TITLE`),
  `UNKNOWN_NUMBER_FORMAT`, `UNKNOWN_DATE_FORMAT` (`YYYY`); `"minimum": "abc"` → 400 `REQUIRED` field
  `body`; NUMERIC without `numericProperties` → 400 `REQUIRED` field `numericProperties`; quickstart
  16 (BOOLEAN with `textProperties`) → 201 without `textProperties`; `GET` returns the same
  properties.

### Backend implementation for User Story 2

- [X] T041 [P] [US2] Implement records `MAIN/attribute/model/TextProperties.java`,
  `NumericProperties.java` (BigDecimal minimum/maximum), `DateTimeProperties.java`,
  `CurrencyProperties.java` (`currencySymbol()` as in T039) implementing `AttributeProperties`, with
  validation in their constructors. T037–T039 pass.
- [X] T042 [US2] In `MAIN/attribute/controller/AttributeController.java`, build these four property
  types from their DTOs in `propertiesFor` (missing DTO for the chosen type →
  `InvalidAttributeException("<type>Properties", REQUIRED)`, except for Texto, whose properties
  are all optional (spec A-005), so a missing `textProperties` means "no properties"; implemented in
  `MAIN/attribute/controller/AttributePropertiesMapper.java`); unknown enum codes → the matching
  `UNKNOWN_*` code with field `<type>Properties.<field>`); in `MAIN/attribute/dto/AttributeResponse`
  map them back. T040 passes.

### Frontend for User Story 2

- [X] T043 [P] [US2] Extend `FE/attributes/attributeForm.test.ts` and `FE/pages/AttributeCreationPage.test.tsx`:
  choosing Texto shows "Longitud mínima", "Longitud máxima", "Formato de mayúsculas" (options from
  the API), "Máscara de captura (expresión regular)", "Texto enriquecido (HTML)"; Numérico shows
  "Formato" and, for Decimal only, "Decimales", plus "Valor mínimo"/"Valor máximo"; Fecha y hora
  shows "Formato de fecha"; Moneda shows "Moneda (código ISO, p. ej. MXN)" and "Decimales";
  `toRequest` converts numeric inputs to numbers and omits empty ones; a 400 error for
  `textProperties.maxLength` appears next to "Longitud máxima"; UX check: minimum > maximum shows a
  message without calling the API.
- [X] T044 [US2] Implement those sections in `FE/attributes/attributeForm.ts` and
  `FE/pages/AttributeCreationPage.tsx` (one sub-component per type in the same file or in
  `FE/pages/attributeTypeFields.tsx`). T043 passes.

**Checkpoint**: US1 and US2 work; builds pass.

---

## Phase 5: User Story 3 - List-of-values attribute (Priority: P2)

**Goal**: LOV attributes are offered; saving is refused (422) while no lists exist (FR-012, D-12).

**Independent Test**: quickstart API scenario 13; UI scenario U5.

- [X] T045 [P] [US3] Extend `TEST/attribute/service/AttributeServiceTest.java` (order of checks as in
  data-model "ListOfValuesProperties"): with the empty catalog, an LOV draft with or without a
  selection mode and list ID → `ListOfValuesUnavailableException` (`LIST_OF_VALUES_NOT_AVAILABLE`)
  and nothing registered in feature 003; with a test catalog holding `LOV-COLOR`: missing selection
  mode → `InvalidAttributeException` `REQUIRED` (field `listOfValuesProperties.selectionMode`),
  missing list ID → `REQUIRED` (field `listOfValuesProperties.listOfValuesId`), `LOV-OTRA` →
  `LIST_OF_VALUES_NOT_FOUND`, `LOV-COLOR` → created; a list may be used by two attributes.
- [X] T046 [P] [US3] Extend `TEST/attribute/controller/AttributeControllerIntegrationTest.java`
  (empty catalog): LIST_OF_VALUES `SINGLE` with `listOfValuesId` `LOV-X`, without `listOfValuesId`,
  and without `listOfValuesProperties` → 422 `LIST_OF_VALUES_NOT_AVAILABLE` (field
  `listOfValuesProperties.listOfValuesId`), title "Lista de valores no disponible"; nothing created;
  `selectionMode` `ALL` → 400 `UNKNOWN_SELECTION_MODE` (field `listOfValuesProperties.selectionMode`).
  Write `TEST/attribute/controller/AttributeListOfValuesIntegrationTest.java` with a
  `@MockitoBean ListOfValuesCatalog` (`hasAny()` true, `exists("LOV-COLOR")` true): `LOV-OTRA` →
  422 `LIST_OF_VALUES_NOT_FOUND`; missing `listOfValuesId` → 400 `REQUIRED`; `LOV-COLOR` `MULTIPLE`
  → 201 echoing both fields.
- [X] T047 [US3] Implement `MAIN/attribute/model/ListOfValuesProperties.java` (record; both fields
  nullable at construction so the catalog check runs first); the DTO has **no** `@NotNull` (contract:
  no required fields). In `AttributeService.create`, for LIST_OF_VALUES, in this order:
  `!catalog.hasAny()` → `LIST_OF_VALUES_NOT_AVAILABLE`; missing selection mode or list ID →
  `InvalidAttributeException(REQUIRED)`; unknown ID → `LIST_OF_VALUES_NOT_FOUND`. In the controller,
  build LOV properties (missing DTO → properties with both fields null; unknown mode text →
  `UNKNOWN_SELECTION_MODE`); map `ListOfValuesUnavailableException` to 422 in the handler. T045,
  T046 pass.
- [X] T048 [US3] Frontend: in `FE/pages/AttributeCreationPage.tsx` and its test, choosing "Lista de
  valores" shows "Tipo de selección" (options from the API) and an info `Notification` "Aún no
  existen listas de valores. Podrás crear este atributo cuando exista una lista."; saving shows the
  backend 422 message; `toRequest` sends `listOfValuesProperties` with the selection mode.

**Checkpoint**: US1–US3 work; builds pass.

---

## Phase 6: User Story 4 - Reference attribute (Priority: P2)

**Goal**: reference attributes with a target object type and cardinality (FR-015, D-11).

**Independent Test**: quickstart API scenario 14.

- [X] T049 [P] [US4] Write `TEST/attribute/model/ReferencePropertiesTest.java`: "`targetObjectType`
  Required" (`REQUIRED`); "`cardinality` Optional; `SINGLE` (default) or `MULTIPLE`"; and a test that
  `ReferenceTargetType` has exactly the codes of `com.pim.backend.identifier.model.ObjectType`, in
  the same order (keeps them in sync).
- [X] T050 [P] [US4] Extend `TEST/attribute/controller/AttributeControllerIntegrationTest.java`:
  REFERENCE `PRODUCT` `MULTIPLE` → 201 echoing both; without `targetObjectType` → 400 `REQUIRED`;
  `WIDGET` → 400 `UNKNOWN_TARGET_OBJECT_TYPE`; cardinality `MANY` → `UNKNOWN_CARDINALITY`;
  cardinality omitted → `SINGLE` in the response.
- [X] T051 [US4] Implement `MAIN/attribute/model/ReferenceProperties.java`, its controller mapping
  and response mapping; then make `AttributeProperties` a sealed interface that `permits` exactly
  the six records (`TextProperties`, `NumericProperties`, `ListOfValuesProperties`,
  `DateTimeProperties`, `CurrencyProperties`, `ReferenceProperties`). T049, T050 pass.
- [X] T052 [US4] Frontend: choosing "Referencia" shows "Objeto referenciado" and "Cantidad"
  (options from the API) in `FE/pages/AttributeCreationPage.tsx`; tests in its test file and in
  `FE/attributes/attributeForm.test.ts` (`toRequest` sends `referenceProperties`).

**Checkpoint**: all seven types can be configured; builds pass.

---

## Phase 7: User Story 5 - Every new attribute is global and ungrouped (Priority: P3)

**Goal**: confirm FR-017 and FR-018 end to end.

**Independent Test**: quickstart API scenario 15; UI scenario U1 (no scope or group field).

- [X] T053 [P] [US5] Extend `TEST/attribute/controller/AttributeControllerIntegrationTest.java`: a
  body with `"scope": "LOCAL"` and `"groups": ["X"]` → 201 with `scope` `GLOBAL` and no `groups`
  field (unknown fields ignored, contract); and `TEST/attribute/service/AttributeServiceTest.java`:
  every created attribute has `AttributeScope.GLOBAL`.
- [X] T054 [P] [US5] Extend `FE/pages/AttributeCreationPage.test.tsx`: the form has no scope or group
  field, and the success notification says the attribute is global ("Atributo global creado con el ID
  {id}."); update the message in `FE/pages/AttributeCreationPage.tsx` accordingly.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T055 [P] Write `TEST/attribute/service/AttributeServiceConcurrencyTest.java` (R-18): 20 threads
  released at once create attributes with the same display name → exactly one created, 19
  `DisplayNameInUseException`, one feature 003 registration; 100 threads with distinct names → 100
  attributes with 100 distinct IDs.
- [X] T056 Compare every operation, status, header, field and error code in
  `specs/004-attribute-creation/contracts/attributes.openapi.yaml` with the controllers, DTOs and
  handler; list differences and fix the code (report instead if the contract is wrong).
- [X] T057 [P] Check `specs/004-attribute-creation/quickstart.md` expected values against the code;
  correct the quickstart only where the code follows the contract, and report it.
- [X] T058 Backend: `.\gradlew.bat spotlessApply` and `.\gradlew.bat clean build` (T001 count plus
  new tests, all passing); frontend: `npm run lint`, `npm run format:check`, `npm test`,
  `npm run build`. Report per-class / per-file test counts.
- [X] T059 Run quickstart API scenarios 1–17 against a running backend (use a temporary MongoDB on
  another port so the owner's local data is untouched) and record the results.
- [X] T060 Owner check: run UI scenarios U1–U7 in the browser (owner confirms), and show the owner
  the Spanish messages of 403, 409 and 422 for SC-006.
- [X] T061 Scope check (quickstart "Scope checks"): `git diff --stat main` shows no change under
  `MAIN/identifier/`, `MAIN/product/`, `TEST/identifier/`, `TEST/product/`, `specs/002-*`,
  `specs/003-*`, the constitution or CLAUDE.md; no new dependency; backend changes outside
  `MAIN/attribute/` limited to `MAIN/config/CorsConfiguration.java`.

---

## Dependencies & Execution Order

- **Setup (1)** → **Foundational (2)** → **US1 (3)** → US2 (4), US3 (5), US4 (6), US5 (7) → **Polish (8)**.
- US1 creates `AttributeService`, `AttributeController`, `AttributeResponse`, the API client and the
  page; US2–US5 extend those files, so they follow US1. US2, US3 and US4 are otherwise independent
  of each other, but they edit the same files (controller, response, page, tests): run them one after
  another in priority order.
- Within a phase: tests before implementation; tasks editing the same file never run in parallel.
- `AttributeProperties` stays a plain interface until T051 seals it (T012).

### Parallel opportunities

- Foundational: T002, T004, T005, T006, T007, T009, T011, T014, T016, T018, T021 together.
- US1: T022, T023, T024, T025, T030, T031, T032 together; then T033 and T034 together.
- US2: T037, T038, T039, T040 together; T041 with T043.
- US3: T045, T046 together. US4: T049, T050 together. US5: T053, T054 together.
- Polish: T055, T057 together.

### Parallel example: User Story 1

```text
T022 AttributeServiceTest ─┐
T023 types IT             ├─ backend tests together, plus T025 DTOs
T024 controller IT        ┘
T030 api test ─┐
T031 form test ├─ frontend tests together
T032 page test ┘
then T026 → T027 → T028 → T029 (backend) and T033, T034 → T035 → T036 (frontend)
```

## Requirement coverage

| Requirement | Tasks |
|---|---|
| FR-001 (create page, REST only) | T019–T021, T030–T036 |
| FR-002 (create permission) | T009, T010, T016, T022, T024, T029, T030 |
| FR-003 (display name, type; description optional) | T007, T008, T011, T022, T024, T031, T032 |
| FR-004 (ID from feature 003) | T022, T024, T026 |
| FR-005 (feature 003 rejections and messages) | T022, T024, T029 |
| FR-006 (declared key fields only) | T022, T026 |
| FR-007 (seven types) | T004, T023, T027, T028, T032 |
| FR-008, FR-009 (text) | T037, T040–T044 |
| FR-010 (numeric) | T038, T040–T044 |
| FR-011 (boolean) | T022, T024, T032 |
| FR-012 (LOV, existing list required) | T015, T045–T048 |
| FR-013 (date/time) | T039–T044 |
| FR-014 (currency) | T039–T044 |
| FR-015 (reference) | T049–T052 |
| FR-016 (contradictory settings refused) | T037–T040, T043 |
| FR-017 (no groups) | T053, T054 |
| FR-018 (always global) | T011, T053, T054 |
| FR-019 (global-structure permission = `ATTRIBUTE_CREATE`, D-2) | T009, T022, T024 |
| FR-020 (no inheritance) | T053 (scope only global; nothing to inherit) |
| FR-021 (confirmation, read back) | T024, T032, T040, T050 |
| FR-022 (no frontend attribute data) | T023, T027, T032, T043 |
| FR-023 (unique display name) | T007, T014, T022, T024, T032, T055 |
| SC-001, SC-006 (usability) | T060 |
| SC-002 | T022, T055 |
| SC-003 | T022, T024 |
| SC-004 | T037–T040 |
| SC-005 | T024, T040, T050 |
| SC-007 | T022, T024, T055 |
| Decisions D-1–D-12 | D-1 T013–T015; D-2 T009, T016; D-3 T021, T030; D-4/D-5 T022, T026; D-6/D-7 T007, T037; D-8 T038; D-9 T039; D-10 T039; D-11 T049; D-12 T015, T045–T047 |

## Implementation Strategy

1. **MVP = Phases 1–3 (US1)**: Boolean attributes end to end, with permissions, unique names, system
   IDs from feature 003, the types endpoint and the page. Validate with quickstart 1–7, 17 and U1, U4,
   U6, U7.
2. **+ US2**: text, numeric, date/time and currency properties (all P1 complete).
3. **+ US3, US4**: list-of-values (refused until lists exist) and reference attributes.
4. **+ US5**: global and ungrouped confirmed end to end.
5. **Polish**: concurrency, contract and quickstart checks, full builds, API run, owner UI check,
   scope check.

Before merge (plan, Constitution Check XI): CF-001 to CF-003 resolved or accepted by the owner
(Quality Gate 8).
