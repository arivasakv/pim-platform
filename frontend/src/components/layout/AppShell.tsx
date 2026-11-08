import type { ReactNode } from 'react'
import type { NavigationEntry } from '../../navigation'
import { AppHeader } from './AppHeader'
import { SideNavigation } from './SideNavigation'

interface AppShellProps {
  entries: NavigationEntry[]
  activePath: string
  /** The current page. */
  children: ReactNode
}

/** Frame shared by every page: header, side navigation and main content area (FR-008). */
export function AppShell({ entries, activePath, children }: AppShellProps) {
  return (
    <div className="app-shell">
      <a className="app-skip-link" href="#contenido">
        Saltar al contenido
      </a>
      <AppHeader />
      <SideNavigation id="navegacion-principal" entries={entries} activePath={activePath} />
      <main id="contenido" className="app-main" tabIndex={-1}>
        {children}
      </main>
    </div>
  )
}
