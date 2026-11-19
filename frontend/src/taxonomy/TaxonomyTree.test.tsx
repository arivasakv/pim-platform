import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { TaxonomyTree } from './TaxonomyTree'
import { buildTree } from './nodeTree'

const ROOTS = buildTree([
  {
    systemId: 'CLS000001',
    name: 'Ropa',
    parentId: null,
    level: 1,
    directAttributeCount: 3,
    childCount: 1,
  },
  {
    systemId: 'CLS000002',
    name: 'Ropa de hombre',
    parentId: 'CLS000001',
    level: 2,
    directAttributeCount: 1,
    childCount: 1,
  },
  {
    systemId: 'CLS000003',
    name: 'Camisas',
    parentId: 'CLS000002',
    level: 3,
    directAttributeCount: 0,
    childCount: 0,
  },
])

function renderTree(expanded: string[], selectedId?: string) {
  const onToggle = vi.fn()
  const onSelect = vi.fn()
  render(
    <TaxonomyTree
      roots={ROOTS}
      selectedId={selectedId}
      expanded={new Set(expanded)}
      onToggle={onToggle}
      onSelect={onSelect}
    />,
  )
  return { onToggle, onSelect }
}

describe('TaxonomyTree', () => {
  it('shows expanded children only, with direct counts', () => {
    renderTree(['CLS000001'])

    expect(screen.getByRole('button', { name: 'Ropa, 3 atributos directos' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Ropa de hombre, 1 atributo directo' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /^Camisas/ })).toBeNull()
    expect(
      screen.getByRole('button', { name: 'Ocultar hijos de Ropa' }).getAttribute('aria-expanded'),
    ).toBe('true')
    expect(
      screen
        .getByRole('button', { name: 'Mostrar hijos de Ropa de hombre' })
        .getAttribute('aria-expanded'),
    ).toBe('false')
  })

  it('reports toggles and selections and marks the chosen node', () => {
    const { onToggle, onSelect } = renderTree(['CLS000001', 'CLS000002'], 'CLS000003')

    const camisas = screen.getByRole('button', { name: 'Camisas, 0 atributos directos' })
    expect(camisas.getAttribute('aria-pressed')).toBe('true')
    expect(screen.queryByRole('button', { name: /hijos de Camisas/ })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Ocultar hijos de Ropa de hombre' }))
    expect(onToggle).toHaveBeenCalledWith('CLS000002')
    fireEvent.click(screen.getByRole('button', { name: 'Ropa, 3 atributos directos' }))
    expect(onSelect).toHaveBeenCalledWith('CLS000001')
  })

  it('shows a folder for nodes with children and a tag for nodes without them', () => {
    renderTree(['CLS000001', 'CLS000002'])

    const icon = (name: RegExp) =>
      screen.getByRole('button', { name }).querySelector('[data-icon]')?.getAttribute('data-icon')
    expect(icon(/^Ropa,/)).toBe('folder-open')
    expect(icon(/^Ropa de hombre,/)).toBe('folder-open')
    expect(icon(/^Camisas,/)).toBe('tag')
  })

  it('shows a closed folder for a collapsed node', () => {
    renderTree([])

    const ropa = screen.getByRole('button', { name: /^Ropa,/ })
    expect(ropa.querySelector('[data-icon]')?.getAttribute('data-icon')).toBe('folder')
    expect(ropa.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true')
  })
})
