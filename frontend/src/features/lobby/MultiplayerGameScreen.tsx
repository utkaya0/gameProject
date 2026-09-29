import { useEffect, useRef, useState } from 'react'
import { getLobbyGame, readyLobbyGame, saveLobbyColorDraft, submitLobbyGuess, type MultiplayerGame } from './api'
import { useColorDraft } from '../color-game/useColorDraft'
import './MultiplayerGameScreen.css'

const validColor = /^#[0-9A-F]{6}$/
const score = (value: number) => value.toFixed(2)

export default function MultiplayerGameScreen({ code, name, initial, onHome, onLeave, onReturnToLobby, onRematch, canRematch, leaving }: {
  code: string; name: string; initial: MultiplayerGame | null
  onHome: () => void; onLeave: () => void; onReturnToLobby: () => void
  onRematch: () => void; canRematch: boolean; leaving: boolean
}) {
  const [game, setGame] = useState<MultiplayerGame | null>(initial)
  const [previewColor, setPreviewColor] = useState('#808080')
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(Date.now())
  const [serverOffset, setServerOffset] = useState(0)
  const activeRound = useRef('')

  useEffect(() => {
    if (!game) return
    const key = `${game.id}:${game.currentRound}`
    if (activeRound.current === key) return
    activeRound.current = key
    const restoredColor = game.yourDraft && validColor.test(game.yourDraft) ? game.yourDraft : '#808080'
    setPreviewColor(restoredColor)
  }, [game?.id, game?.currentRound, game?.yourDraft])

  useEffect(() => {
    const controller = new AbortController()
    let stopped = false
    let timer: ReturnType<typeof setTimeout>
    const sync = async () => {
      try {
        const next = await getLobbyGame(code, controller.signal)
        if (!stopped) { setServerOffset(Date.parse(next.serverTime) - Date.now()); setGame(next); setMessage(null) }
      } catch (error) {
        if (!stopped && !controller.signal.aborted) setMessage(error instanceof Error ? error.message : 'Bağlantı kurulamadı.')
      }
      if (!stopped) timer = setTimeout(sync, 400)
    }
    timer = setTimeout(sync, 0)
    return () => { stopped = true; clearTimeout(timer); controller.abort() }
  }, [code])

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 100)
    return () => clearInterval(timer)
  }, [])

  const remaining = game?.phaseEndsAt ? Math.max(0, Date.parse(game.phaseEndsAt) - (now + serverOffset)) : 0
  const currentResult = game?.revealedRounds.find(round => round.roundNumber === game.currentRound)
  const self = game?.players.find(player => player.displayName === name)
  const readyCount = game?.players.filter(player => player.ready).length ?? 0
  const draftStatus = useColorDraft(code, game?.currentRound, game?.phase,
    previewColor, true, game?.yourSubmissionReceived ?? false, saveLobbyColorDraft)
  const action = async (run: () => Promise<MultiplayerGame>) => {
    setBusy(true)
    setMessage(null)
    try { const next = await run(); setServerOffset(Date.parse(next.serverTime) - Date.now()); setGame(next) }
    catch (error) { setMessage(error instanceof Error ? error.message : 'İşlem tamamlanamadı.') }
    finally { setBusy(false) }
  }

  return <div className="multi-shell">
    <header className="multi-header">
      <button className="multi-brand" type="button" onClick={onHome} aria-label="Project ana sayfa">Project<span>.</span></button>
      <div className="multi-header__actions"><span>LOBİ {code}</span></div>
    </header>
    <main className="multi-main">
      {!game ? <p role="status">Maç yükleniyor…</p> : <>
        <div className="multi-heading"><span>COLOR · ÇOK OYUNCULU</span><h1>{game.status === 'FINISHED' ? 'Final sıralaması' : `Raund ${game.currentRound} / ${game.totalRounds}`}</h1></div>
        {game.status === 'FINISHED' ? <section className="multi-card multi-card--with-back">
          <button className="panel-back" type="button" onClick={onReturnToLobby}>← Lobi</button>
          <ol className="multi-ranking">{[...game.players].sort((a, b) => b.totalScore - a.totalScore || a.displayName.localeCompare(b.displayName)).map(player =>
            <li key={player.displayName}><strong>{player.displayName}</strong><span>{score(player.totalScore)} / {game.totalRounds * 10}</span></li>)}</ol>
          <div className="multi-final-actions">
            <button type="button" onClick={onReturnToLobby}>Lobiye dön</button>
            {canRematch && <button type="button" onClick={onRematch} disabled={leaving}>Rövanş başlat</button>}
            <button type="button" onClick={onLeave} disabled={leaving}>Lobiden ayrıl</button>
          </div>
        </section> : <div className="multi-grid">
          <section className="multi-card multi-stage multi-card--with-back">
            <button className="panel-back" type="button" onClick={onLeave} disabled={leaving}>← Lobiden ayrıl</button>
            <span className="multi-label">{game.phase === 'PREVIEW' ? 'HEDEF RENK' : game.phase === 'REVEAL' ? 'RAUND SONUCU' : 'SENİN TAHMİNİN'}</span>
            {game.phase === 'PREVIEW' && game.gameData?.targetColor && <div className="multi-swatch" style={{ background: game.gameData.targetColor }} aria-label="Hedef renk" />}
            {(game.phase === 'TRANSITION' || game.phase === 'INPUT') && <div className="multi-guess-preview">
              <div className="multi-guess-preview__color" style={{ backgroundColor: previewColor }} role="img" aria-label="Seçtiğin renk" />
              <p>Hedef renk gizlendi. Aklındaki tonu seç.</p>
            </div>}
            {game.phase === 'REVEAL' && <div className="multi-result">
              <div className="multi-swatch" style={{ background: game.gameData?.targetColor }} aria-label="Hedef renk" />
              <h2>Bu raundun puanları</h2>
              <ul>{currentResult?.scores.map(row => <li key={row.displayName}><span>{row.displayName}</span><strong>{score(row.score)} / 10</strong></li>)}</ul>
            </div>}
          </section>
          <section className="multi-card multi-controls">
            <span className="multi-label">{game.phase === 'PREVIEW' ? 'İNCELE' : game.phase === 'REVEAL' ? 'DEVAM' : 'TAHMİN'}</span>
            {game.phaseEndsAt && <p className="multi-timer">{(remaining / 1000).toFixed(1)} <small>sn</small></p>}
            {game.phase === 'PREVIEW' && <p>Rengi dikkatle incele. Birazdan gizlenecek.</p>}
            {(game.phase === 'TRANSITION' || game.phase === 'INPUT') && <>
              <label htmlFor="multi-color">Renk seç</label>
              <input id="multi-color" type="color" value={previewColor} onChange={event => setPreviewColor(event.target.value.toUpperCase())} disabled={game.yourSubmissionReceived} />
              <button type="button" disabled={busy || game.phase !== 'INPUT' || remaining <= 0 || game.yourSubmissionReceived} onClick={() => void action(() => submitLobbyGuess(code, game.currentRound, previewColor))}>
                {game.yourSubmissionReceived ? 'Tahmin gönderildi' : 'Tahminimi gönder'}
              </button>
              {!game.yourSubmissionReceived && <p role="status">{draftStatus === 'saved'
                ? 'Seçili renk kaydedildi. Süre dolarsa otomatik cevap olur.'
                : draftStatus === 'error' ? 'Renk kaydedilemedi; bağlantı düzelince yeniden denenecek.'
                  : 'Seçili renk kaydediliyor…'}</p>}
              {game.yourSubmissionReceived && <p>Diğer oyuncular bekleniyor.</p>}
            </>}
            {game.phase === 'REVEAL' && <>
              <p>{readyCount} / {game.players.length} oyuncu hazır.</p>
              <ul className="multi-ready">{game.players.map(player => <li key={player.displayName}>{player.displayName}: {player.ready ? 'Hazır' : 'Bekliyor'}</li>)}</ul>
              <button type="button" disabled={busy || self?.ready} onClick={() => void action(() => readyLobbyGame(code))}>{self?.ready ? 'Hazırsın' : 'Devam'}</button>
              <p>İlk hazır oyuncudan 15 saniye sonra sonraki raund başlar.</p>
            </>}
          </section>
        </div>}
      </>}
      {message && <p className="multi-error" role="alert">{message}</p>}
    </main>
  </div>
}
