import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AttributeGroupsPage } from './AttributeGroupsPage'

const BASE_URL = 'http://servidor.ejemplo:9090'
const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

type Member = { systemId: string; displayName: string; dataType: string }
type Group = { id: string; name: string; description: string | null; attributes: Member[] }
type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function json(status: number, body?: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function problem(status: number, field: string, code: string, detail: string): FakeResponse {
  return json(status, { detail, errors: [{ field, code, message: detail }] })
}

/**
 * A small in-memory stand-in for the backend's group and attribute endpoints, so each test drives
 * the page through real request sequences.
 */
function fakeBackend(attributes: Member[], knownIds = attributes.map((a) => a.systemId)) {
  const groups: Group[] = []
  let nextId = 1
  const view = (group: Group) => ({
    ...group,
    attributeCount: group.attributes.length,
    createdAt: '2026-11-09T10:00:00Z',
  })
  const handle = (method: string, path: string, body: Record<string, unknown>): FakeResponse => {
    if (path === '/api/v1/attributes' && method === 'GET') {
      return json(200, { items: attributes, total: attributes.length })
    }
    if (path === '/api/v1/attribute-groups' && method === 'GET') {
      const items = [...groups]
        .sort((a, b) => a.name.localeCompare(b.name))
        .map(({ id, name, description, attributes: members }) => ({
          id,
          name,
          description,
          attributeCount: members.length,
        }))
      return json(200, { items, total: items.length })
    }
    if (path === '/api/v1/attribute-groups' && method === 'POST') {
      const name = String(body.name)
      if (groups.some((group) => group.name.toLowerCase() === name.toLowerCase())) {
        const detail = `Ya existe un grupo con el nombre "${name}". Usa otro nombre.`
        return problem(409, 'name', 'GROUP_NAME_IN_USE', detail)
      }
      const group = {
        id: `g-${nextId++}`,
        name,
        description: (body.description as string | null) ?? null,
        attributes: [],
      }
      groups.push(group)
      return json(201, view(group))
    }
    const match = path.match(/^\/api\/v1\/attribute-groups\/([^/]+)(\/attributes(?:\/([^/]+))?)?$/)
    const group = match && groups.find((candidate) => candidate.id === match[1])
    if (!match || !group) return problem(404, 'groupId', 'GROUP_NOT_FOUND', 'No existe el grupo.')
    if (!match[2] && method === 'GET') return json(200, view(group))
    if (!match[2] && method === 'PUT') {
      group.name = String(body.name)
      group.description = (body.description as string | null) ?? null
      return json(200, view(group))
    }
    if (!match[2] && method === 'DELETE') {
      if (group.attributes.length > 0) {
        const detail = `El grupo "${group.name}" tiene ${group.attributes.length} atributos. Quítalos antes de eliminarlo.`
        return problem(409, 'groupId', 'GROUP_NOT_EMPTY', detail)
      }
      groups.splice(groups.indexOf(group), 1)
      return json(204)
    }
    if (match[2] && !match[3] && method === 'POST') {
      const ids = body.attributeIds as string[]
      const unknown = ids.find((id) => !knownIds.includes(id))
      if (unknown) {
        const detail = `No existe un atributo con el ID ${unknown}.`
        return problem(422, 'attributeIds', 'ATTRIBUTE_NOT_FOUND', detail)
      }
      const added = ids.filter((id) => !group.attributes.some((m) => m.systemId === id))
      group.attributes.push(...attributes.filter((a) => added.includes(a.systemId)))
      return json(200, { group: view(group), added, alreadyInGroup: [] })
    }
    if (match[3] && method === 'DELETE') {
      group.attributes = group.attributes.filter((m) => m.systemId !== match[3])
      return json(204)
    }
    return json(500, {})
  }
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET'
    const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : {}
    return Promise.resolve(handle(method, new URL(url).pathname, body))
  })
  vi.stubGlobal('fetch', fetchMock)
  return { fetchMock, groups }
}

const PESO = { systemId: 'ATR000001', displayName: 'Peso', dataType: 'NUMERIC' }
const VOLUMEN = { systemId: 'ATR000002', displayName: 'Volumen', dataType: 'NUMERIC' }
const COLOR = { systemId: 'ATR000003', displayName: 'Color', dataType: 'TEXT' }

function groupList() {
  return within(screen.getByRole('region', { name: 'Grupos' }))
}

async function createGroupNamed(name: string) {
  fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: name } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))
  await screen.findByText(`Grupo «${name}» creado.`)
}

async function openGroup(name: string) {
  fireEvent.click(await groupList().findByRole('button', { name: new RegExp(name) }))
  return within(await screen.findByRole('region', { name }))
}

async function addToGroup(panel: ReturnType<typeof within>, ...labels: string[]) {
  fireEvent.click(panel.getByRole('button', { name: 'Agregar atributos' }))
  for (const label of labels) fireEvent.click(await panel.findByLabelText(new RegExp(label)))
  fireEvent.click(panel.getByRole('button', { name: /^Agregar seleccionados/ }))
}

