---

description: "Task list for the Liverpool-inspired frontend look & feel"
---

# Tasks: Liverpool-Inspired Frontend Look & Feel

**Input**: Design documents from `specs/001-liverpool-look-and-feel/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/ui-components.md](./contracts/ui-components.md),
[quickstart.md](./quickstart.md)

**Tests**: Included. Constitution Principle VI requires frontend tests. They are kept small and
behavior-focused, and each one is written before the code it checks (confirm it fails first).

**Organization**: Phases follow the spec priorities: US1 shell + home (P1), US4 responsive (P2),
US3 components (P3), US2 identity documentation (P4).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story the task belongs to (US1–US4)
- Paths are relative to the repository root. Commands are **Windows PowerShell**. `npm` commands
  run in `frontend\`.

## Ground rules for every task

- Work only in `frontend\` and `docs\architecture\`. **Never modify `backend\`** (FR-016) or
  `.specify\memory\constitution.md`.
- Keep every existing frontend file. The only removal is `frontend\.oxlintrc.json` (T004),
  because it becomes unused when Oxlint is replaced. Unused scaffold assets (`src\assets\*`,
  `public\icons.svg`) stay.
- Lint and format with ESLint + Prettier only. Do not reintroduce Oxlint.
- TypeScript only: no new `.js`/`.jsx` files (constitution II). Function components, English code
  names, Spanish (es-MX) user-visible text (FR-018).
- CSS colors, font sizes, font weights, spacing, radius and shadows MUST use `var(--…)` tokens from
  `frontend\src\styles\tokens.css`. Literal values are allowed only for `0`, `1px` borders, `100%`
  and `transparent`, and for the breakpoint written in media queries (data-model.md, Design Token).
- Prettier style: no semicolons, single quotes, trailing commas, print width 100.
- No runtime dependencies. Dev dependencies only as listed in T002.
- Out of scope: dropdowns, selection controls and tables (spec, Deferred Scope); product
  features; business navigation entries (FR-017).

---

## Phase 1: Setup (Tooling: research R-09, R-10, R-11, R-16)

**Purpose**: Bring the frontend tooling in line with the constitution (ESLint + Prettier, Vitest,
strict TypeScript) before any UI work.

- [X] T001 Record the baseline before any change. From the repository root run `git status --short -- backend/ | Out-File -Encoding utf8 "$env:TEMP\pim-backend-before.txt"` and `git ls-files -o -c --exclude-standard frontend/ | Sort-Object | Out-File -Encoding utf8 "$env:TEMP\pim-frontend-files-before.txt"` (quickstart.md, "Before you start")
- [X] T002 Update dev dependencies in `frontend\package.json` and `frontend\package-lock.json`. In `frontend\`, run in order:
  1. `npm uninstall oxlint`
  2. `npm install -D typescript@~6.0.3`
  3. `npm install -D eslint@^10.14.0 @eslint/js@^10.0.1 typescript-eslint@^8.72.1 eslint-plugin-react-hooks@^7.1.1 eslint-plugin-react-refresh@^0.5.7 globals@^17.14.0 eslint-config-prettier@^10.1.8 prettier@^3.9.10 jiti@^2.7.0`
  4. `npm install -D vitest@^5.1.0 jsdom@^31.0.0 @testing-library/react@^16.3.3 @testing-library/dom@^10.4.2`
  
  Confirm `npx tsc -v` prints `Version 6.0.x` and `npm ls --depth=0` shows no peer-dependency errors
- [X] T003 Set the npm scripts in `frontend\package.json` to exactly: `"dev": "vite"`, `"build": "tsc -b && vite build"`, `"lint": "eslint ."`, `"format": "prettier --write ."`, `"format:check": "prettier --check ."`, `"test": "vitest run"`, `"preview": "vite preview"`
- [X] T004 Remove the Oxlint leftovers.
  1. Delete the config with `Remove-Item frontend\.oxlintrc.json`.
  2. In `frontend\README.md`, replace only the section "Expanding the Oxlint configuration" (heading, text and JSON block) with a short "Comandos" section listing `npm run dev`, `npm run build`, `npm run lint`, `npm run format`, `npm run format:check` and `npm test`.
  3. From the repository root, confirm `git grep -i --untracked -n oxlint -- frontend` prints nothing (it skips the git-ignored `node_modules`).
- [X] T005 [P] Create `frontend\eslint.config.ts` as an ESLint flat config using `defineConfig` and `globalIgnores` from `eslint/config`:
  - `globalIgnores(['dist', 'coverage'])`;
  - one block for `files: ['**/*.{ts,tsx}']` that extends `js.configs.recommended` (`@eslint/js`), `tseslint.configs.recommended` (`typescript-eslint`, no type-aware rules), the flat-config recommended preset of `eslint-plugin-react-hooks` v7 (check the plugin README for the export name) and `reactRefresh.configs.vite`, with `languageOptions.globals: globals.browser`;
  - `eslint-config-prettier` as the last entry
- [X] T006 [P] Create `frontend\.prettierrc.json` with exactly `{ "semi": false, "singleQuote": true, "trailingComma": "all", "printWidth": 100 }` and `frontend\.prettierignore` listing `dist`, `coverage` and `package-lock.json`
- [X] T007 [P] Add `"strict": true` to `compilerOptions` in `frontend\tsconfig.app.json` and `frontend\tsconfig.node.json`. In `frontend\tsconfig.node.json`, set `"include": ["vite.config.ts", "eslint.config.ts"]`
- [X] T008 Configure Vitest in `frontend\vite.config.ts`: import `defineConfig` from `vitest/config` instead of `vite`, keep `plugins: [react()]`, and add `test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'] }`. Create `frontend\src\test\setup.ts` that imports `afterEach` from `vitest` and `cleanup` from `@testing-library/react` and calls `cleanup()` after each test. Tests import `describe`/`it`/`expect`/`vi` from `vitest` explicitly
- [X] T009 Run `npm run format` once (expect small changes in `frontend\src\App.tsx` and `frontend\src\main.tsx`). Then confirm `npm run lint`, `npm run format:check` and `npm run build` finish with zero errors, and that `npx vitest run --passWithNoTests` runs

