import { describe, expect, it } from 'vitest'
import { emptyForm, type AttributeForm } from './attributeForm'
import {
  applyCase,
  checkDate,
  checkNumber,
  checkText,
  currencySymbol,
  datePattern,
  describePreview,
  listSummary,
  previewKey,
} from './fieldPreview'

const TARGETS = { targetObjectType: [{ code: 'PRODUCT', label: 'Producto' }] }

function form(changes: Partial<AttributeForm>): AttributeForm {
  return { ...emptyForm(), displayName: 'Peso', ...changes }
}

function text(changes: Partial<AttributeForm['text']>): AttributeForm {
  return form({ dataType: 'TEXT', text: { ...emptyForm().text, ...changes } })
}

function numeric(changes: Partial<AttributeForm['numeric']>): AttributeForm {
  return form({ dataType: 'NUMERIC', numeric: { ...emptyForm().numeric, ...changes } })
}

function currency(currencyCode: string, decimals = ''): AttributeForm {
  return form({ dataType: 'CURRENCY', currency: { currencyCode, decimals } })
}

describe('describePreview: core (FR-004, FR-010, FR-011)', () => {
  it('labels the field with the name and marks it optional', () => {
    expect(describePreview(form({ dataType: 'BOOLEAN' }), {}).label).toBe('Peso (opcional)')
    expect(describePreview(form({ displayName: '  ', dataType: 'BOOLEAN' }), {}).label).toBe(
      'Nombre del atributo (opcional)',
    )
  })

  it('starts the help text with the description', () => {
    const preview = describePreview(form({ dataType: 'BOOLEAN', description: ' Peso bruto ' }), {})
    expect(preview.hintParts).toEqual(['Peso bruto'])
  })

  it('asks for a data type before showing a field', () => {
    const preview = describePreview(form({}), {})
    expect(preview.kind).toBe('none')
    expect(preview.notes).toEqual(['Elige un tipo de dato para ver el campo.'])
  })

  it('changes its key whenever any configuration field changes', () => {
    const base = text({ maxLength: '5' })
    expect(previewKey(base)).toBe(previewKey(text({ maxLength: '5' })))
    expect(previewKey(base)).not.toBe(previewKey(text({ maxLength: '6' })))
    expect(previewKey(base)).not.toBe(previewKey({ ...base, displayName: 'Otro' }))
  })
})

describe('text (FR-005)', () => {
  it('chooses a single or a multi-line field', () => {
    expect(describePreview(text({}), {}).kind).toBe('text')
    const rich = describePreview(text({ richText: true }), {})
    expect(rich.kind).toBe('textarea')
    expect(rich.hintParts).toContain('Acepta texto enriquecido (HTML).')
  })

  it('describes the length limits', () => {
    expect(describePreview(text({ minLength: '2', maxLength: '5' }), {}).hintParts).toEqual([
      'Entre 2 y 5 caracteres',
    ])
    expect(describePreview(text({ maxLength: '5' }), {}).hintParts).toEqual(['Hasta 5 caracteres'])
    expect(describePreview(text({ minLength: '2' }), {}).hintParts).toEqual([
      'Al menos 2 caracteres',
    ])
    expect(describePreview(text({ maxLength: '5' }), {}).maxLength).toBe(5)
  })

  it('applies uppercase and lowercase, and nothing for mixed case', () => {
    expect(describePreview(text({ caseFormat: 'UPPERCASE' }), {}).hintParts).toEqual([
      'Se escribe en mayúsculas.',
    ])
    expect(describePreview(text({ caseFormat: 'LOWERCASE' }), {}).hintParts).toEqual([
      'Se escribe en minúsculas.',
    ])
    const mixed = describePreview(text({ caseFormat: 'MIXED_CASE' }), {})
    expect(mixed.caseFormat).toBeUndefined()
    expect(mixed.hintParts).toEqual([])
    expect(applyCase('añb', 'UPPERCASE')).toBe('AÑB')
    expect(applyCase('AÑB', 'LOWERCASE')).toBe('añb')
    expect(applyCase('aÑb', undefined)).toBe('aÑb')
  })

  it('checks the whole value against the capture mask and the minimum length', () => {
    const preview = describePreview(text({ minLength: '2', captureMask: '[A-Z]+' }), {})
    expect(checkText('AB', preview)).toBeUndefined()
    expect(checkText('AB1', preview)).toBe('No cumple la máscara de captura.')
    expect(checkText('A', preview)).toBe('Escribe al menos 2 caracteres.')
    expect(checkText('', preview)).toBeUndefined()
  })
})

