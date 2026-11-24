import { useEffect, useState, type FormEvent } from 'react'
import {
  AttributeApiError,
  createAttribute,
  fetchAttributeTypes,
  reviewListUnlink,
  unlinkList,
  updateAttribute,
  type Attribute,
  type AttributeCreationRequest,
  type ListUnlinkReview,
  type AttributeOption,
  type AttributeType,
  type DataType,
} from '../api/attributesApi'
import { currencyChoices } from './currencyOptions'
import { choiceOf, fromChoice, numericChoices, typeChoices } from './dataTypeChoices'
import { listLists, type ListOfValuesSummary } from '../api/listsOfValuesApi'
import { Button, Card, CheckboxField, Notification, SelectField, TextField } from '../components/ui'
import {
  emptyForm,
  fromAttribute,
  toRequest,
  validateForm,
  type AttributeForm,
  type FieldErrors,
} from './attributeForm'
import { AttributeFieldPreview } from './AttributeFieldPreview'
import styles from './AttributeEditor.module.css'
import { UnlinkReview } from './UnlinkReview'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

type TypesState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; types: AttributeType[] }

type SaveState =
  | { status: 'idle' }
  | { status: 'saving' }
  | { status: 'created'; systemId: string }
  | { status: 'failed'; message: string }
  | { status: 'outdated'; message: string }

/** The unlink review (feature 014 FR-009); `null` outside unlink mode. */
type ReviewState =
  | null
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; review: ListUnlinkReview }

/** The lists a list-of-values attribute can use (feature 006 FR-026); `null` until requested. */
type ListsState = null | { status: 'error' } | { status: 'ready'; lists: ListOfValuesSummary[] }

type AttributeEditorProps = {
  /** `create` (feature 004) or `edit` an existing attribute (feature 007 FR-007). */
  mode?: 'create' | 'edit'
  /** The attribute to edit; required in `edit` mode. */
  attribute?: Attribute
  /** Called with the attribute as the backend saved it. */
  onSaved?: (attribute: Attribute) => void
  /** Shows a "Cancelar" button that calls it. */
  onCancel?: () => void
  /** Opens a list-of-values attribute directly in unlink mode (feature 014 FR-006). */
  startUnlinking?: boolean
  /** Called with the attribute as stored after its list was unlinked (feature 014). */
  onUnlinked?: (attribute: Attribute) => void
}

/** The request without the fields an unlink does not read (feature 014 contract). */
function propertiesOf(request: AttributeCreationRequest) {
  return Object.fromEntries(
    Object.entries(request).filter(([key]) => key !== 'displayName' && key !== 'systemId'),
  ) as Omit<AttributeCreationRequest, 'displayName' | 'systemId'>
}

/**
 * The attribute form: creates one attribute definition at a time (feature 004) or edits one
 * (feature 007). The data types and their options come from the backend (FR-022); the backend
 * checks every rule and assigns the system ID. When editing, the system ID and the data type are
 * shown but cannot be changed (feature 007 FR-010).
 */
