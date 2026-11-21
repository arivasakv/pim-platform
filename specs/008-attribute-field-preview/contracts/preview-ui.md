# UI Contract: Attribute Field Preview

**Feature**: `008-attribute-field-preview`. No API contract changes; this file fixes what the preview
shows, so tests and reviewers share one reference. Texts are exact (Spanish, es-MX).

## Panel

- Position:
  - creation and editing: inside the attribute form card, after the type-specific fields, before the
    action buttons;
  - consultation (amendment 2026-11-12): inside the attribute detail card on "Atributos", after the
    configuration details ("Modificado" row), built from the saved attribute.
- Heading, note, label, help text, per-type fields, notes and list states are identical in the three
  scenarios.
- Heading (h3): **Vista previa del campo**
- Note: **Así se verá el campo en un formulario de producto. Lo que escribas aquí no se guarda.**
- Label of the field: **{nombre} (opcional)**; empty name: **Nombre del atributo (opcional)**.
- Help text: the rule texts below, joined with " · " (attributes have no description since 2026-11-20, spec FR-004 amendment).
- Configuration notes, when any: an info notification **Vista previa con ajustes:** followed by one
  line per note.

## Per data type

| Data type | Field | Rule texts (hint) | Value messages (error) |
|---|---|---|---|
| (none) | none | — | — ; note **Elige un tipo de dato para ver el campo.** |
| Texto | text input (`textarea` if rich text) | **Entre {min} y {max} caracteres** / **Hasta {max} caracteres** / **Al menos {min} caracteres**; rich text: **Acepta texto enriquecido (HTML).**; uppercase: **Se escribe en mayúsculas.**; lowercase: **Se escribe en minúsculas.** | **Escribe al menos {min} caracteres.** · **No cumple la máscara de captura.** |
| Numérico entero | number, step 1 | **Número entero** + range text | **Escribe un número entero.** · range message |
| Numérico decimal | number, step 10^-p | **Hasta {p} decimales** + range text | **Usa como máximo {p} decimales.** · range message |
| Booleano | checkbox | — | — |
| Lista, única | select, placeholder **Selecciona una opción** | **Lista: {lista}** | — |
| Lista, múltiple | one checkbox per value (fieldset, legend = label) | **Lista: {lista} · Puedes elegir varios valores** | — |
| Fecha y hora | text, placeholder = format | **Formato: {formato}** | **Usa el formato {formato}.** · **La fecha no existe.** |
| Moneda | number, step 10^-d | **Moneda: {código} ({símbolo}) · {d} decimales** | **Usa como máximo {d} decimales.** |
| Referencia | disabled select | **Los objetos se podrán elegir cuando existan los formularios de producto.** | — |

Range text: **Entre {min} y {max}** / **Desde {min}** / **Hasta {max}**. Range message: **El valor
debe estar entre {min} y {max}.** / **El valor debe ser al menos {min}.** / **El valor debe ser como
máximo {max}.**

Reference placeholder: **Selecciona un objeto: {objeto}** (single) / **Selecciona uno o varios:
{objeto}** (multiple).

## Configuration notes (spec FR-010)

| Situation | Note |
|---|---|
| Invalid capture mask | **La máscara de captura no es una expresión regular válida; la vista previa no la aplica.** |
| Length minimum > maximum | **La longitud mínima es mayor que la máxima; la vista previa no aplica los límites de longitud.** |
| Value minimum > maximum | **El valor mínimo es mayor que el máximo; la vista previa no aplica el rango.** |
| A limit that is not a number | **Un límite no es un número; la vista previa lo ignora.** |
| Decimal without decimals | **Sin decimales configurados: la vista previa usa 2.** |
| Unknown currency | **Moneda {código} no reconocida; se muestra solo el código.** |
| No currency code | **Escribe el código de moneda para ver el símbolo.** |
| No date format | **Elige un formato de fecha para ver el campo.** |
| No selection mode | **Tipo de selección sin elegir: la vista previa usa selección única.** |
| No reference target | **Elige el objeto referenciado para ver el campo.** |

## List of values states

| State | Shown |
|---|---|
| No list chosen | **La vista previa necesita una lista de valores asociada al atributo.** |
| Loading | **Cargando valores de la lista…** |
| Error | **No se pudieron cargar los valores de la lista para la vista previa.** |
| No values | **La lista {lista} todavía no tiene valores.** |
| More than 50 | **Vista previa con los primeros 50 de {total} valores.** |
