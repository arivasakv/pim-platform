# PIM Design System

**Feature**: [001-liverpool-look-and-feel](../../specs/001-liverpool-look-and-feel/spec.md) ·
**Source of the values**: [`frontend/src/styles/tokens.css`](../../frontend/src/styles/tokens.css) ·
**Decisions**: [research.md](../../specs/001-liverpool-look-and-feel/research.md) R-02 to R-08

## Purpose

This document describes the visual identity of the PIM frontend: colors, typography, spacing,
borders, radius, shadows, layout, icons and the rules that keep the interface consistent.

- The identity is **inspired by Liverpool, the Mexican department store**. Liverpool is a visual
  reference only. The application is an internal Product Information Management tool, not an
  online shop.
- **No official Liverpool brand guidelines, fonts or logo files were available** when this
  identity was defined (spec A-001, open question OQ-002).
- **Every value below is a project design choice.** None of them is an official Liverpool
  specification. The Origin column states this for each value (FR-005).

The components are in `frontend/src/components/ui` (each with its own tests) and are used by the
application pages. The former **Componentes** reference page was removed on 2026-11-11.

## Design tokens

All values are CSS custom properties defined once in `frontend/src/styles/tokens.css`. Components
use only `var(--…)` tokens for colors, font sizes, font weights, spacing, radius and shadows.
Literal values are allowed only for `0`, `1px` borders, `100%`, `transparent` and the breakpoint
in media queries.

### Color

| Token | Value | Use | Origin |
|---|---|---|---|
| `--color-primary` | `#c4007f` | Header background, primary buttons, active navigation marker, links, focus outline | Design choice |
| `--color-primary-hover` | `#a3006c` | Primary button hover | Design choice |
| `--color-primary-subtle` | `#fce6f3` | Active navigation background, secondary button hover | Design choice |
| `--color-text-on-primary` | `#ffffff` | Text and icons on the primary color | Design choice |
| `--color-text` | `#1f1f24` | Body text, headings | Design choice |
| `--color-text-secondary` | `#4a4a55` | Labels, secondary text, navigation links | Design choice |
| `--color-text-muted` | `#6b6b76` | Hints, placeholders, search icon | Design choice |
| `--color-surface` | `#ffffff` | Cards, navigation, inputs, secondary buttons | Design choice |
| `--color-background` | `#f5f5f7` | Page background, disabled inputs, neutral badge | Design choice |
| `--color-border-control` | `#8a8a94` | Input borders | Design choice |
| `--color-border-subtle` | `#e3e3e8` | Card and navigation borders (decorative) | Design choice |
| `--color-success` / `--color-success-subtle` | `#176b34` / `#e7f4ec` | Success badges and notifications | Design choice |
| `--color-warning` / `--color-warning-subtle` | `#9a5b00` / `#fdf3e1` | Warning badges and notifications | Design choice |
| `--color-error` / `--color-error-subtle` | `#c62828` / `#fdecec` | Errors: badges, notifications, field errors | Design choice |
| `--color-info` / `--color-info-subtle` | `#1565c0` / `#e8f1fb` | Information badges and notifications | Design choice |
| `--color-backdrop` | `rgba(31, 31, 36, 0.4)` | Backdrop behind the tablet navigation drawer | Design choice |

The primary pink is deliberately a little deeper than the pink commonly associated with Liverpool
online (around `#e10098`). That shade gives only 4.51:1 contrast with white text, too close to the
WCAG AA limit. Pink is used only as an accent (header, primary actions, active state) so screens
stay neutral and business-like.

### Typography

| Token | Value | Use | Origin |
|---|---|---|---|
| `--font-family-base` | `'Segoe UI', system-ui, -apple-system, Roboto, 'Helvetica Neue', Arial, sans-serif` | All text (system fonts, no downloads) | Design choice |
| `--text-xs` | `0.75rem` (12 px) | Hints, field errors, badges | Design choice |
| `--text-sm` | `0.875rem` (14 px) | Body text, labels, buttons, inputs | Design choice |
| `--text-md` | `1rem` (16 px) | Reserved for larger body text | Design choice |
| `--text-lg` | `1.25rem` (20 px) | Header title, section (card) headings | Design choice |
| `--text-xl` | `1.5rem` (24 px) | Page title | Design choice |
| `--weight-regular` | `400` | Body text | Design choice |
| `--weight-semibold` | `600` | Labels, buttons, headings, active navigation | Design choice |
| `--weight-bold` | `700` | Page title | Design choice |
| `--leading-body` | `1.5` | Body text line height | Design choice |
| `--leading-heading` | `1.25` | Heading line height | Design choice |

Liverpool's official typefaces are unknown and not licensed to this project, so no official font
is claimed or imitated.

### Spacing

| Token | Value | Use | Origin |
|---|---|---|---|
| `--space-1` | `4px` | Small gaps, badge padding, active navigation marker width | Design choice |
| `--space-2` | `8px` | Button and input vertical padding, small gaps | Design choice |
| `--space-3` | `12px` | Input horizontal padding, gaps between controls | Design choice |
| `--space-4` | `16px` | Button horizontal padding, gaps inside cards | Design choice |
| `--space-5` | `24px` | Card padding, content area padding, gaps between cards | Design choice |
| `--space-6` | `32px` | Larger gaps; part of the 44 px touch target | Design choice |

### Borders, radius and shadows

