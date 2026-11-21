import { useEffect, useState, type FormEvent } from 'react'
import {
  AttributeApiError,
  createAttribute,
  fetchAttributeTypes,
  updateAttribute,
  type Attribute,
  type AttributeOption,
  type AttributeType,
  type DataType,
} from '../api/attributesApi'
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

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

type TypesState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; types: AttributeType[] }

type SaveState =
  | { status: 'idle' }
  | { status: 'saving' }
  | { status: 'created'; systemId: string }
  | { status: 'failed'; message: string }

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
}: AttributeEditorProps) {
  const editTarget = mode === 'edit' ? attribute : undefined
  const editing = editTarget !== undefined
  const [typesState, setTypesState] = useState<TypesState>({ status: 'loading' })
  const [form, setForm] = useState<AttributeForm>(() =>
    editTarget ? fromAttribute(editTarget) : emptyForm(),
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

  function retryLoadingTypes() {
    setTypesState({ status: 'loading' })
    setTypesRequest((request) => request + 1)
  }

  function update(changes: Partial<AttributeForm>) {
    setForm((current) => ({ ...current, ...changes }))
  }

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

  return (
    <Card title={editing ? 'Editar atributo' : 'Nuevo atributo'}>
      <p className={styles.intro}>
        {editing
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
        <form className={styles.form} noValidate onSubmit={handleSubmit}>
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
            onChange={(event) => update({ displayName: event.target.value })}
            error={errors.displayName}
          />
          <SelectField
            id="dataType"
            label="Tipo de dato"
            placeholder="Elige un tipo de dato"
            options={typesState.types}
            value={form.dataType}
            onChange={(code) => update({ dataType: code as DataType | '' })}
            error={errors.dataType}
            disabled={editing}
          />

          <TypeFields
            form={form}
            errors={errors}
            options={optionsOf(typesState.types, form.dataType)}
            listsState={listsState}
            onChange={update}
          />

          <AttributeFieldPreview form={form} options={optionsOf(typesState.types, form.dataType)} />

          <div className={styles.actions}>
            {onCancel && (
              <Button
                variant="secondary"
                disabled={saveState.status === 'saving'}
                onClick={onCancel}
              >
                Cancelar
              </Button>
            )}
            <Button type="submit" disabled={saveState.status === 'saving'}>
              {editing ? 'Guardar cambios' : 'Crear atributo'}
            </Button>
          </div>
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
  'numericProperties.numberFormat',
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
          <SelectField
            id="numeric-numberFormat"
            label="Formato"
            placeholder="Elige un formato"
            options={options.numberFormat ?? []}
            value={form.numeric.numberFormat}
            onChange={(code) => set({ numberFormat: code })}
            error={errors['numericProperties.numberFormat']}
          />
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
          <legend className={styles.legend}>Propiedades de la lista de valores</legend>
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
              label="Lista de valores"
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
            <TextField
              id="currency-currencyCode"
              label="Moneda (código ISO, p. ej. MXN)"
              maxLength={3}
              value={form.currency.currencyCode}
              onChange={(event) => set({ currencyCode: event.target.value })}
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
