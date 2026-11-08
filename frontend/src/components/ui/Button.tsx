import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { Icon, type IconName } from './Icon'
import styles from './Button.module.css'

interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className' | 'style'> {
  variant?: 'primary' | 'secondary'
  /** Optional icon shown before the text. */
  icon?: IconName
  children: ReactNode
}

export function Button({
  variant = 'primary',
  icon,
  type = 'button',
  children,
  ...buttonProps
}: ButtonProps) {
  return (
    <button type={type} className={`${styles.button} ${styles[variant]}`} {...buttonProps}>
      {icon && <Icon name={icon} size={16} />}
      {children}
    </button>
  )
}
