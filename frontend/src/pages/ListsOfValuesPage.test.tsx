import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ListsOfValuesPage } from './ListsOfValuesPage'

const BASE_URL = 'http://servidor.ejemplo:9090'
const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

type Value = { id: string; label: string; equivalence: string | null }
type Attribute = { systemId: string; displayName: string; selectionMode: string }
type List = {
  systemId: string
  name: string
  equivalencesEnabled: boolean
  values: Value[]
  attributes: Attribute[]
  next: number
}
type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function json(status: number, body?: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function problem(status: number, field: string, code: string, detail: string, extra = {}) {
  return json(status, { detail, errors: [{ field, code, message: detail }], ...extra })
}

/** A small in-memory stand-in for the lists endpoints (contract of feature 006). */
function fakeBackend() {
  const lists: List[] = []
  let nextList = 1
  let importAnswer: FakeResponse | null = null
  const view = (list: List) => ({
    systemId: list.systemId,
    name: list.name,
    equivalencesEnabled: list.equivalencesEnabled,
    valueCount: list.values.length,
    values: list.values,
    attributes: list.attributes,
    createdAt: '2026-11-11T10:00:00Z',
  })
  const handle = (method: string, path: string, body: Record<string, unknown>): FakeResponse => {
    if (path === '/api/v1/lists-of-values' && method === 'GET') {
      const items = [...lists]
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((list) => ({
          systemId: list.systemId,
          name: list.name,
          valueCount: list.values.length,
          attributeCount: list.attributes.length,
        }))
      return json(200, { items, total: items.length })
    }
    if (path === '/api/v1/lists-of-values' && method === 'POST') {
      const name = String(body.name)
      if (lists.some((list) => list.name.toLowerCase() === name.toLowerCase())) {
        const detail = `Ya existe una lista con el nombre "${name}". Usa otro nombre.`
        return problem(409, 'name', 'LIST_NAME_IN_USE', detail)
      }
      const list = {
        systemId: `LOV00000${nextList++}`,
        name,
        equivalencesEnabled: false,
        values: [],
        attributes: [],
        next: 1,
      }
      lists.push(list)
      return json(201, view(list))
    }
    const match = path.match(
      /^\/api\/v1\/lists-of-values\/([^/]+)(\/values(?:\/(import|[^/]+))?)?$/,
    )
    const list = match && lists.find((candidate) => candidate.systemId === match[1])
    if (!match || !list) return problem(404, 'listId', 'LIST_NOT_FOUND', 'No existe la lista.')
    const valueId = match[3] && decodeURIComponent(match[3])
    if (!match[2] && method === 'GET') return json(200, view(list))
    if (!match[2] && method === 'PUT') {
      list.name = String(body.name)
      list.equivalencesEnabled = Boolean(body.equivalencesEnabled)
      return json(200, view(list))
    }
    if (!match[2] && method === 'DELETE') {
      if (list.attributes.length > 0) {
        const detail = `La lista "${list.name}" está vinculada a ${list.attributes.length} atributos y no se puede eliminar.`
        return problem(409, 'listId', 'LIST_IN_USE', detail)
      }
      lists.splice(lists.indexOf(list), 1)
      return json(204)
    }
    if (match[3] === 'import' && method === 'POST') {
      return importAnswer ?? json(200, { added: 0, updated: 0, total: list.values.length })
    }
    if (match[2] && !match[3] && method === 'POST') {
      let id = body.id as string | undefined
      if (id && list.values.some((value) => value.id === id)) {
        const detail = `Ya existe un valor con el ID "${id}" en esta lista.`
        return problem(409, 'id', 'VALUE_ID_IN_USE', detail)
      }
      if (!id) id = String(list.next++).padStart(4, '0')
      const value = {
        id,
        label: String(body.label),
        equivalence: (body.equivalence as string) ?? null,
      }
      list.values.push(value)
      return json(201, value)
    }
    const value = list.values.find((candidate) => candidate.id === valueId)
    if (!value) return problem(404, 'valueId', 'VALUE_NOT_FOUND', 'No existe el valor.')
    if (method === 'PUT') {
      value.label = String(body.label)
      value.equivalence = (body.equivalence as string | null) ?? null
      return json(200, value)
    }
    if (method === 'DELETE') {
      list.values.splice(list.values.indexOf(value), 1)
      return json(204)
    }
    return json(500, {})
  }
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    const body =
      typeof init?.body === 'string' ? (JSON.parse(init.body) as Record<string, unknown>) : {}
    return Promise.resolve(handle(method, new URL(url).pathname, body))
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    fetchMock,
    lists,
    answerImportWith: (answer: FakeResponse) => {
      importAnswer = answer
    },
  }
}

