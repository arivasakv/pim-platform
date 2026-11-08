# Feature Specification: Liverpool-Inspired Frontend Look & Feel

**Feature Branch**: `001-liverpool-look-and-feel`

**Created**: 2026-11-08

**Status**: Draft

**Input**: User description: "Feature: Liverpool-Inspired Frontend Look & Feel — establish a consistent visual identity for the PIM frontend inspired by the Mexican Liverpool department store's digital experience. The application must remain a PIM application; Liverpool is a visual reference, not the functional scope." (full description supplied by the user in the `/speckit-specify` request of 2026-11-08; referred to below as *the feature request*)

## Clarifications

### Session 2026-11-08

- Q: Language of on-screen labels? → A: Spanish (es-MX); existing English texts are translated
  (FR-018).

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
   backend connection message is still displayed inside the main content area.
4. **Given** the backend is not running, **When** the user opens the home page, **Then** the shell
   still renders and the existing "could not connect" message is shown in Spanish, styled as a
   status notification.

---

### User Story 2 - Recognisable, consistent visual identity (Priority: P2)

A PIM user perceives the interface as clean, modern and visually related to the Liverpool
department store brand, while clearly being an internal business tool rather than a shopping
website. Colors, typography, spacing, borders, shadows, corner radiuses and icons look the same on
every page.

**Why this priority**: The visual identity is the core purpose of the feature, but it needs the
shell from Story 1 to be visible.

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

### User Story 3 - Reusable UI components (Priority: P2)

A frontend developer building future PIM screens can reuse a shared set of styled components —
primary and secondary buttons, text inputs, search fields, dropdowns and selection controls,
tables, cards and content panels, page titles and section headings, status indicators and
notifications — instead of styling each screen from scratch.

**Why this priority**: Reusable components are what keep the identity consistent as product
features are added later.

**Independent Test**: Open the component reference page and confirm every listed component is
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

### User Story 4 - Desktop and tablet use (Priority: P3)

A PIM user works on a desktop monitor or a tablet. On both, navigation remains reachable, content
fits the screen width and nothing is cut off.

**Why this priority**: Layout adaptation refines the shell; the application is usable on desktop
without it.

**Independent Test**: View every page at 1440 px, 1024 px and 768 px wide (portrait and landscape
tablet) and confirm navigation is reachable and no horizontal page scrolling occurs.

**Acceptance Scenarios**:

1. **Given** a desktop-width screen (1024 px or wider), **When** the user opens the application,
   **Then** the side navigation is permanently visible next to the content area.
2. **Given** a tablet-width screen (768 px to 1023 px), **When** the user opens the application,
   **Then** the navigation is reachable through a clearly visible control in the header and does
   not cover content until opened.
3. **Given** any supported width, **When** a page contains a table wider than the content area,
   **Then** the table scrolls within its own container and the page itself does not scroll
   horizontally.

### Edge Cases

- Very long page titles, navigation labels or table cell text: text wraps or is truncated with the
  full value available (e.g. on hover/focus); the layout does not break.
- Empty table: a styled empty-state message is shown instead of a blank area.
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
  and weights) for page titles, section headings, body text, labels and table text.
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

#### Reusable components

- **FR-010**: The application MUST provide reusable, consistently styled components for: primary
  and secondary buttons; text inputs and search fields; dropdowns and selection controls (at least
  select, checkbox and radio); tables; cards and content panels; page titles and section headings;
  status indicators and notifications. [Source: feature request §3]
- **FR-011**: Interactive components MUST have distinct normal, hover, focus and disabled states;
  input components MUST also have an error state with a message. [Source: feature request §3]
  (states are a design choice derived from "consistent reusable components")
- **FR-012**: The application MUST include a component reference page that displays every
  component from FR-010 in its states, so the design can be reviewed in the running application.
  (Technical decision TD-002)

#### Responsive behavior

