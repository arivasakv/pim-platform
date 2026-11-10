import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  AttributeGroupApiError,
  addAttributes,
  canChangeGroups,
  createGroup,
  deleteGroup,
  getGroup,
  listGroups,
  removeAttribute,
  updateGroup,
} from './attributeGroupsApi'

const BASE_URL = 'http://servidor.ejemplo:9090'
const GROUPS = `${BASE_URL}/api/v1/attribute-groups`

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body?: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function stubFetch(answer: () => Promise<FakeResponse>) {
  const fetchMock = vi.fn().mockImplementation(answer)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const GROUP = {
  id: 'g-1',
  name: 'Logísticos',
  description: null,
  attributeCount: 0,
  attributes: [],
  createdAt: '2026-11-09T10:00:00Z',
}

describe('attributeGroupsApi', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('lists the groups', async () => {
    const items = [{ id: 'g-1', name: 'Logísticos', attributeCount: 2 }]
    const fetchMock = stubFetch(() => Promise.resolve(response(200, { items, total: 1 })))

    await expect(listGroups()).resolves.toEqual(items)
    expect(fetchMock.mock.calls[0][0]).toBe(GROUPS)
  })

  it('creates a group with the development permission header', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(response(201, GROUP)))

    await expect(createGroup({ name: 'Logísticos', description: 'Peso' })).resolves.toEqual(GROUP)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(GROUPS)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ name: 'Logísticos', description: 'Peso' })
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
  })

  it('reads one group, encoding its identifier', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(response(200, GROUP)))

    await expect(getGroup('g 1')).resolves.toEqual(GROUP)
    expect(fetchMock.mock.calls[0][0]).toBe(`${GROUPS}/g%201`)
  })

  it('adds attributes and returns what was added', async () => {
    const result = { group: GROUP, added: ['ATR000001'], alreadyInGroup: [] }
    const fetchMock = stubFetch(() => Promise.resolve(response(200, result)))

    await expect(addAttributes('g-1', ['ATR000001'])).resolves.toEqual(result)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${GROUPS}/g-1/attributes`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ attributeIds: ['ATR000001'] })
  })

  it('removes an attribute', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(response(204)))

    await removeAttribute('g-1', 'ATR000001')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${GROUPS}/g-1/attributes/ATR000001`)
    expect(init.method).toBe('DELETE')
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
  })

  it('renames and deletes a group', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(response(200, GROUP)))

    await updateGroup('g-1', { name: 'Logística' })
    await deleteGroup('g-1')

    expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: 'Logística' })
    expect(fetchMock.mock.calls[1][0]).toBe(`${GROUPS}/g-1`)
    expect(fetchMock.mock.calls[1][1].method).toBe('DELETE')
  })

  it.each([
    [400, 'validation'],
    [403, 'forbidden'],
    [404, 'notFound'],
    [409, 'conflict'],
    [422, 'unprocessable'],
  ])('turns a %i problem into a "%s" error with the backend message', async (status, kind) => {
    const errors = [{ field: 'name', code: 'CODE', message: 'Mensaje' }]
    stubFetch(() => Promise.resolve(response(status, { detail: 'Detalle', errors })))

    const error = await createGroup({ name: 'x' }).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(AttributeGroupApiError)
    expect(error).toMatchObject({ kind, detail: 'Detalle', errors })
  })

  it('reports an unreachable backend or an unexpected status as unavailable', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')))
    await expect(listGroups()).rejects.toMatchObject({ kind: 'unavailable' })

    stubFetch(() => Promise.resolve(response(503, {})))
    await expect(listGroups()).rejects.toMatchObject({ kind: 'unavailable' })
  })

  it('knows whether the development permission allows changes', () => {
    expect(canChangeGroups()).toBe(true)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'OTHER, attribute_create ')
    expect(canChangeGroups()).toBe(true)
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    expect(canChangeGroups()).toBe(false)
  })
})
