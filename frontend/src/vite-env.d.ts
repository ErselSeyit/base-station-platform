/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string
  /**
   * Set to 'true' to run the UI against canned data with no backend.
   * Declared explicitly: without it, reads fall through Vite's
   * `[key: string]: any` index signature and are unchecked.
   */
  readonly VITE_MOCK?: 'true' | 'false'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

