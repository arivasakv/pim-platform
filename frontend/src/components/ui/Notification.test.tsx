import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Notification } from './Notification'

describe('Notification', () => {
  it('uses role "alert" for errors', () => {
    render(<Notification variant="error">Algo salió mal.</Notification>)
    expect(screen.getByRole('alert').textContent).toContain('Algo salió mal.')
  })

  it.each(['success', 'info', 'warning'] as const)('uses role "status" for %s', (variant) => {
    render(<Notification variant={variant}>Mensaje</Notification>)
    expect(screen.getByRole('status').textContent).toContain('Mensaje')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('shows the title and the text', () => {
    render(
      <Notification variant="info" title="Aviso">
        Texto del aviso
      </Notification>,
    )
    expect(screen.getByText('Aviso')).toBeTruthy()
    expect(screen.getByText('Texto del aviso')).toBeTruthy()
  })

  it('calls onDismiss when the close button is clicked', () => {
    const onDismiss = vi.fn()
    render(
      <Notification variant="success" onDismiss={onDismiss}>
        Guardado
      </Notification>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar notificación' }))
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('renders no close button without onDismiss', () => {
    render(<Notification variant="success">Guardado</Notification>)
    expect(screen.queryByRole('button')).toBeNull()
  })
})
