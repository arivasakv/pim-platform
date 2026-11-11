import { describe, expect, it } from 'vitest'
import type { Attribute } from '../api/attributesApi'
import {
  emptyForm,
  fromAttribute,
  toRequest,
  validateForm,
  type AttributeForm,
} from './attributeForm'

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
          dataType: 'LIST_OF_VALUES',
          listOfValues: { selectionMode: 'MULTIPLE', listOfValuesId: ' LOV000001 ' },
        }),
      ).listOfValuesProperties,
    ).toEqual({ selectionMode: 'MULTIPLE', listOfValuesId: 'LOV000001' })
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

describe('fromAttribute (feature 007)', () => {
  const common = { systemId: 'ATR000001', scope: 'GLOBAL' as const, displayName: 'X' }

  it('fills the common fields and keeps other types empty', () => {
    const form = fromAttribute({ ...common, dataType: 'BOOLEAN', description: 'Desc' })

    expect(form.systemId).toBe('ATR000001')
    expect(form.displayName).toBe('X')
    expect(form.description).toBe('Desc')
    expect(form.dataType).toBe('BOOLEAN')
    expect(form.text).toEqual(emptyForm().text)
    expect(fromAttribute({ ...common, dataType: 'BOOLEAN' }).description).toBe('')
  })

  it('turns every type back into form text that rebuilds the same request', () => {
    const attributes: Attribute[] = [
      {
        ...common,
        dataType: 'TEXT',
        textProperties: {
          minLength: 2,
          maxLength: 50,
          caseFormat: 'UPPERCASE',
          captureMask: '[A-Z]+',
          richText: true,
        },
      },
      {
        ...common,
        dataType: 'NUMERIC',
        numericProperties: { numberFormat: 'DECIMAL', precision: 2, minimum: 0, maximum: 9.5 },
      },
      {
        ...common,
        dataType: 'LIST_OF_VALUES',
        listOfValuesProperties: {
          selectionMode: 'MULTIPLE',
          listOfValuesId: 'LOV000001',
          listOfValuesName: 'Color',
        },
      },
      { ...common, dataType: 'DATE_TIME', dateTimeProperties: { dateFormat: 'DD/MM/AAAA' } },
      {
        ...common,
        dataType: 'CURRENCY',
        currencyProperties: { currencyCode: 'MXN', decimals: 2, currencySymbol: '$' },
      },
      {
        ...common,
        dataType: 'REFERENCE',
        referenceProperties: { targetObjectType: 'PRODUCT', cardinality: 'MULTIPLE' },
      },
    ]

    expect(fromAttribute(attributes[0]).text.maxLength).toBe('50')
    expect(fromAttribute(attributes[1]).numeric.maximum).toBe('9.5')
    expect(toRequest(fromAttribute(attributes[0])).textProperties).toEqual(
      attributes[0].textProperties,
    )
    expect(toRequest(fromAttribute(attributes[1])).numericProperties).toEqual(
      attributes[1].numericProperties,
    )
    expect(toRequest(fromAttribute(attributes[2])).listOfValuesProperties).toEqual({
      selectionMode: 'MULTIPLE',
      listOfValuesId: 'LOV000001',
    })
    expect(toRequest(fromAttribute(attributes[3])).dateTimeProperties).toEqual({
      dateFormat: 'DD/MM/AAAA',
    })
    expect(toRequest(fromAttribute(attributes[4])).currencyProperties).toEqual({
      currencyCode: 'MXN',
      decimals: 2,
    })
    expect(toRequest(fromAttribute(attributes[5])).referenceProperties).toEqual(
      attributes[5].referenceProperties,
    )
    expect(toRequest(fromAttribute(attributes[5])).systemId).toBe('ATR000001')
  })
})
