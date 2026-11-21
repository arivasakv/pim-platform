import { useEffect, useState, type ReactNode } from 'react'
import {
  AttributeApiError,
  canChangeAttributes,
  deleteAttribute,
  fetchAttributeTypes,
  getAttribute,
  listAttributes,
  type Attribute,
  type AttributeType,
} from '../api/attributesApi'
import { canChangeGroups, listGroups } from '../api/attributeGroupsApi'
import { AttributeEditor } from '../attributes/AttributeEditor'
import { AttributeGroupPanel } from '../attributes/AttributeGroupPanel'
import { AttributeGroupsSection } from '../attributes/AttributeGroupsSection'
import {
  ALL,
  ALL_TYPES,
  browse,
  countLabel,
  effectiveEntry,
  entryLabel,
  ungroupedCount,
  type BrowseEntry,
  type GroupsState,
} from '../attributes/attributeBrowsing'
import { AttributeFieldPreview } from '../attributes/AttributeFieldPreview'
import { fromAttribute } from '../attributes/attributeForm'
import { attributeTypeLabel, typeChoices } from '../attributes/dataTypeChoices'
import { Button, Card, Notification, PageHeader, SelectField, TextField } from '../components/ui'
import styles from './AttributesPage.module.css'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'
/** Rows shown at a time; "Mostrar más" adds this many (feature 007 research R-10). */
const PAGE_SIZE = 50

type ListState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; attributes: Attribute[]; types: AttributeType[] }

/** What the right column shows. */
type Panel = 'none' | 'detail' | 'create' | 'edit'

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

/** "1 coincidencia" / "N coincidencias en otros grupos." (feature 009 FR-006). */
function elsewhereLabel(count: number): string {
  return `${plural(count, 'coincidencia', 'coincidencias')} en otros grupos.`
}

function messageOf(error: unknown): string {
  if (error instanceof AttributeApiError && error.kind !== 'unavailable') {
    return error.detail ?? error.errors[0]?.message ?? CONNECTION_ERROR
  }
  return CONNECTION_ERROR
}

function formatDate(iso: string | undefined): string | undefined {
  if (!iso) return undefined
  return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })
}

/**
 * Consults, searches, creates, edits and deletes attributes (feature 007; feature 004 for the
 * form), laid out like "Lista de Valores" (feature 006), browsed by attribute group (feature 009).
 * Everything shown comes from the backend (FR-003). Change controls appear only with the
 * development permission `ATTRIBUTE_CREATE` (FR-018); the backend checks it again.
 */
