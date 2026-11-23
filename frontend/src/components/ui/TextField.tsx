import type { InputHTMLAttributes } from 'react'
import { Icon } from './Icon'
import styles from './TextField.module.css'

interface TextFieldProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'className' | 'style' | 'type'
> {
  id: string
  label: string
  type?: 'text' | 'email' | 'number' | 'password' | 'search'
  /** Hides the label visually; screen readers still announce it (used for search boxes). */
  hideLabel?: boolean
  hint?: string
  error?: string
  /** Short text shown inside the field, before the value (e.g. a currency symbol). */
  prefix?: string
}

export function TextField({
  id,
  label,
  type = 'text',
  hideLabel = false,
  hint,
  error,
  prefix,
  ...inputProps
}: TextFieldProps) {
  const hintId = `${id}-ayuda`
  const errorId = `${id}-error`
  // Screen readers read the hint and the error after the label.
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined
  const isSearch = type === 'search'

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={hideLabel ? 'visually-hidden' : styles.label}>
        {label}
      </label>
      <div className={`${styles.control} ${prefix ? styles.controlPrefixed : ''}`}>
        {prefix && (
          <span className={styles.prefix} aria-hidden="true">
            {prefix}
          </span>
        )}
        {isSearch && (
          <span className={styles.searchIcon}>
            <Icon name="search" size={16} />
          </span>
        )}
        <input
          id={id}
          type={type}
          className={`${styles.input} ${isSearch ? styles.inputSearch : ''} ${prefix ? styles.inputPrefixed : ''} ${error ? styles.inputError : ''}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...inputProps}
        />
      </div>
      {hint && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className={styles.error}>
          <Icon name="error" size={16} />
          {error}
        </p>
      )}
    </div>
  )
}
