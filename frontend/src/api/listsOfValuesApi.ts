import { buildApiUrl } from './apiConfig'
import { permissionHeaders, type ApiFieldError } from './attributesApi'

// Shapes from specs/006-lov-management/contracts/lists-of-values.openapi.yaml.
export type ListOfValuesSummary = {
  systemId: string
  name: string
  valueCount: number
  attributeCount: number
}

export type ListValue = { id: string; label: string; equivalence?: string | null }

export type LinkedAttribute = {
  systemId: string
  displayName: string
  selectionMode: 'SINGLE' | 'MULTIPLE'
}

export type ListOfValues = {
  systemId: string
  name: string
  equivalencesEnabled: boolean
  valueCount: number
  /** In the order they were added; equivalences are included even when the column is off. */
  values: ListValue[]
  /** Attributes that use the list (feature 004), sorted by display name. */
  attributes: LinkedAttribute[]
  createdAt: string
}

export type ImportResult = { added: number; updated: number; total: number }

/** One wrong row of a CSV file; `row` counts the header as row 1. */
export type ImportRowError = { row: number; column: string; code: string; message: string }

export type ListOfValuesApiErrorKind =
  'validation' | 'forbidden' | 'notFound' | 'conflict' | 'unprocessable' | 'unavailable'

const KIND_BY_STATUS: Record<number, ListOfValuesApiErrorKind> = {
  400: 'validation',
  403: 'forbidden',
  404: 'notFound',
  409: 'conflict',
  422: 'unprocessable',
}

/** Permission code that allows changing lists (feature 006 FR-002, same as features 004 and 005). */
const CHANGE_PERMISSION = 'ATTRIBUTE_CREATE'

/**
 * Why a list request failed: the backend refused it (with its Spanish `detail`, field `errors`
 * and, for CSV files, `rowErrors`) or could not be reached (`unavailable`).
 */
export class ListOfValuesApiError extends Error {
  readonly kind: ListOfValuesApiErrorKind
  readonly detail: string | undefined
  readonly errors: ApiFieldError[]
  readonly rowErrors: ImportRowError[]

  constructor(
    kind: ListOfValuesApiErrorKind,
    detail?: string,
    errors: ApiFieldError[] = [],
    rowErrors: ImportRowError[] = [],
  ) {
    super(detail ?? 'No se pudo conectar con el servidor.')
    this.name = 'ListOfValuesApiError'
    this.kind = kind
    this.detail = detail
    this.errors = errors
    this.rowErrors = rowErrors
  }
}

/**
 * Whether the development permission setting allows changing lists. Only decides which controls
 * the page shows; the backend still refuses changes without the permission (403).
 */
export function canChangeLists(): boolean {
  const codes = import.meta.env.VITE_DEV_PERMISSIONS ?? ''
  return codes.split(',').some((code: string) => code.trim().toUpperCase() === CHANGE_PERMISSION)
}

/** The problem body's `detail`, `errors` and `rowErrors`; empty when unreadable. */
async function readListProblem(response: Response): Promise<{
  detail?: string
  errors: ApiFieldError[]
  rowErrors: ImportRowError[]
}> {
  try {
    const body = (await response.json()) as {
      detail?: unknown
      errors?: unknown
      rowErrors?: unknown
    }
    return {
      detail: typeof body.detail === 'string' ? body.detail : undefined,
      errors: Array.isArray(body.errors) ? (body.errors as ApiFieldError[]) : [],
      rowErrors: Array.isArray(body.rowErrors) ? (body.rowErrors as ImportRowError[]) : [],
    }
  } catch {
    return { errors: [], rowErrors: [] }
  }
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const url = buildApiUrl(path)
  if (!url) throw new ListOfValuesApiError('unavailable')
  let response: Response
  try {
    response = await fetch(url, init)
  } catch {
    throw new ListOfValuesApiError('unavailable')
  }
  if (response.ok) return response
  const kind = KIND_BY_STATUS[response.status]
  if (!kind) throw new ListOfValuesApiError('unavailable')
  const { detail, errors, rowErrors } = await readListProblem(response)
  throw new ListOfValuesApiError(kind, detail, errors, rowErrors)
}

function change(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...permissionHeaders(),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  }
}

const LISTS = '/api/v1/lists-of-values'

function listPath(listId: string): string {
  return `${LISTS}/${encodeURIComponent(listId)}`
}

function valuePath(listId: string, valueId: string): string {
  return `${listPath(listId)}/values/${encodeURIComponent(valueId)}`
}

/** Every list, sorted by name (`GET /api/v1/lists-of-values`). */
export async function listLists(): Promise<ListOfValuesSummary[]> {
  const response = await request(LISTS, { headers: { Accept: 'application/json' } })
  const body = (await response.json()) as { items: ListOfValuesSummary[] }
  return body.items
}

