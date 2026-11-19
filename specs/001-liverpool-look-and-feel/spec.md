# Feature Specification: Liverpool-Inspired Frontend Look & Feel

**Feature Branch**: `001-liverpool-look-and-feel`

**Created**: 2026-11-08

**Status**: Approved

**Approved**: 2026-11-08, by the project owner

**Input**: User description: "Feature: Liverpool-Inspired Frontend Look & Feel — establish a consistent visual identity for the PIM frontend inspired by the Mexican Liverpool department store's digital experience. The application must remain a PIM application; Liverpool is a visual reference, not the functional scope." (full description supplied by the user in the `/speckit-specify` request of 2026-11-08; referred to below as *the feature request*)

## Clarifications

### Session 2026-11-08

- Q: Language of on-screen labels? → A: Spanish (es-MX); existing English texts are translated
  (FR-018).
- Q: The backend returns an English text and cannot change; how is the connection result shown?
  → A: Keep the existing endpoint unchanged. The frontend performs the connection check and shows
  a Spanish success or error message. The English backend response is not shown to the user
  (User Story 1 scenarios 3–4, FR-015, FR-018). Recorded during planning (plan R-13).
- Q: How large should the initial scope be? → A: Simplify it (user decision during task review).
  Prioritize the application shell, responsive layout, home page with backend connection status,
  and a small set of reusable components. Priorities changed to US1 P1, US4 P2, US3 P3, US2 P4.
  FR-010 is reduced to buttons, text/search inputs, cards with section headings, page titles,
  status badges and notifications. Dropdowns, selection controls (select, checkbox, radio) and
  tables, which the feature request §3 lists, are **deferred to a follow-up feature** (see Deferred
  Scope).

### Session 2026-11-19

Amendment requested by the project owner after features 002–010 added business entries to the side
navigation.

- Q: How should the side navigation be organized now that it lists six entries? → A: In two named
  sections, in this order: **General** (Inicio, Buscar Producto) and **Administración de catálogo**
  (Lista de Valores, Atributos, Grupo de Atributos, Taxonomía). Section names are subtle uppercase
  labels, visually secondary to the entries, and the sections are separated by spacing and a subtle
  divider (FR-019).
- Q: Which icons should the entries use? → A: Each entry gets its own icon that represents its
  function: Inicio = house, Buscar Producto = search, Lista de Valores = list, Atributos = tags,
  Grupo de Atributos = folder tree, Taxonomía = network. The icons are added to the existing icon
  set; no icon library is introduced (FR-004, FR-020, TD-003).
- Q: What must stay unchanged? → A: Routes, page behavior, header, palette, sidebar width, active,
  hover and focus styles, the tablet drawer and the Spanish texts. Frontend only; the backend is not
  changed (FR-016).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consistent application shell (Priority: P1)

A PIM user opens the application and sees a professional workspace: a header that identifies the
application, a side navigation menu that lists the available sections, and a main content area
where the selected page is shown. Moving between sections keeps the header and navigation in
place and only changes the content area.

**Why this priority**: Every later PIM screen sits inside this shell. Without it there is no
place to apply the visual identity, and no consistent navigation.

**Independent Test**: Open the application, move between every entry in the side navigation and
confirm the header, navigation and content area keep the same position, size and styling on each
page.

**Acceptance Scenarios**:

1. **Given** the application is running, **When** a user opens it, **Then** a header, a side
   navigation menu and a main content area are visible.
2. **Given** the user is on any page, **When** they select another navigation entry, **Then**
   only the main content area changes and the selected entry is visibly marked as active.
3. **Given** the backend is running, **When** the user opens the home page, **Then** the existing
   backend connection check succeeds and a Spanish success message is shown in the main content
   area. The backend's own (English) response text is not shown.
4. **Given** the backend is not running, **When** the user opens the home page, **Then** the shell
   still renders and a Spanish "could not connect" message is shown, styled as a status
   notification.
5. **Given** the application is open at any supported width, **When** the user looks at the side
   navigation, **Then** the entries are listed under the section names "GENERAL" and
   "ADMINISTRACIÓN DE CATÁLOGO" (FR-019), each entry shows its own icon (FR-020), and selecting any
   entry still opens its page and marks it as active. *(Added 2026-11-19.)*

---

### User Story 2 - Recognisable, consistent visual identity (Priority: P4)

A PIM user perceives the interface as clean, modern and visually related to the Liverpool
department store brand, while clearly being an internal business tool rather than a shopping
website. Colors, typography, spacing, borders, shadows, corner radiuses and icons look the same on
every page.

**Why this priority**: The identity itself (tokens) is built in the foundation for every story.
This story adds its documentation and review, which is useful only once the shell, layout and
components exist.

