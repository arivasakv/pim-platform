import { useSyncExternalStore } from 'react'
import type { IconName } from './components/ui/Icon'

export type NavigationEntry = {
  /** Hash path starting with "/", unique across entries. */
  path: string
  /** Spanish label shown in the side navigation. */
  label: string
  icon: IconName
}

/**
 * Pages shown in the side navigation. Entries for PIM business sections are
 * added by the features that introduce them (FR-017), not here.
 */
export const NAVIGATION_ENTRIES: NavigationEntry[] = [
  { path: '/', label: 'Inicio', icon: 'home' },
  { path: '/componentes', label: 'Componentes', icon: 'components' },
  { path: '/buscar-producto', label: 'Buscar producto', icon: 'search' },
  { path: '/listas-de-valores', label: 'Listas de valores', icon: 'components' },
  { path: '/crear-atributo', label: 'Crear atributo', icon: 'components' },
  { path: '/grupos-de-atributos', label: 'Grupos de atributos', icon: 'components' },
]

const HOME_PATH = '/'

/** Turns a location hash such as "#/componentes" into a known path; anything else is home. */
export function getPathFromHash(hash: string): string {
  const path = hash.replace(/^#/, '')
  const isKnownPath = NAVIGATION_ENTRIES.some((entry) => entry.path === path)
  return isKnownPath ? path : HOME_PATH
}

function subscribeToHashChanges(onChange: () => void) {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

function readCurrentPath() {
  return getPathFromHash(window.location.hash)
}

/** Current page path, updated whenever the URL hash changes. */
export function useCurrentPath(): string {
  return useSyncExternalStore(subscribeToHashChanges, readCurrentPath)
}
