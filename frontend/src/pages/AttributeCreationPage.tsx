import { useEffect, useState, type FormEvent } from 'react'
import {
  AttributeApiError,
  createAttribute,
  fetchAttributeTypes,
  type AttributeOption,
  type AttributeType,
  type DataType,
} from '../api/attributesApi'
import {
  emptyForm,
  toRequest,
  validateForm,
  type AttributeForm,
  type FieldErrors,
} from '../attributes/attributeForm'
import {
  Button,
  Card,
  CheckboxField,
  Notification,
  PageHeader,
  SelectField,
  TextAreaField,
  TextField,
} from '../components/ui'
import styles from './AttributeCreationPage.module.css'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

type TypesState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; types: AttributeType[] }

type SaveState =
  | { status: 'idle' }
  | { status: 'saving' }
  | { status: 'created'; systemId: string }
  | { status: 'failed'; message: string }

/**
 * Creates one attribute definition at a time (spec 004). The data types and their options come
 * from the backend (FR-022); the backend checks every rule and assigns the system ID.
 */
export function AttributeCreationPage() {
  const [typesState, setTypesState] = useState<TypesState>({ status: 'loading' })
  const [form, setForm] = useState<AttributeForm>(emptyForm)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saveState, setSaveState] = useState<SaveState>({ status: 'idle' })

  const [typesRequest, setTypesRequest] = useState(0)

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
      const attribute = await createAttribute(toRequest(form))
      setForm(emptyForm())
      setErrors({})
      setSaveState({ status: 'created', systemId: attribute.systemId })
    } catch (error) {
      if (error instanceof AttributeApiError && error.kind !== 'unavailable') {
        const fieldErrors = Object.fromEntries(
          error.errors.map((item) => [item.field, item.message]),
        )
        setErrors(fieldErrors)
        const shownNextToAField = error.errors.some((item) => FORM_FIELDS.has(item.field))
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
    <>
      <PageHeader
        title="Crear atributo"
        description="Define un atributo nuevo, su tipo de dato y sus propiedades. Los atributos se crean globales (aplican a todo el sistema) y sin grupos."
      />

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
        <Card>
          <form className={styles.form} noValidate onSubmit={handleSubmit}>
            {/* The ID comes first: it is captured before every other field. */}
            <TextField
              id="systemId"
              label="ID (opcional)"
              maxLength={40}
              hint="Déjalo vacío para que el sistema genere uno."
              value={form.systemId}
              onChange={(event) => update({ systemId: event.target.value })}
              error={errors.systemId}
            />
            <TextField
              id="displayName"
              label="Nombre"
              maxLength={120}
              value={form.displayName}
              onChange={(event) => update({ displayName: event.target.value })}
              error={errors.displayName}
            />
            <TextAreaField
              id="description"
              label="Descripción"
              maxLength={1000}
              hint="Opcional."
              value={form.description}
              onChange={(event) => update({ description: event.target.value })}
              error={errors.description}
            />
            <SelectField
              id="dataType"
              label="Tipo de dato"
              placeholder="Elige un tipo de dato"
              options={typesState.types}
              value={form.dataType}
              onChange={(code) => update({ dataType: code as DataType | '' })}
              error={errors.dataType}
            />

            <TypeFields
              form={form}
              errors={errors}
              options={optionsOf(typesState.types, form.dataType)}
              onChange={update}
            />

            <div className={styles.actions}>
              <Button type="submit" disabled={saveState.status === 'saving'}>
                Crear atributo
              </Button>
            </div>
          </form>
        </Card>
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
    </>
  )
}

/** Request fields that have an input on this page; other errors are shown as a notification. */
const FORM_FIELDS = new Set([
  'displayName',
  'description',
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
  onChange: (changes: Partial<AttributeForm>) => void
}

/** The properties of the chosen data type (FR-008 to FR-015); none for Booleano. */
function TypeFields({ form, errors, options, onChange }: TypeFieldsProps) {
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
    case 'LIST_OF_VALUES':
      return (
        <fieldset className={styles.typeFields}>
          <legend className={styles.legend}>Propiedades de la lista de valores</legend>
          <Notification variant="info">
            Aún no existen listas de valores. Podrás crear este atributo cuando exista una lista.
          </Notification>
          <SelectField
            id="listOfValues-selectionMode"
            label="Tipo de selección"
            placeholder="Elige un tipo de selección"
            options={options.selectionMode ?? []}
            value={form.listOfValues.selectionMode}
            onChange={(code) => onChange({ listOfValues: { selectionMode: code } })}
            error={errors['listOfValuesProperties.selectionMode']}
          />
        </fieldset>
      )
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
