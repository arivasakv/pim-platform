import type { Ref } from 'react'
import { Icon } from '../ui/Icon'

interface AppHeaderProps {
  onMenuClick: () => void
  isMenuOpen: boolean
  /** id of the navigation element the menu button opens. */
  menuId: string
  /** Lets AppShell return focus to the menu button when the drawer closes. */
  menuButtonRef: Ref<HTMLButtonElement>
}

/** Application header. Internal tool: no search, user menu, cart or promotions (FR-006). */
export function AppHeader({ onMenuClick, isMenuOpen, menuId, menuButtonRef }: AppHeaderProps) {
  return (
    <header className="app-header">
      {/* Visible only below 1024px (App.css). */}
      <button
        ref={menuButtonRef}
        type="button"
        className="app-header__menu-button"
        aria-label="Abrir menú de navegación"
        aria-expanded={isMenuOpen}
        aria-controls={menuId}
        onClick={onMenuClick}
      >
        <Icon name="menu" size={24} />
      </button>
      {/* Logo slot: stays empty until an official asset is supplied (FR-007). */}
      <span className="app-header__logo" aria-hidden="true" />
      <span className="app-header__title">Plataforma PIM</span>
    </header>
  )
}
