import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AttributeApiError,
  createAttribute,
  fetchAttributeTypes,
  listAttributes,
} from './attributesApi'

const BASE_URL = 'http://servidor.ejemplo:9090'

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function stubFetch(answer: () => Promise<FakeResponse>) {
  const fetchMock = vi.fn().mockImplementation(answer)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const BOOLEAN_REQUEST = { displayName: 'Apto para regalo', dataType: 'BOOLEAN' as const }

describe('attributesApi', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('reads the data types from the backend', async () => {
    const items = [{ code: 'BOOLEAN', label: 'Booleano', options: {} }]
    const fetchMock = stubFetch(() => Promise.resolve(response(200, { items, total: 1 })))

    await expect(fetchAttributeTypes()).resolves.toEqual(items)
    expect(fetchMock.mock.calls[0][0]).toBe(`${BASE_URL}/api/v1/attribute-types`)
  })

  it('posts the attribute with the development permission headers', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
    vi.stubEnv('VITE_DEV_MANUAL_ID_PERMISSIONS', 'ATTRIBUTE')
    const created = { systemId: 'ATR000001', ...BOOLEAN_REQUEST, scope: 'GLOBAL' }
    const fetchMock = stubFetch(() => Promise.resolve(response(201, created)))

    await expect(createAttribute(BOOLEAN_REQUEST)).resolves.toEqual(created)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${BASE_URL}/api/v1/attributes`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual(BOOLEAN_REQUEST)
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
    expect(init.headers['X-Pim-Manual-Id-Permissions']).toBe('ATTRIBUTE')
  })

  it('omits the permission headers when they are not configured', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    const fetchMock = stubFetch(() => Promise.resolve(response(201, {})))

    await createAttribute(BOOLEAN_REQUEST)

    const headers = fetchMock.mock.calls[0][1].headers
    expect(headers).not.toHaveProperty('X-Pim-Permissions')
    expect(headers).not.toHaveProperty('X-Pim-Manual-Id-Permissions')
  })

  it.each([
    [400, 'validation'],
    [403, 'forbidden'],
    [409, 'conflict'],
    [422, 'unprocessable'],
  ])(
    'turns a %i problem into an error of kind %s with its detail and field errors',
    async (status, kind) => {
      const problem = {
        detail: 'Detalle del problema',
        errors: [{ field: 'displayName', code: 'X', message: 'Mensaje' }],
      }
      stubFetch(() => Promise.resolve(response(status, problem)))

      const error = await createAttribute(BOOLEAN_REQUEST).catch((caught: unknown) => caught)

      expect(error).toBeInstanceOf(AttributeApiError)
      expect(error).toMatchObject({ kind, detail: 'Detalle del problema', errors: problem.errors })
    },
  )

  it('reports the backend as unavailable on a network failure, an unexpected status or no base URL', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')))
    await expect(createAttribute(BOOLEAN_REQUEST)).rejects.toMatchObject({ kind: 'unavailable' })

    stubFetch(() => Promise.resolve(response(500, {})))
    await expect(fetchAttributeTypes()).rejects.toMatchObject({ kind: 'unavailable' })

    vi.stubEnv('VITE_API_BASE_URL', '')
    await expect(createAttribute(BOOLEAN_REQUEST)).rejects.toMatchObject({ kind: 'unavailable' })
  })

  it('lists every attribute for the groups page (feature 005)', async () => {
    const items = [{ systemId: 'ATR000001', displayName: 'Peso', dataType: 'BOOLEAN' }]
    const fetchMock = stubFetch(() => Promise.resolve(response(200, { items, total: 1 })))

    await expect(listAttributes()).resolves.toEqual(items)
    expect(fetchMock.mock.calls[0][0]).toBe(`${BASE_URL}/api/v1/attributes`)

    stubFetch(() => Promise.resolve(response(503, {})))
    await expect(listAttributes()).rejects.toMatchObject({ kind: 'unavailable' })
  })
})
