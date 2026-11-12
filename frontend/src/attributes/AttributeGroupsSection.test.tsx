import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Attribute } from '../api/attributesApi'
import { AttributeGroupsSection } from './AttributeGroupsSection'
import type { GroupsState } from './attributeBrowsing'

const BASE_URL = 'http://servidor.ejemplo:9090'
const GROUPS_URL = `${BASE_URL}/api/v1/attribute-groups`

const GROUPS: GroupsState = {
  status: 'ready',
  groups: [
    { id: 'g2', name: 'Compra Ventas', attributeCount: 1 },
    { id: 'g1', name: 'Logísticos', attributeCount: 1 },
    { id: 'g3', name: 'Temporal', attributeCount: 0 },
  ],
}

function peso(groups = [{ id: 'g1', name: 'Logísticos' }]): Attribute {
  return {
    systemId: 'ATR000001',
    displayName: 'Peso',
    dataType: 'NUMERIC',
    scope: 'GLOBAL',
    createdAt: '2026-11-10T10:00:00Z',
    groups,
  } as Attribute
}

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body?: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

/** Answers membership changes with `change` and the attribute read with `current`. */
function stubFetch(change: FakeResponse, current: Attribute) {
  const fetchMock = vi.fn((url: string) =>
    Promise.resolve(url.includes('/api/v1/attributes/') ? response(200, current) : change),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderSection(
  attribute: Attribute,
  options: { canChange?: boolean; groupsState?: GroupsState } = {},
) {
  const onChanged = vi.fn()
  const onError = vi.fn()
  render(
    <AttributeGroupsSection
      attribute={attribute}
      groupsState={options.groupsState ?? GROUPS}
      canChange={options.canChange ?? true}
      onChanged={onChanged}
      onError={onError}
    />,
  )
  return { onChanged, onError, section: within(screen.getByRole('group', { name: 'Grupos' })) }
}

describe('AttributeGroupsSection', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('lists the groups of the attribute, or says it has none', () => {
    const { section } = renderSection(peso())
    expect(section.getByRole('heading', { level: 3, name: 'Grupos' })).toBeTruthy()
    expect(section.getByText('Logísticos')).toBeTruthy()
    expect(section.getByRole('button', { name: 'Quitar del grupo Logísticos' })).toBeTruthy()
  })

  it('says "Sin grupos" for an attribute in no group', () => {
    const { section } = renderSection(peso([]))
    expect(section.getByText('Sin grupos')).toBeTruthy()
  })

  it('removes the attribute from a group after confirming', async () => {
    const fetchMock = stubFetch(response(204), peso([]))
    const { section, onChanged } = renderSection(peso())

    fireEvent.click(section.getByRole('button', { name: 'Quitar del grupo Logísticos' }))
    expect(
      section.getByText(
        '¿Quitar «Peso» del grupo «Logísticos»? El atributo no se elimina ni cambia.',
      ),
    ).toBeTruthy()
    fireEvent.click(section.getByRole('button', { name: 'Cancelar' }))
    expect(fetchMock).not.toHaveBeenCalled()

    fireEvent.click(section.getByRole('button', { name: 'Quitar del grupo Logísticos' }))
    fireEvent.click(section.getByRole('button', { name: 'Sí, quitar del grupo' }))

    await waitFor(() =>
      expect(onChanged).toHaveBeenCalledWith(
        peso([]),
        'Atributo «Peso» quitado del grupo «Logísticos».',
      ),
    )
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe(`${GROUPS_URL}/g1/attributes/ATR000001`)
    expect(init.method).toBe('DELETE')
    expect((init.headers as Record<string, string>)['X-Pim-Permissions']).toBe('ATTRIBUTE_CREATE')
    expect(fetchMock.mock.calls[1][0]).toBe(`${BASE_URL}/api/v1/attributes/ATR000001`)
  })

  it('adds the attribute to a group it is not in yet', async () => {
    const added = peso([
      { id: 'g1', name: 'Logísticos' },
      { id: 'g3', name: 'Temporal' },
    ])
    const fetchMock = stubFetch(
      response(200, { group: {}, added: ['ATR000001'], alreadyInGroup: [] }),
      added,
    )
    const { section, onChanged } = renderSection(peso())
    const select = section.getByLabelText<HTMLSelectElement>('Agregar a un grupo')

    expect([...select.options].map((option) => option.text)).toEqual([
      'Elige un grupo',
      'Compra Ventas',
      'Temporal',
    ])
    const add = section.getByRole<HTMLButtonElement>('button', { name: 'Agregar' })
    expect(add.disabled).toBe(true)
    fireEvent.change(select, { target: { value: 'g3' } })
    fireEvent.click(add)

    await waitFor(() =>
      expect(onChanged).toHaveBeenCalledWith(
        added,
        'Atributo «Peso» agregado al grupo «Temporal».',
      ),
    )
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe(`${GROUPS_URL}/g3/attributes`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ attributeIds: ['ATR000001'] })
  })

  it('shows the backend refusal and reads the attribute again', async () => {
    const detail = 'No existe el grupo g3.'
    stubFetch(response(404, { detail, errors: [] }), peso())
    const { section, onChanged, onError } = renderSection(peso())

    fireEvent.change(section.getByLabelText('Agregar a un grupo'), { target: { value: 'g3' } })
    fireEvent.click(section.getByRole('button', { name: 'Agregar' }))

    await waitFor(() => expect(onError).toHaveBeenCalledWith(detail))
    await waitFor(() => expect(onChanged).toHaveBeenCalledWith(peso()))
  })

  it('explains when there is nothing to add', () => {
    renderSection(
      peso([
        { id: 'g1', name: 'Logísticos' },
        { id: 'g2', name: 'Compra Ventas' },
        { id: 'g3', name: 'Temporal' },
      ]),
    )
    expect(screen.getByText('El atributo ya está en todos los grupos.')).toBeTruthy()
  })

  it('explains when no group exists or groups could not be loaded', () => {
    const { section } = renderSection(peso([]), { groupsState: { status: 'ready', groups: [] } })
    expect(
      section.getByText('Todavía no hay grupos. Créalos en «Grupo de Atributos».'),
    ).toBeTruthy()
    expect(section.queryByLabelText('Agregar a un grupo')).toBeNull()
  })

  it('keeps the current groups removable when the groups could not be loaded', () => {
    const { section } = renderSection(peso(), { groupsState: { status: 'error' } })
    expect(
      section.getByText('No se pudieron cargar los grupos para agregar el atributo.'),
    ).toBeTruthy()
    expect(section.getByRole('button', { name: 'Quitar del grupo Logísticos' })).toBeTruthy()
  })

  it('shows no change controls without the permission', () => {
    const { section } = renderSection(peso(), { canChange: false })
    expect(section.getByText('Logísticos')).toBeTruthy()
    expect(section.queryByRole('button', { name: /Quitar/ })).toBeNull()
    expect(section.queryByLabelText('Agregar a un grupo')).toBeNull()
  })
})
