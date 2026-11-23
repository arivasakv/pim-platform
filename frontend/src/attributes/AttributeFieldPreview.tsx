import { useEffect, useId, useState } from 'react'
import type { AttributeOption } from '../api/attributesApi'
import { getList, type ListOfValues } from '../api/listsOfValuesApi'
import {
  CheckboxField,
  Notification,
  SelectField,
  TextAreaField,
  TextField,
} from '../components/ui'
import type { AttributeForm } from './attributeForm'
import styles from './AttributeFieldPreview.module.css'
import {
  PREVIEW_VALUE_LIMIT,
  applyCase,
  checkAmount,
  checkDate,
  checkNumber,
  checkText,
  describePreview,
  formatAmount,
  listSummary,
  previewKey,
  type FieldPreview,
} from './fieldPreview'

/** The chosen list's values for the preview (spec FR-008). */
type ListState =
  | { status: 'none' }
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; list: ListOfValues }

type Loaded =
  { listId: string; status: 'ready'; list: ListOfValues } | { listId: string; status: 'error' }

/** Reads the list once per chosen list ID; an answer for a list no longer chosen is ignored. */
function useListValues(listId: string | undefined): ListState {
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  useEffect(() => {
    if (!listId) return
    let isCurrent = true
    getList(listId).then(
      (list) => {
        if (isCurrent) setLoaded({ listId, status: 'ready', list })
      },
      () => {
        if (isCurrent) setLoaded({ listId, status: 'error' })
      },
    )
    return () => {
      isCurrent = false
    }
  }, [listId])
  if (!listId) return { status: 'none' }
  if (loaded?.listId !== listId) return { status: 'loading' }
  return loaded.status === 'ready' ? { status: 'ready', list: loaded.list } : { status: 'error' }
}

type AttributeFieldPreviewProps = {
  /** The attribute form's current state; read only. */
  form: AttributeForm
  /** The option lists of the chosen data type (from `GET /api/v1/attribute-types`). */
  options: Record<string, AttributeOption[]>
}

/**
 * Shows how the attribute's field will look in a product form, from the unsaved configuration
 * (feature 008). Values typed here live only in the inner field, which is re-created on every
 * configuration change, and are never sent or saved (spec FR-011, FR-012).
 */
export function AttributeFieldPreview({ form, options }: AttributeFieldPreviewProps) {
  const headingId = useId()
  const preview = describePreview(form, options)
  const isList = preview.kind === 'select' || preview.kind === 'checkboxes'
  const list = useListValues(isList ? preview.listOfValuesId : undefined)

  return (
    <section className={styles.panel} aria-labelledby={headingId}>
      <h3 id={headingId} className={styles.heading}>
        Vista previa del campo
      </h3>
      <p className={styles.note}>
        Así se verá el campo en un formulario de producto. Lo que escribas aquí no se guarda.
      </p>
      {preview.notes.length > 0 && (
        <Notification variant="info">
          <p className={styles.notesTitle}>Vista previa con ajustes:</p>
          {preview.notes.map((note) => (
            <p key={note} className={styles.noteLine}>
              {note}
            </p>
          ))}
        </Notification>
      )}
      <PreviewField key={previewKey(form)} preview={preview} list={list} />
    </section>
  )
}

type PreviewFieldProps = { preview: FieldPreview; list: ListState }

