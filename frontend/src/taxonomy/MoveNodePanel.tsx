import { useEffect, useRef, useState } from 'react'
import type { TaxonomyNode, TaxonomyNodeSummary } from '../api/taxonomyApi'
import { normalized } from '../attributes/attributeBrowsing'
import { Button, Notification, TextField } from '../components/ui'
import { Icon } from '../components/ui/Icon'
import styles from './MoveNodePanel.module.css'
import { descendantIds, moveDestinations, pathOf, type MoveDestination } from './nodeTree'

/** Destinations shown at a time (feature 011 UI contract, like "Agregar atributos"). */
const PAGE_SIZE = 50
const TOP_LEVEL_LABEL = 'Primer nivel (sin nodo padre)'
/** The chosen destination: a node's system ID, or the top level. */
const TOP = '__primer-nivel__'

interface MoveNodePanelProps {
  node: TaxonomyNode
  /** The flat node list the page already loaded; used only to show choices (FR-016). */
  items: TaxonomyNodeSummary[]
  busy: boolean
  onCancel: () => void
  /** Called after the confirmation with the new parent's system ID, or `null` for the top level. */
  onMove: (parentId: string | null) => void
}

function descendantsNote(count: number): string | undefined {
  if (count === 0) return undefined
  if (count === 1) return 'Se moverá junto con su nodo descendiente.'
  return `Se moverá junto con sus ${count} nodos descendientes.`
}

function countText(name: string, total: number): string {
  if (total === 1) return `Se moverá 1 nodo: «${name}».`
  const rest = total - 1
  return `Se moverán ${total} nodos: «${name}» y ${rest} ${rest === 1 ? 'descendiente' : 'descendientes'}.`
}

function matches(destination: MoveDestination, search: string): boolean {
  const wanted = normalized(search.trim())
  return (
    wanted === '' ||
    normalized(destination.name).includes(wanted) ||
    normalized(destination.systemId).includes(wanted)
  )
}

/**
 * Moves the node with its branch (feature 011): choose a destination, then confirm. Invalid
 * destinations (the node, its descendants, its current parent) are not offered; the backend still
 * checks every rule (FR-016). Texts: specs/011-taxonomy-node-relocation/contracts/taxonomy-move-ui.md.
 */
export function MoveNodePanel({ node, items, busy, onCancel, onMove }: MoveNodePanelProps) {
  const [search, setSearch] = useState('')
  const [shown, setShown] = useState(PAGE_SIZE)
  const [chosen, setChosen] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const confirmationRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (confirming) confirmationRef.current?.focus()
  }, [confirming])

  const descendants = descendantIds(items, node.systemId).size
  const isTopLevel = !node.parentId
  const destinations = moveDestinations(items, node)
  const matching = destinations.filter((destination) => matches(destination, search))
  const wantedTop = normalized(search.trim())
  const showTop =
    !isTopLevel && (wantedTop === '' || normalized(TOP_LEVEL_LABEL).includes(wantedTop))
  const currentLocation =
    node.path.length === 0 ? 'Primer nivel' : node.path.map((ancestor) => ancestor.name).join(' › ')
  const note = descendantsNote(descendants)

  if (confirming && chosen) {
    const destination = destinations.find((candidate) => candidate.systemId === chosen)
    const toTop = chosen === TOP
    const question = toTop
      ? `¿Mover «${node.name}» al primer nivel?`
      : `¿Mover «${node.name}» a «${destination?.name ?? ''}»?`
    const newLocation = toTop
      ? `Nueva ubicación: ${node.name} (primer nivel)`
      : `Nueva ubicación: ${pathOf(items, chosen).join(' › ')} › ${node.name}`
    return (
      <div className={styles.panel}>
        <Notification variant="warning">
          <p ref={confirmationRef} tabIndex={-1} className={styles.message}>
            {question}
          </p>
          <p className={styles.message}>{newLocation}</p>
          <p className={styles.message}>{countText(node.name, descendants + 1)}</p>
          <p className={styles.message}>
            Dejará de heredar los atributos de su ubicación actual y heredará los de su nuevo
            destino.
          </p>
          <div className={styles.actions}>
            <Button variant="secondary" disabled={busy} onClick={() => setConfirming(false)}>
              Cancelar
            </Button>
            <Button disabled={busy} onClick={() => onMove(toTop ? null : chosen)}>
              Sí, mover nodo
            </Button>
          </div>
        </Notification>
      </div>
    )
  }

  function option(id: string, name: string, detail?: string) {
    const selected = chosen === id
    return (
      <li key={id}>
        <button
          type="button"
          className={styles.option}
          aria-pressed={selected}
          onClick={() => setChosen(id)}
        >
          <span className={styles.check} aria-hidden="true">
            {selected && <Icon name="success" size={16} />}
          </span>
          <span className={styles.optionText}>
            <span className={styles.optionName}>{name}</span>
            {detail && <span className={styles.optionDetail}>{detail}</span>}
          </span>
        </button>
      </li>
    )
  }

  const nothingAvailable = destinations.length === 0 && isTopLevel

  return (
    <fieldset className={styles.panel}>
      <legend className={styles.legend}>Mover «{node.name}»</legend>
      <p className={styles.muted}>Ubicación actual: {currentLocation}</p>
      {note && <p className={styles.muted}>{note}</p>}

      {nothingAvailable ? (
        <p className={styles.muted}>No hay otro lugar al que mover este nodo.</p>
      ) : (
        <>
          <TextField
            id="buscar-nodo-destino"
            label="Buscar nodo destino"
            type="search"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setShown(PAGE_SIZE)
            }}
          />
          {!showTop && matching.length === 0 && (
            <p className={styles.muted}>Ningún nodo coincide con la búsqueda.</p>
          )}
          <ul className={styles.options}>
            {showTop && option(TOP, TOP_LEVEL_LABEL)}
            {matching
              .slice(0, shown)
              .map((destination) =>
                option(
                  destination.systemId,
                  destination.name,
                  `${destination.path.join(' › ')} · ${destination.systemId}`,
                ),
              )}
          </ul>
          {matching.length > shown && (
            <div className={styles.actions}>
              <p className={styles.muted}>
                Mostrando {shown} de {matching.length} nodos.
              </p>
              <Button variant="secondary" onClick={() => setShown(shown + PAGE_SIZE)}>
                Mostrar más
              </Button>
            </div>
          )}
        </>
      )}

      <div className={styles.actions}>
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button disabled={busy || chosen === null} onClick={() => setConfirming(true)}>
          Continuar
        </Button>
      </div>
    </fieldset>
  )
}
