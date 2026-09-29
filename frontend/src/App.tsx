import { useEffect, useState } from 'react'
import ColorMemoryGame from './features/color-game/ColorMemoryGame'
import LobbyScreen from './features/lobby/LobbyScreen'
import { clearActiveGameId } from './features/single-game/activeGame'
import { leaveLobby } from './features/lobby/api'

type Page = 'home' | 'color' | 'lobby'
type AppPath = '/' | '/color' | '/color/lobby'

function pageFromPath(pathname: string): Page {
  const path = pathname.replace(/\/+$/, '') || '/'
  if (path === '/color/lobby') return 'lobby'
  if (path === '/color') return 'color'
  return 'home'
}

function abandonGamesForHome(): void {
  clearActiveGameId()
  let lobbyCode: string | null = null
  try {
    lobbyCode = sessionStorage.getItem('project:lobby-code')
    sessionStorage.removeItem('project:lobby-code')
  } catch {
    // Navigation still works when browser storage is unavailable.
  }
  if (lobbyCode) void leaveLobby(lobbyCode).catch(() => {})
}

function App() {
  const [page, setPage] = useState<Page>(() => pageFromPath(window.location.pathname))

  useEffect(() => {
    if (pageFromPath(window.location.pathname) === 'home') abandonGamesForHome()
    const handleHistory = () => {
      const next = pageFromPath(window.location.pathname)
      if (next === 'home') abandonGamesForHome()
      setPage(next)
    }
    window.addEventListener('popstate', handleHistory)
    return () => window.removeEventListener('popstate', handleHistory)
  }, [])

  const navigate = (path: AppPath) => {
    if (path === '/') abandonGamesForHome()
    if (window.location.pathname !== path) window.history.pushState(null, '', path)
    setPage(pageFromPath(path))
  }

  if (page === 'color') return <ColorMemoryGame onBack={() => navigate('/')} onLobby={() => navigate('/color/lobby')} />
  if (page === 'lobby') return <LobbyScreen onBack={() => navigate('/color')} onHome={() => navigate('/')} />

  return (
    <div className="home-shell">
      <header className="home-header">
        <button className="home-logo" type="button" onClick={() => navigate('/')} aria-label="Project ana sayfa">Project<span>.</span></button>
        <span className="home-header__label">Oyunlar</span>
      </header>
      <main className="home-main">
        <div className="home-intro">
          <h1>Oyunlar</h1>
          <p>Oynamak için bir oyun seç.</p>
        </div>
        <section className="games-section" aria-label="Oyunlar">
          <button className="game-card game-card--color" type="button" onClick={() => navigate('/color')}>
            <span className="game-card__preview game-card__preview--color" aria-hidden="true"><i /><i /><i /><i /></span>
            <span className="game-card__content"><strong>Color</strong><span>Renk hafızası oyunu · Tek veya çok oyunculu</span></span>
            <span className="game-card__arrow" aria-hidden="true">↗</span>
          </button>
        </section>
      </main>
    </div>
  )
}

export default App
