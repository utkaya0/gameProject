import { useEffect, useRef, useState } from 'react'

type DraftStatus = 'saving' | 'saved' | 'error'

export function useColorDraft(
  sessionKey: string | undefined,
  roundNumber: number | undefined,
  phase: string | undefined,
  color: string,
  valid: boolean,
  submitted: boolean,
  saveDraft: (sessionKey: string, roundNumber: number, color: string, revision: number) => Promise<unknown>,
): DraftStatus {
  const [status, setStatus] = useState<DraftStatus>('saving')
  const version = useRef({ key: '', next: 0 })

  useEffect(() => {
    if (!sessionKey || !roundNumber || (phase !== 'TRANSITION' && phase !== 'INPUT') || !valid || submitted) return
    const key = `${sessionKey}:${roundNumber}`
    if (version.current.key !== key) version.current = { key, next: 0 }
    const revision = version.current.next = Math.max(Date.now() * 1000, version.current.next + 1)
    let stopped = false
    let timer: ReturnType<typeof setTimeout>
    const send = async () => {
      try {
        await saveDraft(sessionKey, roundNumber, color, revision)
        if (!stopped) setStatus('saved')
      } catch (error) {
        if (stopped) return
        setStatus('error')
        const code = error && typeof error === 'object' && 'code' in error ? error.code : null
        if (['GAME_FINISHED', 'ROUND_NOT_CURRENT', 'SUBMISSION_WINDOW_CLOSED'].includes(String(code))) return
        timer = setTimeout(() => void send(), 500)
      }
    }
    setStatus('saving')
    timer = setTimeout(() => void send(), 80)
    return () => { stopped = true; clearTimeout(timer) }
  }, [sessionKey, roundNumber, phase, color, valid, submitted, saveDraft])

  return status
}
