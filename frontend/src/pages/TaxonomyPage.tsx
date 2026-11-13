import { useEffect, useState, type FormEvent } from 'react'
import {
  fetchAttributeTypes,
  listAttributes,
  type Attribute,
  type AttributeType,
} from '../api/attributesApi'
import {
  TaxonomyApiError,
  assignAttributes,
  canChangeTaxonomy,
  createNode,
  deleteNode,
  getNode,
  listNodes,
  removeAttribute,
  updateNode,
  type EffectiveAttribute,
  type TaxonomyNode,
  type TaxonomyNodeSummary,
} from '../api/taxonomyApi'
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
import { NodeAttributesTable } from '../taxonomy/NodeAttributesTable'
import { TaxonomyTree } from '../taxonomy/TaxonomyTree'
import { buildTree } from '../taxonomy/nodeTree'
import styles from './TaxonomyPage.module.css'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'
const NAME_REQUIRED = 'Escribe el nombre del nodo.'
/** Attributes shown at a time in "Agregar atributos" (feature 010 US2). */
const PICKER_PAGE_SIZE = 50

type ListState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; items: TaxonomyNodeSummary[] }

/** What the right column shows. */
type Panel = 'none' | 'detail' | 'createTop' | 'createChild' | 'edit'

type NodeForm = { name: string; description: string; nameError?: string }

type Picker = { attributes: Attribute[]; chosen: string[]; search: string; shown: number }

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

/** The backend's Spanish message, or the connection message when it could not be reached. */
function messageOf(error: unknown): string {
  if (error instanceof TaxonomyApiError && error.kind !== 'unavailable') {
    return error.detail ?? error.errors[0]?.message ?? CONNECTION_ERROR
  }
  return CONNECTION_ERROR
}

/** The backend's message for the name field, when the problem names it. */
function nameMessage(error: unknown): string | undefined {
  if (!(error instanceof TaxonomyApiError)) return undefined
  return error.errors.find((fieldError) => fieldError.field === 'name')?.message
}

/**
 * The product hierarchy and its attribute inheritance (feature 010): a tree of nodes and, for the
 * chosen node, which attributes apply to it, directly or inherited, and from which node. Laid out
 * like "Lista de Valores". Everything comes from the backend, which computes inheritance (FR-020).
 * Change controls appear only with the development permission `ATTRIBUTE_CREATE` (FR-017).
 */
