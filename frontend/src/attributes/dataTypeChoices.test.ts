import { describe, expect, it } from 'vitest'
import type { Attribute, AttributeType } from '../api/attributesApi'
import {
  attributeTypeLabel,
  choiceOf,
  fromChoice,
  matchesChoice,
  typeChoices,
} from './dataTypeChoices'

/** Feature 004 FR-007 as amended on 2026-11-20 (option B). */
const TYPES: AttributeType[] = [
  { code: 'TEXT', label: 'Texto (String)', options: {} },
  { code: 'NUMERIC', label: 'Numérico (Integer / Float)', options: {} },
  { code: 'BOOLEAN', label: 'Booleano (Sí/No)', options: {} },
]

function numeric(numberFormat: string): Attribute {
  return {
    systemId: 'ATR1',
    displayName: 'Peso',
    dataType: 'NUMERIC',
    scope: 'GLOBAL',
    numericProperties: { numberFormat },
  }
}

describe('dataTypeChoices', () => {
  it('shows Numérico as two choices, in its place', () => {
    expect(typeChoices(TYPES).map((choice) => choice.label)).toEqual([
      'Texto (String)',
      'Numérico Entero (Integer)',
      'Numérico Decimal (Float)',
      'Booleano (Sí/No)',
    ])
  })

  it('turns a choice into the stored type and format, and back', () => {
    expect(fromChoice('NUMERIC_INTEGER')).toEqual({ dataType: 'NUMERIC', numberFormat: 'INTEGER' })
    expect(fromChoice('NUMERIC_DECIMAL')).toEqual({ dataType: 'NUMERIC', numberFormat: 'DECIMAL' })
    expect(fromChoice('TEXT')).toEqual({ dataType: 'TEXT' })
    expect(fromChoice('')).toEqual({ dataType: '' })
    expect(choiceOf('NUMERIC', 'INTEGER')).toBe('NUMERIC_INTEGER')
    expect(choiceOf('NUMERIC', 'DECIMAL')).toBe('NUMERIC_DECIMAL')
    expect(choiceOf('NUMERIC', '')).toBe('')
    expect(choiceOf('BOOLEAN', 'DECIMAL')).toBe('BOOLEAN')
  })

  it('filters numbers by format and labels them by their choice', () => {
    expect(matchesChoice(numeric('INTEGER'), 'NUMERIC_INTEGER')).toBe(true)
    expect(matchesChoice(numeric('INTEGER'), 'NUMERIC_DECIMAL')).toBe(false)
    expect(matchesChoice(numeric('DECIMAL'), 'NUMERIC')).toBe(true)
    expect(attributeTypeLabel(numeric('INTEGER'), TYPES)).toBe('Numérico Entero (Integer)')
    expect(attributeTypeLabel(numeric('DECIMAL'), TYPES)).toBe('Numérico Decimal (Float)')
    expect(attributeTypeLabel({ ...numeric('DECIMAL'), dataType: 'BOOLEAN' }, TYPES)).toBe(
      'Booleano (Sí/No)',
    )
    expect(attributeTypeLabel({ ...numeric('DECIMAL'), dataType: 'REFERENCE' }, [])).toBe(
      'Referencias / Relaciones',
    )
  })
})
