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

  it('returns the home path for the removed components page "#/componentes"', () => {
    expect(getPathFromHash('#/componentes')).toBe('/')
  })

  it('returns the product search path for "#/buscar-producto"', () => {
    expect(getPathFromHash('#/buscar-producto')).toBe('/buscar-producto')
  })
})

describe('getPathFromHash for the attributes page (feature 007)', () => {
  it('returns the attributes path for "#/atributos"', () => {
    expect(getPathFromHash('#/atributos')).toBe('/atributos')
  })

  it('returns the home path for the replaced creation page "#/crear-atributo"', () => {
    expect(getPathFromHash('#/crear-atributo')).toBe('/')
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

  it('orders the side navigation with "Lista de Valores" before the attribute pages', () => {
    expect(NAVIGATION_ENTRIES.map((entry) => entry.label)).toEqual([
      'Inicio',
      'Buscar Producto',
      'Lista de Valores',
      'Atributos',
      'Grupo de Atributos',
    ])
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
