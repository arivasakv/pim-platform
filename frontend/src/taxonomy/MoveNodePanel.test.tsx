import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { TaxonomyNode, TaxonomyNodeSummary } from '../api/taxonomyApi'
import { MoveNodePanel } from './MoveNodePanel'

function summary(systemId: string, name: string, parentId: string | null): TaxonomyNodeSummary {
  return { systemId, name, parentId, level: 1, directAttributeCount: 0, childCount: 0 }
}

/** The spec example of feature 011, plus three children under "Camisas formales". */
const ITEMS = [
  summary('CLS000001', 'Ropa', null),
  summary('CLS000002', 'Ropa de hombre', 'CLS000001'),
  summary('CLS000003', 'Camisas', 'CLS000002'),
  summary('CLS000004', 'Camisas formales', 'CLS000003'),
  summary('CLS000005', 'Pantalones', 'CLS000002'),
  summary('CLS000006', 'Ropa de mujer', 'CLS000001'),
  summary('CLS000007', 'Calzado', null),
]

function detail(systemId: string, name: string, path: [string, string][]): TaxonomyNode {
  const parent = path[path.length - 1]
  return {
    systemId,
    name,
    parentId: parent ? parent[0] : null,
    level: path.length + 1,
    path: path.map(([id, pathName]) => ({ systemId: id, name: pathName })),
    children: [],
    directAttributeCount: 0,
    effectiveAttributes: [],
    listAttributes: [],
    createdAt: '2026-11-19T10:00:00Z',
  }
}

const CAMISAS = detail('CLS000003', 'Camisas', [
  ['CLS000001', 'Ropa'],
  ['CLS000002', 'Ropa de hombre'],
])
const HOMBRE = detail('CLS000002', 'Ropa de hombre', [['CLS000001', 'Ropa']])
const ROPA = detail('CLS000001', 'Ropa', [])

function renderPanel(node: TaxonomyNode, items: TaxonomyNodeSummary[] = ITEMS, busy = false) {
  const onMove = vi.fn()
  const onCancel = vi.fn()
  render(
    <MoveNodePanel node={node} items={items} busy={busy} onCancel={onCancel} onMove={onMove} />,
  )
  return {
    onMove,
    onCancel,
    panel: within(screen.getByRole('group', { name: `Mover «${node.name}»` })),
  }
}

function optionNames(panel: ReturnType<typeof within>) {
  return within(panel.getByRole('list'))
    .getAllByRole('button')
    .map((option) => option.textContent)
}

