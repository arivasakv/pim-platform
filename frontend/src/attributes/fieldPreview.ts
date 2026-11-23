import type { AttributeOption } from '../api/attributesApi'
import type { AttributeForm } from './attributeForm'
import { currencySymbolOf } from './currencyOptions'

/**
 * Pure rules of the live field preview (feature 008). They turn the attribute form's current state
 * into a description of the product field it would produce. Nothing here is stored or sent, and no
 * rule is added to the attribute itself: settings the preview cannot use are only reported as notes
 * (spec FR-010). Texts follow specs/008-attribute-field-preview/contracts/preview-ui.md.
 */

/** Most list values the preview shows (decision 4). */
export const PREVIEW_VALUE_LIMIT = 50

export type PreviewKind =
  | 'none'
  | 'text'
  | 'textarea'
  | 'number'
  | 'checkbox'
  | 'select'
  | 'checkboxes'
  | 'date'
  | 'reference'

export type FieldPreview = {
  kind: PreviewKind
  label: string
  /** Help text parts: the rules, joined with " · " (attributes have no description since 2026-11-20). */
  hintParts: string[]
  /** Settings the preview cannot use, in plain Spanish (FR-010). */
  notes: string[]
  minLength?: number
  maxLength?: number
  caseFormat?: 'UPPERCASE' | 'LOWERCASE'
  mask?: RegExp
  integer?: boolean
  decimals?: number
  min?: number
  max?: number
  step?: string
  dateFormat?: string
  listOfValuesId?: string
  /** Placeholder of the disabled reference selector. */
  referencePlaceholder?: string
}

const UNNAMED = 'Nombre del atributo'
const INVALID_LIMIT = 'Un límite no es un número; la vista previa lo ignora.'
const DEFAULT_DECIMALS = 2

/** A string that changes whenever any configuration field changes (FR-011). */
export function previewKey(form: AttributeForm): string {
  return JSON.stringify(form)
}

type Parsed = { value?: number; invalid: boolean }

