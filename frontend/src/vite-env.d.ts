/// <reference types="vite/client" />

// Environment variables used by the frontend. See .env.example.
interface ImportMetaEnv {
  /** Base URL of the PIM backend, e.g. http://localhost:8080 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
