import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ValueFilterDetail } from '../api/taxonomyApi'
import { ValueFilterPanel } from './ValueFilterPanel'
import { CAMISAS, COLORES, HOMBRE, detail, listAttribute, values } from './filterFixtures'

function renderPanel(data: ValueFilterDetail, busy = false) {
  const handlers = { onCancel: vi.fn(), onSave: vi.fn(), onRemove: vi.fn() }
  render(<ValueFilterPanel detail={data} busy={busy} {...handlers} />)
  return handlers
}

function column(name: string) {
  return within(screen.getByRole('region', { name }))
}

const available = () => column('Valores disponibles')
const included = () => column('Valores incluidos en el filtro')

function labelsIn(region: ReturnType<typeof within>) {
  const list = region.queryByRole('list')
  if (!list) return []
  return within(list)
    .getAllByRole('listitem')
    .map((item) => item.firstElementChild?.firstElementChild?.textContent)
}

describe('ValueFilterPanel', () => {
  it('moves values between the two columns and saves them in list order', () => {
    const { onSave, onCancel } = renderPanel(detail({}))

    expect(screen.getByRole('group', { name: 'Filtrar «Color» en «Ropa de hombre»' })).toBeTruthy()
    expect(labelsIn(available())).toHaveLength(7)
    expect(
      included().getByText(
        'Todavía no incluyes valores. Sin filtro se permiten los 7 valores disponibles.',
      ),
    ).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Guardar filtro' }).hasAttribute('disabled')).toBe(
      true,
    )
    expect(screen.queryByRole('button', { name: 'Quitar filtro' })).toBeNull()

    for (const label of ['Blanco', 'Azul', 'Negro', 'Rojo']) {
      fireEvent.click(available().getByRole('button', { name: `Incluir ${label}` }))
    }
    expect(labelsIn(included())).toEqual(['Rojo', 'Azul', 'Negro', 'Blanco'])
    expect(screen.getByText('4 valores incluidos')).toBeTruthy()
    fireEvent.click(included().getByRole('button', { name: 'Quitar Rojo' }))
    expect(screen.getByText('3 valores incluidos')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Guardar filtro' }))
    expect(onSave).toHaveBeenCalledWith(['0002', '0004', '0005'])

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCancel).toHaveBeenCalled()
  })

  it('searches each column by label or ID and moves all matches at once', () => {
    renderPanel(detail({}))

    fireEvent.change(available().getByLabelText('Buscar en disponibles'), {
      target: { value: 'ROSA' },
    })
    expect(labelsIn(available())).toEqual(['Rosa'])
    fireEvent.change(available().getByLabelText('Buscar en disponibles'), {
      target: { value: '000' },
    })
    fireEvent.click(available().getByRole('button', { name: 'Incluir los mostrados (7)' }))
    expect(screen.getByText('7 valores incluidos')).toBeTruthy()
    expect(available().getByText('No quedan valores por incluir.')).toBeTruthy()

    fireEvent.change(included().getByLabelText('Buscar en incluidos'), {
      target: { value: 'verde' },
    })
    fireEvent.click(included().getByRole('button', { name: 'Quitar los mostrados (1)' }))
    expect(screen.getByText('6 valores incluidos')).toBeTruthy()
    fireEvent.change(included().getByLabelText('Buscar en incluidos'), {
      target: { value: 'nada' },
    })
    expect(included().getByText('Ningún valor coincide con la búsqueda.')).toBeTruthy()
  })

  it('shows 50 rows at a time', () => {
    const many = Array.from({ length: 120 }, (_, index) => ({
      id: `V${index}`,
      label: `Valor ${index}`,
    }))
    renderPanel(detail({ availableValues: many, allowedValues: many, totalValues: 120 }))

    expect(available().getByText('Mostrando 50 de 120.')).toBeTruthy()
    fireEvent.click(available().getByRole('button', { name: 'Mostrar más' }))
    expect(available().getByText('Mostrando 100 de 120.')).toBeTruthy()
  })

  it('offers only the values allowed above and marks own values excluded above', () => {
    const { onSave } = renderPanel(
      detail({
        node: CAMISAS,
        attribute: listAttribute(
          [
            { node: HOMBRE, includedCount: 2 },
            { node: CAMISAS, includedCount: 2 },
          ],
          1,
          true,
        ),
        availableValues: values('0002', '0004'),
        includedValueIds: ['0002', '0005'],
        excludedAboveValues: [{ id: '0005', label: 'Blanco', excludedBy: HOMBRE }],
        allowedValues: values('0002'),
        filters: [
          { node: HOMBRE, includedCount: 2 },
          { node: CAMISAS, includedCount: 2 },
        ],
      }),
    )

    expect(
      screen.getByText('Arriba se permiten 2 de 7 valores (filtro de «Ropa de hombre»).'),
    ).toBeTruthy()
    expect(labelsIn(available())).toEqual(['Negro'])
    expect(labelsIn(included())).toEqual(['Azul', 'Blanco'])
    expect(included().getByText('Excluido por «Ropa de hombre»')).toBeTruthy()
    expect(included().queryByRole('button', { name: 'Quitar Blanco' })).toBeNull()
    expect(
      screen.getByText(
        '1 valor de este filtro ya no está permitido arriba y no se aplica. Se quitará al guardar.',
      ),
    ).toBeTruthy()
    expect(screen.getByText('1 valor incluido')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Guardar filtro' }))
    expect(onSave).toHaveBeenCalledWith(['0002'])
  })

  it('confirms before removing the filter', () => {
    const { onRemove } = renderPanel(
      detail({
        includedValueIds: ['0001'],
        attribute: listAttribute([{ node: HOMBRE, includedCount: 1 }], 1, true),
      }),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Quitar filtro' }))
    expect(
      screen.getByText(
        '¿Quitar el filtro de «Color» en «Ropa de hombre»? Este nodo y sus niveles inferiores permitirán los valores heredados de arriba.',
      ),
    ).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.getByRole('group', { name: 'Filtrar «Color» en «Ropa de hombre»' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Quitar filtro' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, quitar filtro' }))
    expect(onRemove).toHaveBeenCalled()
  })

  it('keeps every value of a list available when nothing is filtered above', () => {
    renderPanel(detail({}))

    expect(screen.queryByText(/^Arriba se permiten/)).toBeNull()
    expect(labelsIn(available())).toEqual(COLORES.map((value) => value.label))
  })
})
