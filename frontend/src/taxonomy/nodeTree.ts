import type { TaxonomyNodeSummary } from '../api/taxonomyApi'

/** A node of the flat API list with its children nested (feature 010, display only). */
export type TreeNode = TaxonomyNodeSummary & { children: TreeNode[] }

/**
 * Nests the flat node list by `parentId`, keeping the API's order (by name within each parent). A
 * node whose parent is not in the list is shown at the top level, so nothing disappears.
 */
export function buildTree(items: TaxonomyNodeSummary[]): TreeNode[] {
  const byId = new Map<string, TreeNode>()
  for (const item of items) byId.set(item.systemId.toUpperCase(), { ...item, children: [] })
  const roots: TreeNode[] = []
  for (const item of items) {
    const node = byId.get(item.systemId.toUpperCase())!
    const parent = item.parentId ? byId.get(item.parentId.toUpperCase()) : undefined
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}
