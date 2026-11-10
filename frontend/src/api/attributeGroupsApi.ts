import { buildApiUrl } from './apiConfig'
import { permissionHeaders, readProblem, type ApiFieldError } from './attributesApi'

// Shapes from specs/005-attribute-groups/contracts/attribute-groups.openapi.yaml.
export type AttributeGroupSummary = {
  id: string
  name: string
  description?: string | null
  attributeCount: number
}

export type GroupMember = { systemId: string; displayName: string; dataType: string }

export type AttributeGroup = AttributeGroupSummary & {
  /** Members in the order they were added. */
  attributes: GroupMember[]
  createdAt: string
}

export type AttributeGroupRequest = { name: string; description?: string | null }

export type AddAttributesResult = {
  group: AttributeGroup
  added: string[]
  alreadyInGroup: string[]
}

export type AttributeGroupApiErrorKind =
  'validation' | 'forbidden' | 'notFound' | 'conflict' | 'unprocessable' | 'unavailable'

const KIND_BY_STATUS: Record<number, AttributeGroupApiErrorKind> = {
  400: 'validation',
  403: 'forbidden',
  404: 'notFound',
  409: 'conflict',
  422: 'unprocessable',
}

/** Permission code that allows changing groups (feature 005 FR-002, same as feature 004). */
const CHANGE_PERMISSION = 'ATTRIBUTE_CREATE'

/**
 * Why a group request failed: the backend refused it (with its Spanish `detail` and field
 * `errors`) or could not be reached (`unavailable`).
 */
export class AttributeGroupApiError extends Error {
  readonly kind: AttributeGroupApiErrorKind
  readonly detail: string | undefined
  readonly errors: ApiFieldError[]

  constructor(kind: AttributeGroupApiErrorKind, detail?: string, errors: ApiFieldError[] = []) {
    super(detail ?? 'No se pudo conectar con el servidor.')
    this.name = 'AttributeGroupApiError'
    this.kind = kind
    this.detail = detail
    this.errors = errors
  }
}

/**
 * Whether the development permission setting allows changing groups. Only decides which controls
 * the page shows; the backend still refuses changes without the permission (403).
 */
export function canChangeGroups(): boolean {
  const codes = import.meta.env.VITE_DEV_PERMISSIONS ?? ''
  return codes.split(',').some((code: string) => code.trim().toUpperCase() === CHANGE_PERMISSION)
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const url = buildApiUrl(path)
  if (!url) throw new AttributeGroupApiError('unavailable')
  let response: Response
  try {
    response = await fetch(url, init)
  } catch {
    throw new AttributeGroupApiError('unavailable')
  }
  if (response.ok) return response
  const kind = KIND_BY_STATUS[response.status]
  if (!kind) throw new AttributeGroupApiError('unavailable')
  const { detail, errors } = await readProblem(response)
  throw new AttributeGroupApiError(kind, detail, errors)
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

const GROUPS = '/api/v1/attribute-groups'

function groupPath(groupId: string): string {
  return `${GROUPS}/${encodeURIComponent(groupId)}`
}

/** The groups sorted by name (`GET /api/v1/attribute-groups`). */
export async function listGroups(): Promise<AttributeGroupSummary[]> {
  const response = await request(GROUPS, { headers: { Accept: 'application/json' } })
  const body = (await response.json()) as { items: AttributeGroupSummary[] }
  return body.items
}

/** One group with its members (`GET /api/v1/attribute-groups/{groupId}`). */
export async function getGroup(groupId: string): Promise<AttributeGroup> {
  const response = await request(groupPath(groupId), { headers: { Accept: 'application/json' } })
  return (await response.json()) as AttributeGroup
}

/** Creates an empty group (`POST /api/v1/attribute-groups`). */
export async function createGroup(group: AttributeGroupRequest): Promise<AttributeGroup> {
  const response = await request(GROUPS, change('POST', group))
  return (await response.json()) as AttributeGroup
}

/** Renames a group and replaces its description (`PUT /api/v1/attribute-groups/{groupId}`). */
export async function updateGroup(
  groupId: string,
  group: AttributeGroupRequest,
): Promise<AttributeGroup> {
  const response = await request(groupPath(groupId), change('PUT', group))
  return (await response.json()) as AttributeGroup
}

/** Deletes an empty group (`DELETE /api/v1/attribute-groups/{groupId}`). */
export async function deleteGroup(groupId: string): Promise<void> {
  await request(groupPath(groupId), change('DELETE'))
}

/** Adds existing attributes, all or nothing (`POST …/{groupId}/attributes`). */
export async function addAttributes(
  groupId: string,
  attributeIds: string[],
): Promise<AddAttributesResult> {
  const response = await request(
    `${groupPath(groupId)}/attributes`,
    change('POST', { attributeIds }),
  )
  return (await response.json()) as AddAttributesResult
}

/** Removes an attribute from the group; it stays in the system (`DELETE …/attributes/{id}`). */
export async function removeAttribute(groupId: string, attributeId: string): Promise<void> {
  await request(
    `${groupPath(groupId)}/attributes/${encodeURIComponent(attributeId)}`,
    change('DELETE'),
  )
}
