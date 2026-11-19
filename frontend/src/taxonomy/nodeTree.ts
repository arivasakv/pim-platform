import type { TaxonomyNodeSummary } from '../api/taxonomyApi'

/** A node of the flat API list with its children nested (feature 010, display only). */
export type TreeNode = TaxonomyNodeSummary & { children: TreeNode[] }

/** A node the move panel offers as destination, with its path for the label (feature 011). */
export type MoveDestination = TaxonomyNodeSummary & { path: string[] }

const key = (systemId: string) => systemId.toUpperCase()

/**
 * Nests the flat node list by `parentId`, keeping the API's order (by name within each parent). A
 * node whose parent is not in the list is shown at the top level, so nothing disappears.
 */
export function buildTree(items: TaxonomyNodeSummary[]): TreeNode[] {
  const byId = new Map<string, TreeNode>()
  for (const item of items) byId.set(key(item.systemId), { ...item, children: [] })
  const roots: TreeNode[] = []
  for (const item of items) {
    const node = byId.get(key(item.systemId))!
    const parent = item.parentId ? byId.get(key(item.parentId)) : undefined
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

/**
 * The IDs (upper-cased) of every node below `nodeId`, at any depth (feature 011, display only).
 * A visited set stops the walk if the list were ever inconsistent.
 */
export function descendantIds(items: TaxonomyNodeSummary[], nodeId: string): Set<string> {
  const children = new Map<string, string[]>()
  for (const item of items) {
    if (!item.parentId) continue
    const parent = key(item.parentId)
    children.set(parent, [...(children.get(parent) ?? []), key(item.systemId)])
  }
  const start = key(nodeId)
  const found = new Set<string>()
  const pending = [...(children.get(start) ?? [])]
  while (pending.length > 0) {
    const current = pending.shift()!
    if (current === start || found.has(current)) continue
    found.add(current)
    pending.push(...(children.get(current) ?? []))
  }
  return found
}

/** The names from the top level down to the node, the node included (feature 011, display only). */
export function pathOf(items: TaxonomyNodeSummary[], nodeId: string): string[] {
  const byId = new Map(items.map((item) => [key(item.systemId), item]))
  const names: string[] = []
  const seen = new Set<string>()
  let current = byId.get(key(nodeId))
  while (current && !seen.has(key(current.systemId))) {
    seen.add(key(current.systemId))
    names.unshift(current.name)
    current = current.parentId ? byId.get(key(current.parentId)) : undefined
  }
  return names
}

/**
 * Where the node can be moved (feature 011 FR-005, FR-006; display only, the backend decides):
 * every node except itself, its descendants and its current parent, sorted by path.
 */
export function moveDestinations(
  items: TaxonomyNodeSummary[],
  node: { systemId: string; parentId?: string | null },
): MoveDestination[] {
  const excluded = descendantIds(items, node.systemId)
  excluded.add(key(node.systemId))
  if (node.parentId) excluded.add(key(node.parentId))
  return items
    .filter((item) => !excluded.has(key(item.systemId)))
    .map((item) => ({ ...item, path: pathOf(items, item.systemId) }))
    .sort((a, b) => a.path.join(' › ').localeCompare(b.path.join(' › '), 'es'))
}