describe('AttributeGroupsPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  // --- User Story 1 ---

  it('shows the empty state and the create form', async () => {
    fakeBackend([PESO])
    render(<AttributeGroupsPage />)

    expect(screen.getByRole('heading', { level: 1, name: 'Grupo de Atributos' })).toBeTruthy()
    expect(screen.getByText('Cargando grupos…')).toBeTruthy()
    expect(await screen.findByText('Todavía no hay grupos de atributos.')).toBeTruthy()
    expect(screen.getByLabelText('Nombre')).toBeTruthy()
    expect(screen.getByLabelText('Descripción')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Crear grupo' })).toBeTruthy()
  })

  it('shows the groups but no create form without the permission', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    fakeBackend([PESO])
    render(<AttributeGroupsPage />)

    expect(await screen.findByText('Todavía no hay grupos de atributos.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Crear grupo' })).toBeNull()
    expect(screen.queryByLabelText('Nombre')).toBeNull()
  })

  it('creates a group, confirms it and lists it with no attributes', async () => {
    const { fetchMock } = fakeBackend([PESO])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')

    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: ' Dimensiones ' } })
    await createGroupNamed('Logísticos')

    expect(await groupList().findByText('Logísticos')).toBeTruthy()
    expect(groupList().getByText('Dimensiones')).toBeTruthy()
    expect(groupList().getByText('0 atributos')).toBeTruthy()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      name: 'Logísticos',
      description: 'Dimensiones',
    })
    expect(post?.[1]?.headers).toMatchObject({ 'X-Pim-Permissions': 'ATTRIBUTE_CREATE' })
  })

  it('shows the backend message next to the name when it is already used', async () => {
    fakeBackend([PESO])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logísticos')

    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'logísticos' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))

    expect(
      await screen.findByText('Ya existe un grupo con el nombre "logísticos". Usa otro nombre.'),
    ).toBeTruthy()
    expect(screen.getByLabelText('Nombre').getAttribute('aria-invalid')).toBe('true')
  })

  it('asks for a name without calling the backend', async () => {
    const { fetchMock } = fakeBackend([PESO])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')

    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))

    expect(await screen.findByText('Escribe el nombre del grupo.')).toBeTruthy()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })

  it('shows a connection error with a retry button', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', fetchMock)
    render(<AttributeGroupsPage />)

    expect(await screen.findByText(CONNECTION_ERROR)).toBeTruthy()

    fakeBackend([PESO])
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Todavía no hay grupos de atributos.')).toBeTruthy()
  })

  // --- User Story 2 ---

  it('adds attributes from the picker and updates the count', async () => {
    fakeBackend([COLOR, PESO, VOLUMEN])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logísticos')
    const panel = await openGroup('Logísticos')
    expect(panel.getByText('Este grupo todavía no tiene atributos.')).toBeTruthy()

    await addToGroup(panel, 'Peso', 'Volumen')

    expect(await panel.findByText('Volumen')).toBeTruthy()
    expect(panel.getByText('Peso')).toBeTruthy()
    expect(panel.getAllByText('Numérico')).toHaveLength(2)
    expect(panel.getByText('ATR000001')).toBeTruthy()
    expect(await groupList().findByText('2 atributos')).toBeTruthy()
    expect(panel.queryByLabelText(/Peso \(ATR000001\)/)).toBeNull()
    expect(panel.getByLabelText('Color (ATR000003)')).toBeTruthy()
  })

  it('shows the backend message when an attribute does not exist', async () => {
    fakeBackend([PESO], [])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logísticos')
    const panel = await openGroup('Logísticos')

    await addToGroup(panel, 'Peso')

    expect(await screen.findByText('No existe un atributo con el ID ATR000001.')).toBeTruthy()
    expect(panel.getByText('Este grupo todavía no tiene atributos.')).toBeTruthy()
  })

  it('shows the members but no change controls without the permission', async () => {
    const backend = fakeBackend([PESO])
    backend.groups.push({ id: 'g-9', name: 'Logísticos', description: null, attributes: [PESO] })
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    render(<AttributeGroupsPage />)

    const panel = await openGroup('Logísticos')

    expect(panel.getByText('Peso')).toBeTruthy()
    expect(panel.queryByRole('button', { name: 'Agregar atributos' })).toBeNull()
    expect(panel.queryByRole('button', { name: 'Quitar Peso' })).toBeNull()
    expect(panel.queryByRole('button', { name: 'Editar' })).toBeNull()
    expect(panel.queryByRole('button', { name: 'Eliminar grupo' })).toBeNull()
  })

  // --- User Story 3 ---

  it('removes an attribute, which can then be picked again', async () => {
    fakeBackend([PESO, VOLUMEN])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logísticos')
    const panel = await openGroup('Logísticos')
    await addToGroup(panel, 'Peso', 'Volumen')
    await panel.findByText('Volumen')

    fireEvent.click(panel.getByRole('button', { name: 'Quitar Peso' }))

    await waitFor(() => expect(panel.queryByText('Peso')).toBeNull())
    expect(panel.getByText('Volumen')).toBeTruthy()
    expect(await panel.findByLabelText('Peso (ATR000001)')).toBeTruthy()
    expect(await groupList().findByText('1 atributo')).toBeTruthy()
  })

  // --- User Story 5 ---

  it('renames a group and keeps its attributes', async () => {
    fakeBackend([PESO])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logisticos')
    const panel = await openGroup('Logisticos')
    await addToGroup(panel, 'Peso')
    await panel.findByText('ATR000001')

    fireEvent.click(panel.getByRole('button', { name: 'Editar' }))
    fireEvent.change(panel.getByLabelText('Nombre del grupo'), { target: { value: 'Logística' } })
    fireEvent.click(panel.getByRole('button', { name: 'Guardar' }))

    const renamed = within(await screen.findByRole('region', { name: 'Logística' }))
    expect(renamed.getByText('Peso')).toBeTruthy()
    expect(await groupList().findByText('Logística')).toBeTruthy()
  })

  it('refuses to delete a group with attributes, then deletes it once empty', async () => {
    const backend = fakeBackend([PESO])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logísticos')
    const panel = await openGroup('Logísticos')
    await addToGroup(panel, 'Peso')
    await panel.findByText('ATR000001')

    // Feature 009 FR-015: a group with attributes is not offered for deletion.
    fireEvent.click(panel.getByRole('button', { name: 'Eliminar grupo' }))
    expect(
      panel.getByText(
        'El grupo «Logísticos» tiene 1 atributo. Quítalos del grupo antes de eliminarlo; los atributos no se eliminan.',
      ),
    ).toBeTruthy()
    expect(panel.queryByRole('button', { name: 'Sí, eliminar grupo' })).toBeNull()
    const deletes = backend.fetchMock.mock.calls.filter(([, init]) => init?.method === 'DELETE')
    expect(deletes).toHaveLength(0)
    fireEvent.click(panel.getByRole('button', { name: 'Cancelar' }))

    fireEvent.click(panel.getByRole('button', { name: 'Quitar Peso' }))
    await panel.findByText('Este grupo todavía no tiene atributos.')
    fireEvent.click(panel.getByRole('button', { name: 'Eliminar grupo' }))
    expect(
      panel.getByText('¿Eliminar el grupo «Logísticos»? Ningún atributo se elimina.'),
    ).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Cancelar' }))
    expect(backend.groups).toHaveLength(1)
    fireEvent.click(panel.getByRole('button', { name: 'Eliminar grupo' }))
    fireEvent.click(panel.getByRole('button', { name: 'Sí, eliminar grupo' }))

    expect(await screen.findByText('Grupo «Logísticos» eliminado.')).toBeTruthy()
    expect(await screen.findByText('Todavía no hay grupos de atributos.')).toBeTruthy()
  })

  // --- Feature 009, User Story 4: scalable picker ---

  it('finds attributes to add and keeps the chosen ones across searches', async () => {
    fakeBackend([COLOR, PESO, VOLUMEN])
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logísticos')
    const panel = await openGroup('Logísticos')
    fireEvent.click(panel.getByRole('button', { name: 'Agregar atributos' }))
    const search = await panel.findByLabelText('Buscar atributo')

    fireEvent.change(search, { target: { value: 'VOL' } })
    expect(panel.queryByLabelText('Peso (ATR000001)')).toBeNull()
    fireEvent.click(panel.getByLabelText('Volumen (ATR000002)'))
    fireEvent.change(search, { target: { value: 'atr000003' } })
    fireEvent.click(panel.getByLabelText('Color (ATR000003)'))
    fireEvent.change(search, { target: { value: 'nada' } })
    expect(panel.getByText('Ningún atributo coincide con la búsqueda.')).toBeTruthy()
    fireEvent.change(search, { target: { value: '' } })
    expect(panel.getByLabelText<HTMLInputElement>('Volumen (ATR000002)').checked).toBe(true)

    fireEvent.click(panel.getByRole('button', { name: 'Agregar seleccionados (2)' }))

    expect(await panel.findByText('Volumen')).toBeTruthy()
    expect(panel.getByText('Color')).toBeTruthy()
  })

  it('shows 50 attributes to add at a time', async () => {
    const many = Array.from({ length: 60 }, (_, index) => ({
      systemId: `ATR${String(index).padStart(6, '0')}`,
      displayName: `Atributo ${String(index).padStart(2, '0')}`,
      dataType: 'TEXT',
    }))
    fakeBackend(many)
    render(<AttributeGroupsPage />)
    await screen.findByText('Todavía no hay grupos de atributos.')
    await createGroupNamed('Logísticos')
    const panel = await openGroup('Logísticos')
    fireEvent.click(panel.getByRole('button', { name: 'Agregar atributos' }))

    await panel.findByLabelText('Buscar atributo')
    expect(panel.getAllByRole('checkbox')).toHaveLength(50)
    expect(panel.getByText('Mostrando 50 de 60 atributos.')).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Mostrar más' }))
    expect(panel.getAllByRole('checkbox')).toHaveLength(60)
  })
})
