import { creationKey, clearCreationKey } from '../guest/creationKey'

export type LobbyParticipant = { displayName: string; role: 'HOST' | 'PLAYER' }

export type LobbySnapshot = {
  code: string
  status: 'WAITING' | 'IN_GAME' | 'FINISHED' | 'EXPIRED'
  maxPlayers: number
  expiresAt: string
  yourRole: 'HOST' | 'PLAYER'
  yourDisplayName: string
  sequence: number
  participants: LobbyParticipant[]
  gameId: string | null
}

export type MultiplayerGame = {
  id: string
  status: 'IN_PROGRESS' | 'FINISHED'
  currentRound: number
  totalRounds: number
  phase: 'PREVIEW' | 'TRANSITION' | 'INPUT' | 'REVEAL' | 'COMPLETED'
  serverTime: string
  phaseEndsAt: string | null
  gameData: { targetColor: string } | null
  yourSubmissionReceived: boolean
  yourDraft: string | null
  players: { displayName: string; totalScore: number; roundScore: number | null; submitted: boolean; ready: boolean }[]
  revealedRounds: { roundNumber: number; scores: { displayName: string; score: number }[] }[]
}

export class LobbyApiError extends Error {
  readonly code?: string

  constructor(message: string, code?: string) {
    super(message)
    this.code = code
  }
}

async function readResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    const message = problem?.code === 'RATE_LIMITED' ? 'Çok fazla istek gönderildi. Biraz sonra yeniden dene.'
      : problem?.code === 'REQUEST_TOO_LARGE' ? 'İstek çok büyük.'
        : problem?.detail ?? 'Lobi isteği tamamlanamadı.'
    throw new LobbyApiError(message, problem?.code)
  }
  return response.json() as Promise<T>
}

const readLobby = (response: Response) => readResponse<LobbySnapshot>(response)

export async function createLobby(displayName: string, maxPlayers: number): Promise<LobbySnapshot> {
  const body = JSON.stringify({ displayName, maxPlayers })
  const key = await creationKey('lobby', body)
  const lobby = await readLobby(await fetch('/api/v1/lobbies', {
    method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key }, body,
  }))
  clearCreationKey('lobby')
  return lobby
}

export async function joinLobby(code: string, displayName: string): Promise<LobbySnapshot> {
  return readLobby(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/join`, {
    method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName }),
  }))
}

export async function getLobby(code: string): Promise<LobbySnapshot> {
  return readLobby(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}`, { credentials: 'same-origin' }))
}

export async function leaveLobby(code: string): Promise<void> {
  const response = await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/leave`, {
    method: 'POST', credentials: 'same-origin',
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { code?: string; detail?: string } | null
    throw new LobbyApiError(problem?.detail ?? 'Lobiden ayrılamadın.', problem?.code)
  }
}

export async function startLobbyGame(code: string): Promise<MultiplayerGame> {
  return readResponse(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/start`, { method: 'POST' }))
}

export async function rematchLobbyGame(code: string): Promise<MultiplayerGame> {
  return readResponse(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/rematch`, { method: 'POST' }))
}

export async function getLobbyGame(code: string, signal?: AbortSignal): Promise<MultiplayerGame> {
  return readResponse(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/game`, { signal }))
}

export async function submitLobbyGuess(code: string, round: number, color: string): Promise<MultiplayerGame> {
  return readResponse(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/game/rounds/${round}/submission`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ guess: { color } }),
  }))
}

export async function saveLobbyColorDraft(code: string, round: number, color: string, revision: number): Promise<MultiplayerGame> {
  return readResponse(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/game/rounds/${round}/draft`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ guess: { color }, revision }),
  }))
}

export async function readyLobbyGame(code: string): Promise<MultiplayerGame> {
  return readResponse(await fetch(`/api/v1/lobbies/${encodeURIComponent(code)}/game/ready`, { method: 'POST' }))
}
