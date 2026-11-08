# Implementation Plan: Liverpool-Inspired Frontend Look & Feel

**Branch**: `001-liverpool-look-and-feel` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-liverpool-look-and-feel/spec.md`

## Summary

Give the PIM frontend a simple, consistent visual foundation inspired by Liverpool, the Mexican
department store, as an internal business tool, not a shop. The work covers:

1. design tokens (colors, typography, spacing, radius, shadows) defined once as CSS custom
   properties;
2. an application frame (header, side navigation, content area) that also works on tablets;
3. a home page with the backend connection status;
4. a small set of reusable UI components (Button, TextField, Card, PageHeader, StatusBadge,
   Notification, Icon), shown on a component reference page.

Dropdowns, selection controls and tables are deferred to a follow-up feature (spec, Deferred
Scope).

The home page keeps its existing backend connection check and shows the result in Spanish. All
labels are in Spanish (es-MX). Frontend tooling is aligned with the constitution: ESLint +
Prettier replace Oxlint, and Vitest is added for tests. Frontend only: no backend changes, no
product functionality, no new runtime dependencies. Details and alternatives:
[research.md](./research.md).

## Planning Principles (user guidance, 2026-11-08)

1. **Beginner-friendly**: essential visual foundation first; no abstractions the spec does not
   need.
2. **Keep existing files**: no frontend file or asset is deleted unless it is confirmed unused and
   its removal is necessary. The only removal is `.oxlintrc.json`, which becomes unused once Oxlint
   is replaced and would otherwise leave a stale, misleading linter config (R-10). Other files are
   reused and their content replaced where needed.
3. **Design choices, not official specs**: all Liverpool-inspired colors and typography are
   documented as project design choices. Nothing is labeled official without a verified official
   reference (spec FR-005, A-001).
4. **Backend message (R-13, resolved)**: the existing endpoint stays unchanged. The frontend does
   the connection check and shows only a Spanish success or error message. The English backend
   response is never shown.
5. **Versions from the repo and the registry**: current versions are read from
   `frontend/package.json` and `frontend/node_modules`. New tool versions are the latest
   published on 2026-11-08, checked against each package's peer-dependency and Node engine ranges
   (R-16). Nothing is assumed.
6. **Backend untouched**: no file under `backend/` changes.
7. **Existing structure kept**: `frontend/` keeps its current layout. New code goes into a few new
   folders under `src/`.
8. **Constitution unchanged**: tooling is brought into line with constitution V (ESLint +
   Prettier). The constitution itself is not modified.

## Technical Context

### Existing packages (checked 2026-11-08)

| Package | Declared now | Installed now | Planned |
|---|---|---|---|
| react / react-dom | ^19.3.1 | 19.3.1 | keep |
| vite | ^8.3.6 | 8.3.6 | keep |
| @vitejs/plugin-react | ^6.1.2 | 6.2.1 | keep |
| @types/react / @types/react-dom | ^19.3.0 | 19.3.0 | keep |
| @types/node | ^24.19.2 | 24.19.2 | keep |
| typescript | ~7.0.2 | 7.0.2 | **change to `~6.0.3`** (R-16, user decision 2026-11-08) |
| oxlint | ^1.90.0 | 1.92.0 | **remove** (replaced by ESLint, R-10) |

### New dev dependencies (latest on 2026-11-08, peer ranges verified, R-16)

| Package | Version range | Purpose | Compatibility checked |
|---|---|---|---|
| eslint | ^10.14.0 | Linter | Node ^20.19 / ^22.13 / >=24 |
| @eslint/js | ^10.0.1 | ESLint recommended JS rules | peer eslint ^10 |
| typescript-eslint | ^8.72.1 | TypeScript parser and rules for ESLint | peer eslint ^8.57–^10, **typescript >=4.8.4 <6.1.0** |
| eslint-plugin-react-hooks | ^7.1.1 | Rules of Hooks | peer eslint up to ^10 |
| eslint-plugin-react-refresh | ^0.5.7 | Vite fast-refresh safety | peer eslint ^9 / ^10 |
| globals | ^17.14.0 | Browser globals for ESLint | — |
| eslint-config-prettier | ^10.1.8 | Turns off ESLint rules that conflict with Prettier | peer eslint >=7 |
| prettier | ^3.9.10 | Code formatter | Node >=14 |
| jiti | ^2.7.0 | Lets ESLint load a TypeScript config file (`eslint.config.ts`) | optional peer of eslint |
| vitest | ^5.1.0 | Test runner | peer vite ^6.4 / ^7 / ^8, @types/node ^22 / >=24 |
| jsdom | ^31.0.0 | Browser-like DOM for tests | peer of vitest |
| @testing-library/react | ^16.3.3 | Render and query components in tests | peer react ^18 / ^19, @testing-library/dom ^10 |
| @testing-library/dom | ^10.4.2 | Required peer of @testing-library/react | — |

**Node.js requirement** (intersection of vite, eslint and vitest engine ranges): `^22.13.0 ||
^24.0.0 || >=26.0.0`. The development machine runs Node 26.10.0.

### npm scripts (`frontend/package.json`)

| Script | Command | Status |
|---|---|---|
| `dev` | `vite` | keep |
| `build` | `tsc -b && vite build` | keep |
| `preview` | `vite preview` | keep |
| `lint` | `eslint .` | **change** (was `oxlint`) |
| `format` | `prettier --write .` | **new** |
| `format:check` | `prettier --check .` | **new** |
| `test` | `vitest run` | **new** |

**Language/Version**: TypeScript ~6.0.3 (strict), React 19.3.1, CSS (custom properties + CSS
Modules, both supported by Vite with no plugin)

**Primary Dependencies**: React 19.3.1, Vite 8.3.6 (runtime/build, unchanged). Dev tooling as
listed above. No new runtime dependencies.

**Storage**: N/A

**Configuration**: `VITE_API_BASE_URL` (backend base URL, non-sensitive, exposed to the bundle).
Developers copy `frontend/.env.example` to `frontend/.env` (git-ignored). Without it, the home
page reports the backend as unavailable without calling it (spec TD-004, R-15).

**Testing**: Vitest + Testing Library, a few behavior-focused tests (navigation, shell and
tablet drawer, notifications, home page connection messages, Button and TextField states) and one
smoke test of the component reference page. Manual visual and responsive checks per [quickstart.md](./quickstart.md).

**Target Platform**: Current desktop browsers and tablets at 768 px width or more

**Project Type**: Web application (separate `frontend/` and `backend/`); this feature touches
`frontend/` only

**Performance Goals**: No web font downloads; no layout shift when the shell loads

**Constraints**: No horizontal page scroll at 768 px and wider; WCAG 2.1 AA contrast; keyboard
operable; backend untouched; no new runtime dependencies; `npm run lint`,
`npm run format:check`, `npm test` and `npm run build` all pass with zero errors

**Scale/Scope**: 2 pages (Inicio, Componentes), 3 layout components, 6 UI components and 1 Icon
component (9 icons)

**Development environment**: Windows with PowerShell. Commands in tasks and quickstart are
PowerShell-compatible; the backend is started with `.\gradlew.bat bootRun`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Specification-Driven | ✅ Pass | Spec has no markers; R-13 resolved and recorded in spec Clarifications. Tasks will reference FR-xxx |
| II | Technology Stack | ✅ Pass | React + TypeScript + Vite; `"strict": true` set explicitly (R-11); TypeScript stays the language (version pinned to ~6.0.3 for tooling compatibility, R-16). ESLint config written in TypeScript (`eslint.config.ts`), so no plain `.js` file is added. No UI, state, router or icon library |
| III | Separated Apps | ✅ Pass | Frontend only; calls the existing `GET /api/v1/hello` unchanged |
| IV | PIM Business Scope | ✅ Pass | No business domains; navigation holds only Inicio and Componentes (FR-017) |
| V | Code Quality | ✅ Pass | ESLint (typescript-eslint, React Hooks, React Refresh rules) + Prettier, configured to not conflict; `lint` and `format:check` must pass with zero errors. New dev dependencies justified in Technical Context and R-09/R-10/R-16; Oxlint removed |
| VI | Testing | ✅ Pass | Tests for all frontend logic and interactive components; `npm test` added. No new endpoints, so no API integration tests |
| VII | API Design | ✅ N/A | No new or changed endpoints. UI contracts in `contracts/ui-components.md` |
| VIII | Security | ✅ Pass | No secrets. The backend base URL is environment configuration (`VITE_API_BASE_URL`), documented in a tracked `.env.example`; local `.env` files stay git-ignored (spec TD-004, R-15). Corrected after analysis finding C1 |
| IX | Development Scope | ✅ Pass | No persistence, integrations, auth or AI |
| X | AI Governance | ✅ Pass | Tooling changes and TypeScript version change explained with alternatives (R-10, R-16) and approved by the user; only `.oxlintrc.json` removed (necessary, R-10) |
| XI | Source of Truth | ✅ Pass | Sources listed in spec; colors and typography labeled as design choices (R-02, R-03, R-08); reference repository read only |

**Gate result**: PASS. No deviations.

**Post-design re-check (after Phase 1)**: PASS. The design adds no runtime dependencies,
endpoints or business entities. New dev tooling is required by constitution V and VI.

## Project Structure

### Documentation (this feature)

```text
specs/001-liverpool-look-and-feel/
├── spec.md
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1: design tokens, navigation entries, connection status
├── quickstart.md        # Phase 1: validation guide
├── contracts/
│   └── ui-components.md # Phase 1: component props contracts
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)

