import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'
import { TextField } from './TextField'

describe('Button', () => {
  it('defaults to type "button"', () => {
    render(<Button>Guardar</Button>)
    expect(screen.getByRole('button', { name: 'Guardar' }).getAttribute('type')).toBe('button')
  })

  it('calls onClick when enabled', () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Guardar</Button>)
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('is disabled and ignores clicks when disabled', () => {
    const onClick = vi.fn()
    render(
      <Button disabled onClick={onClick}>
        Guardar
      </Button>,
    )
    const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Guardar' })
    fireEvent.click(button)

    expect(button.disabled).toBe(true)
    expect(onClick).not.toHaveBeenCalled()
  })
})

describe('TextField', () => {
  function describedByText(input: HTMLElement) {
    const ids = input.getAttribute('aria-describedby')?.split(' ') ?? []
    return ids.map((id) => document.getElementById(id)?.textContent ?? '').join(' ')
  }

  it('is found by its label', () => {
    render(<TextField id="nombre" label="Nombre" />)
    expect(screen.getByLabelText('Nombre').tagName).toBe('INPUT')
  })

  it('is still found by its label when the label is visually hidden', () => {
    render(<TextField id="busqueda" label="Buscar" type="search" hideLabel />)
    expect(screen.getByLabelText('Buscar')).toBeTruthy()
  })

  it('renders a search input for type "search"', () => {
    render(<TextField id="busqueda" label="Buscar" type="search" />)
    expect(screen.getByLabelText('Buscar').getAttribute('type')).toBe('search')
  })

  it('marks the input invalid and links the error message', () => {
    render(<TextField id="codigo" label="Código" error="Campo obligatorio" />)
    const input = screen.getByLabelText('Código')

    expect(input.getAttribute('aria-invalid')).toBe('true')
    expect(describedByText(input)).toContain('Campo obligatorio')
  })

  it('links the hint', () => {
    render(<TextField id="codigo" label="Código" hint="Usa solo letras y números." />)
    expect(describedByText(screen.getByLabelText('Código'))).toContain('Usa solo letras y números.')
  })

  it('disables the input', () => {
    render(<TextField id="codigo" label="Código" disabled />)
    expect(screen.getByLabelText<HTMLInputElement>('Código').disabled).toBe(true)
  })
})
