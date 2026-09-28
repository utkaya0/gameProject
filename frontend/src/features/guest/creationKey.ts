type Pending = { payload: string; key: string; createdAt: number }
const lifetime = 10 * 60 * 1000

export async function creationKey(action: string, payload: string): Promise<string> {
  const session = await fetch('/api/v1/session')
  if (!session.ok) throw new Error('Oturum açılamadı. Lütfen yeniden dene.')
  const storageKey = `project:pending:${action}`
  try {
    const previous = JSON.parse(sessionStorage.getItem(storageKey) ?? 'null') as Pending | null
    if (previous?.payload === payload && Date.now() - previous.createdAt < lifetime) return previous.key
    const key = crypto.randomUUID()
    sessionStorage.setItem(storageKey, JSON.stringify({ payload, key, createdAt: Date.now() } satisfies Pending))
    return key
  } catch {
    return crypto.randomUUID()
  }
}

export function clearCreationKey(action: string): void {
  try { sessionStorage.removeItem(`project:pending:${action}`) } catch { /* Storage may be unavailable. */ }
}
