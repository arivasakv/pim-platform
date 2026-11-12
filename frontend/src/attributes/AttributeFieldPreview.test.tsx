import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AttributeFieldPreview } from './AttributeFieldPreview'
import { emptyForm, type AttributeForm } from './attributeForm'

const BASE_URL = 'http://servidor.ejemplo:9090'
const TARGETS = { targetObjectType: [{ code: 'PRODUCT', label: 'Producto' }] }

type FakeResponse = { ok: boolean; status: number; json: () => Promise<unknown> }

function response(status: number, body: unknown): FakeResponse {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

function list(systemId: string, name: string, count: number) {
  const values = Array.from({ length: count }, (_, index) => ({
    id: String(index + 1).padStart(4, '0'),
    label: `${name} ${index + 1}`,
  }))
  return { systemId, name, equivalencesEnabled: false, valueCount: count, values, attributes: [] }
}

/** Answers `GET /api/v1/lists-of-values/{id}` from these lists; other requests fail. */
function stubLists(...lists: ReturnType<typeof list>[]) {
  const fetchMock = vi.fn((url: string) => {
    const found = lists.find((candidate) =>
      url.endsWith(`/api/v1/lists-of-values/${candidate.systemId}`),
    )
    return found
      ? Promise.resolve(response(200, found))
      : Promise.reject(new TypeError(`Unexpected ${url}`))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function form(changes: Partial<AttributeForm>): AttributeForm {
  return { ...emptyForm(), displayName: 'Peso', ...changes }
}

function lov(selectionMode: string, listOfValuesId = ''): AttributeForm {
  return form({ dataType: 'LIST_OF_VALUES', listOfValues: { selectionMode, listOfValuesId } })
}

function field() {
  return screen.getByLabelText<HTMLInputElement>(/\(opcional\)$/)
}

describe('AttributeFieldPreview', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  // --- Panel (FR-001, FR-012) ---

  it('shows the panel, its note and asks for a data type without any request', () => {
    const fetchMock = stubLists()
    render(<AttributeFieldPreview form={form({})} options={{}} />)

    const panel = screen.getByRole('region', { name: 'Vista previa del campo' })
    expect(within(panel).getByRole('heading', { level: 3 })).toBeTruthy()
    expect(
      screen.getByText(
        'Así se verá el campo en un formulario de producto. Lo que escribas aquí no se guarda.',
      ),
    ).toBeTruthy()
    expect(screen.getByText('Elige un tipo de dato para ver el campo.')).toBeTruthy()
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  // --- User Story 1 ---

  it('shows a text field that applies the length, case and mask', () => {
    const fetchMock = stubLists()
    render(
      <AttributeFieldPreview
        form={form({
          dataType: 'TEXT',
          description: 'Código interno',
          text: {
            ...emptyForm().text,
            minLength: '2',
            maxLength: '5',
            caseFormat: 'UPPERCASE',
            captureMask: '[A-Z]+',
          },
        })}
        options={{}}
      />,
    )

    const input = field()
    expect(input.maxLength).toBe(5)
    expect(
      screen.getByText('Código interno · Entre 2 y 5 caracteres · Se escribe en mayúsculas.'),
    ).toBeTruthy()
    fireEvent.change(input, { target: { value: 'ab1' } })
    expect(input.value).toBe('AB1')
    expect(screen.getByText('No cumple la máscara de captura.')).toBeTruthy()
    fireEvent.change(input, { target: { value: 'a' } })
    expect(screen.getByText('Escribe al menos 2 caracteres.')).toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows a multi-line field for rich text', () => {
    render(
      <AttributeFieldPreview
        form={form({ dataType: 'TEXT', text: { ...emptyForm().text, richText: true } })}
        options={{}}
      />,
    )

    expect(field().tagName).toBe('TEXTAREA')
    expect(screen.getByText('Acepta texto enriquecido (HTML).')).toBeTruthy()
  })

  it('shows a number field with its range, decimals and messages', () => {
    render(
      <AttributeFieldPreview
        form={form({
          dataType: 'NUMERIC',
          numeric: { numberFormat: 'DECIMAL', precision: '2', minimum: '0', maximum: '100' },
        })}
        options={{}}
      />,
    )

    const input = field()
    expect(input.type).toBe('number')
    expect(input.min).toBe('0')
    expect(input.max).toBe('100')
    expect(input.step).toBe('0.01')
    fireEvent.change(input, { target: { value: '150' } })
    expect(screen.getByText('El valor debe estar entre 0 y 100.')).toBeTruthy()
    fireEvent.change(input, { target: { value: '1.234' } })
    expect(screen.getByText('Usa como máximo 2 decimales.')).toBeTruthy()
  })

  it('shows a checkbox, a date field and a currency field', () => {
    const { rerender } = render(
      <AttributeFieldPreview form={form({ dataType: 'BOOLEAN' })} options={{}} />,
    )
    expect(screen.getByRole('checkbox', { name: 'Peso (opcional)' })).toBeTruthy()

    rerender(
      <AttributeFieldPreview
        form={form({ dataType: 'DATE_TIME', dateTime: { dateFormat: 'DD/MM/AAAA' } })}
        options={{}}
      />,
    )
    expect(field().placeholder).toBe('DD/MM/AAAA')
    fireEvent.change(field(), { target: { value: '31/02/2026' } })
    expect(screen.getByText('La fecha no existe.')).toBeTruthy()

    rerender(
      <AttributeFieldPreview
        form={form({ dataType: 'CURRENCY', currency: { currencyCode: 'MXN', decimals: '2' } })}
        options={{}}
      />,
    )
    expect(field().type).toBe('number')
    expect(screen.getByText('Moneda: MXN ($) · 2 decimales')).toBeTruthy()
  })

  it('clears the typed value when the configuration changes, without touching the form', () => {
    const configuration = form({ dataType: 'TEXT', text: { ...emptyForm().text, maxLength: '5' } })
    const snapshot = JSON.stringify(configuration)
    const { rerender } = render(<AttributeFieldPreview form={configuration} options={{}} />)
    fireEvent.change(field(), { target: { value: 'hola' } })
    expect(field().value).toBe('hola')

    rerender(<AttributeFieldPreview form={configuration} options={{}} />)
    expect(field().value).toBe('hola')

    rerender(
      <AttributeFieldPreview
        form={{ ...configuration, text: { ...configuration.text, maxLength: '6' } }}
        options={{}}
      />,
    )
    expect(field().value).toBe('')
    expect(JSON.stringify(configuration)).toBe(snapshot)
  })

  // --- User Story 2 ---

  it('asks for a list, then shows its values in a drop-down', async () => {
    const fetchMock = stubLists(list('LOV1', 'Color', 2))
    const { rerender } = render(<AttributeFieldPreview form={lov('SINGLE')} options={{}} />)
    expect(
      screen.getByText('La vista previa necesita una lista de valores asociada al atributo.'),
    ).toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalled()

    rerender(<AttributeFieldPreview form={lov('SINGLE', 'LOV1')} options={{}} />)
    expect(screen.getByText('Cargando valores de la lista…')).toBeTruthy()
    const select = await screen.findByRole('combobox', { name: 'Peso (opcional)' })
    expect(
      within(select)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['Selecciona una opción', 'Color 1', 'Color 2'])
    expect(screen.getByText('Lista: Color')).toBeTruthy()
  })

  it('shows checkboxes for multiple selection and does not read the list again', async () => {
    const fetchMock = stubLists(list('LOV1', 'Color', 2))
    const { rerender } = render(
      <AttributeFieldPreview form={lov('MULTIPLE', 'LOV1')} options={{}} />,
    )

    const group = await screen.findByRole('group', { name: 'Peso (opcional)' })
    const boxes = within(group).getAllByRole('checkbox')
    fireEvent.click(boxes[0])
    fireEvent.click(boxes[1])
    expect(boxes.every((box) => (box as HTMLInputElement).checked)).toBe(true)
    expect(screen.getByText('Lista: Color · Puedes elegir varios valores')).toBeTruthy()

    rerender(
      <AttributeFieldPreview
        form={{ ...lov('MULTIPLE', 'LOV1'), displayName: 'Tono' }}
        options={{}}
      />,
    )
    expect(await screen.findByRole('group', { name: 'Tono (opcional)' })).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows the first 50 values of a long list', async () => {
    stubLists(list('LOV1', 'Grande', 80))
    render(<AttributeFieldPreview form={lov('SINGLE', 'LOV1')} options={{}} />)

    const select = await screen.findByRole('combobox', { name: 'Peso (opcional)' })
    expect(within(select).getAllByRole('option')).toHaveLength(51)
    expect(screen.getByText('Vista previa con los primeros 50 de 80 valores.')).toBeTruthy()
  })

  it('explains an empty list, a failed read and a missing selection mode', async () => {
    stubLists(list('LOV1', 'Color', 0))
    const { rerender } = render(<AttributeFieldPreview form={lov('', 'LOV1')} options={{}} />)
    expect(await screen.findByText('La lista Color todavía no tiene valores.')).toBeTruthy()
    expect(
      screen.getByText('Tipo de selección sin elegir: la vista previa usa selección única.'),
    ).toBeTruthy()

    rerender(<AttributeFieldPreview form={lov('SINGLE', 'LOV404')} options={{}} />)
    expect(
      await screen.findByText(
        'No se pudieron cargar los valores de la lista para la vista previa.',
      ),
    ).toBeTruthy()
  })

  it('shows only the last chosen list when the choice changes during a read', async () => {
    let answerFirst: (value: FakeResponse) => void = () => {}
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        url.endsWith('/LOV1')
          ? new Promise<FakeResponse>((resolve) => {
              answerFirst = resolve
            })
          : Promise.resolve(response(200, list('LOV2', 'Talla', 1))),
      ),
    )
    const { rerender } = render(<AttributeFieldPreview form={lov('SINGLE', 'LOV1')} options={{}} />)
    rerender(<AttributeFieldPreview form={lov('SINGLE', 'LOV2')} options={{}} />)
    await screen.findByText('Lista: Talla')

    answerFirst(response(200, list('LOV1', 'Color', 1)))
    await Promise.resolve()
    expect(screen.queryByText('Lista: Color')).toBeNull()
    expect(screen.getByText('Lista: Talla')).toBeTruthy()
  })

  // --- User Story 3 ---

  it('shows a disabled reference selector', () => {
    render(
      <AttributeFieldPreview
        form={form({
          dataType: 'REFERENCE',
          reference: { targetObjectType: 'PRODUCT', cardinality: 'MULTIPLE' },
        })}
        options={TARGETS}
      />,
    )

    const select = screen.getByRole('combobox', { name: 'Peso (opcional)' }) as HTMLSelectElement
    expect(select.disabled).toBe(true)
    expect(select.textContent).toBe('Selecciona uno o varios: Producto')
    expect(
      screen.getByText('Los objetos se podrán elegir cuando existan los formularios de producto.'),
    ).toBeTruthy()
  })

  it('lists the settings it cannot use and keeps the field usable', () => {
    render(
      <AttributeFieldPreview
        form={form({
          dataType: 'TEXT',
          text: { ...emptyForm().text, minLength: '9', maxLength: '5', captureMask: '[A-Z' },
        })}
        options={{}}
      />,
    )

    expect(screen.getByText('Vista previa con ajustes:')).toBeTruthy()
    expect(
      screen.getByText(
        'La máscara de captura no es una expresión regular válida; la vista previa no la aplica.',
      ),
    ).toBeTruthy()
    expect(
      screen.getByText(
        'La longitud mínima es mayor que la máxima; la vista previa no aplica los límites de longitud.',
      ),
    ).toBeTruthy()
    const input = field()
    expect(input.maxLength).toBe(-1)
    fireEvent.change(input, { target: { value: 'abc1' } })
    expect(input.value).toBe('abc1')
  })
})
