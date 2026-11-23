import type { Attribute, AttributeType, DataType } from '../api/attributesApi'
import type { SelectOption } from '../components/ui'

/**
 * The «Tipo de dato» choices of the owner's amendment of 2026-11-20 (feature 004 FR-007, option B).
 * The backend keeps one type `NUMERIC` with a number format; the user sees it as two choices,
 * «Numérico Entero (Integer)» and «Numérico Decimal (Float)». Every other choice is a data type code.
 */
export const INTEGER_CHOICE = 'NUMERIC_INTEGER'
export const DECIMAL_CHOICE = 'NUMERIC_DECIMAL'

const NUMERIC_CHOICES: SelectOption[] = [
  { code: INTEGER_CHOICE, label: 'Numérico Entero (Integer)' },
  { code: DECIMAL_CHOICE, label: 'Numérico Decimal (Float)' },
]

const FORMAT_OF: Record<string, string> = {
  [INTEGER_CHOICE]: 'INTEGER',
  [DECIMAL_CHOICE]: 'DECIMAL',
}

/** Labels of the stored types, for lists that only know the type code (groups, taxonomy). */
export const DATA_TYPE_LABELS: Record<string, string> = {
  TEXT: 'Texto (String)',
  NUMERIC: 'Numérico (Integer / Float)',
  BOOLEAN: 'Booleano (Sí/No)',
  LIST_OF_VALUES: "Lista de Valores (LOV's)",
  DATE_TIME: 'Fecha y Hora (Date/Time)',
  CURRENCY: 'Moneda (Currency)',
  REFERENCE: 'Referencias / Relaciones',
}

/** The dropdown choices: the backend's types in order, with `NUMERIC` replaced by its two choices. */
export function typeChoices(types: AttributeType[]): SelectOption[] {
  return types.flatMap((type) =>
    type.code === 'NUMERIC' ? NUMERIC_CHOICES : [{ code: type.code, label: type.label }],
  )
}

/** The only choices a numeric attribute may switch between while it is edited (feature 007). */
export function numericChoices(): SelectOption[] {
  return NUMERIC_CHOICES
}

/** The choice for a data type and number format; empty for a numeric type without a format. */
export function choiceOf(dataType: string, numberFormat: string | undefined): string {
  if (dataType !== 'NUMERIC') return dataType
  if (numberFormat === 'INTEGER') return INTEGER_CHOICE
  if (numberFormat === 'DECIMAL') return DECIMAL_CHOICE
  return ''
}

/** The data type and, for a numeric choice, the number format that a choice stands for. */
export function fromChoice(choice: string): { dataType: DataType | ''; numberFormat?: string } {
  const numberFormat = FORMAT_OF[choice]
  if (numberFormat) return { dataType: 'NUMERIC', numberFormat }
  return { dataType: choice as DataType | '' }
}

/** Whether an attribute matches a choice of the type filter (a plain type code matches the type). */
export function matchesChoice(attribute: Attribute, choice: string): boolean {
  return (
    attribute.dataType === choice ||
    choiceOf(attribute.dataType, attribute.numericProperties?.numberFormat) === choice
  )
}

/** The label of an attribute's choice, falling back to its type's label and then its code. */
export function attributeTypeLabel(attribute: Attribute, types: AttributeType[]): string {
  const choice = choiceOf(attribute.dataType, attribute.numericProperties?.numberFormat)
  const numeric = NUMERIC_CHOICES.find((option) => option.code === choice)
  if (numeric) return numeric.label
  return (
    types.find((type) => type.code === attribute.dataType)?.label ??
    DATA_TYPE_LABELS[attribute.dataType] ??
    attribute.dataType
  )
}
