import { useEffect, useState } from 'react'
import { Card, Notification, PageHeader } from '../components/ui'
import type { NotificationVariant } from '../components/ui'

// Existing backend endpoint, unchanged (research R-15).
const BACKEND_HELLO_URL = 'http://localhost:8080/api/v1/hello'

type ConnectionStatus = 'checking' | 'connected' | 'unavailable'

// Spanish messages written by the frontend. The backend's own (English) text is never shown (R-13).
const STATUS_MESSAGES: Record<ConnectionStatus, { variant: NotificationVariant; text: string }> = {
  checking: { variant: 'info', text: 'Verificando conexión con el servidor…' },
  connected: { variant: 'success', text: 'Conexión con el servidor establecida.' },
  unavailable: { variant: 'error', text: 'No se pudo conectar con el servidor.' },
}

export function HomePage() {
  const [status, setStatus] = useState<ConnectionStatus>('checking')

  useEffect(() => {
    // Ignore a late answer if the page was left before the request finished.
    let ignore = false

    fetch(BACKEND_HELLO_URL)
      .then((response) => {
        if (!ignore) setStatus(response.ok ? 'connected' : 'unavailable')
      })
      .catch(() => {
        if (!ignore) setStatus('unavailable')
      })

    return () => {
      ignore = true
    }
  }, [])

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