describe('numbers (FR-006)', () => {
  it('describes integers and decimals', () => {
    const integer = describePreview(numeric({ numberFormat: 'INTEGER' }), {})
    expect(integer.step).toBe('1')
    expect(integer.hintParts).toEqual(['Número entero'])
    const decimal = describePreview(numeric({ numberFormat: 'DECIMAL', precision: '3' }), {})
    expect(decimal.step).toBe('0.001')
    expect(decimal.hintParts).toEqual(['Hasta 3 decimales'])
    expect(decimal.notes).toEqual([])
  })

  it('describes the range', () => {
    const hints = (minimum: string, maximum: string) =>
      describePreview(numeric({ numberFormat: 'INTEGER', minimum, maximum }), {}).hintParts[1]
    expect(hints('0', '100')).toBe('Entre 0 y 100')
    expect(hints('0', '')).toBe('Desde 0')
    expect(hints('', '100')).toBe('Hasta 100')
  })

  it('checks whole numbers, decimals and the range', () => {
    const integer = describePreview(
      numeric({ numberFormat: 'INTEGER', minimum: '0', maximum: '100' }),
      {},
    )
    expect(checkNumber('1.5', integer)).toBe('Escribe un número entero.')
    expect(checkNumber('150', integer)).toBe('El valor debe estar entre 0 y 100.')
    expect(checkNumber('50', integer)).toBeUndefined()
    const decimal = describePreview(
      numeric({ numberFormat: 'DECIMAL', precision: '3', minimum: '0' }),
      {},
    )
    expect(checkNumber('1.2345', decimal)).toBe('Usa como máximo 3 decimales.')
    expect(checkNumber('-1', decimal)).toBe('El valor debe ser al menos 0.')
    const atMost = describePreview(numeric({ numberFormat: 'INTEGER', maximum: '100' }), {})
    expect(checkNumber('101', atMost)).toBe('El valor debe ser como máximo 100.')
    expect(checkNumber('', atMost)).toBeUndefined()
  })
})

describe('dates (FR-009)', () => {
  it('turns every feature 004 format into a pattern', () => {
    expect(datePattern('DD/MM/AAAA HH:mm').pattern.test('01/02/2026 13:05')).toBe(true)
    expect(datePattern('MM/DD/AAAA').pattern.test('02/01/2026')).toBe(true)
    expect(datePattern('AAAA-MM-DD').pattern.test('2026-02-01')).toBe(true)
    expect(datePattern('AAAA-MM-DD HH:mm').pattern.test('2026-02-01 09:30')).toBe(true)
    expect(datePattern('DD/MM/AAAA').pattern.test('2026-02-01')).toBe(false)
  })

  it('checks the format and that the date exists', () => {
    expect(checkDate('2026-02-01', 'DD/MM/AAAA')).toBe('Usa el formato DD/MM/AAAA.')
    expect(checkDate('31/02/2026', 'DD/MM/AAAA')).toBe('La fecha no existe.')
    expect(checkDate('29/02/2028', 'DD/MM/AAAA')).toBeUndefined()
    expect(checkDate('01/01/2026 25:00', 'DD/MM/AAAA HH:mm')).toBe('La fecha no existe.')
    expect(checkDate('02/31/2026', 'MM/DD/AAAA')).toBe('La fecha no existe.')
    expect(checkDate('', 'DD/MM/AAAA')).toBeUndefined()
  })

  it('shows the format, or asks for one', () => {
    const preview = describePreview(
      form({ dataType: 'DATE_TIME', dateTime: { dateFormat: 'DD/MM/AAAA' } }),
      {},
    )
    expect(preview.kind).toBe('date')
    expect(preview.hintParts).toEqual(['Formato: DD/MM/AAAA'])
    const missing = describePreview(form({ dataType: 'DATE_TIME' }), {})
    expect(missing.kind).toBe('none')
    expect(missing.notes).toEqual(['Elige un formato de fecha para ver el campo.'])
  })
})

