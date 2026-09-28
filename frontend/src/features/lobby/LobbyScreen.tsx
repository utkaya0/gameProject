import { useEffect, useState, type FormEvent } from 'react'
import { Client } from '@stomp/stompjs'
import { createLobby, getLobby, joinLobby, leaveLobby, rematchLobbyGame, startLobbyGame, LobbyApiError, type LobbySnapshot, type MultiplayerGame } from './api'
import MultiplayerGameScreen from './MultiplayerGameScreen'
import './LobbyScreen.css'

function errorText(error: unknown): string {
  if (error instanceof LobbyApiError) {
    switch (error.code) {
      case 'INVALID_DISPLAY_NAME': return 'Takma ad 1–32 karakter olmalı.'
      case 'INVALID_CODE': return 'Lobi kodu 6 harf veya rakam olmalı.'
      case 'LOBBY_NOT_FOUND': return 'Bu kodla bir lobi bulunamadı.'
      case 'LOBBY_EXPIRED': return 'Bu lobinin süresi dolmuş.'
      case 'LOBBY_FULL': return 'Lobi dolu.'
      case 'DISPLAY_NAME_TAKEN': return 'Bu takma ad lobide kullanılıyor.'
      case 'NOT_MEMBER': return 'Artık bu lobinin üyesi değilsin.'
      case 'NOT_ENOUGH_PLAYERS': return 'Maç için en az iki oyuncu gerekli.'
      case 'GAME_IN_PROGRESS': return 'Bu lobide bir maç zaten devam ediyor.'
      default: return error.message
    }
  }
  return 'Bağlantı kurulamadı. Lütfen yeniden dene.'
}

