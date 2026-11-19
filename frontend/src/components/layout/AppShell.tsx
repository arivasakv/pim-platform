import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import type { NavigationEntry, NavigationSection } from '../../navigation'
import { AppHeader } from './AppHeader'
import { SideNavigation } from './SideNavigation'

const NAVIGATION_ID = 'navegacion-principal'

interface AppShellProps {
  entries: NavigationEntry[]
  /** Side navigation sections; entries are grouped under them. */
  sections?: NavigationSection[]
  activePath: string
  /** The current page. */
  children: ReactNode
}

/**
 * Frame shared by every page: header, side navigation and main content area (FR-008).
 * Below 1024px the navigation is a drawer opened from the header (FR-013, FR-014).
 */
export function AppShell({ entries, sections = [], activePath, children }: AppShellProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)

  const closeMenu = useCallback(() => {
    if (!isMenuOpen) return
    setIsMenuOpen(false)
    // Return keyboard focus to the button that opened the drawer.
    menuButtonRef.current?.focus()
  }, [isMenuOpen])

  // Close the drawer with the Escape key while it is open.
  useEffect(() => {
    if (!isMenuOpen) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') closeMenu()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isMenuOpen, closeMenu])

  return (
    <div className="app-shell">
      <a className="app-skip-link" href="#contenido">
        Saltar al contenido
      </a>
      <AppHeader
        onMenuClick={() => setIsMenuOpen((open) => !open)}
        isMenuOpen={isMenuOpen}
        menuId={NAVIGATION_ID}
        menuButtonRef={menuButtonRef}
      />
      <SideNavigation
        id={NAVIGATION_ID}
        entries={entries}
        sections={sections}
        activePath={activePath}
        isOpen={isMenuOpen}
        onClose={closeMenu}
      />
      {isMenuOpen && <div className="app-backdrop" aria-hidden="true" onClick={closeMenu} />}
      <main id="contenido" className="app-main" tabIndex={-1}>
        {children}
      </main>
    </div>
  )
}
