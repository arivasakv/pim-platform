import { useState } from 'react'
import type { ValueFilterDetail } from '../api/taxonomyApi'
import { PREVIEW_VALUE_LIMIT, listSummary } from '../attributes/fieldPreview'
import { Button, CheckboxField, Notification, SelectField } from '../components/ui'
import styles from './ValueFilterPanel.module.css'

interface NodeListFieldPreviewProps {
  detail: ValueFilterDetail
  onClose: () => void
}

/**
 * The list attribute's field as it would be captured for a product of this node (feature 012 US4,
 * FR-016): feature 008's select (single) or checkboxes (multiple), with only the values allowed at
 * the node, the first 50 and feature 008's summary. Nothing chosen here is saved.
 */
export function NodeListFieldPreview({ detail, onClose }: NodeListFieldPreviewProps) {
  const [selected, setSelected] = useState('')
  const [checked, setChecked] = useState<string[]>([])
  const { attribute, node } = detail
  const values = detail.allowedValues.slice(0, PREVIEW_VALUE_LIMIT)
  const summary = listSummary(detail.allowedValues.length)
  const noneAllowed = detail.allowedValues.length === 0 && detail.filters.length > 0
  const filterNodes = detail.filters.map((filter) => `«${filter.node.name}»`).join(', ')

  return (
    <section className={styles.panel} aria-label={`Vista previa de «${attribute.displayName}»`}>
      <h4 className={styles.columnTitle}>
        Vista previa de «{attribute.displayName}» en «{node.name}»
      </h4>
      <p className={styles.muted}>
        Así se verá el campo al capturar un producto de este nodo. Lo que elijas aquí no se guarda.
      </p>
      {noneAllowed ? (
        <Notification variant="warning">
          Ningún valor está permitido en este nodo. Revisa los filtros de {filterNodes}.
        </Notification>
      ) : attribute.selectionMode === 'MULTIPLE' ? (
        <fieldset className={styles.panel}>
          <legend className={styles.legend}>{attribute.displayName}</legend>
          {values.map((value, index) => (
            <CheckboxField
              key={value.id}
              id={`vista-previa-nodo-${index}`}
              label={value.label}
              checked={checked.includes(value.id)}
              onChange={(isChecked) =>
                setChecked((current) =>
                  isChecked ? [...current, value.id] : current.filter((id) => id !== value.id),
                )
              }
            />
          ))}
        </fieldset>
      ) : (
        <SelectField
          id="vista-previa-nodo"
          label={attribute.displayName}
          options={values.map((value) => ({ code: value.id, label: value.label }))}
          value={selected}
          placeholder="Selecciona una opción"
          onChange={setSelected}
        />
      )}
      {!noneAllowed && summary && <p className={styles.muted}>{summary}</p>}
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onClose}>
          Cerrar vista previa
        </Button>
      </div>
    </section>
  )
}
