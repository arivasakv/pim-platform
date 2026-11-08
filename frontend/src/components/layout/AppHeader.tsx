/** Application header. Internal tool: no search, user menu, cart or promotions (FR-006). */
export function AppHeader() {
  return (
    <header className="app-header">
      {/* Logo slot: stays empty until an official asset is supplied (FR-007). */}
      <span className="app-header__logo" aria-hidden="true" />
      <span className="app-header__title">Plataforma PIM</span>
    </header>
  )
}
