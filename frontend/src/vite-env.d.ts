/// <reference types="vite/client" />

// Environment variables used by the frontend. See .env.example.
interface ImportMetaEnv {
  /** Base URL of the PIM backend, e.g. http://localhost:8080 */
  readonly VITE_API_BASE_URL?: string
  /**
   * Development stand-in for the user's permissions until authentication exists (feature 004,
   * research R-07). Comma-separated codes, e.g. ATTRIBUTE_CREATE. Sent as X-Pim-Permissions.
   */
  readonly VITE_DEV_PERMISSIONS?: string
  /**
   * Development stand-in: object types for which the user may give manual system IDs, e.g.
   * ATTRIBUTE (feature 003). Sent as X-Pim-Manual-Id-Permissions.
   */
  readonly VITE_DEV_MANUAL_ID_PERMISSIONS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
