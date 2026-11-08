import { fireEvent, render, screen, within } from '@testing-library/react'
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

// Tablet drawer (US4). jsdom ignores media queries, so these tests check state and attributes.
describe('AppShell navigation drawer', () => {
  function getMenuButton() {
    return screen.getByRole('button', { name: 'Abrir menú de navegación' })
  }

  function getNavigation() {
    return screen.getByRole('navigation', { name: 'Navegación principal' })
  }

  function openDrawer() {
    fireEvent.click(getMenuButton())
  }

  function expectDrawerClosed() {
    expect(getMenuButton().getAttribute('aria-expanded')).toBe('false')
    expect(getNavigation().getAttribute('data-open')).toBe('false')
  }

  it('has a closed menu button that controls the navigation', () => {
    renderShell()
    const menuButton = getMenuButton()

    expect(menuButton.getAttribute('aria-expanded')).toBe('false')
    expect(menuButton.getAttribute('aria-controls')).toBe('navegacion-principal')
    expect(getNavigation().id).toBe('navegacion-principal')
  })

  it('opens the drawer and moves focus into it', () => {
    renderShell()
    openDrawer()

    expect(getMenuButton().getAttribute('aria-expanded')).toBe('true')
    expect(getNavigation().getAttribute('data-open')).toBe('true')
    expect(getNavigation().contains(document.activeElement)).toBe(true)
  })

  it('closes with Escape and returns focus to the menu button', () => {
    renderShell()
    openDrawer()
    fireEvent.keyDown(document, { key: 'Escape' })

    expectDrawerClosed()
    expect(document.activeElement).toBe(getMenuButton())
  })

  it('closes when a navigation link is selected', () => {
    renderShell()
    openDrawer()
    fireEvent.click(within(getNavigation()).getByRole('link', { name: 'Otra sección' }))

    expectDrawerClosed()
  })

  it('closes when the backdrop is clicked', () => {
    const { container } = renderShell()
    openDrawer()
    const backdrop = container.querySelector('.app-backdrop')
    expect(backdrop).not.toBeNull()
    fireEvent.click(backdrop as Element)

    expectDrawerClosed()
    expect(container.querySelector('.app-backdrop')).toBeNull()
  })

  it('closes with the close button and returns focus to the menu button', () => {
    renderShell()
    openDrawer()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar menú de navegación' }))

    expectDrawerClosed()
    expect(document.activeElement).toBe(getMenuButton())
  })
})