**Checkpoint**: Tooling complies with constitution II, V and VI. The app still shows the old page.

---

## Phase 2: Foundational (Blocking prerequisites)

**Purpose**: Design tokens, global base styles, document language and icons used by every story.

**⚠️ CRITICAL**: No user story work starts until this phase is complete.

- [X] T010 Create `frontend\src\styles\tokens.css` with one `:root` block and a header comment saying all values are project design choices, not official Liverpool specifications (research R-02–R-04).
  - **Colors**:
    - `--color-primary: #C4007F`, `--color-primary-hover: #A3006C`, `--color-primary-subtle: #FCE6F3`, `--color-text-on-primary: #FFFFFF`;
    - `--color-text: #1F1F24`, `--color-text-secondary: #4A4A55`, `--color-text-muted: #6B6B76`;
    - `--color-surface: #FFFFFF`, `--color-background: #F5F5F7`;
    - `--color-border-control: #8A8A94`, `--color-border-subtle: #E3E3E8`;
    - `--color-success: #176B34`, `--color-success-subtle: #E7F4EC`, `--color-warning: #9A5B00`, `--color-warning-subtle: #FDF3E1`, `--color-error: #C62828`, `--color-error-subtle: #FDECEC`, `--color-info: #1565C0`, `--color-info-subtle: #E8F1FB`;
    - `--color-backdrop: rgba(31, 31, 36, 0.4)`.
  - **Typography**:
    - `--font-family-base: "Segoe UI", system-ui, -apple-system, Roboto, "Helvetica Neue", Arial, sans-serif`;
    - `--text-xs: 0.75rem`, `--text-sm: 0.875rem`, `--text-md: 1rem`, `--text-lg: 1.25rem`, `--text-xl: 1.5rem`;
    - `--weight-regular: 400`, `--weight-semibold: 600`, `--weight-bold: 700`;
    - `--leading-body: 1.5`, `--leading-heading: 1.25`.
  - **Spacing**: `--space-1: 4px`, `--space-2: 8px`, `--space-3: 12px`, `--space-4: 16px`, `--space-5: 24px`, `--space-6: 32px`.
  - **Borders**: `--border-width: 1px`, `--border-focus-width: 2px`.
  - **Radius**: `--radius-sm: 4px`, `--radius-md: 8px`, `--radius-pill: 999px`.
  - **Shadows**: `--shadow-sm: 0 1px 2px rgba(31, 31, 36, 0.08)`, `--shadow-md: 0 4px 12px rgba(31, 31, 36, 0.12)`.
  - **Layout**: `--layout-header-height: 56px`, `--layout-nav-width: 240px`.
  - **Z-index**: `--z-header: 10`, `--z-backdrop: 20`, `--z-drawer: 30`.
