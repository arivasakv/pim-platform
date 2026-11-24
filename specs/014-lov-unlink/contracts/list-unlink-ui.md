# UI Contract: Unlink a list of values from an attribute

**Feature**: [spec.md](../spec.md) | **API**: [list-unlink.openapi.yaml](list-unlink.openapi.yaml)

Texts in Spanish; design-system components only (feature 001). Shown only with `ATTRIBUTE_CREATE`.

## Entry point 1 — attribute edit form (FR-006)

In «Editar atributo» (feature 007) of an attribute of type «Lista de Valores (LOV's)», below the list
fields: secondary button **Desvincular lista**. It switches the form to unlink mode.

## Entry point 2 — list detail (FR-006)

In the list's «Atributos que la usan» (feature 006), each attribute row gets a secondary button
**Desvincular** (accessible name «Desvincular {nombre}»). It opens `#/atributos?desvincular={systemId}`:
the attributes page opens that attribute's edit form directly in unlink mode.

## Unlink mode (FR-001–FR-003, FR-009, FR-010)

| Element | Content |
|---|---|
| Heading | **Desvincular lista de «{nombre del atributo}»** |
| Intro | «El atributo dejará de usar la lista «{nombre de la lista}» y tendrá un nuevo tipo de dato. Su ID, nombre, grupos y nodos de taxonomía no cambian.» |
| ID, Nombre | Read only |
| Tipo de dato | Required; the seven non-list choices of feature 004 FR-007 (Numérico shown as Entero / Decimal); placeholder «Elige el nuevo tipo de dato» |
| Properties | The chosen type's fields, as in «Nuevo atributo» |
| Field preview | Feature 008's «Vista previa del campo» for the new type |
| Review box | Heading **Qué cambiará**; «Se desvinculará la lista «{lista}».»; then either «Se eliminarán los filtros de valores de este atributo en {n} nodos de taxonomía:» with the node names, or «Este atributo no tiene filtros de valores en la taxonomía.» |
| Loading review | «Revisando la desvinculación…» |
| Buttons | **Desvincular y guardar** (primary), **Cancelar** (back to the normal edit form; nothing changes) |
| Success | Detail panel with «Lista desvinculada de «{nombre}». Ahora es de tipo {tipo}.» |
| 400 property errors | Under each field, as in creation |
| 409 UNLINK_OUTDATED | Warning with the message; the review reloads so the user can confirm again |
| Other errors | Error notification with the backend's message, or «No se pudo conectar con el servidor. Inténtalo de nuevo.» |
