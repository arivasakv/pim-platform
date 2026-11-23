import type { ListAttribute, TaxonomyNode } from '../api/taxonomyApi'
import { Button, StatusBadge } from '../components/ui'
import styles from './NodeAttributesTable.module.css'

interface NodeListAttributesProps {
  node: TaxonomyNode
  canChange: boolean
  busy: boolean
  onFilter: (attribute: ListAttribute) => void
  onPreview: (attribute: ListAttribute) => void
  onOpenNode: (nodeId: string) => void
}

/** Where the restriction of a list attribute comes from (UI contract, "Restricción"). */
function Restriction({
  attribute,
  node,
  onOpenNode,
}: {
  attribute: ListAttribute
  node: TaxonomyNode
  onOpenNode: (nodeId: string) => void
}) {
  const filters = attribute.filters
  if (filters.length === 0) return <span>Sin filtro</span>
  const nearestAbove = [...filters]
    .reverse()
    .find((filter) => filter.node.systemId.toUpperCase() !== node.systemId.toUpperCase())
  const link = (target: { systemId: string; name: string }) => (
    <button
      type="button"
      className={styles.link}
      aria-label={`Ir al nodo ${target.name}`}
      onClick={() => onOpenNode(target.systemId)}
    >
      {target.name}
    </button>
  )
  if (!attribute.hasOwnFilter && nearestAbove) {
    return <span>Filtro heredado de «{link(nearestAbove.node)}»</span>
  }
  return (
    <>
      <span>Filtro en este nodo</span>
      {nearestAbove && (
        <span className={styles.secondary}>También limitado por «{nearestAbove.node.name}»</span>
      )}
    </>
  )
}

/**
 * The node's list-of-values attributes with the values allowed there (feature 012 FR-013). Texts:
 * specs/012-taxonomy-list-value-filters/contracts/taxonomy-value-filters-ui.md. Everything shown
 * comes from the backend (FR-019).
 */
export function NodeListAttributes({
  node,
  canChange,
  busy,
  onFilter,
  onPreview,
  onOpenNode,
}: NodeListAttributesProps) {
  const attributes = node.listAttributes ?? []
  if (attributes.length === 0) return null

  return (
    <div className={styles.section}>
      <h3 className={styles.heading}>Lista de Valores (LOV's) en este nodo</h3>
      <p className={styles.muted}>
        Los filtros limitan los valores permitidos en este nodo y en todos sus niveles inferiores.
      </p>
      <div className={styles.scroll}>
        <table className={styles.table} aria-label={`Lista de Valores (LOV's) de «${node.name}»`}>
          <thead>
            <tr>
              <th scope="col">Atributo</th>
              <th scope="col">Valores permitidos</th>
              <th scope="col">Restricción</th>
              <th scope="col">
                <span className={styles.srOnly}>Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {attributes.map((attribute) => (
              <tr key={attribute.systemId}>
                <td>
                  <span className={styles.name}>{attribute.displayName}</span>
                  <span className={styles.secondary}>
                    Lista «{attribute.listName}» · {attribute.systemId}
                  </span>
                </td>
                <td>
                  {attribute.allowedCount === 0 && attribute.filters.length > 0 ? (
                    <StatusBadge variant="error">Ningún valor permitido</StatusBadge>
                  ) : (
                    `${attribute.allowedCount} de ${attribute.totalValues}`
                  )}
                </td>
                <td>
                  <Restriction attribute={attribute} node={node} onOpenNode={onOpenNode} />
                </td>
                <td className={styles.actions}>
                  <Button
                    variant="secondary"
                    disabled={busy}
                    aria-label={`Vista previa de ${attribute.displayName}`}
                    onClick={() => onPreview(attribute)}
                  >
                    Vista previa
                  </Button>
                  {canChange && (
                    <Button
                      variant="secondary"
                      disabled={busy}
                      aria-label={`Filtrar valores de ${attribute.displayName}`}
                      onClick={() => onFilter(attribute)}
                    >
                      Filtrar valores
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
