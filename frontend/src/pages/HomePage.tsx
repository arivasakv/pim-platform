import { useEffect, useState } from 'react'
import { buildApiUrl } from '../api/apiConfig'
import { Card, Notification, PageHeader } from '../components/ui'
import type { NotificationVariant } from '../components/ui'

// Existing backend endpoint path, unchanged. The base URL is environment configuration (R-15).
const HELLO_PATH = '/api/v1/hello'

type ConnectionStatus = 'checking' | 'connected' | 'unavailable'

// Spanish messages written by the frontend. The backend's own (English) text is never shown (R-13).
const STATUS_MESSAGES: Record<ConnectionStatus, { variant: NotificationVariant; text: string }> = {
  checking: { variant: 'info', text: 'Verificando conexión con el servidor…' },
  connected: { variant: 'success', text: 'Conexión con el servidor establecida.' },
  unavailable: { variant: 'error', text: 'No se pudo conectar con el servidor.' },
}

export function HomePage() {
  const helloUrl = buildApiUrl(HELLO_PATH)
  // Without a configured backend URL there is nothing to check: report it as unavailable.
  const [status, setStatus] = useState<ConnectionStatus>(helloUrl ? 'checking' : 'unavailable')

  useEffect(() => {
    if (!helloUrl) {
      console.error(
        'VITE_API_BASE_URL is not set. Copy frontend/.env.example to frontend/.env and restart the dev server.',
      )
      return
    }

    // Ignore a late answer if the page was left before the request finished.
    let ignore = false

    fetch(helloUrl)
      .then((response) => {
        if (!ignore) setStatus(response.ok ? 'connected' : 'unavailable')
      })
      .catch(() => {
        if (!ignore) setStatus('unavailable')
      })

    return () => {
      ignore = true
    }
  }, [helloUrl])

  const message = STATUS_MESSAGES[status]

  return (
    <>
      <PageHeader title="Inicio" />
      <Card title="Estado del servidor">
        <Notification variant={message.variant}>{message.text}</Notification>
      </Card>
    </>
  )
}
