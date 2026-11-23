import { useEffect, useState, type FormEvent } from 'react'
import {
  ListOfValuesApiError,
  addValue,
  canChangeLists,
  createList,
  deleteList,
  deleteValue,
  getList,
  importTemplateUrl,
  importValues,
  listLists,
  updateList,
  updateValue,
  type ImportRowError,
  type ListOfValues,
  type ListOfValuesSummary,
  type ListValue,
} from '../api/listsOfValuesApi'
import { Button, Card, CheckboxField, Notification, PageHeader, TextField } from '../components/ui'
import { BulkLoadPanel } from '../listsOfValues/BulkLoadPanel'
import styles from './ListsOfValuesPage.module.css'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'
const NAME_REQUIRED = 'Escribe el nombre de la lista.'
const LABEL_REQUIRED = 'Escribe la etiqueta.'
/** Rows shown at a time; "Mostrar más" adds this many (research R-14). */
const PAGE_SIZE = 500

const SELECTION_LABELS: Record<string, string> = {
  SINGLE: 'Selección única',
  MULTIPLE: 'Selección múltiple',
}

type ListState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; lists: ListOfValuesSummary[] }

type NewValue = {
  id: string
  label: string
  equivalence: string
  labelError?: string
  idError?: string
}
type EditingValue = { id: string; label: string; equivalence: string; labelError?: string }
type ImportReport =
  | { status: 'done'; message: string }
  | { status: 'failed'; message: string; rows: ImportRowError[] }

const EMPTY_VALUE: NewValue = { id: '', label: '', equivalence: '' }

function plural(count: number, one: string, many: string): string {
  return count === 1 ? `1 ${one}` : `${count} ${many}`
}

/** The backend's Spanish message, or the connection message when it could not be reached. */
function messageOf(error: unknown): string {
  if (error instanceof ListOfValuesApiError && error.kind !== 'unavailable' && error.detail) {
    return error.detail
  }
  return CONNECTION_ERROR
}

/** The backend's message for one field, when the problem names it. */
function fieldMessage(error: unknown, field: string): string | undefined {
  if (!(error instanceof ListOfValuesApiError)) return undefined
  return error.errors.find((fieldError) => fieldError.field === field)?.message
}

function matches(value: ListValue, filter: string): boolean {
  const text = filter.trim().toLowerCase()
  return (
    text === '' || value.id.toLowerCase().includes(text) || value.label.toLowerCase().includes(text)
  )
}

/**
 * Lists of values (feature 006): create, rename and delete lists («Crear LOV»), or load several
 * lists from one Excel or CSV file («Carga Masiva de LOV», feature 013); add, edit and delete their
 * values one by one or from a CSV file; turn the equivalence column on or off; see which
 * attributes use each list. Every list and value comes from the backend (FR-025). Change controls
 * are shown only with the development permission `ATTRIBUTE_CREATE`; the backend still checks it.
 */