export function AttributesPage() {
  const canChange = canChangeAttributes()
  const canChangeMemberships = canChangeGroups()
  const [listState, setListState] = useState<ListState>({ status: 'loading' })
  const [groupsState, setGroupsState] = useState<GroupsState>({ status: 'loading' })
  const [entry, setEntry] = useState<BrowseEntry>(ALL)
  const [listRequest, setListRequest] = useState(0)
  const [confirmation, setConfirmation] = useState<string>()
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState(ALL_TYPES)
  const [shown, setShown] = useState(PAGE_SIZE)

  const [panel, setPanel] = useState<Panel>('none')
  const [selected, setSelected] = useState<Attribute | null>(null)
  const [panelError, setPanelError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  // Loads the attributes, the data types and, independently, the groups (feature 009 FR-008); a
  // reload increments listRequest. State changes only in the callbacks.
  useEffect(() => {
    let isCurrent = true
    Promise.all([listAttributes(), fetchAttributeTypes()]).then(
      ([attributes, types]) => {
        if (isCurrent) setListState({ status: 'ready', attributes, types })
      },
      () => {
        if (isCurrent) setListState({ status: 'error' })
      },
    )
    listGroups().then(
      (groups) => {
        if (isCurrent) setGroupsState({ status: 'ready', groups })
      },
      () => {
        if (isCurrent) setGroupsState({ status: 'error' })
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

  function retryGroups() {
    setGroupsState({ status: 'loading' })
    reloadList()
  }

  function chooseEntry(chosen: BrowseEntry) {
    setEntry(chosen)
    setShown(PAGE_SIZE)
  }

  function handleMembershipChanged(attribute: Attribute, message?: string) {
    setSelected(attribute)
    if (message) {
      setPanelError(undefined)
      setConfirmation(message)
    }
    reloadList()
  }

  function handleMembershipError(message: string) {
    setConfirmation(undefined)
    setPanelError(message)
  }

  function closePanel() {
    setPanel('none')
    setSelected(null)
    setPanelError(undefined)
    setConfirmingDelete(false)
  }

  async function openAttribute(systemId: string) {
    setPanelError(undefined)
    setConfirmingDelete(false)
    setBusy(true)
    try {
      setSelected(await getAttribute(systemId))
      setPanel('detail')
    } catch (error) {
      setSelected(null)
      setPanel('none')
      setPanelError(messageOf(error))
      reloadList()
    } finally {
      setBusy(false)
    }
  }

  function startCreating() {
    setConfirmation(undefined)
    setPanelError(undefined)
    setConfirmingDelete(false)
    setSelected(null)
    setPanel('create')
  }

  function handleCreated(attribute: Attribute) {
    setSelected(attribute)
    setPanel('detail')
    setConfirmation(`Atributo «${attribute.displayName}» creado con el ID ${attribute.systemId}.`)
    reloadList()
  }

  function handleUpdated(attribute: Attribute) {
    setSelected(attribute)
    setPanel('detail')
    setConfirmation(`Atributo «${attribute.displayName}» actualizado.`)
    reloadList()
  }

  async function removeAttribute(attribute: Attribute) {
    setPanelError(undefined)
    setBusy(true)
    try {
      await deleteAttribute(attribute.systemId)
      closePanel()
      setConfirmation(`Atributo «${attribute.displayName}» eliminado.`)
      reloadList()
    } catch (error) {
      setConfirmingDelete(false)
      setPanelError(messageOf(error))
      // A group may have been added meanwhile, or the attribute deleted: show the current state.
      if (error instanceof AttributeApiError && error.kind === 'conflict') {
        try {
          setSelected(await getAttribute(attribute.systemId))
        } catch {
          // Keep the shown attribute; the message already explains the refusal.
        }
      }
      if (error instanceof AttributeApiError && error.kind === 'notFound') {
        setSelected(null)
        setPanel('none')
        reloadList()
      }
    } finally {
      setBusy(false)
    }
  }

  const types = listState.status === 'ready' ? listState.types : []
  const attributes = listState.status === 'ready' ? listState.attributes : []
  const shownEntry = effectiveEntry(entry, groupsState)
  const shownLabel = entryLabel(shownEntry, groupsState, attributes)
  const { visible, elsewhere } = browse(attributes, shownEntry, search, typeFilter)
  const filtering = search.trim() !== '' || typeFilter !== ALL_TYPES

  return (
    <>
      <PageHeader
        title="Atributos"
        description="Consulta, crea, edita y elimina las características que pueden tener los productos."
      />

      {confirmation && (
        <div className={styles.messages}>
          <Notification variant="success" onDismiss={() => setConfirmation(undefined)}>
            {confirmation}
          </Notification>
        </div>
      )}

      <div className={styles.layout}>
        <div className={styles.groupsArea}>
          <AttributeGroupPanel
            groupsState={groupsState}
            totalCount={attributes.length}
            ungroupedCount={ungroupedCount(attributes)}
            entry={shownEntry}
            entryLabel={shownLabel}
            onSelect={chooseEntry}
            onRetry={retryGroups}
          />
        </div>

        <div className={styles.column}>
          <Card
            title={shownLabel}
            actions={
              canChange ? (
                <Button disabled={panel === 'create'} onClick={startCreating}>
                  Nuevo atributo
                </Button>
              ) : undefined
            }
          >
            {listState.status === 'loading' && <p className={styles.muted}>Cargando atributos…</p>}
            {listState.status === 'error' && (
              <Notification variant="error">
                <p className={styles.inlineMessage}>{CONNECTION_ERROR}</p>
                <Button variant="secondary" onClick={retryList}>
                  Reintentar
                </Button>
              </Notification>
            )}
            {listState.status === 'ready' && listState.attributes.length === 0 && (
              <p className={styles.muted}>Todavía no hay atributos.</p>
            )}
            {listState.status === 'ready' && listState.attributes.length > 0 && (
              <div className={styles.form}>
                <TextField
                  id="buscar-atributo"
                  label="Buscar por nombre o ID"
                  type="search"
                  value={search}
                  onChange={(event) => {
                    setSearch(event.target.value)
                    setShown(PAGE_SIZE)
                  }}
                />
                <SelectField
                  id="filtrar-tipo"
                  label="Tipo de dato"
                  placeholder="Todos"
                  options={typeChoices(types)}
                  value={typeFilter}
                  onChange={(code) => {
                    setTypeFilter(code)
                    setShown(PAGE_SIZE)
                  }}
                />
                <p className={styles.counts} aria-live="polite">
                  {countLabel(visible.length)}
                </p>
                {visible.length === 0 ? (
                  <NoMatch
                    entry={shownEntry}
                    label={shownLabel}
                    filtering={filtering}
                    elsewhere={elsewhere}
                    onSearchAll={() => chooseEntry(ALL)}
                  />
                ) : (
                  <ul className={styles.attributeList}>
                    {visible.slice(0, shown).map((attribute) => (
                      <li key={attribute.systemId}>
                        <button
                          type="button"
                          className={`${styles.listButton} ${selected?.systemId === attribute.systemId ? styles.selected : ''}`}
                          aria-pressed={selected?.systemId === attribute.systemId}
                          onClick={() => void openAttribute(attribute.systemId)}
                        >
                          <span className={styles.listName}>{attribute.displayName}</span>
                          <span className={styles.systemId}>
                            {attribute.systemId} · {attributeTypeLabel(attribute, types)}
                          </span>
                          <span className={styles.counts}>
                            {attribute.groups && attribute.groups.length > 0
                              ? `Grupos: ${attribute.groups.map((group) => group.name).join(', ')}`
                              : 'Sin grupos'}
                            {attribute.listOfValuesProperties?.listOfValuesName &&
                              ` · Lista: ${attribute.listOfValuesProperties.listOfValuesName}`}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                {visible.length > shown && (
                  <div className={styles.actions}>
                    <p className={styles.muted}>
                      Mostrando {shown} de {visible.length} atributos.
                    </p>
                    <Button variant="secondary" onClick={() => setShown(shown + PAGE_SIZE)}>
                      Mostrar más
                    </Button>
                  </div>
                )}
              </div>
            )}
          </Card>
        </div>

        {(panel !== 'none' || panelError) && (
          <div className={styles.column}>
            {panelError && <Notification variant="error">{panelError}</Notification>}

            {panel === 'create' && (
              <AttributeEditor key="nuevo" onSaved={handleCreated} onCancel={closePanel} />
            )}

            {panel === 'edit' && selected && (
              <AttributeEditor
                key={`editar-${selected.systemId}`}
                mode="edit"
                attribute={selected}
                onSaved={handleUpdated}
                onCancel={() => setPanel('detail')}
              />
            )}

            {panel === 'detail' && selected && (
              <Card
                title={selected.displayName}
                actions={
                  canChange && !confirmingDelete ? (
                    <>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => {
                          setConfirmation(undefined)
                          setPanelError(undefined)
                          setPanel('edit')
                        }}
                      >
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
                <p className={styles.systemId}>{selected.systemId}</p>

                {confirmingDelete && (
                  <DeleteConfirmation
                    attribute={selected}
                    busy={busy}
                    onCancel={() => setConfirmingDelete(false)}
                    onConfirm={() => void removeAttribute(selected)}
                  />
                )}

                <AttributeDetail attribute={selected} types={types} />

                <AttributeGroupsSection
                  key={selected.systemId}
                  attribute={selected}
                  groupsState={groupsState}
                  canChange={canChangeMemberships}
                  onChanged={handleMembershipChanged}
                  onError={handleMembershipError}
                />

                {/* The same preview as in the form, from the saved configuration (feature 008 FR-016). */}
                <div className={styles.preview}>
                  <AttributeFieldPreview
                    form={fromAttribute(selected)}
                    options={types.find((type) => type.code === selected.dataType)?.options ?? {}}
                  />
                </div>
              </Card>
            )}
          </div>
        )}
      </div>
    </>
  )
}

type NoMatchProps = {
  entry: BrowseEntry
  label: string
  /** Whether a search or type filter is active. */
  filtering: boolean
  elsewhere: number
  onSearchAll: () => void
}

/** What the list says when the chosen entry shows nothing (feature 009 FR-006, FR-008). */
function NoMatch({ entry, label, filtering, elsewhere, onSearchAll }: NoMatchProps) {
  if (entry.kind === 'all') {
    return <p className={styles.muted}>Ningún atributo coincide con la búsqueda.</p>
  }
  if (!filtering && entry.kind === 'group') {
    return (
      <>
        <p className={styles.muted}>Este grupo todavía no tiene atributos.</p>
        <p className={styles.muted}>
          Agrégalos desde la sección «Grupos» de cada atributo o en «Grupo de Atributos».
        </p>
      </>
    )
  }
  if (!filtering) {
    return <p className={styles.muted}>Todos los atributos pertenecen al menos a un grupo.</p>
  }
  return (
    <>
      <p className={styles.muted}>
        {entry.kind === 'group'
          ? `Ningún atributo de «${label}» coincide con la búsqueda.`
          : 'Ningún atributo sin grupo coincide con la búsqueda.'}
      </p>
      {elsewhere > 0 && (
        <div className={styles.actions}>
          <p className={styles.muted}>{elsewhereLabel(elsewhere)}</p>
          <Button variant="secondary" onClick={onSearchAll}>
            Buscar en todos los atributos
          </Button>
        </div>
      )}
    </>
  )
}

type DeleteConfirmationProps = {
  attribute: Attribute
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
}

/**
 * Inline confirmation, as in "Lista de Valores" (feature 007 FR-012). An attribute in groups
 * cannot be deleted until it is removed from them (FR-014), so only "Cancelar" is offered; the
 * "Grupos" section of the detail removes it (feature 009 FR-013).
 */
function DeleteConfirmation({ attribute, busy, onCancel, onConfirm }: DeleteConfirmationProps) {
  const groups = attribute.groups ?? []
  const listName = attribute.listOfValuesProperties?.listOfValuesName
  if (groups.length > 0) {
    return (
      <Notification variant="warning">
        <p className={styles.inlineMessage}>
          El atributo «{attribute.displayName}» pertenece a{' '}
          {plural(groups.length, 'grupo', 'grupos')}: {groups.map((group) => group.name).join(', ')}
          . Quítalo de esos grupos en la sección «Grupos» de este atributo antes de eliminarlo.
        </p>
        <div className={styles.actions}>
          <Button variant="secondary" onClick={onCancel}>
            Cancelar
          </Button>
        </div>
      </Notification>
    )
  }
  return (
    <Notification variant="warning">
      <p className={styles.inlineMessage}>
        ¿Eliminar el atributo «{attribute.displayName}» ({attribute.systemId})?
        {listName && ` La lista de valores «${listName}» se conserva.`}
      </p>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button disabled={busy} onClick={onConfirm}>
          Sí, eliminar atributo
        </Button>
      </div>
    </Notification>
  )
}

type AttributeDetailProps = { attribute: Attribute; types: AttributeType[] }

/** The complete configuration of an attribute (feature 007 FR-006), with Spanish labels. */
function AttributeDetail({ attribute, types }: AttributeDetailProps) {
  const type = types.find((candidate) => candidate.code === attribute.dataType)
  const option = (property: string, code: string | undefined) =>
    code === undefined
      ? undefined
      : (type?.options[property]?.find((candidate) => candidate.code === code)?.label ?? code)
  const rows: Array<[string, ReactNode]> = [
    ['Tipo de dato', attributeTypeLabel(attribute, types)],
    ['Alcance', 'Global'],
  ]
  const add = (label: string, value: ReactNode | undefined) => {
    if (value !== undefined && value !== null && value !== '') rows.push([label, value])
  }

  const text = attribute.textProperties
  if (text) {
    add('Longitud mínima', text.minLength)
    add('Longitud máxima', text.maxLength)
    add('Formato de mayúsculas', option('caseFormat', text.caseFormat))
    add('Máscara de captura', text.captureMask)
    add('Texto enriquecido (HTML)', text.richText ? 'Sí' : 'No')
  }
  const numeric = attribute.numericProperties
  if (numeric) {
    add('Decimales', numeric.precision)
    add('Valor mínimo', numeric.minimum)
    add('Valor máximo', numeric.maximum)
  }
  const list = attribute.listOfValuesProperties
  if (list) {
    add('Tipo de selección', option('selectionMode', list.selectionMode))
    add(
      'Lista de valores',
      list.listOfValuesName
        ? `${list.listOfValuesName} (${list.listOfValuesId})`
        : list.listOfValuesId,
    )
  }
  if (attribute.dateTimeProperties) {
    add('Formato de fecha', option('dateFormat', attribute.dateTimeProperties.dateFormat))
  }
  const currency = attribute.currencyProperties
  if (currency) {
    add(
      'Moneda',
      currency.currencySymbol
        ? `${currency.currencyCode} (${currency.currencySymbol})`
        : currency.currencyCode,
    )
    add('Decimales', currency.decimals)
  }
  const reference = attribute.referenceProperties
  if (reference) {
    add('Objeto referenciado', option('targetObjectType', reference.targetObjectType))
    add('Cantidad', option('cardinality', reference.cardinality))
  }
  add('Creado', formatDate(attribute.createdAt))
  add('Modificado', formatDate(attribute.updatedAt))

  return (
    <dl className={styles.details}>
      {rows.map(([label, value]) => (
        <div key={label} className={styles.detailRow}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}
