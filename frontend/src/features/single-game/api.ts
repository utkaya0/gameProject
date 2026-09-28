import { creationKey, clearCreationKey } from '../guest/creationKey'

export type GamePhase = 'PREVIEW' | 'TRANSITION' | 'INPUT' | 'REVEAL' | 'COMPLETED'

export type ColorGameData = {
  targetColor: string
  guessedColor?: string
  colorDistance?: number
}

export type RoundResult = {
  roundNumber: number
  score: number
  responseTimeMs?: number
  gameData: ColorGameData
}

export type SingleGame = {
  id: string
  gameType: 'COLOR_GUESS'
  mode: 'SINGLE_PLAYER'
  status: 'IN_PROGRESS' | 'FINISHED'
  configVersion: string
  currentRound: number
  totalRounds: number
  phase: GamePhase
  serverTime: string
  phaseEndsAt?: string
  gameData?: ColorGameData
  yourDraft?: string
  totalScore: number
  revealedRounds: RoundResult[]
}

export class GameApiError extends Error {
  readonly code?: string

  constructor(message: string, code?: string) {
    super(message)
    this.code = code
  }
}

async function readResponse(response: Response): Promise<SingleGame> {
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    const message = problem?.code === 'RATE_LIMITED' ? 'Çok fazla istek gönderildi. Biraz sonra yeniden dene.'
      : problem?.code === 'REQUEST_TOO_LARGE' ? 'İstek çok büyük.'
        : problem?.detail ?? 'İstek tamamlanamadı.'
    throw new GameApiError(message, problem?.code)
  }
  return response.json() as Promise<SingleGame>
}

export async function startSingleGame(): Promise<SingleGame> {
  const body = JSON.stringify({ gameType: 'COLOR_GUESS' })
  const key = await creationKey('single', body)
  const response = await fetch('/api/v1/single-games', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
    body,
  })
  const game = await readResponse(response)
  clearCreationKey('single')
  return game
}

export async function getSingleGame(id: string, signal?: AbortSignal): Promise<SingleGame> {
  const response = await fetch(`/api/v1/games/${id}`, { signal })
  return readResponse(response)
}

export async function continueSingleGame(id: string): Promise<SingleGame> {
  const response = await fetch(`/api/v1/games/${id}/continue`, { method: 'POST' })
  return readResponse(response)
}

export async function submitColorGuess(id: string, roundNumber: number, color: string): Promise<SingleGame> {
  const response = await fetch(`/api/v1/games/${id}/rounds/${roundNumber}/submission`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ guess: { color } }),
  })
  return readResponse(response)
}

export async function saveSingleColorDraft(id: string, roundNumber: number, color: string, revision: number): Promise<SingleGame> {
  return readResponse(await fetch(`/api/v1/games/${id}/rounds/${roundNumber}/draft`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ guess: { color }, revision }),
  }))
}
