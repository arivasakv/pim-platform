import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AttributeCreationPage } from './AttributeCreationPage'

const BASE_URL = 'http://servidor.ejemplo:9090'
const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

const TYPES = {
  total: 7,
  items: [
    {
      code: 'TEXT',
      label: 'Texto',
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
    {
      code: 'DATE_TIME',
      label: 'Fecha y hora',
      options: { dateFormat: [{ code: 'DD/MM/AAAA', label: 'DD/MM/AAAA' }] },
    },
    { code: 'CURRENCY', label: 'Moneda', options: {} },
    {
      code: 'REFERENCE',
      label: 'Referencia',
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
  render(<AttributeCreationPage />)
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

describe('AttributeCreationPage', () => {
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
    render(<AttributeCreationPage />)

    expect(screen.getByRole('heading', { level: 1, name: 'Crear Atributos' })).toBeTruthy()
    expect(screen.getByText('Cargando tipos de dato…')).toBeTruthy()
    const typeSelect = await screen.findByLabelText<HTMLSelectElement>('Tipo de dato')
    expect(Array.from(typeSelect.options).map((option) => option.textContent)).toEqual([
      'Elige un tipo de dato',
      'Texto',
      'Numérico',
      'Booleano',
      'Lista de valores',
      'Fecha y hora',
      'Moneda',
      'Referencia',
    ])
    expect(screen.getByLabelText('Nombre')).toBeTruthy()
    expect(screen.getByLabelText('Descripción')).toBeTruthy()
    expect(screen.getByLabelText('ID (opcional)')).toBeTruthy()
  })

  it('asks for the ID before every other field', async () => {
    stubBackend()
    render(<AttributeCreationPage />)

    await screen.findByLabelText('Tipo de dato')
    const form = screen.getByLabelText('ID (opcional)').closest('form')
    const fieldIds = Array.from(form?.querySelectorAll('input, textarea, select') ?? []).map(
      (field) => field.id,
    )
    expect(fieldIds.slice(0, 4)).toEqual(['systemId', 'displayName', 'description', 'dataType'])
  })

  it('offers a retry when the data types cannot be loaded', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementationOnce(() => Promise.reject(new TypeError('Failed to fetch')))
      .mockImplementationOnce(() => Promise.resolve(response(200, TYPES)))
    vi.stubGlobal('fetch', fetchMock)
    render(<AttributeCreationPage />)

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
      description: null,
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
    type('Tipo de dato', 'NUMERIC')
    type('Formato', 'INTEGER')
    expect(screen.queryByLabelText('Decimales')).toBeNull()
    type('Formato', 'DECIMAL')
    expect(screen.getByLabelText('Decimales')).toBeTruthy()
    type('Valor mínimo', '5')
    type('Valor máximo', '1')
    save()

    expect(
      await screen.findByText('El valor mínimo no puede ser mayor que el valor máximo.'),
    ).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows the date and currency fields', async () => {
    stubBackend()
    await renderReadyPage()

    type('Tipo de dato', 'DATE_TIME')
    expect(screen.getByLabelText('Formato de fecha')).toBeTruthy()
    type('Tipo de dato', 'CURRENCY')
    expect(screen.getByLabelText('Moneda (código ISO, p. ej. MXN)')).toBeTruthy()
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
