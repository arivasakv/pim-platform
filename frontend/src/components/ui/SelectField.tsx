import type { SelectHTMLAttributes } from 'react'
import { Icon } from './Icon'
import styles from './FormField.module.css'

export type SelectOption = {
  code: string
  label: string
}

interface SelectFieldProps extends Omit<
  SelectHTMLAttributes<HTMLSelectElement>,
  'className' | 'style' | 'onChange' | 'value'
> {
  id: string
  label: string
  options: SelectOption[]
  value: string
  onChange: (code: string) => void
  /** Text of the empty first option; without it no empty option is shown. */
  placeholder?: string
  hint?: string
  error?: string
}

export function SelectField({
  id,
  label,
  options,
  value,
  onChange,
  placeholder,
  hint,
  error,
  ...selectProps
}: SelectFieldProps) {
  const hintId = `${id}-ayuda`
  const errorId = `${id}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <select
        id={id}
        className={`${styles.input} ${error ? styles.inputError : ''}`}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        {...selectProps}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.code} value={option.code}>
            {option.label}
          </option>
        ))}
      </select>
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