- **FR-013**: The layout MUST support desktop (1024 px and wider) and tablet (768 px to 1023 px)
  widths, in both tablet orientations. [Source: feature request §2, §4]
- **FR-014**: At every supported width, navigation MUST remain reachable and the page MUST NOT
  scroll horizontally. [Source: feature request §4]

#### Preservation and scope limits

- **FR-015**: The existing frontend behavior (application start-up and the backend connection
  message on the home page) MUST keep working. [Source: feature request §Acceptance Criteria 6]
- **FR-016**: The backend MUST NOT be changed. [Source: feature request §Technical Constraints,
  §Acceptance Criteria 7]
- **FR-017**: The feature MUST NOT add product management, authentication, data persistence, API
  integration or other business functionality. Navigation entries MUST NOT represent PIM business
  sections that no specification defines yet. [Source: feature request §Out of Scope; constitution
  Principle IV]
- **FR-018**: All user-visible labels and messages MUST be in Spanish (es-MX), including the
  existing home page texts (application name subtitle and backend connection messages), which are
  translated. A language switcher is out of scope. [Source: user clarification, 2026-11-08, Q1]

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
- **TD-002**: A component reference page is part of the deliverable. Rationale: the feature has no
  business screens to show the components, and acceptance criteria 1 and 3 need something
  reviewable in the running application.
- **TD-003**: No new UI component library or icon library is assumed; whether one is needed is
  decided and justified in `plan.md`. Rationale: feature request §Technical Constraints ("do not
  introduce unnecessary dependencies"); constitution Principles II and V.

### Key Entities

- **Design token**: a named visual value (color, font size, font weight, spacing step, radius,
  shadow, breakpoint) with its value and its origin (official Liverpool specification with source,
  or project design choice).
- **Navigation entry**: a label, an icon and a target page shown in the side navigation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of pages share the same header, side navigation and content area layout.
- **SC-002**: 100% of colors, font sizes, font weights, spacing, radius and shadow values used in
  the interface come from the documented visual identity (verified by review of the styling).
- **SC-003**: 100% of palette and typography values are labeled as official (with source) or as a
  design choice.
- **SC-004**: All 7 component groups in FR-010 appear on the component reference page in all
  applicable states.
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
  (showing the existing backend connection message) and the component reference page. Business
  entries are added by the specifications that introduce those sections.
- **A-003**: The header shows the application name as text and reserves space for an official logo
  to be added later (FR-007).
- **A-004**: Supported targets are current desktop browsers and tablets at 768 px width or more.
  Phone layouts are out of scope.
- **A-005**: There is no user identity yet, so the header shows no user menu and navigation is not
  filtered by profile.
- **A-006**: WCAG 2.1 AA contrast is the accessibility baseline.

## Open Questions

- **OQ-002**: Can the team obtain official Liverpool brand guidelines (colors, typography, logo) and
  permission to use the logo in this internal application? Until then, A-001 and A-003 apply.
  Non-blocking.

## Sources Consulted

| Source | Tier | Result |
|---|---|---|
| Feature request (user, 2026-11-08) | Request | Basis of all requirements |
| `.specify/memory/constitution.md` (v2.0.0) | Governance | Principles II, IV, V, IX, XI applied |
| `docs/source-of-truth/README.md`; all `docs/source-of-truth/*` subfolders | 1 | Subfolders are empty; no UI or brand requirements |
| `p360:requerimientos-funcionales/`, `p360:docs/`, `p360:diagramas-to-be/` (search: look and feel, palette, visual identity, style guide, design system, typography, side menu, responsive, tablet) | 2 | No documented UI or brand requirements |
| `p360:docs/discovery/migracion-p360/06-actividades-frontend.md` | 3 (analysis of existing code) | OEB-001 |
| `p360:P360-repositorios/` (search for theme/palette/colors files) | 3 | None found (OEB-002) |
| `frontend/` in pim-platform (`package.json`, `src/App.tsx`, `src/main.tsx`) | Current code | Existing behavior preserved by FR-015 |
