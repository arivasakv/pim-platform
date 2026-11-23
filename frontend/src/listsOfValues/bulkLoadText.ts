import type { BulkLoadResult, BulkLoadSummary } from '../api/listsOfValuesApi'

/** Sentences of the bulk load panel (feature 013 UI contract), in singular or plural. */

/** "1 lista" / "3 listas". */
function count(value: number, one: string, many: string): string {
  return value === 1 ? `1 ${one}` : `${value} ${many}`
}

/** «Se crearán 1 lista…»: verb in singular or plural with its count. */
function future(value: number, singular: string, plural: string, noun?: [string, string]): string {
  const verb = value === 1 ? singular : plural
  return noun ? `${verb} ${count(value, noun[0], noun[1])}` : `${verb} ${value}`
}

/** The summary sentence of the UI contract. */
export function summarySentence(summary: BulkLoadSummary): string {
  return (
    `${future(summary.listsToCreate, 'Se creará', 'Se crearán', ['lista', 'listas'])} y ` +
    `${future(summary.listsToUpdate, 'se actualizará', 'se actualizarán')}. ` +
    `${future(summary.valuesToAdd, 'Se agregará', 'Se agregarán', ['valor', 'valores'])} y ` +
    `${future(summary.valuesToUpdate, 'se actualizará', 'se actualizarán')}.`
  )
}

/** The result sentence of the UI contract. */
export function resultSentence(result: BulkLoadResult): string {
  return (
    `Carga aplicada: ${count(result.listsCreated, 'lista creada', 'listas creadas')}, ` +
    `${count(result.listsUpdated, 'actualizada', 'actualizadas')}; ` +
    `${count(result.valuesAdded, 'valor agregado', 'valores agregados')} y ` +
    `${count(result.valuesUpdated, 'actualizado', 'actualizados')}.`
  )
}
