import { useId, useState } from 'react'
import {
  AttributeApiError,
  getAttribute,
  type Attribute,
  type GroupReference,
} from '../api/attributesApi'
import { AttributeGroupApiError, addAttributes, removeAttribute } from '../api/attributeGroupsApi'
import { Button, Notification, SelectField } from '../components/ui'
import type { GroupsState } from './attributeBrowsing'
import styles from './AttributeGroupsSection.module.css'

const CONNECTION_ERROR = 'No se pudo conectar con el servidor. Inténtalo de nuevo.'

type Props = {
  attribute: Attribute
  groupsState: GroupsState
  /** Whether change controls are shown (feature 005's development permission, unchanged). */
  canChange: boolean
  /** The attribute as now stored, and the confirmation when a change was saved. */
  onChanged: (attribute: Attribute, message?: string) => void
  onError: (message: string) => void
}

function messageOf(error: unknown): string {
  if (
    (error instanceof AttributeGroupApiError || error instanceof AttributeApiError) &&
    error.kind !== 'unavailable' &&
    error.detail
  ) {
    return error.detail
  }
  return CONNECTION_ERROR
}

/**
 * The groups of an attribute in its detail (feature 009 FR-011): add it to a group or remove it
 * through feature 005's operations, which never change or delete the attribute.
 */
export function AttributeGroupsSection({
  attribute,
  groupsState,
  canChange,
  onChanged,
  onError,
}: Props) {
  const headingId = useId()
  const [chosen, setChosen] = useState('')
  const [removing, setRemoving] = useState<GroupReference | null>(null)
  const [busy, setBusy] = useState(false)

  const memberOf = attribute.groups ?? []
  const allGroups = groupsState.status === 'ready' ? groupsState.groups : []
  const candidates = allGroups.filter((group) => !memberOf.some((member) => member.id === group.id))

  /** Runs one membership change, then reads the attribute again so the page shows its state. */
  async function change(action: () => Promise<unknown>, message: string) {
    setBusy(true)
    try {
      await action()
      onChanged(await getAttribute(attribute.systemId), message)
      setChosen('')
    } catch (error) {
      onError(messageOf(error))
      try {
        onChanged(await getAttribute(attribute.systemId))
      } catch {
        // The message already explains the failure; keep what is shown.
      }
    } finally {
      setBusy(false)
      setRemoving(null)
    }
  }

  function add() {
    const group = candidates.find((candidate) => candidate.id === chosen)
    if (!group) return
    void change(
      () => addAttributes(group.id, [attribute.systemId]),
      `Atributo «${attribute.displayName}» agregado al grupo «${group.name}».`,
    )
  }

  function remove(group: GroupReference) {
    void change(
      () => removeAttribute(group.id, attribute.systemId),
      `Atributo «${attribute.displayName}» quitado del grupo «${group.name}».`,
    )
  }

  return (
    <div className={styles.section} aria-labelledby={headingId} role="group">
      <h3 id={headingId} className={styles.heading}>
        Grupos
      </h3>
      {memberOf.length === 0 ? (
        <p className={styles.muted}>Sin grupos</p>
      ) : (
        <ul className={styles.groups}>
          {memberOf.map((group) => (
            <li key={group.id} className={styles.group}>
              <span className={styles.groupName}>{group.name}</span>
              {canChange && (
                <Button
                  variant="secondary"
                  disabled={busy || removing !== null}
                  aria-label={`Quitar del grupo ${group.name}`}
                  onClick={() => setRemoving(group)}
                >
                  Quitar
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {removing && (
        <Notification variant="warning">
          <p className={styles.inlineMessage}>
            ¿Quitar «{attribute.displayName}» del grupo «{removing.name}»? El atributo no se elimina
            ni cambia.
          </p>
          <div className={styles.actions}>
            <Button variant="secondary" disabled={busy} onClick={() => setRemoving(null)}>
              Cancelar
            </Button>
            <Button disabled={busy} onClick={() => remove(removing)}>
              Sí, quitar del grupo
            </Button>
          </div>
        </Notification>
      )}

      {canChange && groupsState.status === 'error' && (
        <p className={styles.muted}>No se pudieron cargar los grupos para agregar el atributo.</p>
      )}
      {canChange && groupsState.status === 'ready' && allGroups.length === 0 && (
        <p className={styles.muted}>Todavía no hay grupos. Créalos en «Grupo de Atributos».</p>
      )}
      {canChange && allGroups.length > 0 && candidates.length === 0 && (
        <p className={styles.muted}>El atributo ya está en todos los grupos.</p>
      )}
      {canChange && candidates.length > 0 && (
        <div className={styles.add}>
          <SelectField
            id="agregar-a-grupo"
            label="Agregar a un grupo"
            placeholder="Elige un grupo"
            options={candidates.map((group) => ({ code: group.id, label: group.name }))}
            value={chosen}
            onChange={setChosen}
          />
          <Button variant="secondary" disabled={busy || chosen === ''} onClick={add}>
            Agregar
          </Button>
        </div>
      )}
    </div>
  )
}
