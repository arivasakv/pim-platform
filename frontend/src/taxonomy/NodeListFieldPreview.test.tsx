import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NodeListFieldPreview } from './NodeListFieldPreview'
import { CAMISAS, HOMBRE, detail, listAttribute, values } from './filterFixtures'

describe('NodeListFieldPreview', () => {
  it('shows a select with only the allowed values for single selection', () => {
    const onClose = vi.fn()
    render(
      <NodeListFieldPreview
        detail={detail({ node: CAMISAS, allowedValues: values('0002', '0005') })}
        onClose={onClose}
      />,
    )

    expect(screen.getByText('Vista previa de «Color» en «Camisas»')).toBeTruthy()
    expect(
      screen.getByText(
        'Así se verá el campo al capturar un producto de este nodo. Lo que elijas aquí no se guarda.',
      ),
    ).toBeTruthy()
    const select = screen.getByLabelText<HTMLSelectElement>('Color')
    const options = within(select)
      .getAllByRole('option')
      .map((option) => option.textContent)
    expect(options).toEqual(['Selecciona una opción', 'Azul', 'Blanco'])

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar vista previa' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('shows checkboxes for multiple selection and the first 50 values with the summary', () => {
    const many = Array.from({ length: 60 }, (_, index) => ({
      id: `V${index}`,
      label: `Valor ${index}`,
    }))
    render(
      <NodeListFieldPreview
        detail={detail({
          attribute: listAttribute([], 60, false, 'MULTIPLE'),
          allowedValues: many,
        })}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getAllByRole('checkbox')).toHaveLength(50)
    expect(screen.getByText('Vista previa con los primeros 50 de 60 valores.')).toBeTruthy()
  })

  it('warns when no value is allowed', () => {
    render(
      <NodeListFieldPreview
        detail={detail({
          node: CAMISAS,
          allowedValues: [],
          filters: [
            { node: HOMBRE, includedCount: 1 },
            { node: CAMISAS, includedCount: 1 },
          ],
        })}
        onClose={vi.fn()}
      />,
    )

    expect(
      screen.getByText(
        'Ningún valor está permitido en este nodo. Revisa los filtros de «Ropa de hombre», «Camisas».',
      ),
    ).toBeTruthy()
    expect(screen.queryByRole('combobox')).toBeNull()
  })
})
