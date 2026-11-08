import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ComponentGalleryPage } from './ComponentGalleryPage'

const GROUPS = ['Botones', 'Campos de texto', 'Tarjetas', 'Títulos', 'Estados y notificaciones']

describe('ComponentGalleryPage', () => {
  it('shows the page title', () => {
    render(<ComponentGalleryPage />)
    expect(screen.getByRole('heading', { level: 1, name: 'Componentes' })).toBeTruthy()
  })

  it.each(GROUPS)('shows the "%s" group', (group) => {
    render(<ComponentGalleryPage />)
    expect(screen.getByRole('heading', { level: 2, name: group })).toBeTruthy()
  })

  it('shows a disabled button and a field with an error', () => {
    render(<ComponentGalleryPage />)
    const buttons = screen.getAllByRole<HTMLButtonElement>('button')

    expect(buttons.some((button) => button.disabled)).toBe(true)
    expect(screen.getByText('Campo obligatorio')).toBeTruthy()
  })
})
