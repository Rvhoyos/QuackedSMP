import { useState, useEffect, useCallback } from 'react'
import styles from './HardcorePanel.module.css'

export default function HardcorePanel({ token, onExpired }) {
  const [data,        setData]        = useState(null)
  const [loading,     setLoading]     = useState(false)
  const [error,       setError]       = useState(null)
  const [confirmName, setConfirmName] = useState(null)

  const auth = { 'Authorization': `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const r = await fetch('/api/admin/hardcore', { headers: auth })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else setData(d)
    } catch { setError('Failed to load sessions') }
    setLoading(false)
  }, [token])

  useEffect(() => { load() }, [load])

  async function endSession(name) {
    try {
      const r = await fetch('/api/admin/hardcore/end', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ name }),
      })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setConfirmName(null)
      load()
    } catch { setError('Failed to end session') }
  }

  const sessions = data?.sessions ?? []

  return (
    <div className={styles.wrap}>
      <div className={styles.toolbar}>
        <span className={styles.count}>
          {data
            ? `${sessions.length} active session${sessions.length !== 1 ? 's' : ''}`
            : loading ? 'Loading...' : 'No data'}
        </span>
        <button className={styles.refresh} onClick={load} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>x</button>
        </div>
      )}

      <div className={styles.list}>
        {!data && !loading && <div className={styles.empty}>No data</div>}
        {sessions.length === 0 && data && <div className={styles.empty}>No active hardcore sessions</div>}
        {sessions.map(s => (
          <div key={s.name} className={styles.row}>
            <div className={styles.sessionInfo}>
              <span className={styles.sessionName}>{s.name}</span>
              <span className={styles.sessionDim}>{s.start} ({s.dim})</span>
            </div>
            <div className={styles.stats}>
              <span><span className={styles.alive}>{s.alive}</span> alive</span>
              <span><span className={styles.dead}>{s.dead}</span> dead</span>
              <span>peak {s.peak}</span>
              <span>{s.deaths}/{s.threshold} deaths</span>
            </div>
            <div className={styles.actions}>
              {confirmName === s.name ? (
                <>
                  <span className={styles.confirmLabel}>End session?</span>
                  <button className={styles.btnDanger} onClick={() => endSession(s.name)}>Confirm</button>
                  <button className={styles.btnGhost} onClick={() => setConfirmName(null)}>Cancel</button>
                </>
              ) : (
                <button className={styles.btnDanger} onClick={() => setConfirmName(s.name)}>
                  End Session
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
