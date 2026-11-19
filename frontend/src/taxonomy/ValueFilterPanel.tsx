import { useState } from 'react'
import type { ListValue, ValueFilterDetail } from '../api/taxonomyApi'
import { normalized } from '../attributes/attributeBrowsing'
import { Button, Notification, TextField } from '../components/ui'
import styles from './ValueFilterPanel.module.css'

/** Rows shown at a time in each column (UI contract). */
const PAGE_SIZE = 50

interface ValueFilterPanelProps {
  detail: ValueFilterDetail
  busy: boolean
  onCancel: () => void
  /** Called with the included value IDs allowed above, in list order. */
  onSave: (valueIds: string[]) => void
  /** Called after the inline confirmation. */
  onRemove: () => void
}

const key = (id: string) => id.trim().toUpperCase()

function matches(value: ListValue, search: string): boolean {
  const wanted = normalized(search.trim())
  return (
    wanted === '' ||
    normalized(value.label).includes(wanted) ||
    normalized(value.id).includes(wanted)
  )
}

type ColumnProps = {
  title: string
  searchId: string
  searchLabel: string
  values: ListValue[]
  emptyText: string
  action: 'Incluir' | 'Quitar'
  bulkLabel: string
  busy: boolean
  /** Rows that cannot be moved, with their note (values excluded above). */
  notes?: Map<string, string>
  onMove: (ids: string[]) => void
}

