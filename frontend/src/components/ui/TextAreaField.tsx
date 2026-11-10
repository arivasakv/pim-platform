import type { TextareaHTMLAttributes } from 'react'
import { Icon } from './Icon'
import styles from './FormField.module.css'

interface TextAreaFieldProps extends Omit<
  TextareaHTMLAttributes<HTMLTextAreaElement>,
  'className' | 'style'
> {
  id: string
  label: string
  hint?: string
  error?: string
}

export function TextAreaField({
  id,
  label,
  hint,
  error,
  rows = 3,
  ...textAreaProps
}: TextAreaFieldProps) {
  const hintId = `${id}-ayuda`
  const errorId = `${id}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <textarea
        id={id}
        rows={rows}
        className={`${styles.input} ${styles.textArea} ${error ? styles.inputError : ''}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...textAreaProps}
      />
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