describe('currency (FR-009)', () => {
  it('shows the symbol and the decimals, 2 by default', () => {
    expect(currencySymbol('MXN')).toBe('$')
    const preview = describePreview(currency('mxn'), {})
    expect(preview.hintParts).toEqual(['Moneda: MXN ($)', '2 decimales'])
    expect(preview.step).toBe('0.01')
    expect(describePreview(currency('MXN', '0'), {}).step).toBe('1')
  })
})

describe('lists of values (FR-008)', () => {
  it('uses a drop-down or checkboxes, single by default', () => {
    const lov = (selectionMode: string) =>
      describePreview(
        form({
          dataType: 'LIST_OF_VALUES',
          listOfValues: { selectionMode, listOfValuesId: 'LOV1' },
        }),
        {},
      )
    expect(lov('SINGLE').kind).toBe('select')
    expect(lov('MULTIPLE').kind).toBe('checkboxes')
    expect(lov('SINGLE').listOfValuesId).toBe('LOV1')
    expect(lov('').kind).toBe('select')
    expect(lov('').notes).toEqual([
      'Tipo de selección sin elegir: la vista previa usa selección única.',
    ])
  })

  it('summarizes lists longer than 50 values', () => {
    expect(listSummary(50)).toBeUndefined()
    expect(listSummary(80)).toBe('Vista previa con los primeros 50 de 80 valores.')
  })
})

describe('references and notes (FR-009, FR-010)', () => {
  it('describes a disabled selector for the target', () => {
    const reference = (cardinality: string) =>
      describePreview(
        form({ dataType: 'REFERENCE', reference: { targetObjectType: 'PRODUCT', cardinality } }),
        TARGETS,
      )
    expect(reference('SINGLE').referencePlaceholder).toBe('Selecciona un objeto: Producto')
    expect(reference('').referencePlaceholder).toBe('Selecciona un objeto: Producto')
    expect(reference('MULTIPLE').referencePlaceholder).toBe('Selecciona uno o varios: Producto')
    expect(reference('SINGLE').hintParts).toEqual([
      'Los objetos se podrán elegir cuando existan los formularios de producto.',
    ])
    const missing = describePreview(form({ dataType: 'REFERENCE' }), TARGETS)
    expect(missing.kind).toBe('none')
    expect(missing.notes).toEqual(['Elige el objeto referenciado para ver el campo.'])
  })

  it('reports every setting it cannot use and falls back', () => {
    const mask = describePreview(text({ captureMask: '[A-Z' }), {})
    expect(mask.notes).toEqual([
      'La máscara de captura no es una expresión regular válida; la vista previa no la aplica.',
    ])
    expect(mask.mask).toBeUndefined()

    const lengths = describePreview(text({ minLength: '9', maxLength: '5' }), {})
    expect(lengths.notes).toEqual([
      'La longitud mínima es mayor que la máxima; la vista previa no aplica los límites de longitud.',
    ])
    expect(lengths.maxLength).toBeUndefined()

    const range = describePreview(
      numeric({ numberFormat: 'INTEGER', minimum: '9', maximum: '1' }),
      {},
    )
    expect(range.notes).toEqual([
      'El valor mínimo es mayor que el máximo; la vista previa no aplica el rango.',
    ])
    expect(range.min).toBeUndefined()

    const notANumber = describePreview(numeric({ numberFormat: 'INTEGER', minimum: 'abc' }), {})
    expect(notANumber.notes).toEqual(['Un límite no es un número; la vista previa lo ignora.'])

    const noDecimals = describePreview(numeric({ numberFormat: 'DECIMAL' }), {})
    expect(noDecimals.notes).toEqual(['Sin decimales configurados: la vista previa usa 2.'])
    expect(noDecimals.step).toBe('0.01')

    const unknown = describePreview(currency('XYZ'), {})
    expect(unknown.notes).toEqual(['Moneda XYZ no reconocida; se muestra solo el código.'])
    expect(unknown.hintParts[0]).toBe('Moneda: XYZ')

    expect(describePreview(currency(''), {}).notes).toEqual([
      'Escribe el código de moneda para ver el símbolo.',
    ])
  })
})
