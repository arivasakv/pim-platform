import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AttributesPage } from './AttributesPage'

const BASE_URL = 'http://servidor.ejemplo:9090'
const ATTRIBUTES = `${BASE_URL}/api/v1/attributes`
const GROUPS = `${BASE_URL}/api/v1/attribute-groups`

const TYPES = {
  total: 3,
  items: [
    {
      code: 'NUMERIC',
      label: 'Numérico (Integer / Float)',
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
      label: "Lista de Valores (LOV's)",
      options: {
        selectionMode: [
          { code: 'SINGLE', label: 'Selección única' },
          { code: 'MULTIPLE', label: 'Selección múltiple' },
        ],
      },
    },
  ],
}

type GroupRef = { id: string; name: string }
type Attribute = Record<string, unknown> & {
  systemId: string
  displayName: string
  groups?: GroupRef[]
}

function peso(): Attribute {
  return {
    systemId: 'ATR000001',
    displayName: 'Peso logístico',
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

/** The groups named by these attributes, plus `extra` ones. */
function groupsOf(attributes: Attribute[], extra: GroupRef[] = []): GroupRef[] {
  const all = [...attributes.flatMap((attribute) => attribute.groups ?? []), ...extra]
  return all.filter((group, index) => all.findIndex((other) => other.id === group.id) === index)
}

/**
 * A backend holding these attributes and groups (feature 005 endpoints included). `overrides`
 * answer a "METHOD url" before the default behavior, once each.
 */
function fakeBackend(
  initial: Attribute[],
  overrides: Record<string, () => FakeResponse> = {},
  options: { failList?: boolean; failGroups?: boolean; groups?: GroupRef[] } = {},
) {
  let attributes = [...initial]
  let groups = groupsOf(initial, options.groups)
  const groupsAnswer = () => {
    const items = [...groups]
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((group) => ({
        ...group,
        attributeCount: attributes.filter((attribute) =>
          (attribute.groups ?? []).some((member) => member.id === group.id),
        ).length,
      }))
    return response(200, { items, total: items.length })
  }
  const setGroups = (id: string, change: (current: GroupRef[]) => GroupRef[]) => {
    attributes = attributes.map((attribute) =>
      attribute.systemId === id
        ? {
            ...attribute,
            groups: change(attribute.groups ?? []).sort((a, b) => a.name.localeCompare(b.name)),
          }
        : attribute,
    )
  }
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    const key = `${method} ${url}`
    const override = overrides[key]
    if (override) {
      delete overrides[key]
      return Promise.resolve(override())
    }
    if (url === GROUPS && method === 'GET') {
      if (options.failGroups) return Promise.reject(new TypeError('Network down'))
      return Promise.resolve(groupsAnswer())
    }
    if (url.startsWith(`${GROUPS}/`)) {
      const [groupId, , attributeId] = url.slice(GROUPS.length + 1).split('/')
      const group = groups.find((candidate) => candidate.id === groupId)
      if (!group) {
        return Promise.resolve(response(404, { status: 404, detail: 'No existe el grupo.' }))
      }
      if (method === 'POST') {
        const [id] = JSON.parse(String(init?.body)).attributeIds as string[]
        setGroups(id, (current) => [...current, group])
        return Promise.resolve(response(200, { group, added: [id], alreadyInGroup: [] }))
      }
      if (method === 'DELETE' && attributeId) {
        setGroups(attributeId, (current) => current.filter((member) => member.id !== groupId))
        return Promise.resolve(response(204, null))
      }
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
  return Object.assign(fetchMock, {
    /** Deletes a group on the "server", as another user would. */
    dropGroup(id: string) {
      groups = groups.filter((group) => group.id !== id)
      attributes = attributes.map((attribute) => ({
        ...attribute,
        groups: (attribute.groups ?? []).filter((group) => group.id !== id),
      }))
    },
  })
}

/** The rows of the attribute list, whose card is titled after the chosen entry. */
function listButtons(entry = 'Todos los atributos') {
  const card = within(screen.getByRole('region', { name: entry }))
  return within(card.getByRole('list')).getAllByRole('button')
}

function panel() {
  return within(screen.getByRole('region', { name: 'Grupos' }))
}

async function choose(entryName: RegExp) {
  fireEvent.click(await panel().findByRole('button', { name: entryName }))
}

const LOGISTICOS = { id: 'g1', name: 'Logísticos' }
const VENTAS = { id: 'g2', name: 'Compra Ventas' }
const TEMPORAL = { id: 'g3', name: 'Temporal' }

/** Peso in two groups, Volumen in one, Activo in none (feature 009 fixtures). */
function organized(): Attribute[] {
  return [
    { ...peso(), groups: [VENTAS, LOGISTICOS] },
    {
      ...peso(),
      systemId: 'ATR000004',
      displayName: 'Volumen',
      groups: [LOGISTICOS],
    },
    activo(),
  ]
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
    expect(rows[1].textContent).toContain("ATR000002 · Lista de Valores (LOV's)")
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

  it('opens an attribute from «#/atributos?desvincular=…» in unlink mode and reports the unlink (feature 014)', async () => {
    const unlinked = {
      ...color(),
      dataType: 'BOOLEAN' as const,
      listOfValuesProperties: undefined,
      updatedAt: '2026-11-23T10:00:00Z',
    }
    const fetchMock = fakeBackend([peso(), color()], {
      [`GET ${ATTRIBUTES}/ATR000002/list-unlink`]: () =>
        response(200, {
          attributeId: 'ATR000002',
          attributeName: 'Color',
          listOfValuesId: 'LOV000001',
          listOfValuesName: 'Colores',
          filteredNodes: [],
          reviewToken: 'token-1',
        }),
      [`POST ${ATTRIBUTES}/ATR000002/list-unlink`]: () => response(200, unlinked),
    })
    window.location.hash = '#/atributos?desvincular=ATR000002'
    render(<AttributesPage />)

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Desvincular lista de «Color»' }),
    ).toBeTruthy()
    expect(window.location.hash).toBe('#/atributos')
    await screen.findByText('Se desvinculará la lista «Colores».')
    const editor = within(screen.getByRole('region', { name: 'Desvincular lista de «Color»' }))
    fireEvent.change(editor.getByLabelText('Tipo de dato'), { target: { value: 'BOOLEAN' } })
    fireEvent.click(editor.getByRole('button', { name: 'Desvincular y guardar' }))

    expect(
      await screen.findByText('Lista desvinculada de «Color». Ahora es de tipo Booleano.'),
    ).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledWith(
      `${ATTRIBUTES}/ATR000002/list-unlink`,
      expect.objectContaining({ method: 'POST' }),
    )
    window.location.hash = ''
  })

  it('shows the complete configuration of an attribute', async () => {
    fakeBackend([peso(), color()])
    render(<AttributesPage />)

    await open(/Peso logístico/)

    const detail = screen.getByRole('region', { name: 'Peso logístico' })
    expect(within(detail).getByText('ATR000001')).toBeTruthy()
    expect(within(detail).queryByText('Descripción')).toBeNull()
    expect(within(detail).getByText('Numérico Decimal (Float)')).toBeTruthy()
    expect(within(detail).queryByText('Formato')).toBeNull()
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
      screen.getByText(
        'El atributo «Peso logístico» pertenece a 1 grupo: Logísticos. Quítalo de esos grupos en la sección «Grupos» de este atributo antes de eliminarlo.',
      ),
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

  it('shows the refusal for an attribute assigned to taxonomy nodes (feature 010 FR-021)', async () => {
    const message =
      'El atributo pertenece a 1 nodo de la taxonomía: Ropa. Quítalo de esos nodos antes de eliminarlo.'
    fakeBackend([activo()], {
      [`DELETE ${ATTRIBUTES}/ATR000003`]: () =>
        response(409, {
          status: 409,
          detail: message,
          errors: [{ field: 'nodes', code: 'ATTRIBUTE_ON_TAXONOMY_NODES', message }],
          nodes: [{ systemId: 'CLS000001', name: 'Ropa' }],
        }),
    })
    render(<AttributesPage />)
    await open(/Activo/)

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar atributo' }))

    expect(await screen.findByText(message)).toBeTruthy()
    expect(screen.getByRole('region', { name: 'Activo' })).toBeTruthy()
  })

  // --- Feature 008 amendment: preview when consulting (FR-016 to FR-018) ---

  it('shows the field preview after the details of a saved attribute', async () => {
    const fetchMock = fakeBackend([peso()])
    render(<AttributesPage />)
    await open(/Peso logístico/)

    const detail = screen.getByRole('region', { name: 'Peso logístico' })
    const preview = within(detail).getByRole('region', { name: 'Vista previa del campo' })
    const created = within(detail).getByText('Creado')
    expect(created.compareDocumentPosition(preview) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    const field = within(preview).getByLabelText<HTMLInputElement>('Peso logístico (opcional)')
    expect(field.type).toBe('number')
    expect(field.step).toBe('0.01')

    fireEvent.change(field, { target: { value: '1.234' } })
    expect(within(preview).getByText('Usa como máximo 2 decimales.')).toBeTruthy()
    expect(
      fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT' || init?.method === 'POST'),
    ).toBe(false)
  })

  it('previews a saved list-of-values attribute with its values', async () => {
    fakeBackend([color()])
    render(<AttributesPage />)
    await open(/^Color/)

    const preview = screen.getByRole('region', { name: 'Vista previa del campo' })
    const group = await within(preview).findByRole('group', { name: 'Color (opcional)' })
    expect(within(group).getByRole('checkbox', { name: 'Azul' })).toBeTruthy()
    expect(within(preview).getByText('Lista: Colores · Puedes elegir varios valores')).toBeTruthy()
  })

  it('shows the first 50 values of a long list in the consultation preview', async () => {
    const values = Array.from({ length: 80 }, (_, index) => ({
      id: String(index + 1).padStart(4, '0'),
      label: `Color ${index + 1}`,
    }))
    fakeBackend([color()], {
      [`GET ${BASE_URL}/api/v1/lists-of-values/LOV000001`]: () =>
        response(200, {
          systemId: 'LOV000001',
          name: 'Colores',
          equivalencesEnabled: false,
          valueCount: 80,
          values,
          attributes: [],
        }),
    })
    render(<AttributesPage />)
    await open(/^Color/)

    const preview = screen.getByRole('region', { name: 'Vista previa del campo' })
    const group = await within(preview).findByRole('group', { name: 'Color (opcional)' })
    expect(within(group).getAllByRole('checkbox')).toHaveLength(50)
    expect(
      within(preview).getByText('Vista previa con los primeros 50 de 80 valores.'),
    ).toBeTruthy()
  })

  it('discards preview values when another attribute is opened', async () => {
    fakeBackend([peso(), activo()])
    render(<AttributesPage />)
    await open(/Peso logístico/)
    const field = () => screen.getByLabelText<HTMLInputElement>('Peso logístico (opcional)')
    fireEvent.change(field(), { target: { value: '42' } })
    expect(field().value).toBe('42')

    await open(/Activo/)
    expect(screen.getByRole('checkbox', { name: 'Activo (opcional)' })).toBeTruthy()
    await open(/Peso logístico/)
    expect(field().value).toBe('')
  })

  it('shows the preview without the development permission', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    fakeBackend([activo()])
    render(<AttributesPage />)
    await open(/Activo/)

    const preview = screen.getByRole('region', { name: 'Vista previa del campo' })
    expect(within(preview).getByRole('checkbox', { name: 'Activo (opcional)' })).toBeTruthy()
  })

  it('previews the new configuration after an edit is saved', async () => {
    fakeBackend([peso()])
    render(<AttributesPage />)
    await open(/Peso logístico/)

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    fireEvent.change(await screen.findByLabelText('Decimales'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await screen.findByText('Atributo «Peso logístico» actualizado.')
    const preview = screen.getByRole('region', { name: 'Vista previa del campo' })
    expect(within(preview).getByLabelText<HTMLInputElement>('Peso logístico (opcional)').step).toBe(
      '0.001',
    )
  })

  // --- Feature 009, User Story 1: browse by group ---

  it('shows a group panel with counts and lists the attributes of the chosen entry', async () => {
    fakeBackend(organized())
    render(<AttributesPage />)

    expect(await panel().findByRole('button', { name: 'Compra Ventas, 1 atributo' })).toBeTruthy()
    expect(panel().getByRole('button', { name: 'Todos los atributos, 3 atributos' })).toBeTruthy()
    expect(panel().getByRole('button', { name: 'Sin grupo, 1 atributo' })).toBeTruthy()
    expect(panel().getByRole('button', { name: 'Logísticos, 2 atributos' })).toBeTruthy()
    expect(listButtons()).toHaveLength(3)

    await choose(/^Logísticos/)
    expect(listButtons('Logísticos').map((row) => row.textContent)).toEqual([
      expect.stringContaining('Peso logístico'),
      expect.stringContaining('Volumen'),
    ])
    expect(screen.getByText('2 atributos')).toBeTruthy()

    await choose(/^Compra Ventas/)
    expect(listButtons('Compra Ventas')).toHaveLength(1)
    await open(/Peso logístico/)
    expect(
      within(screen.getByRole('region', { name: 'Peso logístico' })).getByText('ATR000001'),
    ).toBeTruthy()

    await choose(/^Sin grupo/)
    expect(listButtons('Sin grupo').map((row) => row.textContent)).toEqual([
      expect.stringContaining('Activo'),
    ])
    // The detail stays open while browsing (FR-009).
    expect(screen.getByRole('region', { name: 'Peso logístico' })).toBeTruthy()
  })

  it('explains an empty group and an empty "Sin grupo"', async () => {
    fakeBackend([{ ...peso(), groups: [LOGISTICOS] }], {}, { groups: [TEMPORAL] })
    render(<AttributesPage />)

    await choose(/^Temporal/)
    expect(screen.getByText('Este grupo todavía no tiene atributos.')).toBeTruthy()
    expect(
      screen.getByText(
        'Agrégalos desde la sección «Grupos» de cada atributo o en «Grupo de Atributos».',
      ),
    ).toBeTruthy()

    await choose(/^Sin grupo/)
    expect(screen.getByText('Todos los atributos pertenecen al menos a un grupo.')).toBeTruthy()
  })

  it('keeps the list usable when the groups cannot be loaded', async () => {
    fakeBackend(organized(), {}, { failGroups: true })
    render(<AttributesPage />)

    expect(await panel().findByText('No se pudieron cargar los grupos.')).toBeTruthy()
    expect(listButtons()).toHaveLength(3)
    await choose(/^Sin grupo/)
    expect(listButtons('Sin grupo')).toHaveLength(1)

    fakeBackend(organized())
    fireEvent.click(panel().getByRole('button', { name: 'Reintentar' }))
    expect(await panel().findByRole('button', { name: 'Logísticos, 2 atributos' })).toBeTruthy()
  })

  it('goes back to every attribute when the chosen group no longer exists', async () => {
    const backend = fakeBackend(organized())
    render(<AttributesPage />)
    await choose(/^Logísticos/)
    await open(/Volumen/)

    backend.dropGroup('g1')
    fireEvent.click(screen.getByRole('button', { name: 'Quitar del grupo Logísticos' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, quitar del grupo' }))

    expect(await screen.findByText('No existe el grupo.')).toBeTruthy()
    await waitFor(() => expect(listButtons()).toHaveLength(3))
    expect(panel().queryByRole('button', { name: /^Logísticos/ })).toBeNull()
  })

  // --- Feature 009, User Story 2: find attributes and groups ---

  it('searches within the chosen group and points to matches elsewhere', async () => {
    fakeBackend(organized())
    render(<AttributesPage />)
    await choose(/^Logísticos/)
    const search = screen.getByLabelText('Buscar por nombre o ID')

    fireEvent.change(search, { target: { value: 'VOL' } })
    expect(listButtons('Logísticos').map((row) => row.textContent)).toEqual([
      expect.stringContaining('Volumen'),
    ])

    fireEvent.change(search, { target: { value: 'activo' } })
    expect(
      screen.getByText('Ningún atributo de «Logísticos» coincide con la búsqueda.'),
    ).toBeTruthy()
    expect(screen.getByText('1 coincidencia en otros grupos.')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Buscar en todos los atributos' }))
    expect(listButtons().map((row) => row.textContent)).toEqual([expect.stringContaining('Activo')])
    expect(screen.getByLabelText<HTMLInputElement>('Buscar por nombre o ID').value).toBe('activo')
  })

  it('keeps the filters when changing entry and says when "Sin grupo" has no match', async () => {
    fakeBackend(organized())
    render(<AttributesPage />)
    await screen.findByText('3 atributos')

    fireEvent.change(screen.getByLabelText('Tipo de dato'), {
      target: { value: 'NUMERIC_DECIMAL' },
    })
    await choose(/^Sin grupo/)
    expect(screen.getByText('Ningún atributo sin grupo coincide con la búsqueda.')).toBeTruthy()
    expect(screen.getByText('2 coincidencias en otros grupos.')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('Buscar por nombre o ID'), { target: { value: 'nada' } })
    expect(screen.queryByText(/en otros grupos/)).toBeNull()
  })

  it('shows the first 50 rows again after changing entry', async () => {
    const many = Array.from({ length: 60 }, (_, index) => ({
      ...activo(),
      systemId: `ATR${String(index).padStart(6, '0')}`,
      displayName: `Atributo ${String(index).padStart(2, '0')}`,
    }))
    fakeBackend(many)
    render(<AttributesPage />)
    await screen.findByText('60 atributos')
    fireEvent.click(screen.getByRole('button', { name: 'Mostrar más' }))
    expect(listButtons()).toHaveLength(60)

    await choose(/^Sin grupo/)
    expect(listButtons('Sin grupo')).toHaveLength(50)
  })

  // --- Feature 009, User Story 3: groups from the attribute ---

  it('adds the open attribute to a group and updates the counts', async () => {
    fakeBackend(organized(), {}, { groups: [TEMPORAL] })
    render(<AttributesPage />)
    await open(/Activo/)

    const detail = within(screen.getByRole('region', { name: 'Activo' }))
    expect(detail.getAllByRole('term').length).toBeGreaterThan(0)
    expect(detail.queryByText('Grupos', { selector: 'dt' })).toBeNull()
    fireEvent.change(await detail.findByLabelText('Agregar a un grupo'), {
      target: { value: 'g3' },
    })
    fireEvent.click(detail.getByRole('button', { name: 'Agregar' }))

    expect(await screen.findByText('Atributo «Activo» agregado al grupo «Temporal».')).toBeTruthy()
    expect(detail.getByRole('button', { name: 'Quitar del grupo Temporal' })).toBeTruthy()
    expect(await panel().findByRole('button', { name: 'Temporal, 1 atributo' })).toBeTruthy()
    expect(panel().getByRole('button', { name: 'Sin grupo, 0 atributos' })).toBeTruthy()
  })

  it('removes the open attribute from the chosen group and keeps its detail', async () => {
    fakeBackend(organized())
    render(<AttributesPage />)
    await choose(/^Logísticos/)
    await open(/Volumen/)

    fireEvent.click(screen.getByRole('button', { name: 'Quitar del grupo Logísticos' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, quitar del grupo' }))

    expect(
      await screen.findByText('Atributo «Volumen» quitado del grupo «Logísticos».'),
    ).toBeTruthy()
    await waitFor(() => expect(listButtons('Logísticos')).toHaveLength(1))
    const detail = within(screen.getByRole('region', { name: 'Volumen' }))
    expect(detail.getByText('Sin grupos')).toBeTruthy()
    expect(panel().getByRole('button', { name: 'Sin grupo, 2 atributos' })).toBeTruthy()
  })

  it('lists the groups without change controls without the permission', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    fakeBackend(organized())
    render(<AttributesPage />)
    await open(/Peso logístico/)

    const detail = within(screen.getByRole('region', { name: 'Peso logístico' }))
    expect(detail.getByText('Compra Ventas')).toBeTruthy()
    expect(detail.queryByRole('button', { name: /Quitar/ })).toBeNull()
    expect(detail.queryByLabelText('Agregar a un grupo')).toBeNull()
  })
})
