import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProductSearchError, searchProductsByEan } from './productsApi'

const BASE_URL = 'http://servidor.ejemplo:9090'

function jsonResponse(status: number, body: unknown) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  })
}

async function expectSearchError(promise: Promise<unknown>) {
  const error = await promise.then(
    () => {
      throw new Error('expected the search to fail')
    },
    (reason: unknown) => reason,
  )
  expect(error).toBeInstanceOf(ProductSearchError)
  return error as ProductSearchError
}

describe('searchProductsByEan', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('calls the products endpoint with the EAN and the abort signal', async () => {
    const fetchMock = vi.fn(() => jsonResponse(200, { items: [], total: 0 }))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    await searchProductsByEan('7501234567890', controller.signal)

    expect(fetchMock).toHaveBeenCalledWith(
      `${BASE_URL}/api/v1/products?ean=7501234567890`,
      expect.objectContaining({ signal: controller.signal }),
    )
  })

  it('returns the collection from a 200 response', async () => {
    const body = {
      items: [
        {
          sku: '1000001',
          ean: '7501234567890',
          title: 'Licuadora 10 velocidades 600 W',
          brand: 'MARCA DEMO',
          business: 'LIVERPOOL',
        },
      ],
      total: 1,
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(200, body)),
    )

    await expect(searchProductsByEan('7501234567890')).resolves.toEqual(body)
  })

  it('maps a 400 Problem Details response to a validation error with its codes', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        jsonResponse(400, {
          status: 400,
          title: 'EAN no válido',
          errors: [{ field: 'ean', code: 'INVALID_LENGTH', message: '…' }],
        }),
      ),
    )

    const error = await expectSearchError(searchProductsByEan('750123456'))
    expect(error.kind).toBe('validation')
    expect(error.codes).toEqual(['INVALID_LENGTH'])
  })

  it('reports a network failure as unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    )

    expect((await expectSearchError(searchProductsByEan('7501234567890'))).kind).toBe('unavailable')
  })

  it('reports a server error as unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(500, {})),
    )

    expect((await expectSearchError(searchProductsByEan('7501234567890'))).kind).toBe('unavailable')
  })

  it('reports a missing base URL as unavailable without calling the backend', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    expect((await expectSearchError(searchProductsByEan('7501234567890'))).kind).toBe('unavailable')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rethrows an abort unchanged', async () => {
    const abort = new DOMException('The operation was aborted.', 'AbortError')
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(abort)),
    )

    await expect(searchProductsByEan('7501234567890')).rejects.toBe(abort)
  })
})