- [X] T011 Replace the content of `frontend\src\index.css` (keep the file; `main.tsx` imports it) with global base styles:
  - `@import './styles/tokens.css';` and `color-scheme: light`;
  - `box-sizing: border-box` on all elements;
  - `body`: `margin: 0`, `font-family: var(--font-family-base)`, `font-size: var(--text-sm)`, `line-height: var(--leading-body)`, `color: var(--color-text)`, `background: var(--color-background)`;
  - `h1`–`h3`: `margin: 0`, `line-height: var(--leading-heading)`;
  - `a` color `var(--color-primary)`;
  - global `:focus-visible { outline: var(--border-focus-width) solid var(--color-primary); outline-offset: 2px }`;
  - a `.visually-hidden` screen-reader-only utility class.
  
  Remove the scaffold's purple palette, dark-mode block and 1126 px `#root` column
- [X] T012 [P] Update `frontend\index.html`: `<html lang="es-MX">` and `<title>Plataforma PIM</title>`; keep everything else
- [X] T013 [P] Create `frontend\src\components\ui\Icon.tsx` exporting:
  - `type IconName = 'menu' | 'close' | 'home' | 'components' | 'search' | 'info' | 'warning' | 'error' | 'success'`;
  - `Icon({ name, size = 20, label })` with `size?: 16 | 20 | 24`.
  
  Render an inline `<svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">` with a simple outline drawing per name. Without `label`, set `aria-hidden="true"`; with `label`, set `role="img"` and `aria-label={label}`. No icon library (research R-05)

**Checkpoint**: Tokens, base styles and icons ready; `npm run lint` and `npm run build` pass.

---

## Phase 3: User Story 1 — Application shell and home page (Priority: P1) 🎯 MVP

**Goal**: Header, side navigation and content area shared by all pages, and a home page that
shows the backend connection status in Spanish (FR-008, FR-009, FR-015, FR-018).

**Independent Test**: At `#/` you see header "Plataforma PIM", the side navigation with "Inicio"
active, and the content area. With the backend running, "Conexión con el servidor establecida."
is shown; with it stopped, "No se pudo conectar con el servidor.". "Hello from PIM Backend!"
never appears (quickstart scenarios 1, 3, 4).

### Tests for User Story 1 (write first, confirm they fail)

- [X] T014 [P] [US1] Write `frontend\src\navigation.test.ts`:
  - `getPathFromHash('')`, `getPathFromHash('#/')` and `getPathFromHash('#/desconocido')` all return `'/'` (data-model.md: "Unknown or empty hashes resolve to `/`");
  - `useCurrentPath()` (via `renderHook`) returns `'/'` after `window.location.hash = '#/'` and a `hashchange` event
- [X] T015 [P] [US1] Write `frontend\src\components\ui\Notification.test.tsx`:
  - `variant="error"` has `role="alert"`; `success`, `info` and `warning` have `role="status"`;
  - title and text are shown;
  - with `onDismiss`, the button "Cerrar notificación" calls it on click; without it, no button
- [X] T016 [P] [US1] Write `frontend\src\pages\HomePage.test.tsx`, stubbing `fetch` with `vi.stubGlobal` and restoring it after each test:
  - a pending fetch shows "Verificando conexión con el servidor…";
  - `ok: true` with `text()` returning `'Hello from PIM Backend!'` shows "Conexión con el servidor establecida." and `queryByText(/Hello from PIM Backend/)` is `null`;
  - `ok: false` and a rejected fetch both show "No se pudo conectar con el servidor.";
  - `fetch` is called once with `'http://localhost:8080/api/v1/hello'`
