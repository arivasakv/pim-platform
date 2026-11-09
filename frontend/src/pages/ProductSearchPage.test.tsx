import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProductSearchPage } from './ProductSearchPage'

const BASE_URL = 'http://servidor.ejemplo:9090'
const FOUND =
  'Ya existe un producto con este EAN. En Mercaderías no se crea otra vez un producto existente.'
const NOT_FOUND =
  'No encontramos un producto con este EAN. Puedes continuar con una propuesta nueva.'
const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

const LICUADORA = {
  sku: '1000001',
  ean: '7501234567890',
  title: 'Licuadora 10 velocidades 600 W',
  brand: 'MARCA DEMO',
  business: 'LIVERPOOL',
}
const BATIDORA_LIVERPOOL = {
  sku: '1000002',
  ean: '7502000000017',
  title: 'Batidora de mano 300 W',
  brand: 'HOGAR DEMO',
  business: 'LIVERPOOL',
}
const BATIDORA_SUBURBIA = { ...BATIDORA_LIVERPOOL, sku: '1000003', business: 'SUBURBIA' }

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function stubFetch(...answers: Array<() => Promise<FakeResponse>>) {
  const fetchMock = vi.fn()
  answers.forEach((answer) => fetchMock.mockImplementationOnce(answer))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function search(value: string) {
  fireEvent.change(screen.getByLabelText('EAN'), { target: { value } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
}

describe('ProductSearchPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('shows the page title, the EAN field and the search button', () => {
    render(<ProductSearchPage />)
    expect(screen.getByRole('heading', { level: 1, name: 'Buscar producto' })).toBeTruthy()
    expect(screen.getByLabelText('EAN')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeTruthy()
  })

  it('shows an existing Liverpool product with the found warning', async () => {
    const fetchMock = stubFetch(() =>
      Promise.resolve(response(200, { items: [LICUADORA], total: 1 })),
    )
    render(<ProductSearchPage />)

    search('7501234567890')

    expect(await screen.findByText(FOUND)).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const card = screen.getByRole('region', { name: 'Licuadora 10 velocidades 600 W' })
    expect(within(card).getByText('1000001')).toBeTruthy()
    expect(within(card).getByText('7501234567890')).toBeTruthy()
    expect(within(card).getByText('MARCA DEMO')).toBeTruthy()
    expect(within(card).getByText('Liverpool')).toBeTruthy()
  })

  it('lists the same EAN in Liverpool and then Suburbia', async () => {
    stubFetch(() =>
      Promise.resolve(response(200, { items: [BATIDORA_LIVERPOOL, BATIDORA_SUBURBIA], total: 2 })),
    )
    render(<ProductSearchPage />)

    search('7502000000017')

    await screen.findByText(FOUND)
    const cards = screen.getAllByRole('region', { name: 'Batidora de mano 300 W' })
    expect(cards).toHaveLength(2)
    expect(within(cards[0]).getByText('Liverpool')).toBeTruthy()
    expect(within(cards[1]).getByText('Suburbia')).toBeTruthy()
  })

  it('says the supplier can continue when no product has the EAN', async () => {
    stubFetch(() => Promise.resolve(response(200, { items: [], total: 0 })))
    render(<ProductSearchPage />)

    search('7509999999999')

    expect(await screen.findByText(NOT_FOUND)).toBeTruthy()
  })

  it.each([
    ['', 'Escribe un EAN.'],
    ['licuadora', 'El EAN solo puede contener números.'],
    ['750123456', 'El EAN debe tener 8, 12, 13 o 14 dígitos.'],
  ])('rejects %j without calling the backend', (value, message) => {
    const fetchMock = stubFetch()
    render(<ProductSearchPage />)

    search(value)

    expect(screen.getByText(message)).toBeTruthy()
    expect(screen.getByLabelText('EAN').getAttribute('aria-invalid')).toBe('true')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('submits the search with Enter (form submission)', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(response(200, { items: [], total: 0 })))
    render(<ProductSearchPage />)
    const field = screen.getByLabelText('EAN')

    fireEvent.change(field, { target: { value: '7509999999999' } })
    fireEvent.submit(field.closest('form') as HTMLFormElement)

    expect(await screen.findByText(NOT_FOUND)).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows a connection error with a retry button that repeats the search', async () => {
    const fetchMock = stubFetch(
      () => Promise.resolve(response(200, { items: [LICUADORA], total: 1 })),
      () => Promise.reject(new TypeError('Failed to fetch')),
      () => Promise.resolve(response(200, { items: [], total: 0 })),
    )
    render(<ProductSearchPage />)

    search('7501234567890')
    await screen.findByText(FOUND)
    search('7509999999999')

    expect(await screen.findByText(CONNECTION_ERROR)).toBeTruthy()
    expect(screen.queryByText(FOUND)).toBeNull()
    expect(screen.queryByText('Licuadora 10 velocidades 600 W')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText(NOT_FOUND)).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[2][0]).toBe(`${BASE_URL}/api/v1/products?ean=7509999999999`)
  })

  it('shows the field message when the backend rejects the EAN', async () => {
    stubFetch(() =>
      Promise.resolve(
        response(400, {
          status: 400,
          title: 'EAN no válido',
          errors: [{ field: 'ean', code: 'INVALID_LENGTH', message: '…' }],
        }),
      ),
    )
    render(<ProductSearchPage />)

    search('7501234567890')

    expect(await screen.findByText('El EAN debe tener 8, 12, 13 o 14 dígitos.')).toBeTruthy()
  })

  it('ignores a slow response that arrives after a newer search', async () => {
    let resolveSlow: (value: FakeResponse) => void = () => {}
    stubFetch(
      () =>
        new Promise<FakeResponse>((resolve) => {
          resolveSlow = resolve
        }),
      () => Promise.resolve(response(200, { items: [], total: 0 })),
    )
    render(<ProductSearchPage />)

    search('7501234567890')
    search('7509999999999')
    await screen.findByText(NOT_FOUND)

    await act(async () => {
      resolveSlow(response(200, { items: [LICUADORA], total: 1 }))
    })

    await waitFor(() => expect(screen.getByText(NOT_FOUND)).toBeTruthy())
    expect(screen.queryByText(FOUND)).toBeNull()
    expect(screen.queryByText('Licuadora 10 velocidades 600 W')).toBeNull()
  })

  it('offers no image or title search', () => {
    const { container } = render(<ProductSearchPage />)
    expect(container.querySelector('input[type="file"]')).toBeNull()
    expect(screen.queryByText(/imagen|foto|título/i)).toBeNull()
  })
})
