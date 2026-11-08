import './App.css'
import { AppShell } from './components/layout/AppShell'
import { NAVIGATION_ENTRIES, useCurrentPath } from './navigation'
import { HomePage } from './pages/HomePage'

function renderPage(path: string) {
  switch (path) {
    case '/':
    default:
      return <HomePage />
  }
}

function App() {
  const path = useCurrentPath()

  return (
    <AppShell entries={NAVIGATION_ENTRIES} activePath={path}>
      {renderPage(path)}
    </AppShell>
  )
}

export default App
