# UI Contract: Mover nodo (Taxonomía)

**Feature**: `011-taxonomy-node-relocation`. Exact Spanish (es-MX) texts and behavior added to the
"Taxonomía" page of feature 010 ([taxonomy-ui.md](../../010-taxonomy-attribute-inheritance/contracts/taxonomy-ui.md)).
API: [taxonomy-move.openapi.yaml](taxonomy-move.openapi.yaml). Everything not listed here is unchanged.

## Node card

| Element | Text / behavior |
|---|---|
| Actions (permission) | **Agregar nodo hijo**, **Editar**, **Mover**, **Eliminar** (Mover added before Eliminar) |
| Without permission | No **Mover** button |
| While the move panel or a confirmation is open | The card actions are hidden, as during the delete confirmation |

## Move panel (inline in the node card, like "Agregar atributos")

| Element | Text / behavior |
|---|---|
| Fieldset legend | **Mover «{nodo}»** |
| Current location | **Ubicación actual: {ruta}** where {ruta} is the ancestors joined by **›**, or **Primer nivel** |
| Branch note | 1 descendant: **Se moverá junto con su nodo descendiente.**; n > 1: **Se moverá junto con sus {n} nodos descendientes.**; none: not shown |
| Search | **Buscar nodo destino** (`id="buscar-nodo-destino"`, `type="search"`; name or system ID, accents and case ignored) |
| Top-level option | **Primer nivel (sin nodo padre)**; first in the list; not shown for a top-level node or when the search has text that does not match it |
| Destination options | One button per node with `aria-pressed`; label **{nombre}**, below it secondary **{ruta completa} · {systemId}**; sorted by path; the node itself, its descendants and its current parent are not offered |
| Paging | 50 at a time; **Mostrando {m} de {n} nodos.** + **Mostrar más** |
| No match | **Ningún nodo coincide con la búsqueda.** |
| No destination available | **No hay otro lugar al que mover este nodo.** |
| Buttons | **Cancelar** (closes, nothing changes), **Continuar** (disabled until a destination is chosen) |

## Confirmation (inline warning, like delete)

| Element | Text |
|---|---|
| Message | **¿Mover «{nodo}» a «{destino}»?** / **¿Mover «{nodo}» al primer nivel?** |
| New location | **Nueva ubicación: {ruta nueva} › {nodo}** (top level: **Nueva ubicación: {nodo} (primer nivel)**) |
| Count | **Se moverá 1 nodo: «{nodo}».** / **Se moverán 2 nodos: «{nodo}» y 1 descendiente.** / **Se moverán {n} nodos: «{nodo}» y {n-1} descendientes.** |
| Inheritance note | **Dejará de heredar los atributos de su ubicación actual y heredará los de su nuevo destino.** |
| Buttons | **Cancelar** (back to the panel with the choice kept), **Sí, mover nodo** (disabled while saving) |

## Result

| Situation | Behavior / text |
|---|---|
| Success | Success notification **Nodo «{nodo}» movido a «{destino}».** / **Nodo «{nodo}» movido al primer nivel.**; with descendants add **Se movieron {n} nodos.** (n = `movedNodeCount`). The node stays selected; its detail shows the new path, level and effective attributes; the tree reloads and expands the new ancestors |
| Refusal (409, 404, 403) | Error notification with the backend `detail`; the panel stays open so another destination can be chosen. A 404 for the node itself closes the panel and reloads the tree |
| Backend unreachable | **No se pudo conectar con el servidor. Inténtalo de nuevo.**; nothing changed |

## Accessibility

- Destination buttons are reachable with Tab and announce their pressed state; the chosen one is
  visibly marked (not by color alone: a check icon or "Seleccionado" text).
- When the confirmation appears, focus moves to its message; after success, the success notification is
  announced through its `role="status"`, as for every other change on the page.
