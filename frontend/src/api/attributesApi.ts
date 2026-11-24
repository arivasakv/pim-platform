import { buildApiUrl } from './apiConfig'

// Shapes from specs/004-attribute-creation/contracts/attributes.openapi.yaml (v1.2.0, feature 007).
export type DataType =
  'TEXT' | 'NUMERIC' | 'BOOLEAN' | 'LIST_OF_VALUES' | 'DATE_TIME' | 'CURRENCY' | 'REFERENCE'

/** `symbol` is only given for the currencies of a currency attribute (feature 004 FR-014). */
export type AttributeOption = { code: string; label: string; symbol?: string }

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
export type ListOfValuesProperties = {
  selectionMode?: string
  listOfValuesId?: string
  /** Read only, in responses: the linked list's name (feature 007). */
  listOfValuesName?: string
}
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
  dataType: DataType
  textProperties?: TextProperties
  numericProperties?: NumericProperties
  listOfValuesProperties?: ListOfValuesProperties
  dateTimeProperties?: DateTimeProperties
  currencyProperties?: CurrencyProperties
  referenceProperties?: ReferenceProperties
}

/** A group that contains an attribute (feature 007). */
export type GroupReference = { id: string; name: string }

export type Attribute = AttributeCreationRequest & {
  systemId: string
  scope: 'GLOBAL'
  createdAt?: string
  /** Absent until the attribute is first edited (feature 007). */
  updatedAt?: string
  /** The groups containing the attribute, sorted by name (feature 007). */
  groups?: GroupReference[]
}

/** One problem in a request, as listed in the RFC 9457 `errors` property (ADR 0001). */
export type ApiFieldError = { field: string; code: string; message: string }

export type AttributeApiErrorKind =
  'validation' | 'forbidden' | 'notFound' | 'conflict' | 'unprocessable' | 'unavailable'

const KIND_BY_STATUS: Record<number, AttributeApiErrorKind> = {
  400: 'validation',
  403: 'forbidden',
  404: 'notFound',
  409: 'conflict',
  422: 'unprocessable',
}

/** Permission code that allows creating, editing and deleting attributes (feature 007 FR-018). */
const CHANGE_PERMISSION = 'ATTRIBUTE_CREATE'

/**
 * Why a request failed: the backend refused it (with its Spanish `detail` and field `errors`) or
 * could not be reached (`unavailable`).
 */
export class AttributeApiError extends Error {
  readonly kind: AttributeApiErrorKind
  readonly detail: string | undefined
  readonly errors: ApiFieldError[]
  /** For a refused deletion: the groups that still contain the attribute (feature 007 FR-014). */
  readonly groups: GroupReference[]

  constructor(
    kind: AttributeApiErrorKind,
    detail?: string,
    errors: ApiFieldError[] = [],
    groups: GroupReference[] = [],
  ) {
    super(detail ?? 'No se pudo conectar con el servidor.')
    this.name = 'AttributeApiError'
    this.kind = kind
    this.detail = detail
    this.errors = errors
    this.groups = groups
  }
}

/**
 * Whether the development permissions allow creating, editing and deleting attributes, so the
 * page can hide those controls (feature 007 FR-018). The backend checks again.
 */
export function canChangeAttributes(): boolean {
  const codes = import.meta.env.VITE_DEV_PERMISSIONS ?? ''
  return codes.split(',').some((code: string) => code.trim().toUpperCase() === CHANGE_PERMISSION)
}

/**
 * Development stand-ins for the user's permissions until authentication exists (feature 004,
 * research R-07). Each header is sent only when its setting is not empty. Also used by the
 * attribute groups client (feature 005).
 */
export function permissionHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}
  const permissions = import.meta.env.VITE_DEV_PERMISSIONS?.trim()
  const manualIdPermissions = import.meta.env.VITE_DEV_MANUAL_ID_PERMISSIONS?.trim()
  if (permissions) headers['X-Pim-Permissions'] = permissions
  if (manualIdPermissions) headers['X-Pim-Manual-Id-Permissions'] = manualIdPermissions
  return headers
}

