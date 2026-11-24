import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AttributeApiError,
  canChangeAttributes,
  createAttribute,
  deleteAttribute,
  fetchAttributeTypes,
  getAttribute,
  listAttributes,
  reviewListUnlink,
  unlinkList,
  updateAttribute,
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
    vi.stubEnv('VITE_DEV_MANUAL_ID_PERMISSIONS', '')
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

  // --- Feature 007 ---

  it('reads one attribute with its groups and list name', async () => {
    const attribute = {
      systemId: 'ATR000001',
      displayName: 'Color',
      dataType: 'LIST_OF_VALUES',
      scope: 'GLOBAL',
      listOfValuesProperties: {
        selectionMode: 'SINGLE',
        listOfValuesId: 'LOV000001',
        listOfValuesName: 'Color',
      },
      groups: [{ id: 'g1', name: 'Logísticos' }],
    }
    const fetchMock = stubFetch(() => Promise.resolve(response(200, attribute)))

    await expect(getAttribute('ATR 1')).resolves.toEqual(attribute)
    expect(fetchMock.mock.calls[0][0]).toBe(`${BASE_URL}/api/v1/attributes/ATR%201`)
  })

  it('reports an unknown attribute as not found', async () => {
    stubFetch(() =>
      Promise.resolve(response(404, { status: 404, detail: 'No existe un atributo con el ID X.' })),
    )

    await expect(getAttribute('X')).rejects.toMatchObject({
      kind: 'notFound',
      detail: 'No existe un atributo con el ID X.',
    })
  })

  it('puts an edit with the permission headers', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
    const saved = { systemId: 'ATR000001', ...BOOLEAN_REQUEST, scope: 'GLOBAL' }
    const fetchMock = stubFetch(() => Promise.resolve(response(200, saved)))

    await expect(updateAttribute('ATR000001', BOOLEAN_REQUEST)).resolves.toEqual(saved)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${BASE_URL}/api/v1/attributes/ATR000001`)
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual(BOOLEAN_REQUEST)
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
  })

  it('passes on the refusals of an edit', async () => {
    const message = 'Ya existe un atributo con el nombre "Color". Usa otro nombre.'
    stubFetch(() =>
      Promise.resolve(
        response(409, {
          status: 409,
          detail: message,
          errors: [{ field: 'displayName', code: 'DISPLAY_NAME_IN_USE', message }],
        }),
      ),
    )

    const error = await updateAttribute('ATR000001', BOOLEAN_REQUEST).catch((caught) => caught)
    expect(error).toBeInstanceOf(AttributeApiError)
    expect(error.kind).toBe('conflict')
    expect(error.errors[0].code).toBe('DISPLAY_NAME_IN_USE')
  })

  it('deletes an attribute and exposes the groups of a refusal', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
    const fetchMock = stubFetch(() => Promise.resolve(response(204, null)))

    await expect(deleteAttribute('ATR000001')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${BASE_URL}/api/v1/attributes/ATR000001`)
    expect(init.method).toBe('DELETE')
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')

    stubFetch(() =>
      Promise.resolve(
        response(409, {
          status: 409,
          detail: 'El atributo pertenece a 1 grupo: Ventas.',
          groups: [{ id: 'g2', name: 'Ventas' }],
        }),
      ),
    )
    await expect(deleteAttribute('ATR000001')).rejects.toMatchObject({
      kind: 'conflict',
      groups: [{ id: 'g2', name: 'Ventas' }],
    })
  })

  it('knows whether the development permissions allow changes', () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    expect(canChangeAttributes()).toBe(false)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'OTHER, attribute_create')
    expect(canChangeAttributes()).toBe(true)
  })

  it('reviews and unlinks the list of an attribute (feature 014)', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
    const review = {
      attributeId: 'ATR000001',
      attributeName: 'Color',
      listOfValuesId: 'LOV000001',
      listOfValuesName: 'Colores',
      filteredNodes: [{ id: 'CLS000001', name: 'Ropa' }],
      reviewToken: 'abc',
    }
    const fetchMock = stubFetch(() => Promise.resolve(response(200, review)))

    await expect(reviewListUnlink('ATR000001')).resolves.toEqual(review)
    expect(fetchMock.mock.calls[0][0]).toBe(`${BASE_URL}/api/v1/attributes/ATR000001/list-unlink`)

    const saved = {
      systemId: 'ATR000001',
      displayName: 'Color',
      dataType: 'BOOLEAN',
      scope: 'GLOBAL',
    }
    fetchMock.mockImplementation(() => Promise.resolve(response(200, saved)))
    await expect(
      unlinkList('ATR000001', { dataType: 'BOOLEAN', reviewToken: 'abc' }),
    ).resolves.toEqual(saved)
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe(`${BASE_URL}/api/v1/attributes/ATR000001/list-unlink`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ dataType: 'BOOLEAN', reviewToken: 'abc' })
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')

    fetchMock.mockImplementation(() =>
      Promise.resolve(
        response(409, { detail: 'El atributo o sus filtros cambiaron…', errors: [] }),
      ),
    )
    await expect(
      unlinkList('ATR000001', { dataType: 'BOOLEAN', reviewToken: 'abc' }),
    ).rejects.toMatchObject({ kind: 'conflict' })
    fetchMock.mockImplementation(() => Promise.resolve(response(422, { detail: 'No es lista' })))
    await expect(reviewListUnlink('ATR000002')).rejects.toMatchObject({ kind: 'unprocessable' })
  })
})