describe('MoveNodePanel', () => {
  it('shows the current location, the branch and the destinations', () => {
    const { panel } = renderPanel(CAMISAS)

    expect(panel.getByText('Ubicación actual: Ropa › Ropa de hombre')).toBeTruthy()
    expect(panel.getByText('Se moverá junto con su nodo descendiente.')).toBeTruthy()
    expect(optionNames(panel)).toEqual([
      'Primer nivel (sin nodo padre)',
      'CalzadoCalzado · CLS000007',
      'RopaRopa · CLS000001',
      'PantalonesRopa › Ropa de hombre › Pantalones · CLS000005',
      'Ropa de mujerRopa › Ropa de mujer · CLS000006',
    ])
    expect(panel.getByRole('button', { name: 'Continuar' }).hasAttribute('disabled')).toBe(true)
  })

  it('counts several descendants and offers nothing of its own branch', () => {
    const items = [
      ...ITEMS,
      summary('CLS000008', 'Camisas de lino', 'CLS000004'),
      summary('CLS000009', 'Camisas de seda', 'CLS000004'),
    ]
    const { panel } = renderPanel(HOMBRE, items)

    expect(panel.getByText('Se moverá junto con sus 5 nodos descendientes.')).toBeTruthy()
    expect(optionNames(panel)).toEqual([
      'Primer nivel (sin nodo padre)',
      'CalzadoCalzado · CLS000007',
      'Ropa de mujerRopa › Ropa de mujer · CLS000006',
    ])
  })

  it('searches by name or system ID ignoring accents and case', () => {
    const { panel } = renderPanel(CAMISAS)
    const search = panel.getByLabelText('Buscar nodo destino')

    fireEvent.change(search, { target: { value: 'MUJER' } })
    expect(optionNames(panel)).toEqual(['Ropa de mujerRopa › Ropa de mujer · CLS000006'])

    fireEvent.change(search, { target: { value: 'cls000007' } })
    expect(optionNames(panel)).toEqual(['CalzadoCalzado · CLS000007'])

    fireEvent.change(search, { target: { value: 'primer nivel' } })
    expect(optionNames(panel)).toEqual(['Primer nivel (sin nodo padre)'])

    fireEvent.change(search, { target: { value: 'nada' } })
    expect(panel.getByText('Ningún nodo coincide con la búsqueda.')).toBeTruthy()
  })

  it('shows destinations 50 at a time', () => {
    const many = Array.from({ length: 60 }, (_, index) =>
      summary(`CLS1${String(index).padStart(5, '0')}`, `Nodo ${index}`, null),
    )
    const { panel } = renderPanel(CAMISAS, [...ITEMS, ...many])

    // 4 destinations of the example (Camisas' branch and parent left out) + 60 new ones.
    expect(panel.getByText('Mostrando 50 de 64 nodos.')).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Mostrar más' }))
    expect(panel.queryByRole('button', { name: 'Mostrar más' })).toBeNull()
  })

  it('confirms the move with the new location and the node count', () => {
    const { panel, onMove } = renderPanel(CAMISAS)

    const mujer = panel.getByRole('button', { name: /^Ropa de mujer/ })
    fireEvent.click(mujer)
    expect(mujer.getAttribute('aria-pressed')).toBe('true')
    fireEvent.click(panel.getByRole('button', { name: 'Continuar' }))

    expect(screen.getByText('¿Mover «Camisas» a «Ropa de mujer»?')).toBeTruthy()
    expect(document.activeElement?.textContent).toBe('¿Mover «Camisas» a «Ropa de mujer»?')
    expect(screen.getByText('Nueva ubicación: Ropa › Ropa de mujer › Camisas')).toBeTruthy()
    expect(screen.getByText('Se moverán 2 nodos: «Camisas» y 1 descendiente.')).toBeTruthy()
    expect(
      screen.getByText(
        'Dejará de heredar los atributos de su ubicación actual y heredará los de su nuevo destino.',
      ),
    ).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    const back = within(screen.getByRole('group', { name: 'Mover «Camisas»' }))
    expect(back.getByRole('button', { name: /^Ropa de mujer/ }).getAttribute('aria-pressed')).toBe(
      'true',
    )
    fireEvent.click(back.getByRole('button', { name: 'Continuar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, mover nodo' }))

    expect(onMove).toHaveBeenCalledWith('CLS000006')
  })

  it('moves to the top level', () => {
    const { panel, onMove } = renderPanel(HOMBRE)

    fireEvent.click(panel.getByRole('button', { name: 'Primer nivel (sin nodo padre)' }))
    fireEvent.click(panel.getByRole('button', { name: 'Continuar' }))

    expect(screen.getByText('¿Mover «Ropa de hombre» al primer nivel?')).toBeTruthy()
    expect(screen.getByText('Nueva ubicación: Ropa de hombre (primer nivel)')).toBeTruthy()
    expect(screen.getByText('Se moverán 4 nodos: «Ropa de hombre» y 3 descendientes.')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Sí, mover nodo' }))
    expect(onMove).toHaveBeenCalledWith(null)
  })

  it('does not offer the top level to a top-level node', () => {
    const { panel } = renderPanel(ROPA)

    expect(panel.getByText('Ubicación actual: Primer nivel')).toBeTruthy()
    expect(optionNames(panel)).toEqual(['CalzadoCalzado · CLS000007'])
  })

  it('says when there is nowhere to move', () => {
    const { panel, onCancel } = renderPanel(ROPA, ITEMS.slice(0, 6))

    expect(panel.getByText('No hay otro lugar al que mover este nodo.')).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Cancelar' }))
    expect(onCancel).toHaveBeenCalled()
  })

  it('disables the final button while saving', () => {
    const { panel } = renderPanel(CAMISAS, ITEMS, true)

    fireEvent.click(panel.getByRole('button', { name: /^Calzado/ }))
    expect(panel.getByRole('button', { name: 'Continuar' }).hasAttribute('disabled')).toBe(true)
  })
})
