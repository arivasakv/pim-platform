import { describe, expect, it } from 'vitest'
import type { TaxonomyNodeSummary } from '../api/taxonomyApi'
import { buildTree, descendantIds, moveDestinations, pathOf } from './nodeTree'

function node(systemId: string, name: string, parentId: string | null): TaxonomyNodeSummary {
  return { systemId, name, parentId, level: 1, directAttributeCount: 0, childCount: 0 }
}

/** The spec example of feature 011. */
const EXAMPLE = [
  node('CLS000001', 'Ropa', null),
  node('CLS000002', 'Ropa de hombre', 'CLS000001'),
  node('CLS000003', 'Camisas', 'CLS000002'),
  node('CLS000004', 'Camisas formales', 'cls000003'),
  node('CLS000005', 'Pantalones', 'CLS000002'),
  node('CLS000006', 'Ropa de mujer', 'CLS000001'),
  node('CLS000007', 'Calzado', null),
]

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

describe('descendantIds (feature 011)', () => {
  it('finds every node below, at any depth, ignoring letter case', () => {
    expect([...descendantIds(EXAMPLE, 'cls000002')].sort()).toEqual([
      'CLS000003',
      'CLS000004',
      'CLS000005',
    ])
    expect(descendantIds(EXAMPLE, 'CLS000004').size).toBe(0)
  })

  it('stops on an inconsistent list instead of looping', () => {
    const looped = [node('CLS000001', 'A', 'CLS000002'), node('CLS000002', 'B', 'CLS000001')]

    expect([...descendantIds(looped, 'CLS000001')]).toEqual(['CLS000002'])
  })
})

describe('pathOf (feature 011)', () => {
  it('lists the names from the top level down to the node', () => {
    expect(pathOf(EXAMPLE, 'CLS000004')).toEqual([
      'Ropa',
      'Ropa de hombre',
      'Camisas',
      'Camisas formales',
    ])
    expect(pathOf(EXAMPLE, 'CLS000007')).toEqual(['Calzado'])
    expect(pathOf(EXAMPLE, 'CLS999999')).toEqual([])
  })
})

describe('moveDestinations (feature 011)', () => {
  it('leaves out the node, its descendants and its current parent, sorted by path', () => {
    const destinations = moveDestinations(EXAMPLE, { systemId: 'CLS000003', parentId: 'CLS000002' })

    expect(destinations.map((destination) => destination.path.join(' › '))).toEqual([
      'Calzado',
      'Ropa',
      'Ropa › Ropa de hombre › Pantalones',
      'Ropa › Ropa de mujer',
    ])
  })

  it('offers no part of its own branch for a top-level node', () => {
    const destinations = moveDestinations(EXAMPLE, { systemId: 'CLS000001', parentId: null })

    expect(destinations.map((destination) => destination.name)).toEqual(['Calzado'])
  })
})