- [X] T017 [P] [US1] Write `frontend\src\components\layout\AppShell.test.tsx`:
  - landmarks: `banner` containing "Plataforma PIM", `navigation` named "Navegación principal", `main` with `id="contenido"` containing the children;
  - the active link has `aria-current="page"`;
  - the first link is "Saltar al contenido" with `href="#contenido"`;
  - no text matches `/carrito|precio|comprar/i` (FR-006)

### Implementation for User Story 1

- [X] T018 [P] [US1] Create `frontend\src\navigation.ts` exporting:
  - `type NavigationEntry = { path: string; label: string; icon: IconName }`;
  - `NAVIGATION_ENTRIES` with only `{ path: '/', label: 'Inicio', icon: 'home' }` (Componentes is added in T040);
  - `getPathFromHash(hash)`, which returns the matching entry path or `'/'`;
  - `useCurrentPath()`, which reads `window.location.hash` and updates on `hashchange` with cleanup.
  
  Rules: "Hash path starting with `/`, unique"; "Entries for PIM business sections MUST NOT be added by this feature (FR-017)"
- [X] T019 [P] [US1] Create `frontend\src\components\ui\Notification.tsx` + `Notification.module.css` (contracts, Notification):
  - banner with `--radius-md`, padding `--space-3` `--space-4`, background `--color-<variant>-subtle`, left border of width `--space-1` in `--color-<variant>`;
  - the variant `Icon`, an optional bold title, the children;
  - `role="alert"` for `error`, else `role="status"`;
  - optional close button (`Icon name="close"`, `aria-label="Cerrar notificación"`)
- [X] T020 [P] [US1] Create `frontend\src\components\ui\PageHeader.tsx` + `PageHeader.module.css`:
  - props `{ title; description?; actions? }`;
  - the single `<h1>` at `--text-xl` `--weight-bold`, description in `--color-text-secondary`;
  - actions on the right, wrapping below on narrow widths;
  - bottom margin `--space-5`
- [X] T021 [P] [US1] Create `frontend\src\components\ui\Card.tsx` + `Card.module.css`:
  - props `{ title?; actions?; children }`;
  - `<section>` with `--color-surface`, `--border-width` solid `--color-border-subtle`, `--radius-md`, `--shadow-sm`, padding `--space-5`;
  - an optional `title` as `<h2>` at `--text-lg` `--weight-semibold`, labelling the section with `aria-labelledby`
- [X] T022 [US1] Create `frontend\src\components\ui\index.ts` exporting `Icon` (+ type `IconName`), `Notification`, `PageHeader` and `Card`
- [X] T023 [US1] Replace the content of `frontend\src\App.css` (keep the file) with the desktop app frame, using `app-` class names:
  - `.app-shell`: grid with header row `--layout-header-height`; at 1024px and wider, columns `--layout-nav-width minmax(0, 1fr)`; `min-height: 100vh`;
  - `.app-header`: `--color-primary` background, `--color-text-on-primary` text, `--shadow-md`, `z-index: var(--z-header)`, title at `--text-lg` `--weight-semibold`;
  - `.app-nav`: `--color-surface` background, right border `--color-border-subtle`;
  - `.app-nav__link`: padding `--space-3` `--space-4`, `--color-text-secondary`, icon + label;
  - `.app-nav__link[aria-current="page"]`: `--color-primary-subtle` background, `--color-primary` text, `--weight-semibold`, left marker of width `--space-1`;
  - `.app-main`: padding `--space-5`, `min-width: 0`, `overflow-wrap: anywhere`;
  - `.app-skip-link`: visible only on focus.
  
  Remove the scaffold rules. Tablet rules come in T031
