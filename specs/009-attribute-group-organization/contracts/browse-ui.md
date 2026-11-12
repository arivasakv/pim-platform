# UI Contract: Attribute Organization by Attribute Groups

**Feature**: `009-attribute-group-organization`. **No API contract changes**: the feature uses
`specs/004-attribute-creation/contracts/attributes.openapi.yaml` (v1.2.0, feature 007) and
`specs/005-attribute-groups/contracts/attribute-groups.openapi.yaml` as published. This file fixes the
texts (Spanish, es-MX) and behavior so tests and reviewers share one reference.

## "Atributos" page – group panel

| Element | Text / behavior |
|---|---|
| Panel card title | **Grupos** |
| Link (card action) | **Administrar grupos** → `#/grupos-de-atributos` |
| Narrow-screen toggle (button, `aria-expanded`) | **Grupo: {entrada} ({n})** |
| Group search (`id="buscar-grupo"`, type search) | Label **Buscar grupo**; shown when at least one group exists |
| Fixed entries | **Todos los atributos** · **Sin grupo** (always listed first, never filtered) |
| Group entries | Group name, sorted by name; count at the right |
| Entry button | `aria-pressed` on the chosen entry; accessible name **{entrada}, {n} atributo(s)** |
| Loading | **Cargando grupos…** |
| Error | **No se pudieron cargar los grupos.** + button **Reintentar** (reloads groups and list) |
| No groups | **Todavía no hay grupos de atributos.** + link **Crear grupos en «Grupo de Atributos»** |
| Group search without match | **Ningún grupo coincide con «{texto}».** |

## "Atributos" page – attribute list

| Element | Text / behavior |
|---|---|
| List card title | The entry: **Todos los atributos** / **Sin grupo** / **{grupo}** |
| Search, type filter, count, paging | Feature 007 texts, unchanged (`buscar-atributo`, `filtrar-tipo`, "{n} atributo(s)", "Mostrando {m} de {n} atributos.", "Mostrar más") |
| No attributes at all | **Todavía no hay atributos.** (feature 007) |
| Empty group | **Este grupo todavía no tiene atributos.** + **Agrégalos desde la sección «Grupos» de cada atributo o en «Grupo de Atributos».** |
| "Sin grupo" empty | **Todos los atributos pertenecen al menos a un grupo.** |
| No match, entry "Todos" | **Ningún atributo coincide con la búsqueda.** (feature 007) |
| No match in a group | **Ningún atributo de «{grupo}» coincide con la búsqueda.** |
| No match in "Sin grupo" | **Ningún atributo sin grupo coincide con la búsqueda.** |
| Matches elsewhere (only after one of the two above) | **{n} coincidencia(s) en otros grupos.** + button **Buscar en todos los atributos** (chooses "Todos los atributos", keeps search and type) |
| List row | Feature 007's row, unchanged (name; system ID · type; groups or "Sin grupos"; list name) |

Switching entry: keeps search and type; resets paging to 50; keeps the right column (detail, form or
confirmation) as it is.

## "Atributos" page – "Grupos" section of the detail

Replaces the "Grupos" row of the details list; placed after the details list, before the field preview.

| Element | Text / behavior |
|---|---|
| Heading (h3) | **Grupos** |
| No groups | **Sin grupos** |
| One line per group | Group name; with change permission a button **Quitar** with accessible name **Quitar del grupo {grupo}** |
| Remove confirmation (inline warning) | **¿Quitar «{atributo}» del grupo «{grupo}»? El atributo no se elimina ni cambia.** + **Cancelar** / **Sí, quitar del grupo** |
| Add (with change permission) | Select `id="agregar-a-grupo"`, label **Agregar a un grupo**, placeholder **Elige un grupo**, options = groups the attribute is not in, by name; button **Agregar** (disabled until a group is chosen) |
| Already in every group | **El atributo ya está en todos los grupos.** |
| No groups exist | **Todavía no hay grupos. Créalos en «Grupo de Atributos».** |
| Groups not loaded | **No se pudieron cargar los grupos para agregar el atributo.** (the current groups are still listed and can be removed) |
| Success (page notification) | **Atributo «{atributo}» agregado al grupo «{grupo}».** / **Atributo «{atributo}» quitado del grupo «{grupo}».** |
| Refusal | The backend's Spanish `detail` (feature 005) in an error notification; the section then shows the attribute's current groups |

Without change permission: groups listed, no "Quitar", no "Agregar".

### Delete refusal text (feature 007, changed by D-9)

**El atributo «{atributo}» pertenece a {n} grupo(s): {grupos}. Quítalo de esos grupos en la sección
«Grupos» de este atributo antes de eliminarlo.** + **Cancelar** only (rule unchanged).

## "Grupo de Atributos" page (feature 005)

| Element | Text / behavior |
|---|---|
| "Eliminar grupo", empty group | Inline warning **¿Eliminar el grupo «{grupo}»? Ningún atributo se elimina.** + **Cancelar** / **Sí, eliminar grupo**; the request is sent only after confirming |
| "Eliminar grupo", group with attributes | Inline warning **El grupo «{grupo}» tiene {n} atributo(s). Quítalos del grupo antes de eliminarlo; los atributos no se eliminan.** + **Cancelar**; no request |
| Picker search (`id="buscar-para-agregar"`) | Label **Buscar atributo** (name or system ID, feature 007 rule) |
| Picker paging | First 50 matches; **Mostrando {m} de {n} atributos.** + **Mostrar más** |
| Picker without match | **Ningún atributo coincide con la búsqueda.** |
| Picker add button | **Agregar seleccionados ({n})** (disabled at 0); chosen attributes stay chosen when hidden by the search |
| Everything else | Feature 005, unchanged |

## Layout

| Viewport | Arrangement |
|---|---|
| ≥ 90rem | Panel · list · detail in three columns; panel sticky, its entry list scrolls |
| 64–90rem | Panel as a full-width block above, collapsed to its toggle; list · detail in two columns |
| < 64rem | One column: panel (collapsed), list, detail |

No horizontal page scroll from 768 px up.
