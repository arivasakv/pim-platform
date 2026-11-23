# UI Contract: Lists-of-values page — «Crear LOV» and «Carga Masiva de LOV»

**Feature**: [spec.md](../spec.md) | **API**: [lov-bulk-load.openapi.yaml](lov-bulk-load.openapi.yaml)

Route `#/listas-de-valores` (title «Lista de Valores (LOV's)», feature 006). Texts in Spanish; design
system components only (feature 001). Everything below is shown only to users with the
list-administration permission (`VITE_DEV_PERMISSIONS` contains `ATTRIBUTE_CREATE`).

## Page header actions (FR-001)

| Element | Text | Behaviour |
|---|---|---|
| Primary button | **Crear LOV** | Opens the create card in the left column (closes the bulk card) |
| Secondary button | **Carga Masiva de LOV** | Opens the bulk card in the left column (closes the create card) |

Without the permission neither button nor card is shown (as today's "Nueva lista" card).

## Create card (FR-002)

Feature 006's "Nueva lista" form with the card title **Crear LOV** and the button **Crear LOV**; same
field («Nombre»), rules and messages. «Cancelar» closes the card.

## Bulk card «Carga Masiva de LOV» (FR-003, FR-010–FR-013)

| Step | Content |
|---|---|
| Intro | «Sube un archivo Excel (.xlsx) o CSV con las columnas Lista, ID, Etiqueta y Equivalencia. Cada fila es un valor de la lista indicada; si la lista no existe, se crea. Un ID vacío se genera. Un ID que ya existe en la lista actualiza su etiqueta. Primero verás un resumen; nada se guarda hasta que confirmes.» |
| Templates | Links **Descargar plantilla Excel** (`?format=xlsx`) and **Descargar plantilla CSV** (`?format=csv`) |
| File | Label **Archivo (Excel o CSV)**, accepts `.xlsx,.csv`; choosing another file clears any summary or report |
| Check | Button **Revisar archivo** (disabled without a file or while busy); while busy «Revisando archivo…» |
| Errors (422 INVALID_IMPORT) | Error notification with the problem's `detail`, then a table **Fila · Columna · Problema** with every `rowErrors` entry, in order |
| File error (other 422, 400, 403) | Error notification with the problem's `detail` |
| Summary (200 /check) | Heading **Resumen de la carga**; line «Se crearán {n} listas y se actualizarán {m}. Se agregarán {a} valores y se actualizarán {u}.» (singular/plural); table **Lista · Acción · Valores nuevos · Valores actualizados** (Acción «Crear» or «Actualizar»); note «Nada se ha guardado todavía.»; buttons **Confirmar carga** (primary) and **Cancelar** |
| Applying | «Aplicando carga…»; buttons disabled |
| Result (200 apply) | Success notification «Carga aplicada: {n} listas creadas, {m} actualizadas; {a} valores agregados y {u} actualizados.»; the list overview reloads; the file input clears |
| Outdated (409 BULK_LOAD_OUTDATED) | Warning notification with the message; back to the file step with the same file chosen, so «Revisar archivo» can be pressed again |
| Other 409 / connection error | Error notification with the message, or «No se pudo conectar con el servidor. Inténtalo de nuevo.» |
| Cancelar | Clears summary and file; nothing is sent |

## List detail (FR-004)

The per-list «Carga masiva» section of feature 006 is unchanged.
