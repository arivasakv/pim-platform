import type { NavigationEntry } from '../../navigation'
import { Icon } from '../ui/Icon'

interface SideNavigationProps {
  id: string
  entries: NavigationEntry[]
  activePath: string
}

export function SideNavigation({ id, entries, activePath }: SideNavigationProps) {
  return (
    <nav id={id} className="app-nav" aria-label="Navegación principal">
      <ul className="app-nav__list">
        {entries.map((entry) => (
          <li key={entry.path}>
            <a
              className="app-nav__link"
              href={`#${entry.path}`}
              aria-current={entry.path === activePath ? 'page' : undefined}
            >
              <Icon name={entry.icon} />
              {entry.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  )
}
