# Research: Liverpool-Inspired Frontend Look & Feel

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-08

Each entry gives the decision, why it was chosen, and the alternatives considered. All palette and
typography values here are **project design choices**, not official Liverpool specifications
(spec FR-005, A-001): no official brand guidelines were found in `docs/source-of-truth/` or in the
reference repository (spec OEB-002).

## Baseline findings (current `frontend/`)

| Finding | Location | Impact |
|---|---|---|
| Installed: react/react-dom 19.3.1, vite 8.3.6, typescript 7.0.2, @vitejs/plugin-react 6.2.1, oxlint 1.92.0; no runtime dependencies besides React (read from `package.json` and `node_modules/*/package.json`) | `frontend/package.json` | Stack matches constitution II; full table in plan Technical Context |
| `strict` not written in tsconfig; TypeScript 7 enables it by default (`tsc --showConfig` lists no override) | `frontend/tsconfig.app.json` | Compliant, but not visible → R-11 |
| TypeScript 7.0.2 package exposes only `tsc` and `unstable/*` entry points, no classic compiler API | `frontend/node_modules/typescript/package.json` | typescript-eslint cannot use it → R-16 |
| No test runner or test script | `frontend/package.json` | Constitution VI cannot be met → R-09 |
| Lint is `oxlint`, no ESLint/Prettier | `frontend/package.json`, `frontend/.oxlintrc.json` | Not compliant with constitution V; replaced by ESLint + Prettier → R-10 |
| Vite scaffold styles (purple accent, 1126 px centered column, dark mode, hero/counter classes) | `frontend/src/index.css`, `frontend/src/App.css` | Files kept, content replaced → R-14 |
| Home page fetches `GET http://localhost:8080/api/v1/hello` and shows its text, or "Could not connect to backend" | `frontend/src/App.tsx` | Kept (FR-015) |
| Backend returns the fixed English text `Hello from PIM Backend!` | `backend/src/main/java/com/pim/backend/HelloController.java:13` | Conflicts with FR-018 (Spanish) while FR-016 forbids backend changes → R-13 |
| Unused scaffold assets: `src/assets/hero.png`, `react.svg`, `vite.svg`, `public/icons.svg` (only `favicon.svg` is referenced) | `frontend/src/assets/`, `frontend/public/` | Kept: removal is not necessary → R-14 |

## R-01 Styling approach

- **Decision**: Plain CSS. Design tokens as CSS custom properties in one file
  (`src/styles/tokens.css`); global base styles in the existing `src/index.css`; the app frame
  (header, navigation, content, drawer) in the existing `src/App.css`; one CSS Module
  (`*.module.css`) per reusable UI component. All styles reference only `var(--…)` tokens.
- **Rationale**: Vite supports CSS Modules natively and `vite/client` types already declare them,
  so no dependency is added. Keeps styling separate from component logic (spec TD-001) and lets the
  identity be changed in one file when official guidelines arrive.
- **Alternatives**: Tailwind (new build dependency and utility classes spread the identity across
  markup); CSS-in-JS such as Emotion (runtime dependency; constitution II requires an ADR); MUI, as
  used by the existing Vendor Center (spec OEB-001) (large dependency, its own look to override,
  ADR required). All rejected under "no unnecessary dependencies".

## R-02 Color palette (design choice)

Liverpool's digital presence is recognisable by a pink/magenta brand color. Because no official
value is available, the primary color is a **design choice** picked to read as "Liverpool pink"
while meeting WCAG AA with white text. Contrast ratios were computed with the WCAG 2.1 relative
luminance formula.

