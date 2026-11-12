import { useEffect, useState, type FormEvent } from 'react'
import { listAttributes, type Attribute } from '../api/attributesApi'
import {
  AttributeGroupApiError,
  addAttributes,
  canChangeGroups,
  createGroup,
  deleteGroup,
  getGroup,
  listGroups,
  removeAttribute,
  updateGroup,
  type AttributeGroup,
  type AttributeGroupSummary,
} from '../api/attributeGroupsApi'
import { matchesFilters } from '../attributes/attributeBrowsing'
import {
  Button,
  Card,
  CheckboxField,
  Notification,
  PageHeader,
  TextAreaField,
  TextField,
} from '../components/ui'
import styles from './AttributeGroupsPage.module.css'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'
const NAME_REQUIRED = 'Escribe el nombre del grupo.'
/** Attributes shown at a time in "Agregar atributos" (feature 009 FR-015). */
const PICKER_PAGE_SIZE = 50

/** Spanish labels of feature 004's data types, for the member list. */
const DATA_TYPE_LABELS: Record<string, string> = {
  TEXT: 'Texto',
  NUMERIC: 'Numérico',
  BOOLEAN: 'Booleano',
  LIST_OF_VALUES: 'Lista de valores',
  DATE_TIME: 'Fecha y hora',
  CURRENCY: 'Moneda',
  REFERENCE: 'Referencia',
}

type ListState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; groups: AttributeGroupSummary[] }

type Picker = { attributes: Attribute[]; chosen: string[]; search: string; shown: number }

type Editing = { name: string; description: string; nameError?: string }

function countLabel(count: number): string {
  return count === 1 ? '1 atributo' : `${count} atributos`
}

/** The backend's Spanish message, or the connection message when it could not be reached. */
function messageOf(error: unknown): string {
  if (error instanceof AttributeGroupApiError && error.kind !== 'unavailable' && error.detail) {
    return error.detail
  }
  return CONNECTION_ERROR
}

/** The backend's message for one field, when the problem names it. */
function fieldMessage(error: unknown, field: string): string | undefined {
  if (!(error instanceof AttributeGroupApiError)) return undefined
  return error.errors.find((fieldError) => fieldError.field === field)?.message
}

/**
 * Attribute groups (feature 005): list, create, rename and delete groups, and add or remove
 * attributes. Every group and attribute comes from the backend (FR-013). Change controls are shown
 * only with the development permission `ATTRIBUTE_CREATE`; the backend still checks it (FR-002).
 */
