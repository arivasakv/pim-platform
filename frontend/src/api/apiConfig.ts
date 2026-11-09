/**
 * Builds the full URL of a backend endpoint from the environment configuration
 * (VITE_API_BASE_URL, see frontend/.env.example). Returns null when the base URL is not
 * configured, so callers can report the backend as unavailable without making a request.
 */
export function buildApiUrl(path: string): string | null {
  const baseUrl = import.meta.env.VITE_API_BASE_URL?.trim().replace(/\/+$/, '')
  return baseUrl ? `${baseUrl}${path}` : null
}