- [X] T024 [P] [US1] Create `frontend\src\components\layout\AppHeader.tsx`: `<header className="app-header">` with "Plataforma PIM" and an empty logo slot (FR-007, A-003); no search, user menu, cart or promotions (FR-006, A-005). The menu button comes in T032
- [X] T025 [P] [US1] Create `frontend\src\components\layout\SideNavigation.tsx` with props `{ id; entries; activePath }`. Render `<nav className="app-nav" aria-label="Navegación principal">` with one link per entry: `href={'#' + entry.path}`, `Icon` + label, and `aria-current="page"` when `path === activePath`. Drawer props come in T033
- [X] T026 [US1] Create `frontend\src\components\layout\AppShell.tsx` with props `{ entries; activePath; children }`. Inside `<div className="app-shell">`, render:
  1. `<a className="app-skip-link" href="#contenido">Saltar al contenido</a>`;
  2. `AppHeader`;
  3. `SideNavigation id="navegacion-principal"`;
  4. `<main id="contenido" className="app-main" tabIndex={-1}>`
- [X] T027 [US1] Create `frontend\src\pages\HomePage.tsx`, moving the connection check out of `App.tsx`:
  - `useEffect` calls `fetch('http://localhost:8080/api/v1/hello')` once (URL unchanged, R-15), with an `ignore` flag in the cleanup;
  - status goes `checking` → `connected` if `response.ok`, otherwise (non-OK status or rejected promise) `unavailable`;
  - never read or display the response body (R-13).
  
  Render `PageHeader title="Inicio"` and a `Card` with `Notification`:
  - `info` "Verificando conexión con el servidor…";
  - `success` "Conexión con el servidor establecida.";
  - `error` "No se pudo conectar con el servidor."
- [X] T028 [US1] Replace `frontend\src\App.tsx`:
  - import `./App.css`;
  - get `path` from `useCurrentPath()`;
  - render `<AppShell entries={NAVIGATION_ENTRIES} activePath={path}>` with a plain `switch (path)` whose default is `<HomePage />`;
  - remove the old inline fetch.
  
  `frontend\src\main.tsx` stays unchanged
- [X] T029 [US1] Run `npm test`, `npm run lint`, `npm run format:check` and `npm run build`; all must pass. Check quickstart scenarios 1, 3 and 4: start the backend in a second PowerShell window from `backend\` with `.\gradlew.bat bootRun` for scenario 3, stop it with Ctrl+C for scenario 4, and edit no backend file

**Checkpoint (MVP)**: Shell and home page work at desktop width, with Spanish connection
messages.

---

## Phase 4: User Story 4 — Desktop and tablet layout (Priority: P2)

**Goal**: Below 1024 px the navigation becomes a drawer opened from the header. Nothing overflows
horizontally from 768 px up (FR-013, FR-014, research R-07).

**Independent Test**: At 1440 and 1024 px the navigation is always visible. At 1023 and 768 px a
menu button opens and closes the drawer (link, Escape, close button, backdrop), and no page
scrolls horizontally (quickstart scenarios 5, 6).

### Tests for User Story 4 (write first, confirm they fail)

- [ ] T030 [US4] Extend `frontend\src\components\layout\AppShell.test.tsx`. jsdom ignores media queries, so assert state and attributes:
  - the button "Abrir menú de navegación" has `aria-expanded="false"` and `aria-controls="navegacion-principal"`;
  - clicking it sets `aria-expanded="true"` and the nav gets `data-open="true"`;
  - each of these closes the drawer: `fireEvent.keyDown(document, { key: 'Escape' })`, a nav link click, a backdrop click, and the "Cerrar menú de navegación" button;
  - after opening, focus is inside the nav; after closing, the menu button has focus

### Implementation for User Story 4

- [ ] T031 [P] [US4] Add to `frontend\src\App.css` a `@media (max-width: 1023.98px)` block:
  - `.app-shell` becomes a single column;
  - `.app-header__menu-button` is shown (hidden at 1024px and wider), `transparent` background, `--color-text-on-primary`, at least 44 px square using `--space-*` tokens;
  - `.app-nav` becomes `position: fixed`, top `--layout-header-height`, left/bottom `0`, width `--layout-nav-width`, `--shadow-md`, `z-index: var(--z-drawer)`, `transform: translateX(-100%)` and `visibility: hidden`, so closed links are not focusable;
  - `.app-nav[data-open="true"]` gets `transform: none` and `visibility: visible`;
  - `.app-backdrop`: fixed below the header, `--color-backdrop`, `z-index: var(--z-backdrop)`;
  - `.app-nav__close` is shown only in this range.
  
  Make sure `html` and `body` never scroll horizontally (FR-014)
- [ ] T032 [P] [US4] Update `frontend\src\components\layout\AppHeader.tsx` to the contract props `{ onMenuClick; isMenuOpen; menuId; menuButtonRef }`. Add `<button className="app-header__menu-button" type="button">` with `Icon name="menu"`, `aria-label="Abrir menú de navegación"`, `aria-expanded={isMenuOpen}`, `aria-controls={menuId}` and `ref={menuButtonRef}`
- [ ] T033 [P] [US4] Update `frontend\src\components\layout\SideNavigation.tsx` with the props `isOpen` and `onClose`:
  - set `data-open={isOpen}` on the `<nav>`;
  - add a close button (`Icon name="close"`, `aria-label="Cerrar menú de navegación"`, class `app-nav__close`);
  - call `onClose` on any link click;
  - when `isOpen` becomes true, focus the first link
- [ ] T034 [US4] Update `frontend\src\components\layout\AppShell.tsx`:
  - hold `isMenuOpen` and a `menuButtonRef`, and wire them to `AppHeader` (toggle) and `SideNavigation` (`isOpen`, `onClose`);
  - while open, listen for Escape on `document` (with cleanup) and render `<div className="app-backdrop" onClick={close} />`;
  - on close, focus the menu button.
  
  Then run `npm test`, `npm run lint`, `npm run format:check` and `npm run build`, and check quickstart scenarios 5 and 6 at 1440, 1024, 1023 and 768 px and 1024×768

**Checkpoint**: Shell, home page and responsive layout complete.

---

## Phase 5: User Story 3 — Small set of reusable UI components (Priority: P3)

**Goal**: Button, TextField (with search variant) and StatusBadge join the components from US1.
All 5 FR-010 groups are shown on `#/componentes` (FR-010, FR-011, FR-012).

