import { Icon } from '../components/ui'
import type { TreeNode } from './nodeTree'
import styles from './TaxonomyTree.module.css'

type Props = {
  roots: TreeNode[]
  selectedId?: string
  /** IDs of the nodes whose children are shown. */
  expanded: ReadonlySet<string>
  onToggle: (nodeId: string) => void
  onSelect: (nodeId: string) => void
}

function directLabel(count: number): string {
  return count === 1 ? '1 atributo directo' : `${count} atributos directos`
}

/**
 * The product hierarchy as nested lists (feature 010 FR-018): each node with an expand button when
 * it has children and a button that opens it, showing its number of direct attributes. Decorative
 * icons: a folder (open or closed) for nodes with children, a tag for nodes without them.
 */
export function TaxonomyTree({ roots, selectedId, expanded, onToggle, onSelect }: Props) {
  function renderNodes(nodes: TreeNode[], nested: boolean) {
    return (
      <ul className={nested ? styles.nested : styles.tree}>
        {nodes.map((node) => {
          const isOpen = expanded.has(node.systemId)
          const isSelected = selectedId === node.systemId
          const icon = node.children.length === 0 ? 'tag' : isOpen ? 'folder-open' : 'folder'
          return (
            <li key={node.systemId}>
              <div className={styles.row}>
                {node.children.length > 0 ? (
                  <button
                    type="button"
                    className={styles.toggle}
                    aria-expanded={isOpen}
                    aria-label={`${isOpen ? 'Ocultar' : 'Mostrar'} hijos de ${node.name}`}
                    onClick={() => onToggle(node.systemId)}
                  >
                    <Icon name={isOpen ? 'chevron-down' : 'chevron-right'} size={16} />
                  </button>
                ) : (
                  <span className={styles.spacer} aria-hidden="true" />
                )}
                <button
                  type="button"
                  className={`${styles.node} ${isSelected ? styles.selected : ''}`}
                  aria-pressed={isSelected}
                  aria-label={`${node.name}, ${directLabel(node.directAttributeCount)}`}
                  onClick={() => onSelect(node.systemId)}
                >
                  <span className={styles.icon} data-icon={icon}>
                    <Icon name={icon} size={20} />
                  </span>
                  <span className={styles.name}>{node.name}</span>
                  <span className={styles.count}>{node.directAttributeCount}</span>
                </button>
              </div>
              {isOpen && node.children.length > 0 && renderNodes(node.children, true)}
            </li>
          )
        })}
      </ul>
    )
  }

  return renderNodes(roots, false)
}
