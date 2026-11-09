/**
 * EAN rule used for instant feedback before calling the backend. It mirrors the backend's rule
 * (data-model.md, "Ean"); the backend's check stays authoritative (constitution VIII).
 */

export type EanErrorCode = 'REQUIRED' | 'TOO_LONG' | 'INVALID_CHARACTERS' | 'INVALID_LENGTH'

export const EAN_MESSAGES: Record<EanErrorCode, string> = {
  REQUIRED: 'Escribe un EAN.',
  TOO_LONG: 'El EAN no puede tener más de 20 caracteres.',
  INVALID_CHARACTERS: 'El EAN solo puede contener números.',
  INVALID_LENGTH: 'El EAN debe tener 8, 12, 13 o 14 dígitos.',
}

export const EAN_ERROR_CODES = Object.keys(EAN_MESSAGES) as EanErrorCode[]

const MAX_RAW_LENGTH = 20
const VALID_LENGTHS = [8, 12, 13, 14]

export type EanValidation = { ok: true; ean: string } | { ok: false; code: EanErrorCode }

/** Applies the rules in order: required, at most 20 characters, only digits/spaces/dashes, valid length. */
export function validateEan(raw: string): EanValidation {
  if (raw.trim() === '') return { ok: false, code: 'REQUIRED' }
  if (raw.length > MAX_RAW_LENGTH) return { ok: false, code: 'TOO_LONG' }
  if (!/^[0-9 -]*$/.test(raw)) return { ok: false, code: 'INVALID_CHARACTERS' }

  const ean = raw.replace(/[ -]/g, '')
  if (!VALID_LENGTHS.includes(ean.length)) return { ok: false, code: 'INVALID_LENGTH' }
  return { ok: true, ean }
}