**Independent Test**: At `#/componentes` the 5 groups appear with normal, hover, focus, disabled
and error states; keyboard focus is visible (quickstart scenarios 7, 8).

### Tests for User Story 3 (write first, confirm they fail)

- [ ] T035 [P] [US3] Write `frontend\src\components\ui\Components.test.tsx`:
  - **Button**:
    - defaults to `type="button"`;
    - calls `onClick` when enabled;
    - when `disabled`, the native button is disabled and does not call `onClick`.
  - **TextField**:
    - found by its label (`getByLabelText`), including when `hideLabel` is set;
    - `type="search"` renders an input of type `search`;
    - with `error="Campo obligatorio"`: `aria-invalid="true"` and an `aria-describedby` element containing the message;
    - `hint` is referenced by `aria-describedby`;
    - `disabled` disables the input.
- [ ] T036 [P] [US3] Write `frontend\src\pages\ComponentGalleryPage.test.tsx` (smoke test):
  - heading "Componentes";
  - section headings "Botones", "Campos de texto", "Tarjetas", "Títulos", "Estados y notificaciones";
  - at least one disabled button and one field with an error message

### Implementation for User Story 3

- [ ] T037 [P] [US3] Create `frontend\src\components\ui\Button.tsx` + `Button.module.css` (contracts, Button):
  - default `type="button"`, `variant` `'primary' | 'secondary'`, optional leading `icon`;
  - primary: `--color-primary` background, `--color-text-on-primary`, hover `--color-primary-hover`;
  - secondary: `--color-surface` background, `--border-width` solid `--color-primary`, `--color-primary` text, hover `--color-primary-subtle`;
  - `--radius-sm`, padding `--space-2` `--space-4`, `--text-sm` `--weight-semibold`;
  - disabled: `opacity: 0.5`, `cursor: not-allowed`, no hover change
- [ ] T038 [P] [US3] Create `frontend\src\components\ui\TextField.tsx` + `TextField.module.css` (contracts, TextField):
  - **Label**: `<label>` linked by `id` (`--text-sm` `--weight-semibold` `--color-text-secondary`); `hideLabel` applies `visually-hidden`.
  - **Input**: `--border-width` solid `--color-border-control`, `--radius-sm`, padding `--space-2` `--space-3`, `--color-surface`; disabled background `--color-background`.
  - **Search variant**: `type="search"` adds a leading `Icon name="search"`.
  - **Hint**: `--text-xs` `--color-text-muted`.
  - **Error**: message at `--text-xs` in `--color-error` with `Icon name="error" size={16}`; error border `--color-error`; `aria-invalid="true"`; `aria-describedby` lists the hint and error ids.
