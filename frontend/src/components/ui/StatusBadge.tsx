import type { ReactNode } from 'react'
import { Icon } from './Icon'
import styles from './StatusBadge.module.css'

export type StatusVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral'

interface StatusBadgeProps {
  variant: StatusVariant
  /** Always name the status in words, so it never depends on color alone. */
  children: ReactNode
}

export function StatusBadge({ variant, children }: StatusBadgeProps) {
  return (
    <span className={`${styles.badge} ${styles[variant]}`}>
      {variant !== 'neutral' && <Icon name={variant} size={16} />}
      {children}
    </span>
  )
}
