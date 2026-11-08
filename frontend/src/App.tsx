import './App.css'
import { AppShell } from './components/layout/AppShell'
import { NAVIGATION_ENTRIES, useCurrentPath } from './navigation'
import { ComponentGalleryPage } from './pages/ComponentGalleryPage'
import { HomePage } from './pages/HomePage'

function renderPage(path: string) {
  switch (path) {
    case '/componentes':
      return <ComponentGalleryPage />
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
