import { describe, expect, it } from 'vitest'
import type { Attribute } from '../api/attributesApi'
import type { AttributeGroupSummary } from '../api/attributeGroupsApi'
import {
  ALL,
  UNGROUPED,
  browse,
  countLabel,
  effectiveEntry,
  entryLabel,
  filterGroups,
  inEntry,
  matchesFilters,
  ungroupedCount,
  type BrowseEntry,
} from './attributeBrowsing'

function attribute(
  systemId: string,
  displayName: string,
  dataType: string,
  groups?: Array<{ id: string; name: string }>,
): Attribute {
  return { systemId, displayName, dataType, scope: 'GLOBAL', groups } as Attribute
}

const PESO = attribute('ATR000001', 'Peso logístico', 'NUMERIC', [
  { id: 'g1', name: 'Logísticos' },
  { id: 'g2', name: 'Compra Ventas' },
])
const VOLUMEN = attribute('ATR000002', 'Volumen', 'NUMERIC', [{ id: 'g1', name: 'Logísticos' }])
const COSTO = attribute('ATR000003', 'Costo', 'CURRENCY', [{ id: 'g2', name: 'Compra Ventas' }])
const COLOR = attribute('ATR000004', 'Color', 'LIST_OF_VALUES', [])
const SIN_CAMPO = attribute('ATR000005', 'Activo', 'BOOLEAN')
const ALL_ATTRIBUTES = [COLOR, COSTO, PESO, SIN_CAMPO, VOLUMEN]

const LOGISTICOS: BrowseEntry = { kind: 'group', groupId: 'g1' }

function group(id: string, name: string, attributeCount = 0): AttributeGroupSummary {
  return { id, name, attributeCount }
}

describe('matchesFilters (feature 007 rule, unchanged)', () => {
  it('matches the name or system ID ignoring accents and letter case', () => {
    expect(matchesFilters(PESO, 'LOGISTICO', '')).toBe(true)
    expect(matchesFilters(PESO, 'atr000001', '')).toBe(true)
    expect(matchesFilters(PESO, 'volumen', '')).toBe(false)
  })

  it('matches everything with a blank search and filters by type', () => {
    expect(matchesFilters(PESO, '   ', '')).toBe(true)
    expect(matchesFilters(PESO, '', 'NUMERIC')).toBe(true)
    expect(matchesFilters(PESO, '', 'BOOLEAN')).toBe(false)
    expect(matchesFilters(PESO, 'peso', 'BOOLEAN')).toBe(false)
  })
})

describe('inEntry and ungroupedCount', () => {
  it('places attributes in "all", "ungrouped" and their groups', () => {
    expect(inEntry(PESO, ALL)).toBe(true)
    expect(inEntry(PESO, UNGROUPED)).toBe(false)
    expect(inEntry(COLOR, UNGROUPED)).toBe(true)
    expect(inEntry(SIN_CAMPO, UNGROUPED)).toBe(true)
    expect(inEntry(PESO, LOGISTICOS)).toBe(true)
    expect(inEntry(COSTO, LOGISTICOS)).toBe(false)
  })

  it('counts the attributes in no group', () => {
    expect(ungroupedCount(ALL_ATTRIBUTES)).toBe(2)
    expect(ungroupedCount([])).toBe(0)
  })
})

describe('browse', () => {
  it('lists the attributes of the entry in input order', () => {
    expect(browse(ALL_ATTRIBUTES, LOGISTICOS, '', '').visible).toEqual([PESO, VOLUMEN])
    expect(browse(ALL_ATTRIBUTES, UNGROUPED, '', '').visible).toEqual([COLOR, SIN_CAMPO])
    expect(browse(ALL_ATTRIBUTES, ALL, '', '').visible).toEqual(ALL_ATTRIBUTES)
  })

  it('shows an attribute in two groups under both, as the same object', () => {
    const logisticos = browse(ALL_ATTRIBUTES, LOGISTICOS, '', '').visible
    const ventas = browse(ALL_ATTRIBUTES, { kind: 'group', groupId: 'g2' }, '', '').visible
    expect(logisticos).toContain(PESO)
    expect(ventas).toContain(PESO)
  })

  it('combines the entry with the search and the type filter', () => {
    expect(browse(ALL_ATTRIBUTES, LOGISTICOS, 'vol', '').visible).toEqual([VOLUMEN])
    expect(browse(ALL_ATTRIBUTES, LOGISTICOS, '', 'CURRENCY').visible).toEqual([])
  })

  it('counts the matches outside the entry', () => {
    expect(browse(ALL_ATTRIBUTES, LOGISTICOS, 'costo', '')).toEqual({ visible: [], elsewhere: 1 })
    expect(browse(ALL_ATTRIBUTES, UNGROUPED, '', 'NUMERIC').elsewhere).toBe(2)
    expect(browse(ALL_ATTRIBUTES, ALL, 'nada', '').elsewhere).toBe(0)
    expect(browse(ALL_ATTRIBUTES, ALL, '', '').elsewhere).toBe(0)
  })
})

describe('filterGroups', () => {
  const groups = [group('g2', 'Compra Ventas'), group('g1', 'Logísticos')]

  it('narrows by name ignoring accents and letter case', () => {
    expect(filterGroups(groups, 'LOGISTICOS')).toEqual([groups[1]])
    expect(filterGroups(groups, 'nada')).toEqual([])
  })

  it('returns every group for a blank query', () => {
    expect(filterGroups(groups, '  ')).toEqual(groups)
  })
})

describe('effectiveEntry', () => {
  it('falls back to "all" when the group is no longer in the loaded list', () => {
    const ready = { status: 'ready' as const, groups: [group('g2', 'Compra Ventas')] }
    expect(effectiveEntry(LOGISTICOS, ready)).toEqual(ALL)
    expect(effectiveEntry({ kind: 'group', groupId: 'g2' }, ready)).toEqual({
      kind: 'group',
      groupId: 'g2',
    })
    expect(effectiveEntry(UNGROUPED, ready)).toEqual(UNGROUPED)
  })

  it('keeps the entry while groups load or after they failed', () => {
    expect(effectiveEntry(LOGISTICOS, { status: 'loading' })).toEqual(LOGISTICOS)
    expect(effectiveEntry(LOGISTICOS, { status: 'error' })).toEqual(LOGISTICOS)
  })
})

describe('countLabel and entryLabel', () => {
  it('writes counts in Spanish', () => {
    expect(countLabel(1)).toBe('1 atributo')
    expect(countLabel(0)).toBe('0 atributos')
    expect(countLabel(12)).toBe('12 atributos')
  })

  it('names the entries, a group from the loaded groups or from its attributes', () => {
    const ready = { status: 'ready' as const, groups: [group('g1', 'Logística')] }
    expect(entryLabel(ALL, ready, ALL_ATTRIBUTES)).toBe('Todos los atributos')
    expect(entryLabel(UNGROUPED, ready, ALL_ATTRIBUTES)).toBe('Sin grupo')
    expect(entryLabel(LOGISTICOS, ready, ALL_ATTRIBUTES)).toBe('Logística')
    expect(entryLabel(LOGISTICOS, { status: 'error' }, ALL_ATTRIBUTES)).toBe('Logísticos')
    expect(entryLabel({ kind: 'group', groupId: 'g9' }, { status: 'loading' }, [])).toBe('Grupo')
  })
})