/** The interactive field; its value is temporary state of this component only. */
function PreviewField({ preview, list }: PreviewFieldProps) {
  const [text, setText] = useState('')
  const [checked, setChecked] = useState(false)
  const [chosen, setChosen] = useState<string[]>([])
  const id = 'vista-previa-campo'

  switch (preview.kind) {
    case 'none':
      return null
    case 'text':
    case 'textarea': {
      const props = {
        id,
        label: preview.label,
        hint: hint(preview.hintParts),
        maxLength: preview.maxLength,
        value: text,
        error: checkText(text, preview),
      }
      const change = (value: string) => setText(applyCase(value, preview.caseFormat))
      return preview.kind === 'textarea' ? (
        <TextAreaField {...props} onChange={(event) => change(event.target.value)} />
      ) : (
        <TextField {...props} onChange={(event) => change(event.target.value)} />
      )
    }
    case 'number':
      return (
        <TextField
          id={id}
          type="number"
          label={preview.label}
          hint={hint(preview.hintParts)}
          min={preview.min}
          max={preview.max}
          step={preview.step}
          value={text}
          error={checkNumber(text, preview)}
          onChange={(event) => setText(event.target.value)}
        />
      )
    case 'currency':
      return <CurrencyField id={id} preview={preview} />
    case 'date':
      return (
        <TextField
          id={id}
          label={preview.label}
          hint={hint(preview.hintParts)}
          placeholder={preview.dateFormat}
          value={text}
          error={preview.dateFormat ? checkDate(text, preview.dateFormat) : undefined}
          onChange={(event) => setText(event.target.value)}
        />
      )
    case 'checkbox':
      return (
        <div className={styles.field}>
          <CheckboxField id={id} label={preview.label} checked={checked} onChange={setChecked} />
          {preview.hintParts.length > 0 && <p className={styles.hint}>{hint(preview.hintParts)}</p>}
        </div>
      )
    case 'reference':
      return (
        <SelectField
          id={id}
          label={preview.label}
          hint={hint(preview.hintParts)}
          placeholder={preview.referencePlaceholder}
          options={[]}
          value=""
          disabled
          onChange={() => {}}
        />
      )
    case 'select':
    case 'checkboxes':
      return <ListField preview={preview} list={list} value={chosen} onChange={setChosen} />
  }
}

/**
 * A currency amount with the symbol in front, shown in financial format once the user leaves the
 * field and as typed while editing (feature 008 FR-009, amended 2026-11-23).
 */
function CurrencyField({ id, preview }: { id: string; preview: FieldPreview }) {
  const [text, setText] = useState('')
  const [editing, setEditing] = useState(false)
  const error = checkAmount(text, preview)
  const formatted =
    !editing && !error && text.trim() !== '' ? formatAmount(text, preview.decimals ?? 2) : undefined
  return (
    <TextField
      id={id}
      label={preview.label}
      hint={hint(preview.hintParts)}
      prefix={preview.currencyPrefix}
      inputMode="decimal"
      value={formatted ?? text}
      error={error}
      onFocus={() => setEditing(true)}
      onBlur={() => setEditing(false)}
      onChange={(event) => setText(event.target.value)}
    />
  )
}

type ListFieldProps = {
  preview: FieldPreview
  list: ListState
  value: string[]
  onChange: (value: string[]) => void
}

function ListField({ preview, list, value, onChange }: ListFieldProps) {
  if (list.status === 'none') {
    return (
      <p className={styles.message}>
        La vista previa necesita una lista de valores asociada al atributo.
      </p>
    )
  }
  if (list.status === 'loading') {
    return <p className={styles.message}>Cargando valores de la lista…</p>
  }
  if (list.status === 'error') {
    return (
      <p className={styles.message}>
        No se pudieron cargar los valores de la lista para la vista previa.
      </p>
    )
  }
  if (list.list.values.length === 0) {
    return <p className={styles.message}>La lista {list.list.name} todavía no tiene valores.</p>
  }
  const values = list.list.values.slice(0, PREVIEW_VALUE_LIMIT)
  const summary = listSummary(Math.max(list.list.valueCount, list.list.values.length))
  const parts = [...preview.hintParts, `Lista: ${list.list.name}`]

  if (preview.kind === 'select') {
    return (
      <div className={styles.field}>
        <SelectField
          id="vista-previa-campo"
          label={preview.label}
          hint={hint(parts)}
          placeholder="Selecciona una opción"
          options={values.map((item) => ({ code: item.id, label: item.label }))}
          value={value[0] ?? ''}
          onChange={(code) => onChange(code ? [code] : [])}
        />
        {summary && <p className={styles.hint}>{summary}</p>}
      </div>
    )
  }
  return (
    <fieldset className={styles.choices}>
      <legend className={styles.legend}>{preview.label}</legend>
      <p className={styles.hint}>{hint([...parts, 'Puedes elegir varios valores'])}</p>
      {values.map((item, index) => (
        <CheckboxField
          key={item.id}
          id={`vista-previa-valor-${index}`}
          label={item.label}
          checked={value.includes(item.id)}
          onChange={(isChecked) =>
            onChange(isChecked ? [...value, item.id] : value.filter((id) => id !== item.id))
          }
        />
      ))}
      {summary && <p className={styles.hint}>{summary}</p>}
    </fieldset>
  )
}

function hint(parts: string[]): string | undefined {
  return parts.length > 0 ? parts.join(' · ') : undefined
}
