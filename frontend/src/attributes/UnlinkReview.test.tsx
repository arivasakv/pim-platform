import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ListUnlinkReview } from '../api/attributesApi'
import { UnlinkReview } from './UnlinkReview'

const REVIEW: ListUnlinkReview = {
  attributeId: 'ATR000001',
  attributeName: 'Color',
  listOfValuesId: 'LOV000001',
  listOfValuesName: 'Colores',
  filteredNodes: [
    { id: 'CLS000002', name: 'Camisas' },
    { id: 'CLS000001', name: 'Ropa de hombre' },
  ],
  reviewToken: 'abc',
}

describe('UnlinkReview (feature 014 FR-009)', () => {
  it('names the list and every node that loses its filters', () => {
    render(<UnlinkReview review={REVIEW} />)

    const box = within(screen.getByRole('region', { name: 'Qué cambiará' }))
    expect(box.getByText('Se desvinculará la lista «Colores».')).toBeTruthy()
    expect(
      box.getByText(
        'Se eliminarán los filtros de valores de este atributo en 2 nodos de taxonomía:',
      ),
    ).toBeTruthy()
    expect(box.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Camisas',
      'Ropa de hombre',
    ])
  })

  it('uses the singular for one node', () => {
    render(<UnlinkReview review={{ ...REVIEW, filteredNodes: [REVIEW.filteredNodes[0]] }} />)

    expect(
      screen.getByText(
        'Se eliminarán los filtros de valores de este atributo en 1 nodo de taxonomía:',
      ),
    ).toBeTruthy()
  })

  it('says when there are no filters and falls back to the list ID', () => {
    render(<UnlinkReview review={{ ...REVIEW, listOfValuesName: null, filteredNodes: [] }} />)

    expect(screen.getByText('Se desvinculará la lista «LOV000001».')).toBeTruthy()
    expect(
      screen.getByText('Este atributo no tiene filtros de valores en la taxonomía.'),
    ).toBeTruthy()
    expect(screen.queryByRole('list')).toBeNull()
  })
})
