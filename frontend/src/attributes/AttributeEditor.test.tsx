import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AttributeEditor } from './AttributeEditor'

const BASE_URL = 'http://servidor.ejemplo:9090'
const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

const TYPES = {
  total: 7,
  items: [
    {
      code: 'TEXT',
      label: 'Texto (String)',
      options: {
        caseFormat: [
          { code: 'UPPERCASE', label: 'Mayúsculas' },
          { code: 'LOWERCASE', label: 'Minúsculas' },
          { code: 'MIXED_CASE', label: 'Capitalización mixta' },
        ],
      },
    },
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
    { code: 'BOOLEAN', label: 'Booleano (Sí/No)', options: {} },
    {
      code: 'LIST_OF_VALUES',
      label: 'Lista de Valores (LOV)',
      options: {
        selectionMode: [
          { code: 'SINGLE', label: 'Selección única' },
          { code: 'MULTIPLE', label: 'Selección múltiple' },
        ],
      },
    },
    {
      code: 'DATE_TIME',
      label: 'Fecha y Hora (Date/Time)',
      options: { dateFormat: [{ code: 'DD/MM/AAAA', label: 'DD/MM/AAAA' }] },
    },
    {
      code: 'CURRENCY',
      label: 'Moneda (Currency)',
      options: {
        currencyCode: [
          { code: 'MXN', label: 'Peso mexicano', symbol: '$' },
          { code: 'USD', label: 'Dólar estadounidense', symbol: '$' },
          { code: 'EUR', label: 'Euro', symbol: '€' },
          { code: 'CHF', label: 'Franco suizo', symbol: 'CHF' },
        ],
      },
    },
    {
      code: 'REFERENCE',
      label: 'Referencias / Relaciones',
      options: {
        targetObjectType: [{ code: 'PRODUCT', label: 'Producto' }],
        cardinality: [
          { code: 'SINGLE', label: 'Una' },
          { code: 'MULTIPLE', label: 'Varias' },
        ],
      },
    },
  ],
}

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

/** The first call answers the data types; the next ones answer the given creations, in order. */
function stubBackend(...creations: Array<() => Promise<FakeResponse>>) {
  return stubBackendWithLists([], ...creations)
}

/**
 * Like `stubBackend`, and `GET /api/v1/lists-of-values` (feature 006) answers these lists
 * without using up a creation answer.
 */
function stubBackendWithLists(
  lists: Array<{ systemId: string; name: string }>,
  ...creations: Array<() => Promise<FakeResponse>>
) {
  const answers = [() => Promise.resolve(response(200, TYPES)), ...creations]
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url.endsWith('/api/v1/lists-of-values') && !init?.method) {
      const items = lists.map((list) => ({ ...list, valueCount: 0, attributeCount: 0 }))
      return Promise.resolve(response(200, { items, total: items.length }))
    }
    // The field preview (feature 008) reads the chosen list; answered without using up an answer.
    const listDetail = /\/api\/v1\/lists-of-values\/([^/]+)$/.exec(url)
    if (listDetail && !init?.method) {
      const list = lists.find((candidate) => candidate.systemId === listDetail[1])
      return Promise.resolve(
        response(200, {
          ...list,
          valueCount: 0,
          values: [],
          attributes: [],
          equivalencesEnabled: false,
        }),
      )
    }
    const answer = answers.shift()
    return answer ? answer() : Promise.reject(new TypeError('No more answers'))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function problem(status: number, detail: string, field: string, code: string, message: string) {
  return () =>
    Promise.resolve(response(status, { status, detail, errors: [{ field, code, message }] }))
}

async function renderReadyPage() {
  render(<AttributeEditor />)
  await screen.findByLabelText('Tipo de dato')
}

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function save() {
  fireEvent.click(screen.getByRole('button', { name: 'Crear atributo' }))
}

function lastRequestBody(fetchMock: ReturnType<typeof vi.fn>) {
  const calls = fetchMock.mock.calls
  return JSON.parse(calls[calls.length - 1][1].body)
}