function parse(text: string): Parsed {
  const trimmed = text.trim()
  if (trimmed === '') return { invalid: false }
  const value = Number(trimmed)
  return Number.isFinite(value) ? { value, invalid: false } : { invalid: true }
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

/** Step of a number field with this many decimals: "1", "0.1", "0.01"… */
function stepFor(decimals: number): string {
  return decimals === 0 ? '1' : (1 / 10 ** decimals).toFixed(decimals)
}

function rangeHint(min: number | undefined, max: number | undefined): string | undefined {
  if (min !== undefined && max !== undefined) return `Entre ${min} y ${max}`
  if (min !== undefined) return `Desde ${min}`
  if (max !== undefined) return `Hasta ${max}`
  return undefined
}

/**
 * Reads a minimum and a maximum; a non-number is ignored with a note, and a minimum above the
 * maximum drops both with `contradiction` as note.
 */
function limits(
  minText: string,
  maxText: string,
  contradiction: string,
  notes: string[],
): { min?: number; max?: number } {
  const min = parse(minText)
  const max = parse(maxText)
  if ((min.invalid || max.invalid) && !notes.includes(INVALID_LIMIT)) notes.push(INVALID_LIMIT)
  if (min.value !== undefined && max.value !== undefined && min.value > max.value) {
    notes.push(contradiction)
    return {}
  }
  return { min: min.value, max: max.value }
}

/** The capture mask as a whole-value regular expression, or `null` when it is not valid. */
export function compileMask(mask: string): RegExp | null | undefined {
  if (mask === '') return undefined
  try {
    return new RegExp(`^(?:${mask})$`, 'u')
  } catch {
    return null
  }
}

function optionLabel(options: AttributeOption[] | undefined, code: string): string {
  return options?.find((option) => option.code === code)?.label ?? code
}

/** Describes the preview field for the current form (spec FR-004 to FR-010). */
export function describePreview(
  form: AttributeForm,
  options: Record<string, AttributeOption[]>,
): FieldPreview {
  const notes: string[] = []
  const hintParts: string[] = []
  const preview: FieldPreview = {
    kind: 'none',
    label: `${form.displayName.trim() || UNNAMED} (opcional)`,
    hintParts,
    notes,
  }

  switch (form.dataType) {
    case '':
      notes.push('Elige un tipo de dato para ver el campo.')
      return preview
    case 'TEXT': {
      const text = form.text
      preview.kind = text.richText ? 'textarea' : 'text'
      const { min, max } = limits(
        text.minLength,
        text.maxLength,
        'La longitud mínima es mayor que la máxima; la vista previa no aplica los límites de longitud.',
        notes,
      )
      preview.minLength = min
      preview.maxLength = max
      if (min !== undefined && max !== undefined) hintParts.push(`Entre ${min} y ${max} caracteres`)
      else if (max !== undefined) hintParts.push(`Hasta ${max} caracteres`)
      else if (min !== undefined) hintParts.push(`Al menos ${min} caracteres`)
      if (text.caseFormat === 'UPPERCASE' || text.caseFormat === 'LOWERCASE') {
        preview.caseFormat = text.caseFormat
        hintParts.push(
          text.caseFormat === 'UPPERCASE'
            ? 'Se escribe en mayúsculas.'
            : 'Se escribe en minúsculas.',
        )
      }
      if (text.richText) hintParts.push('Acepta texto enriquecido (HTML).')
      const mask = compileMask(text.captureMask)
      if (mask === null) {
        notes.push(
          'La máscara de captura no es una expresión regular válida; la vista previa no la aplica.',
        )
      } else if (mask) {
        preview.mask = mask
      }
      return preview
    }
    case 'NUMERIC': {
      preview.kind = 'number'
      const numeric = form.numeric
      if (numeric.numberFormat === 'INTEGER') {
        preview.integer = true
        preview.step = '1'
        hintParts.push('Número entero')
      } else if (numeric.numberFormat === 'DECIMAL') {
        const precision = parse(numeric.precision).value
        const valid = precision !== undefined && Number.isInteger(precision)
        const decimals = valid && precision >= 1 && precision <= 10 ? precision : DEFAULT_DECIMALS
        if (decimals !== precision) notes.push('Sin decimales configurados: la vista previa usa 2.')
        preview.decimals = decimals
        preview.step = stepFor(decimals)
        hintParts.push(`Hasta ${plural(decimals, 'decimal', 'decimales')}`)
      } else {
        preview.step = 'any'
      }
      const { min, max } = limits(
        numeric.minimum,
        numeric.maximum,
        'El valor mínimo es mayor que el máximo; la vista previa no aplica el rango.',
        notes,
      )
      preview.min = min
      preview.max = max
      const range = rangeHint(min, max)
      if (range) hintParts.push(range)
      return preview
    }
    case 'BOOLEAN':
      preview.kind = 'checkbox'
      return preview
    case 'LIST_OF_VALUES': {
      const mode = form.listOfValues.selectionMode
      preview.kind = mode === 'MULTIPLE' ? 'checkboxes' : 'select'
      if (mode === '') {
        notes.push('Tipo de selección sin elegir: la vista previa usa selección única.')
      }
      const listId = form.listOfValues.listOfValuesId?.trim()
      if (listId) preview.listOfValuesId = listId
      return preview
    }
    case 'DATE_TIME': {
      const format = form.dateTime.dateFormat
      if (format === '') {
        notes.push('Elige un formato de fecha para ver el campo.')
        return preview
      }
      preview.kind = 'date'
      preview.dateFormat = format
      hintParts.push(`Formato: ${format}`)
      return preview
    }
    case 'CURRENCY': {
      preview.kind = 'number'
      const code = form.currency.currencyCode.trim().toUpperCase()
      if (code === '') {
        notes.push('Elige una moneda para ver el símbolo.')
      } else {
        // The backend's symbol, the same one the detail shows (feature 004 FR-014, 2026-11-23).
        const symbol = currencySymbolOf(options.currencyCode, code)
        if (symbol === undefined) {
          notes.push(`Moneda ${code} no reconocida; se muestra solo el código.`)
          hintParts.push(`Moneda: ${code}`)
        } else {
          hintParts.push(symbol === code ? `Moneda: ${code}` : `Moneda: ${code} (${symbol})`)
        }
      }
      const parsed = parse(form.currency.decimals).value
      const decimals =
        parsed !== undefined && Number.isInteger(parsed) && parsed >= 0 && parsed <= 4
          ? parsed
          : DEFAULT_DECIMALS
      preview.decimals = decimals
      preview.step = stepFor(decimals)
      hintParts.push(plural(decimals, 'decimal', 'decimales'))
      return preview
    }
    case 'REFERENCE': {
      const target = form.reference.targetObjectType
      if (target === '') {
        notes.push('Elige el objeto referenciado para ver el campo.')
        return preview
      }
      const label = optionLabel(options.targetObjectType, target)
      preview.kind = 'reference'
      preview.referencePlaceholder =
        form.reference.cardinality === 'MULTIPLE'
          ? `Selecciona uno o varios: ${label}`
          : `Selecciona un objeto: ${label}`
      hintParts.push('Los objetos se podrán elegir cuando existan los formularios de producto.')
      return preview
    }
  }
}

/** The typed text with the configured case applied ("Capitalización mixta" changes nothing). */
export function applyCase(value: string, caseFormat: FieldPreview['caseFormat']): string {
  if (caseFormat === 'UPPERCASE') return value.toLocaleUpperCase('es-MX')
  if (caseFormat === 'LOWERCASE') return value.toLocaleLowerCase('es-MX')
  return value
}

/** Message for a typed text, or undefined when it follows the configuration. */
export function checkText(value: string, preview: FieldPreview): string | undefined {
  if (value === '') return undefined
  if (preview.minLength !== undefined && value.length < preview.minLength) {
    return `Escribe al menos ${preview.minLength} caracteres.`
  }
  if (preview.mask && !preview.mask.test(value)) return 'No cumple la máscara de captura.'
  return undefined
}

/** Message for a typed number, or undefined when it follows the configuration. */
export function checkNumber(value: string, preview: FieldPreview): string | undefined {
  const trimmed = value.trim()
  if (trimmed === '') return undefined
  const number = Number(trimmed)
  if (!Number.isFinite(number)) return undefined
  if (preview.integer && !Number.isInteger(number)) return 'Escribe un número entero.'
  if (preview.decimals !== undefined) {
    const decimals = trimmed.split('.')[1]?.length ?? 0
    if (decimals > preview.decimals) {
      return `Usa como máximo ${plural(preview.decimals, 'decimal', 'decimales')}.`
    }
  }
  const { min, max } = preview
  const outside = (min !== undefined && number < min) || (max !== undefined && number > max)
  if (!outside) return undefined
  if (min !== undefined && max !== undefined) return `El valor debe estar entre ${min} y ${max}.`
  if (min !== undefined) return `El valor debe ser al menos ${min}.`
  return `El valor debe ser como máximo ${max}.`
}

const DATE_TOKENS = /AAAA|DD|MM|HH|mm/g

/** The format as a regular expression and the order of its parts. */
export function datePattern(format: string): { pattern: RegExp; tokens: string[] } {
  const tokens: string[] = []
  let source = ''
  let last = 0
  for (const match of format.matchAll(DATE_TOKENS)) {
    source += format.slice(last, match.index).replace(/[.*+?^${}()|[\]\\/]/g, '\\$&')
    source += match[0] === 'AAAA' ? '(\\d{4})' : '(\\d{2})'
    tokens.push(match[0])
    last = (match.index ?? 0) + match[0].length
  }
  source += format.slice(last).replace(/[.*+?^${}()|[\]\\/]/g, '\\$&')
  return { pattern: new RegExp(`^${source}$`), tokens }
}

/** Message for a typed date, or undefined when it follows the format and exists. */
export function checkDate(value: string, format: string): string | undefined {
  const trimmed = value.trim()
  if (trimmed === '') return undefined
  const { pattern, tokens } = datePattern(format)
  const match = pattern.exec(trimmed)
  if (!match) return `Usa el formato ${format}.`
  const part: Record<string, number> = {}
  tokens.forEach((token, index) => {
    part[token] = Number(match[index + 1])
  })
  const year = part.AAAA
  const month = part.MM
  const day = part.DD
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const exists =
    month >= 1 &&
    month <= 12 &&
    day >= 1 &&
    day <= daysInMonth &&
    (part.HH === undefined || part.HH <= 23) &&
    (part.mm === undefined || part.mm <= 59)
  return exists ? undefined : 'La fecha no existe.'
}

/** "Vista previa con los primeros 50 de N valores." when a list has more than the limit. */
export function listSummary(total: number): string | undefined {
  return total > PREVIEW_VALUE_LIMIT
    ? `Vista previa con los primeros ${PREVIEW_VALUE_LIMIT} de ${total} valores.`
    : undefined
}
