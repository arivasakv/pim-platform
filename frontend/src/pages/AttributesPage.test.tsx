import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AttributesPage } from './AttributesPage'

const BASE_URL = 'http://servidor.ejemplo:9090'
const ATTRIBUTES = `${BASE_URL}/api/v1/attributes`

const TYPES = {
  total: 3,
  items: [
    {
      code: 'NUMERIC',
      label: 'Numérico',
      options: {
        numberFormat: [
          { code: 'INTEGER', label: 'Entero' },
          { code: 'DECIMAL', label: 'Decimal' },
        ],
      },
    },
    { code: 'BOOLEAN', label: 'Booleano', options: {} },
    {
      code: 'LIST_OF_VALUES',
      label: 'Lista de valores',
      options: {
        selectionMode: [
          { code: 'SINGLE', label: 'Selección única' },
          { code: 'MULTIPLE', label: 'Selección múltiple' },
        ],
      },
    },
  ],
}

type Attribute = Record<string, unknown> & { systemId: string; displayName: string }

function peso(): Attribute {
  return {
    systemId: 'ATR000001',
    displayName: 'Peso logístico',
    description: 'Peso bruto',
    dataType: 'NUMERIC',
    scope: 'GLOBAL',
    numericProperties: { numberFormat: 'DECIMAL', precision: 2 },
    createdAt: '2026-11-10T10:00:00Z',
    groups: [{ id: 'g1', name: 'Logísticos' }],
  }
}

function color(): Attribute {
  return {
    systemId: 'ATR000002',
    displayName: 'Color',
    dataType: 'LIST_OF_VALUES',
    scope: 'GLOBAL',
    listOfValuesProperties: {
      selectionMode: 'MULTIPLE',
      listOfValuesId: 'LOV000001',
      listOfValuesName: 'Colores',
    },
    createdAt: '2026-11-10T10:00:00Z',
    groups: [],
  }
}

function activo(): Attribute {
  return {
    systemId: 'ATR000003',
    displayName: 'Activo',
    dataType: 'BOOLEAN',
    scope: 'GLOBAL',
    createdAt: '2026-11-10T10:00:00Z',
    groups: [],
  }
}

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function byName(attributes: Attribute[]) {
  return [...attributes].sort((a, b) =>
    a.displayName.toLowerCase().localeCompare(b.displayName.toLowerCase()),
  )
}

/**
 * A backend holding these attributes. `overrides` answer a "METHOD url" before the default
 * behavior, once each.
 */