**Independent Test**: Review every page against the documented visual identity (palette,
typography scale, spacing scale, radius, shadow and icon rules) and confirm no page uses values
outside it.

**Acceptance Scenarios**:

1. **Given** the documented visual identity, **When** a reviewer inspects any page, **Then** every
   color, font size, font weight, spacing, border, shadow and corner radius used comes from that
   identity.
2. **Given** the visual identity documentation, **When** a reader checks the palette and
   typography, **Then** each value is labeled either as an official Liverpool specification (with
   its source) or as a project design choice.
3. **Given** any page, **When** a reviewer compares it with a consumer shopping site, **Then** the
   page contains no shopping elements (cart, prices, promotional banners, product carousels,
   checkout calls to action).

---

### User Story 3 - Reusable UI components (Priority: P3)

A frontend developer building future PIM screens can reuse a small shared set of styled
components — primary and secondary buttons, text and search inputs, cards with section headings,
page titles, status badges and notifications — instead of styling each screen from scratch.
Dropdowns, selection controls and tables follow in a later feature.

**Why this priority**: Reusable components are what keep the identity consistent as product
features are added later.

**Independent Test** (the reference page was removed on 2026-11-11, see FR-012; the components are
now reviewed on the pages that use them): Open the component reference page and confirm every listed component is
shown in its normal, hover/focus, disabled and (where relevant) error states, all following the
visual identity.

**Acceptance Scenarios**:

1. **Given** the component reference page, **When** a user opens it, **Then** every component
   listed in FR-010 is displayed with sample content.
2. **Given** an interactive component, **When** it receives keyboard focus, **Then** a visible
   focus indicator is shown.
3. **Given** a disabled control, **When** the user tries to interact with it, **Then** it does not
   respond and looks visibly disabled.
4. **Given** a status indicator or notification, **When** it is shown in each variant (success,
   warning, error, information), **Then** each variant is distinguishable without relying on color
   alone (icon or text label).

---

### User Story 4 - Desktop and tablet use (Priority: P2)

A PIM user works on a desktop monitor or a tablet. On both, navigation remains reachable, content
fits the screen width and nothing is cut off.

**Why this priority**: The shell must be usable on tablets from the start (user priority). It
builds directly on Story 1.

**Independent Test**: View every page at 1440 px, 1024 px and 768 px wide (portrait and landscape
tablet) and confirm navigation is reachable and no horizontal page scrolling occurs.

**Acceptance Scenarios**:

1. **Given** a desktop-width screen (1024 px or wider), **When** the user opens the application,
   **Then** the side navigation is permanently visible next to the content area.
2. **Given** a tablet-width screen (768 px to 1023 px), **When** the user opens the application,
   **Then** the navigation is reachable through a clearly visible control in the header and does
   not cover content until opened.
3. **Given** any supported width, **When** a page contains long content (long titles, long
   unbroken text), **Then** the content wraps within the content area and the page itself does
   not scroll horizontally.

### Edge Cases

- Very long page titles, navigation labels or notification text: text wraps; the layout does not
  break.
- Screen narrower than 768 px (phone): not a supported target; the shell must still not overflow
  horizontally, but no phone-specific layout is required.
- Browser zoom at 200%: content remains readable and reachable (it may reflow to the tablet
  layout).
- Official brand fonts unavailable: the chosen fallback fonts are used without layout breakage.
- Tablet navigation open and the user selects an entry: the navigation closes and the selected page
  is shown.

## Requirements *(mandatory)*

### Explicit Requirements

No tier 1 source (`docs/source-of-truth/`) or tier 2 source (reference repository documented
requirements) covers frontend visual identity. All requirements below come from the feature request
of 2026-11-08 and are cited as `[Source: feature request §<section>]`.

#### Visual identity

- **FR-001**: The application MUST use a single documented color palette inspired by the Mexican
  Liverpool department store, including a primary brand color, neutrals, text colors, and semantic
  colors for success, warning, error and information. [Source: feature request §1]
- **FR-002**: The application MUST use a single documented typography scale (font families, sizes
  and weights) for page titles, section headings, body text and labels.
  [Source: feature request §1]
- **FR-003**: The application MUST use documented scales for spacing, borders, shadows and corner
  radiuses, and every component MUST use only values from those scales. [Source: feature request §1]
- **FR-004**: All icons MUST follow one consistent style (same stroke/fill style, sizes from a
  defined set). [Source: feature request §1]
- **FR-005**: Each palette and typography value MUST be documented as either an official Liverpool
  specification, with its source, or a project design choice. No value may be presented as
  official without a source. [Source: feature request §Design Guidelines]
- **FR-006**: The interface MUST NOT use Liverpool Football Club branding, and MUST NOT include
  consumer shopping elements (cart, prices, promotional banners, product carousels, checkout calls
  to action). [Source: feature request §Design Guidelines]