| Token | Value | Use | Origin |
|---|---|---|---|
| `--border-width` | `1px` | Card, input, navigation and secondary button borders | Design choice |
| `--border-focus-width` | `2px` | Keyboard focus outline | Design choice |
| `--radius-sm` | `4px` | Buttons, inputs, small controls | Design choice |
| `--radius-md` | `8px` | Cards, notifications | Design choice |
| `--radius-pill` | `999px` | Status badges | Design choice |
| `--shadow-sm` | `0 1px 2px rgba(31, 31, 36, 0.08)` | Cards | Design choice |
| `--shadow-md` | `0 4px 12px rgba(31, 31, 36, 0.12)` | Header, tablet navigation drawer | Design choice |

### Layout and layers

| Token | Value | Use | Origin |
|---|---|---|---|
| `--layout-header-height` | `56px` | Header height | Design choice |
| `--layout-nav-width` | `240px` | Side navigation and drawer width | Design choice |
| `--z-header` | `10` | Header above content | Design choice |
| `--z-backdrop` | `20` | Drawer backdrop above content | Design choice |
| `--z-drawer` | `30` | Drawer and skip link on top | Design choice |

## Color contrast (WCAG 2.1 AA)

Targets: at least **4.5:1** for text and **3:1** for control borders and icons (SC-006). Ratios
are computed with the WCAG 2.1 relative luminance formula for every text/background pair the
current components use. Disabled controls are exempt under WCAG and are not listed.

| Foreground | Background | Where it is used | Ratio | Result |
|---|---|---|---|---|
| `text-on-primary` | `primary` | Header, primary button | 5.75:1 | Pass |
| `primary` | `surface` | Secondary button, links, skip link, focus outline | 5.75:1 | Pass |
| `primary` | `background` | Focus outline on the page background | 5.28:1 | Pass |
| `primary` | `primary-subtle` | Active navigation link, secondary button hover | 4.86:1 | Pass |
| `text` | `surface` | Body text, card titles | 16.41:1 | Pass |
| `text` | `background` | Page title, text on the page background, navigation hover | 15.07:1 | Pass |
| `text-secondary` | `surface` | Labels, navigation links | 8.74:1 | Pass |
| `text-secondary` | `background` | Neutral badge, page description | 8.03:1 | Pass |
| `text-muted` | `surface` | Hints, placeholders, search icon | 5.26:1 | Pass |
| `error` | `surface` | Field error message and border | 5.62:1 | Pass |
| `border-control` | `surface` | Input borders (3:1 target) | 3.42:1 | Pass |
| `success` | `success-subtle` | Success badge and notification icon | 5.82:1 | Pass |
| `warning` | `warning-subtle` | Warning badge and notification icon | 4.93:1 | Pass |
| `error` | `error-subtle` | Error badge and notification icon | 4.92:1 | Pass |
| `info` | `info-subtle` | Information badge and notification icon | 5.04:1 | Pass |
| `text` | `success-subtle` | Success notification text | 14.49:1 | Pass |
| `text` | `warning-subtle` | Warning notification text | 14.91:1 | Pass |
| `text` | `error-subtle` | Error notification text | 14.37:1 | Pass |
| `text` | `info-subtle` | Information notification text | 14.38:1 | Pass |
| `text-secondary` | `success-subtle` | Notification close button | 7.72:1 | Pass |
| `text-secondary` | `warning-subtle` | Notification close button | 7.95:1 | Pass |
| `text-secondary` | `error-subtle` | Notification close button | 7.65:1 | Pass |
| `text-secondary` | `info-subtle` | Notification close button | 7.66:1 | Pass |

When a component starts using a new color pair, add it here with its ratio. If a pair fails, use a
passing token pair in the component instead of changing a token value.

## Icons

- One in-house set rendered by `Icon` (`frontend/src/components/ui/Icon.tsx`); no icon library.
- Outline style on a 24×24 grid, 1.75 stroke width, round caps and joins, drawn in `currentColor`
  so icons take the text color.
- Sizes: 16, 20 (default) or 24 px.
- Current set (9): `menu`, `close`, `home`, `components`, `search`, `info`, `warning`, `error`,
  `success`.
- Icons next to visible text are hidden from screen readers. An icon-only button must have an
  accessible name (`aria-label`).

## Layout

- **Frame**: header (56 px, primary color), side navigation (240 px) and main content area
  (24 px padding), shared by every page.
- **Desktop (1024 px and wider)**: the side navigation is always visible to the left of the
  content.
- **Tablet (768–1023 px)**: the navigation becomes a drawer that opens from the menu button in the
  header. It closes on link selection, Escape, the close button or a click on the backdrop.
- **No horizontal page scrolling** at any supported width: long text wraps inside the content
  area.
- **Keyboard**: a "Saltar al contenido" skip link comes first, and every interactive element
  shows a 2 px primary focus outline.
- Phone widths (below 768 px) are not a supported target.

## Brand and content rules

- **Not a shop**: no shopping elements such as cart, prices, promotional banners, product
  carousels or checkout calls to action (FR-006).
- **Mexican department store only**: never use Liverpool Football Club branding (FR-006).
- **Logo**: the header keeps an empty logo slot. Use the Liverpool logo only if an official asset
  is supplied, and never recreate or approximate it (FR-007).
- **Language**: all visible text is in Spanish (es-MX) (FR-018).

## How to change the identity

1. Edit the value in `frontend/src/styles/tokens.css`. Components pick it up automatically.
2. Update the matching row in this document and re-check the contrast table.
3. Mark a value `Official — <source>` only when a verified official Liverpool reference is
   provided, and name that source (FR-005). Until then every value stays `Design choice`.