/** The Spanish `detail` and field `errors` of an RFC 9457 problem body; empty when unreadable. */
export async function readProblem(
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

/**
 * Every attribute, sorted by display name (`GET /api/v1/attributes`, contract v1.1.0). Used by
 * the attribute groups page to offer attributes to add (feature 005, plan decision D-3).
 */
export async function listAttributes(): Promise<Attribute[]> {
  const response = await send('/api/v1/attributes', { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new AttributeApiError('unavailable')
  const body = (await response.json()) as { items: Attribute[] }
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
  throw await refusal(response)
}

/** One attribute with its groups and list name (`GET /api/v1/attributes/{id}`, feature 007). */
export async function getAttribute(systemId: string): Promise<Attribute> {
  const response = await send(`/api/v1/attributes/${encodeURIComponent(systemId)}`, {
    headers: { Accept: 'application/json' },
  })
  if (response.ok) return (await response.json()) as Attribute
  throw await refusal(response)
}

/**
 * Edits an attribute (`PUT /api/v1/attributes/{id}`, feature 007 FR-007). The body has the
 * creation fields; the system ID and the data type must stay the same.
 */
export async function updateAttribute(
  systemId: string,
  request: AttributeCreationRequest,
): Promise<Attribute> {
  const response = await send(`/api/v1/attributes/${encodeURIComponent(systemId)}`, {
    method: 'PUT',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...permissionHeaders(),
    },
    body: JSON.stringify(request),
  })
  if (response.ok) return (await response.json()) as Attribute
  throw await refusal(response)
}

/**
 * Deletes an attribute that belongs to no group (`DELETE /api/v1/attributes/{id}`, feature 007
 * FR-012 to FR-016). A refusal because of groups carries them in `groups`.
 */
export async function deleteAttribute(systemId: string): Promise<void> {
  const response = await send(`/api/v1/attributes/${encodeURIComponent(systemId)}`, {
    method: 'DELETE',
    headers: { Accept: 'application/problem+json', ...permissionHeaders() },
  })
  if (response.ok) return
  throw await refusal(response)
}

// --- Feature 014: unlink a list of values from an attribute ---

/** A taxonomy node named in an unlink review. */
export type NodeReference = { id: string; name: string }

/** What unlinking the attribute's list would affect; nothing changes (`GET …/list-unlink`). */
export type ListUnlinkReview = {
  attributeId: string
  attributeName: string
  listOfValuesId: string
  listOfValuesName: string | null
  filteredNodes: NodeReference[]
  reviewToken: string
}

/** The new data type and its properties, in feature 004's shapes, plus the review's token. */
export type ListUnlinkRequest = Omit<AttributeCreationRequest, 'displayName' | 'systemId'> & {
  reviewToken: string
}

function unlinkPath(systemId: string): string {
  return `/api/v1/attributes/${encodeURIComponent(systemId)}/list-unlink`
}

/** The list to detach and the nodes whose value filters would be deleted (feature 014 FR-009). */
export async function reviewListUnlink(systemId: string): Promise<ListUnlinkReview> {
  const response = await send(unlinkPath(systemId), { headers: { Accept: 'application/json' } })
  if (response.ok) return (await response.json()) as ListUnlinkReview
  throw await refusal(response)
}

/**
 * Detaches the attribute's list and gives it the new data type and properties (feature 014
 * FR-001). A `conflict` means the attribute or its filters changed since the review.
 */
export async function unlinkList(systemId: string, request: ListUnlinkRequest): Promise<Attribute> {
  const response = await send(unlinkPath(systemId), {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...permissionHeaders(),
    },
    body: JSON.stringify(request),
  })
  if (response.ok) return (await response.json()) as Attribute
  throw await refusal(response)
}

/** The error for a response the backend refused; unknown statuses count as unavailable. */
async function refusal(response: Response): Promise<AttributeApiError> {
  const kind = KIND_BY_STATUS[response.status]
  if (!kind) return new AttributeApiError('unavailable')
  try {
    const body = (await response.json()) as { detail?: unknown; errors?: unknown; groups?: unknown }
    return new AttributeApiError(
      kind,
      typeof body.detail === 'string' ? body.detail : undefined,
      Array.isArray(body.errors) ? (body.errors as ApiFieldError[]) : [],
      Array.isArray(body.groups) ? (body.groups as GroupReference[]) : [],
    )
  } catch {
    return new AttributeApiError(kind)
  }
}
