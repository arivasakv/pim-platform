import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildApiUrl } from './apiConfig'

describe('buildApiUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('joins the configured base URL and the path', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://servidor.ejemplo:9090')
    expect(buildApiUrl('/api/v1/hello')).toBe('http://servidor.ejemplo:9090/api/v1/hello')
  })

  it('removes trailing slashes from the base URL', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://servidor.ejemplo:9090//')
    expect(buildApiUrl('/api/v1/hello')).toBe('http://servidor.ejemplo:9090/api/v1/hello')
  })

  it('returns null when the base URL is empty', () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    expect(buildApiUrl('/api/v1/hello')).toBeNull()
  })

  it('returns null when the base URL is only spaces', () => {
    vi.stubEnv('VITE_API_BASE_URL', '   ')
    expect(buildApiUrl('/api/v1/hello')).toBeNull()
  })
})