/** One list with its values and linked attributes (`GET /api/v1/lists-of-values/{listId}`). */
export async function getList(listId: string): Promise<ListOfValues> {
  const response = await request(listPath(listId), { headers: { Accept: 'application/json' } })
  return (await response.json()) as ListOfValues
}

/**
 * Creates an empty list (`POST /api/v1/lists-of-values`). A blank `systemId` is left out, so the
 * backend generates one; a given one follows feature 003's manual-ID rules (feature 013 FR-002).
 */
export async function createList(name: string, systemId?: string): Promise<ListOfValues> {
  const id = systemId?.trim()
  const response = await request(LISTS, change('POST', id ? { name, systemId: id } : { name }))
  return (await response.json()) as ListOfValues
}

/** Renames a list and turns its equivalence column on or off (`PUT …/{listId}`). */
export async function updateList(
  listId: string,
  list: { name: string; equivalencesEnabled: boolean },
): Promise<ListOfValues> {
  const response = await request(listPath(listId), change('PUT', list))
  return (await response.json()) as ListOfValues
}

/** Deletes a list that no attribute uses (`DELETE …/{listId}`). */
export async function deleteList(listId: string): Promise<void> {
  await request(listPath(listId), change('DELETE'))
}

/** Adds one value; without `id` the backend generates one (`POST …/{listId}/values`). */
export async function addValue(
  listId: string,
  value: { id?: string; label: string; equivalence?: string | null },
): Promise<ListValue> {
  const response = await request(`${listPath(listId)}/values`, change('POST', value))
  return (await response.json()) as ListValue
}

/** Replaces a value's label and equivalence; its ID never changes (`PUT …/values/{valueId}`). */
export async function updateValue(
  listId: string,
  valueId: string,
  value: { label: string; equivalence?: string | null },
): Promise<ListValue> {
  const response = await request(valuePath(listId, valueId), change('PUT', value))
  return (await response.json()) as ListValue
}

/** Deletes one value (`DELETE …/values/{valueId}`). */
export async function deleteValue(listId: string, valueId: string): Promise<void> {
  await request(valuePath(listId, valueId), change('DELETE'))
}

/** Adds or updates values from a CSV file, all or nothing (`POST …/values/import`). */
export async function importValues(listId: string, file: File): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  const response = await request(`${listPath(listId)}/values/import`, {
    method: 'POST',
    headers: { Accept: 'application/json', ...permissionHeaders() },
    body: form,
  })
  return (await response.json()) as ImportResult
}

/** Address of the CSV template, or `null` when the backend address is not configured. */
export function importTemplateUrl(): string | null {
  return buildApiUrl(`${LISTS}/import-template`)
}

// --- Feature 013: bulk load of several lists from a CSV or Excel file ---

/** One list a bulk load creates or updates; `systemId` is `null` for a list still to create. */
export type PlannedListChange = {
  name: string
  systemId: string | null
  action: 'CREATE' | 'UPDATE'
  valuesAdded: number
  valuesUpdated: number
}

/** What a checked file would change; nothing is saved yet (`POST …/bulk-load/check`). */
export type BulkLoadSummary = {
  lists: PlannedListChange[]
  listsToCreate: number
  listsToUpdate: number
  valuesToAdd: number
  valuesToUpdate: number
  checkToken: string
}

/** What an applied file changed (`POST …/bulk-load`). */
export type BulkLoadResult = {
  lists: PlannedListChange[]
  listsCreated: number
  listsUpdated: number
  valuesAdded: number
  valuesUpdated: number
}

function fileUpload(fields: Record<string, string | File>): RequestInit {
  const form = new FormData()
  Object.entries(fields).forEach(([name, value]) => form.append(name, value))
  return {
    method: 'POST',
    headers: { Accept: 'application/json', ...permissionHeaders() },
    body: form,
  }
}

/** Reads and checks a file of several lists; returns the summary and its check token. */
export async function checkBulkLoad(file: File): Promise<BulkLoadSummary> {
  const response = await request(`${LISTS}/bulk-load/check`, fileUpload({ file }))
  return (await response.json()) as BulkLoadSummary
}

/**
 * Applies a checked file: every list or none. A 409 (`conflict`) means the lists changed since the
 * check and the file must be checked again.
 */
export async function applyBulkLoad(file: File, checkToken: string): Promise<BulkLoadResult> {
  const response = await request(`${LISTS}/bulk-load`, fileUpload({ file, checkToken }))
  return (await response.json()) as BulkLoadResult
}

/** Address of the bulk load template in Excel or CSV, or `null` without a backend address. */
export function bulkLoadTemplateUrl(format: 'xlsx' | 'csv'): string | null {
  return buildApiUrl(`${LISTS}/bulk-load/template?format=${format}`)
}