/** One column of the dual list: search, 50 rows at a time, a move button per row, bulk move. */
function Column({
  title,
  searchId,
  searchLabel,
  values,
  emptyText,
  action,
  bulkLabel,
  busy,
  notes,
  onMove,
}: ColumnProps) {
  const [search, setSearch] = useState('')
  const [shown, setShown] = useState(PAGE_SIZE)
  const matching = values.filter((value) => matches(value, search))
  const movable = matching.filter((value) => !notes?.has(key(value.id)))

  return (
    <section className={styles.column} aria-label={title}>
      <h4 className={styles.columnTitle}>{title}</h4>
      <TextField
        id={searchId}
        label={searchLabel}
        type="search"
        value={search}
        onChange={(event) => {
          setSearch(event.target.value)
          setShown(PAGE_SIZE)
        }}
      />
      {values.length === 0 ? (
        <p className={styles.muted}>{emptyText}</p>
      ) : matching.length === 0 ? (
        <p className={styles.muted}>Ningún valor coincide con la búsqueda.</p>
      ) : (
        <>
          {movable.length > 0 && (
            <div className={styles.bulk}>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={() => onMove(movable.map((value) => value.id))}
              >
                {bulkLabel} ({movable.length})
              </Button>
            </div>
          )}
          <ul className={styles.rows}>
            {matching.slice(0, shown).map((value) => {
              const note = notes?.get(key(value.id))
              return (
                <li key={value.id} className={styles.row}>
                  <span className={styles.rowText}>
                    <span className={styles.label}>{value.label}</span>
                    <span className={styles.secondary}>{value.id}</span>
                    {note && <span className={styles.secondary}>{note}</span>}
                  </span>
                  {!note && (
                    <Button
                      variant="secondary"
                      disabled={busy}
                      aria-label={`${action} ${value.label}`}
                      onClick={() => onMove([value.id])}
                    >
                      {action}
                    </Button>
                  )}
                </li>
              )
            })}
          </ul>
          {matching.length > shown && (
            <div className={styles.paging}>
              <p className={styles.muted}>
                Mostrando {shown} de {matching.length}.
              </p>
              <Button variant="secondary" onClick={() => setShown(shown + PAGE_SIZE)}>
                Mostrar más
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  )
}

/**
 * Defines the values of a list attribute allowed at a node (feature 012 US1–US3, HU2.3): two
 * columns, "Valores disponibles" (allowed above) and "Valores incluidos en el filtro". Only values
 * allowed above can be included; the backend checks every rule again (FR-019). Texts:
 * specs/012-taxonomy-list-value-filters/contracts/taxonomy-value-filters-ui.md.
 */
export function ValueFilterPanel({
  detail,
  busy,
  onCancel,
  onSave,
  onRemove,
}: ValueFilterPanelProps) {
  const [included, setIncluded] = useState<Set<string>>(
    () => new Set((detail.includedValueIds ?? []).map(key)),
  )
  const [confirmingRemove, setConfirmingRemove] = useState(false)

  const attribute = detail.attribute
  const node = detail.node
  const available = detail.availableValues
  const excludedNotes = new Map(
    detail.excludedAboveValues
      .filter((value) => included.has(key(value.id)))
      .map((value) => [key(value.id), `Excluido por «${value.excludedBy.name}»`] as const),
  )
  const left = available.filter((value) => !included.has(key(value.id)))
  const right = [
    ...available.filter((value) => included.has(key(value.id))),
    ...detail.excludedAboveValues.filter((value) => excludedNotes.has(key(value.id))),
  ]
  const validIds = available.filter((value) => included.has(key(value.id))).map((value) => value.id)
  const above = [...detail.filters]
    .reverse()
    .find((filter) => filter.node.systemId.toUpperCase() !== node.systemId.toUpperCase())
  const excludedCount = excludedNotes.size

  function include(ids: string[]) {
    setIncluded((current) => new Set([...current, ...ids.map(key)]))
  }

  function exclude(ids: string[]) {
    setIncluded((current) => {
      const next = new Set(current)
      ids.forEach((id) => next.delete(key(id)))
      return next
    })
  }

  if (confirmingRemove) {
    return (
      <div className={styles.panel}>
        <Notification variant="warning">
          <p className={styles.message}>
            ¿Quitar el filtro de «{attribute.displayName}» en «{node.name}»? Este nodo y sus niveles
            inferiores permitirán los valores heredados de arriba.
          </p>
          <div className={styles.actions}>
            <Button variant="secondary" disabled={busy} onClick={() => setConfirmingRemove(false)}>
              Cancelar
            </Button>
            <Button disabled={busy} onClick={onRemove}>
              Sí, quitar filtro
            </Button>
          </div>
        </Notification>
      </div>
    )
  }

  return (
    <fieldset className={styles.panel}>
      <legend className={styles.legend}>
        Filtrar «{attribute.displayName}» en «{node.name}»
      </legend>
      {above && (
        <p className={styles.muted}>
          Arriba se permiten {available.length} de {detail.totalValues} valores (filtro de «
          {above.node.name}»).
        </p>
      )}
      {excludedCount > 0 && (
        <Notification variant="info">
          {excludedCount === 1
            ? '1 valor de este filtro ya no está permitido arriba y no se aplica. Se quitará al guardar.'
            : `${excludedCount} valores de este filtro ya no están permitidos arriba y no se aplican. Se quitarán al guardar.`}
        </Notification>
      )}
      <div className={styles.columns}>
        <Column
          title="Valores disponibles"
          searchId="buscar-valores-disponibles"
          searchLabel="Buscar en disponibles"
          values={left}
          emptyText="No quedan valores por incluir."
          action="Incluir"
          bulkLabel="Incluir los mostrados"
          busy={busy}
          onMove={include}
        />
        <Column
          title="Valores incluidos en el filtro"
          searchId="buscar-valores-incluidos"
          searchLabel="Buscar en incluidos"
          values={right}
          emptyText={`Todavía no incluyes valores. Sin filtro se permiten los ${available.length} valores disponibles.`}
          action="Quitar"
          bulkLabel="Quitar los mostrados"
          busy={busy}
          notes={excludedNotes}
          onMove={exclude}
        />
      </div>
      <p className={styles.counter}>
        {validIds.length === 1 ? '1 valor incluido' : `${validIds.length} valores incluidos`}
      </p>
      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        {detail.includedValueIds !== null && (
          <Button variant="secondary" disabled={busy} onClick={() => setConfirmingRemove(true)}>
            Quitar filtro
          </Button>
        )}
        <Button disabled={busy || validIds.length === 0} onClick={() => onSave(validIds)}>
          Guardar filtro
        </Button>
      </div>
    </fieldset>
  )
}