- **FR-007**: The Liverpool logo or other official brand assets MUST be used only if an official
  asset is supplied; the logo MUST NOT be recreated or approximated. [Source: feature request
  §Design Guidelines]

#### Application layout

- **FR-008**: The application MUST provide a header, a side navigation menu and a main content
  area, shared by all pages. [Source: feature request §2]
- **FR-009**: The side navigation MUST show which entry is active, and MUST be operable by mouse,
  touch and keyboard. [Source: feature request §2, §4]
- **FR-019**: The side navigation MUST group its entries into named sections, shown in this order:
  1. **General**: Inicio, Buscar Producto;
  2. **Administración de catálogo**: Lista de Valores, Atributos, Grupo de Atributos, Taxonomía.

  Each section name MUST be shown above its entries as a small uppercase label that is visually
  secondary to the entries, and consecutive sections MUST be separated by spacing and a subtle
  divider. Each section's list MUST be announced with its section name to assistive technology.
  Entry order, routes and the active, hover and focus styles MUST NOT change. Entries added by later
  features MUST be assigned to a section. [Source: project owner request, 2026-11-19; see
  Clarifications, Session 2026-11-19]
- **FR-020**: Each navigation entry MUST have its own icon that represents its function (house,
  search, list, tags, folder tree, network for the six entries in FR-019). The icons MUST belong to
  the project's icon set and follow FR-004. [Source: project owner request, 2026-11-19]

#### Reusable components

- **FR-010**: The application MUST provide reusable, consistently styled components for these 5
  groups:
  1. primary and secondary buttons;
  2. text inputs, including a search variant;
  3. cards and content panels, with an optional section heading;
  4. page titles;
  5. status badges and notifications.
  
  [Source: feature request §3, reduced by user decision 2026-11-08; see Clarifications and
  Deferred Scope]
- **FR-011**: Interactive components MUST have distinct normal, hover, focus and disabled states;
  input components MUST also have an error state with a message. [Source: feature request §3]
  (states are a design choice derived from "consistent reusable components")
- **FR-012**: ~~The application MUST include a component reference page that displays every
  component from FR-010 in its states, so the design can be reviewed in the running application.
  (Technical decision TD-002)~~ **Withdrawn 2026-11-11** by the project owner: the component reference page was removed; the
  components stay and are covered by their own tests and `docs/architecture/design-system.md`.

#### Responsive behavior

- **FR-013**: The layout MUST support desktop (1024 px and wider) and tablet (768 px to 1023 px)
  widths, in both tablet orientations. [Source: feature request §2, §4]
- **FR-014**: At every supported width, navigation MUST remain reachable and the page MUST NOT
  scroll horizontally. [Source: feature request §4]

#### Preservation and scope limits

- **FR-015**: The existing frontend behavior (application start-up and the backend connection
  check on the home page, using the unchanged existing endpoint) MUST keep working. The result
  MUST be shown as a Spanish success or error message; the backend's response text MUST NOT be
  displayed. [Source: feature request §Acceptance Criteria 6; user clarification, 2026-11-08]
- **FR-016**: The backend MUST NOT be changed. [Source: feature request §Technical Constraints,
  §Acceptance Criteria 7]
- **FR-017**: The feature MUST NOT add product management, authentication, data persistence, API
  integration or other business functionality. Navigation entries MUST NOT represent PIM business
  sections that no specification defines yet. [Source: feature request §Out of Scope; constitution
  Principle IV]
- **FR-018**: All user-visible labels and messages MUST be in Spanish (es-MX), including the
  existing home page texts (application name and backend connection messages), which are
  replaced by Spanish texts written in the frontend. A language switcher is out of scope. [Source: user clarification, 2026-11-08, Q1]

### Observed Existing Behavior

These items come from tier 3 evidence. They are not requirements and are listed for context only.

- **OEB-001**: The current Liverpool Vendor Center frontend (which P360 catalog screens live in)
  uses a host application with a layout component and a side menu filtered by user profile, built
  with a third-party component library. [Evidence: p360:docs/discovery/migracion-p360/06-actividades-frontend.md:3-15,87,173]
  This feature does not adopt that library or profile filtering (see TD-003, A-005).
- **OEB-002**: No theme, palette or color definition files for the existing frontend were found in
  the reference repository, so no existing Liverpool color values can be cited as evidence.

### Technical Decisions

