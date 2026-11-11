import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { NAVIGATION_ENTRIES, getPathFromHash, useCurrentPath } from './navigation'

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

describe('getPathFromHash for the attribute groups page (feature 005)', () => {
  it('returns the attribute groups path for "#/grupos-de-atributos"', () => {
    expect(getPathFromHash('#/grupos-de-atributos')).toBe('/grupos-de-atributos')
  })
})

describe('navigation for the lists of values page (feature 006)', () => {
  it('returns the lists path for "#/listas-de-valores"', () => {
    expect(getPathFromHash('#/listas-de-valores')).toBe('/listas-de-valores')
  })

  it('places "Listas de valores" right after "Grupos de atributos"', () => {
    const labels = NAVIGATION_ENTRIES.map((entry) => entry.label)
    expect(labels.indexOf('Listas de valores')).toBe(labels.indexOf('Grupos de atributos') + 1)
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
