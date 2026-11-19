import type {
  FilterOnPath,
  ListAttribute,
  ListValue,
  TaxonomyNode,
  ValueFilterDetail,
} from '../api/taxonomyApi'

/** Test data of feature 012: the list «Colores» and the example nodes (quickstart "Example data"). */
export const COLORES: ListValue[] = [
  ['0001', 'Rojo'],
  ['0002', 'Azul'],
  ['0003', 'Verde'],
  ['0004', 'Negro'],
  ['0005', 'Blanco'],
  ['0006', 'Amarillo'],
  ['0007', 'Rosa'],
].map(([id, label]) => ({ id, label }))

export const ROPA = { systemId: 'CLS000001', name: 'Ropa' }
export const HOMBRE = { systemId: 'CLS000002', name: 'Ropa de hombre' }
export const CAMISAS = { systemId: 'CLS000003', name: 'Camisas' }

export function values(...ids: string[]): ListValue[] {
  return COLORES.filter((value) => ids.includes(value.id))
}

export function listAttribute(
  filters: FilterOnPath[],
  allowedCount: number,
  hasOwnFilter: boolean,
  selectionMode: 'SINGLE' | 'MULTIPLE' = 'SINGLE',
): ListAttribute {
  return {
    systemId: 'ATR000001',
    displayName: 'Color',
    listOfValuesId: 'LOV000001',
    listName: 'Colores',
    selectionMode,
    totalValues: 7,
    allowedCount,
    hasOwnFilter,
    filters,
  }
}

export function nodeWith(
  reference: { systemId: string; name: string },
  listAttributes: ListAttribute[],
): TaxonomyNode {
  return {
    ...reference,
    level: 2,
    path: [ROPA],
    children: [],
    directAttributeCount: 0,
    effectiveAttributes: [],
    listAttributes,
    createdAt: '2026-11-19T10:00:00Z',
  }
}

export function detail(overrides: Partial<ValueFilterDetail>): ValueFilterDetail {
  return {
    node: HOMBRE,
    attribute: listAttribute([], 7, false),
    totalValues: 7,
    availableValues: COLORES,
    includedValueIds: null,
    excludedAboveValues: [],
    allowedValues: COLORES,
    filters: [],
    ...overrides,
  }
}
