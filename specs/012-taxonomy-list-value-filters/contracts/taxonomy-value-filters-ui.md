# UI Contract: Filtros de valores por nivel (Taxonomía)

**Feature**: `012-taxonomy-list-value-filters`. Exact Spanish (es-MX) texts and behavior added to the node
card of the "Taxonomía" page (features 010, 011). API: [taxonomy-value-filters.openapi.yaml](taxonomy-value-filters.openapi.yaml).
Everything not listed here is unchanged.

## Section in the node card

Shown after "Atributos del nodo", only when the node has at least one list-of-values attribute.

| Element | Text / behavior |
|---|---|
| Heading (h3) | **Listas de valores en este nodo** |
| Intro | **Los filtros limitan los valores permitidos en este nodo y en todos sus niveles inferiores.** |
| Table caption (accessible name) | **Listas de valores de «{nodo}»** |
| Columns | **Atributo** · **Valores permitidos** · **Restricción** · (actions) |
| Atributo cell | Display name; below, secondary: **Lista «{lista}» · {systemId}** |
| Valores permitidos cell | **{allowed} de {total}**; with no value allowed: `StatusBadge` error **Ningún valor permitido** |
| Restricción cell | No filter: **Sin filtro**; own filter only: **Filtro en este nodo**; inherited only: **Filtro heredado de «{nodo}»** (link-button to that node, nearest one); both: **Filtro en este nodo** + secondary **También limitado por «{nodo}»** |
| Actions | **Vista previa** (always); **Filtrar valores** (permission only). Accessible names: **Vista previa de {atributo}**, **Filtrar valores de {atributo}** |

## Filter panel (inline in the node card, like the move panel)

| Element | Text / behavior |
|---|---|
| Fieldset legend | **Filtrar «{atributo}» en «{nodo}»** |
| Inherited note (when filters above exist) | **Arriba se permiten {n} de {total} valores (filtro de «{nodo}»).** — with several, the nearest node |
| Two columns | Left **Valores disponibles** (allowed above, not included); right **Valores incluidos en el filtro** (working copy). One column under 48rem |
| Search per column | **Buscar en disponibles** (`id="buscar-valores-disponibles"`), **Buscar en incluidos** (`id="buscar-valores-incluidos"`); label or ID, accents and case ignored |
| Row | **{label}** + secondary **{id}**; button **Incluir** (left) / **Quitar** (right); accessible names **Incluir {label}** / **Quitar {label}** |
| Bulk | **Incluir los mostrados ({n})** / **Quitar los mostrados ({n})**: act on all rows matching the current search, not only the visible page |
| Paging | 50 rows per column; **Mostrando {m} de {n}.** + **Mostrar más** |
| Empty columns | Left: **No quedan valores por incluir.** / no match: **Ningún valor coincide con la búsqueda.**; right: **Todavía no incluyes valores. Sin filtro se permiten los {n} valores disponibles.** |
| Excluded above (own filter narrowed later) | In the right column, those rows show secondary **Excluido por «{nodo}»** (no **Quitar** button) and an info notification above the columns: 1: **1 valor de este filtro ya no está permitido arriba y no se aplica. Se quitará al guardar.**; k > 1: **{k} valores de este filtro ya no están permitidos arriba y no se aplican. Se quitarán al guardar.** Saving sends only the values allowed above (the backend refuses the others, FR-009) |
| Counter | **{k} valores incluidos** (1: **1 valor incluido**), counting the included values allowed above |
| Buttons | **Cancelar**, **Quitar filtro** (only when the node has its own filter), **Guardar filtro** (disabled with 0 included or while saving) |
| Remove confirmation (inline warning) | **¿Quitar el filtro de «{atributo}» en «{nodo}»? Este nodo y sus niveles inferiores permitirán los valores heredados de arriba.** + **Cancelar** / **Sí, quitar filtro** |
| Success | **Filtro de «{atributo}» guardado en «{nodo}»: {k} de {total} valores permitidos.** / **Filtro de «{atributo}» quitado de «{nodo}».** |
| Refusal | Backend `detail` in an error notification; the panel stays open with the working copy |

## Preview

| Element | Text / behavior |
|---|---|
| Heading (h4) | **Vista previa de «{atributo}» en «{nodo}»** |
| Note | **Así se verá el campo al capturar un producto de este nodo. Lo que elijas aquí no se guarda.** |
| Field | Single selection: select with the allowed values; multiple: checkboxes. First 50 values, then feature 008's summary text |
| No value allowed | Warning notification **Ningún valor está permitido en este nodo. Revisa los filtros de «{nodos}».** |
| Close | **Cerrar vista previa** |

## Permissions

Without `ATTRIBUTE_CREATE`: the section, counts, restrictions and preview are shown; no **Filtrar
valores**.