| Token | Value | Use | Contrast check |
|---|---|---|---|
| `--color-primary` | `#C4007F` | Header background, primary buttons, active navigation marker, links | white on it 5.75:1 |
| `--color-primary-hover` | `#A3006C` | Hover/pressed primary | white on it 7.57:1 |
| `--color-primary-subtle` | `#FCE6F3` | Active navigation background, selected rows | primary text on it 4.86:1 |
| `--color-text` | `#1F1F24` | Body text, headings | on white 16.41:1 |
| `--color-text-secondary` | `#4A4A55` | Secondary text, labels | on page background 8.03:1 |
| `--color-text-muted` | `#6B6B76` | Hints, placeholders, captions | on white 5.26:1 |
| `--color-surface` | `#FFFFFF` | Cards, panels, navigation, inputs | — |
| `--color-background` | `#F5F5F7` | Page background behind panels | — |
| `--color-border-control` | `#8A8A94` | Input borders | on white 3.42:1 (≥3:1 UI boundary) |
| `--color-border-subtle` | `#E3E3E8` | Card and navigation borders (decorative) | exempt |
| `--color-success` / `-subtle` | `#176B34` / `#E7F4EC` | Success status | 5.82:1 on subtle |
| `--color-warning` / `-subtle` | `#9A5B00` / `#FDF3E1` | Warning status | 4.93:1 on subtle |
| `--color-error` / `-subtle` | `#C62828` / `#FDECEC` | Error status, field errors | 4.92:1 on subtle |
| `--color-info` / `-subtle` | `#1565C0` / `#E8F1FB` | Information status | 5.04:1 on subtle |

- **Rationale**: The commonly seen Liverpool pink (around `#E10098`) gives only 4.51:1 with white
  and 4.14:1 on the light background, too close to the AA limit. A slightly deeper pink keeps the
  brand association and passes AA. The pink is used only as an accent (header, primary actions,
  active state) so the workspace stays neutral and business-like, not promotional.
- **Alternatives**: Use `#E10098` directly (borderline contrast, and would be presented as
  official without a source; rejected per FR-005); neutral palette with no pink (loses the
  Liverpool association, fails acceptance criterion 1).

## R-03 Typography (design choice)

- **Decision**: System font stack `"Segoe UI", system-ui, -apple-system, Roboto, "Helvetica Neue",
  Arial, sans-serif`. No web fonts.
  Scale (rem on a 16 px root): caption 12 px, body 14 px, body-large 16 px, section heading
  20 px, page title 24 px. Weights: 400 regular, 600 semibold, 700 bold. Line heights: 1.5 body,
  1.25 headings.
  - *Deferred*: a 32 px display size (`--text-2xl`) was dropped when the scope was reduced. It is
    not in `tokens.css` and can be added when a screen needs it.
- **Rationale**: Liverpool's official typefaces are unknown and not licensed to the project, so no
  official font is claimed or approximated (feature request §Design Guidelines). System fonts load
  instantly and need no files or licences. 14 px body suits dense business screens.
- **Alternatives**: Self-hosted Google font such as Montserrat or Inter (adds font files and could be
  mistaken for an official brand choice; revisit if official guidelines name a font).

## R-04 Spacing, borders, radius, shadows (design choice)

- **Spacing** (4 px base): `--space-1` 4, `-2` 8, `-3` 12, `-4` 16, `-5` 24, `-6` 32 (px).
  *Deferred*: a 48 px step (`--space-7`) was dropped when the scope was reduced; it is not in
  `tokens.css`.
- **Borders**: 1 px solid; focus ring 2 px `--color-primary` with 2 px offset.
- **Radius**: `--radius-sm` 4 px (inputs, buttons), `--radius-md` 8 px (cards, panels,
  notifications), `--radius-pill` 999 px (status badges).
- **Shadows**: `--shadow-sm` `0 1px 2px rgba(31,31,36,.08)` (cards); `--shadow-md`
  `0 4px 12px rgba(31,31,36,.12)` (tablet navigation drawer, header).
- **Layout**: header height 56 px; side navigation width 240 px; content max width none, padding
  `--space-5`.
- **Rationale**: Small radius and soft shadows give a modern but restrained look suited to an
  internal tool.

## R-05 Icons

- **Decision**: An in-house set of inline SVG icons rendered by one `Icon` component: 24×24
  viewBox, outline style, 1.75 stroke, round caps and joins, `currentColor`, sizes 16/20/24.
  Initial set (9): `menu`, `close`, `home`, `components`, `search`, `info`, `warning`, `error`,
  `success`.
- **Rationale**: Meets FR-004 with no dependency; nine simple geometric icons is little
  code. Decorative icons are `aria-hidden`; icon-only buttons need an accessible label.
