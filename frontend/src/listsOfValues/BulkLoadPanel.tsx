import { useState } from 'react'
import {
  ListOfValuesApiError,
  applyBulkLoad,
  bulkLoadTemplateUrl,
  checkBulkLoad,
  type BulkLoadResult,
  type BulkLoadSummary,
  type ImportRowError,
} from '../api/listsOfValuesApi'
import { Button, Notification } from '../components/ui'
import styles from './BulkLoadPanel.module.css'
import { resultSentence, summarySentence } from './bulkLoadText'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

/** Where the load is (feature 013 UI contract «Carga Masiva de LOV»). */
type State =
  | { step: 'file' }
  | { step: 'checking' }
  | { step: 'failed'; message: string; rows: ImportRowError[] }
  | { step: 'outdated'; message: string }
  | { step: 'summary'; summary: BulkLoadSummary }
  | { step: 'applying'; summary: BulkLoadSummary }
  | { step: 'done'; message: string }

type BulkLoadPanelProps = {
  /** Called after a file was applied, so the page reloads its lists. */
  onApplied: (result: BulkLoadResult) => void
}

function messageOf(error: unknown): string {
  if (error instanceof ListOfValuesApiError && error.kind !== 'unavailable' && error.detail) {
    return error.detail
  }
  return CONNECTION_ERROR
}

/**
 * «Carga Masiva de LOV» (feature 013): one CSV or Excel file creates or updates several lists. The
 * file is checked first and a summary is shown; nothing is saved until the user confirms (FR-010).
 * The backend decides everything; this panel only shows its answers.
 */
export function BulkLoadPanel({ onApplied }: BulkLoadPanelProps) {
  const [file, setFile] = useState<File | null>(null)
  const [fileInputKey, setFileInputKey] = useState(0)
  const [state, setState] = useState<State>({ step: 'file' })
  const busy = state.step === 'checking' || state.step === 'applying'
  const excelTemplate = bulkLoadTemplateUrl('xlsx')
  const csvTemplate = bulkLoadTemplateUrl('csv')

  function chooseFile(chosen: File | null) {
    setFile(chosen)
    setState({ step: 'file' })
  }

  async function check() {
    if (!file) return
    setState({ step: 'checking' })
    try {
      setState({ step: 'summary', summary: await checkBulkLoad(file) })
    } catch (error) {
      setState({
        step: 'failed',
        message: messageOf(error),
        rows: error instanceof ListOfValuesApiError ? error.rowErrors : [],
      })
    }
  }

  async function apply(summary: BulkLoadSummary) {
    if (!file) return
    setState({ step: 'applying', summary })
    try {
      const result = await applyBulkLoad(file, summary.checkToken)
      setFile(null)
      setFileInputKey((key) => key + 1)
      setState({ step: 'done', message: resultSentence(result) })
      onApplied(result)
    } catch (error) {
      if (error instanceof ListOfValuesApiError && error.kind === 'conflict') {
        setState({ step: 'outdated', message: messageOf(error) })
        return
      }
      setState({
        step: 'failed',
        message: messageOf(error),
        rows: error instanceof ListOfValuesApiError ? error.rowErrors : [],
      })
    }
  }

  function cancel() {
    setFile(null)
    setFileInputKey((key) => key + 1)
    setState({ step: 'file' })
  }

  const summary = state.step === 'summary' || state.step === 'applying' ? state.summary : null

  return (
    <div className={styles.panel}>
      <p className={styles.muted}>
        Sube un archivo Excel (.xlsx) o CSV con las columnas Lista, ID, Etiqueta y Equivalencia.
        Cada fila es un valor de la lista indicada; si la lista no existe, se crea. Un ID vacío se
        genera. Un ID que ya existe en la lista actualiza su etiqueta. Primero verás un resumen;
        nada se guarda hasta que confirmes.
      </p>
      {(excelTemplate || csvTemplate) && (
        <div className={styles.links}>
          {excelTemplate && (
            <a className={styles.link} href={excelTemplate} download>
              Descargar plantilla Excel
            </a>
          )}
          {csvTemplate && (
            <a className={styles.link} href={csvTemplate} download>
              Descargar plantilla CSV
            </a>
          )}
        </div>
      )}
      <div className={styles.fileRow}>
        <label htmlFor="archivo-carga-masiva" className={styles.fileLabel}>
          Archivo (Excel o CSV)
        </label>
        <input
          key={fileInputKey}
          id="archivo-carga-masiva"
          type="file"
          accept=".xlsx,.csv"
          className={styles.fileInput}
          disabled={busy}
          onChange={(event) => chooseFile(event.target.files?.[0] ?? null)}
        />
        <Button disabled={busy || !file || summary !== null} onClick={() => void check()}>
          Revisar archivo
        </Button>
      </div>

      <div aria-live="polite">
        {state.step === 'checking' && <p className={styles.muted}>Revisando archivo…</p>}
        {state.step === 'applying' && <p className={styles.muted}>Aplicando carga…</p>}
        {state.step === 'done' && <Notification variant="success">{state.message}</Notification>}
        {state.step === 'outdated' && (
          <Notification variant="warning">{state.message}</Notification>
        )}
        {state.step === 'failed' && (
          <Notification variant="error">
            <p className={styles.inlineMessage}>{state.message}</p>
            {state.rows.length > 0 && (
              <div className={styles.tableWrapper}>
                <table className={styles.table} aria-label="Errores del archivo">
                  <thead>
                    <tr>
                      <th scope="col">Fila</th>
                      <th scope="col">Columna</th>
                      <th scope="col">Problema</th>
                    </tr>
                  </thead>
                  <tbody>
                    {state.rows.map((row) => (
                      <tr key={`${row.row}-${row.column}`}>
                        <td>{row.row}</td>
                        <td>{row.column}</td>
                        <td>{row.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Notification>
        )}
      </div>

      {summary && (
        <section className={styles.summary} aria-labelledby="resumen-carga">
          <h3 id="resumen-carga" className={styles.subheading}>
            Resumen de la carga
          </h3>
          <p>{summarySentence(summary)}</p>
          <div className={styles.tableWrapper}>
            <table className={styles.table} aria-label="Listas de la carga">
              <thead>
                <tr>
                  <th scope="col">Lista</th>
                  <th scope="col">Acción</th>
                  <th scope="col">Valores nuevos</th>
                  <th scope="col">Valores actualizados</th>
                </tr>
              </thead>
              <tbody>
                {summary.lists.map((list) => (
                  <tr key={list.name}>
                    <td>{list.name}</td>
                    <td>{list.action === 'CREATE' ? 'Crear' : 'Actualizar'}</td>
                    <td>{list.valuesAdded}</td>
                    <td>{list.valuesUpdated}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className={styles.muted}>Nada se ha guardado todavía.</p>
          <div className={styles.actions}>
            <Button disabled={busy} onClick={() => void apply(summary)}>
              Confirmar carga
            </Button>
            <Button variant="secondary" disabled={busy} onClick={cancel}>
              Cancelar
            </Button>
          </div>
        </section>
      )}
    </div>
  )
}
