import { useId, type ReactNode } from 'react'
import styles from './Card.module.css'

interface CardProps {
  /** Optional section heading; it also names the section for screen readers. */
  title?: string
  actions?: ReactNode
  children: ReactNode
}

export function Card({ title, actions, children }: CardProps) {
  const titleId = useId()
  const hasHeader = Boolean(title || actions)

  return (
    <section className={styles.card} aria-labelledby={title ? titleId : undefined}>
      {hasHeader && (
        <div className={styles.header}>
          {title && (
            <h2 id={titleId} className={styles.title}>
              {title}
            </h2>
          )}
          {actions && <div className={styles.actions}>{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}