describe('AttributeEditor (create mode, feature 004)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  // --- User Story 1 ---

  it('loads the data types from the backend and shows the common fields', async () => {
    stubBackend()
    render(<AttributeEditor />)

    expect(screen.getByRole('heading', { level: 2, name: 'Nuevo atributo' })).toBeTruthy()
    expect(screen.getByText('Cargando tipos de dato…')).toBeTruthy()
    const typeSelect = await screen.findByLabelText<HTMLSelectElement>('Tipo de dato')
    expect(Array.from(typeSelect.options).map((option) => option.textContent)).toEqual([
      'Elige un tipo de dato',
      'Texto (String)',
      'Numérico Entero (Integer)',
      'Numérico Decimal (Float)',
      'Booleano (Sí/No)',
      'Lista de Valores (LOV)',
      'Fecha y Hora (Date/Time)',
      'Moneda (Currency)',
      'Referencias / Relaciones',
    ])
    expect(screen.getByLabelText('Nombre')).toBeTruthy()
    expect(screen.queryByLabelText('Descripción')).toBeNull()
    expect(screen.getByLabelText('ID (opcional)')).toBeTruthy()
  })

  it('asks for the ID before every other field', async () => {
    stubBackend()
    render(<AttributeEditor />)

    await screen.findByLabelText('Tipo de dato')
    const form = screen.getByLabelText('ID (opcional)').closest('form')
    const fieldIds = Array.from(form?.querySelectorAll('input, textarea, select') ?? []).map(
      (field) => field.id,
    )
    expect(fieldIds.slice(0, 3)).toEqual(['systemId', 'displayName', 'dataType'])
  })

  it('offers a retry when the data types cannot be loaded', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() => Promise.reject(new TypeError('Failed to fetch')))
      .mockImplementationOnce(() => Promise.resolve(response(200, TYPES)))
    vi.stubGlobal('fetch', fetchMock)
    render(<AttributeEditor />)

    expect(await screen.findByText(CONNECTION_ERROR)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByLabelText('Tipo de dato')).toBeTruthy()
  })

  it('creates a Boolean attribute, shows its ID and empties the form', async () => {
    const fetchMock = stubBackend(() =>
      Promise.resolve(
        response(201, {
          systemId: 'ATR000001',
          displayName: 'Apto para regalo',
          dataType: 'BOOLEAN',
          scope: 'GLOBAL',
        }),
      ),
    )
    await renderReadyPage()

    type('Nombre', 'Apto para regalo')
    type('Tipo de dato', 'BOOLEAN')
    save()

    expect(await screen.findByText('Atributo global creado con el ID ATR000001.')).toBeTruthy()
    expect(lastRequestBody(fetchMock)).toEqual({
      displayName: 'Apto para regalo',
      dataType: 'BOOLEAN',
    })
    expect(screen.getByLabelText<HTMLInputElement>('Nombre').value).toBe('')
    expect(screen.getByLabelText<HTMLSelectElement>('Tipo de dato').value).toBe('')
  })

  it('shows a duplicate-name message next to the name and keeps the data', async () => {
    stubBackend(
      problem(
        409,
        'Ya existe un atributo con el nombre "Color".',
        'displayName',
        'DISPLAY_NAME_IN_USE',
        'Ya existe un atributo con el nombre "Color". Usa otro nombre.',
      ),
    )
    await renderReadyPage()

    type('Nombre', 'Color')
    type('Tipo de dato', 'BOOLEAN')
    save()

    expect(
      await screen.findByText('Ya existe un atributo con el nombre "Color". Usa otro nombre.'),
    ).toBeTruthy()
    expect(screen.getByLabelText('Nombre').getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByLabelText<HTMLInputElement>('Nombre').value).toBe('Color')
  })

  it('shows an ID used by another object under the ID field and keeps the data (feature 003 FR-011)', async () => {
    const message =
      'El ID ATR000001 ya se encuentra en uso por otro objeto. Favor de asignar un identificador inédito.'
    stubBackend(problem(409, message, 'systemId', 'SYSTEM_ID_IN_USE', message))
    await renderReadyPage()

    type('ID (opcional)', 'ATR000001')
    type('Nombre', 'Talla')
    type('Tipo de dato', 'BOOLEAN')
    save()

    expect(await screen.findByText(message)).toBeTruthy()
    const id = screen.getByLabelText<HTMLInputElement>('ID (opcional)')
    expect(id.getAttribute('aria-invalid')).toBe('true')
    expect(id.getAttribute('aria-describedby')).toContain('systemId-error')
    expect(document.getElementById('systemId-error')?.textContent).toBe(message)
    expect(id.value).toBe('ATR000001')
    expect(screen.getByLabelText<HTMLInputElement>('Nombre').value).toBe('Talla')
    expect(screen.queryByText(/Atributo global creado/)).toBeNull()
  })

  it('shows the permission message from the backend', async () => {
    stubBackend(
      problem(
        403,
        'No tienes permiso para crear atributos.',
        'permissions',
        'ATTRIBUTE_CREATE_NOT_PERMITTED',
        'No tienes permiso para crear atributos.',
      ),
    )
    await renderReadyPage()

    type('Nombre', 'Color')
    type('Tipo de dato', 'BOOLEAN')
    save()

    expect(await screen.findByText('No tienes permiso para crear atributos.')).toBeTruthy()
  })

  it('shows the connection message and keeps the data when the backend cannot be reached', async () => {
    stubBackend(() => Promise.reject(new TypeError('Failed to fetch')))
    await renderReadyPage()

    type('Nombre', 'Color')
    type('Tipo de dato', 'BOOLEAN')
    save()

    expect(await screen.findByText(CONNECTION_ERROR)).toBeTruthy()
    expect(screen.getByLabelText<HTMLInputElement>('Nombre').value).toBe('Color')
  })

  it('does not call the backend when the name is empty', async () => {
    const fetchMock = stubBackend()
    await renderReadyPage()

    type('Tipo de dato', 'BOOLEAN')
    save()

    expect(await screen.findByText('Escribe un nombre.')).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  // --- User Story 2 ---

  it('shows the text fields and sends them', async () => {
    const fetchMock = stubBackend(() => Promise.resolve(response(201, { systemId: 'ATR000001' })))
    await renderReadyPage()

    type('Nombre', 'Código')
    type('Tipo de dato', 'TEXT')
    type('Longitud mínima', '2')
    type('Longitud máxima', '50')
    type('Formato de mayúsculas', 'UPPERCASE')
    type('Máscara de captura (expresión regular)', '^[A-Z]+$')
    fireEvent.click(screen.getByLabelText('Texto enriquecido (HTML)'))
    save()

    await screen.findByText('Atributo global creado con el ID ATR000001.')
    expect(lastRequestBody(fetchMock).textProperties).toEqual({
      minLength: 2,
      maxLength: 50,
      caseFormat: 'UPPERCASE',
      captureMask: '^[A-Z]+$',
      richText: true,
    })
  })

  it('shows a backend error for a property next to that property', async () => {
    stubBackend(
      problem(
        400,
        'La solicitud tiene datos no válidos.',
        'textProperties.maxLength',
        'INVALID_LENGTH',
        'La longitud debe ser un número entero entre 1 y 100000.',
      ),
    )
    await renderReadyPage()

    type('Nombre', 'Código')
    type('Tipo de dato', 'TEXT')
    type('Longitud máxima', '0')
    save()

    expect(
      await screen.findByText('La longitud debe ser un número entero entre 1 y 100000.'),
    ).toBeTruthy()
    expect(screen.getByLabelText('Longitud máxima').getAttribute('aria-invalid')).toBe('true')
  })

  it('shows decimals only for decimal numbers and checks the range before saving', async () => {
    const fetchMock = stubBackend()
    await renderReadyPage()

    type('Nombre', 'Peso')
    type('Tipo de dato', 'NUMERIC_INTEGER')
    expect(screen.queryByLabelText('Formato')).toBeNull()
    expect(screen.queryByLabelText('Decimales')).toBeNull()
    type('Tipo de dato', 'NUMERIC_DECIMAL')
    expect(screen.getByLabelText('Decimales')).toBeTruthy()
    type('Valor mínimo', '5')
    type('Valor máximo', '1')
    save()

    expect(
      await screen.findByText('El valor mínimo no puede ser mayor que el valor máximo.'),
    ).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('creates an integer number from the "Numérico Entero (Integer)" choice', async () => {
    const fetchMock = stubBackend(() =>
      Promise.resolve(response(201, { systemId: 'ATR000001', displayName: 'Piezas' })),
    )
    await renderReadyPage()

    type('Nombre', 'Piezas')
    type('Tipo de dato', 'NUMERIC_INTEGER')
    save()

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(lastRequestBody(fetchMock)).toEqual({
      displayName: 'Piezas',
      dataType: 'NUMERIC',
      numericProperties: { numberFormat: 'INTEGER' },
    })
  })

  it('creates a currency attribute from the selector and previews its symbol', async () => {
    const fetchMock = stubBackend(() =>
      Promise.resolve(response(201, { systemId: 'ATR000001', displayName: 'Precio' })),
    )
    await renderReadyPage()

    type('Nombre', 'Precio')
    type('Tipo de dato', 'CURRENCY')
    type('Moneda', 'EUR')
    const panel = screen.getByRole('region', { name: 'Vista previa del campo' })
    expect(within(panel).getByText('Moneda: EUR (€) · 2 decimales')).toBeTruthy()
    save()

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(lastRequestBody(fetchMock)).toEqual({
      displayName: 'Precio',
      dataType: 'CURRENCY',
      currencyProperties: { currencyCode: 'EUR' },
    })
  })

  it('keeps a stored currency that is not in the list when editing', async () => {
    stubBackend()
    const oro = {
      systemId: 'ATR000003',
      displayName: 'Valor en oro',
      dataType: 'CURRENCY' as const,
      scope: 'GLOBAL' as const,
      currencyProperties: { currencyCode: 'XAU', decimals: 2, currencySymbol: 'XAU' },
      groups: [],
    }
    render(<AttributeEditor mode="edit" attribute={oro} />)

    const currency = await screen.findByLabelText<HTMLSelectElement>('Moneda')
    expect(currency.value).toBe('XAU')
    expect(currency.options[1].textContent).toBe('XAU')
  })

  it('shows the date and currency fields', async () => {
    stubBackend()
    await renderReadyPage()

    type('Tipo de dato', 'DATE_TIME')
    expect(screen.getByLabelText('Formato de fecha')).toBeTruthy()
    type('Tipo de dato', 'CURRENCY')
    const currency = screen.getByLabelText<HTMLSelectElement>('Moneda')
    expect(Array.from(currency.options).map((option) => option.textContent)).toEqual([
      'Elige una moneda',
      '$ · MXN · Peso mexicano',
      '$ · USD · Dólar estadounidense',
      '€ · EUR · Euro',
      'CHF · Franco suizo',
    ])
    expect(screen.getByLabelText('Decimales')).toBeTruthy()
    type('Tipo de dato', 'BOOLEAN')
    expect(screen.queryByLabelText('Decimales')).toBeNull()
  })

  // --- User Story 3 ---

  it('explains that no lists of values exist yet and shows the backend refusal', async () => {
    const fetchMock = stubBackend(
      problem(
        422,
        'Aún no existen listas de valores.',
        'listOfValuesProperties.listOfValuesId',
        'LIST_OF_VALUES_NOT_AVAILABLE',
        'Aún no existen listas de valores. Un atributo de tipo lista de valores necesita una lista existente.',
      ),
    )
    await renderReadyPage()

    type('Nombre', 'Color')
    type('Tipo de dato', 'LIST_OF_VALUES')
    expect(
      screen.getByText(
        'Aún no existen listas de valores. Podrás crear este atributo cuando exista una lista.',
      ),
    ).toBeTruthy()
    type('Tipo de selección', 'MULTIPLE')
    save()

    expect(
      await screen.findByText(
        'Aún no existen listas de valores. Un atributo de tipo lista de valores necesita una lista existente.',
      ),
    ).toBeTruthy()
    expect(lastRequestBody(fetchMock).listOfValuesProperties).toEqual({ selectionMode: 'MULTIPLE' })
  })

  // --- Feature 006: lists of values to link (FR-026) ---

  it('offers the existing lists and sends the chosen one', async () => {
    const fetchMock = stubBackendWithLists(
      [
        { systemId: 'LOV000001', name: 'Color' },
        { systemId: 'LOV000002', name: 'Talla' },
      ],
      () => Promise.resolve(response(201, { systemId: 'ATR000001' })),
    )
    await renderReadyPage()

    type('Nombre', 'Tono')
    type('Tipo de dato', 'LIST_OF_VALUES')
    const listSelect = await screen.findByLabelText('Lista de valores')
    expect(screen.getByRole('option', { name: 'Color (LOV000001)' })).toBeTruthy()
    expect(screen.getByRole('option', { name: 'Talla (LOV000002)' })).toBeTruthy()
    expect(screen.queryByText(/Aún no existen listas de valores/)).toBeNull()
    fireEvent.change(listSelect, { target: { value: 'LOV000002' } })
    type('Tipo de selección', 'SINGLE')
    save()

    expect(await screen.findByText('Atributo global creado con el ID ATR000001.')).toBeTruthy()
    expect(lastRequestBody(fetchMock).listOfValuesProperties).toEqual({
      selectionMode: 'SINGLE',
      listOfValuesId: 'LOV000002',
    })
  })

  it('shows no list select while no lists exist', async () => {
    stubBackendWithLists([])
    await renderReadyPage()

    type('Tipo de dato', 'LIST_OF_VALUES')

    expect(
      await screen.findByText(
        'Aún no existen listas de valores. Podrás crear este atributo cuando exista una lista.',
      ),
    ).toBeTruthy()
    await waitFor(() => expect(screen.queryByLabelText('Lista de valores')).toBeNull())
  })

  // --- User Story 4 ---

  it('shows and sends the reference fields', async () => {
    const fetchMock = stubBackend(() => Promise.resolve(response(201, { systemId: 'ATR000001' })))
    await renderReadyPage()

    type('Nombre', 'Repuesto')
    type('Tipo de dato', 'REFERENCE')
    type('Objeto referenciado', 'PRODUCT')
    type('Cantidad', 'MULTIPLE')
    save()

    await screen.findByText('Atributo global creado con el ID ATR000001.')
    expect(lastRequestBody(fetchMock).referenceProperties).toEqual({
      targetObjectType: 'PRODUCT',
      cardinality: 'MULTIPLE',
    })
  })

  // --- User Story 5 ---

  it('has no scope or group field', async () => {
    stubBackend()
    await renderReadyPage()

    expect(screen.queryByLabelText(/alcance/i)).toBeNull()
    expect(screen.queryByLabelText(/grupo/i)).toBeNull()
    await waitFor(() => expect(screen.getByText(/global/i)).toBeTruthy())
  })
})

describe('AttributeEditor (edit mode, feature 007)', () => {
  const PESO = {
    systemId: 'ATR000001',
    displayName: 'Peso',
    dataType: 'NUMERIC' as const,
    scope: 'GLOBAL' as const,
    numericProperties: { numberFormat: 'DECIMAL', precision: 2, minimum: 0 },
    groups: [],
  }

  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('fills the form, shows the ID read-only and only lets a number switch integer/decimal', async () => {
    stubBackend()
    render(<AttributeEditor mode="edit" attribute={PESO} />)
    await screen.findByLabelText('Tipo de dato')

    expect(screen.getByRole('heading', { level: 2, name: 'Editar atributo' })).toBeTruthy()
    const id = screen.getByLabelText<HTMLInputElement>('ID')
    expect(id.value).toBe('ATR000001')
    expect(id.readOnly).toBe(true)
    const typeSelect = screen.getByLabelText<HTMLSelectElement>('Tipo de dato')
    expect(typeSelect.disabled).toBe(false)
    expect(typeSelect.value).toBe('NUMERIC_DECIMAL')
    expect(Array.from(typeSelect.options).map((option) => option.textContent)).toEqual([
      'Elige un tipo de dato',
      'Numérico Entero (Integer)',
      'Numérico Decimal (Float)',
    ])
    expect(screen.getByLabelText<HTMLInputElement>('Nombre').value).toBe('Peso')
    expect(screen.queryByLabelText('Descripción')).toBeNull()
    expect(screen.getByLabelText<HTMLInputElement>('Decimales').value).toBe('2')
  })

  it('saves with PUT and reports the saved attribute', async () => {
    const saved = { ...PESO, displayName: 'Peso neto', updatedAt: '2026-11-11T18:00:00Z' }
    const fetchMock = stubBackend(() => Promise.resolve(response(200, saved)))
    const onSaved = vi.fn()
    render(<AttributeEditor mode="edit" attribute={PESO} onSaved={onSaved} />)
    await screen.findByLabelText('Tipo de dato')

    type('Nombre', 'Peso neto')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved))
    const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1]
    expect(url).toBe(`${BASE_URL}/api/v1/attributes/ATR000001`)
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(String(init?.body))).toMatchObject({
      systemId: 'ATR000001',
      displayName: 'Peso neto',
      dataType: 'NUMERIC',
      numericProperties: { numberFormat: 'DECIMAL', precision: 2, minimum: 0 },
    })
  })

  it('switches a number from decimal to integer and sends no decimals', async () => {
    const fetchMock = stubBackend(() => Promise.resolve(response(200, PESO)))
    render(<AttributeEditor mode="edit" attribute={PESO} onSaved={vi.fn()} />)
    await screen.findByLabelText('Tipo de dato')

    type('Tipo de dato', 'NUMERIC_INTEGER')
    expect(screen.queryByLabelText('Decimales')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(lastRequestBody(fetchMock)).toMatchObject({
      dataType: 'NUMERIC',
      numericProperties: { numberFormat: 'INTEGER', minimum: 0 },
    })
    expect(lastRequestBody(fetchMock)).not.toHaveProperty('numericProperties.precision')
  })

  it('keeps every other data type locked while editing', async () => {
    stubBackend()
    const activo = {
      systemId: 'ATR000002',
      displayName: 'Activo',
      dataType: 'BOOLEAN' as const,
      scope: 'GLOBAL' as const,
      groups: [],
    }
    render(<AttributeEditor mode="edit" attribute={activo} />)

    const typeSelect = await screen.findByLabelText<HTMLSelectElement>('Tipo de dato')
    expect(typeSelect.disabled).toBe(true)
    expect(typeSelect.value).toBe('BOOLEAN')
  })

  it('shows a duplicate name next to the name field', async () => {
    const message = 'Ya existe un atributo con el nombre "Color". Usa otro nombre.'
    stubBackend(problem(409, message, 'displayName', 'DISPLAY_NAME_IN_USE', message))
    const onSaved = vi.fn()
    render(<AttributeEditor mode="edit" attribute={PESO} onSaved={onSaved} />)
    await screen.findByLabelText('Tipo de dato')

    type('Nombre', 'Color')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText(message)).toBeTruthy()
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('cancels without sending anything', async () => {
    const fetchMock = stubBackend()
    const onCancel = vi.fn()
    render(<AttributeEditor mode="edit" attribute={PESO} onCancel={onCancel} />)
    await screen.findByLabelText('Tipo de dato')

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(onCancel).toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('AttributeEditor with the field preview (feature 008)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('shows the preview above the buttons and follows the configuration', async () => {
    stubBackend()
    render(<AttributeEditor />)
    await screen.findByLabelText('Tipo de dato')

    const panel = screen.getByRole('region', { name: 'Vista previa del campo' })
    const button = screen.getByRole('button', { name: 'Crear atributo' })
    expect(panel.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(within(panel).getByText('Elige un tipo de dato para ver el campo.')).toBeTruthy()

    type('Nombre', 'Peso')
    type('Tipo de dato', 'NUMERIC_INTEGER')
    const field = await within(panel).findByLabelText<HTMLInputElement>('Peso (opcional)')
    expect(field.type).toBe('number')
  })

  it('previews an attribute being edited and never sends preview values', async () => {
    const attribute = {
      systemId: 'ATR000001',
      displayName: 'Activo',
      dataType: 'BOOLEAN' as const,
      scope: 'GLOBAL' as const,
      groups: [],
    }
    const fetchMock = stubBackend(() => Promise.resolve(response(200, attribute)))
    render(<AttributeEditor mode="edit" attribute={attribute} onSaved={vi.fn()} />)
    await screen.findByLabelText('Tipo de dato')

    const panel = screen.getByRole('region', { name: 'Vista previa del campo' })
    fireEvent.click(within(panel).getByRole('checkbox', { name: 'Activo (opcional)' }))
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(lastRequestBody(fetchMock)).toEqual({
      systemId: 'ATR000001',
      displayName: 'Activo',
      dataType: 'BOOLEAN',
    })
  })
})
