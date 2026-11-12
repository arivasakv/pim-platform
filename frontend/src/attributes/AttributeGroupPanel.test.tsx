import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AttributeGroupPanel } from './AttributeGroupPanel'
import { ALL, type BrowseEntry, type GroupsState } from './attributeBrowsing'

const GROUPS: GroupsState = {
  status: 'ready',
  groups: [
    { id: 'g2', name: 'Compra Ventas', attributeCount: 2 },
    { id: 'g1', name: 'Logísticos', attributeCount: 1 },
  ],
}

function renderPanel(
  props: Partial<{
    groupsState: GroupsState
    entry: BrowseEntry
    entryLabel: string
    onSelect: (entry: BrowseEntry) => void
    onRetry: () => void
  }> = {},
) {
  const onSelect = props.onSelect ?? vi.fn()
  const onRetry = props.onRetry ?? vi.fn()
  render(
    <AttributeGroupPanel
      groupsState={props.groupsState ?? GROUPS}
      totalCount={4}
      ungroupedCount={1}
      entry={props.entry ?? ALL}
      entryLabel={props.entryLabel ?? 'Todos los atributos'}
      onSelect={onSelect}
      onRetry={onRetry}
    />,
  )
  return { onSelect, onRetry, panel: within(screen.getByRole('region', { name: 'Grupos' })) }
}

function entryNames(panel: ReturnType<typeof within>) {
  return within(panel.getByRole('list'))
    .getAllByRole('button')
    .map((button) => button.getAttribute('aria-label'))
}

describe('AttributeGroupPanel', () => {
  it('lists every attribute, those in no group and every group with its count', () => {
    const { panel } = renderPanel()

    expect(entryNames(panel)).toEqual([
      'Todos los atributos, 4 atributos',
      'Sin grupo, 1 atributo',
      'Compra Ventas, 2 atributos',
      'Logísticos, 1 atributo',
    ])
    const all = panel.getByRole('button', { name: 'Todos los atributos, 4 atributos' })
    expect(all.getAttribute('aria-pressed')).toBe('true')
    expect(
      panel.getByRole('button', { name: 'Logísticos, 1 atributo' }).getAttribute('aria-pressed'),
    ).toBe('false')
    const link = panel.getByRole('link', { name: 'Administrar grupos' })
    expect(link.getAttribute('href')).toBe('#/grupos-de-atributos')
  })

  it('reports the chosen entry and marks it', () => {
    const { onSelect, panel } = renderPanel({
      entry: { kind: 'group', groupId: 'g1' },
      entryLabel: 'Logísticos',
    })

    expect(
      panel.getByRole('button', { name: 'Logísticos, 1 atributo' }).getAttribute('aria-pressed'),
    ).toBe('true')
    fireEvent.click(panel.getByRole('button', { name: 'Sin grupo, 1 atributo' }))
    expect(onSelect).toHaveBeenCalledWith({ kind: 'ungrouped' })
    fireEvent.click(panel.getByRole('button', { name: 'Compra Ventas, 2 atributos' }))
    expect(onSelect).toHaveBeenCalledWith({ kind: 'group', groupId: 'g2' })
  })

  it('opens and closes on narrow screens with a toggle naming the chosen entry', () => {
    const { panel } = renderPanel({
      entry: { kind: 'group', groupId: 'g2' },
      entryLabel: 'Compra Ventas',
    })
    const toggle = panel.getByRole('button', { name: 'Grupo: Compra Ventas (2)' })

    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(toggle)
    expect(toggle.getAttribute('aria-expanded')).toBe('true')
    fireEvent.click(panel.getByRole('button', { name: 'Todos los atributos, 4 atributos' }))
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
  })

  it('finds groups by name while keeping the fixed entries', () => {
    const { panel } = renderPanel()

    fireEvent.change(panel.getByLabelText('Buscar grupo'), { target: { value: 'LOGISTICOS' } })
    expect(entryNames(panel)).toEqual([
      'Todos los atributos, 4 atributos',
      'Sin grupo, 1 atributo',
      'Logísticos, 1 atributo',
    ])

    fireEvent.change(panel.getByLabelText('Buscar grupo'), { target: { value: 'nada' } })
    expect(entryNames(panel)).toHaveLength(2)
    expect(panel.getByText('Ningún grupo coincide con «nada».')).toBeTruthy()
  })

  it('shows loading, failure with retry, and no groups', () => {
    const { panel, onRetry } = renderPanel({ groupsState: { status: 'error' } })
    expect(panel.getByText('No se pudieron cargar los grupos.')).toBeTruthy()
    expect(entryNames(panel)).toHaveLength(2)
    expect(panel.queryByLabelText('Buscar grupo')).toBeNull()
    fireEvent.click(panel.getByRole('button', { name: 'Reintentar' }))
    expect(onRetry).toHaveBeenCalled()
  })

  it('says it is loading the groups', () => {
    const { panel } = renderPanel({ groupsState: { status: 'loading' } })
    expect(panel.getByText('Cargando grupos…')).toBeTruthy()
  })

  it('links to the groups page when there are no groups', () => {
    const { panel } = renderPanel({ groupsState: { status: 'ready', groups: [] } })
    expect(panel.getByText('Todavía no hay grupos de atributos.')).toBeTruthy()
    const link = panel.getByRole('link', { name: 'Crear grupos en «Grupo de Atributos»' })
    expect(link.getAttribute('href')).toBe('#/grupos-de-atributos')
  })
})
