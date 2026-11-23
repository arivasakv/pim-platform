import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BulkLoadSummary } from '../api/listsOfValuesApi'
import { BulkLoadPanel } from './BulkLoadPanel'
import { resultSentence, summarySentence } from './bulkLoadText'

const BASE_URL = 'http://servidor.ejemplo:9090'
const LISTS = `${BASE_URL}/api/v1/lists-of-values`

const SUMMARY: BulkLoadSummary = {
  lists: [
    { name: 'Color', systemId: 'LOV000001', action: 'UPDATE', valuesAdded: 1, valuesUpdated: 1 },
    { name: 'Talla', systemId: null, action: 'CREATE', valuesAdded: 3, valuesUpdated: 0 },
  ],
  listsToCreate: 1,
  listsToUpdate: 1,
  valuesToAdd: 4,
  valuesToUpdate: 1,
  checkToken: 'token-1',
}

const RESULT = {
  lists: [],
  listsCreated: 1,
  listsUpdated: 1,
  valuesAdded: 4,
  valuesUpdated: 1,
}

function response(status: number, body?: unknown) {
  return { ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) }
}

/** Answers the check and the apply calls in order. */
function stubBackend(...answers: Array<ReturnType<typeof response>>) {
  const fetchMock = vi.fn()
  answers.forEach((answer) => fetchMock.mockImplementationOnce(() => Promise.resolve(answer)))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function chooseFile(name = 'listas.xlsx') {
  const file = new File(['contenido'], name)
  fireEvent.change(screen.getByLabelText('Archivo (Excel o CSV)'), { target: { files: [file] } })
  return file
}

describe('BulkLoadPanel (feature 013)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE_URL)
    vi.stubEnv('VITE_DEV_PERMISSIONS', 'ATTRIBUTE_CREATE')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('offers both templates and checks only once a file is chosen', () => {
    render(<BulkLoadPanel onApplied={vi.fn()} />)

    expect(
      screen.getByRole('link', { name: 'Descargar plantilla Excel' }).getAttribute('href'),
    ).toBe(`${LISTS}/bulk-load/template?format=xlsx`)
    expect(screen.getByRole('link', { name: 'Descargar plantilla CSV' }).getAttribute('href')).toBe(
      `${LISTS}/bulk-load/template?format=csv`,
    )
    const check = screen.getByRole<HTMLButtonElement>('button', { name: 'Revisar archivo' })
    expect(check.disabled).toBe(true)
    chooseFile()
    expect(check.disabled).toBe(false)
  })

  it('shows the summary, saves nothing until confirmed, then reports the result', async () => {
    const fetchMock = stubBackend(response(200, SUMMARY), response(200, RESULT))
    const onApplied = vi.fn()
    render(<BulkLoadPanel onApplied={onApplied} />)
    const file = chooseFile()

    fireEvent.click(screen.getByRole('button', { name: 'Revisar archivo' }))

    const summary = await screen.findByRole('region', { name: 'Resumen de la carga' })
    expect(
      within(summary).getByText(
        'Se creará 1 lista y se actualizará 1. Se agregarán 4 valores y se actualizará 1.',
      ),
    ).toBeTruthy()
    const rows = within(summary).getAllByRole('row').slice(1)
    expect(rows.map((row) => row.textContent)).toEqual(['ColorActualizar11', 'TallaCrear30'])
    expect(within(summary).getByText('Nada se ha guardado todavía.')).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    fireEvent.click(within(summary).getByRole('button', { name: 'Confirmar carga' }))

    expect(
      await screen.findByText(
        'Carga aplicada: 1 lista creada, 1 actualizada; 4 valores agregados y 1 actualizado.',
      ),
    ).toBeTruthy()
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe(`${LISTS}/bulk-load`)
    expect((init.body as FormData).get('file')).toBe(file)
    expect((init.body as FormData).get('checkToken')).toBe('token-1')
    expect(onApplied).toHaveBeenCalledWith(RESULT)
    expect(screen.queryByRole('region', { name: 'Resumen de la carga' })).toBeNull()
  })

  it('cancels without sending anything', async () => {
    const fetchMock = stubBackend(response(200, SUMMARY))
    render(<BulkLoadPanel onApplied={vi.fn()} />)
    chooseFile()
    fireEvent.click(screen.getByRole('button', { name: 'Revisar archivo' }))
    await screen.findByRole('region', { name: 'Resumen de la carga' })

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('region', { name: 'Resumen de la carga' })).toBeNull()
    expect(
      screen.getByRole<HTMLButtonElement>('button', { name: 'Revisar archivo' }).disabled,
    ).toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('lists every wrong row of the file', async () => {
    stubBackend(
      response(422, {
        detail: 'El archivo tiene 2 errores. No se aplicó ningún valor.',
        rowErrors: [
          { row: 3, column: 'Etiqueta', code: 'REQUIRED', message: 'Este dato es obligatorio.' },
          { row: 5, column: 'Lista', code: 'INVALID_LIST_NAME', message: 'Nombre muy largo.' },
        ],
      }),
    )
    render(<BulkLoadPanel onApplied={vi.fn()} />)
    chooseFile()

    fireEvent.click(screen.getByRole('button', { name: 'Revisar archivo' }))

    const table = await screen.findByRole('table', { name: 'Errores del archivo' })
    expect(screen.getByText('El archivo tiene 2 errores. No se aplicó ningún valor.')).toBeTruthy()
    expect(
      within(table)
        .getAllByRole('row')
        .map((row) => row.textContent),
    ).toEqual([
      'FilaColumnaProblema',
      '3EtiquetaEste dato es obligatorio.',
      '5ListaNombre muy largo.',
    ])
  })

  it('asks to check again when the lists changed since the check', async () => {
    stubBackend(
      response(200, SUMMARY),
      response(409, {
        detail: 'Las listas cambiaron desde la revisión. Vuelve a revisar el archivo.',
      }),
    )
    const onApplied = vi.fn()
    render(<BulkLoadPanel onApplied={onApplied} />)
    chooseFile()
    fireEvent.click(screen.getByRole('button', { name: 'Revisar archivo' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar carga' }))

    expect(
      await screen.findByText(
        'Las listas cambiaron desde la revisión. Vuelve a revisar el archivo.',
      ),
    ).toBeTruthy()
    expect(onApplied).not.toHaveBeenCalled()
    await waitFor(() =>
      expect(
        screen.getByRole<HTMLButtonElement>('button', { name: 'Revisar archivo' }).disabled,
      ).toBe(false),
    )
  })

  it('reports an unreachable backend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    render(<BulkLoadPanel onApplied={vi.fn()} />)
    chooseFile()

    fireEvent.click(screen.getByRole('button', { name: 'Revisar archivo' }))

    expect(
      await screen.findByText('No se pudo conectar con el servidor. Inténtalo de nuevo.'),
    ).toBeTruthy()
  })
})

describe('bulk load sentences', () => {
  it('uses singular and plural', () => {
    expect(
      summarySentence({
        ...SUMMARY,
        listsToCreate: 2,
        listsToUpdate: 0,
        valuesToAdd: 1,
        valuesToUpdate: 3,
      }),
    ).toBe('Se crearán 2 listas y se actualizarán 0. Se agregará 1 valor y se actualizarán 3.')
    expect(resultSentence({ ...RESULT, listsCreated: 0, listsUpdated: 2, valuesAdded: 1 })).toBe(
      'Carga aplicada: 0 listas creadas, 2 actualizadas; 1 valor agregado y 1 actualizado.',
    )
  })
})
