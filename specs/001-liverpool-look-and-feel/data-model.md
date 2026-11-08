# Data Model: Liverpool-Inspired Frontend Look & Feel

**Feature**: [spec.md](./spec.md) | **Date**: 2026-11-08

This feature has no business data and no persistence. Its "entities" (spec §Key Entities) are
static configuration that ships with the frontend.

## Design Token

A named visual value defined once in `frontend/src/styles/tokens.css` and documented in
`docs/architecture/design-system.md`.

| Field | Type | Rules |
|---|---|---|
| name | string | CSS custom property, kebab-case, prefixed by category: `--color-`, `--font-`, `--text-`, `--weight-`, `--leading-`, `--space-`, `--radius-`, `--shadow-`, `--border-`, `--layout-`, `--z-` |
| category | enum | color · font family · font size · font weight · line height · spacing · radius · shadow · border · layout · z-index |
| value | CSS value | Colors in hex; sizes in rem (font) or px (spacing/radius/layout) |
| use | text | Where the token applies (documentation only) |
| origin | enum | `Design choice` or `Official — <source>`. All values are `Design choice` today (spec A-001, FR-005) |

**Validation rules**
- Component CSS Modules MUST use only `var(--…)` tokens for color, font size, font weight,
  spacing, radius and shadow. Literal values are allowed only for `0`, `1px` borders, `100%`
  and `transparent` (spec FR-003, SC-002). Checked in review with a search for hex colors and px
  values in `*.module.css`.
- Text/background color pairs used together MUST meet WCAG 2.1 AA (4.5:1 text, 3:1 large text
  and control borders) (SC-006). Verified pairs: [research.md R-02](./research.md#r-02-color-palette-design-choice).
- A token whose origin is `Official` MUST cite its source document.

Token values: [research.md](./research.md) R-02 (color), R-03 (typography), R-04 (spacing,
borders, radius, shadows, layout). Breakpoint: `1024px`. CSS custom properties cannot be used
inside media queries, so the breakpoint value is documented here and repeated in the layout CSS
Modules.

## Navigation Entry

Defined in `frontend/src/navigation.ts`.

| Field | Type | Rules |
|---|---|---|
| path | string | Hash path starting with `/`, unique (`/`, `/componentes`) |
| label | string | Spanish (es-MX), shown in side navigation and used as page title |
| icon | IconName | One of the icon set names ([contracts](./contracts/ui-components.md#icon)) |

**Initial entries** (spec A-002):

| path | label | icon | page |
|---|---|---|---|
| `/` | Inicio | `home` | HomePage |
| `/componentes` | Componentes | `components` | ComponentGalleryPage |

**Rules**
- Exactly one entry is active: the one whose `path` equals the current hash path. Unknown or empty
  hashes resolve to `/` (R-06).
- Entries for PIM business sections MUST NOT be added by this feature (FR-017).

## Backend Connection Status (home page view state)

Not persisted. Represents the result of the existing `GET /api/v1/hello` call.

| State | Shown as | Text (es-MX) |
|---|---|---|
| `checking` | Info notification | "Verificando conexión con el servidor…" |
| `connected` | Success notification | "Conexión con el servidor establecida." |
| `unavailable` | Error notification | "No se pudo conectar con el servidor." |

Transitions: `checking` → `connected` (response OK) · `checking` → `unavailable` (network error
or non-OK status). The check runs once per visit to the home page. The response body (English
text from the backend) is not displayed (R-13, FR-015).
