import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { TaxonomyPage } from './TaxonomyPage'

const BASE_URL = 'http://servidor.ejemplo:9090'
const NODES = '/api/v1/taxonomy-nodes'

type FakeAttribute = { systemId: string; displayName: string; dataType: string }
type FakeNode = {
  systemId: string
  name: string
  description: string | null
  parentId: string | null
  attributeIds: string[]
}
type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function json(status: number, body?: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function problem(status: number, field: string, code: string, detail: string): FakeResponse {
  return json(status, { detail, errors: [{ field, code, message: detail }] })
}

const ATTRIBUTES: FakeAttribute[] = [
  ['ATR000001', 'Color'],
  ['ATR000002', 'Material'],
  ['ATR000003', 'Marca'],
  ['ATR000004', 'Género'],
  ['ATR000005', 'Largo de manga'],
  ['ATR000006', 'Talla de cintura'],
  ['ATR000007', 'Temporada'],
].map(([systemId, displayName]) => ({ systemId, displayName, dataType: 'TEXT' }))

/** The feature request's example (spec US3). */
function example(): FakeNode[] {
  return [
    node('CLS000001', 'Ropa', null, 'ATR000001', 'ATR000002', 'ATR000003'),
    node('CLS000002', 'Ropa de hombre', 'CLS000001', 'ATR000004'),
    node('CLS000003', 'Camisas', 'CLS000002', 'ATR000005'),
    node('CLS000004', 'Pantalones', 'CLS000002', 'ATR000006'),
  ]
}

function node(systemId: string, name: string, parentId: string | null, ...attributeIds: string[]) {
  return { systemId, name, description: null, parentId, attributeIds }
}

/**
 * An in-memory stand-in for the taxonomy endpoints, computing effective attributes with the rules
 * of data-model.md, so the page is driven through real request sequences.
 */
function fakeBackend(initial: FakeNode[] = [], options: { failList?: boolean } = {}) {
  const nodes = initial.map((entry) => ({ ...entry, attributeIds: [...entry.attributeIds] }))
  let next = nodes.length + 1
  const byId = (id: string) => nodes.find((entry) => entry.systemId === id.toUpperCase())
  const chain = (start: FakeNode) => {
    const result: FakeNode[] = []
    let current: FakeNode | undefined = start
    while (current) {
      result.unshift(current)
      current = current.parentId ? byId(current.parentId) : undefined
    }
    return result
  }
  const ref = (entry: FakeNode) => ({ systemId: entry.systemId, name: entry.name })
  const sorted = (list: FakeNode[]) => [...list].sort((a, b) => a.name.localeCompare(b.name))
  const detail = (entry: FakeNode) => {
    const nodesUp = chain(entry)
    const origins = new Map<string, number[]>()
    nodesUp.forEach((step, depth) =>
      step.attributeIds.forEach((id) => origins.set(id, [...(origins.get(id) ?? []), depth])),
    )
    const self = nodesUp.length - 1
    const effective = [...origins.entries()]
      .map(([id, depths]) => {
        const origin = depths[depths.length - 1]
        const attribute = ATTRIBUTES.find((candidate) => candidate.systemId === id)!
        const value = {
          ...attribute,
          assignment: origin === self ? 'DIRECT' : 'INHERITED',
          origin: ref(nodesUp[origin]),
          alsoInheritedFrom:
            origin === self && depths.length > 1 ? ref(nodesUp[depths[depths.length - 2]]) : null,
        }
        return { value, order: origin * 1000 + nodesUp[origin].attributeIds.indexOf(id) }
      })
      .sort((a, b) => a.order - b.order)
      .map((entry) => entry.value)
    return {
      ...entry,
      level: nodesUp.length,
      path: nodesUp.slice(0, -1).map(ref),
      children: sorted(nodes.filter((candidate) => candidate.parentId === entry.systemId)).map(ref),
      directAttributeCount: entry.attributeIds.length,
      effectiveAttributes: effective,
      createdAt: '2026-11-12T10:00:00Z',
    }
  }
  const siblingsHave = (parentId: string | null, name: string, except?: string) =>
    nodes.some(
      (candidate) =>
        candidate.parentId === parentId &&
        candidate.name.toLowerCase() === name.trim().toLowerCase() &&
        candidate.systemId !== except,
    )
  const inUse = (name: string) =>
    problem(
      409,
      'name',
      'NODE_NAME_IN_USE',
      `Ya existe un nodo «${name}» en este nivel. Usa otro nombre.`,
    )

  const handle = (method: string, path: string, body: Record<string, unknown>): FakeResponse => {
    if (path === '/api/v1/attribute-types') {
      return json(200, { items: [{ code: 'TEXT', label: 'Texto', options: {} }], total: 1 })
    }
    if (path === '/api/v1/attributes')
      return json(200, { items: ATTRIBUTES, total: ATTRIBUTES.length })
    if (path === NODES && method === 'GET') {
      const items = sorted(nodes).map((entry) => ({
        systemId: entry.systemId,
        name: entry.name,
        parentId: entry.parentId,
        level: chain(entry).length,
        directAttributeCount: entry.attributeIds.length,
        childCount: nodes.filter((candidate) => candidate.parentId === entry.systemId).length,
      }))
      return json(200, { items, total: items.length })
    }
    if (path === NODES && method === 'POST') {
      const parentId = (body.parentId as string | null) ?? null
      const name = String(body.name)
      if (siblingsHave(parentId, name)) return inUse(name)
      const created = {
        systemId: `CLS${String(next++).padStart(6, '0')}`,
        name,
        description: (body.description as string | null) ?? null,
        parentId,
        attributeIds: [],
      }
      nodes.push(created)
      return json(201, detail(created))
    }
    const moving = path.match(/^\/api\/v1\/taxonomy-nodes\/([^/]+)\/move$/)
    if (moving && method === 'POST') {
      // Feature 011 rules, as the backend applies them.
      const moved = byId(moving[1])
      if (!moved)
        return problem(404, 'nodeId', 'NODE_NOT_FOUND', `No existe un nodo con el ID ${moving[1]}.`)
      const parentId = (body.parentId as string | null) ?? null
      const destination = parentId ? byId(parentId) : null
      if (parentId && !destination)
        return problem(
          404,
          'parentId',
          'NODE_NOT_FOUND',
          `No existe un nodo con el ID ${parentId}.`,
        )
      if (destination && chain(destination).includes(moved))
        return problem(
          409,
          'parentId',
          'NODE_MOVE_INTO_OWN_BRANCH',
          `No puedes mover «${moved.name}» dentro de sí mismo ni de uno de sus nodos descendientes.`,
        )
      if ((destination?.systemId ?? null) === moved.parentId)
        return problem(409, 'parentId', 'NODE_ALREADY_THERE', `«${moved.name}» ya está aquí.`)
      if (siblingsHave(destination?.systemId ?? null, moved.name, moved.systemId))
        return problem(
          409,
          'parentId',
          'NODE_NAME_IN_USE',
          `Ya existe un nodo «${moved.name}» en «${destination?.name ?? 'el primer nivel'}». Cambia el nombre de uno de los dos antes de mover.`,
        )
      moved.parentId = destination?.systemId ?? null
      const movedNodeCount = nodes.filter((candidate) => chain(candidate).includes(moved)).length
      return json(200, { node: detail(moved), movedNodeCount })
    }
    const match = path.match(/^\/api\/v1\/taxonomy-nodes\/([^/]+)(\/attributes(?:\/([^/]+))?)?$/)
    const found = match ? byId(match[1]) : undefined
    if (!match || !found) return problem(404, 'nodeId', 'NODE_NOT_FOUND', 'No existe el nodo.')
    if (!match[2] && method === 'GET') return json(200, detail(found))
    if (!match[2] && method === 'PUT') {
      if (siblingsHave(found.parentId, String(body.name), found.systemId))
        return inUse(String(body.name))
      found.name = String(body.name)
      found.description = (body.description as string | null) ?? null
      return json(200, detail(found))
    }
    if (!match[2] && method === 'DELETE') {
      nodes.splice(nodes.indexOf(found), 1)
      return json(204)
    }
    if (match[2] && !match[3] && method === 'POST') {
      const ids = body.attributeIds as string[]
      const added = ids.filter((id) => !found.attributeIds.includes(id))
      found.attributeIds.push(...added)
      const alreadyAssigned = ids.filter((id) => !added.includes(id))
      return json(200, { node: detail(found), added, alreadyAssigned })
    }
    if (match[3] && method === 'DELETE') {
      found.attributeIds = found.attributeIds.filter((id) => id !== match[3])
      return json(204)
    }
    return json(500, {})
  }

  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const path = new URL(url).pathname
    if (options.failList && path === NODES) return Promise.reject(new TypeError('Network down'))
    const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : {}
    return Promise.resolve(handle(init?.method ?? 'GET', path, body))
  })
  vi.stubGlobal('fetch', fetchMock)
  return { fetchMock, nodes }
}

