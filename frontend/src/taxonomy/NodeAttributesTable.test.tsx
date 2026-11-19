import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { EffectiveAttribute, TaxonomyNode } from '../api/taxonomyApi'
import { NodeAttributesTable } from './NodeAttributesTable'

const ROPA = { systemId: 'CLS000001', name: 'Ropa' }
const HOMBRE = { systemId: 'CLS000002', name: 'Ropa de hombre' }
const CAMISAS = { systemId: 'CLS000003', name: 'Camisas' }

function attribute(
  systemId: string,
  displayName: string,
  assignment: 'DIRECT' | 'INHERITED',
  origin: { systemId: string; name: string },
  alsoInheritedFrom?: { systemId: string; name: string },
): EffectiveAttribute {
  return { systemId, displayName, dataType: 'TEXT', assignment, origin, alsoInheritedFrom }
}

function camisas(effectiveAttributes: EffectiveAttribute[], level = 3): TaxonomyNode {
  return {
    ...CAMISAS,
    level,
    path: [ROPA, HOMBRE],
    children: [],
    directAttributeCount: effectiveAttributes.filter((entry) => entry.assignment === 'DIRECT')
      .length,
    effectiveAttributes,
    listAttributes: [],
    createdAt: '2026-11-12T10:00:00Z',
  }
}

const EXAMPLE = [
  attribute('ATR000001', 'Color', 'INHERITED', ROPA),
  attribute('ATR000002', 'Material', 'INHERITED', ROPA),
  attribute('ATR000003', 'Marca', 'INHERITED', ROPA),
  attribute('ATR000004', 'Género', 'INHERITED', HOMBRE),
  attribute('ATR000005', 'Largo de manga', 'DIRECT', CAMISAS),
]

function renderTable(node: TaxonomyNode, canChange = true) {
  const onOpenNode = vi.fn()
  const onRemove = vi.fn()
  render(
    <NodeAttributesTable
      node={node}
      typeLabel={(code) => (code === 'TEXT' ? 'Texto' : code)}
      canChange={canChange}
      busy={false}
      onOpenNode={onOpenNode}
      onRemove={onRemove}
    />,
  )
  return { onOpenNode, onRemove }
}

function rows() {
  const table = screen.getByRole('table', { name: 'Atributos de «Camisas»' })
  return within(table).getAllByRole('row').slice(1)
}

describe('NodeAttributesTable', () => {
  it('lists every attribute with type, assignment badge and origin', () => {
    renderTable(camisas(EXAMPLE))

    expect(screen.getByRole('heading', { level: 3, name: 'Atributos del nodo' })).toBeTruthy()
    expect(screen.getByText('1 directo · 4 heredados')).toBeTruthy()
    expect(
      rows().map((row) =>
        within(row)
          .getAllByRole('cell')
          .slice(0, 4)
          .map((cell) => cell.textContent),
      ),
    ).toEqual([
      ['ColorATR000001', 'Texto', 'Heredado', 'Ropa'],
      ['MaterialATR000002', 'Texto', 'Heredado', 'Ropa'],
      ['MarcaATR000003', 'Texto', 'Heredado', 'Ropa'],
      ['GéneroATR000004', 'Texto', 'Heredado', 'Ropa de hombre'],
      ['Largo de mangaATR000005', 'Texto', 'Directo', 'Este nodo'],
    ])
    expect(screen.getByText('Los atributos heredados se quitan en su nodo de origen.')).toBeTruthy()
  })

  it('opens origins and removes only direct attributes', () => {
    const { onOpenNode, onRemove } = renderTable(camisas(EXAMPLE))

    fireEvent.click(screen.getAllByRole('button', { name: 'Ir al nodo Ropa' })[0])
    expect(onOpenNode).toHaveBeenCalledWith('CLS000001')
    expect(screen.getAllByRole('button', { name: /^Quitar/ })).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: 'Quitar Largo de manga de este nodo' }))
    expect(onRemove).toHaveBeenCalledWith(EXAMPLE[4])
  })

  it('says when a direct attribute is also inherited', () => {
    renderTable(camisas([attribute('ATR000004', 'Género', 'DIRECT', CAMISAS, HOMBRE)]))

    expect(screen.getByText('También se hereda de «Ropa de hombre»')).toBeTruthy()
    expect(screen.getByText('1 directo · 0 heredados')).toBeTruthy()
  })

  it('explains a node with only inherited attributes', () => {
    renderTable(camisas(EXAMPLE.slice(0, 4)))

    expect(
      screen.getByText(
        'Este nodo no tiene atributos asignados directamente; hereda 4 de sus niveles superiores.',
      ),
    ).toBeTruthy()
  })

  it('explains nodes without attributes', () => {
    renderTable(camisas([], 1))
    expect(screen.getByText('Este nodo no tiene atributos todavía.')).toBeTruthy()
  })

  it('explains a lower node without attributes', () => {
    renderTable(camisas([], 3))
    expect(
      screen.getByText(
        'Este nodo no tiene atributos todavía; tampoco hereda atributos de sus niveles superiores.',
      ),
    ).toBeTruthy()
  })

  it('shows no change controls without the permission', () => {
    renderTable(camisas(EXAMPLE), false)

    expect(screen.queryByRole('button', { name: /^Quitar/ })).toBeNull()
    expect(screen.queryByText('Los atributos heredados se quitan en su nodo de origen.')).toBeNull()
    expect(screen.getAllByRole('columnheader')).toHaveLength(4)
  })
})