export function AttributeEditor({
  mode = 'create',
  attribute,
  onSaved,
  onCancel,
  startUnlinking = false,
  onUnlinked,
}: AttributeEditorProps) {
  const editTarget = mode === 'edit' ? attribute : undefined
  const editing = editTarget !== undefined
  /** Only a list-of-values attribute being edited can be unlinked (feature 014 FR-012). */
  const canUnlink = editTarget?.dataType === 'LIST_OF_VALUES'
  const [unlinking, setUnlinking] = useState(canUnlink && startUnlinking)
  const [reviewState, setReviewState] = useState<ReviewState>(
    canUnlink && startUnlinking ? { status: 'loading' } : null,
  )
  const [reviewRequest, setReviewRequest] = useState(0)
  const [typesState, setTypesState] = useState<TypesState>({ status: 'loading' })
  const [form, setForm] = useState<AttributeForm>(() =>
    editTarget ? unlinkStart(fromAttribute(editTarget), canUnlink && startUnlinking) : emptyForm(),
  )
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saveState, setSaveState] = useState<SaveState>({ status: 'idle' })

  const [typesRequest, setTypesRequest] = useState(0)
  const [listsState, setListsState] = useState<ListsState>(null)
  const wantsLists = form.dataType === 'LIST_OF_VALUES'

  // Loads the existing lists the first time "Lista de valores" is chosen (feature 006 FR-026).
  useEffect(() => {
    if (!wantsLists || listsState !== null) return
    let isCurrent = true
    listLists().then(
      (lists) => {
        if (isCurrent) setListsState({ status: 'ready', lists })
      },
      () => {
        if (isCurrent) setListsState({ status: 'error' })
      },
    )
    return () => {
      isCurrent = false
    }
  }, [wantsLists, listsState])

  // Loads the data types; a retry increments typesRequest. State changes only in the callbacks.
  useEffect(() => {
    let isCurrent = true
    fetchAttributeTypes().then(
      (types) => {
        if (isCurrent) setTypesState({ status: 'ready', types })
      },
      () => {
        if (isCurrent) setTypesState({ status: 'error' })
      },
    )
    return () => {
      isCurrent = false
    }
  }, [typesRequest])

  // Loads the unlink review whenever unlink mode starts or the review is asked again (feature 014).
  useEffect(() => {
    if (!unlinking || !editTarget) return
    let isCurrent = true
    reviewListUnlink(editTarget.systemId).then(
      (review) => {
        if (isCurrent) setReviewState({ status: 'ready', review })
      },
      (error: unknown) => {
        if (isCurrent) setReviewState({ status: 'error', message: messageOf(error) })
      },
    )
    return () => {
      isCurrent = false
    }
  }, [unlinking, reviewRequest, editTarget])

  function startUnlink() {
    setErrors({})
    setSaveState({ status: 'idle' })
    setForm((current) => unlinkStart(current, true))
    setReviewState({ status: 'loading' })
    setUnlinking(true)
  }

  function cancelUnlink() {
    setErrors({})
    setSaveState({ status: 'idle' })
    if (editTarget) setForm(fromAttribute(editTarget))
    setReviewState(null)
    setUnlinking(false)
  }

  async function handleUnlink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editTarget || reviewState?.status !== 'ready') return
    const formErrors = validateForm(form)
    setErrors(formErrors)
    if (Object.keys(formErrors).length > 0) return
    setSaveState({ status: 'saving' })
    try {
      const saved = await unlinkList(editTarget.systemId, {
        ...propertiesOf(toRequest(form)),
        reviewToken: reviewState.review.reviewToken,
      })
      setErrors({})
      setSaveState({ status: 'idle' })
      onUnlinked?.(saved)
    } catch (error) {
      if (error instanceof AttributeApiError && error.kind === 'conflict') {
        setSaveState({ status: 'outdated', message: messageOf(error) })
        setReviewState({ status: 'loading' })
        setReviewRequest((request) => request + 1)
        return
      }
      if (error instanceof AttributeApiError && error.kind !== 'unavailable') {
        setErrors(Object.fromEntries(error.errors.map((item) => [item.field, item.message])))
        const shownNextToAField = error.errors.some((item) => FORM_FIELDS.has(item.field))
        setSaveState(
          shownNextToAField ? { status: 'idle' } : { status: 'failed', message: messageOf(error) },
        )
        return
      }
      setSaveState({ status: 'failed', message: CONNECTION_ERROR })
    }
  }

  function retryLoadingTypes() {
    setTypesState({ status: 'loading' })
    setTypesRequest((request) => request + 1)
  }

  function update(changes: Partial<AttributeForm>) {
    setForm((current) => ({ ...current, ...changes }))
  }

  /**
   * One of the eight «Tipo de dato» choices (feature 004 FR-007, amended 2026-11-20): a numeric
   * choice sets the data type Numérico and its format together.
   */
  function chooseType(choice: string) {
    const { dataType, numberFormat } = fromChoice(choice)
    setForm((current) => ({
      ...current,
      dataType,
      numeric: numberFormat === undefined ? current.numeric : { ...current.numeric, numberFormat },
    }))
  }

  /** While editing, only a numeric attribute may change its choice, between integer and decimal. */
  const numericEdit = editing && form.dataType === 'NUMERIC'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formErrors = validateForm(form)
    setErrors(formErrors)
    if (Object.keys(formErrors).length > 0) {
      setSaveState({ status: 'idle' })
      return
    }
    setSaveState({ status: 'saving' })
    try {
      if (editTarget) {
        const saved = await updateAttribute(editTarget.systemId, toRequest(form))
        setErrors({})
        setSaveState({ status: 'idle' })
        onSaved?.(saved)
        return
      }
      const created = await createAttribute(toRequest(form))
      setForm(emptyForm())
      setErrors({})
      setSaveState({ status: 'created', systemId: created.systemId })
      onSaved?.(created)
    } catch (error) {
      if (error instanceof AttributeApiError && error.kind !== 'unavailable') {
        const fieldErrors = Object.fromEntries(
          error.errors.map((item) => [item.field, item.message]),
        )
        setErrors(fieldErrors)
        // The list select exists only while lists are offered (feature 006 FR-026).
        const listSelectShown = listsState?.status === 'ready' && listsState.lists.length > 0
        const shownNextToAField = error.errors.some(
          (item) =>
            FORM_FIELDS.has(item.field) ||
            (listSelectShown && item.field === 'listOfValuesProperties.listOfValuesId'),
        )
        setSaveState(
          shownNextToAField
            ? { status: 'idle' }
            : {
                status: 'failed',
                message: error.errors[0]?.message ?? error.detail ?? CONNECTION_ERROR,
              },
        )
        return
      }
      setSaveState({ status: 'failed', message: CONNECTION_ERROR })
    }
  }

  const listName =
    reviewState?.status === 'ready'
      ? (reviewState.review.listOfValuesName ?? reviewState.review.listOfValuesId)
      : (editTarget?.listOfValuesProperties?.listOfValuesName ??
        editTarget?.listOfValuesProperties?.listOfValuesId ??
        '')
  const saving = saveState.status === 'saving'

  return (
    <Card
      title={
        unlinking && editTarget
          ? `Desvincular lista de «${editTarget.displayName}»`
          : editing
            ? 'Editar atributo'
            : 'Nuevo atributo'
      }
    >
      <p className={styles.intro}>
        {unlinking
          ? `El atributo dejará de usar la lista «${listName}» y tendrá un nuevo tipo de dato. Su ID, nombre, grupos y nodos de taxonomía no cambian.`
          : editing
            ? 'El ID y el tipo de dato no se pueden cambiar.'
            : 'Define un atributo nuevo, su tipo de dato y sus propiedades. Los atributos se crean globales (aplican a todo el sistema) y sin grupos.'}
      </p>

      {typesState.status === 'loading' && <p className={styles.loading}>Cargando tipos de dato…</p>}

      {typesState.status === 'error' && (
        <Notification variant="error">
          <div className={styles.errorContent}>
            <span>{CONNECTION_ERROR}</span>
            <Button variant="secondary" onClick={retryLoadingTypes}>
              Reintentar
            </Button>
          </div>
        </Notification>
      )}

      {typesState.status === 'ready' && (
        <form className={styles.form} noValidate onSubmit={unlinking ? handleUnlink : handleSubmit}>
          {/* The ID comes first: it is captured before every other field. */}
          {editing ? (
            <TextField id="systemId" label="ID" value={form.systemId} readOnly />
          ) : (
            <TextField
              id="systemId"
              label="ID (opcional)"
              maxLength={40}
              hint="Déjalo vacío para que el sistema genere uno."
              value={form.systemId}
              onChange={(event) => update({ systemId: event.target.value })}
              error={errors.systemId}
            />
          )}
          <TextField
            id="displayName"
            label="Nombre"
            maxLength={120}
            value={form.displayName}
            readOnly={unlinking}
            onChange={(event) => update({ displayName: event.target.value })}
            error={errors.displayName}
          />
          {unlinking ? (
            <SelectField
              id="dataType"
              label="Tipo de dato"
              placeholder="Elige el nuevo tipo de dato"
              options={typeChoices(typesState.types).filter(
                (choice) => choice.code !== 'LIST_OF_VALUES',
              )}
              value={choiceOf(form.dataType, form.numeric.numberFormat)}
              onChange={chooseType}
              error={errors.dataType ?? errors['numericProperties.numberFormat']}
            />
          ) : (
            <SelectField
              id="dataType"
              label="Tipo de dato"
              placeholder="Elige un tipo de dato"
              options={numericEdit ? numericChoices() : typeChoices(typesState.types)}
              value={choiceOf(form.dataType, form.numeric.numberFormat)}
              onChange={chooseType}
              error={errors.dataType ?? errors['numericProperties.numberFormat']}
              hint={numericEdit ? 'Puedes cambiar entre entero y decimal.' : undefined}
              disabled={editing && !numericEdit}
            />
          )}

          <TypeFields
            form={form}
            errors={errors}
            options={optionsOf(typesState.types, form.dataType)}
            listsState={listsState}
            onChange={update}
          />

          <AttributeFieldPreview form={form} options={optionsOf(typesState.types, form.dataType)} />

          {unlinking && reviewState?.status === 'loading' && (
            <p className={styles.loading}>Revisando la desvinculación…</p>
          )}
          {unlinking && reviewState?.status === 'error' && (
            <Notification variant="error">{reviewState.message}</Notification>
          )}
          {unlinking && reviewState?.status === 'ready' && (
            <UnlinkReview review={reviewState.review} />
          )}

          {unlinking ? (
            <div className={styles.actions}>
              <Button variant="secondary" disabled={saving} onClick={cancelUnlink}>
                Cancelar
              </Button>
              <Button type="submit" disabled={saving || reviewState?.status !== 'ready'}>
                Desvincular y guardar
              </Button>
            </div>
          ) : (
            <div className={styles.actions}>
              {canUnlink && (
                <Button variant="secondary" disabled={saving} onClick={startUnlink}>
                  Desvincular lista
                </Button>
              )}
              {onCancel && (
                <Button variant="secondary" disabled={saving} onClick={onCancel}>
                  Cancelar
                </Button>
              )}
              <Button type="submit" disabled={saving}>
                {editing ? 'Guardar cambios' : 'Crear atributo'}
              </Button>
            </div>
          )}
        </form>
      )}

      <div className={styles.messages} aria-live="polite">
        {saveState.status === 'created' && (
          <Notification variant="success">
            Atributo global creado con el ID {saveState.systemId}.
          </Notification>
        )}
        {saveState.status === 'failed' && (
          <Notification variant="error">{saveState.message}</Notification>
        )}
        {saveState.status === 'outdated' && (
          <Notification variant="warning">{saveState.message}</Notification>
        )}
      </div>
    </Card>
  )
}