docs/architecture/
└── design-system.md     # Visual identity documentation; every value marked "Design choice" (R-08)
```

### Source Code

The existing `frontend/` layout is kept. Legend: **[keep]** unchanged, **[update]** existing file
with new content, **[new]** added, **[remove]** deleted (only when unused and necessary).

```text
frontend/
├── index.html                 [update] lang="es-MX", title "Plataforma PIM"
├── package.json               [update] scripts (lint, format, format:check, test); typescript ~6.0.3;
│                                       oxlint removed; ESLint, Prettier and test dev dependencies added
├── package-lock.json          [update] by npm install
├── eslint.config.ts           [new]    ESLint flat config (R-10)
├── .prettierrc.json           [new]    Prettier options (R-10)
├── .prettierignore            [new]    dist, coverage, package-lock.json
├── .oxlintrc.json             [remove] unused after Oxlint is replaced (R-10)
├── README.md                  [update] Oxlint section replaced by the lint/format/test commands
├── .env.example               [new]    tracked template: VITE_API_BASE_URL=http://localhost:8080 (R-15)
├── .gitignore                 [update] + coverage, .env, .env.*; exception !.env.example (R-15)
├── tsconfig.json              [keep]
├── tsconfig.app.json          [update] + "strict": true (tests import Vitest functions explicitly, so no global test types)
├── tsconfig.node.json         [update] + "strict": true; include eslint.config.ts
├── vite.config.ts             [update] + Vitest "test" block (jsdom, setup file)
├── public/
│   ├── favicon.svg            [keep]
│   └── icons.svg              [keep] unused today, not deleted
└── src/
    ├── main.tsx               [keep] entry point (may be reformatted by Prettier)
    ├── vite-env.d.ts          [new] type for import.meta.env.VITE_API_BASE_URL (R-15)
    ├── index.css              [update] scaffold styles replaced by global base styles; imports tokens.css
    ├── App.css                [update] scaffold styles replaced by the app frame layout (header, navigation, content, drawer)
    ├── App.tsx                [update] renders AppShell and the current page
    ├── assets/                [keep] hero.png, react.svg, vite.svg unused today, not deleted
    ├── styles/
    │   └── tokens.css         [new] all design tokens, one source of the visual identity
    ├── navigation.ts          [new] page list (Inicio, Componentes) + useCurrentPath hook
    ├── navigation.test.ts     [new]
    ├── components/
    │   ├── layout/            [new] AppShell.tsx (+ test), AppHeader.tsx, SideNavigation.tsx (styled by App.css)
    │   └── ui/                [new] Icon.tsx, and Button, TextField, Card, PageHeader, StatusBadge,
    │                                Notification (each .tsx + .module.css), Notification.test.tsx,
    │                                Components.test.tsx (Button + TextField), index.ts
    ├── pages/
    │   ├── HomePage.tsx       [new] connection check moved from App.tsx; backend URL from VITE_API_BASE_URL; Spanish messages (+ test)
    │   └── ComponentGalleryPage.tsx [new] component reference page, FR-012 (+ smoke test)
    └── test/
        └── setup.ts           [new] Testing Library cleanup
