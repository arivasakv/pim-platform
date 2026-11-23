import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ListOfValuesApiError,
  addValue,
  applyBulkLoad,
  bulkLoadTemplateUrl,
  canChangeLists,
  checkBulkLoad,
  createList,
  deleteList,
  deleteValue,
  getList,
  importTemplateUrl,
  importValues,
  listLists,
  updateList,
  updateValue,
} from './listsOfValuesApi'

const BASE_URL = 'http://servidor.ejemplo:9090'
const LISTS = `${BASE_URL}/api/v1/lists-of-values`

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body?: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function stubFetch(answer: () => Promise<FakeResponse>) {
  const fetchMock = vi.fn().mockImplementation(answer)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const LIST = {
  systemId: 'LOV000001',
  name: 'Color',
  equivalencesEnabled: false,
  valueCount: 0,
  values: [],
  attributes: [],
  createdAt: '2026-11-11T10:00:00Z',
}

describe('listsOfValuesApi', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('lists and reads lists without the permission header', async () => {
    const items = [{ systemId: 'LOV000001', name: 'Color', valueCount: 2, attributeCount: 1 }]
    const fetchMock = stubFetch(() => Promise.resolve(response(200, { items, total: 1 })))

    await expect(listLists()).resolves.toEqual(items)
    expect(fetchMock.mock.calls[0][0]).toBe(LISTS)
    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty('X-Pim-Permissions')

    fetchMock.mockImplementation(() => Promise.resolve(response(200, LIST)))
    await expect(getList('LOV 1')).resolves.toEqual(LIST)
    expect(fetchMock.mock.calls[1][0]).toBe(`${LISTS}/LOV%201`)
  })

  it('creates and changes lists with the development permission header', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(response(201, LIST)))

    await expect(createList('Color')).resolves.toEqual(LIST)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(LISTS)
    expect(init.method).toBe('POST')
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
    expect(JSON.parse(init.body)).toEqual({ name: 'Color' })

    fetchMock.mockImplementation(() => Promise.resolve(response(200, LIST)))
    await updateList('LOV000001', { name: 'Colores', equivalencesEnabled: true })
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      name: 'Colores',
      equivalencesEnabled: true,
    })

    fetchMock.mockImplementation(() => Promise.resolve(response(204)))
    await deleteList('LOV000001')
    expect(fetchMock.mock.calls[2][0]).toBe(`${LISTS}/LOV000001`)
    expect(fetchMock.mock.calls[2][1].method).toBe('DELETE')
  })

  it('sends a typed list ID and leaves a blank one out (feature 013 FR-002)', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(response(201, LIST)))

    await createList('Color', ' LOV-COLOR ')
    await createList('Color', '  ')
    await createList('Color')

    const bodies = fetchMock.mock.calls.map(([, init]) => JSON.parse(String(init.body)))
    expect(bodies).toEqual([
      { name: 'Color', systemId: 'LOV-COLOR' },
      { name: 'Color' },
      { name: 'Color' },
    ])
  })

  it('adds, updates and deletes values', async () => {
    const value = { id: '0001', label: 'Azul', equivalence: null }
    const fetchMock = stubFetch(() => Promise.resolve(response(201, value)))

    await expect(addValue('LOV000001', { label: 'Azul' })).resolves.toEqual(value)
    expect(fetchMock.mock.calls[0][0]).toBe(`${LISTS}/LOV000001/values`)

    fetchMock.mockImplementation(() => Promise.resolve(response(200, value)))
    await updateValue('LOV000001', 'a.1', { label: 'AZUL', equivalence: 'BLUE' })
    expect(fetchMock.mock.calls[1][0]).toBe(`${LISTS}/LOV000001/values/a.1`)
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      label: 'AZUL',
      equivalence: 'BLUE',
    })

    fetchMock.mockImplementation(() => Promise.resolve(response(204)))
    await deleteValue('LOV000001', '0001')
    expect(fetchMock.mock.calls[2][1].method).toBe('DELETE')
  })

  it('uploads a CSV file as multipart form data and reports row errors', async () => {
    const fetchMock = stubFetch(() =>
      Promise.resolve(response(200, { added: 2, updated: 1, total: 3 })),
    )
    const file = new File(['Etiqueta\nAzul\n'], 'valores.csv', { type: 'text/csv' })

    await expect(importValues('LOV000001', file)).resolves.toEqual({
      added: 2,
      updated: 1,
      total: 3,
    })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${LISTS}/LOV000001/values/import`)
    expect(init.body).toBeInstanceOf(FormData)
    expect((init.body as FormData).get('file')).toBe(file)
    expect(init.headers).not.toHaveProperty('Content-Type')
    expect(init.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')

    const rowErrors = [{ row: 3, column: 'ID', code: 'DUPLICATE_ID_IN_FILE', message: 'Repetido' }]
    fetchMock.mockImplementation(() =>
      Promise.resolve(
        response(422, {
          detail: 'El archivo tiene 1 error. No se aplicó ningún valor.',
          errors: [{ field: 'file', code: 'INVALID_IMPORT', message: 'x' }],
          rowErrors,
        }),
      ),
    )
    await expect(importValues('LOV000001', file)).rejects.toMatchObject({
      kind: 'unprocessable',
      detail: 'El archivo tiene 1 error. No se aplicó ningún valor.',
      rowErrors,
    })
  })

  it('checks and applies a bulk load of several lists (feature 013)', async () => {
    const file = new File(['Lista,ID,Etiqueta'], 'listas.xlsx')
    const summary = {
      lists: [
        { name: 'Talla', systemId: null, action: 'CREATE', valuesAdded: 2, valuesUpdated: 0 },
      ],
      listsToCreate: 1,
      listsToUpdate: 0,
      valuesToAdd: 2,
      valuesToUpdate: 0,
      checkToken: 'abc',
    }
    const fetchMock = stubFetch(() => Promise.resolve(response(200, summary)))

    await expect(checkBulkLoad(file)).resolves.toEqual(summary)
    const [checkUrl, checkInit] = fetchMock.mock.calls[0]
    expect(checkUrl).toBe(`${LISTS}/bulk-load/check`)
    expect((checkInit.body as FormData).get('file')).toBe(file)
    expect(checkInit.headers['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')

    const result = { lists: [], listsCreated: 1, listsUpdated: 0, valuesAdded: 2, valuesUpdated: 0 }
    fetchMock.mockImplementation(() => Promise.resolve(response(200, result)))
    await expect(applyBulkLoad(file, 'abc')).resolves.toEqual(result)
    const [applyUrl, applyInit] = fetchMock.mock.calls[1]
    expect(applyUrl).toBe(`${LISTS}/bulk-load`)
    expect((applyInit.body as FormData).get('checkToken')).toBe('abc')

    fetchMock.mockImplementation(() =>
      Promise.resolve(
        response(409, {
          detail: 'Las listas cambiaron desde la revisión. Vuelve a revisar el archivo.',
        }),
      ),
    )
    await expect(applyBulkLoad(file, 'abc')).rejects.toMatchObject({ kind: 'conflict' })
    expect(bulkLoadTemplateUrl('xlsx')).toBe(`${LISTS}/bulk-load/template?format=xlsx`)
  })

  it('turns refusals into errors with their kind and Spanish detail', async () => {
    const cases: Array<[number, string]> = [
      [400, 'validation'],
      [403, 'forbidden'],
      [404, 'notFound'],
      [409, 'conflict'],
      [422, 'unprocessable'],
    ]
    for (const [status, kind] of cases) {
      stubFetch(() =>
        Promise.resolve(
          response(status, {
            detail: 'Detalle',
            errors: [{ field: 'name', code: 'X', message: 'Mensaje' }],
          }),
        ),
      )
      const error = await createList('Color').catch((caught: unknown) => caught)
      expect(error).toBeInstanceOf(ListOfValuesApiError)
      expect(error).toMatchObject({ kind, detail: 'Detalle', errors: [{ field: 'name' }] })
    }
  })

  it('reports an unreachable backend as unavailable', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')))
    await expect(listLists()).rejects.toMatchObject({ kind: 'unavailable' })

    vi.stubEnv('VITE_API_BASE_URL', '')
    await expect(listLists()).rejects.toMatchObject({ kind: 'unavailable' })
    expect(importTemplateUrl()).toBeNull()
  })

  it('builds the template address and reads the development permission', () => {
    expect(importTemplateUrl()).toBe(`${LISTS}/import-template`)
    expect(canChangeLists()).toBe(true)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'OTHER')
    expect(canChangeLists()).toBe(false)
  })
})
