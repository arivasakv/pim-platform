import { describe, expect, it } from 'vitest'
import { EAN_MESSAGES, validateEan } from './ean'

describe('validateEan', () => {
  it.each([
    ['7501234567890', '7501234567890'],
    ['750 1234-567890', '7501234567890'],
    ['12345670', '12345670'],
    ['17501234567893', '17501234567893'],
  ])('accepts %s', (raw, expected) => {
    expect(validateEan(raw)).toEqual({ ok: true, ean: expected })
  })

  it.each(['', '   '])('rejects %j as REQUIRED', (raw) => {
    expect(validateEan(raw)).toEqual({ ok: false, code: 'REQUIRED' })
  })

  it('rejects more than 20 characters as TOO_LONG', () => {
    expect(validateEan('750123456789012345678')).toEqual({ ok: false, code: 'TOO_LONG' })
  })

  it('rejects letters as INVALID_CHARACTERS', () => {
    expect(validateEan('licuadora')).toEqual({ ok: false, code: 'INVALID_CHARACTERS' })
  })

  it('rejects a wrong number of digits as INVALID_LENGTH', () => {
    expect(validateEan('750123456')).toEqual({ ok: false, code: 'INVALID_LENGTH' })
  })
})

describe('EAN_MESSAGES', () => {
  it('has the Spanish text for every code', () => {
    expect(EAN_MESSAGES).toEqual({
      REQUIRED: 'Escribe un EAN.',
      TOO_LONG: 'El EAN no puede tener más de 20 caracteres.',
      INVALID_CHARACTERS: 'El EAN solo puede contener números.',
      INVALID_LENGTH: 'El EAN debe tener 8, 12, 13 o 14 dígitos.',
    })
  })
})
