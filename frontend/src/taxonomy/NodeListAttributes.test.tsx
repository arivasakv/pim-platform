import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ListAttribute, TaxonomyNode } from '../api/taxonomyApi'
import { NodeListAttributes } from './NodeListAttributes'
import { CAMISAS, HOMBRE, ROPA, listAttribute, nodeWith } from './filterFixtures'

function renderSection(node: TaxonomyNode, canChange = true) {
  const handlers = { onFilter: vi.fn(), onPreview: vi.fn(), onOpenNode: vi.fn() }
  const result = render(
    <NodeListAttributes node={node} canChange={canChange} busy={false} {...handlers} />,
  )
  return { ...handlers, container: result.container }
}

function row() {
  const table = screen.getByRole('table', { name: /^Lista de Valores \(LOV's\) de «/ })
  return within(within(table).getAllByRole('row')[1])
}

describe('NodeListAttributes', () => {
  it('lists list attributes with allowed counts and no filter', () => {
    const { onFilter, onPreview } = renderSection(nodeWith(HOMBRE, [listAttribute([], 7, false)]))

    expect(
      screen.getByRole('heading', { name: "Lista de Valores (LOV's) en este nodo" }),
    ).toBeTruthy()
    expect(
      screen.getByText(
        'Los filtros limitan los valores permitidos en este nodo y en todos sus niveles inferiores.',
      ),
    ).toBeTruthy()
    expect(row().getByText('Lista «Colores» · ATR000001')).toBeTruthy()
    expect(row().getByText('7 de 7')).toBeTruthy()
    expect(row().getByText('Sin filtro')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Filtrar valores de Color' }))
    fireEvent.click(screen.getByRole('button', { name: 'Vista previa de Color' }))
    expect(onFilter).toHaveBeenCalledWith(expect.objectContaining({ systemId: 'ATR000001' }))
    expect(onPreview).toHaveBeenCalled()
  })

  it('names where each restriction comes from', () => {
    const own: ListAttribute = listAttribute([{ node: HOMBRE, includedCount: 4 }], 4, true)
    const { unmount } = render(
      <NodeListAttributes
        node={nodeWith(HOMBRE, [own])}
        canChange
        busy={false}
        onFilter={vi.fn()}
        onPreview={vi.fn()}
        onOpenNode={vi.fn()}
      />,
    )
    expect(row().getByText('4 de 7')).toBeTruthy()
    expect(row().getByText('Filtro en este nodo')).toBeTruthy()
    unmount()

    const { onOpenNode } = renderSection(
      nodeWith(CAMISAS, [listAttribute([{ node: HOMBRE, includedCount: 4 }], 4, false)]),
    )
    expect(
      row().getByText(
        (_, element) =>
          element?.textContent === 'Filtro heredado de «Ropa de hombre»' &&
          element.tagName === 'SPAN',
      ),
    ).toBeTruthy()
    fireEvent.click(row().getByRole('button', { name: 'Ir al nodo Ropa de hombre' }))
    expect(onOpenNode).toHaveBeenCalledWith('CLS000002')
  })

  it('shows an own filter limited above and a node with no value allowed', () => {
    renderSection(
      nodeWith(CAMISAS, [
        listAttribute(
          [
            { node: ROPA, includedCount: 3 },
            { node: HOMBRE, includedCount: 1 },
            { node: CAMISAS, includedCount: 1 },
          ],
          0,
          true,
        ),
      ]),
    )

    expect(row().getByText('Filtro en este nodo')).toBeTruthy()
    expect(row().getByText('También limitado por «Ropa de hombre»')).toBeTruthy()
    expect(row().getByText('Ningún valor permitido')).toBeTruthy()
  })

  it('offers only the preview without the permission and nothing without list attributes', () => {
    const { container } = renderSection(nodeWith(HOMBRE, [listAttribute([], 7, false)]), false)

    expect(screen.queryByRole('button', { name: 'Filtrar valores de Color' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Vista previa de Color' })).toBeTruthy()
    container.remove()

    renderSection(nodeWith(HOMBRE, []))
    expect(
      screen.queryByRole('heading', { name: "Lista de Valores (LOV's) en este nodo" }),
    ).toBeNull()
  })
})