- **Alternatives**: lucide-react or Material Icons (new dependency; rejected under "no unnecessary
  dependencies", revisit if the set grows a lot).

## R-06 Navigation between pages

- **Decision**: One file, `src/navigation.ts`, holding the route list and a short
  `useCurrentPath` hook that reads `window.location.hash` and listens to `hashchange`:
  `#/` → Inicio, `#/componentes` → Componentes. Unknown hashes show Inicio. `App.tsx` picks the
  page with a plain `switch`.
- **Rationale**: Two pages do not justify a routing library. Hash routes work with browser
  back/forward and deep links, with no server configuration.
- **Alternatives**: react-router (new dependency; adopt via ADR when business pages need nested
  routes or parameters); in-memory state only (no deep links or back button).

## R-07 Responsive layout

- **Decision**: One breakpoint at 1024 px.
  - ≥1024 px: side navigation is always visible to the left of the content.
  - <1024 px (tablet, 768–1023 px supported): navigation becomes an off-canvas drawer opened by a
    menu button in the header (`aria-expanded`, `aria-controls`). It closes on selection, Escape,
    the close button, or a click on the backdrop. When opened, focus moves into the drawer; when
    closed, focus returns to the menu button.
  - Every page: `min-width: 0` on grid/flex children and long text wraps
    (`overflow-wrap: anywhere`), so the page never scrolls horizontally (FR-014). Table scrolling
    is deferred together with tables (spec, Deferred Scope).
- **Rationale**: A permanent 240 px menu takes 31% of a 768 px portrait tablet; a drawer keeps
  content usable while navigation stays one interaction away (SC-005).
- **Alternatives**: Collapsed icon-only rail on tablet (labels hidden, harder to understand with
  only 2 entries).

## R-08 Visual identity documentation

- **Decision**: `docs/architecture/design-system.md` documents every token with its value, use
  and origin column (`Design choice` / `Official — <source>`), the icon rules, the layout rules and
  the "not a shopping site" rule. The component reference page (`#/componentes`) is the live
  counterpart.
- **Rationale**: FR-005 and SC-003. `docs/architecture/` is the location the constitution allows
  for derived design documentation (Principle XI, Separation).

## R-09 Frontend tests

- **Decision**: Add dev dependencies `vitest` ^5.1.0, `jsdom` ^31.0.0,
  `@testing-library/react` ^16.3.3 and its required peer `@testing-library/dom` ^10.4.2 (latest on
  2026-11-08; peers checked: vitest accepts vite ^8 and @types/node >=24; Testing Library accepts
  React ^19), plus a `test` script (`vitest run`). Tests sit next to the code
  they test. They cover behavior: navigation hook, shell and tablet drawer, Button and TextField
  states (disabled, hint, error), notification roles, home page connection states. A smoke test
  renders the component reference page, which covers the purely presentational components (Icon,
  Card, PageHeader, StatusBadge).
- **Rationale**: Constitution VI requires frontend tests and a passing frontend test script.
  Vitest reuses the Vite configuration and needs no Babel or separate transform. Testing behavior
  rather than every piece of markup keeps the suite small and easy to read (user request for
  simplicity, 2026-11-08).
- **Alternatives**: No tests (violates VI); Jest (needs extra transform configuration for
  TypeScript, JSX and CSS Modules); Playwright only (browser end-to-end, heavier; manual
  responsive checks are covered by quickstart instead). `@testing-library/user-event` and
  `@testing-library/jest-dom` were left out to keep dependencies minimal: `fireEvent` and plain
  assertions on DOM properties are enough.

## R-10 Lint and format tooling: ESLint + Prettier (user decision, 2026-11-08)

- **Finding**: Constitution V requires ESLint + Prettier. The Vite scaffold uses Oxlint
  (`"lint": "oxlint"`, `.oxlintrc.json`) and has no formatter.
- **Decision**: Replace Oxlint with ESLint and add Prettier in this feature, as the first step of
  the implementation.
  - **Remove**: `oxlint` dev dependency and `frontend/.oxlintrc.json`. The config file is
    confirmed unused once Oxlint is gone, and keeping it would point readers to a linter the
    project no longer runs.
  - **ESLint** (`frontend/eslint.config.ts`, flat config), applied to `**/*.{ts,tsx}`:
    - `@eslint/js` recommended;
    - `typescript-eslint` recommended (no type-aware rules, keeping it fast and simple);
    - `eslint-plugin-react-hooks` recommended (replaces Oxlint's `react/rules-of-hooks`);
    - `eslint-plugin-react-refresh` Vite preset (replaces Oxlint's
      `react/only-export-components` with `allowConstantExport`);
    - `globals.browser`;
    - `eslint-config-prettier` last, so formatting is left to Prettier;
    - ignores `dist/` and `coverage/`.
    
    This keeps the two rules Oxlint enforced today and adds the standard recommended sets.
  - **Config in TypeScript**: `eslint.config.ts` instead of `.js`, because constitution II forbids
    adding plain `.js` files. ESLint 10 loads TypeScript config files through its optional peer
    `jiti`, which is added as a dev dependency.
  - **Prettier** (`frontend/.prettierrc.json`): `{"semi": false, "singleQuote": true,
    "trailingComma": "all", "printWidth": 100}`. Semicolons off and single quotes match the
    existing code (`App.tsx`, `main.tsx`), so the first format run changes little.
    `.prettierignore`: `dist`, `coverage`, `package-lock.json`.
  - **Scripts**: `"lint": "eslint ."`, `"format": "prettier --write ."`,
    `"format:check": "prettier --check ."`.
  - **README**: the "Expanding the Oxlint configuration" section is replaced with the
    lint/format/test commands.
- **Versions**: eslint ^10.14.0, @eslint/js ^10.0.1, typescript-eslint ^8.72.1,
  eslint-plugin-react-hooks ^7.1.1, eslint-plugin-react-refresh ^0.5.7, globals ^17.14.0,
  eslint-config-prettier ^10.1.8, prettier ^3.9.10, jiti ^2.7.0. These are the latest on
  2026-11-08, checked with `npm view <pkg> peerDependencies engines`. All accept eslint 10.
  typescript-eslint requires TypeScript `<6.1.0` → R-16.
- **Alternatives**:
  - Keep Oxlint and record a deviation (rejected by the user: the plan must comply with V).
  - Run Oxlint and ESLint side by side (two linters, confusing for beginners).
  - Use `eslint-plugin-prettier` to report formatting as lint errors (slower and noisier; a
    separate `format:check` is simpler).
  - `eslint.config.js` with no jiti (one dependency fewer, but adds a plain `.js` file contrary
    to constitution II).

## R-11 TypeScript strict mode

- **Decision**: Add `"strict": true` to `tsconfig.app.json` and `tsconfig.node.json`.
- **Rationale**: The explicit setting makes constitution II compliance visible and does not
  depend on the default of whichever TypeScript version is installed (see R-16).

## R-16 TypeScript version for ESLint compatibility (user decision, 2026-11-08)

- **Finding**: ESLint needs `typescript-eslint` to parse `.ts`/`.tsx`. Its peer range is
  `typescript >=4.8.4 <6.1.0` in every published release (latest 8.72.1, canary
  8.72.2-alpha.11). The project has TypeScript 7.0.2 (`~7.0.2`), whose package no longer exposes
  the classic compiler API that typescript-eslint uses (only `tsc` and `unstable/*` entry points).
- **Decision (user)**: Change the `typescript` dev dependency to `~6.0.3` (latest 6.x, within
  typescript-eslint's range). `tsc -b` in the `build` script keeps working the same way.
- **Impact**:
  - Type checking runs on the JavaScript-based compiler, slower than TypeScript 7's native one
    but not noticeable at this project size.
  - Every option in the current `tsconfig.app.json` and `tsconfig.node.json` exists in TypeScript
    6.0 (`erasableSyntaxOnly`, `allowArbitraryExtensions`, `module: nodenext`,
    `moduleResolution: bundler`, `verbatimModuleSyntax`).
  - The first implementation task confirms this with `npm run build`.
- **Alternatives**:
  - Keep TypeScript 7 and add TypeScript 6 under an npm alias just for ESLint (two compilers,
    fragile peer resolution).
  - Keep Oxlint until typescript-eslint supports TypeScript 7 (leaves the constitution deviation).
- **Follow-up**: return to TypeScript 7 in a separate change once typescript-eslint supports it.

## Node.js requirement

The intersection of the engine ranges of vite 8.3.6 (`^20.19.0 || >=22.12.0`), eslint 10.14.0
and @eslint/js (`^20.19.0 || ^22.13.0 || >=24`) and vitest 5.1.0 (`^22.12.0 || ^24.0.0 ||
>=26.0.0`) is **`^22.13.0 || ^24.0.0 || >=26.0.0`**. Node 20 and 25 are not supported. The
development machine runs Node 26.10.0.

## R-12 Language and document metadata

- **Decision**: All labels in Spanish (es-MX) (FR-018). `index.html` gets `lang="es-MX"` and the
  title `Plataforma PIM`. The header shows "Plataforma PIM". Navigation: "Inicio",
  "Componentes". Labels are written directly in components; no translation layer (spec Q1: Spanish
  only, no language switcher).
- **Rationale**: The `lang` attribute gives correct screen reader pronunciation and hyphenation.

## R-13 Backend connection message vs. FR-018 (resolved by the user, 2026-11-08)

- **Conflict**: FR-018 requires the backend connection messages to be in Spanish. The success
  text comes from the backend (`HelloController.java:13`, `"Hello from PIM Backend!"`), and FR-016
  forbids changing the backend.
- **Decision (user)**: Keep the existing endpoint unchanged. The frontend performs the
  connection check (`GET /api/v1/hello`) and shows a Spanish notification written by the
  frontend: while checking → "Verificando conexión con el servidor…"; success (OK response) →
  "Conexión con el servidor establecida."; failure (network error or non-OK status) → "No se pudo
  conectar con el servidor.". The response body is not read or displayed.
- **Spec updated**: User Story 1 scenarios 3–4, FR-015, FR-018 and Clarifications.
- **Alternatives rejected**: showing the English text as server data (the user rejected it);
  changing the backend text (forbidden by FR-016).

## R-14 Scaffold cleanup

- **Decision**: No frontend file or asset is deleted unless it is confirmed unused and its removal
  is necessary (user guidance, 2026-11-08). The only such file is `.oxlintrc.json` (R-10). For the
  rest:
  - `src/index.css` is kept and its scaffold rules are replaced with the global base styles and
    the token import;
  - `src/App.css` is kept and its scaffold rules are replaced with the app frame layout;
  - `src/assets/hero.png`, `react.svg`, `vite.svg` and `public/icons.svg` are unused but stay where
    they are, because removing them is not needed for this feature. They can be cleaned up later
    in a separate change once confirmed unused;
  - `public/favicon.svg` stays referenced from `index.html`.

  The scaffold's dark-mode rules disappear with the replaced CSS content. The app uses one light
  visual identity (`color-scheme: light`).
- **Rationale**: Reusing existing files keeps the project structure familiar and the diff small.
  Deleting unused assets brings no benefit to this feature.

## R-15 Backend URL from environment configuration (revised after analysis finding C1)

- **Finding**: The home page called `http://localhost:8080/api/v1/hello` with the base URL written
  in source code. Constitution VIII requires environment-specific configuration outside source
  code. The original plan deferred this; `/speckit-analyze` reported it as CRITICAL (C1).
- **Decision**:
  - The home page builds the URL as `${import.meta.env.VITE_API_BASE_URL}/api/v1/hello`. A
    trailing slash in the base URL is removed, so both `http://localhost:8080` and
    `http://localhost:8080/` work. The endpoint path itself is unchanged.
  - The variable is read when the check runs, not when the module loads, so tests can set it.
  - A tracked `frontend/.env.example` contains `VITE_API_BASE_URL=http://localhost:8080`.
    Developers copy it to `frontend/.env` (or `.env.local`), which stays git-ignored.
  - `.gitignore` keeps ignoring `.env` and `.env.*`, with the exception `!.env.example` so the
    template is tracked.
  - `src/vite-env.d.ts` declares `VITE_API_BASE_URL?: string` so the value is typed.
  - If the variable is missing or empty, the home page does not call the backend. It shows the
    Spanish "No se pudo conectar con el servidor." message and logs a developer hint to the
    console (spec TD-004).
- **Rationale**: Uses Vite's built-in environment support, so nothing is added. The URL is not
  secret, so exposing it to the bundle is allowed (constitution VIII: "Only non-sensitive values
  MAY be exposed to the frontend bundle").
- **Alternatives**:
  - A fallback to `http://localhost:8080` in code (rejected: still configuration in source code);
  - a committed `.env.development` (rejected: the user asked for a tracked `.env.example` template).
- No backend change and no new endpoint.
- No changes under `backend/` (FR-016).