- [ ] T039 [P] [US3] Create `frontend\src\components\ui\StatusBadge.tsx` + `StatusBadge.module.css`:
  - pill (`--radius-pill`), padding `--space-1` `--space-2`, `--text-xs` `--weight-semibold`;
  - `--color-<variant>-subtle` background and `--color-<variant>` text with the variant `Icon` at size 16;
  - `neutral` uses `--color-background` / `--color-text-secondary` and no icon;
  - the text always names the status
- [ ] T040 [US3] Add `{ path: '/componentes', label: 'Componentes', icon: 'components' }` to `NAVIGATION_ENTRIES` in `frontend\src\navigation.ts`, and add `getPathFromHash('#/componentes')` → `'/componentes'` to `frontend\src\navigation.test.ts`. Export `Button`, `TextField` and `StatusBadge` from `frontend\src\components\ui\index.ts`
- [ ] T041 [US3] Create `frontend\src\pages\ComponentGalleryPage.tsx`: `PageHeader title="Componentes"` with the description "Referencia visual de los componentes reutilizables de la plataforma." and one `Card` per group, with Spanish sample content:
  1. **"Botones"**: primary, secondary, primary with icon, and disabled primary and secondary.
  2. **"Campos de texto"**:
     - a field with a hint;
     - a field with the error "Campo obligatorio";
     - a disabled field;
     - a search field with `hideLabel` and label "Buscar".
  3. **"Tarjetas"**: a Card with a title and actions, and a Card without a title.
  4. **"Títulos"**: a sample page title and section heading, plus one long title that wraps and one long unbroken string (to show wrapping, US4-AS3).
  5. **"Estados y notificaciones"**: all 5 StatusBadge variants, and all 4 Notification variants, one with `onDismiss` that hides it.
  
  No product, price or shopping terms (FR-006, FR-017)
- [ ] T042 [US3] Add `case '/componentes': return <ComponentGalleryPage />` to the `switch` in `frontend\src\App.tsx`. Run `npm test`, `npm run lint`, `npm run format:check` and `npm run build`, and check quickstart scenarios 2, 7 and 8

**Checkpoint**: All components available and visible on `#/componentes`. Navigation between both
pages works, including Back.

---

## Phase 6: User Story 2 — Visual identity documentation (Priority: P4)

**Goal**: The identity is documented, with every value labeled as a design choice, and contrast is
verified (FR-001–FR-007, SC-003, SC-006).

**Independent Test**: `docs\architecture\design-system.md` lists every token from `tokens.css`
labeled "Design choice", and the contrast pairs pass AA (quickstart scenarios 10, 12, 13).

- [ ] T043 [US2] Create `docs\architecture\design-system.md`:
  - a purpose statement: Liverpool (the Mexican department store) is a visual reference only; no official guidelines were available (spec A-001, OQ-002); every value is a project design choice;
  - one table per token category (Token | Value | Use | Origin) with Origin `Design choice` on every row and values exactly as in `frontend\src\styles\tokens.css`;
  - the research R-02 contrast table, re-checked for every text/background pair the components actually use, with any extra pair added together with its ratio (≥4.5:1 text, ≥3:1 control borders);
  - icon rules (R-05) and layout rules (header 56 px, navigation 240 px, drawer below 1024 px);
  - the rules: no Liverpool Football Club branding, no shopping elements, and the logo only from an official asset, never recreated (FR-006, FR-007);
  - "How to change the identity": edit `tokens.css`, update this file, and mark a value `Official — <source>` only with a verified official reference (FR-005)

**Checkpoint**: All four user stories complete.

---

## Phase 7: Polish & Validation

