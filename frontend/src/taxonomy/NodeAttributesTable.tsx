import type { EffectiveAttribute, TaxonomyNode } from '../api/taxonomyApi'
import { Button, Notification, StatusBadge } from '../components/ui'
import styles from './NodeAttributesTable.module.css'

type Props = {
  node: TaxonomyNode
  /** Spanish label of a data type code (the code itself when unknown). */
  typeLabel: (code: string) => string
  canChange: boolean
  busy: boolean
  onOpenNode: (nodeId: string) => void
  onRemove: (attribute: EffectiveAttribute) => void
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

/**
 * The effective attributes of a node (feature 010 FR-019): each with a "Directo" or "Heredado"
 * badge and the node it comes from, so the user sees what applies and why.
 */
export function NodeAttributesTable({
  node,
  typeLabel,
  canChange,
  busy,
  onOpenNode,
  onRemove,
}: Props) {
  const attributes = node.effectiveAttributes
  const direct = attributes.filter((attribute) => attribute.assignment === 'DIRECT').length
  const inherited = attributes.length - direct

  return (
    <div className={styles.section}>
      <h3 className={styles.heading}>Atributos del nodo</h3>
      {attributes.length === 0 ? (
        <p className={styles.muted}>
          {node.level === 1
            ? 'Este nodo no tiene atributos todavía.'
            : 'Este nodo no tiene atributos todavía; tampoco hereda atributos de sus niveles superiores.'}
        </p>
      ) : (
        <>
          <p className={styles.summary}>
            {plural(direct, 'directo', 'directos')} · {plural(inherited, 'heredado', 'heredados')}
          </p>
          {direct === 0 && (
            <Notification variant="info">
              Este nodo no tiene atributos asignados directamente; hereda {inherited} de sus niveles
              superiores.
            </Notification>
          )}
          <div className={styles.scroll}>
            <table className={styles.table} aria-label={`Atributos de «${node.name}»`}>
              <thead>
                <tr>
                  <th scope="col">Atributo</th>
                  <th scope="col">Tipo de dato</th>
                  <th scope="col">Asignación</th>
                  <th scope="col">Origen</th>
                  {canChange && (
                    <th scope="col">
                      <span className={styles.srOnly}>Acciones</span>
                    </th>
                  )}
                </tr>
              </thead>
              <tbody>
                {attributes.map((attribute) => (
                  <tr key={attribute.systemId}>
                    <td>
                      <span className={styles.name}>{attribute.displayName}</span>
                      <span className={styles.secondary}>{attribute.systemId}</span>
                    </td>
                    <td>{typeLabel(attribute.dataType)}</td>
                    <td>
                      {attribute.assignment === 'DIRECT' ? (
                        <StatusBadge variant="info">Directo</StatusBadge>
                      ) : (
                        <StatusBadge variant="neutral">Heredado</StatusBadge>
                      )}
                    </td>
                    <td>
                      {attribute.assignment === 'DIRECT' ? (
                        <>
                          <span>Este nodo</span>
                          {attribute.alsoInheritedFrom && (
                            <span className={styles.secondary}>
                              También se hereda de «{attribute.alsoInheritedFrom.name}»
                            </span>
                          )}
                        </>
                      ) : (
                        <button
                          type="button"
                          className={styles.link}
                          aria-label={`Ir al nodo ${attribute.origin.name}`}
                          onClick={() => onOpenNode(attribute.origin.systemId)}
                        >
                          {attribute.origin.name}
                        </button>
                      )}
                    </td>
                    {canChange && (
                      <td className={styles.actions}>
                        {attribute.assignment === 'DIRECT' && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            aria-label={`Quitar ${attribute.displayName} de este nodo`}
                            onClick={() => onRemove(attribute)}
                          >
                            Quitar
                          </Button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {canChange && inherited > 0 && (
            <p className={styles.muted}>Los atributos heredados se quitan en su nodo de origen.</p>
          )}
        </>
      )}
    </div>
  )
}
