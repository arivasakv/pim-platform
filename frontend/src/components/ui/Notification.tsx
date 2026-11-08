import type { ReactNode } from 'react'
import { Icon } from './Icon'
import styles from './Notification.module.css'

export type NotificationVariant = 'success' | 'warning' | 'error' | 'info'

interface NotificationProps {
  variant: NotificationVariant
  title?: string
  children: ReactNode
  /** When set, a close button is shown and calls this function. */
  onDismiss?: () => void
}

export function Notification({ variant, title, children, onDismiss }: NotificationProps) {
  return (
    <div
      className={`${styles.notification} ${styles[variant]}`}
      role={variant === 'error' ? 'alert' : 'status'}
    >
      <span className={styles.icon}>
        <Icon name={variant} />
      </span>
      <div className={styles.content}>
        {title && <p className={styles.title}>{title}</p>}
        <div>{children}</div>
      </div>
      {onDismiss && (
        <button
          type="button"
          className={styles.closeButton}
          aria-label="Cerrar notificación"
          onClick={onDismiss}
        >
          <Icon name="close" size={16} />
        </button>
      )}
    </div>
  )
}
