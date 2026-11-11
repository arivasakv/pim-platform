import type { AttributeCreationRequest, DataType } from '../api/attributesApi'

/** What the user has typed, kept as text until the request is built. */
export type AttributeForm = {
  systemId: string
  displayName: string
  description: string
  dataType: DataType | ''
  text: {
    minLength: string
    maxLength: string
    caseFormat: string
    captureMask: string
    richText: boolean
  }
  numeric: { numberFormat: string; precision: string; minimum: string; maximum: string }
  /** `listOfValuesId` is the system ID of an existing list (feature 006 FR-026). */
  listOfValues: { selectionMode: string; listOfValuesId?: string }
  dateTime: { dateFormat: string }
  currency: { currencyCode: string; decimals: string }
  reference: { targetObjectType: string; cardinality: string }
}

/** Messages keyed by the request field they belong to, e.g. `textProperties.maxLength`. */
export type FieldErrors = Record<string, string>

export function emptyForm(): AttributeForm {
  return {
    systemId: '',
    displayName: '',
    description: '',
    dataType: '',
    text: { minLength: '', maxLength: '', caseFormat: '', captureMask: '', richText: false },
    numeric: { numberFormat: '', precision: '', minimum: '', maximum: '' },
    listOfValues: { selectionMode: '', listOfValuesId: '' },
    dateTime: { dateFormat: '' },
    currency: { currencyCode: '', decimals: '' },
    reference: { targetObjectType: '', cardinality: '' },
  }
}

const NUMBER_EXPECTED = 'Escribe un número.'

function isBlank(value: string): boolean {
  return value.trim() === ''
}

function parseNumber(value: string): number | undefined {
  return isBlank(value) ? undefined : Number(value.trim())
}

function checkNumber(errors: FieldErrors, field: string, value: string) {
  if (!isBlank(value) && Number.isNaN(Number(value.trim()))) errors[field] = NUMBER_EXPECTED
}

function checkRange(errors: FieldErrors, field: string, min: string, max: string, message: string) {
  const minimum = parseNumber(min)
  const maximum = parseNumber(max)
  if (minimum !== undefined && maximum !== undefined && minimum > maximum && !errors[field]) {
    errors[field] = message
  }
}

/**
 * Instant feedback before saving. The backend checks everything again and is authoritative
 * (constitution VIII); these checks only spare a request for obvious mistakes.
 */
export function validateForm(form: AttributeForm): FieldErrors {
  const errors: FieldErrors = {}
  if (isBlank(form.displayName)) errors.displayName = 'Escribe un nombre.'
  if (form.dataType === '') errors.dataType = 'Elige un tipo de dato.'

  if (form.dataType === 'TEXT') {
    checkNumber(errors, 'textProperties.minLength', form.text.minLength)
    checkNumber(errors, 'textProperties.maxLength', form.text.maxLength)
    checkRange(
      errors,
      'textProperties.maxLength',
      form.text.minLength,
      form.text.maxLength,
      'La longitud mínima no puede ser mayor que la longitud máxima.',
    )
  }
  if (form.dataType === 'NUMERIC') {
    checkNumber(errors, 'numericProperties.precision', form.numeric.precision)
    checkNumber(errors, 'numericProperties.minimum', form.numeric.minimum)
    checkNumber(errors, 'numericProperties.maximum', form.numeric.maximum)
    checkRange(
      errors,
      'numericProperties.maximum',
      form.numeric.minimum,
      form.numeric.maximum,
      'El valor mínimo no puede ser mayor que el valor máximo.',
    )
  }
  if (form.dataType === 'CURRENCY') {
    checkNumber(errors, 'currencyProperties.decimals', form.currency.decimals)
  }
  return errors
}

/** Copies only the entries whose value is set. */
function defined<T extends object>(value: T): T {
  return Object.fromEntries(
    Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== ''),
  ) as T
}

function text(value: string): string | undefined {
  return isBlank(value) ? undefined : value.trim()
}

/** Builds the request body: common fields plus the properties of the chosen type only (A-004). */
export function toRequest(form: AttributeForm): AttributeCreationRequest {
  const dataType = form.dataType as DataType
  const request: AttributeCreationRequest = {
    displayName: form.displayName.trim(),
    description: text(form.description) ?? null,
    dataType,
  }
  const systemId = text(form.systemId)
  if (systemId) request.systemId = systemId

  switch (dataType) {
    case 'TEXT':
      request.textProperties = defined({
        minLength: parseNumber(form.text.minLength),
        maxLength: parseNumber(form.text.maxLength),
        caseFormat: text(form.text.caseFormat),
        captureMask: form.text.captureMask === '' ? undefined : form.text.captureMask,
        richText: form.text.richText,
      })
      break
    case 'NUMERIC':
      request.numericProperties = defined({
        numberFormat: form.numeric.numberFormat,
        precision:
          form.numeric.numberFormat === 'DECIMAL' ? parseNumber(form.numeric.precision) : undefined,
        minimum: parseNumber(form.numeric.minimum),
        maximum: parseNumber(form.numeric.maximum),
      })
      break
    case 'LIST_OF_VALUES':
      request.listOfValuesProperties = defined({
        selectionMode: text(form.listOfValues.selectionMode),
        listOfValuesId: text(form.listOfValues.listOfValuesId ?? ''),
      })
      break
    case 'DATE_TIME':
      request.dateTimeProperties = defined({ dateFormat: form.dateTime.dateFormat })
      break
    case 'CURRENCY':
      request.currencyProperties = defined({
        currencyCode: form.currency.currencyCode.trim().toUpperCase(),
        decimals: parseNumber(form.currency.decimals),
      })
      break
    case 'REFERENCE':
      request.referenceProperties = defined({
        targetObjectType: form.reference.targetObjectType,
        cardinality: text(form.reference.cardinality),
      })
      break
    case 'BOOLEAN':
      break
  }
  return request
}
