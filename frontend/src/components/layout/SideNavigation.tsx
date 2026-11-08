import { useEffect, useRef } from 'react'
import type { NavigationEntry } from '../../navigation'
import { Icon } from '../ui/Icon'

interface SideNavigationProps {
  id: string
  entries: NavigationEntry[]
  activePath: string
  /** Drawer state; only has a visible effect below 1024px. */
  isOpen: boolean
  onClose: () => void
}

export function SideNavigation({ id, entries, activePath, isOpen, onClose }: SideNavigationProps) {
  const firstLinkRef = useRef<HTMLAnchorElement>(null)

  // When the drawer opens, move keyboard focus into it.
  useEffect(() => {
    if (isOpen) firstLinkRef.current?.focus()
  }, [isOpen])

  return (
    <nav id={id} className="app-nav" aria-label="Navegación principal" data-open={isOpen}>
      <button
        type="button"
        className="app-nav__close"
        aria-label="Cerrar menú de navegación"
        onClick={onClose}
      >
        <Icon name="close" />
      </button>
      <ul className="app-nav__list">
        {entries.map((entry, index) => (
          <li key={entry.path}>
            <a
              ref={index === 0 ? firstLinkRef : undefined}
              className="app-nav__link"
              href={`#${entry.path}`}
              aria-current={entry.path === activePath ? 'page' : undefined}
              onClick={onClose}
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
