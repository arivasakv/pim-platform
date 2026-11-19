import { buildApiUrl } from './apiConfig'
import { permissionHeaders, readProblem, type ApiFieldError } from './attributesApi'

// Shapes from specs/010-taxonomy-attribute-inheritance/contracts/taxonomy.openapi.yaml (v1.2.0,
// including feature 011's move and feature 012's list-value filters).
export type NodeReference = { systemId: string; name: string }

export type TaxonomyNodeSummary = {
  systemId: string
  name: string
  parentId?: string | null
  /** 1 for top-level nodes. */
  level: number
  directAttributeCount: number
  childCount: number
}

export type AssignmentType = 'DIRECT' | 'INHERITED'

export type EffectiveAttribute = {
  systemId: string
  displayName: string
  dataType: string
  assignment: AssignmentType
  /** The node itself for DIRECT; the nearest ancestor with a direct assignment for INHERITED. */
  origin: NodeReference
  /** Only for DIRECT entries that an ancestor also has directly. */
  alsoInheritedFrom?: NodeReference | null
}

/** Feature 012: one value of a list. */
export type ListValue = { id: string; label: string }

/** Feature 012: a filter on the node or an ancestor, with how many of its values are still in the list. */
export type FilterOnPath = { node: NodeReference; includedCount: number }

/** Feature 012: a list-of-values attribute that applies to the node. */
export type ListAttribute = {
  systemId: string
  displayName: string
  listOfValuesId: string
  listName: string
  selectionMode: 'SINGLE' | 'MULTIPLE'
  totalValues: number
  allowedCount: number
  hasOwnFilter: boolean
  /** Filters on the node and its ancestors, from the top level down. */
  filters: FilterOnPath[]
}

/** Feature 012: a value of the node's own filter that a filter above no longer allows. */
export type ExcludedValue = ListValue & { excludedBy: NodeReference }

/** Feature 012: everything about one list attribute at one node (`GET …/value-filters/{id}`). */
export type ValueFilterDetail = {
  node: NodeReference
  attribute: ListAttribute
  totalValues: number
  /** Values allowed by the filters above: what the node's own filter may include. */
  availableValues: ListValue[]
  /** The node's own filter, in list order; null when it has none. */
  includedValueIds: string[] | null
  excludedAboveValues: ExcludedValue[]
  /** Values allowed at the node, in list order. */
  allowedValues: ListValue[]
  filters: FilterOnPath[]
}

export type TaxonomyNode = {
  systemId: string
  name: string
  description?: string | null
  parentId?: string | null
  level: number
  /** Ancestors from the top level down to the parent. */
  path: NodeReference[]
  children: NodeReference[]
  directAttributeCount: number
  /** Inherited first (from the top down), then direct; each attribute once. */
  effectiveAttributes: EffectiveAttribute[]
  /** Feature 012: the effective list-of-values attributes with their allowed values. */
  listAttributes: ListAttribute[]
  createdAt: string
  updatedAt?: string | null
}

export type TaxonomyNodeRequest = {
  name: string
  description?: string | null
  parentId?: string | null
}

export type AssignAttributesResult = {
  node: TaxonomyNode
  added: string[]
  alreadyAssigned: string[]
}

/** Feature 011: the moved node and how many nodes moved with it (the node plus its descendants). */
export type MoveNodeResult = {
  node: TaxonomyNode
  movedNodeCount: number
}

export type TaxonomyApiErrorKind =
  'validation' | 'forbidden' | 'notFound' | 'conflict' | 'unprocessable' | 'unavailable'

const KIND_BY_STATUS: Record<number, TaxonomyApiErrorKind> = {
  400: 'validation',
  403: 'forbidden',
  404: 'notFound',
  409: 'conflict',
  422: 'unprocessable',
}

/** Permission code that allows changing the taxonomy (feature 010 FR-017, same as features 004–007). */
const CHANGE_PERMISSION = 'ATTRIBUTE_CREATE'

/**
 * Why a taxonomy request failed: the backend refused it (with its Spanish `detail` and field
 * `errors`) or could not be reached (`unavailable`).
 */
export class TaxonomyApiError extends Error {
  readonly kind: TaxonomyApiErrorKind
  readonly detail: string | undefined
  readonly errors: ApiFieldError[]

  constructor(kind: TaxonomyApiErrorKind, detail?: string, errors: ApiFieldError[] = []) {
    super(detail ?? 'No se pudo conectar con el servidor.')
    this.name = 'TaxonomyApiError'
    this.kind = kind
    this.detail = detail
    this.errors = errors
  }
}

/**
 * Whether the development permission setting allows changing the taxonomy. Only decides which
 * controls the page shows; the backend still refuses changes without the permission (403).
 */