function fakeBackend(
  initial: Attribute[],
  overrides: Record<string, () => FakeResponse> = {},
  options: { failList?: boolean } = {},
) {
  let attributes = [...initial]
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    const key = `${method} ${url}`
    const override = overrides[key]
    if (override) {
      delete overrides[key]
      return Promise.resolve(override())
    }
    if (url.endsWith('/api/v1/attribute-types')) return Promise.resolve(response(200, TYPES))
    if (url.endsWith('/api/v1/lists-of-values')) {
      return Promise.resolve(
        response(200, {
          items: [{ systemId: 'LOV000001', name: 'Colores', valueCount: 0, attributeCount: 1 }],
          total: 1,
        }),
      )
    }
    // The field preview (feature 008) reads the chosen list.
    if (url.endsWith('/api/v1/lists-of-values/LOV000001')) {
      return Promise.resolve(
        response(200, {
          systemId: 'LOV000001',
          name: 'Colores',
          equivalencesEnabled: false,
          valueCount: 1,
          values: [{ id: '0001', label: 'Azul' }],
          attributes: [],
        }),
      )
    }
    if (url === ATTRIBUTES && method === 'GET') {
      if (options.failList) return Promise.reject(new TypeError('Network down'))
      return Promise.resolve(response(200, { items: byName(attributes), total: attributes.length }))
    }
    if (url === ATTRIBUTES && method === 'POST') {
      const body = JSON.parse(String(init?.body))
      const created = {
        ...body,
        systemId: `ATR00000${attributes.length + 1}`,
        scope: 'GLOBAL',
        groups: [],
        createdAt: '2026-11-11T10:00:00Z',
      }
      attributes = [...attributes, created]
      return Promise.resolve(response(201, created))
    }
    const id = decodeURIComponent(url.slice(ATTRIBUTES.length + 1))
    const current = attributes.find((attribute) => attribute.systemId === id)
    if (!current) {
      return Promise.resolve(
        response(404, { status: 404, detail: `No existe un atributo con el ID ${id}.` }),
      )
    }
    if (method === 'GET') return Promise.resolve(response(200, current))
    if (method === 'PUT') {
      const body = JSON.parse(String(init?.body))
      const saved = { ...current, ...body, updatedAt: '2026-11-11T12:00:00Z' }
      attributes = attributes.map((attribute) => (attribute.systemId === id ? saved : attribute))
      return Promise.resolve(response(200, saved))
    }
    if (method === 'DELETE') {
      attributes = attributes.filter((attribute) => attribute.systemId !== id)
      return Promise.resolve(response(204, null))
    }
    return Promise.reject(new TypeError(`Unexpected ${key}`))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function listButtons() {
  return within(screen.getByRole('list')).getAllByRole('button')
}

async function open(name: RegExp) {
  fireEvent.click(await screen.findByRole('button', { name }))
  return screen.findByRole('heading', { level: 2, name: name })
}

describe('AttributesPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  // --- User Story 1: consult ---

  it('lists every attribute with its ID, type, groups and list', async () => {
    fakeBackend([peso(), color(), activo()])
    render(<AttributesPage />)

    expect(screen.getByRole('heading', { level: 1, name: 'Atributos' })).toBeTruthy()
    expect(screen.getByText('Cargando atributos…')).toBeTruthy()
    expect(await screen.findByText('3 atributos')).toBeTruthy()
    const rows = listButtons()
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringContaining('Activo'),
      expect.stringContaining('Color'),
      expect.stringContaining('Peso logístico'),
    ])
    expect(rows[1].textContent).toContain('ATR000002 · Lista de valores')
    expect(rows[1].textContent).toContain('Lista: Colores')
    expect(rows[2].textContent).toContain('Grupos: Logísticos')
    expect(rows[0].textContent).toContain('Sin grupos')
  })

  it('searches by name or ID ignoring case and accents, and filters by type', async () => {
    fakeBackend([peso(), color(), activo()])
    render(<AttributesPage />)
    await screen.findByText('3 atributos')
    const search = screen.getByLabelText('Buscar por nombre o ID')

    fireEvent.change(search, { target: { value: 'LOGISTICO' } })
    expect(screen.getByText('1 atributo')).toBeTruthy()
    expect(listButtons()[0].textContent).toContain('Peso logístico')

    fireEvent.change(search, { target: { value: 'atr000002' } })
    expect(listButtons()[0].textContent).toContain('Color')

    fireEvent.change(search, { target: { value: 'nada' } })
    expect(screen.getByText('Ningún atributo coincide con la búsqueda.')).toBeTruthy()

    fireEvent.change(search, { target: { value: '  ' } })
    fireEvent.change(screen.getByLabelText('Tipo de dato'), { target: { value: 'BOOLEAN' } })
    expect(screen.getByText('1 atributo')).toBeTruthy()
    expect(listButtons()[0].textContent).toContain('Activo')

    fireEvent.change(search, { target: { value: 'peso' } })
    expect(screen.getByText('0 atributos')).toBeTruthy()
  })

  it('shows the complete configuration of an attribute', async () => {
    fakeBackend([peso(), color()])
    render(<AttributesPage />)

    await open(/Peso logístico/)

    const detail = screen.getByRole('region', { name: 'Peso logístico' })
    expect(within(detail).getByText('ATR000001')).toBeTruthy()
    expect(within(detail).getByText('Peso bruto')).toBeTruthy()
    expect(within(detail).getByText('Numérico')).toBeTruthy()
    expect(within(detail).getByText('Decimal')).toBeTruthy()
    expect(within(detail).getByText('Logísticos')).toBeTruthy()
    expect(within(detail).getByText('Creado')).toBeTruthy()
    expect(within(detail).queryByText('Modificado')).toBeNull()

    await open(/^Color/)
    const list = screen.getByRole('region', { name: 'Color' })
    expect(within(list).getByText('Selección múltiple')).toBeTruthy()
    expect(within(list).getByText('Colores (LOV000001)')).toBeTruthy()
  })

  it('shows an empty state and a load error with a retry', async () => {
    fakeBackend([], {}, { failList: true })
    render(<AttributesPage />)

    expect(
      await screen.findByText('No se pudo conectar con el servidor. Inténtalo de nuevo.'),
    ).toBeTruthy()
    fakeBackend([])
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Todavía no hay atributos.')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Nuevo atributo' })).toBeTruthy()
  })

  it('creates an attribute with the feature 004 form and opens it', async () => {
    fakeBackend([])
    render(<AttributesPage />)
    await screen.findByText('Todavía no hay atributos.')

    fireEvent.click(screen.getByRole('button', { name: 'Nuevo atributo' }))
    await screen.findByRole('heading', { level: 2, name: 'Nuevo atributo' })
    fireEvent.change(await screen.findByLabelText('Nombre'), { target: { value: 'Apto' } })
    fireEvent.change(screen.getAllByLabelText('Tipo de dato')[0], {
      target: { value: 'BOOLEAN' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Crear atributo' }))

    expect(await screen.findByText('Atributo «Apto» creado con el ID ATR000001.')).toBeTruthy()
    expect(await screen.findByRole('heading', { level: 2, name: 'Apto' })).toBeTruthy()
    await waitFor(() => expect(screen.getByText('1 atributo')).toBeTruthy())
  })

  it('shows no change controls without the development permission', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    fakeBackend([peso()])
    render(<AttributesPage />)

    await open(/Peso logístico/)

    expect(screen.queryByRole('button', { name: 'Nuevo atributo' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Editar' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Eliminar' })).toBeNull()
  })

  it('shows 50 rows at a time', async () => {
    const many = Array.from({ length: 60 }, (_, index) => ({
      ...activo(),
      systemId: `ATR${String(index).padStart(6, '0')}`,
      displayName: `Atributo ${String(index).padStart(2, '0')}`,
    }))
    fakeBackend(many)
    render(<AttributesPage />)
    await screen.findByText('60 atributos')

    expect(listButtons()).toHaveLength(50)
    expect(screen.getByText('Mostrando 50 de 60 atributos.')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Mostrar más' }))
    expect(listButtons()).toHaveLength(60)
  })

  // --- User Story 2: edit ---

  it('edits an attribute and shows the change', async () => {
    fakeBackend([peso()])
    render(<AttributesPage />)
    await open(/Peso logístico/)

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    await screen.findByRole('heading', { level: 2, name: 'Editar atributo' })
    const name = await screen.findByLabelText<HTMLInputElement>('Nombre')
    expect(name.value).toBe('Peso logístico')
    fireEvent.change(name, { target: { value: 'Peso neto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText('Atributo «Peso neto» actualizado.')).toBeTruthy()
    const detail = await screen.findByRole('region', { name: 'Peso neto' })
    expect(within(detail).getByText('Modificado')).toBeTruthy()
    await waitFor(() => expect(listButtons()[0].textContent).toContain('Peso neto'))
  })

  it('cancels an edit without changes', async () => {
    const fetchMock = fakeBackend([peso()])
    render(<AttributesPage />)
    await open(/Peso logístico/)

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))

    expect(await screen.findByRole('region', { name: 'Peso logístico' })).toBeTruthy()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false)
  })

  it('says so when the attribute was deleted meanwhile', async () => {
    fakeBackend([peso()], {
      [`PUT ${ATTRIBUTES}/ATR000001`]: () =>
        response(404, { status: 404, detail: 'No existe un atributo con el ID ATR000001.' }),
    })
    render(<AttributesPage />)
    await open(/Peso logístico/)

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText('No existe un atributo con el ID ATR000001.')).toBeTruthy()
  })

  // --- User Story 3: delete ---

  it('confirms, then deletes an ungrouped attribute and keeps its list', async () => {
    fakeBackend([color(), activo()])
    render(<AttributesPage />)
    await open(/^Color/)

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    expect(
      screen.getByText(
        '¿Eliminar el atributo «Color» (ATR000002)? La lista de valores «Colores» se conserva.',
      ),
    ).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.getByRole('region', { name: 'Color' })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar atributo' }))

    expect(await screen.findByText('Atributo «Color» eliminado.')).toBeTruthy()
    expect(screen.queryByRole('region', { name: 'Color' })).toBeNull()
    await waitFor(() => expect(screen.getByText('1 atributo')).toBeTruthy())
  })

  it('does not offer to delete an attribute that is in groups', async () => {
    fakeBackend([peso()])
    render(<AttributesPage />)
    await open(/Peso logístico/)

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(
      screen.getByText(/pertenece a 1 grupo: Logísticos\. Quítalo de esos grupos/),
    ).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Sí, eliminar atributo' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeTruthy()
  })

  it('shows the refusal when a group was added meanwhile', async () => {
    const message =
      'El atributo pertenece a 1 grupo: Ventas. Quítalo de esos grupos antes de eliminarlo.'
    const inGroup = { ...activo(), groups: [{ id: 'g2', name: 'Ventas' }] }
    fakeBackend([activo()], {
      [`DELETE ${ATTRIBUTES}/ATR000003`]: () =>
        response(409, { status: 409, detail: message, groups: [{ id: 'g2', name: 'Ventas' }] }),
    })
    render(<AttributesPage />)
    await open(/Activo/)
    // After the refusal the page reads the attribute again; this read shows the new group.
    fakeBackend([inGroup], {
      [`DELETE ${ATTRIBUTES}/ATR000003`]: () =>
        response(409, { status: 409, detail: message, groups: [{ id: 'g2', name: 'Ventas' }] }),
    })

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar atributo' }))

    expect(await screen.findByText(message)).toBeTruthy()
    const detail = screen.getByRole('region', { name: 'Activo' })
    await waitFor(() => expect(within(detail).getByText('Ventas')).toBeTruthy())
  })
})
