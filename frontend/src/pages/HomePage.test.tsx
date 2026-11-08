import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { HomePage } from './HomePage'

const BACKEND_URL = 'http://localhost:8080/api/v1/hello'
const CHECKING = 'Verificando conexión con el servidor…'
const CONNECTED = 'Conexión con el servidor establecida.'
const UNAVAILABLE = 'No se pudo conectar con el servidor.'

function stubFetch(implementation: () => Promise<unknown>) {
  const fetchMock = vi.fn(implementation)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('HomePage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the checking message while the request is pending', () => {
    stubFetch(() => new Promise(() => {}))
    render(<HomePage />)
    expect(screen.getByText(CHECKING)).toBeTruthy()
  })

  it('shows the Spanish success message and never the backend text', async () => {
    stubFetch(() =>
      Promise.resolve({ ok: true, text: () => Promise.resolve('Hello from PIM Backend!') }),
    )
    render(<HomePage />)
    expect(await screen.findByText(CONNECTED)).toBeTruthy()
    expect(screen.queryByText(/Hello from PIM Backend/)).toBeNull()
  })

  it('shows the error message when the response is not OK', async () => {
    stubFetch(() => Promise.resolve({ ok: false, text: () => Promise.resolve('') }))
    render(<HomePage />)
    expect(await screen.findByText(UNAVAILABLE)).toBeTruthy()
  })

  it('shows the error message when the request fails', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')))
    render(<HomePage />)
    expect(await screen.findByText(UNAVAILABLE)).toBeTruthy()
  })

  it('calls the existing endpoint once', () => {
    const fetchMock = stubFetch(() => new Promise(() => {}))
    render(<HomePage />)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith(BACKEND_URL)
  })
})
