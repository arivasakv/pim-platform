import { EAN_ERROR_CODES, type EanErrorCode } from '../products/ean'
import { buildApiUrl } from './apiConfig'

// Shapes from specs/002-existing-product-search/contracts/product-search.openapi.yaml.
export type Business = 'LIVERPOOL' | 'SUBURBIA'

export type ProductSummary = {
  sku: string
  ean: string
  title: string
  brand: string
  business: Business
}

export type ProductCollection = {
  items: ProductSummary[]
  total: number
}

/**
 * Why a search could not return results: the backend rejected the EAN (`validation`, with its
 * error codes) or could not be reached or answered with an unexpected error (`unavailable`).
 */
export class ProductSearchError extends Error {
  readonly kind: 'validation' | 'unavailable'
  readonly codes: EanErrorCode[]

  constructor(kind: 'validation' | 'unavailable', codes: EanErrorCode[] = []) {
    super(kind === 'validation' ? 'El EAN no es válido.' : 'No se pudo conectar con el servidor.')
    this.name = 'ProductSearchError'
    this.kind = kind
    this.codes = codes
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

/** Reads the error codes from an RFC 9457 Problem Details body (docs/decisions/0001). */
async function readErrorCodes(response: Response): Promise<EanErrorCode[]> {
  try {
    const problem = (await response.json()) as { errors?: Array<{ code?: unknown }> }
    return (problem.errors ?? [])
      .map((error) => error.code)
      .filter((code): code is EanErrorCode => EAN_ERROR_CODES.includes(code as EanErrorCode))
  } catch {
    return []
  }
}

/**
 * Finds existing Mercaderías products with exactly this EAN (`GET /api/v1/products?ean=`).
 * An abort (a newer search started) is rethrown unchanged so callers can ignore it.
 */
export async function searchProductsByEan(
  ean: string,
  signal?: AbortSignal,
): Promise<ProductCollection> {
  const url = buildApiUrl(`/api/v1/products?ean=${encodeURIComponent(ean)}`)
  if (!url) {
    throw new ProductSearchError('unavailable')
  }

  let response: Response
  try {
    response = await fetch(url, { signal, headers: { Accept: 'application/json' } })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new ProductSearchError('unavailable')
  }

  if (response.status === 400) {
    throw new ProductSearchError('validation', await readErrorCodes(response))
  }
  if (!response.ok) {
    throw new ProductSearchError('unavailable')
  }
  return (await response.json()) as ProductCollection
}
