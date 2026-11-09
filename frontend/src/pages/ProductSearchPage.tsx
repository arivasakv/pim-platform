import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  ProductSearchError,
  searchProductsByEan,
  type Business,
  type ProductSummary,
} from '../api/productsApi'
import { Button, Card, Notification, PageHeader, StatusBadge, TextField } from '../components/ui'
import { EAN_MESSAGES, validateEan, type EanErrorCode } from '../products/ean'
import styles from './ProductSearchPage.module.css'

const BUSINESS_NAMES: Record<Business, string> = {
  LIVERPOOL: 'Liverpool',
  SUBURBIA: 'Suburbia',
}

type SearchState =
  | { status: 'idle' }
  | { status: 'searching' }
  | { status: 'found'; items: ProductSummary[] }
  | { status: 'notFound' }
  | { status: 'error' }

/**
 * Checks whether a product already exists before a Mercaderías supplier proposes it
 * (spec 002). The search runs in the backend; this page only validates for instant feedback
 * and shows the result.
 */
export function ProductSearchPage() {
  const [value, setValue] = useState('')
  const [fieldError, setFieldError] = useState<EanErrorCode | null>(null)
  const [state, setState] = useState<SearchState>({ status: 'idle' })
  const lastEanRef = useRef<string | null>(null)
  const requestRef = useRef<AbortController | null>(null)

  // Cancel a request still running when the page is left.
  useEffect(() => () => requestRef.current?.abort(), [])

  async function runSearch(ean: string) {
    // Only the latest search may update the page (spec edge case).
    requestRef.current?.abort()
    const request = new AbortController()
    requestRef.current = request
    lastEanRef.current = ean
    setState({ status: 'searching' })

    try {
      const result = await searchProductsByEan(ean, request.signal)
      if (request.signal.aborted) return
      setState(result.total > 0 ? { status: 'found', items: result.items } : { status: 'notFound' })
    } catch (error) {
      if (request.signal.aborted) return
      if (error instanceof ProductSearchError && error.kind === 'validation' && error.codes[0]) {
        setFieldError(error.codes[0])
        setState({ status: 'idle' })
        return
      }
      setState({ status: 'error' })
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validation = validateEan(value)
    if (!validation.ok) {
      requestRef.current?.abort()
      setFieldError(validation.code)
      setState({ status: 'idle' })
      return
    }
    setFieldError(null)
    void runSearch(validation.ean)
  }

  function handleRetry() {
    if (lastEanRef.current) void runSearch(lastEanRef.current)
  }

  return (
    <>
      <PageHeader
        title="Buscar producto"
        description="Revisa si el producto ya existe antes de proponerlo. Busca por EAN."
      />

      <Card>
        <form className={styles.form} noValidate onSubmit={handleSubmit}>
          <div className={styles.field}>
            <TextField
              id="ean"
              label="EAN"
              type="search"
              inputMode="numeric"
              autoComplete="off"
              maxLength={20}
              hint="8, 12, 13 o 14 dígitos"
              value={value}
              onChange={(event) => setValue(event.target.value)}
              error={fieldError ? EAN_MESSAGES[fieldError] : undefined}
            />
          </div>
          <Button type="submit" icon="search">
            Buscar
          </Button>
        </form>
      </Card>

      <div className={styles.results} aria-live="polite">
        {state.status === 'searching' && <p className={styles.searching}>Buscando…</p>}

        {state.status === 'notFound' && (
          <Notification variant="success">
            No encontramos un producto con este EAN. Puedes continuar con una propuesta nueva.
          </Notification>
        )}

        {state.status === 'error' && (
          <Notification variant="error">
            <div className={styles.errorContent}>
              <span>No se pudo conectar con el servidor. Inténtalo de nuevo.</span>
              <Button variant="secondary" onClick={handleRetry}>
                Reintentar
              </Button>
            </div>
          </Notification>
        )}

        {state.status === 'found' && (
          <>
            <Notification variant="warning">
              Ya existe un producto con este EAN. En Mercaderías no se crea otra vez un producto
              existente.
            </Notification>
            {state.items.map((product) => (
              <Card
                key={`${product.business}-${product.sku}`}
                title={product.title}
                actions={
                  <StatusBadge variant="neutral">{BUSINESS_NAMES[product.business]}</StatusBadge>
                }
              >
                <dl className={styles.details}>
                  <div>
                    <dt>SKU</dt>
                    <dd>{product.sku}</dd>
                  </div>
                  <div>
                    <dt>EAN</dt>
                    <dd>{product.ean}</dd>
                  </div>
                  <div>
                    <dt>Marca</dt>
                    <dd>{product.brand}</dd>
                  </div>
                </dl>
              </Card>
            ))}
          </>
        )}
      </div>
    </>
  )
}