function listsPanel() {
  return within(screen.getByRole('region', { name: 'Listas' }))
}

async function createListNamed(name: string) {
  fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: name } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear lista' }))
  await screen.findByText(`Lista «${name}» creada.`)
  return within(await screen.findByRole('region', { name }))
}

async function addValueTo(panel: ReturnType<typeof within>, label: string, id = '') {
  fireEvent.change(panel.getByLabelText('ID (opcional)'), { target: { value: id } })
  fireEvent.change(panel.getByLabelText('Etiqueta'), { target: { value: label } })
  fireEvent.click(panel.getByRole('button', { name: 'Agregar valor' }))
  await panel.findByRole('cell', { name: label })
}

describe('ListsOfValuesPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  // --- User Story 1 ---

  it('shows the empty state and creates a list', async () => {
    fakeBackend()
    render(<ListsOfValuesPage />)

    expect(screen.getByRole('heading', { level: 1, name: 'Lista de Valores' })).toBeTruthy()
    expect(screen.getByText('Cargando listas…')).toBeTruthy()
    expect(await screen.findByText('Todavía no hay listas de valores.')).toBeTruthy()
    const panel = await createListNamed('Color')

    expect(panel.getByText('LOV000001')).toBeTruthy()
    expect(panel.getByText('Esta lista todavía no tiene valores.')).toBeTruthy()
    expect(await listsPanel().findByText('0 valores · 0 atributos')).toBeTruthy()
  })

  it('adds values with and without an ID', async () => {
    const { lists } = fakeBackend()
    render(<ListsOfValuesPage />)
    await screen.findByText('Todavía no hay listas de valores.')
    const panel = await createListNamed('Color')

    await addValueTo(panel, 'Azul', 'A1')
    await addValueTo(panel, 'Rojo')

    expect(lists[0].values.map((value) => value.id)).toEqual(['A1', '0001'])
    expect(panel.getByRole('cell', { name: '0001' })).toBeTruthy()
    expect(await listsPanel().findByText('2 valores · 0 atributos')).toBeTruthy()
  })

  it('shows refusals next to the field and checks the name before calling the backend', async () => {
    const { fetchMock } = fakeBackend()
    render(<ListsOfValuesPage />)
    await screen.findByText('Todavía no hay listas de valores.')

    fireEvent.click(screen.getByRole('button', { name: 'Crear lista' }))
    expect(await screen.findByText('Escribe el nombre de la lista.')).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    const panel = await createListNamed('Color')
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'COLOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear lista' }))
    expect(
      await screen.findByText('Ya existe una lista con el nombre "COLOR". Usa otro nombre.'),
    ).toBeTruthy()

    await addValueTo(panel, 'Azul', '0001')
    fireEvent.change(panel.getByLabelText('ID (opcional)'), { target: { value: '0001' } })
    fireEvent.change(panel.getByLabelText('Etiqueta'), { target: { value: 'Otro' } })
    fireEvent.click(panel.getByRole('button', { name: 'Agregar valor' }))
    expect(
      await panel.findByText('Ya existe un valor con el ID "0001" en esta lista.'),
    ).toBeTruthy()
  })

  it('shows no change controls without the permission', async () => {
    const backend = fakeBackend()
    backend.lists.push({
      systemId: 'LOV000001',
      name: 'Color',
      equivalencesEnabled: false,
      values: [{ id: '0001', label: 'Azul', equivalence: null }],
      attributes: [],
      next: 2,
    })
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    render(<ListsOfValuesPage />)

    fireEvent.click(await listsPanel().findByRole('button', { name: /Color/ }))
    const panel = within(await screen.findByRole('region', { name: 'Color' }))

    expect(panel.getByRole('cell', { name: 'Azul' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Crear lista' })).toBeNull()
    expect(panel.queryByRole('button', { name: 'Agregar valor' })).toBeNull()
    expect(panel.queryByRole('button', { name: 'Eliminar lista' })).toBeNull()
    expect(panel.queryByRole('button', { name: /Editar Azul/ })).toBeNull()
    expect(panel.queryByLabelText('Archivo CSV')).toBeNull()
  })

  it('offers a retry when the backend cannot be reached', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    )
    render(<ListsOfValuesPage />)

    expect(await screen.findByText(CONNECTION_ERROR)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeTruthy()
  })

  // --- User Story 2 ---

  it('shows the values, counts and the attributes that use a list', async () => {
    const backend = fakeBackend()
    backend.lists.push({
      systemId: 'LOV000001',
      name: 'Color',
      equivalencesEnabled: false,
      values: [
        { id: '0001', label: 'Azul', equivalence: null },
        { id: '0002', label: 'Rojo', equivalence: null },
      ],
      attributes: [
        { systemId: 'ATR000002', displayName: 'Color base', selectionMode: 'SINGLE' },
        { systemId: 'ATR000001', displayName: 'Tono', selectionMode: 'MULTIPLE' },
      ],
      next: 3,
    })
    render(<ListsOfValuesPage />)

    expect(await listsPanel().findByText('2 valores · 2 atributos')).toBeTruthy()
    fireEvent.click(listsPanel().getByRole('button', { name: /Color/ }))
    const panel = within(await screen.findByRole('region', { name: 'Color' }))

    expect(panel.getByRole('cell', { name: '0002' })).toBeTruthy()
    expect(panel.getByText('Color base')).toBeTruthy()
    expect(panel.getByText('Selección única')).toBeTruthy()
    expect(panel.getByText('Selección múltiple')).toBeTruthy()

    fireEvent.change(panel.getByLabelText('Buscar por ID o etiqueta'), { target: { value: 'roj' } })
    expect(panel.queryByRole('cell', { name: 'Azul' })).toBeNull()
    expect(panel.getByRole('cell', { name: 'Rojo' })).toBeTruthy()
  })

  it('shows large lists 500 rows at a time', async () => {
    const backend = fakeBackend()
    backend.lists.push({
      systemId: 'LOV000001',
      name: 'Grande',
      equivalencesEnabled: false,
      values: Array.from({ length: 1200 }, (_, index) => ({
        id: String(index + 1),
        label: `Valor ${index + 1}`,
        equivalence: null,
      })),
      attributes: [],
      next: 1201,
    })
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    render(<ListsOfValuesPage />)

    fireEvent.click(await listsPanel().findByRole('button', { name: /Grande/ }))
    const panel = within(await screen.findByRole('region', { name: 'Grande' }))

    expect(panel.getAllByRole('row')).toHaveLength(501)
    expect(panel.getByText('Mostrando 500 de 1200 valores.')).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Mostrar más' }))
    expect(panel.getAllByRole('row')).toHaveLength(1001)
  })

  // --- User Story 3 ---

  it('edits a label keeping the ID and deletes a value after confirming', async () => {
    const { lists } = fakeBackend()
    render(<ListsOfValuesPage />)
    await screen.findByText('Todavía no hay listas de valores.')
    const panel = await createListNamed('Color')
    await addValueTo(panel, 'Azul', '0001')

    fireEvent.click(panel.getByRole('button', { name: 'Editar Azul (0001)' }))
    // The ID is shown as text, never as an editable field (FR-010).
    expect(panel.queryByDisplayValue('0001')).toBeNull()
    fireEvent.change(panel.getByLabelText('Etiqueta', { selector: '#editar-etiqueta-0001' }), {
      target: { value: 'AZUL' },
    })
    fireEvent.click(panel.getByRole('button', { name: 'Guardar' }))
    expect(await panel.findByRole('cell', { name: 'AZUL' })).toBeTruthy()
    expect(lists[0].values[0]).toEqual({ id: '0001', label: 'AZUL', equivalence: null })

    fireEvent.click(panel.getByRole('button', { name: 'Eliminar AZUL (0001)' }))
    expect(panel.getByText('¿Eliminar el valor «AZUL» (0001)?')).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Cancelar' }))
    expect(lists[0].values).toHaveLength(1)

    fireEvent.click(panel.getByRole('button', { name: 'Eliminar AZUL (0001)' }))
    fireEvent.click(panel.getByRole('button', { name: 'Sí, eliminar' }))
    expect(await screen.findByText('Valor «AZUL» (0001) eliminado.')).toBeTruthy()
    expect(lists[0].values).toHaveLength(0)
  })

  it('renames a list and refuses to delete one that attributes use', async () => {
    const backend = fakeBackend()
    backend.lists.push({
      systemId: 'LOV000001',
      name: 'Color',
      equivalencesEnabled: false,
      values: [],
      attributes: [
        { systemId: 'ATR000001', displayName: 'Tono', selectionMode: 'SINGLE' },
        { systemId: 'ATR000002', displayName: 'Color base', selectionMode: 'SINGLE' },
      ],
      next: 1,
    })
    render(<ListsOfValuesPage />)
    fireEvent.click(await listsPanel().findByRole('button', { name: /Color/ }))
    let panel = within(await screen.findByRole('region', { name: 'Color' }))

    fireEvent.click(panel.getByRole('button', { name: 'Editar lista' }))
    fireEvent.change(panel.getByLabelText('Nombre de la lista'), { target: { value: 'Colores' } })
    fireEvent.click(panel.getByRole('button', { name: 'Guardar' }))
    panel = within(await screen.findByRole('region', { name: 'Colores' }))

    fireEvent.click(panel.getByRole('button', { name: 'Eliminar lista' }))
    fireEvent.click(panel.getByRole('button', { name: 'Sí, eliminar lista' }))
    expect(
      await screen.findByText(
        'La lista "Colores" está vinculada a 2 atributos y no se puede eliminar.',
      ),
    ).toBeTruthy()
    expect(backend.lists).toHaveLength(1)
  })

  it('deletes an unused list after confirming', async () => {
    const { lists } = fakeBackend()
    render(<ListsOfValuesPage />)
    await screen.findByText('Todavía no hay listas de valores.')
    const panel = await createListNamed('Temporal')

    fireEvent.click(panel.getByRole('button', { name: 'Eliminar lista' }))
    expect(panel.getByText('¿Eliminar la lista «Temporal» y sus 0 valores?')).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Sí, eliminar lista' }))

    expect(await screen.findByText('Lista «Temporal» eliminada.')).toBeTruthy()
    expect(lists).toHaveLength(0)
    expect(screen.queryByRole('region', { name: 'Temporal' })).toBeNull()
  })

  // --- User Story 4 ---

  it('uploads a CSV file and reports the result', async () => {
    const backend = fakeBackend()
    render(<ListsOfValuesPage />)
    await screen.findByText('Todavía no hay listas de valores.')
    const panel = await createListNamed('Color')

    expect(panel.getByRole('link', { name: 'Descargar plantilla' }).getAttribute('href')).toBe(
      `${BASE_URL}/api/v1/lists-of-values/import-template`,
    )
    const upload = panel.getByRole('button', { name: 'Cargar archivo CSV' })
    expect(upload.hasAttribute('disabled')).toBe(true)

    backend.answerImportWith(json(200, { added: 2, updated: 1, total: 3 }))
    const file = new File(['Etiqueta\nAzul\n'], 'valores.csv', { type: 'text/csv' })
    fireEvent.change(panel.getByLabelText('Archivo CSV'), { target: { files: [file] } })
    fireEvent.click(upload)

    expect(await panel.findByText('Se agregaron 2 valores y se actualizaron 1.')).toBeTruthy()
    const [, init] = backend.fetchMock.mock.calls.find(([url]) => url.endsWith('/import'))!
    expect((init!.body as FormData).get('file')).toBe(file)
  })

  it('lists every wrong row when the file has errors', async () => {
    const backend = fakeBackend()
    render(<ListsOfValuesPage />)
    await screen.findByText('Todavía no hay listas de valores.')
    const panel = await createListNamed('Color')
    backend.answerImportWith(
      problem(
        422,
        'file',
        'INVALID_IMPORT',
        'El archivo tiene 2 errores. No se aplicó ningún valor.',
        {
          rowErrors: [
            {
              row: 4,
              column: 'ID',
              code: 'DUPLICATE_ID_IN_FILE',
              message: 'El ID "0002" está repetido.',
            },
            { row: 7, column: 'Etiqueta', code: 'REQUIRED', message: 'Este dato es obligatorio.' },
          ],
        },
      ),
    )

    const file = new File(['x'], 'valores.csv', { type: 'text/csv' })
    fireEvent.change(panel.getByLabelText('Archivo CSV'), { target: { files: [file] } })
    fireEvent.click(panel.getByRole('button', { name: 'Cargar archivo CSV' }))

    expect(
      await panel.findByText('El archivo tiene 2 errores. No se aplicó ningún valor.'),
    ).toBeTruthy()
    const report = within(panel.getByRole('table', { name: 'Errores del archivo' }))
    expect(report.getByRole('cell', { name: 'El ID "0002" está repetido.' })).toBeTruthy()
    expect(report.getByRole('cell', { name: '7' })).toBeTruthy()
    expect(report.getByRole('cell', { name: 'Etiqueta' })).toBeTruthy()
  })

  // --- User Story 5 ---

  it('turns the equivalence column on and off keeping the texts', async () => {
    const { lists } = fakeBackend()
    render(<ListsOfValuesPage />)
    await screen.findByText('Todavía no hay listas de valores.')
    const panel = await createListNamed('Color')
    expect(panel.queryByLabelText('Equivalencia (opcional)')).toBeNull()

    fireEvent.click(panel.getByLabelText('Usar columna de equivalencias'))
    const equivalence = await panel.findByLabelText('Equivalencia (opcional)')
    fireEvent.change(panel.getByLabelText('Etiqueta'), { target: { value: 'Negro intenso' } })
    fireEvent.change(equivalence, { target: { value: 'NEGRO' } })
    fireEvent.click(panel.getByRole('button', { name: 'Agregar valor' }))
    expect(await panel.findByRole('cell', { name: 'NEGRO' })).toBeTruthy()
    expect(panel.getByRole('columnheader', { name: 'Equivalencia' })).toBeTruthy()

    fireEvent.click(panel.getByLabelText('Usar columna de equivalencias'))
    await waitFor(() =>
      expect(panel.queryByRole('columnheader', { name: 'Equivalencia' })).toBeNull(),
    )
    expect(lists[0].values[0].equivalence).toBe('NEGRO')

    fireEvent.click(panel.getByLabelText('Usar columna de equivalencias'))
    expect(await panel.findByRole('cell', { name: 'NEGRO' })).toBeTruthy()
  })
})
