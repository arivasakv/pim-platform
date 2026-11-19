# UI Contracts: Layout and Reusable Components

**Feature**: [spec.md](../spec.md) | **Date**: 2026-11-08

These are the public props contracts of the components in `frontend/src/components/ui/` (all
exported from `components/ui/index.ts`, including `Icon`) and `frontend/src/components/layout/`.
They are what future PIM screens build on. Props are kept to what the spec needs; new options are
added only when a later feature needs them. Dropdowns, selection controls and tables are deferred
(spec, Deferred Scope).

All components:

- are function components that do not accept `className` or `style`, so the identity cannot be
  overridden ad hoc;
- style themselves only with design tokens ([data-model.md](../data-model.md#design-token));
- show a visible focus ring (`:focus-visible`) on every interactive element (FR-011).

No REST endpoints are added or changed, so there is no OpenAPI contract (constitution VII).

---

## Icon

```ts
type IconName = 'menu' | 'close' | 'home' | 'components' | 'search'
  | 'info' | 'warning' | 'error' | 'success'
  | 'chevron-right' | 'chevron-down' | 'folder' | 'folder-open' | 'tag'   // feature 010 (taxonomy tree)
  | 'list' | 'tags' | 'folder-tree' | 'network';                         // navigation icons, FR-020 (2026-11-19)
interface IconProps { name: IconName; size?: 16 | 20 | 24; label?: string }
```
- Default size 20. Without `label`: `aria-hidden="true"`. With `label`: `role="img"` +
  `aria-label`.
- Outline, 1.75 stroke, `currentColor` (research R-05).

## Layout

The layout components are styled by the existing `src/App.css` (app frame), not by CSS Modules.

### AppShell
```ts
interface AppShellProps {
  entries: NavigationEntry[];   // see data-model
  sections?: NavigationSection[]; // see data-model; default [] (FR-019, 2026-11-19)
  activePath: string;
  children: ReactNode;          // current page
}
```
- Renders a "Saltar al contenido" skip link, then AppHeader, SideNavigation and
  `<main id="contenido">` (FR-008).
- Owns the drawer open/closed state used below 1024 px.

### AppHeader
```ts
interface AppHeaderProps {
  onMenuClick: () => void; isMenuOpen: boolean; menuId: string;
  menuButtonRef: Ref<HTMLButtonElement>;   // lets AppShell return focus to the button
}
```
- `<header>` on `--color-primary` with white text "Plataforma PIM"; logo slot empty (FR-007, A-003).
- Menu button (icon `menu`, `aria-label="Abrir menú de navegación"`, `aria-expanded`,
  `aria-controls={menuId}`), visible only below 1024 px.
- No search, cart, user or promotional elements (FR-006, A-005).

### SideNavigation
```ts
interface SideNavigationProps {
  id: string; entries: NavigationEntry[]; activePath: string;
  sections: NavigationSection[]; // display order of the section groups (FR-019)
  isOpen: boolean;              // only meaningful below 1024 px
  onClose: () => void;
}
```
- Entries are grouped by section (data-model, Navigation Section rules). Each group is a `<ul>`
  labelled by its section name through `aria-labelledby`; the name is a muted, `--text-xs`,
  semibold, uppercase label with `--tracking-label` letter spacing. Consecutive groups are separated
  by `--space-3` spacing and a `--color-border-subtle` divider (FR-019, added 2026-11-19).
- `<nav aria-label="Navegación principal" data-open={isOpen}>` with links `href="#<path>"`. The
  active link has `aria-current="page"`, a `--color-primary-subtle` background and a primary left
  marker (FR-009).
- Below 1024 px: off-canvas drawer with a backdrop. It closes on link selection, Escape, the close
  button (`aria-label="Cerrar menú de navegación"`) or a backdrop click. Focus moves to the first
  link when it opens and returns to the menu button when it closes (research R-07).

## Actions

### Button
```ts
interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className' | 'style'> {
  variant?: 'primary' | 'secondary';   // default 'primary'
  icon?: IconName;                     // leading icon
  children: ReactNode;
}
```
- Defaults to `type="button"`. Primary: filled `--color-primary`, white text. Secondary: white with
  primary border and text. States: hover, focus-visible, disabled.

## Inputs

### TextField
```ts
interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className' | 'style' | 'type'> {
  id: string; label: string;
  type?: 'text' | 'email' | 'number' | 'password' | 'search';   // default 'text'
  hideLabel?: boolean;          // label stays for screen readers (used by search boxes)
  hint?: string; error?: string;
}
```
- Visible `<label>` linked by `id` (visually hidden when `hideLabel`).
- `type="search"` adds a leading `search` icon.
- With `error`: `aria-invalid="true"`, error message with the `error` icon in `--color-error`.
- `hint` and `error` are linked through `aria-describedby` (FR-011).

## Containers and titles

### Card
```ts
interface CardProps { title?: string; actions?: ReactNode; children: ReactNode }
```
- `<section>` on `--color-surface`, `--radius-md`, `--shadow-sm`. When `title` is set it is
  rendered as an `<h2>` section heading and labels the section (`aria-labelledby`).

### PageHeader
```ts
interface PageHeaderProps { title: string; description?: string; actions?: ReactNode }
```
- The page's single `<h1>`. Actions align right and wrap below on narrow widths.

## Feedback

### StatusBadge
```ts
type StatusVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral';
interface StatusBadgeProps { variant: StatusVariant; children: ReactNode }
```
- Pill shape, subtle background, colored text and a variant icon (none for `neutral`). The text
  always states the status, so it never relies on color alone.

### Notification
```ts
interface NotificationProps {
  variant: Exclude<StatusVariant, 'neutral'>;
  title?: string; children: ReactNode;
  onDismiss?: () => void;              // shows close button "Cerrar notificación"
}
```
- Inline banner, left border in the variant color, variant icon.
- `role="alert"` for `error`, otherwise `role="status"`.

## Pages

| Route | Component | Contents |
|---|---|---|
| `#/` | HomePage | PageHeader "Inicio" and a Card with the backend connection Notification in Spanish; the backend response text is not shown ([data-model](../data-model.md#backend-connection-status-home-page-view-state)) |
| `#/componentes` | ComponentGalleryPage | PageHeader "Componentes" and one Card per FR-010 group (5), each showing normal, hover, focus, disabled and error states with sample Spanish content |
