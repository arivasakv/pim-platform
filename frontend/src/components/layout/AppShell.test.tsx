import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { NavigationEntry, NavigationSection } from '../../navigation'
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

describe('AppShell navigation sections', () => {
  const SECTIONS: NavigationSection[] = [
    { id: 'general', label: 'General' },
    { id: 'catalogo', label: 'Administración de catálogo' },
  ]
  const SECTIONED_ENTRIES: NavigationEntry[] = [
    { path: '/', label: 'Inicio', icon: 'home', section: 'general' },
    { path: '/atributos', label: 'Atributos', icon: 'tags', section: 'catalogo' },
    { path: '/taxonomia', label: 'Taxonomía', icon: 'network', section: 'catalogo' },
  ]

  function renderSectionedShell(activePath = '/') {
    render(
      <AppShell entries={SECTIONED_ENTRIES} sections={SECTIONS} activePath={activePath}>
        <p>Contenido de prueba</p>
      </AppShell>,
    )
    return screen.getByRole('navigation', { name: 'Navegación principal' })
  }

  it('lists each entry under its named section', () => {
    const navigation = renderSectionedShell()

    const general = within(navigation).getByRole('list', { name: 'General' })
    expect(
      within(general)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Inicio'])

    const catalog = within(navigation).getByRole('list', { name: 'Administración de catálogo' })
    expect(
      within(catalog)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Atributos', 'Taxonomía'])
  })

  it('keeps the links and marks the active entry inside a section', () => {
    const navigation = renderSectionedShell('/taxonomia')

    const taxonomy = within(navigation).getByRole('link', { name: 'Taxonomía' })
    expect(taxonomy.getAttribute('href')).toBe('#/taxonomia')
    expect(taxonomy.getAttribute('aria-current')).toBe('page')
    expect(
      within(navigation).getByRole('link', { name: 'Inicio' }).getAttribute('aria-current'),
    ).toBeNull()
  })

  it('moves focus to the first entry of the first section when the drawer opens', () => {
    const navigation = renderSectionedShell()
    fireEvent.click(screen.getByRole('button', { name: 'Abrir menú de navegación' }))

    expect(document.activeElement).toBe(within(navigation).getByRole('link', { name: 'Inicio' }))
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
