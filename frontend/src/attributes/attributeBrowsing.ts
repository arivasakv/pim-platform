import type { Attribute } from '../api/attributesApi'
import type { AttributeGroupSummary } from '../api/attributeGroupsApi'
import { matchesChoice } from './dataTypeChoices'

/**
 * What the "Atributos" page lists (feature 009): every attribute, those in no group, or the
 * members of one group. Page state only; nothing is stored.
 */
export type BrowseEntry =
  { kind: 'all' } | { kind: 'ungrouped' } | { kind: 'group'; groupId: string }

export const ALL: BrowseEntry = { kind: 'all' }
export const UNGROUPED: BrowseEntry = { kind: 'ungrouped' }

/** The groups as loaded by the page; a failure leaves the attribute list usable. */
export type GroupsState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; groups: AttributeGroupSummary[] }

/** Value of the type filter that matches every data type. */
export const ALL_TYPES = ''

/** Lower case without accents, so "logistico" finds "Logístico". */
export function normalized(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

/**
 * Feature 007's search rule: the type choice matches (or all), and name or system ID contains the
 * search. The type is one of the eight choices of feature 004 FR-007 (amended 2026-11-20).
 */
export function matchesFilters(attribute: Attribute, search: string, dataType: string): boolean {
  if (dataType !== ALL_TYPES && !matchesChoice(attribute, dataType)) return false
  const wanted = normalized(search.trim())
  if (wanted === '') return true
  return (
    normalized(attribute.displayName).includes(wanted) ||
    normalized(attribute.systemId).includes(wanted)
  )
}

export function inEntry(attribute: Attribute, entry: BrowseEntry): boolean {
  const groups = attribute.groups ?? []
  switch (entry.kind) {
    case 'all':
      return true
    case 'ungrouped':
      return groups.length === 0
    case 'group':
      return groups.some((group) => group.id === entry.groupId)
  }
}

export function ungroupedCount(attributes: Attribute[]): number {
  return attributes.filter((attribute) => inEntry(attribute, UNGROUPED)).length
}

/**
 * The attributes of the entry that match the filters, in list order, and how many matching
 * attributes lie outside the entry (spec FR-006).
 */
export function browse(
  attributes: Attribute[],
  entry: BrowseEntry,
  search: string,
  dataType: string,
): { visible: Attribute[]; elsewhere: number } {
  const visible: Attribute[] = []
  let elsewhere = 0
  for (const attribute of attributes) {
    if (!matchesFilters(attribute, search, dataType)) continue
    if (inEntry(attribute, entry)) visible.push(attribute)
    else elsewhere += 1
  }
  return { visible, elsewhere }
}

/** The groups whose name contains the query, ignoring accents and letter case. */
export function filterGroups(
  groups: AttributeGroupSummary[],
  query: string,
): AttributeGroupSummary[] {
  const wanted = normalized(query.trim())
  if (wanted === '') return groups
  return groups.filter((group) => normalized(group.name).includes(wanted))
}

/** The entry to use: a group missing from the loaded groups becomes "all" (spec FR-008). */
export function effectiveEntry(entry: BrowseEntry, groupsState: GroupsState): BrowseEntry {
  if (entry.kind !== 'group' || groupsState.status !== 'ready') return entry
  return groupsState.groups.some((group) => group.id === entry.groupId) ? entry : ALL
}

/** "1 atributo" / "N atributos". */
export function countLabel(count: number): string {
  return `${count} ${count === 1 ? 'atributo' : 'atributos'}`
}

/**
 * Spanish name of an entry. A group's name comes from the loaded groups or, while they are not
 * available, from the attributes that belong to it.
 */
export function entryLabel(
  entry: BrowseEntry,
  groupsState: GroupsState,
  attributes: Attribute[],
): string {
  if (entry.kind === 'all') return 'Todos los atributos'
  if (entry.kind === 'ungrouped') return 'Sin grupo'
  const loaded =
    groupsState.status === 'ready'
      ? groupsState.groups.find((group) => group.id === entry.groupId)?.name
      : undefined
  const fromAttributes = attributes
    .flatMap((attribute) => attribute.groups ?? [])
    .find((group) => group.id === entry.groupId)?.name
  return loaded ?? fromAttributes ?? 'Grupo'
}