function LobbyScreen({ onBack, onHome }: { onBack: () => void; onHome: () => void }) {
  const [lobby, setLobby] = useState<LobbySnapshot | null>(null)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [maxPlayers, setMaxPlayers] = useState(4)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [liveState, setLiveState] = useState<'connecting' | 'live' | 'reconnecting'>('connecting')
  const [startedGame, setStartedGame] = useState<MultiplayerGame | null>(null)
  const [showGame, setShowGame] = useState(true)
  const lobbyCode = lobby?.code

  useEffect(() => {
    const saved = sessionStorage.getItem('project:lobby-code')
    if (!saved) return
    void getLobby(saved).then(setLobby).catch(() => sessionStorage.removeItem('project:lobby-code'))
  }, [])

  useEffect(() => {
    if (lobby && window.location.pathname.replace(/\/+$/, '') === '/color/lobby') {
      sessionStorage.setItem('project:lobby-code', lobby.code)
    }
  }, [lobby])

  useEffect(() => {
    if (lobby?.status === 'IN_GAME' && lobby.gameId) setShowGame(true)
  }, [lobby?.status, lobby?.gameId])

  useEffect(() => {
    if (!lobbyCode) return
    let active = true
    let latestSequence = 0
    const sync = async () => {
      try {
        const next = await getLobby(lobbyCode)
        if (!active) return
        latestSequence = Math.max(latestSequence, next.sequence)
        setLobby((current) => current?.code === lobbyCode && next.sequence >= current.sequence ? next : current)
      } catch (error) {
        if (!active) return
        if (error instanceof LobbyApiError && ['LOBBY_EXPIRED', 'LOBBY_NOT_FOUND', 'NOT_MEMBER'].includes(error.code ?? '')) {
          sessionStorage.removeItem('project:lobby-code')
          setLobby(null)
        }
        setMessage(errorText(error))
      }
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/api/v1/ws`,
      reconnectDelay: 1500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        if (!active) return
        setLiveState('live')
        client.subscribe(`/topic/lobbies/${lobbyCode}`, (frame) => {
          try {
            const event = JSON.parse(frame.body) as { sequence?: number }
            if (typeof event.sequence === 'number' && event.sequence > latestSequence) void sync()
          } catch {
            void sync()
          }
        })
        void sync()
      },
      onWebSocketClose: () => { if (active) { setLiveState('reconnecting'); void sync() } },
      onWebSocketError: () => { if (active) setLiveState('reconnecting') },
      onStompError: () => { if (active) setLiveState('reconnecting') },
    })
    void sync()
    client.activate()
    return () => {
      active = false
      void client.deactivate()
    }
  }, [lobbyCode])

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    setMessage(null)
    try {
      await action()
    } catch (error) {
      if (error instanceof LobbyApiError && ['LOBBY_EXPIRED', 'LOBBY_NOT_FOUND', 'NOT_MEMBER'].includes(error.code ?? '')) {
        sessionStorage.removeItem('project:lobby-code')
        setLobby(null)
      }
      setMessage(errorText(error))
    } finally {
      setBusy(false)
    }
  }

  const handleCreate = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void run(async () => setLobby(await createLobby(name.trim(), maxPlayers)))
  }

  const handleJoin = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void run(async () => setLobby(await joinLobby(code.trim().toUpperCase(), name.trim())))
  }

  const handleRefresh = () => {
    if (!lobby) return
    void run(async () => {
      const next = await getLobby(lobby.code)
      setLobby(next)
    })
  }

  const handleLeave = () => {
    if (!lobby) return
    void run(async () => {
      await leaveLobby(lobby.code)
      sessionStorage.removeItem('project:lobby-code')
      setLobby(null)
      onBack()
    })
  }

  const handleStart = () => {
    if (!lobby) return
    void run(async () => {
      setShowGame(true)
      setStartedGame(await startLobbyGame(lobby.code))
      setLobby(await getLobby(lobby.code))
    })
  }

  const handleRematch = () => {
    if (!lobby) return
    void run(async () => {
      const next = await rematchLobbyGame(lobby.code)
      setStartedGame(next)
      setLobby(await getLobby(lobby.code))
      setShowGame(true)
    })
  }

  const handleCopy = async () => {
    if (!lobby) return
    try {
      await navigator.clipboard.writeText(lobby.code)
      setMessage('Lobi kodu kopyalandı.')
    } catch {
      setMessage('Kod kopyalanamadı. Ekrandaki kodu paylaşabilirsin.')
    }
  }

  if (lobby?.gameId && showGame) return <MultiplayerGameScreen key={lobby.gameId} code={lobby.code} name={lobby.yourDisplayName}
    initial={startedGame?.id === lobby.gameId ? startedGame : null} onHome={onHome} onLeave={handleLeave} leaving={busy}
    canRematch={lobby.yourRole === 'HOST' && lobby.participants.length >= 2} onRematch={handleRematch}
    onReturnToLobby={() => { setShowGame(false); void getLobby(lobby.code).then(setLobby).catch(() => {}) }} />

  return (
    <div className="lobby-shell">
      <header className="lobby-header">
        <button className="lobby-brand" type="button" onClick={onHome} aria-label="Project ana sayfa">Project<span>.</span></button>
        {!lobby && <button type="button" className="lobby-back" onClick={onBack}>← Color</button>}
      </header>
      <main className="lobby-main">
        {!lobby ? <>
          <div className="lobby-intro">
            <span className="lobby-eyebrow">ÇOK OYUNCULU</span>
            <h1>Arkadaşlarınla<br /><em>aynı lobide buluş.</em></h1>
            <p>Bir lobi kur veya arkadaşının gönderdiği kodla katıl.</p>
          </div>
          <div className="lobby-forms">
            <form className="lobby-panel" onSubmit={handleCreate}>
              <span className="lobby-panel__number">01 / LOBİ KUR</span>
              <h2>Yeni lobi</h2>
              <label htmlFor="create-name">Takma adın</label>
              <input id="create-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={32} required placeholder="Örn. Oyuncu1" />
              <label htmlFor="max-players">Oyuncu sınırı</label>
              <select id="max-players" value={maxPlayers} onChange={(event) => setMaxPlayers(Number(event.target.value))}>
                {[2, 3, 4, 5, 6, 7, 8].map((count) => <option key={count} value={count}>{count} oyuncu</option>)}
              </select>
              <button type="submit" disabled={busy || !name.trim()}>Lobi oluştur <span aria-hidden="true">↗</span></button>
            </form>
            <form className="lobby-panel" onSubmit={handleJoin}>
              <span className="lobby-panel__number">02 / KODLA KATIL</span>
              <h2>Mevcut lobi</h2>
              <label htmlFor="join-name">Takma adın</label>
              <input id="join-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={32} required placeholder="Örn. Oyuncu2" />
              <label htmlFor="lobby-code">Lobi kodu</label>
              <input id="lobby-code" value={code} onChange={(event) => setCode(event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))} maxLength={6} minLength={6} required placeholder="ABC123" autoCapitalize="characters" spellCheck={false} />
              <button type="submit" disabled={busy || !name.trim() || code.length !== 6}>Lobiye katıl <span aria-hidden="true">↗</span></button>
            </form>
          </div>
        </> : <>
          <div className="lobby-intro">
            <span className="lobby-eyebrow">LOBİ HAZIR</span>
            <h1>Oyuncular<br /><em>bir araya geliyor.</em></h1>
            <p>Kodu arkadaşlarınla paylaş. Katılan oyuncular listede otomatik görünecek.</p>
          </div>
          <section className="lobby-room" aria-label="Lobi bilgileri">
            <div className="lobby-room__top">
              <div><span>LOBİ KODU</span><strong>{lobby.code}</strong></div>
              <button type="button" className="lobby-secondary" onClick={() => void handleCopy()}>Kodu kopyala</button>
            </div>
            <div className="lobby-room__heading">
              <h2>Oyuncular <small>{lobby.participants.length} / {lobby.maxPlayers}</small></h2>
              <div className="lobby-room__actions">
                <span className={`lobby-live lobby-live--${liveState}`} role="status">{liveState === 'live' ? 'Canlı bağlı' : liveState === 'connecting' ? 'Bağlanıyor…' : 'Yeniden bağlanıyor…'}</span>
                <button type="button" className="lobby-secondary" onClick={handleRefresh} disabled={busy}>Listeyi yenile</button>
              </div>
            </div>
            <ul className="lobby-members">
              {lobby.participants.map((participant, index) => <li key={`${participant.displayName}-${index}`}>
                <span className="lobby-member-icon" aria-hidden="true">{participant.displayName.charAt(0).toUpperCase()}</span>
                <strong>{participant.displayName}</strong>
                {participant.role === 'HOST' && <span className="lobby-host-tag">EV SAHİBİ</span>}
              </li>)}
            </ul>
            <p className="lobby-room__note">{lobby.status === 'FINISHED' ? 'Maç tamamlandı. Ev sahibi en az iki oyuncuyla rövanş başlatabilir.' : 'En az iki oyuncu katıldığında ev sahibi oyunu başlatabilir.'}</p>
            {lobby.gameId && <button type="button" className="lobby-start" onClick={() => setShowGame(true)}>{lobby.status === 'FINISHED' ? 'Final sıralaması' : 'Maça dön'}</button>}
            {lobby.status === 'WAITING' && lobby.yourRole === 'HOST' && <button type="button" className="lobby-start" onClick={handleStart} disabled={busy || lobby.participants.length < 2}>Oyunu başlat</button>}
            {lobby.status === 'FINISHED' && lobby.yourRole === 'HOST' && <button type="button" className="lobby-start" onClick={handleRematch} disabled={busy || lobby.participants.length < 2}>Rövanş başlat</button>}
            <button type="button" className="lobby-leave" onClick={handleLeave} disabled={busy}>Lobiden ayrıl</button>
          </section>
        </>}
        {message && <p className="lobby-notice" role="status">{message}</p>}
      </main>
      <footer className="lobby-footer"><span>Project</span><span>COLOR · ÇOK OYUNCULU</span></footer>
    </div>
  )
}

export default LobbyScreen
