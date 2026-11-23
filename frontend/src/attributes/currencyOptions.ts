import type { AttributeOption } from '../api/attributesApi'
import type { SelectOption } from '../components/ui'

/**
 * The currency selector of a currency attribute (feature 004 FR-014, amended 2026-11-23). The list,
 * the Spanish names and the symbols come from the backend, so the form, the field preview and the
 * detail always show the same symbol.
 */

/** "$ · MXN · Peso mexicano"; the symbol is left out when it is the code itself (CHF). */
export function currencyLabel(option: AttributeOption): string {
  const symbol = option.symbol && option.symbol !== option.code ? `${option.symbol} · ` : ''
  return `${symbol}${option.code} · ${option.label}`
}

/**
 * The selector's choices. A stored code that is not in the list (an attribute saved before the
 * selector) is offered too, so editing does not lose it.
 */
export function currencyChoices(
  options: AttributeOption[] | undefined,
  current: string,
): SelectOption[] {
  const choices = (options ?? []).map((option) => ({
    code: option.code,
    label: currencyLabel(option),
  }))
  const code = current.trim().toUpperCase()
  if (code !== '' && !choices.some((choice) => choice.code === code)) {
    choices.unshift({ code, label: code })
  }
  return choices
}

/** The symbol of a currency in the list, or undefined when the code is not in it. */
export function currencySymbolOf(
  options: AttributeOption[] | undefined,
  code: string,
): string | undefined {
  return options?.find((option) => option.code === code)?.symbol
}