export function AttributeGroupsPage() {
  const canChange = canChangeGroups()
  const [listState, setListState] = useState<ListState>({ status: 'loading' })
  const [listRequest, setListRequest] = useState(0)
  const [confirmation, setConfirmation] = useState<string>()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [nameError, setNameError] = useState<string>()
  const [createError, setCreateError] = useState<string>()
  const [creating, setCreating] = useState(false)

  const [selected, setSelected] = useState<AttributeGroup | null>(null)
  const [panelError, setPanelError] = useState<string>()
  const [picker, setPicker] = useState<Picker | null>(null)
  const [editing, setEditing] = useState<Editing | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [busy, setBusy] = useState(false)

  // Loads the groups; a reload increments listRequest. State changes only in the callbacks.
  useEffect(() => {
    let isCurrent = true
    listGroups().then(
      (groups) => {
        if (isCurrent) setListState({ status: 'ready', groups })
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
      const group = await createGroup({
        name: name.trim(),
        description: description.trim() || null,
      })
      setName('')
      setDescription('')
      setConfirmation(`Grupo «${group.name}» creado.`)
      reloadList()
    } catch (error) {
      const message = fieldMessage(error, 'name')
      if (message) setNameError(message)
      else setCreateError(messageOf(error))
    } finally {
      setCreating(false)
    }
  }

  async function openGroup(groupId: string) {
    setPanelError(undefined)
    setPicker(null)
    setEditing(null)
    setConfirmingDelete(false)
    try {
      setSelected(await getGroup(groupId))
    } catch (error) {
      setSelected(null)
      setPanelError(messageOf(error))
    }
  }

  /** Runs one change of the open group, then shows its result and refreshes the list counts. */
  async function changeGroup(action: () => Promise<AttributeGroup | null>) {
    setPanelError(undefined)
    setConfirmation(undefined)
    setBusy(true)
    try {
      setSelected(await action())
      reloadList()
    } catch (error) {
      setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  async function openPicker() {
    setPanelError(undefined)
    try {
      setPicker({
        attributes: await listAttributes(),
        chosen: [],
        search: '',
        shown: PICKER_PAGE_SIZE,
      })
    } catch {
      setPanelError(CONNECTION_ERROR)
    }
  }

  function toggleChosen(systemId: string, checked: boolean) {
    setPicker(
      (current) =>
        current && {
          ...current,
          chosen: checked
            ? [...current.chosen, systemId]
            : current.chosen.filter((id) => id !== systemId),
        },
    )
  }

  function addChosen(group: AttributeGroup, chosen: string[]) {
    void changeGroup(async () => {
      const result = await addAttributes(group.id, chosen)
      setPicker((current) => current && { ...current, chosen: [] })
      return result.group
    })
  }

  function removeMember(group: AttributeGroup, attributeId: string) {
    void changeGroup(async () => {
      await removeAttribute(group.id, attributeId)
      return getGroup(group.id)
    })
  }

  async function saveEdit(event: FormEvent<HTMLFormElement>, group: AttributeGroup, edit: Editing) {
    event.preventDefault()
    if (!edit.name.trim()) {
      setEditing({ ...edit, nameError: NAME_REQUIRED })
      return
    }
    setPanelError(undefined)
    setBusy(true)
    try {
      const updated = await updateGroup(group.id, {
        name: edit.name.trim(),
        description: edit.description.trim() || null,
      })
      setSelected(updated)
      setEditing(null)
      reloadList()
    } catch (error) {
      const message = fieldMessage(error, 'name')
      if (message) setEditing({ ...edit, nameError: message })
      else setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  async function removeGroup(group: AttributeGroup) {
    setPanelError(undefined)
    setBusy(true)
    try {
      await deleteGroup(group.id)
      setConfirmingDelete(false)
      setSelected(null)
      setPicker(null)
      setConfirmation(`Grupo «${group.name}» eliminado.`)
      reloadList()
    } catch (error) {
      setConfirmingDelete(false)
      setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  const memberIds = new Set(selected?.attributes.map((member) => member.systemId.toUpperCase()))
  const available =
    picker?.attributes.filter((attribute) => !memberIds.has(attribute.systemId.toUpperCase())) ?? []
  const matching = picker
    ? available.filter((attribute) => matchesFilters(attribute, picker.search, ''))
    : []

  return (
    <>
      <PageHeader
        title="Grupo de Atributos"
        description="Organiza los atributos en grupos. Un atributo puede estar en varios grupos o en ninguno."
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
          {canChange && (
            <Card title="Nuevo grupo">
              <form className={styles.form} noValidate onSubmit={handleCreate}>
                <TextField
                  id="grupo-nombre"
                  label="Nombre"
                  value={name}
                  maxLength={80}
                  error={nameError}
                  onChange={(event) => setName(event.target.value)}
                />
                <TextAreaField
                  id="grupo-descripcion"
                  label="Descripción"
                  value={description}
                  maxLength={500}
                  onChange={(event) => setDescription(event.target.value)}
                />
                {createError && <Notification variant="error">{createError}</Notification>}
                <div className={styles.actions}>
                  <Button type="submit" disabled={creating}>
                    Crear grupo
                  </Button>
                </div>
              </form>
            </Card>
          )}

          <Card title="Grupos">
            {listState.status === 'loading' && <p className={styles.muted}>Cargando grupos…</p>}
            {listState.status === 'error' && (
              <Notification variant="error">
                <p className={styles.inlineMessage}>{CONNECTION_ERROR}</p>
                <Button variant="secondary" onClick={retryList}>
                  Reintentar
                </Button>
              </Notification>
            )}
            {listState.status === 'ready' && listState.groups.length === 0 && (
              <p className={styles.muted}>Todavía no hay grupos de atributos.</p>
            )}
            {listState.status === 'ready' && listState.groups.length > 0 && (
              <ul className={styles.groupList}>
                {listState.groups.map((group) => (
                  <li key={group.id}>
                    <button
                      type="button"
                      className={`${styles.groupButton} ${selected?.id === group.id ? styles.selected : ''}`}
                      aria-pressed={selected?.id === group.id}
                      onClick={() => void openGroup(group.id)}
                    >
                      <span className={styles.groupName}>{group.name}</span>
                      {group.description && (
                        <span className={styles.groupDescription}>{group.description}</span>
                      )}
                      <span className={styles.groupCount}>{countLabel(group.attributeCount)}</span>
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
                  canChange && !editing && !confirmingDelete ? (
                    <>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() =>
                          setEditing({
                            name: selected.name,
                            description: selected.description ?? '',
                          })
                        }
                      >
                        Editar
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => {
                          setConfirmation(undefined)
                          setPanelError(undefined)
                          setConfirmingDelete(true)
                        }}
                      >
                        Eliminar grupo
                      </Button>
                    </>
                  ) : undefined
                }
              >
                {editing ? (
                  <form
                    className={styles.form}
                    noValidate
                    onSubmit={(event) => void saveEdit(event, selected, editing)}
                  >
                    <TextField
                      id="editar-nombre"
                      label="Nombre del grupo"
                      value={editing.name}
                      maxLength={80}
                      error={editing.nameError}
                      onChange={(event) => setEditing({ ...editing, name: event.target.value })}
                    />
                    <TextAreaField
                      id="editar-descripcion"
                      label="Descripción del grupo"
                      value={editing.description}
                      maxLength={500}
                      onChange={(event) =>
                        setEditing({ ...editing, description: event.target.value })
                      }
                    />
                    <div className={styles.actions}>
                      <Button variant="secondary" onClick={() => setEditing(null)}>
                        Cancelar
                      </Button>
                      <Button type="submit" disabled={busy}>
                        Guardar
                      </Button>
                    </div>
                  </form>
                ) : (
                  selected.description && <p className={styles.muted}>{selected.description}</p>
                )}

                {confirmingDelete && (
                  <GroupDeleteConfirmation
                    group={selected}
                    busy={busy}
                    onCancel={() => setConfirmingDelete(false)}
                    onConfirm={() => void removeGroup(selected)}
                  />
                )}

                <h3 className={styles.subheading}>Atributos del grupo</h3>
                {selected.attributes.length === 0 ? (
                  <p className={styles.muted}>Este grupo todavía no tiene atributos.</p>
                ) : (
                  <ul className={styles.memberList}>
                    {selected.attributes.map((member) => (
                      <li key={member.systemId} className={styles.member}>
                        <span className={styles.memberName}>{member.displayName}</span>
                        <span className={styles.muted}>
                          {DATA_TYPE_LABELS[member.dataType] ?? member.dataType}
                        </span>
                        <span className={styles.systemId}>{member.systemId}</span>
                        {canChange && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            aria-label={`Quitar ${member.displayName}`}
                            onClick={() => removeMember(selected, member.systemId)}
                          >
                            Quitar
                          </Button>
                        )}
                      </li>
                    ))}
                  </ul>
                )}

                {canChange && !picker && (
                  <div className={styles.actions}>
                    <Button variant="secondary" onClick={() => void openPicker()}>
                      Agregar atributos
                    </Button>
                  </div>
                )}
                {canChange && picker && (
                  <fieldset className={styles.picker}>
                    <legend className={styles.legend}>Agregar atributos</legend>
                    {available.length === 0 ? (
                      <p className={styles.muted}>No hay más atributos para agregar.</p>
                    ) : (
                      <>
                        <TextField
                          id="buscar-para-agregar"
                          label="Buscar atributo"
                          type="search"
                          value={picker.search}
                          onChange={(event) =>
                            setPicker({
                              ...picker,
                              search: event.target.value,
                              shown: PICKER_PAGE_SIZE,
                            })
                          }
                        />
                        {matching.length === 0 && (
                          <p className={styles.muted}>Ningún atributo coincide con la búsqueda.</p>
                        )}
                        {matching.slice(0, picker.shown).map((attribute) => (
                          <CheckboxField
                            key={attribute.systemId}
                            id={`agregar-${attribute.systemId}`}
                            label={`${attribute.displayName} (${attribute.systemId})`}
                            checked={picker.chosen.includes(attribute.systemId)}
                            onChange={(checked) => toggleChosen(attribute.systemId, checked)}
                          />
                        ))}
                        {matching.length > picker.shown && (
                          <div className={styles.actions}>
                            <p className={styles.muted}>
                              Mostrando {picker.shown} de {matching.length} atributos.
                            </p>
                            <Button
                              variant="secondary"
                              onClick={() =>
                                setPicker({ ...picker, shown: picker.shown + PICKER_PAGE_SIZE })
                              }
                            >
                              Mostrar más
                            </Button>
                          </div>
                        )}
                      </>
                    )}
                    <div className={styles.actions}>
                      <Button variant="secondary" onClick={() => setPicker(null)}>
                        Cerrar
                      </Button>
                      <Button
                        disabled={busy || picker.chosen.length === 0}
                        onClick={() => addChosen(selected, picker.chosen)}
                      >
                        Agregar seleccionados ({picker.chosen.length})
                      </Button>
                    </div>
                  </fieldset>
                )}
              </Card>
            )}
          </div>
        )}
      </div>
    </>
  )
}

type GroupDeleteConfirmationProps = {
  group: AttributeGroup
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
}

/**
 * Inline confirmation before deleting a group (feature 009 FR-015). A group with attributes cannot
 * be deleted (feature 005 FR-005), so it only explains why and offers "Cancelar".
 */
function GroupDeleteConfirmation({
  group,
  busy,
  onCancel,
  onConfirm,
}: GroupDeleteConfirmationProps) {
  const count = group.attributes.length
  return (
    <Notification variant="warning">
      <p className={styles.inlineMessage}>
        {count > 0
          ? `El grupo «${group.name}» tiene ${countLabel(count)}. Quítalos del grupo antes de eliminarlo; los atributos no se eliminan.`
          : `¿Eliminar el grupo «${group.name}»? Ningún atributo se elimina.`}
      </p>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        {count === 0 && (
          <Button disabled={busy} onClick={onConfirm}>
            Sí, eliminar grupo
          </Button>
        )}
      </div>
    </Notification>
  )
}
