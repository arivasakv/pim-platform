# UI Contract: Taxonomía (product hierarchy and inherited attributes)

**Feature**: `010-taxonomy-attribute-inheritance`. Exact Spanish (es-MX) texts and behavior for tests
and reviewers. API: [taxonomy.openapi.yaml](taxonomy.openapi.yaml).

## Navigation and page

| Element | Text / behavior |
|---|---|
| Side navigation entry | **Taxonomía** → `#/taxonomia`, after "Grupo de Atributos" |
| Page header (h1) | **Taxonomía** |
| Page description | **Organiza la jerarquía de productos y asigna atributos que heredan todos los niveles inferiores.** |
| Layout | Two columns like "Lista de Valores": tree card left, node card right; one column under 64rem |

## Tree card

| Element | Text / behavior |
|---|---|
| Card title | **Jerarquía de productos** |
| Action (permission) | **Nuevo nodo de primer nivel** |
| Loading | **Cargando la jerarquía…** |
| Error | **No se pudo conectar con el servidor. Inténtalo de nuevo.** + **Reintentar** |
| Empty | **Todavía no hay nodos en la jerarquía.** (+ with permission: **Crea el primer nivel con «Nuevo nodo de primer nivel».**) |
| Node row | Expand button (only if it has children): accessible name **Mostrar hijos de {nodo}** / **Ocultar hijos de {nodo}**, `aria-expanded`; node button: name + count, accessible name **{nodo}, {n} atributo(s) directo(s)**, `aria-pressed` when chosen |
| Count text in the row | **{n}** in a small rounded badge (secondary text) |
| Row icons (follow-up of 2026-11-12, decorative, `aria-hidden`) | Expand button: chevron right (collapsed) / chevron down (expanded); before the name: closed folder (node with children, collapsed), open folder (expanded), tag (node without children); the chosen node's icon uses the primary color. Design-system icons `chevron-right`, `chevron-down`, `folder`, `folder-open`, `tag` added |
| Expansion | Top level expanded on load; opening a node expands its ancestors; collapsing never changes the selection |

## Node card

| Element | Text / behavior |
|---|---|
| Card title | Node name |
| Path line | Ancestor names as link-buttons separated by **›**, then the node (plain); top level: **Primer nivel** |
| Facts | **{systemId} · Nivel {n}**; description or **Sin descripción** |
| Actions (permission) | **Agregar nodo hijo**, **Editar**, **Mover** (feature 011), **Eliminar** |
| Children | Heading (h3) **Nodos hijos**; list of link-buttons; none: **Este nodo no tiene nodos hijos.** |
| Listas de valores en este nodo (feature 012, 2026-11-19) | Section after "Atributos del nodo" with allowed values per list attribute, **Filtrar valores** and **Vista previa**; texts in [taxonomy-value-filters-ui.md](../../012-taxonomy-list-value-filters/contracts/taxonomy-value-filters-ui.md) |
| Mover nodo (feature 011, 2026-11-19) | **Mover** action between **Editar** and **Eliminar**; panel, confirmation and texts in [taxonomy-move-ui.md](../../011-taxonomy-node-relocation/contracts/taxonomy-move-ui.md) |

### Create / edit form (inline card, like "Grupo de Atributos")

| Element | Text |
|---|---|
| Card title | **Nuevo nodo de primer nivel** / **Nuevo nodo hijo de «{padre}»** / **Editar nodo** |
| Fields | **Nombre** (`id="nodo-nombre"`, max 80), **Descripción** (`id="nodo-descripcion"`, max 500) |
| Buttons | **Cancelar**, **Crear nodo** / **Guardar cambios** |
| Name required | **Escribe el nombre del nodo.** |
| Name in use | Backend message on the name field |
| Success | **Nodo «{nodo}» creado con el ID {systemId}.** / **Nodo «{nodo}» actualizado.** |

### Delete confirmation (inline warning)

| Situation | Text |
|---|---|
| Deletable | **¿Eliminar el nodo «{nodo}» ({systemId})? Ningún atributo se elimina.** + **Cancelar** / **Sí, eliminar nodo** |
| Has children | **El nodo «{nodo}» tiene {n} nodo(s) hijo(s). Elimínalos antes de eliminar este nodo.** + **Cancelar** only, no request |
| Has direct attributes | **El nodo «{nodo}» tiene {n} atributo(s) asignado(s). Quítalos antes de eliminar el nodo.** + **Cancelar** only, no request |
| Success | **Nodo «{nodo}» eliminado.** |

## "Atributos del nodo" section

| Element | Text / behavior |
|---|---|
| Heading (h3) | **Atributos del nodo** |
| Summary | **{d} directo(s) · {h} heredado(s)** |
| Only inherited | **Este nodo no tiene atributos asignados directamente; hereda {h} de sus niveles superiores.** (info notification) |
| Top level with none | **Este nodo no tiene atributos todavía.** |
| Lower level with none | **Este nodo no tiene atributos todavía; tampoco hereda atributos de sus niveles superiores.** |
| Table columns | **Atributo** · **Tipo de dato** · **Asignación** · **Origen** · (actions, permission) |
| Atributo cell | Display name, below it the system ID (secondary) |
| Tipo de dato cell | Label from the attribute types (e.g. "Numérico"); code if unknown |
| Asignación cell | `StatusBadge` **info "Directo"** or **neutral "Heredado"** |
| Origen cell | Direct: **Este nodo**; inherited: link-button with the origin name, accessible name **Ir al nodo {origen}**; direct with ancestor: **Este nodo** + secondary **También se hereda de «{nodo}»** |
| Action cell | Direct rows only: **Quitar** (accessible name **Quitar {atributo} de este nodo**) |
| Note under the table (when inherited rows exist and permission) | **Los atributos heredados se quitan en su nodo de origen.** |
| Remove confirmation | **¿Quitar «{atributo}» de «{nodo}»? Los niveles inferiores dejarán de heredarlo de este nodo. El atributo no se elimina.** + **Cancelar** / **Sí, quitar** |
| Remove success | **Atributo «{atributo}» quitado de «{nodo}».** |

### "Agregar atributos" picker (permission)

| Element | Text / behavior |
|---|---|
| Open button | **Agregar atributos** |
| Fieldset legend | **Agregar atributos a «{nodo}»** |
| Search | **Buscar atributo** (`id="buscar-atributo-nodo"`, name or system ID, accents and case ignored) |
| Options | Attributes not direct on the node, sorted by name; label **{nombre} ({systemId})**; inherited ones add **— heredado de «{origen}»** |
| Paging | 50 at a time; **Mostrando {m} de {n} atributos.** + **Mostrar más** |
| None left | **No hay más atributos para asignar.** |
| No match | **Ningún atributo coincide con la búsqueda.** |
| Buttons | **Cerrar**, **Asignar seleccionados ({n})** (disabled at 0) |
| Success | **{n} atributo(s) asignado(s) a «{nodo}».**; with repeats: + **{k} ya estaba(n) asignado(s).** |
| Refusal | Backend `detail` in an error notification |

## Permissions

Without `ATTRIBUTE_CREATE` in `VITE_DEV_PERMISSIONS`: tree, details and attribute table are shown; no
create, edit, delete, assign or remove controls and no "Los atributos heredados se quitan…" note.

## "Atributos" page (feature 007)

Deleting an attribute assigned to nodes shows the backend refusal: **El atributo pertenece a {n}
nodo(s) de la taxonomía: {nodos}. Quítalo de esos nodos antes de eliminarlo.** (no frontend change).
