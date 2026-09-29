import { useCallback, useEffect, useRef, useState } from 'react'
import { clearActiveGameId, readActiveGameId, saveActiveGameId } from '../single-game/activeGame'
import {
  GameApiError,
  continueSingleGame,
  getSingleGame,
  saveSingleColorDraft,
  startSingleGame,
  submitColorGuess,
  type RoundResult,
  type SingleGame,
} from '../single-game/api'
import { useColorDraft } from './useColorDraft'
import './ColorMemoryGame.css'

const INITIAL_COLOR = '#808080'
const HEX_COLOR_PATTERN = /^#[0-9A-F]{6}$/

function scoreText(score: number): string {
  return score.toFixed(2)
}

function secondsText(milliseconds: number): string {
  return (Math.max(0, milliseconds) / 1000).toFixed(1)
}

function errorText(error: unknown): string {
  if (error instanceof GameApiError) {
    if (error.code === 'SUBMISSION_WINDOW_CLOSED') return 'Tahmin süresi doldu. Sonuç için bir an bekle.'
    if (error.code === 'GAME_NOT_FOUND') return 'Oyun bulunamadı. Yeni bir oyun başlatabilirsin.'
    if (error.code === 'SUBMISSION_CONFLICT') return 'Bu raund için tahminini zaten gönderdin.'
    if (error.code === 'ROUND_NOT_READY') return 'Raund sonucu henüz hazır değil.'
    return error.message
  }
  return 'Bağlantı kurulamadı. Lütfen yeniden dene.'
}

function ColorTile({ color, label, empty = false }: { color?: string; label: string; empty?: boolean }) {
  return (
    <div className="color-tile">
      <div className={`color-tile__swatch${empty ? ' color-tile__swatch--empty' : ''}`} style={color ? { backgroundColor: color } : undefined} />
      <div className="color-tile__caption">
        <span>{label}</span>
      </div>
    </div>
  )
}

function FinalResult({ round }: { round: RoundResult }) {
  const { targetColor, guessedColor } = round.gameData
  return (
    <li className="result-row">
      <span className="result-row__number">{round.roundNumber}</span>
      <div className="result-row__colors" aria-label={guessedColor ? 'Hedef ve tahmin renkleri' : 'Hedef renk; tahmin yapılmadı'}>
        <span className="result-dot" style={{ backgroundColor: targetColor }} title="Hedef renk" />
        <span className={`result-dot${guessedColor ? '' : ' result-dot--empty'}`} style={guessedColor ? { backgroundColor: guessedColor } : undefined} title={guessedColor ? 'Tahmin edilen renk' : 'Tahmin yapılmadı'} />
      </div>
      <strong>{scoreText(round.score)} <small>/ 10</small></strong>
    </li>
  )
}

