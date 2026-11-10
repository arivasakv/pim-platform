import { buildApiUrl } from './apiConfig'

// Shapes from specs/004-attribute-creation/contracts/attributes.openapi.yaml.
export type DataType =
  'TEXT' | 'NUMERIC' | 'BOOLEAN' | 'LIST_OF_VALUES' | 'DATE_TIME' | 'CURRENCY' | 'REFERENCE'

export type AttributeOption = { code: string; label: string }

export type AttributeType = {
  code: DataType
  label: string
  /** Option lists keyed by property name, e.g. `caseFormat`. */
  options: Record<string, AttributeOption[]>
}

export type TextProperties = {
  minLength?: number
  maxLength?: number
  caseFormat?: string
  captureMask?: string
  richText?: boolean
}
export type NumericProperties = {
  numberFormat: string
  precision?: number
  minimum?: number
  maximum?: number
}
export type ListOfValuesProperties = { selectionMode?: string; listOfValuesId?: string }
export type DateTimeProperties = { dateFormat: string }
export type CurrencyProperties = {
  currencyCode: string
  decimals?: number
  currencySymbol?: string
}
export type ReferenceProperties = { targetObjectType: string; cardinality?: string }

export type AttributeCreationRequest = {
  systemId?: string
  displayName: string
  description?: string | null
  dataType: DataType
  textProperties?: TextProperties
  numericProperties?: NumericProperties
  listOfValuesProperties?: ListOfValuesProperties
  dateTimeProperties?: DateTimeProperties
  currencyProperties?: CurrencyProperties
  referenceProperties?: ReferenceProperties
}

export type Attribute = AttributeCreationRequest & {
  systemId: string
  scope: 'GLOBAL'
  createdAt?: string
}

/** One problem in a request, as listed in the RFC 9457 `errors` property (ADR 0001). */
export type ApiFieldError = { field: string; code: string; message: string }

export type AttributeApiErrorKind =
  'validation' | 'forbidden' | 'conflict' | 'unprocessable' | 'unavailable'

const KIND_BY_STATUS: Record<number, AttributeApiErrorKind> = {
  400: 'validation',
  403: 'forbidden',
  409: 'conflict',
  422: 'unprocessable',
}

/**
 * Why a request failed: the backend refused it (with its Spanish `detail` and field `errors`) or
 * could not be reached (`unavailable`).
 */
export class AttributeApiError extends Error {
  readonly kind: AttributeApiErrorKind
  readonly detail: string | undefined
  readonly errors: ApiFieldError[]

  constructor(kind: AttributeApiErrorKind, detail?: string, errors: ApiFieldError[] = []) {
    super(detail ?? 'No se pudo conectar con el servidor.')
    this.name = 'AttributeApiError'
    this.kind = kind
    this.detail = detail
    this.errors = errors
  }
}

/**
 * Development stand-ins for the user's permissions until authentication exists (feature 004,
 * research R-07). Each header is sent only when its setting is not empty.
 */
function permissionHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}
  const permissions = import.meta.env.VITE_DEV_PERMISSIONS?.trim()
  const manualIdPermissions = import.meta.env.VITE_DEV_MANUAL_ID_PERMISSIONS?.trim()
  if (permissions) headers['X-Pim-Permissions'] = permissions
  if (manualIdPermissions) headers['X-Pim-Manual-Id-Permissions'] = manualIdPermissions
  return headers
}

async function readProblem(
  response: Response,
): Promise<{ detail?: string; errors: ApiFieldError[] }> {
  try {
    const body = (await response.json()) as { detail?: unknown; errors?: unknown }
    const errors = Array.isArray(body.errors) ? (body.errors as ApiFieldError[]) : []
    return { detail: typeof body.detail === 'string' ? body.detail : undefined, errors }
  } catch {
    return { errors: [] }
  }
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const url = buildApiUrl(path)
  if (!url) throw new AttributeApiError('unavailable')
  try {
    return await fetch(url, init)
  } catch {
    throw new AttributeApiError('unavailable')
  }
}

/** The seven data types and their options (`GET /api/v1/attribute-types`). */
export async function fetchAttributeTypes(): Promise<AttributeType[]> {
  const response = await send('/api/v1/attribute-types', {
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw new AttributeApiError('unavailable')
  const body = (await response.json()) as { items: AttributeType[] }
  return body.items
}

/** Creates one attribute (`POST /api/v1/attributes`). */
export async function createAttribute(request: AttributeCreationRequest): Promise<Attribute> {
  const response = await send('/api/v1/attributes', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...permissionHeaders(),
    },
    body: JSON.stringify(request),
  })
  if (response.ok) return (await response.json()) as Attribute
  const kind = KIND_BY_STATUS[response.status]
  if (!kind) throw new AttributeApiError('unavailable')
  const { detail, errors } = await readProblem(response)
  throw new AttributeApiError(kind, detail, errors)
}