/** Request fields that have an input on this page; other errors are shown as a notification. */
const FORM_FIELDS = new Set([
  'displayName',
  'systemId',
  'dataType',
  'textProperties.minLength',
  'textProperties.maxLength',
  'textProperties.caseFormat',
  'textProperties.captureMask',
  'numericProperties.precision',
  'numericProperties.minimum',
  'numericProperties.maximum',
  'listOfValuesProperties.selectionMode',
  'dateTimeProperties.dateFormat',
  'currencyProperties.currencyCode',
  'currencyProperties.decimals',
  'referenceProperties.targetObjectType',
  'referenceProperties.cardinality',
])

/** The form at the start of unlink mode: no data type chosen yet (feature 014 FR-001). */
function unlinkStart(form: AttributeForm, unlinking: boolean): AttributeForm {
  return unlinking ? { ...form, dataType: '' } : form
}

/** The backend's Spanish message, or the connection message when it could not be reached. */
function messageOf(error: unknown): string {
  if (error instanceof AttributeApiError && error.kind !== 'unavailable') {
    return error.errors[0]?.message ?? error.detail ?? CONNECTION_ERROR
  }
  return CONNECTION_ERROR
}

function optionsOf(types: AttributeType[], code: DataType | '') {
  return types.find((type) => type.code === code)?.options ?? {}
}

