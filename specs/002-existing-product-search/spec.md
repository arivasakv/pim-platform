# Feature Specification: Search for an Existing Product

**Feature Branch**: `002-existing-product-search`

**Created**: 2026-11-08

**Status**: Approved

**Approved**: 2026-11-08, by the project owner

**Amended**: 2026-11-08, scope reduced to EAN search only; amendment approved by the project
owner (see Clarifications); 2026-11-09, sample catalog stored in MongoDB instead of backend
memory (constitution 2.2.0, Principle IX), with no change to search behavior; approved by the
project owner (see Clarifications)

**Input**: User description: "Add a search for existing products at the start of a manual
Mercaderías proposal: a text field where the user can search by EAN, title, or image, and a
button to run the search for an existing product." (user request of 2026-11-08, referred to below
as *the feature request*). Image search was later deferred to a future feature, and then the
scope was reduced to EAN search only (see Clarifications).

## Clarifications

### Session 2026-11-08

- Q: How should image search work in this feature? → A: Deferred to a later feature (Option A).
  No image upload, no image links, no image similarity search and no AI capability. Image search
  stays a possible future enhancement, with no delivery date and no acceptance criteria in this
  feature (FR-009, Future Enhancements).
- Q: Should the search also cover brand and model (OQ-001)? → A: No. Brand-and-model search is out
  of scope (Out of Scope, Differences from the Sources).
- Q: Who uses the search page (OQ-002)? → A: The Mercaderías supplier (proveedor), checking for
  an existing product before proposing a new one (A-001). User-facing text addresses the supplier.
- Q: Does the brand blacklist apply to Mercaderías (OQ-003)? → A: Decision deferred to the
  proposal-creation feature. Feature 002 does not check the blacklist (Out of Scope).
- Q: Which businesses does the search cover (OQ-004)? → A: Liverpool and Suburbia only;
  Marketplace products never appear in results (FR-014).
- Q: Should Mercaderías matching be its own logic or defined per template (OQ-005, DUD-32)? → A:
  Kept open as a business decision for Gobierno de Producto. Feature 002 uses one rule for every
  product.
- **Amendment (after approval)**: Q: Which searches does feature 002 deliver? → A: **EAN only.**
  Title search is removed from this feature and listed under Future Enhancements, with no delivery
  date. Removed: User Story 2 (title search), FR-005, SC-003 and A-005; FR-003, FR-010, SC-005,
  the edge cases and Key Entities were updated. Requested and approved by the project owner on
  2026-11-08 (constitution Principle X: explicit, explained and re-approved spec change).

### Session 2026-11-09

- **Amendment (after approval)**: Q: Where does the backend keep the product catalog? → A: **In
  MongoDB** (a local instance in development), instead of backend memory. Constitution 2.2.0
  (Principle IX) allows MongoDB for the product catalog. Search rules, validation, messages and
  the successful and validation responses are unchanged. One error response is added: when the
  catalog store cannot be reached, the backend answers 503 and the page shows the FR-011 message.
  TD-001 and Out of Scope were updated. This amendment is the specification Principle IX requires
  for the capability (recorded in plan.md, Complexity Tracking). Requested and approved by the
  project owner on 2026-11-09.

## Context