function ColorMemoryGame({ onBack, onLobby }: { onBack: () => void; onLobby: () => void }) {
  const [game, setGame] = useState<SingleGame | null>(null)
  const [selectedColor, setSelectedColor] = useState(INITIAL_COLOR)
  const [restoring, setRestoring] = useState(() => !!readActiveGameId())
  const [restoreFailed, setRestoreFailed] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const [serverOffsetMs, setServerOffsetMs] = useState(0)
  const [starting, setStarting] = useState(false)
  const [countdownStep, setCountdownStep] = useState<'1' | '2' | 'GO' | null>(null)
  const [sending, setSending] = useState(false)
  const [continuing, setContinuing] = useState(false)
  const [submittedKey, setSubmittedKey] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const inFlightKey = useRef<string | null>(null)
  const lastServerTime = useRef(0)
  const activeRoundKey = useRef<string | null>(null)
  const colorPickerRef = useRef<HTMLInputElement>(null)
  const continueButtonRef = useRef<HTMLButtonElement>(null)
  const replayButtonRef = useRef<HTMLButtonElement>(null)

  const applySnapshot = useCallback((next: SingleGame) => {
    if (window.location.pathname.replace(/\/+$/, '') !== '/color') return
    const timestamp = Date.parse(next.serverTime)
    if (lastServerTime.current && timestamp < lastServerTime.current) return
    lastServerTime.current = timestamp
    const key = `${next.id}:${next.currentRound}`
    if (activeRoundKey.current !== key) {
      activeRoundKey.current = key
      const restoredColor = next.yourDraft && HEX_COLOR_PATTERN.test(next.yourDraft) ? next.yourDraft : INITIAL_COLOR
      setSelectedColor(restoredColor)
      setSubmittedKey(null)
      inFlightKey.current = null
      setMessage(null)
    }
    setServerOffsetMs(timestamp - Date.now())
    setNow(Date.now())
    setGame(next)
    if (next.status === 'FINISHED') clearActiveGameId()
    else saveActiveGameId(next.id)
  }, [])

  const restoreGame = useCallback(async (signal?: AbortSignal) => {
    const id = readActiveGameId()
    if (!id) {
      setRestoring(false)
      return
    }
    setRestoring(true)
    setRestoreFailed(false)
    setMessage(null)
    try {
      const snapshot = await getSingleGame(id, signal)
      if (signal?.aborted) return
      if (snapshot.status === 'FINISHED') {
        clearActiveGameId()
        setMessage('Önceki oyun tamamlandı. Yeni bir oyun başlatabilirsin.')
      } else {
        applySnapshot(snapshot)
      }
    } catch (error) {
      if (signal?.aborted) return
      if (error instanceof GameApiError && error.code === 'GAME_NOT_FOUND') {
        clearActiveGameId()
        setMessage('Önceki oyun artık bulunamadı. Yeni bir oyun başlatabilirsin.')
      } else {
        setRestoreFailed(true)
        setMessage('Oyun yüklenemedi. Bağlantıyı kontrol edip yeniden dene.')
      }
    } finally {
      if (!signal?.aborted) setRestoring(false)
    }
  }, [applySnapshot])

  useEffect(() => {
    const controller = new AbortController()
    const timeout = setTimeout(() => void restoreGame(controller.signal), 0)
    return () => {
      clearTimeout(timeout)
      controller.abort()
    }
  }, [restoreGame])

  const gameId = game?.id
  useEffect(() => {
    if (!gameId) return
    const controller = new AbortController()
    let stopped = false
    let timeout: ReturnType<typeof setTimeout>

    async function poll() {
      try {
        const next = await getSingleGame(gameId!, controller.signal)
        if (stopped) return
        applySnapshot(next)
        setMessage((current) => current === 'Bağlantı kesildi. Yeniden deneniyor…' ? null : current)
        if (next.status === 'FINISHED') return
      } catch (error) {
        if (stopped || controller.signal.aborted) return
        if (error instanceof GameApiError && error.code === 'GAME_NOT_FOUND') {
          clearActiveGameId()
          setGame(null)
          setMessage(errorText(error))
          return
        }
        setMessage('Bağlantı kesildi. Yeniden deneniyor…')
      }
      if (!stopped) timeout = setTimeout(poll, 400)
    }

    timeout = setTimeout(poll, 400)
    return () => {
      stopped = true
      controller.abort()
      clearTimeout(timeout)
    }
  }, [gameId, applySnapshot])

  const isActive = game?.status === 'IN_PROGRESS'
  useEffect(() => {
    if (!isActive) return
    const interval = setInterval(() => setNow(Date.now()), 100)
    return () => clearInterval(interval)
  }, [isActive, gameId])

  useEffect(() => {
    if (game?.status === 'FINISHED') replayButtonRef.current?.focus()
    else if (game?.phase === 'INPUT') colorPickerRef.current?.focus()
    else if (game?.phase === 'REVEAL') continueButtonRef.current?.focus()
  }, [game?.phase, game?.currentRound, game?.status])

  const remainingMs = game?.phaseEndsAt
    ? Math.max(0, Date.parse(game.phaseEndsAt) - (now + serverOffsetMs))
    : 0
  const roundKey = game ? `${game.id}:${game.currentRound}` : null
  const inputOpen = game?.phase === 'INPUT' && remainingMs > 0
  const alreadySubmitted = submittedKey === roundKey
  const draftStatus = useColorDraft(game?.id, game?.currentRound, game?.phase,
    selectedColor, true, alreadySubmitted, saveSingleColorDraft)

  const startGame = useCallback(async () => {
    setStarting(true)
    setMessage(null)
    try {
      const next = await startSingleGame()
      setRestoreFailed(false)
      lastServerTime.current = 0
      applySnapshot(next)
    } catch (error) {
      setMessage(errorText(error))
    } finally {
      setStarting(false)
    }
  }, [applySnapshot])

  useEffect(() => {
    if (!countdownStep) return
    const next = countdownStep === '1' ? '2' : countdownStep === '2' ? 'GO' : null
    const timer = window.setTimeout(() => {
      if (next) setCountdownStep(next)
      else void startGame().finally(() => setCountdownStep(null))
    }, countdownStep === 'GO' ? 650 : 850)
    return () => window.clearTimeout(timer)
  }, [countdownStep, startGame])

  const beginCountdown = () => {
    if (starting || restoring || countdownStep) return
    setMessage(null)
    setCountdownStep('1')
  }

  const submitGuess = useCallback(async () => {
    if (!game || !inputOpen || !roundKey || inFlightKey.current === roundKey || alreadySubmitted) return
    inFlightKey.current = roundKey
    setSending(true)
    setMessage(null)
    try {
      const next = await submitColorGuess(game.id, game.currentRound, selectedColor)
      applySnapshot(next)
      setSubmittedKey(roundKey)
    } catch (error) {
      inFlightKey.current = null
      setMessage(errorText(error))
    } finally {
      setSending(false)
    }
  }, [game, inputOpen, roundKey, alreadySubmitted, selectedColor, applySnapshot])

  const continueRound = async () => {
    if (!game || game.phase !== 'REVEAL' || continuing) return
    setContinuing(true)
    setMessage(null)
    try {
      applySnapshot(await continueSingleGame(game.id))
    } catch (error) {
      setMessage(errorText(error))
    } finally {
      setContinuing(false)
    }
  }

  const currentResult = game?.revealedRounds.find((round) => round.roundNumber === game.currentRound)
  const previewVisible = game?.phase === 'PREVIEW' && remainingMs > 0 && !!game.gameData?.targetColor
  const revealVisible = game?.phase === 'REVEAL' && !!currentResult
  const pickerVisible = game?.phase === 'TRANSITION' || game?.phase === 'INPUT' || (game?.phase === 'PREVIEW' && !previewVisible)

  return (
    <div className="app-shell">
      <div className="ambient ambient--one" aria-hidden="true" />
      <div className="ambient ambient--two" aria-hidden="true" />
      <header className="site-header">
        <button className="brand" type="button" onClick={onBack} aria-label="Project ana sayfa">
          <span className="brand__symbol" aria-hidden="true"><i /><i /><i /><i /></span>
          <span>Project</span>
        </button>
        <nav className="game-nav" aria-label="Oyun menüsü">
          <button className="back-link" type="button" onClick={onLobby}>Lobi</button>
        </nav>
      </header>

      {!game && !countdownStep && (
        <main className="landing landing--centered">
          <section className="landing__panel" aria-labelledby="color-title">
            <button className="panel-back" type="button" onClick={onBack}>← Oyunlar</button>
            <div className="landing__copy">
              <span className="section-label">COLOR</span>
              <h1 id="color-title">Rengi hatırla.</h1>
              <p>Renge bak, kaybolunca hatırladığın tonu seç.</p>
              <div className="landing__actions">
                <button className="button landing__button" type="button" onClick={beginCountdown} disabled={starting || restoring || !!countdownStep}>
                  {restoring ? 'Oyun yükleniyor…' : starting ? 'Oyun hazırlanıyor…' : 'Oyuna başla'} <span aria-hidden="true">→</span>
                </button>
                <button className="lobby-entry" type="button" onClick={onLobby} disabled={restoring || !!countdownStep}>Çok oyunculu <span aria-hidden="true">↗</span></button>
              </div>
              {restoreFailed && <button className="retry-button" type="button" onClick={() => void restoreGame()} disabled={restoring}>Oyuna yeniden bağlan</button>}
            </div>
            <div className="landing__art" aria-hidden="true">
              <div className="memory-visual"><span /><span /><span /></div>
            </div>
          </section>
          {message && <p className="notice landing__notice" role="alert">{message}</p>}
        </main>
      )}

      {countdownStep && !game && <main className="game-screen game-screen--single">
        <button className="panel-back" type="button" onClick={onBack}>← Oyunlar</button>
        <section className="play-panel" aria-label="Color oyunu">
          <div className="play-panel__header">
            <span className="section-label">COLOR</span>
            <div className="play-panel__score"><span>TOPLAM PUAN</span><strong>0.00 <small>/ 50</small></strong></div>
          </div>
          <div className="play-panel__countdown" role="status" aria-live="assertive" aria-label={`Oyun başlıyor: ${countdownStep}`}>
            <span>OYUN BAŞLIYOR</span>
            <strong key={countdownStep}>{countdownStep}</strong>
          </div>
        </section>
      </main>}

      {game && game.status === 'FINISHED' && (
        <main className="final-screen">
          <div className="final-card">
            <button className="panel-back" type="button" onClick={onBack}>← Oyunlar</button>
            <div className="final-card__summary">
              <div className="final-score">{scoreText(game.totalScore)} <small>/ {scoreText(game.totalRounds * 10)}</small></div>
            </div>
            <ol className="results-list">
              {game.revealedRounds.map((round) => <FinalResult key={round.roundNumber} round={round} />)}
            </ol>
            <button ref={replayButtonRef} className="button final-card__replay" type="button" onClick={() => void startGame()} disabled={starting}>
              {starting ? 'Oyun hazırlanıyor…' : 'Yeniden oyna'} <span aria-hidden="true">↗</span>
            </button>
          </div>
          {message && <p className="notice" role="alert">{message}</p>}
        </main>
      )}

      {game && game.status === 'IN_PROGRESS' && (
        <main className="game-screen game-screen--single">
          <button className="panel-back" type="button" onClick={onBack}>← Oyunlar</button>
          <div className="game-heading">
            <div><span className="section-label">COLOR</span></div>
            <div className="score-pill"><span>TOPLAM PUAN</span><strong>{scoreText(game.totalScore)} <small>/ {game.totalRounds * 10}</small></strong></div>
          </div>

          <ol className="round-progress" aria-label="Raund ilerlemesi">
            {Array.from({ length: game.totalRounds }, (_, index) => {
              const number = index + 1
              const state = number < game.currentRound ? 'done' : number === game.currentRound ? 'active' : 'upcoming'
              return <li key={number} className={`round-progress__item round-progress__item--${state}`} aria-label={`Raund ${number}: ${state === 'done' ? 'tamamlandı' : state === 'active' ? 'oynanıyor' : 'bekliyor'}`}>
                <span>{String(number).padStart(2, '0')}</span><i />
              </li>
            })}
          </ol>
          <p className="phase-status" role="status">
            {previewVisible ? 'Hedef renk gösteriliyor. Birazdan gizlenecek.'
              : game.phase === 'REVEAL' ? 'Raund sonucu hazır. Devam düğmesiyle sonraki raunda geçebilirsin.'
                : inputOpen ? 'Tahminini gönderebilirsin. Hedef renk görünmüyor.'
                  : game.phase === 'INPUT' ? 'Tahmin süresi doldu. Sonuç hazırlanıyor.'
                    : 'Hedef renk gizlendi. Tahmin ekranında rengini seçebilirsin.'}
          </p>

          <div className={`game-layout game-layout--${previewVisible ? 'preview' : revealVisible ? 'reveal' : 'guess'}`}>
            <section className="stage-card">
              <div className="stage-card__top">
                <span className="small-label">{previewVisible ? 'HEDEF RENK' : revealVisible ? 'RAUND SONUCU' : 'SENİN TAHMİNİN'}</span>
                <span className="stage-card__round">{String(game.currentRound).padStart(2, '0')} / {String(game.totalRounds).padStart(2, '0')}</span>
              </div>
              {previewVisible && <div className="stage-display stage-display--preview">
                <div className="hero-swatch" role="img" aria-label="Hedef renk" style={{ backgroundColor: game.gameData!.targetColor }} />
                <div className="stage-display__caption"><span>Rengi hatırla</span></div>
              </div>}
              {revealVisible && currentResult && <div className="stage-display stage-display--reveal">
                <div className="reveal-grid">
                  <ColorTile color={currentResult.gameData.targetColor} label="HEDEF" />
                  <ColorTile color={currentResult.gameData.guessedColor} label="TAHMİNİN" empty={!currentResult.gameData.guessedColor} />
                </div>
                <div className="reveal-score"><span>BU RAUNDUN PUANI</span><strong>{scoreText(currentResult.score)} <small>/ 10</small></strong></div>
              </div>}
              {pickerVisible && <div className="stage-display stage-display--hidden stage-display--guess">
                <div className="hidden-orb" style={{ backgroundColor: selectedColor }} aria-hidden="true" />
                <strong>{game.phase === 'INPUT' && !inputOpen ? 'Süre doldu' : 'Rengi tahmin et'}</strong>
                <p>{game.phase === 'INPUT' && !inputOpen ? 'Sonuç hazırlanıyor…' : 'Hedef artık görünmüyor. Aklındaki tonu seç.'}</p>
              </div>}
            </section>

            <aside className="control-card">
              <div className="control-card__top">
                <span className="small-label">{previewVisible ? 'İNCELEME SÜRESİ' : pickerVisible ? 'TAHMİN EKRANI' : 'SONUÇ EKRANI'}</span>
                <span className="live-dot" aria-hidden="true" />
              </div>
              {(previewVisible || game.phase === 'INPUT') && <div aria-live="off" aria-label={`${secondsText(remainingMs)} saniye kaldı`} className={`timer${remainingMs <= 3000 && game.phase === 'INPUT' ? ' timer--urgent' : ''}`}>
                <strong>{secondsText(remainingMs)}</strong><span>sn</span>
              </div>}
              {pickerVisible && game.phase !== 'INPUT' && <div className="timer timer--waiting" aria-hidden="true"><strong>···</strong></div>}
              <div className="control-card__divider" />
              {previewVisible && <div className="instruction">
                <span className="instruction__icon" aria-hidden="true">◎</span>
                <h2>Renge bak.</h2>
              </div>}
              {pickerVisible && <div className="picker-panel picker-panel--guess">
                <label htmlFor="color-picker">HAFIZANDAKİ RENGİ SEÇ</label>
                <div className="picker-panel__input-wrap">
                  <input ref={colorPickerRef} id="color-picker" type="color" value={selectedColor}
                    onChange={event => setSelectedColor(event.target.value.toUpperCase())}
                    disabled={alreadySubmitted || sending || (game.phase === 'INPUT' && !inputOpen)} aria-label="Renk seçici" />
                </div>
                <button className="button button--primary picker-panel__submit" type="button" onClick={() => void submitGuess()} disabled={!inputOpen || alreadySubmitted || sending}>
                  {alreadySubmitted ? 'Tahmin gönderildi' : sending ? 'Gönderiliyor…' : inputOpen ? 'Tahminimi gönder' : game.phase === 'INPUT' ? 'Süre doldu' : 'Tahmin açılıyor…'}
                </button>
                {!alreadySubmitted && (game.phase === 'TRANSITION' || game.phase === 'INPUT') &&
                  <p className="picker-panel__auto" role="status">{draftStatus === 'saved'
                    ? 'Seçili renk kaydedildi. Süre dolarsa otomatik cevap olur.'
                    : draftStatus === 'error' ? 'Renk kaydedilemedi; bağlantı düzelince yeniden denenecek.'
                      : 'Seçili renk kaydediliyor…'}</p>}
              </div>}
              {game.phase === 'REVEAL' && <div className="instruction">
                <span className="instruction__icon instruction__icon--result" aria-hidden="true">✦</span>
                <h2>{currentResult?.gameData.guessedColor ? 'Raund sonucu' : 'Bu raund kaçırıldı'}</h2>
                <button ref={continueButtonRef} className="button button--primary continue-button" type="button" onClick={() => void continueRound()} disabled={continuing}>
                  {continuing ? 'Yükleniyor…' : 'Devam'} <span aria-hidden="true">↗</span>
                </button>
              </div>}
            </aside>
          </div>
          {message && <p className="notice" role="alert">{message}</p>}
        </main>
      )}

    </div>
  )
}

export default ColorMemoryGame
