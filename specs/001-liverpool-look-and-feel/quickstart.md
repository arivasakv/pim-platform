# Quickstart & Validation: Liverpool-Inspired Frontend Look & Feel

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-08

All commands are for **Windows PowerShell**. Paths use `\`.

## Prerequisites

- Node.js `^22.13.0 || ^24.0.0 || >=26.0.0` and npm. This is the range supported by Vite 8.3.6,
  ESLint 10 and Vitest 5 together; Node 20 and 25 are not supported (research, Node.js
  requirement).
- Java 21, only to start the existing backend for scenario 3 (the backend is not modified).
- Package versions: see plan Technical Context (TypeScript `~6.0.3`; ESLint, Prettier and test
  tooling ranges). The lockfile records the exact installed versions.
- Backend URL configuration (spec TD-004, research R-15). From `frontend\`, create your local
  environment file from the tracked template:

  ```powershell
  Copy-Item .env.example .env   # sets VITE_API_BASE_URL=http://localhost:8080; .env is git-ignored
  ```

  Restart `npm run dev` after creating or changing `.env`. Without it, the home page shows "No se
  pudo conectar con el servidor." and does not call the backend.

## Before you start: baseline

From the repository root, save the current state so you can prove later that the backend did not
change and that `frontend\.oxlintrc.json` is the only frontend file removed:

```powershell
git rev-parse HEAD | Out-File -Encoding utf8 "$env:TEMP\pim-baseline-commit.txt"   # backend baseline
git ls-files -o -c --exclude-standard frontend/ | Sort-Object | Out-File -Encoding utf8 "$env:TEMP\pim-frontend-files-before.txt"
```

The backend must be committed and clean at this point (`git status --short -- backend/` prints
nothing). For this feature the baseline commit is `29f5e27`.

## Automated checks

From `frontend\`:

```powershell
npm install
npm run lint          # ESLint: 0 errors, 0 warnings           (constitution V)
npm run format:check  # Prettier: all files formatted          (constitution V)
npm test              # Vitest: all tests pass                 (constitution VI)
npm run build         # tsc -b + vite build: 0 errors          (SC-008, acceptance criterion 8)
npx tsc -v            # Version 6.0.x
npm ls --depth=0      # typescript 6.0.x; no oxlint; runtime deps only react and react-dom
```

If `format:check` fails, run `npm run format` and review the changes.

Tooling files (from `frontend\`):

```powershell
Test-Path eslint.config.ts, .prettierrc.json, .prettierignore   # True, True, True
Test-Path .oxlintrc.json                                        # False
Select-String -Path package.json -Pattern oxlint                # no output
```

Scope limits (from the repository root, after implementation):

```powershell
# Backend untouched (FR-016): both commands print nothing
git status --short -- backend/
git diff --stat (Get-Content "$env:TEMP\pim-baseline-commit.txt") -- backend/   # or: git diff --stat 29f5e27 -- backend/

# Frontend files: the only "<=" line is frontend/.oxlintrc.json; "=>" lines are new files
Compare-Object (Get-Content "$env:TEMP\pim-frontend-files-before.txt") (git ls-files -o -c --exclude-standard frontend/ | Sort-Object)
```

`git status` shows uncommitted backend changes; `git diff --stat <baseline>` also catches backend
changes that were committed after the baseline.

## Manual validation

Start the frontend with `npm run dev` (from `frontend\`) and open the URL Vite prints.

| # | Scenario | Steps | Expected | Covers |
|---|---|---|---|---|
| 1 | Shell present | Open `#/` | Pink header "Plataforma PIM", side navigation (Inicio, Componentes), content area | US1-AS1, FR-008 |
| 2 | Navigation | Click "Componentes", then "Inicio"; use the browser Back button | Only the content changes; active entry highlighted; Back returns to the previous page | US1-AS2, FR-009 |
| 3 | Backend up | With `frontend\.env` created (Prerequisites), in a second PowerShell window, from `backend\`, run `.\gradlew.bat bootRun`; reload `#/` | "Verificando conexión con el servidor…" briefly, then "Conexión con el servidor establecida." The English text "Hello from PIM Backend!" appears **nowhere** on the page | US1-AS3, FR-015, FR-018 |
| 4 | Backend down | Stop the backend (Ctrl+C), reload `#/` | Shell renders; error notification "No se pudo conectar con el servidor." | US1-AS4, FR-015 |
| 5 | Desktop widths | Browser responsive mode at 1440 and 1024 px | Navigation always visible; no horizontal page scrollbar | US4-AS1, SC-005 |
| 6 | Tablet widths | 1023 and 768 px (portrait), 1024×768 (landscape) | Below 1024 px: menu button in header; drawer opens and closes (link, Escape, close button, backdrop); no horizontal page scrollbar | US4-AS2, FR-013, FR-014 |
| 7 | Components | Open `#/componentes` | The 5 FR-010 groups (buttons, text/search inputs, cards, page titles, status badges and notifications) with normal, hover, focus, disabled and error states | US3, SC-004 |
| 8 | Keyboard | Press Tab from the top of the page | Skip link first; visible focus ring on every interactive element; drawer and controls work with the keyboard only | US3-AS2, FR-009 |
| 9 | Edge cases | Long title and long unbroken text samples on `#/componentes`; browser zoom 200%; browser responsive mode at **375 px** wide (phone, not a supported target) on `#/` and `#/componentes` | Text wraps without breaking the layout; no horizontal page scroll; content and navigation still reachable. At 375 px: no horizontal page scrollbar, the menu button opens the drawer, and no phone-specific layout is expected | US4-AS3, spec edge cases (incl. "narrower than 768 px") |
| 10 | Not a shop | Review both pages | No cart, prices, banners, carousels or checkout buttons; no football club branding; no logo recreation | US2-AS3, FR-006, FR-007 |
| 11 | Identity values | Search the CSS files for literal colors and px values (command below) | No hits except the allowed literals | US2-AS1, SC-002 |
| 12 | Design choices documented | Open `docs\architecture\design-system.md` | Every color and typography value labeled "Design choice"; none labeled official | US2-AS2, FR-005, SC-003 |
| 13 | Contrast | Check the color pairs in research R-02 with a contrast checker or browser devtools | All ≥4.5:1 for text, ≥3:1 for control borders | SC-006 |
| 14 | Language | Review all visible text and `<html lang>` | Spanish (es-MX); `lang="es-MX"` | FR-018 |

Scenario 11 command (from `frontend\`). Hits are allowed only for `0`, `1px`, `100%`,
`transparent` and the `1024px`/`1023.98px` breakpoints:

```powershell
Get-ChildItem src -Recurse -Include *.css -Exclude tokens.css | Select-String -Pattern '#[0-9a-fA-F]{3,8}\b','rgba?\(','\d+px'
Get-ChildItem src -Recurse -Include *.tsx | Select-String -Pattern 'style='
```

## After implementation (not part of the implementation tasks)

- **SC-007 stakeholder review**: at least 3 PIM stakeholders review scenarios 1, 7 and 10 and
  confirm the interface reads as an internal business tool related to Liverpool, not a shop.
- **Deferred scope**: dropdowns, selection controls and tables in a follow-up feature (spec,
  Deferred Scope).
