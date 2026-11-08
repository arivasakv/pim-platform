import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { NavigationEntry } from '../../navigation'
import { AppShell } from './AppShell'

const ENTRIES: NavigationEntry[] = [
  { path: '/', label: 'Inicio', icon: 'home' },
  { path: '/otra', label: 'Otra sección', icon: 'components' },
]

function renderShell() {
  return render(
    <AppShell entries={ENTRIES} activePath="/">
      <p>Contenido de prueba</p>
    </AppShell>,
  )
}

describe('AppShell', () => {
  it('renders the header, navigation and main landmarks', () => {
    renderShell()

    expect(within(screen.getByRole('banner')).getByText('Plataforma PIM')).toBeTruthy()
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeTruthy()

    const main = screen.getByRole('main')
    expect(main.id).toBe('contenido')
    expect(within(main).getByText('Contenido de prueba')).toBeTruthy()
  })

  it('marks only the active navigation entry', () => {
    renderShell()
    const navigation = screen.getByRole('navigation', { name: 'Navegación principal' })

    expect(
      within(navigation).getByRole('link', { name: 'Inicio' }).getAttribute('aria-current'),
    ).toBe('page')
    expect(
      within(navigation).getByRole('link', { name: 'Otra sección' }).getAttribute('aria-current'),
    ).toBeNull()
  })

  it('starts with a skip link to the main content', () => {
    renderShell()
    const firstLink = screen.getAllByRole('link')[0]

    expect(firstLink.textContent).toBe('Saltar al contenido')
    expect(firstLink.getAttribute('href')).toBe('#contenido')
  })

  it('contains no shopping elements', () => {
    renderShell()
    expect(screen.queryByText(/carrito|precio|comprar/i)).toBeNull()
  })
})
