import { useEffect, useRef } from 'react'
import type { NavigationEntry, NavigationSection } from '../../navigation'
import { Icon } from '../ui/Icon'

interface SideNavigationProps {
  id: string
  entries: NavigationEntry[]
  /** Sections in display order. Entries without a known section are listed first, without a name. */
  sections: NavigationSection[]
  activePath: string
  /** Drawer state; only has a visible effect below 1024px. */
  isOpen: boolean
  onClose: () => void
}

type NavigationGroup = {
  section?: NavigationSection
  entries: NavigationEntry[]
}

function groupEntries(
  entries: NavigationEntry[],
  sections: NavigationSection[],
): NavigationGroup[] {
  const sectionIds = new Set(sections.map((section) => section.id))
  const ungrouped = entries.filter((entry) => !entry.section || !sectionIds.has(entry.section))
  const groups: NavigationGroup[] = sections.map((section) => ({
    section,
    entries: entries.filter((entry) => entry.section === section.id),
  }))
  return [{ entries: ungrouped }, ...groups].filter((group) => group.entries.length > 0)
}

export function SideNavigation({
  id,
  entries,
  sections,
  activePath,
  isOpen,
  onClose,
}: SideNavigationProps) {
  const firstLinkRef = useRef<HTMLAnchorElement>(null)
  const groups = groupEntries(entries, sections)
  const firstPath = groups[0]?.entries[0]?.path

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
      {groups.map((group) => {
        const labelId = group.section ? `${id}-seccion-${group.section.id}` : undefined
        return (
          <div key={group.section?.id ?? ''} className="app-nav__section">
            {group.section && (
              <span id={labelId} className="app-nav__section-label">
                {group.section.label}
              </span>
            )}
            <ul className="app-nav__list" aria-labelledby={labelId}>
              {group.entries.map((entry) => (
                <li key={entry.path}>
                  <a
                    ref={entry.path === firstPath ? firstLinkRef : undefined}
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
          </div>
        )
      })}
    </nav>
  )
}
