import { describe, expect, it } from 'vitest'
import { emptyForm, toRequest, validateForm, type AttributeForm } from './attributeForm'

function form(changes: Partial<AttributeForm>): AttributeForm {
  return { ...emptyForm(), ...changes }
}

describe('validateForm (instant feedback only; the backend decides)', () => {
  it('requires a display name and a data type', () => {
    expect(validateForm(emptyForm())).toEqual({
      displayName: 'Escribe un nombre.',
      dataType: 'Elige un tipo de dato.',
    })
    expect(validateForm(form({ displayName: '   ', dataType: 'BOOLEAN' }))).toEqual({
      displayName: 'Escribe un nombre.',
    })
    expect(validateForm(form({ displayName: 'Color', dataType: 'BOOLEAN' }))).toEqual({})
  })

  it('refuses a minimum greater than its maximum', () => {
    const text = form({
      displayName: 'Código',
      dataType: 'TEXT',
      text: { ...emptyForm().text, minLength: '10', maxLength: '5' },
    })
    const numeric = form({
      displayName: 'Peso',
      dataType: 'NUMERIC',
      numeric: { ...emptyForm().numeric, numberFormat: 'INTEGER', minimum: '5', maximum: '1' },
    })

    expect(validateForm(text)).toEqual({
      'textProperties.maxLength': 'La longitud mínima no puede ser mayor que la longitud máxima.',
    })
    expect(validateForm(numeric)).toEqual({
      'numericProperties.maximum': 'El valor mínimo no puede ser mayor que el valor máximo.',
    })
  })

  it('asks for numbers where numbers are expected', () => {
    const numeric = form({
      displayName: 'Peso',
      dataType: 'NUMERIC',
      numeric: { ...emptyForm().numeric, numberFormat: 'DECIMAL', minimum: 'abc' },
    })

    expect(validateForm(numeric)).toEqual({ 'numericProperties.minimum': 'Escribe un número.' })
  })
})

describe('toRequest', () => {
  it('trims the common fields and leaves out an empty ID and description', () => {
    expect(
      toRequest(
        form({ displayName: ' Color ', description: '  ', systemId: ' ', dataType: 'BOOLEAN' }),
      ),
    ).toEqual({ displayName: 'Color', description: null, dataType: 'BOOLEAN' })
    expect(
      toRequest(
        form({
          displayName: 'Color',
          description: ' Tono ',
          systemId: ' ATR-COLOR ',
          dataType: 'BOOLEAN',
        }),
      ),
    ).toEqual({
      displayName: 'Color',
      description: 'Tono',
      systemId: 'ATR-COLOR',
      dataType: 'BOOLEAN',
    })
  })

  it('sends only the properties of the chosen type, with numbers as numbers', () => {
    const text = form({
      displayName: 'Código',
      dataType: 'TEXT',
      text: {
        minLength: '2',
        maxLength: '',
        caseFormat: 'UPPERCASE',
        captureMask: '^[A-Z]+$',
        richText: true,
      },
      numeric: { numberFormat: 'INTEGER', precision: '', minimum: '1', maximum: '' },
    })

    expect(toRequest(text)).toEqual({
      displayName: 'Código',
      description: null,
      dataType: 'TEXT',
      textProperties: {
        minLength: 2,
        caseFormat: 'UPPERCASE',
        captureMask: '^[A-Z]+$',
        richText: true,
      },
    })
  })

  it('builds the properties of every other type', () => {
    const base = { displayName: 'X', description: '' }
    expect(
      toRequest(
        form({
          ...base,
          dataType: 'NUMERIC',
          numeric: { numberFormat: 'DECIMAL', precision: '2', minimum: '0', maximum: '999.99' },
        }),
      ).numericProperties,
    ).toEqual({ numberFormat: 'DECIMAL', precision: 2, minimum: 0, maximum: 999.99 })
    expect(
      toRequest(form({ ...base, dataType: 'DATE_TIME', dateTime: { dateFormat: 'DD/MM/AAAA' } }))
        .dateTimeProperties,
    ).toEqual({ dateFormat: 'DD/MM/AAAA' })
    expect(
      toRequest(
        form({ ...base, dataType: 'CURRENCY', currency: { currencyCode: ' mxn ', decimals: '' } }),
      ).currencyProperties,
    ).toEqual({ currencyCode: 'MXN' })
    expect(
      toRequest(
        form({ ...base, dataType: 'LIST_OF_VALUES', listOfValues: { selectionMode: 'SINGLE' } }),
      ).listOfValuesProperties,
    ).toEqual({ selectionMode: 'SINGLE' })
    expect(
      toRequest(
        form({
          ...base,
          dataType: 'REFERENCE',
          reference: { targetObjectType: 'PRODUCT', cardinality: 'MULTIPLE' },
        }),
      ).referenceProperties,
    ).toEqual({ targetObjectType: 'PRODUCT', cardinality: 'MULTIPLE' })
    expect(toRequest(form({ ...base, dataType: 'BOOLEAN' }))).toEqual({
      displayName: 'X',
      description: null,
      dataType: 'BOOLEAN',
    })
  })
})