export function canChangeTaxonomy(): boolean {
  const codes = import.meta.env.VITE_DEV_PERMISSIONS ?? ''
  return codes.split(',').some((code: string) => code.trim().toUpperCase() === CHANGE_PERMISSION)
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const url = buildApiUrl(path)
  if (!url) throw new TaxonomyApiError('unavailable')
  let response: Response
  try {
    response = await fetch(url, init)
  } catch {
    throw new TaxonomyApiError('unavailable')
  }
  if (response.ok) return response
  const kind = KIND_BY_STATUS[response.status]
  if (!kind) throw new TaxonomyApiError('unavailable')
  const { detail, errors } = await readProblem(response)
  throw new TaxonomyApiError(kind, detail, errors)
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

const NODES = '/api/v1/taxonomy-nodes'
const READ: RequestInit = { headers: { Accept: 'application/json' } }

function nodePath(nodeId: string): string {
  return `${NODES}/${encodeURIComponent(nodeId)}`
}

/** Every node as a flat list, sorted by name within each parent (`GET /api/v1/taxonomy-nodes`). */
export async function listNodes(): Promise<TaxonomyNodeSummary[]> {
  const response = await request(NODES, READ)
  const body = (await response.json()) as { items: TaxonomyNodeSummary[] }
  return body.items
}

/** One node with its path, children and effective attributes (`GET …/{nodeId}`). */
export async function getNode(nodeId: string): Promise<TaxonomyNode> {
  const response = await request(nodePath(nodeId), READ)
  return (await response.json()) as TaxonomyNode
}

/** Creates a top-level node (no `parentId`) or a child (`POST /api/v1/taxonomy-nodes`). */
export async function createNode(node: TaxonomyNodeRequest): Promise<TaxonomyNode> {
  const response = await request(NODES, change('POST', node))
  return (await response.json()) as TaxonomyNode
}

/** Renames a node and replaces its description (`PUT …/{nodeId}`). */
export async function updateNode(
  nodeId: string,
  node: { name: string; description?: string | null },
): Promise<TaxonomyNode> {
  const response = await request(nodePath(nodeId), change('PUT', node))
  return (await response.json()) as TaxonomyNode
}

/** Deletes a node with no children and no direct attributes (`DELETE …/{nodeId}`). */
export async function deleteNode(nodeId: string): Promise<void> {
  await request(nodePath(nodeId), change('DELETE'))
}

/**
 * Moves a node with its whole branch under another node, or to the top level with `null`
 * (`POST …/{nodeId}/move`, feature 011).
 */
export async function moveNode(nodeId: string, parentId: string | null): Promise<MoveNodeResult> {
  const response = await request(`${nodePath(nodeId)}/move`, change('POST', { parentId }))
  return (await response.json()) as MoveNodeResult
}

function filterPath(nodeId: string, attributeId: string): string {
  return `${nodePath(nodeId)}/value-filters/${encodeURIComponent(attributeId)}`
}

/** Feature 012: a list attribute's values at the node and its filters. */
export async function getValueFilter(
  nodeId: string,
  attributeId: string,
): Promise<ValueFilterDetail> {
  const response = await request(filterPath(nodeId, attributeId), READ)
  return (await response.json()) as ValueFilterDetail
}

/** Feature 012: defines or replaces the node's filter (`PUT …/value-filters/{attributeId}`). */
export async function saveValueFilter(
  nodeId: string,
  attributeId: string,
  valueIds: string[],
): Promise<ValueFilterDetail> {
  const response = await request(filterPath(nodeId, attributeId), change('PUT', { valueIds }))
  return (await response.json()) as ValueFilterDetail
}

/** Feature 012: removes the node's filter (`DELETE …/value-filters/{attributeId}`). */
export async function removeValueFilter(nodeId: string, attributeId: string): Promise<void> {
  await request(filterPath(nodeId, attributeId), change('DELETE'))
}

/** Assigns existing attributes directly to the node, all or nothing (`POST …/{nodeId}/attributes`). */
export async function assignAttributes(
  nodeId: string,
  attributeIds: string[],
): Promise<AssignAttributesResult> {
  const response = await request(`${nodePath(nodeId)}/attributes`, change('POST', { attributeIds }))
  return (await response.json()) as AssignAttributesResult
}

/** Removes a direct assignment (`DELETE …/{nodeId}/attributes/{attributeId}`). */
export async function removeAttribute(nodeId: string, attributeId: string): Promise<void> {
  await request(
    `${nodePath(nodeId)}/attributes/${encodeURIComponent(attributeId)}`,
    change('DELETE'),
  )
}