- **TD-001**: Visual identity values (palette, type scale, spacing, radius, shadow) are defined once
  in a central, documented place and consumed by all components, so the identity can be updated
  later (for example, when official brand guidelines arrive) without editing each component.
  Rationale: feature request §Technical Constraints ("keep visual styling separate and
  maintainable").
- **TD-002** (withdrawn 2026-11-11 with FR-012): A component reference page is part of the deliverable. Rationale: the feature has no
  business screens to show the components, and acceptance criteria 1 and 3 need something
  reviewable in the running application.
- **TD-003**: No new UI component library or icon library is assumed; whether one is needed is
  decided and justified in `plan.md`. Rationale: feature request §Technical Constraints ("do not
  introduce unnecessary dependencies"); constitution Principles II and V.
- **TD-004**: The backend base URL used by the connection check (FR-015) comes from environment
  configuration (`VITE_API_BASE_URL`), not from source code. A tracked `frontend/.env.example`
  documents the local development value. If the value is not configured, the home page shows the
  Spanish "could not connect" message without calling the backend. Rationale: constitution
  Principle VIII (environment-specific configuration outside source code); analysis finding C1.

### Key Entities

- **Design token**: a named visual value (color, font size, font weight, spacing step, radius,
  shadow, breakpoint) with its value and its origin (official Liverpool specification with source,
  or project design choice).
- **Navigation entry**: a label, an icon, a target page and the section it is listed under in the
  side navigation.
- **Navigation section**: a named group of navigation entries, shown in a fixed order (FR-019).
  *(Added 2026-11-19.)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of pages share the same header, side navigation and content area layout.
- **SC-002**: 100% of colors, font sizes, font weights, spacing, radius and shadow values used in
  the interface come from the documented visual identity (verified by review of the styling).
- **SC-003**: 100% of palette and typography values are labeled as official (with source) or as a
  design choice.
- **SC-004**: ~~All 5 component groups in FR-010 appear on the component reference page in all
  applicable states.~~ **Withdrawn 2026-11-11** by the project owner: the component reference page was removed; the
  components stay and are covered by their own tests and `docs/architecture/design-system.md`.
- **SC-005**: At 1440 px, 1024 px, 1023 px and 768 px wide, no page scrolls horizontally and
  navigation is reachable within one interaction.
- **SC-006**: Text and interactive elements meet WCAG 2.1 AA color contrast (4.5:1 for body text,
  3:1 for large text and UI component boundaries).
- **SC-007**: In a review by at least 3 PIM stakeholders, all agree the interface reads as an
  internal business application, not a shopping website, and recognise it as related to the
  Liverpool brand.
- **SC-008**: The frontend builds with zero type or build errors and starts successfully; the
  backend has no changes in the feature's diff.

## Assumptions

- **A-001**: No official Liverpool brand guidelines, fonts or logo files are available in
  `docs/source-of-truth/` or the reference repository. Palette and typography will therefore be
  proposed as design choices compatible with the Liverpool digital look (FR-005) until official
  assets are supplied.
- **A-002**: The side navigation will contain only non-business entries for now: a home page
  (showing the result of the existing backend connection check) and the component reference page. Business
  entries are added by the specifications that introduce those sections.
- **A-003**: The header shows the application name as text and reserves space for an official logo
  to be added later (FR-007).
- **A-004**: Supported targets are current desktop browsers and tablets at 768 px width or more.
  Phone layouts are out of scope.
- **A-005**: There is no user identity yet, so the header shows no user menu and navigation is not
  filtered by profile.
- **A-006**: WCAG 2.1 AA contrast is the accessibility baseline.

## Deferred Scope

Listed in the feature request §3 but moved to a follow-up feature by user decision (2026-11-08):

- dropdowns and selection controls (select, checkbox, radio group);
- tables (including empty state and horizontal scrolling inside the table).

They will reuse this feature's tokens, form-field styles and component patterns.

## Open Questions

- **OQ-002**: Can the team obtain official Liverpool brand guidelines (colors, typography, logo) and
  permission to use the logo in this internal application? Until then, A-001 and A-003 apply.
  Non-blocking.

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-08) | Request | Basis of all requirements |
| Project owner request (2026-11-19) | Request | Navigation sections and icons (FR-019, FR-020) |
| `.specify/memory/constitution.md` (v2.0.0) | Governance | Principles II, IV, V, IX, XI applied |
| `docs/source-of-truth/README.md`; all `docs/source-of-truth/*` subfolders | 1 | Subfolders are empty; no UI or brand requirements |
| `p360:requerimientos-funcionales/`, `p360:docs/`, `p360:diagramas-to-be/` (search: look and feel, palette, visual identity, style guide, design system, typography, side menu, responsive, tablet) | 2 | No documented UI or brand requirements |
| `p360:docs/discovery/migracion-p360/06-actividades-frontend.md` | 3 (analysis of existing code) | OEB-001 |
| `p360:P360-repositorios/` (search for theme/palette/colors files) | 3 | None found (OEB-002) |
| `frontend/` in pim-platform (`package.json`, `src/App.tsx`, `src/main.tsx`) | Current code | Existing behavior preserved by FR-015 |