export function TaxonomyPage() {
  const canChange = canChangeTaxonomy()
  const [listState, setListState] = useState<ListState>({ status: 'loading' })
  const [listRequest, setListRequest] = useState(0)
  const [types, setTypes] = useState<AttributeType[]>([])
  const [expanded, setExpanded] = useState<Set<string> | null>(null)
  const [confirmation, setConfirmation] = useState<string>()

  const [panel, setPanel] = useState<Panel>('none')
  const [selected, setSelected] = useState<TaxonomyNode | null>(null)
  const [form, setForm] = useState<NodeForm>({ name: '', description: '' })
  const [panelError, setPanelError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [removing, setRemoving] = useState<EffectiveAttribute | null>(null)
  const [picker, setPicker] = useState<Picker | null>(null)

  // Loads the nodes; a reload increments listRequest. The first load expands the top level.
  useEffect(() => {
    let isCurrent = true
    listNodes().then(
      (items) => {
        if (!isCurrent) return
        setListState({ status: 'ready', items })
        setExpanded(
          (current) =>
            current ?? new Set(items.filter((item) => !item.parentId).map((item) => item.systemId)),
        )
      },
      () => {
        if (isCurrent) setListState({ status: 'error' })
      },
    )
    return () => {
      isCurrent = false
    }
  }, [listRequest])

  // The data type labels for the attribute table; without them the codes are shown.
  useEffect(() => {
    let isCurrent = true
    fetchAttributeTypes().then(
      (loaded) => {
        if (isCurrent) setTypes(loaded)
      },
      () => {},
    )
    return () => {
      isCurrent = false
    }
  }, [])

  const reloadList = () => setListRequest((current) => current + 1)
  const items = listState.status === 'ready' ? listState.items : []

  function retryList() {
    setListState({ status: 'loading' })
    reloadList()
  }

  function expand(ids: string[]) {
    setExpanded((current) => new Set([...(current ?? []), ...ids]))
  }

  function toggle(nodeId: string) {
    setExpanded((current) => {
      const next = new Set(current ?? [])
      if (next.has(nodeId)) next.delete(nodeId)
      else next.add(nodeId)
      return next
    })
  }

  function resetPanelState() {
    setPanelError(undefined)
    setConfirmingDelete(false)
    setRemoving(null)
    setPicker(null)
  }

  /** Shows a node in the detail and expands its ancestors in the tree. */
  function show(node: TaxonomyNode) {
    setSelected(node)
    setPanel('detail')
    expand(node.path.map((ancestor) => ancestor.systemId))
  }

  async function openNode(nodeId: string) {
    resetPanelState()
    setBusy(true)
    try {
      show(await getNode(nodeId))
    } catch (error) {
      setSelected(null)
      setPanel('none')
      setPanelError(messageOf(error))
      reloadList()
    } finally {
      setBusy(false)
    }
  }

  function startForm(next: 'createTop' | 'createChild' | 'edit') {
    resetPanelState()
    setConfirmation(undefined)
    setForm(
      next === 'edit' && selected
        ? { name: selected.name, description: selected.description ?? '' }
        : { name: '', description: '' },
    )
    setPanel(next)
  }

  function cancelForm() {
    setPanelError(undefined)
    setPanel(selected ? 'detail' : 'none')
  }

  async function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form.name.trim()) {
      setForm({ ...form, nameError: NAME_REQUIRED })
      return
    }
    setPanelError(undefined)
    setBusy(true)
    const values = { name: form.name.trim(), description: form.description.trim() || null }
    try {
      if (panel === 'edit' && selected) {
        const updated = await updateNode(selected.systemId, values)
        show(updated)
        setConfirmation(`Nodo «${updated.name}» actualizado.`)
      } else {
        const parent = panel === 'createChild' && selected ? selected : null
        const created = await createNode({ ...values, parentId: parent?.systemId ?? null })
        if (parent) expand([parent.systemId])
        show(created)
        setConfirmation(`Nodo «${created.name}» creado con el ID ${created.systemId}.`)
      }
      reloadList()
    } catch (error) {
      const message = nameMessage(error)
      if (message) setForm({ ...form, nameError: message })
      else setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  async function removeNode(node: TaxonomyNode) {
    setPanelError(undefined)
    setBusy(true)
    try {
      await deleteNode(node.systemId)
      setSelected(null)
      setPanel('none')
      setConfirmingDelete(false)
      setConfirmation(`Nodo «${node.name}» eliminado.`)
      reloadList()
    } catch (error) {
      setConfirmingDelete(false)
      setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  async function confirmRemoval(node: TaxonomyNode, attribute: EffectiveAttribute) {
    setPanelError(undefined)
    setConfirmation(undefined)
    setBusy(true)
    try {
      await removeAttribute(node.systemId, attribute.systemId)
      setSelected(await getNode(node.systemId))
      setConfirmation(`Atributo «${attribute.displayName}» quitado de «${node.name}».`)
      reloadList()
    } catch (error) {
      setPanelError(messageOf(error))
    } finally {
      setRemoving(null)
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

  async function assignChosen(node: TaxonomyNode, chosen: string[]) {
    setPanelError(undefined)
    setConfirmation(undefined)
    setBusy(true)
    try {
      const result = await assignAttributes(node.systemId, chosen)
      setSelected(result.node)
      setPicker(null)
      const repeats = result.alreadyAssigned.length
      setConfirmation(
        `${plural(result.added.length, 'atributo asignado', 'atributos asignados')} a «${node.name}».` +
          (repeats > 0
            ? ` ${repeats} ${repeats === 1 ? 'ya estaba asignado' : 'ya estaban asignados'}.`
            : ''),
      )
      reloadList()
    } catch (error) {
      setPanelError(messageOf(error))
    } finally {
      setBusy(false)
    }
  }

  const typeLabel = (code: string) => types.find((type) => type.code === code)?.label ?? code
  const roots = buildTree(items)
  const expandedIds = expanded ?? new Set<string>()

  return (
    <>
      <PageHeader
        title="Taxonomía"
        description="Organiza la jerarquía de productos y asigna atributos que heredan todos los niveles inferiores."
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
          <Card
            title="Jerarquía de productos"
            actions={
              canChange ? (
                <Button disabled={panel === 'createTop'} onClick={() => startForm('createTop')}>
                  Nuevo nodo de primer nivel
                </Button>
              ) : undefined
            }
          >
            {listState.status === 'loading' && (
              <p className={styles.muted}>Cargando la jerarquía…</p>
            )}
            {listState.status === 'error' && (
              <Notification variant="error">
                <p className={styles.inlineMessage}>{CONNECTION_ERROR}</p>
                <Button variant="secondary" onClick={retryList}>
                  Reintentar
                </Button>
              </Notification>
            )}
            {listState.status === 'ready' && items.length === 0 && (
              <>
                <p className={styles.muted}>Todavía no hay nodos en la jerarquía.</p>
                {canChange && (
                  <p className={styles.muted}>
                    Crea el primer nivel con «Nuevo nodo de primer nivel».
                  </p>
                )}
              </>
            )}
            {listState.status === 'ready' && items.length > 0 && (
              <TaxonomyTree
                roots={roots}
                selectedId={selected?.systemId}
                expanded={expandedIds}
                onToggle={toggle}
                onSelect={(nodeId) => void openNode(nodeId)}
              />
            )}
          </Card>
        </div>

        {(panel !== 'none' || panelError) && (
          <div className={styles.column}>
            {panelError && <Notification variant="error">{panelError}</Notification>}

            {(panel === 'createTop' || panel === 'createChild' || panel === 'edit') && (
              <Card
                title={
                  panel === 'edit'
                    ? 'Editar nodo'
                    : panel === 'createChild' && selected
                      ? `Nuevo nodo hijo de «${selected.name}»`
                      : 'Nuevo nodo de primer nivel'
                }
              >
                <form
                  className={styles.form}
                  noValidate
                  onSubmit={(event) => void submitForm(event)}
                >
                  <TextField
                    id="nodo-nombre"
                    label="Nombre"
                    value={form.name}
                    maxLength={80}
                    error={form.nameError}
                    onChange={(event) =>
                      setForm({ ...form, name: event.target.value, nameError: undefined })
                    }
                  />
                  <TextAreaField
                    id="nodo-descripcion"
                    label="Descripción"
                    value={form.description}
                    maxLength={500}
                    onChange={(event) => setForm({ ...form, description: event.target.value })}
                  />
                  <div className={styles.actions}>
                    <Button variant="secondary" onClick={cancelForm}>
                      Cancelar
                    </Button>
                    <Button type="submit" disabled={busy}>
                      {panel === 'edit' ? 'Guardar cambios' : 'Crear nodo'}
                    </Button>
                  </div>
                </form>
              </Card>
            )}

            {panel === 'detail' && selected && (
              <Card
                title={selected.name}
                actions={
                  canChange && !confirmingDelete ? (
                    <>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => startForm('createChild')}
                      >
                        Agregar nodo hijo
                      </Button>
                      <Button variant="secondary" disabled={busy} onClick={() => startForm('edit')}>
                        Editar
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => {
                          setConfirmation(undefined)
                          setConfirmingDelete(true)
                        }}
                      >
                        Eliminar
                      </Button>
                    </>
                  ) : undefined
                }
              >
                <p className={styles.path}>
                  {selected.path.length === 0
                    ? 'Primer nivel'
                    : selected.path.map((ancestor) => (
                        <span key={ancestor.systemId}>
                          <button
                            type="button"
                            className={styles.link}
                            onClick={() => void openNode(ancestor.systemId)}
                          >
                            {ancestor.name}
                          </button>
                          {' › '}
                        </span>
                      ))}
                  {selected.path.length > 0 && <span>{selected.name}</span>}
                </p>
                <p className={styles.systemId}>
                  {selected.systemId} · Nivel {selected.level}
                </p>
                <p className={styles.muted}>{selected.description || 'Sin descripción'}</p>

                {confirmingDelete && (
                  <DeleteConfirmation
                    node={selected}
                    busy={busy}
                    onCancel={() => setConfirmingDelete(false)}
                    onConfirm={() => void removeNode(selected)}
                  />
                )}

                <h3 className={styles.subheading}>Nodos hijos</h3>
                {selected.children.length === 0 ? (
                  <p className={styles.muted}>Este nodo no tiene nodos hijos.</p>
                ) : (
                  <ul className={styles.children}>
                    {selected.children.map((child) => (
                      <li key={child.systemId}>
                        <button
                          type="button"
                          className={styles.link}
                          onClick={() => void openNode(child.systemId)}
                        >
                          {child.name}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}

                <NodeAttributesTable
                  node={selected}
                  typeLabel={typeLabel}
                  canChange={canChange}
                  busy={busy}
                  onOpenNode={(nodeId) => void openNode(nodeId)}
                  onRemove={(attribute) => {
                    setConfirmation(undefined)
                    setRemoving(attribute)
                  }}
                />

                {removing && (
                  <Notification variant="warning">
                    <p className={styles.inlineMessage}>
                      ¿Quitar «{removing.displayName}» de «{selected.name}»? Los niveles inferiores
                      dejarán de heredarlo de este nodo. El atributo no se elimina.
                    </p>
                    <div className={styles.actions}>
                      <Button variant="secondary" disabled={busy} onClick={() => setRemoving(null)}>
                        Cancelar
                      </Button>
                      <Button
                        disabled={busy}
                        onClick={() => void confirmRemoval(selected, removing)}
                      >
                        Sí, quitar
                      </Button>
                    </div>
                  </Notification>
                )}

                {canChange && !picker && (
                  <div className={styles.actions}>
                    <Button variant="secondary" onClick={() => void openPicker()}>
                      Agregar atributos
                    </Button>
                  </div>
                )}
                {canChange && picker && (
                  <AttributePicker
                    node={selected}
                    picker={picker}
                    busy={busy}
                    onChange={setPicker}
                    onToggle={toggleChosen}
                    onClose={() => setPicker(null)}
                    onAssign={() => void assignChosen(selected, picker.chosen)}
                  />
                )}
              </Card>
            )}
          </div>
        )}
      </div>
    </>
  )
}

type DeleteConfirmationProps = {
  node: TaxonomyNode
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
}

/**
 * Inline confirmation before deleting a node (feature 010 FR-006). A node with children or direct
 * attributes cannot be deleted, so it only explains why and offers "Cancelar".
 */
function DeleteConfirmation({ node, busy, onCancel, onConfirm }: DeleteConfirmationProps) {
  const children = node.children.length
  const attributes = node.directAttributeCount
  const blocked = children > 0 || attributes > 0
  let message = `¿Eliminar el nodo «${node.name}» (${node.systemId})? Ningún atributo se elimina.`
  if (children > 0) {
    message = `El nodo «${node.name}» tiene ${plural(children, 'nodo hijo', 'nodos hijos')}. Elimínalos antes de eliminar este nodo.`
  } else if (attributes > 0) {
    message = `El nodo «${node.name}» tiene ${plural(attributes, 'atributo asignado', 'atributos asignados')}. Quítalos antes de eliminar el nodo.`
  }
  return (
    <Notification variant="warning">
      <p className={styles.inlineMessage}>{message}</p>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        {!blocked && (
          <Button disabled={busy} onClick={onConfirm}>
            Sí, eliminar nodo
          </Button>
        )}
      </div>
    </Notification>
  )
}

type AttributePickerProps = {
  node: TaxonomyNode
  picker: Picker
  busy: boolean
  onChange: (picker: Picker) => void
  onToggle: (systemId: string, checked: boolean) => void
  onClose: () => void
  onAssign: () => void
}

/**
 * Chooses existing attributes to assign directly (feature 010 US2): searchable, 50 at a time,
 * without the node's direct attributes; inherited ones say where they come from.
 */
function AttributePicker({
  node,
  picker,
  busy,
  onChange,
  onToggle,
  onClose,
  onAssign,
}: AttributePickerProps) {
  const effective = new Map(
    node.effectiveAttributes.map((attribute) => [attribute.systemId.toUpperCase(), attribute]),
  )
  const available = picker.attributes.filter(
    (attribute) => effective.get(attribute.systemId.toUpperCase())?.assignment !== 'DIRECT',
  )
  const matching = available.filter((attribute) => matchesFilters(attribute, picker.search, ''))

  function label(attribute: Attribute): string {
    const inherited = effective.get(attribute.systemId.toUpperCase())
    const base = `${attribute.displayName} (${attribute.systemId})`
    return inherited ? `${base} — heredado de «${inherited.origin.name}»` : base
  }

  return (
    <fieldset className={styles.picker}>
      <legend className={styles.legend}>Agregar atributos a «{node.name}»</legend>
      {available.length === 0 ? (
        <p className={styles.muted}>No hay más atributos para asignar.</p>
      ) : (
        <>
          <TextField
            id="buscar-atributo-nodo"
            label="Buscar atributo"
            type="search"
            value={picker.search}
            onChange={(event) =>
              onChange({ ...picker, search: event.target.value, shown: PICKER_PAGE_SIZE })
            }
          />
          {matching.length === 0 && (
            <p className={styles.muted}>Ningún atributo coincide con la búsqueda.</p>
          )}
          {matching.slice(0, picker.shown).map((attribute) => (
            <CheckboxField
              key={attribute.systemId}
              id={`asignar-${attribute.systemId}`}
              label={label(attribute)}
              checked={picker.chosen.includes(attribute.systemId)}
              onChange={(checked) => onToggle(attribute.systemId, checked)}
            />
          ))}
          {matching.length > picker.shown && (
            <div className={styles.actions}>
              <p className={styles.muted}>
                Mostrando {picker.shown} de {matching.length} atributos.
              </p>
              <Button
                variant="secondary"
                onClick={() => onChange({ ...picker, shown: picker.shown + PICKER_PAGE_SIZE })}
              >
                Mostrar más
              </Button>
            </div>
          )}
        </>
      )}
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onClose}>
          Cerrar
        </Button>
        <Button disabled={busy || picker.chosen.length === 0} onClick={onAssign}>
          Asignar seleccionados ({picker.chosen.length})
        </Button>
      </div>
    </fieldset>
  )
}