function tree() {
  return within(screen.getByRole('region', { name: 'Jerarquía de productos' }))
}

async function openNode(name: string) {
  fireEvent.click(await tree().findByRole('button', { name: new RegExp(`^${name},`) }))
  return within(await screen.findByRole('region', { name }))
}

function attributeRows(nodeName: string) {
  const table = screen.getByRole('table', { name: `Atributos de «${nodeName}»` })
  return within(table)
    .getAllByRole('row')
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole('cell')
        .slice(0, 4)
        .map((cell) => cell.textContent),
    )
}

describe('TaxonomyPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  // --- User Story 1: hierarchy ---

  it('starts empty and creates a first-level node and a child', async () => {
    fakeBackend()
    render(<TaxonomyPage />)

    expect(screen.getByRole('heading', { level: 1, name: 'Taxonomía' })).toBeTruthy()
    expect(screen.getByText('Cargando la jerarquía…')).toBeTruthy()
    expect(await screen.findByText('Todavía no hay nodos en la jerarquía.')).toBeTruthy()
    expect(screen.getByText('Crea el primer nivel con «Nuevo nodo de primer nivel».')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Nuevo nodo de primer nivel' }))
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Ropa' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear nodo' }))

    expect(await screen.findByText('Nodo «Ropa» creado con el ID CLS000001.')).toBeTruthy()
    const ropa = within(await screen.findByRole('region', { name: 'Ropa' }))
    expect(ropa.getByText('Primer nivel')).toBeTruthy()
    expect(ropa.getByText('CLS000001 · Nivel 1')).toBeTruthy()

    fireEvent.click(ropa.getByRole('button', { name: 'Agregar nodo hijo' }))
    expect(screen.getByRole('heading', { name: 'Nuevo nodo hijo de «Ropa»' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Ropa de hombre' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear nodo' }))

    expect(
      await screen.findByText('Nodo «Ropa de hombre» creado con el ID CLS000002.'),
    ).toBeTruthy()
    const hombre = within(await screen.findByRole('region', { name: 'Ropa de hombre' }))
    expect(hombre.getByText('CLS000002 · Nivel 2')).toBeTruthy()
    expect(hombre.getByRole('button', { name: 'Ropa' })).toBeTruthy()
    expect(
      await tree().findByRole('button', { name: 'Ropa de hombre, 0 atributos directos' }),
    ).toBeTruthy()
  })

  it('asks for a name and shows a name already used by a sibling', async () => {
    fakeBackend(example())
    render(<TaxonomyPage />)
    await tree().findByRole('button', { name: /^Ropa,/ })

    fireEvent.click(screen.getByRole('button', { name: 'Nuevo nodo de primer nivel' }))
    fireEvent.click(screen.getByRole('button', { name: 'Crear nodo' }))
    expect(screen.getByText('Escribe el nombre del nodo.')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'ropa' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear nodo' }))
    expect(
      await screen.findByText('Ya existe un nodo «ropa» en este nivel. Usa otro nombre.'),
    ).toBeTruthy()
  })

  it('edits a node', async () => {
    fakeBackend(example())
    render(<TaxonomyPage />)
    const ropa = await openNode('Ropa')

    fireEvent.click(ropa.getByRole('button', { name: 'Editar' }))
    const name = screen.getByLabelText<HTMLInputElement>('Nombre')
    expect(name.value).toBe('Ropa')
    fireEvent.change(name, { target: { value: 'Ropa y accesorios' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText('Nodo «Ropa y accesorios» actualizado.')).toBeTruthy()
    expect(await tree().findByRole('button', { name: /^Ropa y accesorios,/ })).toBeTruthy()
  })

  it('confirms deletions and explains why a node cannot be deleted', async () => {
    const { fetchMock } = fakeBackend([...example(), node('CLS000005', 'Calzado', null)])
    render(<TaxonomyPage />)
    const deletes = () => fetchMock.mock.calls.filter(([, init]) => init?.method === 'DELETE')

    const ropa = await openNode('Ropa')
    fireEvent.click(ropa.getByRole('button', { name: 'Eliminar' }))
    expect(
      ropa.getByText('El nodo «Ropa» tiene 1 nodo hijo. Elimínalos antes de eliminar este nodo.'),
    ).toBeTruthy()
    expect(ropa.queryByRole('button', { name: 'Sí, eliminar nodo' })).toBeNull()
    fireEvent.click(ropa.getByRole('button', { name: 'Cancelar' }))

    fireEvent.click(await tree().findByRole('button', { name: 'Mostrar hijos de Ropa de hombre' }))
    const camisas = await openNode('Camisas')
    fireEvent.click(camisas.getByRole('button', { name: 'Eliminar' }))
    expect(
      camisas.getByText(
        'El nodo «Camisas» tiene 1 atributo asignado. Quítalos antes de eliminar el nodo.',
      ),
    ).toBeTruthy()
    expect(deletes()).toHaveLength(0)

    const calzado = await openNode('Calzado')
    fireEvent.click(calzado.getByRole('button', { name: 'Eliminar' }))
    expect(
      calzado.getByText('¿Eliminar el nodo «Calzado» (CLS000005)? Ningún atributo se elimina.'),
    ).toBeTruthy()
    fireEvent.click(calzado.getByRole('button', { name: 'Sí, eliminar nodo' }))

    expect(await screen.findByText('Nodo «Calzado» eliminado.')).toBeTruthy()
    await waitFor(() => expect(tree().queryByRole('button', { name: /^Calzado,/ })).toBeNull())
  })

  // --- User Story 3: effective attributes ---

  it('shows direct and inherited attributes with their origin', async () => {
    fakeBackend(example())
    render(<TaxonomyPage />)
    fireEvent.click(await tree().findByRole('button', { name: 'Mostrar hijos de Ropa de hombre' }))

    const camisas = await openNode('Camisas')

    expect(camisas.getByText('1 directo · 4 heredados')).toBeTruthy()
    expect(attributeRows('Camisas')).toEqual([
      ['ColorATR000001', 'Texto', 'Heredado', 'Ropa'],
      ['MaterialATR000002', 'Texto', 'Heredado', 'Ropa'],
      ['MarcaATR000003', 'Texto', 'Heredado', 'Ropa'],
      ['GéneroATR000004', 'Texto', 'Heredado', 'Ropa de hombre'],
      ['Largo de mangaATR000005', 'Texto', 'Directo', 'Este nodo'],
    ])

    fireEvent.click(camisas.getByRole('button', { name: 'Ir al nodo Ropa de hombre' }))
    const hombre = within(await screen.findByRole('region', { name: 'Ropa de hombre' }))
    expect(hombre.getByText('Camisas')).toBeTruthy()
    fireEvent.click(hombre.getByRole('button', { name: 'Ropa' }))
    expect(await screen.findByRole('region', { name: 'Ropa' })).toBeTruthy()
  })

  it('explains a new child that only inherits', async () => {
    fakeBackend([...example(), node('CLS000005', 'Camisas formales', 'CLS000003')])
    render(<TaxonomyPage />)
    fireEvent.click(await tree().findByRole('button', { name: 'Mostrar hijos de Ropa de hombre' }))
    fireEvent.click(await tree().findByRole('button', { name: 'Mostrar hijos de Camisas' }))

    const formales = await openNode('Camisas formales')

    expect(
      formales.getByText(
        'Este nodo no tiene atributos asignados directamente; hereda 5 de sus niveles superiores.',
      ),
    ).toBeTruthy()
    expect(
      formales.getByText(
        (_, element) =>
          element?.tagName === 'P' &&
          element.textContent === 'Ropa › Ropa de hombre › Camisas › Camisas formales',
      ),
    ).toBeTruthy()
  })

  // --- User Story 2: assignments ---

  it('assigns attributes from a searchable picker', async () => {
    fakeBackend(example())
    render(<TaxonomyPage />)
    const hombre = await openNode('Ropa de hombre')

    fireEvent.click(hombre.getByRole('button', { name: 'Agregar atributos' }))
    const picker = within(
      await screen.findByRole('group', { name: 'Agregar atributos a «Ropa de hombre»' }),
    )
    expect(picker.queryByLabelText(/^Género/)).toBeNull()
    expect(picker.getByLabelText('Color (ATR000001) — heredado de «Ropa»')).toBeTruthy()

    fireEvent.change(picker.getByLabelText('Buscar atributo'), { target: { value: 'TEMPO' } })
    fireEvent.click(picker.getByLabelText('Temporada (ATR000007)'))
    fireEvent.change(picker.getByLabelText('Buscar atributo'), { target: { value: 'atr000001' } })
    fireEvent.click(picker.getByLabelText(/^Color/))
    fireEvent.change(picker.getByLabelText('Buscar atributo'), { target: { value: 'nada' } })
    expect(picker.getByText('Ningún atributo coincide con la búsqueda.')).toBeTruthy()
    fireEvent.click(picker.getByRole('button', { name: 'Asignar seleccionados (2)' }))

    expect(await screen.findByText('2 atributos asignados a «Ropa de hombre».')).toBeTruthy()
    expect(
      await tree().findByRole('button', { name: 'Ropa de hombre, 3 atributos directos' }),
    ).toBeTruthy()
    expect(screen.getByText('También se hereda de «Ropa»')).toBeTruthy()
  })

  it('removes a direct attribute after confirming', async () => {
    fakeBackend(example())
    render(<TaxonomyPage />)
    const ropa = await openNode('Ropa')

    fireEvent.click(ropa.getByRole('button', { name: 'Quitar Color de este nodo' }))
    expect(
      ropa.getByText(
        '¿Quitar «Color» de «Ropa»? Los niveles inferiores dejarán de heredarlo de este nodo. El atributo no se elimina.',
      ),
    ).toBeTruthy()
    fireEvent.click(ropa.getByRole('button', { name: 'Sí, quitar' }))

    expect(await screen.findByText('Atributo «Color» quitado de «Ropa».')).toBeTruthy()
    await waitFor(() => expect(attributeRows('Ropa')).toHaveLength(2))
    fireEvent.click(tree().getByRole('button', { name: 'Mostrar hijos de Ropa de hombre' }))
    await openNode('Camisas')
    expect(attributeRows('Camisas').map((row) => row[0])).not.toContain('ColorATR000001')
  })

  // --- Feature 011: moving nodes ---

  /** Feature 011's example: feature 010's plus "Camisas formales", "Ropa de mujer" and "Calzado". */
  function moveExample(): FakeNode[] {
    return [
      ...example(),
      node('CLS000005', 'Camisas formales', 'CLS000003'),
      node('CLS000006', 'Ropa de mujer', 'CLS000001', 'ATR000004'),
      node('CLS000007', 'Calzado', null, 'ATR000007'),
    ]
  }

  async function openCamisas() {
    fireEvent.click(await tree().findByRole('button', { name: 'Mostrar hijos de Ropa de hombre' }))
    return openNode('Camisas')
  }

  function chooseDestination(card: ReturnType<typeof within>, name: RegExp | string) {
    fireEvent.click(card.getByRole('button', { name: 'Mover' }))
    const panel = within(card.getByRole('group', { name: /^Mover «/ }))
    fireEvent.click(panel.getByRole('button', { name }))
    fireEvent.click(panel.getByRole('button', { name: 'Continuar' }))
  }

  it('moves a node with its branch and shows where it now inherits from', async () => {
    const { nodes } = fakeBackend(moveExample())
    render(<TaxonomyPage />)
    const camisas = await openCamisas()

    const actions = camisas
      .getAllByRole('button')
      .map((button) => button.textContent)
      .filter((text) => ['Agregar nodo hijo', 'Editar', 'Mover', 'Eliminar'].includes(text ?? ''))
    expect(actions).toEqual(['Agregar nodo hijo', 'Editar', 'Mover', 'Eliminar'])

    chooseDestination(camisas, /^Calzado/)
    expect(camisas.getByText('¿Mover «Camisas» a «Calzado»?')).toBeTruthy()
    expect(camisas.getByText('Se moverán 2 nodos: «Camisas» y 1 descendiente.')).toBeTruthy()
    fireEvent.click(camisas.getByRole('button', { name: 'Sí, mover nodo' }))

    expect(
      await screen.findByText('Nodo «Camisas» movido a «Calzado». Se movieron 2 nodos.'),
    ).toBeTruthy()
    expect(nodes.find((entry) => entry.name === 'Camisas')?.parentId).toBe('CLS000007')
    const moved = within(await screen.findByRole('region', { name: 'Camisas' }))
    expect(moved.getByText('CLS000003 · Nivel 2')).toBeTruthy()
    expect(moved.getByRole('button', { name: 'Calzado' })).toBeTruthy()
    expect(moved.queryByRole('group', { name: 'Mover «Camisas»' })).toBeNull()
    expect(attributeRows('Camisas')).toEqual([
      ['TemporadaATR000007', 'Texto', 'Heredado', 'Calzado'],
      ['Largo de mangaATR000005', 'Texto', 'Directo', 'Este nodo'],
    ])
    // The tree reloads with the new parent expanded, showing the moved node under it.
    expect(await tree().findByRole('button', { name: 'Ocultar hijos de Calzado' })).toBeTruthy()
    expect(
      tree()
        .getByRole('button', { name: /^Camisas,/ })
        .getAttribute('aria-pressed'),
    ).toBe('true')
  })

  it('keeps the panel open with the backend message when a move is refused', async () => {
    fakeBackend([...moveExample(), node('CLS000008', 'Camisas', 'CLS000006')])
    render(<TaxonomyPage />)
    const camisas = await openCamisas()

    chooseDestination(camisas, /^Ropa de mujer/)
    fireEvent.click(camisas.getByRole('button', { name: 'Sí, mover nodo' }))

    expect(
      await screen.findByText(
        'Ya existe un nodo «Camisas» en «Ropa de mujer». Cambia el nombre de uno de los dos antes de mover.',
      ),
    ).toBeTruthy()
    expect(camisas.getByText('¿Mover «Camisas» a «Ropa de mujer»?')).toBeTruthy()
    fireEvent.click(camisas.getByRole('button', { name: 'Cancelar' }))
    fireEvent.click(
      within(camisas.getByRole('group', { name: 'Mover «Camisas»' })).getByRole('button', {
        name: 'Cancelar',
      }),
    )
    expect(camisas.getByRole('button', { name: 'Mover' })).toBeTruthy()
  })

  it('does not offer the node, its branch or its parent as destination', async () => {
    fakeBackend(moveExample())
    render(<TaxonomyPage />)
    const hombre = await openNode('Ropa de hombre')

    fireEvent.click(hombre.getByRole('button', { name: 'Mover' }))
    const panel = within(hombre.getByRole('group', { name: 'Mover «Ropa de hombre»' }))
    const options = within(panel.getByRole('list'))
      .getAllByRole('button')
      .map((option) => option.textContent)

    expect(options).toEqual([
      'Primer nivel (sin nodo padre)',
      'CalzadoCalzado · CLS000007',
      'Ropa de mujerRopa › Ropa de mujer · CLS000006',
    ])
  })

  it('moves a node to the top level', async () => {
    fakeBackend(moveExample())
    render(<TaxonomyPage />)
    const mujer = await openNode('Ropa de mujer')

    chooseDestination(mujer, 'Primer nivel (sin nodo padre)')
    expect(mujer.getByText('¿Mover «Ropa de mujer» al primer nivel?')).toBeTruthy()
    fireEvent.click(mujer.getByRole('button', { name: 'Sí, mover nodo' }))

    expect(await screen.findByText('Nodo «Ropa de mujer» movido al primer nivel.')).toBeTruthy()
    expect(
      within(await screen.findByRole('region', { name: 'Ropa de mujer' })).getByText(
        'Primer nivel',
      ),
    ).toBeTruthy()
  })

  it('closes the panel and reloads when the node no longer exists', async () => {
    const { nodes } = fakeBackend(moveExample())
    render(<TaxonomyPage />)
    const camisas = await openCamisas()

    chooseDestination(camisas, /^Calzado/)
    nodes.splice(
      nodes.findIndex((entry) => entry.name === 'Camisas'),
      1,
    )
    fireEvent.click(camisas.getByRole('button', { name: 'Sí, mover nodo' }))

    expect(await screen.findByText('No existe un nodo con el ID CLS000003.')).toBeTruthy()
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Camisas' })).toBeNull())
  })

  // --- States and permissions ---

  it('shows a load error with a retry', async () => {
    fakeBackend([], { failList: true })
    render(<TaxonomyPage />)

    expect(
      await screen.findByText('No se pudo conectar con el servidor. Inténtalo de nuevo.'),
    ).toBeTruthy()
    fakeBackend(example())
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await tree().findByRole('button', { name: /^Ropa,/ })).toBeTruthy()
  })

  it('shows the hierarchy and attributes without change controls without the permission', async () => {
    vi.stubEnv('VITE_DEV_PERMISSIONS', '')
    fakeBackend(example())
    render(<TaxonomyPage />)

    const ropa = await openNode('Ropa')

    expect(screen.queryByRole('button', { name: 'Nuevo nodo de primer nivel' })).toBeNull()
    expect(ropa.queryByRole('button', { name: 'Editar' })).toBeNull()
    expect(ropa.queryByRole('button', { name: 'Eliminar' })).toBeNull()
    expect(ropa.queryByRole('button', { name: 'Mover' })).toBeNull()
    expect(ropa.queryByRole('button', { name: 'Agregar atributos' })).toBeNull()
    expect(ropa.queryByRole('button', { name: /^Quitar/ })).toBeNull()
    expect(attributeRows('Ropa')).toHaveLength(3)
  })
})