type TypeFieldsProps = {
  form: AttributeForm
  errors: FieldErrors
  options: Record<string, AttributeOption[]>
  listsState: ListsState
  onChange: (changes: Partial<AttributeForm>) => void
}

/** The properties of the chosen data type (FR-008 to FR-015); none for Booleano. */
function TypeFields({ form, errors, options, listsState, onChange }: TypeFieldsProps) {
  switch (form.dataType) {
    case 'TEXT': {
      const set = (changes: Partial<AttributeForm['text']>) =>
        onChange({ text: { ...form.text, ...changes } })
      return (
        <fieldset className={styles.typeFields}>
          <legend className={styles.legend}>Propiedades de texto</legend>
          <div className={styles.row}>
            <TextField
              id="text-minLength"
              label="Longitud mínima"
              type="number"
              min={0}
              value={form.text.minLength}
              onChange={(event) => set({ minLength: event.target.value })}
              error={errors['textProperties.minLength']}
            />
            <TextField
              id="text-maxLength"
              label="Longitud máxima"
              type="number"
              min={1}
              value={form.text.maxLength}
              onChange={(event) => set({ maxLength: event.target.value })}
              error={errors['textProperties.maxLength']}
            />
          </div>
          <SelectField
            id="text-caseFormat"
            label="Formato de mayúsculas"
            placeholder="Sin formato"
            options={options.caseFormat ?? []}
            value={form.text.caseFormat}
            onChange={(code) => set({ caseFormat: code })}
            error={errors['textProperties.caseFormat']}
          />
          <TextField
            id="text-captureMask"
            label="Máscara de captura (expresión regular)"
            maxLength={500}
            value={form.text.captureMask}
            onChange={(event) => set({ captureMask: event.target.value })}
            error={errors['textProperties.captureMask']}
          />
          <CheckboxField
            id="text-richText"
            label="Texto enriquecido (HTML)"
            checked={form.text.richText}
            onChange={(checked) => set({ richText: checked })}
          />
        </fieldset>
      )
    }
    case 'NUMERIC': {
      const set = (changes: Partial<AttributeForm['numeric']>) =>
        onChange({ numeric: { ...form.numeric, ...changes } })
      return (
        <fieldset className={styles.typeFields}>
          <legend className={styles.legend}>Propiedades numéricas</legend>
          {form.numeric.numberFormat === 'DECIMAL' && (
            <TextField
              id="numeric-precision"
              label="Decimales"
              type="number"
              min={1}
              max={10}
              value={form.numeric.precision}
              onChange={(event) => set({ precision: event.target.value })}
              error={errors['numericProperties.precision']}
            />
          )}
          <div className={styles.row}>
            <TextField
              id="numeric-minimum"
              label="Valor mínimo"
              inputMode="decimal"
              value={form.numeric.minimum}
              onChange={(event) => set({ minimum: event.target.value })}
              error={errors['numericProperties.minimum']}
            />
            <TextField
              id="numeric-maximum"
              label="Valor máximo"
              inputMode="decimal"
              value={form.numeric.maximum}
              onChange={(event) => set({ maximum: event.target.value })}
              error={errors['numericProperties.maximum']}
            />
          </div>
        </fieldset>
      )
    }
    case 'LIST_OF_VALUES': {
      const set = (changes: Partial<AttributeForm['listOfValues']>) =>
        onChange({ listOfValues: { ...form.listOfValues, ...changes } })
      const lists = listsState?.status === 'ready' ? listsState.lists : []
      return (
        <fieldset className={styles.typeFields}>
          <legend className={styles.legend}>Propiedades de la Lista de Valores (LOV's)</legend>
          {listsState?.status === 'error' ? (
            <Notification variant="error">
              No se pudieron cargar las listas de valores. Inténtalo de nuevo.
            </Notification>
          ) : lists.length === 0 ? (
            <Notification variant="info">
              Aún no existen listas de valores. Podrás crear este atributo cuando exista una lista.
            </Notification>
          ) : (
            <SelectField
              id="listOfValues-listOfValuesId"
              label="Lista de Valores (LOV's)"
              placeholder="Elige una lista"
              options={lists.map((list) => ({
                code: list.systemId,
                label: `${list.name} (${list.systemId})`,
              }))}
              value={form.listOfValues.listOfValuesId ?? ''}
              onChange={(code) => set({ listOfValuesId: code })}
              error={errors['listOfValuesProperties.listOfValuesId']}
            />
          )}
          <SelectField
            id="listOfValues-selectionMode"
            label="Tipo de selección"
            placeholder="Elige un tipo de selección"
            options={options.selectionMode ?? []}
            value={form.listOfValues.selectionMode}
            onChange={(code) => set({ selectionMode: code })}
            error={errors['listOfValuesProperties.selectionMode']}
          />
        </fieldset>
      )
    }
    case 'DATE_TIME':
      return (
        <fieldset className={styles.typeFields}>
          <legend className={styles.legend}>Propiedades de fecha y hora</legend>
          <SelectField
            id="dateTime-dateFormat"
            label="Formato de fecha"
            placeholder="Elige un formato"
            options={options.dateFormat ?? []}
            value={form.dateTime.dateFormat}
            onChange={(code) => onChange({ dateTime: { dateFormat: code } })}
            error={errors['dateTimeProperties.dateFormat']}
          />
        </fieldset>
      )
    case 'CURRENCY': {
      const set = (changes: Partial<AttributeForm['currency']>) =>
        onChange({ currency: { ...form.currency, ...changes } })
      return (
        <fieldset className={styles.typeFields}>
          <legend className={styles.legend}>Propiedades de moneda</legend>
          <div className={styles.row}>
            <SelectField
              id="currency-currencyCode"
              label="Moneda"
              placeholder="Elige una moneda"
              options={currencyChoices(options.currencyCode, form.currency.currencyCode)}
              value={form.currency.currencyCode.trim().toUpperCase()}
              onChange={(code) => set({ currencyCode: code })}
              error={errors['currencyProperties.currencyCode']}
            />
            <TextField
              id="currency-decimals"
              label="Decimales"
              type="number"
              min={0}
              max={4}
              hint="Si lo dejas vacío, se usan 2."
              value={form.currency.decimals}
              onChange={(event) => set({ decimals: event.target.value })}
              error={errors['currencyProperties.decimals']}
            />
          </div>
        </fieldset>
      )
    }
    case 'REFERENCE': {
      const set = (changes: Partial<AttributeForm['reference']>) =>
        onChange({ reference: { ...form.reference, ...changes } })
      return (
        <fieldset className={styles.typeFields}>
          <legend className={styles.legend}>Propiedades de la referencia</legend>
          <SelectField
            id="reference-targetObjectType"
            label="Objeto referenciado"
            placeholder="Elige un tipo de objeto"
            options={options.targetObjectType ?? []}
            value={form.reference.targetObjectType}
            onChange={(code) => set({ targetObjectType: code })}
            error={errors['referenceProperties.targetObjectType']}
          />
          <SelectField
            id="reference-cardinality"
            label="Cantidad"
            placeholder="Una (predeterminado)"
            options={options.cardinality ?? []}
            value={form.reference.cardinality}
            onChange={(code) => set({ cardinality: code })}
            error={errors['referenceProperties.cardinality']}
          />
        </fieldset>
      )
    }
    default:
      return null
  }
}