- [ ] T044 Check the scope limits against the T001 baseline, from the repository root:
  - `Compare-Object (Get-Content "$env:TEMP\pim-backend-before.txt") (git status --short -- backend/)` prints nothing (FR-016);
  - `Compare-Object (Get-Content "$env:TEMP\pim-frontend-files-before.txt") (git ls-files -o -c --exclude-standard frontend/ | Sort-Object)` shows `frontend/.oxlintrc.json` as the only `<=` line;
  - `git diff -- .specify/memory/constitution.md` shows no change from this feature;
  - in `frontend\`, `npm ls --depth=0` lists `typescript` 6.0.x, no `oxlint`, and only `react` and `react-dom` as runtime dependencies
- [ ] T045 Audit identity values (SC-002) with the scenario 11 commands in quickstart.md (`Get-ChildItem … | Select-String …` over `frontend\src`). Replace every hit with a token, except the allowed literals (`0`, `1px`, `100%`, `transparent`, `1024px`/`1023.98px`). Then confirm the edge cases (quickstart scenario 9: long text wraps, no horizontal scroll at 768 px, 200% zoom usable) and fix any issue in `frontend\src\App.css` or the relevant `.module.css`
- [ ] T046 Run the full validation in `specs\001-liverpool-look-and-feel\quickstart.md`: the automated checks, the tooling-file checks and manual scenarios 1–14. Report the results directly in the implementation summary: pass or fail per check and per scenario, with the error output for any failure

**After implementation (not a task)**: the SC-007 review by at least 3 PIM stakeholders (quickstart
scenarios 1, 7 and 10), and the follow-up feature for dropdowns, selection controls and tables.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (1)**: start immediately; T001 before any change.
- **Foundational (2)**: after Setup; blocks all stories.
- **US1 (3)**: after Foundational. MVP.
- **US4 (4)**: after US1 (it extends the shell and `App.css`).
- **US3 (5)**: after US1 (reuses Card, PageHeader, Notification, routing). Independent of US4.
- **US2 (6)**: after US3, so the documentation and contrast check cover all components.
- **Polish (7)**: last.

### Task dependencies

- T002 → T003–T008 → T009.
- T010 → T011. T013 is needed by every component that shows an icon.
- US1: T014–T017 first. Then T018–T021 in parallel → T022. T023–T025 → T026. T018 + T022 + T026
  → T027 → T028 → T029.
- US4: T030 first. Then T031–T033 in parallel → T034.
- US3: T035–T036 first. Then T037–T039 in parallel → T040 → T041 → T042.

### Parallel opportunities

- Setup: T005, T006 and T007 after T002.
- Foundational: T012 and T013 next to T010–T011.
- US1: tests T014–T017; components T018–T021; layout T024–T025.
- US4: T031–T033.
- US3: tests T035–T036; components T037–T039.

## Parallel Example: User Story 1

```text
Task: "T014 Write frontend\src\navigation.test.ts"
Task: "T015 Write frontend\src\components\ui\Notification.test.tsx"
Task: "T016 Write frontend\src\pages\HomePage.test.tsx"
Task: "T017 Write frontend\src\components\layout\AppShell.test.tsx"
```

---

## Implementation Strategy

1. **MVP**: Setup → Foundational → US1. Stop and validate quickstart scenarios 1, 3 and 4.
2. **Responsive**: US4. Validate scenarios 5 and 6.
3. **Components**: US3. Validate scenarios 2, 7 and 8.
4. **Documentation**: US2, then Polish with the full quickstart run, reported in the
   implementation summary.

Each step leaves lint, format check, tests and build green (constitution X). Commits only when the
user asks.

## Notes

- Requirement traceability:

  | Requirements | Tasks |
  |---|---|
  | FR-001–FR-004 | T010, T011, T013 |
  | FR-005 | T043, T045 |
  | FR-006, FR-007 | T017, T024, T041, T043 |
  | FR-008, FR-009 | T017, T023–T026 |
  | FR-010–FR-012 | T019–T021, T035–T041 |
  | FR-013, FR-014 | T030–T034, T045 |
  | FR-015 | T016, T027 |
  | FR-016 | T001, T044 |
  | FR-017 | T018, T040, T041 |
  | FR-018 | T012, T016, T027 |
  | SC-001–SC-006, SC-008 | T029, T034, T042, T043–T046 |
  | SC-007 | after implementation |

- [P] tasks touch different files and do not depend on unfinished tasks.
