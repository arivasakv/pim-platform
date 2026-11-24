import type { ListUnlinkReview } from '../api/attributesApi'
import styles from './UnlinkReview.module.css'

type UnlinkReviewProps = { review: ListUnlinkReview }

/**
 * «Qué cambiará»: the list an unlink detaches and the taxonomy nodes whose value filters for the
 * attribute it deletes (feature 014 FR-009, UI contract «Review box»).
 */
export function UnlinkReview({ review }: UnlinkReviewProps) {
  const listName = review.listOfValuesName ?? review.listOfValuesId
  const count = review.filteredNodes.length
  return (
    <section className={styles.review} aria-labelledby="desvinculacion-cambios">
      <h3 id="desvinculacion-cambios" className={styles.heading}>
        Qué cambiará
      </h3>
      <p>Se desvinculará la lista «{listName}».</p>
      {count === 0 ? (
        <p>Este atributo no tiene filtros de valores en la taxonomía.</p>
      ) : (
        <>
          <p>
            {count === 1
              ? 'Se eliminarán los filtros de valores de este atributo en 1 nodo de taxonomía:'
              : `Se eliminarán los filtros de valores de este atributo en ${count} nodos de taxonomía:`}
          </p>
          <ul className={styles.nodes}>
            {review.filteredNodes.map((node) => (
              <li key={node.id}>{node.name}</li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
