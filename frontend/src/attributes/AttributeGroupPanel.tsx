import { useId, useState } from 'react'
import type { AttributeGroupSummary } from '../api/attributeGroupsApi'
import { Button, Card, Notification, TextField } from '../components/ui'
import {
  ALL,
  UNGROUPED,
  countLabel,
  filterGroups,
  type BrowseEntry,
  type GroupsState,
} from './attributeBrowsing'
import styles from './AttributeGroupPanel.module.css'

const GROUPS_PAGE = '#/grupos-de-atributos'

type Props = {
  groupsState: GroupsState
  /** Number of attributes ("Todos los atributos"). */
  totalCount: number
  /** Number of attributes in no group ("Sin grupo"). */
  ungroupedCount: number
  entry: BrowseEntry
  /** Label of the chosen entry, for the narrow-screen toggle. */
  entryLabel: string
  onSelect: (entry: BrowseEntry) => void
  onRetry: () => void
}

function sameEntry(a: BrowseEntry, b: BrowseEntry): boolean {
  if (a.kind === 'group' && b.kind === 'group') return a.groupId === b.groupId
  return a.kind === b.kind
}

/**
 * The group panel of "Atributos" (feature 009): every attribute, those in no group and every
 * group with its number of attributes. Counts are totals; the search does not change them.
 */
export function AttributeGroupPanel({
  groupsState,
  totalCount,
  ungroupedCount,
  entry,
  entryLabel,
  onSelect,
  onRetry,
}: Props) {
  const bodyId = useId()
  const [expanded, setExpanded] = useState(false)
  const [groupSearch, setGroupSearch] = useState('')

  const groups = groupsState.status === 'ready' ? groupsState.groups : []
  const shownGroups = filterGroups(groups, groupSearch)
  const chosenCount =
    entry.kind === 'all'
      ? totalCount
      : entry.kind === 'ungrouped'
        ? ungroupedCount
        : (groups.find((group) => group.id === entry.groupId)?.attributeCount ?? 0)

  function choose(chosen: BrowseEntry) {
    setExpanded(false)
    onSelect(chosen)
  }

  function entryButton(key: string, target: BrowseEntry, label: string, count: number) {
    const pressed = sameEntry(target, entry)
    return (
      <li key={key}>
        <button
          type="button"
          className={`${styles.entry} ${pressed ? styles.selected : ''}`}
          aria-pressed={pressed}
          aria-label={`${label}, ${countLabel(count)}`}
          onClick={() => choose(target)}
        >
          <span className={styles.entryName}>{label}</span>
          <span className={styles.entryCount}>{count}</span>
        </button>
      </li>
    )
  }

  return (
    <Card
      title="Grupos"
      actions={
        <a className={styles.link} href={GROUPS_PAGE}>
          Administrar grupos
        </a>
      }
    >
      <button
        type="button"
        className={styles.toggle}
        aria-expanded={expanded}
        aria-controls={bodyId}
        onClick={() => setExpanded(!expanded)}
      >
        Grupo: {entryLabel} ({chosenCount})
      </button>
      <div id={bodyId} className={`${styles.body} ${expanded ? styles.expanded : ''}`}>
        {groups.length > 0 && (
          <TextField
            id="buscar-grupo"
            label="Buscar grupo"
            type="search"
            value={groupSearch}
            onChange={(event) => setGroupSearch(event.target.value)}
          />
        )}
        <ul className={styles.entries}>
          {entryButton('todos', ALL, 'Todos los atributos', totalCount)}
          {entryButton('sin-grupo', UNGROUPED, 'Sin grupo', ungroupedCount)}
          {shownGroups.map((group: AttributeGroupSummary) =>
            entryButton(
              group.id,
              { kind: 'group', groupId: group.id },
              group.name,
              group.attributeCount,
            ),
          )}
        </ul>
        {groupsState.status === 'loading' && <p className={styles.muted}>Cargando grupos…</p>}
        {groupsState.status === 'error' && (
          <Notification variant="error">
            <p className={styles.inlineMessage}>No se pudieron cargar los grupos.</p>
            <Button variant="secondary" onClick={onRetry}>
              Reintentar
            </Button>
          </Notification>
        )}
        {groupsState.status === 'ready' && groups.length === 0 && (
          <>
            <p className={styles.muted}>Todavía no hay grupos de atributos.</p>
            <a className={styles.link} href={GROUPS_PAGE}>
              Crear grupos en «Grupo de Atributos»
            </a>
          </>
        )}
        {groups.length > 0 && shownGroups.length === 0 && (
          <p className={styles.muted}>Ningún grupo coincide con «{groupSearch.trim()}».</p>
        )}
      </div>
    </Card>
  )
}
