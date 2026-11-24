import { useSyncExternalStore } from 'react'
import type { IconName } from './components/ui/Icon'

export type NavigationSection = {
  id: string
  /** Spanish section name shown above its entries (displayed in uppercase). */
  label: string
}

export type NavigationEntry = {
  /** Hash path starting with "/", unique across entries. */
  path: string
  /** Spanish label shown in the side navigation. */
  label: string
  icon: IconName
  /** Id of the section the entry is listed under. Entries without one are listed without a section name. */
  section?: string
}

/** Sections of the side navigation, in display order. */
export const NAVIGATION_SECTIONS: NavigationSection[] = [
  { id: 'general', label: 'General' },
  { id: 'catalogo', label: 'Administración de catálogo' },
]

/**
 * Pages shown in the side navigation. Entries for PIM business sections are
 * added by the features that introduce them (FR-017), not here.
 */
export const NAVIGATION_ENTRIES: NavigationEntry[] = [
  { path: '/', label: 'Inicio', icon: 'home', section: 'general' },
  { path: '/buscar-producto', label: 'Buscar Producto', icon: 'search', section: 'general' },
  {
    path: '/listas-de-valores',
    label: "Lista de Valores (LOV's)",
    icon: 'list',
    section: 'catalogo',
  },
  { path: '/atributos', label: 'Atributos', icon: 'tags', section: 'catalogo' },
  {
    path: '/grupos-de-atributos',
    label: 'Grupo de Atributos',
    icon: 'folder-tree',
    section: 'catalogo',
  },
  { path: '/taxonomia', label: 'Taxonomía', icon: 'network', section: 'catalogo' },
]

const HOME_PATH = '/'

/**
 * Turns a location hash such as "#/buscar-producto" into a known path; anything else is home. A
 * query after the path ("#/atributos?desvincular=ATR000001", feature 014) does not change the page.
 */
export function getPathFromHash(hash: string): string {
  const path = hash.replace(/^#/, '').split('?')[0]
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
