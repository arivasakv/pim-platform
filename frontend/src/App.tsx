import './App.css'
import { AppShell } from './components/layout/AppShell'
import { NAVIGATION_ENTRIES, useCurrentPath } from './navigation'
import { AttributeCreationPage } from './pages/AttributeCreationPage'
import { AttributeGroupsPage } from './pages/AttributeGroupsPage'
import { HomePage } from './pages/HomePage'
import { ListsOfValuesPage } from './pages/ListsOfValuesPage'
import { ProductSearchPage } from './pages/ProductSearchPage'

function renderPage(path: string) {
  switch (path) {
    case '/buscar-producto':
      return <ProductSearchPage />
    case '/crear-atributo':
      return <AttributeCreationPage />
    case '/grupos-de-atributos':
      return <AttributeGroupsPage />
    case '/listas-de-valores':
      return <ListsOfValuesPage />
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