Before a new product is proposed for Mercaderías (Liverpool and Suburbia's own assortment), the
supplier must check whether the product already exists. In Mercaderías an existing product is never
created again [Source: p360:diagramas-to-be/Guía visual de los flujos TO-BE.html §"Mercaderías,
una por una", step "¿Ya existe?"]. This feature delivers only that first check, by EAN: a single
search field, a search button and the list of matches. Creating the proposal itself is out of
scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find an existing product by EAN (Priority: P1)

A Mercaderías supplier about to propose a new product types its EAN in the search field and
presses the search button. If a product with that EAN already exists, it is shown, so the supplier
knows not to propose it again.

**Why this priority**: EAN is the key that must stay unique within a business, and duplicate EANs
are the main problem the check prevents [Source: p360:ERF - HUs/HU_Productos_New PIM
HEADLESS.docx §"Gestión y Reglas de Unicidad por Negocio del Código EAN"]. It is the only search in
this feature.

**Independent Test**: With the sample catalog loaded, search for an EAN that exists and for one
that does not; the first shows exactly that product, the second shows "no matches".

**Acceptance Scenarios**:

1. **Given** a product with EAN 7501234567890 exists, **When** the user types `7501234567890` and
   presses "Buscar", **Then** that product is listed as a result.
2. **Given** no product has EAN 7509999999999, **When** the user searches for it, **Then** the
   page says no existing product was found and that the user can continue with a new proposal.
3. **Given** the same EAN exists in Liverpool and in Suburbia, **When** the user searches for it,
   **Then** both products are listed, each showing its business.
4. **Given** a Marketplace product has EAN 7501112223334 and no Liverpool or Suburbia product has
   it, **When** the user searches for it, **Then** no product is listed (Marketplace products are
   never shown to a Mercaderías supplier).
5. **Given** the user types text with letters, such as `licuadora`, **When** they press "Buscar",
   **Then** no search runs and the page explains that only an EAN of 8, 12, 13 or 14 digits can be
   searched.

---

### Edge Cases

- Empty field or only spaces: no search runs, and the user is told to type an EAN.
- Letters or symbols other than spaces and dashes (for example `licuadora` or `750#123`): no search
  runs, and the user is told the EAN may contain only digits.
- Only digits but not a valid EAN length (8, 12, 13 or 14 digits): no search runs, and the user is
  told the EAN length is not valid.
- EAN typed with spaces or dashes (`750 1234 567890`): spaces and dashes are removed before
  searching.
- More than 20 characters typed: the field does not accept more (a 14-digit EAN with separators
  fits).
- The catalog cannot be reached: a Spanish error message is shown and the user can try again; no
  results from a previous search remain on screen as if they were current.
- The user presses Enter in the field: same as pressing "Buscar".
- A new search while the previous one is still running: only the results of the latest search are
  shown.

## Requirements *(mandatory)*

### Explicit Requirements

Sources: the feature request (user) and tier 2 documents in the reference repository. No tier 1
document in `docs/source-of-truth/` covers this feature.

- **FR-001**: The application MUST provide a page for checking whether a product already exists,
  reachable from the side navigation. [Source: feature request]
- **FR-002**: The page MUST show one text field for the EAN and one "Buscar" (search) button. The
  search runs when the button is pressed or Enter is typed in the field. [Source: feature request;
  EAN only: amendment 2026-11-08]
- **FR-003**: Before searching, the system MUST remove spaces and dashes from the input and accept
  it only if what remains is 8, 12, 13 or 14 digits. [Source: amendment 2026-11-08; EAN lengths:
  assumption A-004]
- **FR-004**: The search MUST return the Liverpool and Suburbia products whose EAN is exactly the
  typed EAN. [Source: feature request; p360:diagramas-to-be/Guía visual de los flujos TO-BE.html
  §"¿Ya existe?" ("Lo busca por EAN"); EAN may repeat across businesses: p360:ERF - HUs/HU_Productos
  §"Regla de Unicidad Múltiple por Negocio"; business scope: FR-014]
- **FR-005**: *Removed by the 2026-11-08 amendment* (title search; see Future Enhancements).
- **FR-006**: Each result MUST show at least: SKU, EAN, title, brand and business. [Source:
  feature request ("find an existing product"); fields: assumption A-006]
- **FR-007**: When no product matches, the page MUST say that no existing product was found and
  that the user can continue with a new proposal. [Source: p360:Guía visual TO-BE §"¿Ya existe?"
  ("Es un producto nuevo: puedes continuar")]
- **FR-008**: When at least one product matches, the page MUST warn that a product with that EAN
  already exists and that, in Mercaderías, an existing product is not created again.
  [Source: p360:Guía visual TO-BE §"¿Y si ya existiera?"]
- **FR-009**: The page MUST NOT offer image search: no image upload, no image link (URL) and no
  visual-similarity search, and the feature MUST NOT use any AI capability. The page MUST NOT
  announce image search, or any other search type, as an upcoming option. [Source: user
  clarification 2026-11-08 (Option A); constitution Principle IX]
- **FR-010**: Invalid input (empty, containing letters or symbols other than spaces and dashes, or
  digits with a non-EAN length) MUST be rejected with a Spanish message explaining what to type,
  without running the search. [Source: feature request; amendment 2026-11-08; edge cases]
- **FR-011**: If the catalog cannot be reached, the page MUST show a Spanish error message and
  allow retrying. [Source: assumption A-007]
- **FR-012**: The search MUST NOT create, change or reserve any product or proposal. [Source:
  feature request (search only); scope]
- **FR-013**: All visible text MUST be in Spanish (es-MX) and MUST use the shared visual identity
  and components of feature 001 (TextField with search variant, Button, Card, Notification,
  StatusBadge). [Source: specs/001-liverpool-look-and-feel/spec.md FR-010, FR-018]
- **FR-014**: The search MUST cover only Mercaderías businesses (Liverpool and Suburbia).
  Marketplace products MUST never appear in the results. [Source: user decision 2026-11-08
  (OQ-004); AS-IS: a Liverpool proposal is compared only against Mercaderías
  (p360:…/21-matching-blacklist-y-multioferta.md §"Matching segmentado por negocio"); the
  Mercaderías supplier does not see Marketplace variants (p360:Guía visual TO-BE)]

### Observed Existing Behavior

From tier 3 evidence; not requirements, for context only.

- **OEB-001**: The current Entrada Única calls a P360 service that finds articles by EAN or by
  brand and model (POST during capture; an undocumented GET variant from the approval screen).
  [Evidence: p360:P360-repositorios/nuevo-pim/docs/arquitectura/pim-headless/contratos-api.md:66-67,295]
  The source code is not in the reference repository; only its analysis.
- **OEB-002**: Today, when a supplier uses an EAN that another SKU already has, they get an alert
  or error after submitting, and the EAN is released by ticket with the buyer's approval.
  [Evidence: p360:P360-repositorios/nuevo-pim/rag-nuevo-pim/10-flujo-mercaderias.md §"Mercaderías
  AS-IS — propuesta del proveedor", steps 4–5]
- **OEB-003**: Image matching was built once as an AI model (brand, model, product type, color,
  category) and is frozen; it was never released. [Evidence:
  p360:P360-repositorios/nuevo-pim/rag-nuevo-pim/21-matching-blacklist-y-multioferta.md §"Matching
  por imagen, un proyecto congelado"]

### Differences from the Sources

- **EAN only**: the sources search by EAN, by brand and model, by product web page or by photo.
  This feature searches by EAN only (amendment 2026-11-08); the other paths are out of scope.
- **No image search**: the sources list image as a search path (for Mercaderías only "a futuro").
  It is deferred to a later feature by user decision (FR-009).
- **Search before proposal, not a blocking step**: the sources place this check inside the
  proposal flow and block creating a duplicate. No proposal flow exists yet in pim-platform, so
  here the result is informational (FR-007, FR-008, FR-012).
- **No blacklist check**: the TO-BE guide checks the brand blacklist first, but whether it applies
  to Mercaderías is contradictory in the sources (see OQ-003). It is not part of this feature.

### Technical Decisions

- **TD-001**: The products searched are sample products stored in MongoDB and read by the backend
  (amended 2026-11-09; constitution Principle IX 2.2.0). They are loaded once, when the catalog is
  empty; the search never writes to it (FR-012). If the store cannot be reached, the search fails
  with an error (FR-011) and never falls back to other data. The sample set covers every scenario
  in this spec, including the same EAN in Liverpool and Suburbia, and Marketplace products that must be
  excluded from results (FR-014).
- **TD-002**: The search is answered by the backend, so the same rules can later serve the
  proposal flow and bulk upload (the sources propose one shared matching service:
  p360:poc-matching-to-be.html §"Las piezas que intervienen").

### Key Entities

- **Product (search view)**: an existing product as shown in results: SKU, EAN, title, brand,
  business (Liverpool, Suburbia, Marketplace; only Liverpool and Suburbia are ever shown, FR-014).
- **Search request**: the EAN typed by the user, after removing spaces and dashes.
- **Search result**: the searched EAN and the matching products (none, one, or one per business).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can check an EAN and see the answer in under 10 seconds from opening the page.
- **SC-002**: 100% of EAN searches for an EAN in the sample catalog list every Liverpool and
  Suburbia product with that EAN, and no other product (no Marketplace product, ever).
- **SC-003**: *Removed by the 2026-11-08 amendment* (title search).
- **SC-004**: Results appear in under 2 seconds for a sample catalog of at least 500 products.
- **SC-005**: 100% of invalid inputs (empty, containing letters or symbols, wrong EAN length) show
  a message explaining what to type, and no search is run.
- **SC-006**: In a walkthrough, at least 4 of 5 users correctly tell, from the page alone, whether
  the product they searched for already exists.

## Assumptions

- **A-001**: The user is a Mercaderías **supplier (proveedor)** checking for an existing product
  before proposing a new one (OQ-002, resolved). There is no login yet (constitution IX), so in
  this phase anyone can open the page; restricting results to what a supplier may see (for
  example, which suppliers may propose in which catalog) comes with login and roles in a later
  feature [p360:Guía visual TO-BE: "el proveedor de Mercaderías no ve las variantes de
  Marketplace"].
- **A-002**: The page is a new side-navigation entry, "Buscar producto". This is the first PIM
  business section, introduced by this spec (constitution IV).
- **A-003**: *Superseded by FR-014 (OQ-004 resolved)*: the search covers Liverpool and Suburbia
  only, and each result shows its business.
- **A-004**: An EAN is 8, 12, 13 or 14 digits (EAN-8, UPC-A, EAN-13, GTIN-14). The check digit is
  not validated, because the current EAN rules document ("Reglas EANs actuales") is not in the
  reference repository.
- **A-005**: *Removed by the 2026-11-08 amendment* (title result ordering).
- **A-006**: Results show text data only; product images in results are not part of this feature.
- **A-007**: Error handling follows feature 001: Spanish notification, retry by searching again.
- **A-008**: Search terms are not stored.

## Out of Scope

- Creating, saving or sending a proposal; releasing an EAN; blocking a duplicate.
- Title search; brand-and-model search; URL (product web page) search.
- Image search of any kind (upload, image link, visual similarity) and any AI capability.
- Brand blacklist.
- Bulk upload and per-row duplicate search.
- Real catalog data or P360/SAP integration (constitution IX). The MongoDB store holds sample
  products only.
- Any database other than MongoDB, and writing to the catalog from the search (constitution IX,
  FR-012).

## Future Enhancements

Not part of this feature, not committed and with no delivery date. Listed so they are not lost.

- **Title search**: find an existing product by words from its title, to catch products created
  under a different EAN (removed from this feature by the 2026-11-08 amendment).
- **Image search**: find an existing product from a photo (upload or link), as the sources propose
  for Mercaderías "a futuro" [p360:…/10-flujo-mercaderias.md §TO-BE validations (S02-RF-35)]. It
  needs its own specification and, because it relies on AI, a constitution amendment to Principle
  IX first.

## Open Questions

- **OQ-001**: *Resolved 2026-11-08*: no brand-and-model search in this feature (see
  Clarifications). After the amendment, the feature searches by EAN only.
- **OQ-002**: *Resolved 2026-11-08*: the supplier (proveedor), as in the sources (see
  Clarifications and A-001).
- **OQ-003**: Does the brand blacklist apply to Mercaderías? The TO-BE guide says it applies to all
  businesses, but the blacklist is defined as brands Marketplace cannot sell because Mercaderías
  sells them. [p360:Guía visual TO-BE; p360:…/21-matching-blacklist-y-multioferta.md §blacklist]
  *Deferred 2026-11-08*: not part of feature 002; to be decided for the proposal-creation feature
  with Gobierno de Producto and Marketplace. Does not block this feature.
- **OQ-004**: *Resolved 2026-11-08*: Liverpool and Suburbia only; Marketplace products are never
  shown (FR-014).
- **OQ-005**: Matching rules for Mercaderías are undecided (DUD-32: own logic or per template).
  [p360:poc-matching-to-be.html §"Lo que todavía no está cerrado"] *Kept open 2026-11-08*: an
  unresolved business decision to raise with Gobierno de Producto. Feature 002 applies one rule to
  every product (exact EAN match); this does not block the feature, and any per-template rule
  would come in a later feature.

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-08) and the EAN-only amendment (2026-11-08) | Request | Basis of FR-001–FR-003, FR-010, FR-012 |
| `.specify/memory/constitution.md` v2.0.0 | Governance | Principles IV, IX, X, XI applied |
| `docs/source-of-truth/` (all subfolders) | 1 | Empty; no requirement for this feature |
| `p360:ERF - HUs/ERF 1.3 Productos.docx` §3.3.1.3 EAN, §3.6 Reglas (Matching) | 2 | EAN unique per business; matching detects duplicates before creation (generic) |
| `p360:ERF - HUs/HU_Productos_New PIM HEADLESS.docx` §EAN uniqueness, §Reglas de Matching | 2 | EAN rules; matching rule category |
| `p360:ERF - HUs/Capacidades Nuevo PIM_.xlsx` | 2 | Nothing on matching |
| `p360:diagramas-to-be/Guía visual de los flujos TO-BE.html` §"Mercaderías, una por una" | 2 (analysis, sample data) | The "¿Ya existe?" step, outcomes |
| `p360:diagramas-to-be/Mercaderías carga manual.pdf` | 2 (diagram) | Too dense to read at available resolution; summarized by the guide |
| `p360:poc-matching-to-be.html` | 2 (proposal, not ratified) | Shared matching service; open items (DUD-32) |
| `p360:P360-repositorios/nuevo-pim/rag-nuevo-pim/10-flujo-mercaderias.md` | 2 (workshop analysis) | AS-IS proposal flow; TO-BE validations; image search "a futuro" (S02-RF-35) |
| `p360:P360-repositorios/nuevo-pim/rag-nuevo-pim/21-matching-blacklist-y-multioferta.md` | 2 (workshop analysis) | Identification paths; image matching; Mercaderías vs Marketplace |
| `p360:P360-repositorios/nuevo-pim/docs/arquitectura/pim-headless/contratos-api.md` | 3 | OEB-001 |
| `specs/001-liverpool-look-and-feel/` | Project | Shared UI components and language |
