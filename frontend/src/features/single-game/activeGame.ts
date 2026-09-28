const ACTIVE_GAME_KEY = 'project.activeColorGameId'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function readActiveGameId(): string | null {
  try {
    const id = sessionStorage.getItem(ACTIVE_GAME_KEY)
    if (!id) return null
    if (UUID_PATTERN.test(id)) return id
    sessionStorage.removeItem(ACTIVE_GAME_KEY)
  } catch {
    // The game remains playable when browser storage is unavailable.
  }
  return null
}

export function saveActiveGameId(id: string): void {
  try {
    sessionStorage.setItem(ACTIVE_GAME_KEY, id)
  } catch {
    // The current tab can still play without refresh recovery.
  }
}

export function clearActiveGameId(): void {
  try {
    sessionStorage.removeItem(ACTIVE_GAME_KEY)
  } catch {
    // Browser storage may be unavailable.
  }
}
