import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { getPathFromHash, useCurrentPath } from './navigation'

describe('getPathFromHash', () => {
  it('returns the home path for an empty hash', () => {
    expect(getPathFromHash('')).toBe('/')
  })

  it('returns the home path for "#/"', () => {
    expect(getPathFromHash('#/')).toBe('/')
  })

  it('returns the home path for an unknown hash', () => {
    expect(getPathFromHash('#/desconocido')).toBe('/')
  })

  it('returns the components path for "#/componentes"', () => {
    expect(getPathFromHash('#/componentes')).toBe('/componentes')
  })

  it('returns the product search path for "#/buscar-producto"', () => {
    expect(getPathFromHash('#/buscar-producto')).toBe('/buscar-producto')
  })
})

describe('getPathFromHash for the attribute creation page (feature 004)', () => {
  it('returns the attribute creation path for "#/crear-atributo"', () => {
    expect(getPathFromHash('#/crear-atributo')).toBe('/crear-atributo')
  })
})

describe('useCurrentPath', () => {
  afterEach(() => {
    window.location.hash = ''
  })

  it('follows the hash after a hashchange event', () => {
    window.location.hash = '#/desconocido'
    const { result } = renderHook(() => useCurrentPath())
    expect(result.current).toBe('/')

    act(() => {
      window.location.hash = '#/'
      window.dispatchEvent(new HashChangeEvent('hashchange'))
    })

    expect(result.current).toBe('/')
  })
})
