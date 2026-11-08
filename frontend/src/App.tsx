import { useEffect, useState } from 'react'
import './App.css'

function App() {
  const [message, setMessage] = useState('Loading...')

  useEffect(() => {
    fetch('http://localhost:8080/api/v1/hello')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Request failed')
        }
        return response.text()
      })
      .then(setMessage)
      .catch(() => setMessage('Could not connect to backend'))
  }, [])

  return (
    <main>
      <h1>PIM Platform</h1>
      <p>{message}</p>
    </main>
  )
}

export default App