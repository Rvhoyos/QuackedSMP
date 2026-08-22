import { useCallback, useEffect, useRef, useState } from 'react'
import { apiJson } from '../../ui'

const POLL_MS   = 1000
const PAGE      = 500   // server caps at 500 per response
const MAX_KEPT  = 1000  // matches the server-side ring, no point holding more

/*
 * Tails the server log from GET /api/admin/logs.
 *
 * Every line carries a sequence number and we send back the newest one we hold,
 * so each poll returns only what is new instead of the whole buffer. When the
 * first line of a response is further ahead than our cursor, the server dropped
 * lines while we were behind: a gap marker goes into the list rather than
 * quietly stitching an incomplete tail together.
 *
 * echo() adds a local-only line (the command you just sent), which never gets a
 * server sequence, so those use descending negative ids to stay key-unique.
 */
export default function useLogTail({ token, onExpired, enabled = true }) {
  const [lines, setLines] = useState([])
  const [error, setError] = useState(null)
  const [live,  setLive]  = useState(false)

  const cursor = useRef(0)
  const local  = useRef(0)

  const push = useCallback(entries => {
    setLines(prev => prev.concat(entries).slice(-MAX_KEPT))
  }, [])

  const echo = useCallback((msg, level = 'CMD') => {
    local.current -= 1
    push([{ seq: local.current, t: Date.now(), level, logger: '', thread: '', msg }])
  }, [push])

  useEffect(() => {
    if (!enabled) return undefined

    let stopped = false
    let timer   = null

    const tick = async () => {
      try {
        const d = await apiJson(`/api/admin/logs?since=${cursor.current}&limit=${PAGE}`, { token, onExpired })
        if (stopped) return
        const incoming = Array.isArray(d.lines) ? d.lines : []
        if (incoming.length > 0) {
          const dropped = cursor.current > 0 && incoming[0].seq > cursor.current + 1
          const marker  = dropped
            ? [{ seq: incoming[0].seq - 0.5, t: Date.now(), level: 'GAP', logger: '', thread: '',
                 msg: `${incoming[0].seq - cursor.current - 1} older lines dropped` }]
            : []
          cursor.current = incoming[incoming.length - 1].seq
          push(marker.concat(incoming))
        }
        setLive(true)
        setError(null)
      } catch (e) {
        if (stopped || e.expired) return
        setLive(false)
        setError(e.message || 'Log stream unavailable')
      } finally {
        if (!stopped) timer = setTimeout(tick, POLL_MS)
      }
    }

    tick()
    return () => { stopped = true; if (timer) clearTimeout(timer) }
  }, [token, onExpired, enabled, push])

  const clear = useCallback(() => setLines([]), [])

  return { lines, error, live, echo, clear }
}
