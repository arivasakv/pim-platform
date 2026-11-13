import { describe, expect, it } from 'vitest'
import type { TaxonomyNodeSummary } from '../api/taxonomyApi'
import { buildTree } from './nodeTree'

function node(systemId: string, name: string, parentId: string | null): TaxonomyNodeSummary {
  return { systemId, name, parentId, level: 1, directAttributeCount: 0, childCount: 0 }
}

describe('buildTree', () => {
  it('nests nodes by parent keeping the API order', () => {
    const tree = buildTree([
      node('CLS000003', 'Camisas', 'CLS000002'),
      node('CLS000004', 'Pantalones', 'cls000002'),
      node('CLS000001', 'Ropa', null),
      node('CLS000002', 'Ropa de hombre', 'CLS000001'),
    ])

    expect(tree.map((root) => root.name)).toEqual(['Ropa'])
    expect(tree[0].children.map((child) => child.name)).toEqual(['Ropa de hombre'])
    expect(tree[0].children[0].children.map((child) => child.name)).toEqual([
      'Camisas',
      'Pantalones',
    ])
  })

  it('shows a node with an unknown parent at the top level', () => {
    const tree = buildTree([
      node('CLS000002', 'Huérfano', 'CLS999999'),
      node('CLS000001', 'Ropa', null),
    ])

    expect(tree.map((root) => root.name)).toEqual(['Huérfano', 'Ropa'])
  })
})
