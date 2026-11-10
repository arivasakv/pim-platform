import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CheckboxField } from './CheckboxField'
import { SelectField } from './SelectField'
import { TextAreaField } from './TextAreaField'

const OPTIONS = [
  { code: 'TEXT', label: 'Texto' },
  { code: 'NUMERIC', label: 'Numérico' },
]

describe('SelectField', () => {
  it('renders a labelled select with a placeholder and the options', () => {
    render(
      <SelectField
        id="tipo"
        label="Tipo de dato"
        options={OPTIONS}
        value=""
        placeholder="Elige un tipo"
        onChange={() => {}}
      />,
    )
    const select = screen.getByLabelText<HTMLSelectElement>('Tipo de dato')

    expect(select.tagName).toBe('SELECT')
    expect(Array.from(select.options).map((option) => option.textContent)).toEqual([
      'Elige un tipo',
      'Texto',
      'Numérico',
    ])
  })

  it('reports the chosen code', () => {
    const onChange = vi.fn()
    render(<SelectField id="tipo" label="Tipo" options={OPTIONS} value="" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'NUMERIC' } })

    expect(onChange).toHaveBeenCalledWith('NUMERIC')
  })

  it('shows a hint and an error like the text field', () => {
    render(
      <SelectField
        id="tipo"
        label="Tipo"
        options={OPTIONS}
        value=""
        hint="Se define al crear"
        error="Elige un tipo de dato."
        onChange={() => {}}
      />,
    )
    const select = screen.getByLabelText('Tipo')

    expect(select.getAttribute('aria-invalid')).toBe('true')
    expect(select.getAttribute('aria-describedby')).toBe('tipo-ayuda tipo-error')
    expect(screen.getByText('Se define al crear')).toBeTruthy()
    expect(screen.getByText('Elige un tipo de dato.')).toBeTruthy()
  })
})

describe('CheckboxField', () => {
  it('renders a labelled checkbox that toggles', () => {
    const onChange = vi.fn()
    render(
      <CheckboxField
        id="html"
        label="Texto enriquecido (HTML)"
        checked={false}
        onChange={onChange}
      />,
    )
    const checkbox = screen.getByLabelText<HTMLInputElement>('Texto enriquecido (HTML)')

    expect(checkbox.type).toBe('checkbox')
    fireEvent.click(checkbox)
    expect(onChange).toHaveBeenCalledWith(true)
  })
})

describe('TextAreaField', () => {
  it('renders a labelled text area with a maximum length, a hint and an error', () => {
    render(
      <TextAreaField
        id="descripcion"
        label="Descripción"
        maxLength={1000}
        hint="Opcional"
        error="Demasiado larga."
        value=""
        onChange={() => {}}
      />,
    )
    const textArea = screen.getByLabelText<HTMLTextAreaElement>('Descripción')

    expect(textArea.tagName).toBe('TEXTAREA')
    expect(textArea.maxLength).toBe(1000)
    expect(textArea.getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByText('Opcional')).toBeTruthy()
    expect(screen.getByText('Demasiado larga.')).toBeTruthy()
  })
})