```

`backend/` is not touched.

**Structure Decision**: Keep the Vite project exactly where it is and reuse its files:
- `index.css` stays the global stylesheet (now base styles + tokens).
- `App.css` stays the app-level stylesheet, now holding the frame layout used by the three layout
  components.
- `App.tsx` stays the root component.

Tool configuration sits at the `frontend/` root, next to `vite.config.ts`, as Vite projects
usually do. New code goes into a few flat, role-based folders (`styles/`, `components/layout/`,
`components/ui/`, `pages/`) that a beginner can navigate. Reusable UI components use CSS Modules
so their class names never clash as the app grows. Later features add a page in `pages/` and one
entry in `navigation.ts`.

## Implementation Order (guidance for `/speckit-tasks`)

1. **Tooling first**:
   - change the TypeScript range to `~6.0.3`, remove Oxlint and `.oxlintrc.json`, and add the
     ESLint, Prettier and test dev dependencies, configs and scripts;
   - run `npm run format` once to align existing files with Prettier;
   - confirm `lint`, `format:check`, `build` and the (empty) test run pass before any UI work.
2. Tokens, base styles and icons.
3. US1 (P1): app frame and home page with connection status.
4. US4 (P2): tablet drawer and responsive layout.
5. US3 (P3): remaining reusable components and the reference page.
6. US2 (P4): design-system documentation and contrast check; then the quickstart validation.

## Complexity Tracking

No constitution violations. New development dependencies are required by constitution V (ESLint +
Prettier) and VI (tests) and are justified in Technical Context and research R-09, R-10 and R-16.

## Open Items

- **TypeScript 7 later**: once typescript-eslint publishes a release whose `typescript` peer range
  includes 7.x, the project can return to TypeScript 7 in a separate change (R-16).
- **Spec OQ-002** (non-blocking): official Liverpool brand guidelines or logo. Until verified
  references are provided, every color and typography value stays labeled "Design choice". If
  they arrive, only `styles/tokens.css` and `docs/architecture/design-system.md` change.