export function ListsOfValuesPage() {
  const canChange = canChangeLists()
  const [listState, setListState] = useState<ListState>({ status: 'loading' })
  const [listRequest, setListRequest] = useState(0)
  const [confirmation, setConfirmation] = useState<string>()

  /** The card the header actions opened (feature 013 FR-001): one at a time. */
  const [openCard, setOpenCard] = useState<'none' | 'create' | 'bulk'>('none')
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string>()
  const [createError, setCreateError] = useState<string>()
  const [creating, setCreating] = useState(false)

  const [selected, setSelected] = useState<ListOfValues | null>(null)
  const [panelError, setPanelError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [renaming, setRenaming] = useState<{ name: string; error?: string } | null>(null)
  const [confirmingListDelete, setConfirmingListDelete] = useState(false)
  const [newValue, setNewValue] = useState<NewValue>(EMPTY_VALUE)
  const [editingValue, setEditingValue] = useState<EditingValue | null>(null)
  const [valueToDelete, setValueToDelete] = useState<ListValue | null>(null)
  const [filter, setFilter] = useState('')
  const [shown, setShown] = useState(PAGE_SIZE)
  const [file, setFile] = useState<File | null>(null)
  const [fileInputKey, setFileInputKey] = useState(0)
  const [importReport, setImportReport] = useState<ImportReport | null>(null)

  // Loads the lists; a reload increments listRequest. State changes only in the callbacks.
  useEffect(() => {
    let isCurrent = true
    listLists().then(
      (lists) => {
        if (isCurrent) setListState({ status: 'ready', lists })
      },
      () => {
        if (isCurrent) setListState({ status: 'error' })
      },
    )
    return () => {
      isCurrent = false
    }
  }, [listRequest])

  const reloadList = () => setListRequest((request) => request + 1)

  function retryList() {
    setListState({ status: 'loading' })
    reloadList()
  }

  function resetPanel() {
    setPanelError(undefined)
    setRenaming(null)
    setConfirmingListDelete(false)
    setNewValue(EMPTY_VALUE)
    setEditingValue(null)
    setValueToDelete(null)
    setFilter('')
    setShown(PAGE_SIZE)
    setFile(null)
    setFileInputKey((key) => key + 1)
    setImportReport(null)
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setConfirmation(undefined)
    setCreateError(undefined)
    if (!name.trim()) {
      setNameError(NAME_REQUIRED)
      return
    }
    setNameError(undefined)
    setCreating(true)
    try {
      const list = await createList(name.trim())
      setName('')
      setOpenCard('none')
      setConfirmation(`Lista «${list.name}» creada.`)
      resetPanel()
      setSelected(list)
      reloadList()
    } catch (error) {
      const message = fieldMessage(error, 'name')
      if (message) setNameError(message)
      else setCreateError(messageOf(error))
    } finally {
      setCreating(false)
    }
  }

  async function openList(listId: string) {
    resetPanel()
    try {
      setSelected(await getList(listId))
    } catch (error) {
      setSelected(null)
      setPanelError(messageOf(error))
    }
  }

  /** Runs one change, then reloads the open list and the list counts. */
  async function change(list: ListOfValues, action: () => Promise<void>): Promise<boolean> {
    setPanelError(undefined)
    setConfirmation(undefined)
    setBusy(true)
    try {
      await action()
      setSelected(await getList(list.systemId))
      reloadList()
      return true
    } catch (error) {
      setPanelError(messageOf(error))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function handleAddValue(event: FormEvent<HTMLFormElement>, list: ListOfValues) {
    event.preventDefault()
    if (!newValue.label.trim()) {
      setNewValue({ ...newValue, labelError: LABEL_REQUIRED })
      return
    }
    setPanelError(undefined)
    setBusy(true)
    try {
      await addValue(list.systemId, {
        id: newValue.id.trim() || undefined,
        label: newValue.label.trim(),
        equivalence: newValue.equivalence.trim() || null,
      })
      setNewValue(EMPTY_VALUE)
      setSelected(await getList(list.systemId))
      reloadList()
    } catch (error) {
      const idError = fieldMessage(error, 'id')
      const labelError = fieldMessage(error, 'label')
      if (idError || labelError) setNewValue({ ...newValue, idError, labelError })
      else setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  async function saveValue(
    event: FormEvent<HTMLFormElement>,
    list: ListOfValues,
    edit: EditingValue,
  ) {
    event.preventDefault()
    if (!edit.label.trim()) {
      setEditingValue({ ...edit, labelError: LABEL_REQUIRED })
      return
    }
    const saved = await change(list, async () => {
      await updateValue(list.systemId, edit.id, {
        label: edit.label.trim(),
        equivalence: edit.equivalence.trim() || null,
      })
    })
    if (saved) setEditingValue(null)
  }

  async function confirmValueDelete(list: ListOfValues, value: ListValue) {
    const deleted = await change(list, () => deleteValue(list.systemId, value.id))
    if (deleted) {
      setValueToDelete(null)
      setConfirmation(`Valor «${value.label}» (${value.id}) eliminado.`)
    }
  }

  async function saveName(event: FormEvent<HTMLFormElement>, list: ListOfValues) {
    event.preventDefault()
    if (!renaming) return
    if (!renaming.name.trim()) {
      setRenaming({ ...renaming, error: NAME_REQUIRED })
      return
    }
    setPanelError(undefined)
    setBusy(true)
    try {
      setSelected(
        await updateList(list.systemId, {
          name: renaming.name.trim(),
          equivalencesEnabled: list.equivalencesEnabled,
        }),
      )
      setRenaming(null)
      reloadList()
    } catch (error) {
      const message = fieldMessage(error, 'name')
      if (message) setRenaming({ ...renaming, error: message })
      else setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  function toggleEquivalences(list: ListOfValues, enabled: boolean) {
    void change(list, async () => {
      await updateList(list.systemId, { name: list.name, equivalencesEnabled: enabled })
    })
  }

  async function removeList(list: ListOfValues) {
    setPanelError(undefined)
    setBusy(true)
    try {
      await deleteList(list.systemId)
      resetPanel()
      setSelected(null)
      setConfirmation(`Lista «${list.name}» eliminada.`)
      reloadList()
    } catch (error) {
      setConfirmingListDelete(false)
      setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  async function handleImport(list: ListOfValues) {
    if (!file) return
    setPanelError(undefined)
    setImportReport(null)
    setBusy(true)
    try {
      const result = await importValues(list.systemId, file)
      setImportReport({
        status: 'done',
        message: `Se agregaron ${plural(result.added, 'valor', 'valores')} y se actualizaron ${result.updated}.`,
      })
      setFile(null)
      setFileInputKey((key) => key + 1)
      setSelected(await getList(list.systemId))
      reloadList()
    } catch (error) {
      setImportReport({
        status: 'failed',
        message: messageOf(error),
        rows: error instanceof ListOfValuesApiError ? error.rowErrors : [],
      })
    } finally {
      setBusy(false)
    }
  }

  const visibleValues = selected ? selected.values.filter((value) => matches(value, filter)) : []
  const templateUrl = importTemplateUrl()

  return (
    <>
      <PageHeader
        title="Lista de Valores (LOV's)"
        description="Administra listas reutilizables de pares ID y etiqueta. Una lista puede usarse en varios atributos."
        actions={
          canChange && (
            <>
              <Button onClick={() => setOpenCard('create')}>Crear LOV</Button>
              <Button variant="secondary" onClick={() => setOpenCard('bulk')}>
                Carga Masiva de LOV
              </Button>
            </>
          )
        }
      />

      {confirmation && (
        <div className={styles.messages}>
          <Notification variant="success" onDismiss={() => setConfirmation(undefined)}>
            {confirmation}
          </Notification>
        </div>
      )}

      <div className={styles.layout}>
        <div className={styles.column}>
          {canChange && openCard === 'create' && (
            <Card title="Crear LOV">
              <form className={styles.form} noValidate onSubmit={handleCreate}>
                <TextField
                  id="lista-nombre"
                  label="Nombre"
                  value={name}
                  maxLength={80}
                  error={nameError}
                  onChange={(event) => setName(event.target.value)}
                />
                {createError && <Notification variant="error">{createError}</Notification>}
                <div className={styles.actions}>
                  <Button type="submit" disabled={creating}>
                    Crear LOV
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={creating}
                    onClick={() => {
                      setOpenCard('none')
                      setName('')
                      setNameError(undefined)
                      setCreateError(undefined)
                    }}
                  >
                    Cancelar
                  </Button>
                </div>
              </form>
            </Card>
          )}

          {canChange && openCard === 'bulk' && (
            <Card title="Carga Masiva de LOV">
              <BulkLoadPanel onApplied={reloadList} />
            </Card>
          )}

          <Card title="Listas">
            {listState.status === 'loading' && <p className={styles.muted}>Cargando listas…</p>}
            {listState.status === 'error' && (
              <Notification variant="error">
                <p className={styles.inlineMessage}>{CONNECTION_ERROR}</p>
                <Button variant="secondary" onClick={retryList}>
                  Reintentar
                </Button>
              </Notification>
            )}
            {listState.status === 'ready' && listState.lists.length === 0 && (
              <p className={styles.muted}>Todavía no hay listas de valores.</p>
            )}
            {listState.status === 'ready' && listState.lists.length > 0 && (
              <ul className={styles.listOfLists}>
                {listState.lists.map((list) => (
                  <li key={list.systemId}>
                    <button
                      type="button"
                      className={`${styles.listButton} ${selected?.systemId === list.systemId ? styles.selected : ''}`}
                      aria-pressed={selected?.systemId === list.systemId}
                      onClick={() => void openList(list.systemId)}
                    >
                      <span className={styles.listName}>{list.name}</span>
                      <span className={styles.systemId}>{list.systemId}</span>
                      <span className={styles.counts}>
                        {plural(list.valueCount, 'valor', 'valores')} ·{' '}
                        {plural(list.attributeCount, 'atributo', 'atributos')}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        {(selected || panelError) && (
          <div className={styles.column}>
            {panelError && <Notification variant="error">{panelError}</Notification>}
            {selected && (
              <Card
                title={selected.name}
                actions={
                  canChange && !renaming && !confirmingListDelete ? (
                    <>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => setRenaming({ name: selected.name })}
                      >
                        Editar lista
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => setConfirmingListDelete(true)}
                      >
                        Eliminar lista
                      </Button>
                    </>
                  ) : undefined
                }
              >
                <p className={styles.systemId}>{selected.systemId}</p>

                {confirmingListDelete && (
                  <Notification variant="warning">
                    <p className={styles.inlineMessage}>
                      ¿Eliminar la lista «{selected.name}» y sus{' '}
                      {plural(selected.valueCount, 'valor', 'valores')}?
                    </p>
                    <div className={styles.actions}>
                      <Button variant="secondary" onClick={() => setConfirmingListDelete(false)}>
                        Cancelar
                      </Button>
                      <Button disabled={busy} onClick={() => void removeList(selected)}>
                        Sí, eliminar lista
                      </Button>
                    </div>
                  </Notification>
                )}

                {renaming && (
                  <form
                    className={styles.form}
                    noValidate
                    onSubmit={(event) => void saveName(event, selected)}
                  >
                    <TextField
                      id="editar-lista-nombre"
                      label="Nombre de la lista"
                      value={renaming.name}
                      maxLength={80}
                      error={renaming.error}
                      onChange={(event) => setRenaming({ name: event.target.value })}
                    />
                    <div className={styles.actions}>
                      <Button variant="secondary" onClick={() => setRenaming(null)}>
                        Cancelar
                      </Button>
                      <Button type="submit" disabled={busy}>
                        Guardar
                      </Button>
                    </div>
                  </form>
                )}

                {canChange && (
                  <CheckboxField
                    id="usar-equivalencias"
                    label="Usar columna de equivalencias"
                    checked={selected.equivalencesEnabled}
                    disabled={busy}
                    onChange={(checked) => toggleEquivalences(selected, checked)}
                  />
                )}

                <h3 className={styles.subheading}>Valores</h3>
                {selected.values.length === 0 ? (
                  <p className={styles.muted}>Esta lista todavía no tiene valores.</p>
                ) : (
                  <>
                    <TextField
                      id="buscar-valor"
                      label="Buscar por ID o etiqueta"
                      type="search"
                      value={filter}
                      onChange={(event) => {
                        setFilter(event.target.value)
                        setShown(PAGE_SIZE)
                      }}
                    />
                    {valueToDelete && (
                      <Notification variant="warning">
                        <p className={styles.inlineMessage}>
                          ¿Eliminar el valor «{valueToDelete.label}» ({valueToDelete.id})?
                        </p>
                        <div className={styles.actions}>
                          <Button variant="secondary" onClick={() => setValueToDelete(null)}>
                            Cancelar
                          </Button>
                          <Button
                            disabled={busy}
                            onClick={() => void confirmValueDelete(selected, valueToDelete)}
                          >
                            Sí, eliminar
                          </Button>
                        </div>
                      </Notification>
                    )}
                    <div className={styles.tableWrapper}>
                      <table className={styles.table}>
                        <thead>
                          <tr>
                            <th scope="col">ID</th>
                            <th scope="col">Etiqueta</th>
                            {selected.equivalencesEnabled && <th scope="col">Equivalencia</th>}
                            {canChange && (
                              <th scope="col">
                                <span className="visually-hidden">Acciones</span>
                              </th>
                            )}
                          </tr>
                        </thead>
                        <tbody>
                          {visibleValues.slice(0, shown).map((value) =>
                            editingValue?.id === value.id ? (
                              <tr key={value.id}>
                                <td colSpan={canChange ? 4 : 3}>
                                  <form
                                    className={styles.inlineForm}
                                    noValidate
                                    onSubmit={(event) =>
                                      void saveValue(event, selected, editingValue)
                                    }
                                  >
                                    <span className={styles.valueId}>{value.id}</span>
                                    <TextField
                                      id={`editar-etiqueta-${value.id}`}
                                      label="Etiqueta"
                                      value={editingValue.label}
                                      maxLength={200}
                                      error={editingValue.labelError}
                                      onChange={(event) =>
                                        setEditingValue({
                                          ...editingValue,
                                          label: event.target.value,
                                        })
                                      }
                                    />
                                    {selected.equivalencesEnabled && (
                                      <TextField
                                        id={`editar-equivalencia-${value.id}`}
                                        label="Equivalencia (opcional)"
                                        value={editingValue.equivalence}
                                        maxLength={200}
                                        onChange={(event) =>
                                          setEditingValue({
                                            ...editingValue,
                                            equivalence: event.target.value,
                                          })
                                        }
                                      />
                                    )}
                                    <div className={styles.actions}>
                                      <Button
                                        variant="secondary"
                                        onClick={() => setEditingValue(null)}
                                      >
                                        Cancelar
                                      </Button>
                                      <Button type="submit" disabled={busy}>
                                        Guardar
                                      </Button>
                                    </div>
                                  </form>
                                </td>
                              </tr>
                            ) : (
                              <tr key={value.id}>
                                <td className={styles.valueId}>{value.id}</td>
                                <td>{value.label}</td>
                                {selected.equivalencesEnabled && <td>{value.equivalence ?? ''}</td>}
                                {canChange && (
                                  <td className={styles.rowActions}>
                                    <Button
                                      variant="secondary"
                                      disabled={busy}
                                      aria-label={`Editar ${value.label} (${value.id})`}
                                      onClick={() =>
                                        setEditingValue({
                                          id: value.id,
                                          label: value.label,
                                          equivalence: value.equivalence ?? '',
                                        })
                                      }
                                    >
                                      Editar
                                    </Button>
                                    <Button
                                      variant="secondary"
                                      disabled={busy}
                                      aria-label={`Eliminar ${value.label} (${value.id})`}
                                      onClick={() => setValueToDelete(value)}
                                    >
                                      Eliminar
                                    </Button>
                                  </td>
                                )}
                              </tr>
                            ),
                          )}
                        </tbody>
                      </table>
                    </div>
                    {visibleValues.length === 0 && (
                      <p className={styles.muted}>Ningún valor coincide con la búsqueda.</p>
                    )}
                    {visibleValues.length > shown && (
                      <div className={styles.actions}>
                        <span className={styles.muted}>
                          Mostrando {shown} de {visibleValues.length} valores.
                        </span>
                        <Button variant="secondary" onClick={() => setShown(shown + PAGE_SIZE)}>
                          Mostrar más
                        </Button>
                      </div>
                    )}
                  </>
                )}

                {canChange && (
                  <form
                    className={styles.section}
                    noValidate
                    onSubmit={(event) => void handleAddValue(event, selected)}
                  >
                    <h3 className={styles.subheading}>Agregar valor</h3>
                    <div className={styles.row}>
                      <TextField
                        id="valor-id"
                        label="ID (opcional)"
                        hint="Déjalo vacío para que el sistema genere uno."
                        maxLength={40}
                        value={newValue.id}
                        error={newValue.idError}
                        onChange={(event) => setNewValue({ ...newValue, id: event.target.value })}
                      />
                      <TextField
                        id="valor-etiqueta"
                        label="Etiqueta"
                        maxLength={200}
                        value={newValue.label}
                        error={newValue.labelError}
                        onChange={(event) =>
                          setNewValue({ ...newValue, label: event.target.value })
                        }
                      />
                      {selected.equivalencesEnabled && (
                        <TextField
                          id="valor-equivalencia"
                          label="Equivalencia (opcional)"
                          maxLength={200}
                          value={newValue.equivalence}
                          onChange={(event) =>
                            setNewValue({ ...newValue, equivalence: event.target.value })
                          }
                        />
                      )}
                    </div>
                    <div className={styles.actions}>
                      <Button type="submit" disabled={busy}>
                        Agregar valor
                      </Button>
                    </div>
                  </form>
                )}

                {canChange && (
                  <div className={styles.section}>
                    <h3 className={styles.subheading}>Carga masiva</h3>
                    <p className={styles.muted}>
                      Sube un archivo CSV con las columnas ID, Etiqueta y Equivalencia. Un ID que ya
                      existe actualiza su valor; un ID vacío se genera. Si alguna fila tiene
                      errores, no se aplica ningún valor.
                    </p>
                    {templateUrl && (
                      <a className={styles.link} href={templateUrl} download>
                        Descargar plantilla
                      </a>
                    )}
                    <div className={styles.fileRow}>
                      <label htmlFor="archivo-csv" className={styles.fileLabel}>
                        Archivo CSV
                      </label>
                      <input
                        key={fileInputKey}
                        id="archivo-csv"
                        type="file"
                        accept=".csv,text/csv"
                        className={styles.fileInput}
                        onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                      />
                      <Button disabled={busy || !file} onClick={() => void handleImport(selected)}>
                        Cargar archivo CSV
                      </Button>
                    </div>
                    {importReport?.status === 'done' && (
                      <Notification variant="success">{importReport.message}</Notification>
                    )}
                    {importReport?.status === 'failed' && (
                      <Notification variant="error">
                        <p className={styles.inlineMessage}>{importReport.message}</p>
                        {importReport.rows.length > 0 && (
                          <div className={styles.tableWrapper}>
                            <table className={styles.table} aria-label="Errores del archivo">
                              <thead>
                                <tr>
                                  <th scope="col">Fila</th>
                                  <th scope="col">Columna</th>
                                  <th scope="col">Error</th>
                                </tr>
                              </thead>
                              <tbody>
                                {importReport.rows.map((row) => (
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
                )}

                <h3 className={styles.subheading}>Atributos que la usan</h3>
                {selected.attributes.length === 0 ? (
                  <p className={styles.muted}>Ningún atributo usa esta lista.</p>
                ) : (
                  <ul className={styles.attributeList}>
                    {selected.attributes.map((attribute) => (
                      <li key={attribute.systemId} className={styles.attribute}>
                        <span className={styles.listName}>{attribute.displayName}</span>
                        <span className={styles.systemId}>{attribute.systemId}</span>
                        <span className={styles.muted}>
                          {SELECTION_LABELS[attribute.selectionMode] ?? attribute.selectionMode}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            )}
          </div>
        )}
      </div>
    </>
  )
}
