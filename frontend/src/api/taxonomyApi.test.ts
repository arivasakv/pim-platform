import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  TaxonomyApiError,
  assignAttributes,
  canChangeTaxonomy,
  createNode,
  deleteNode,
  getNode,
  getValueFilter,
  listNodes,
  moveNode,
  removeAttribute,
  removeValueFilter,
  saveValueFilter,
  updateNode,
} from './taxonomyApi'

const BASE_URL = 'http://servidor.ejemplo:9090'
const NODES = `${BASE_URL}/api/v1/taxonomy-nodes`

function answer(status: number, body?: unknown) {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function stubFetch(...responses: ReturnType<typeof answer>[]) {
  const fetchMock = vi.fn()
  for (const response of responses) fetchMock.mockResolvedValueOnce(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function call(fetchMock: ReturnType<typeof vi.fn>, index = 0) {
  const [url, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return {
    url,
    method: init.method ?? 'GET',
    headers: init.headers as Record<string, string>,
    body: init.body ? JSON.parse(String(init.body)) : undefined,
  }
}

describe('taxonomyApi', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('lists the nodes and reads one node', async () => {
    const fetchMock = stubFetch(
      answer(200, { items: [{ systemId: 'CLS000001', name: 'Ropa' }], total: 1 }),
      answer(200, { systemId: 'CLS000001', name: 'Ropa' }),
    )

    expect(await listNodes()).toEqual([{ systemId: 'CLS000001', name: 'Ropa' }])
    expect((await getNode('CLS 1')).name).toBe('Ropa')
    expect(call(fetchMock, 0).url).toBe(NODES)
    expect(call(fetchMock, 1).url).toBe(`${NODES}/CLS%201`)
  })

  it('sends changes with the permission header', async () => {
    const fetchMock = stubFetch(
      answer(201, { systemId: 'CLS000002' }),
      answer(200, { systemId: 'CLS000002' }),
      answer(204),
      answer(200, { node: {}, added: ['ATR000001'], alreadyAssigned: [] }),
      answer(204),
    )

    await createNode({ name: 'Camisas', description: null, parentId: 'CLS000001' })
    await updateNode('CLS000002', { name: 'Camisas de vestir', description: 'Formales' })
    await deleteNode('CLS000002')
    expect((await assignAttributes('CLS000002', ['ATR000001'])).added).toEqual(['ATR000001'])
    await removeAttribute('CLS000002', 'ATR000001')

    expect(call(fetchMock, 0)).toMatchObject({
      url: NODES,
      method: 'POST',
      body: { name: 'Camisas', description: null, parentId: 'CLS000001' },
    })
    expect(call(fetchMock, 0).headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
    expect(call(fetchMock, 1)).toMatchObject({ url: `${NODES}/CLS000002`, method: 'PUT' })
    expect(call(fetchMock, 2)).toMatchObject({ url: `${NODES}/CLS000002`, method: 'DELETE' })
    expect(call(fetchMock, 3)).toMatchObject({
      url: `${NODES}/CLS000002/attributes`,
      method: 'POST',
      body: { attributeIds: ['ATR000001'] },
    })
    expect(call(fetchMock, 4)).toMatchObject({
      url: `${NODES}/CLS000002/attributes/ATR000001`,
      method: 'DELETE',
    })
  })

  it('moves a node under another node or to the top level (feature 011)', async () => {
    const fetchMock = stubFetch(
      answer(200, { node: { systemId: 'CLS000003' }, movedNodeCount: 2 }),
      answer(200, { node: { systemId: 'CLS000006' }, movedNodeCount: 1 }),
    )

    expect(await moveNode('CLS000003', 'CLS000006')).toEqual({
      node: { systemId: 'CLS000003' },
      movedNodeCount: 2,
    })
    await moveNode('CLS000006', null)

    expect(call(fetchMock, 0)).toMatchObject({
      url: `${NODES}/CLS000003/move`,
      method: 'POST',
      body: { parentId: 'CLS000006' },
    })
    expect(call(fetchMock, 0).headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
    expect(call(fetchMock, 0).headers['Content-Type']).toBe('application/json')
    expect(call(fetchMock, 1).body).toEqual({ parentId: null })
  })

  it('reads, saves and removes list-value filters (feature 012)', async () => {
    const detail = { node: { systemId: 'CLS000002', name: 'Ropa de hombre' }, allowedValues: [] }
    const fetchMock = stubFetch(answer(200, detail), answer(200, detail), answer(204))

    expect(await getValueFilter('CLS000002', 'ATR000001')).toEqual(detail)
    await saveValueFilter('CLS000002', 'ATR000001', ['0002', '0004'])
    await removeValueFilter('CLS000002', 'ATR000001')

    const path = `${NODES}/CLS000002/value-filters/ATR000001`
    expect(call(fetchMock, 0)).toMatchObject({ url: path, method: 'GET' })
    expect(call(fetchMock, 1)).toMatchObject({
      url: path,
      method: 'PUT',
      body: { valueIds: ['0002', '0004'] },
    })
    expect(call(fetchMock, 1).headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
    expect(call(fetchMock, 2)).toMatchObject({ url: path, method: 'DELETE' })
  })

  it('reports a refused filter with its message', async () => {
    const detail =
      'El valor «Verde» (0003) no está permitido por el filtro de «Ropa de hombre». Solo puedes incluir valores permitidos arriba.'
    stubFetch(
      answer(409, {
        detail,
        errors: [{ field: 'valueIds', code: 'VALUE_EXCLUDED_ABOVE', message: detail }],
      }),
    )

    const error = await saveValueFilter('CLS000003', 'ATR000001', ['0003']).catch(
      (caught: unknown) => caught,
    )

    expect((error as TaxonomyApiError).kind).toBe('conflict')
    expect((error as TaxonomyApiError).detail).toBe(detail)
  })

  it('reports a refused move with its message', async () => {
    const detail = '«Camisas» ya está en «Ropa de hombre».'
    stubFetch(
      answer(409, {
        detail,
        errors: [{ field: 'parentId', code: 'NODE_ALREADY_THERE', message: detail }],
      }),
    )

    const error = await moveNode('CLS000003', 'CLS000002').catch((caught: unknown) => caught)

    expect((error as TaxonomyApiError).kind).toBe('conflict')
    expect((error as TaxonomyApiError).detail).toBe(detail)
  })

  it('turns problems into errors with the backend message', async () => {
    const detail = 'Ya existe un nodo «Ropa» en este nivel. Usa otro nombre.'
    stubFetch(
      answer(409, {
        detail,
        errors: [{ field: 'name', code: 'NODE_NAME_IN_USE', message: detail }],
      }),
    )

    const error = await createNode({ name: 'Ropa' }).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(TaxonomyApiError)
    expect((error as TaxonomyApiError).kind).toBe('conflict')
    expect((error as TaxonomyApiError).detail).toBe(detail)
    expect((error as TaxonomyApiError).errors[0].field).toBe('name')
  })

  it('reports an unreachable backend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Network down')))

    const error = await listNodes().catch((caught: unknown) => caught)

    expect((error as TaxonomyApiError).kind).toBe('unavailable')
  })

  it('reads the development permission setting', () => {
    expect(canChangeTaxonomy()).toBe(true)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'OTHER')
    expect(canChangeTaxonomy()).toBe(false)
  })
})
