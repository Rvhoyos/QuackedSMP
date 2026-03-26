import { useState, useEffect, useCallback } from 'react'
import styles from './ClaimsPanel.module.css'

export default function ClaimsPanel({ token, onExpired }) {
  const [data,         setData]         = useState(null)
  const [loading,      setLoading]      = useState(false)
  const [error,        setError]        = useState(null)
  const [confirmUuid,  setConfirmUuid]  = useState(null)

  const auth = { 'Authorization': `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const r = await fetch('/api/admin/claims', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else setData(d)
    } catch { setError('Failed to load claims') }
    setLoading(false)
  }, [token])

  useEffect(() => { load() }, [load])

  async function unclaimAll(player) {
    try {
      const r = await fetch('/api/admin/claims/unclaim', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ uuid: player.uuid }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setConfirmUuid(null)
      load()
    } catch { setError('Failed to unclaim') }
  }

  const maxCount = data?.players?.[0]?.count ?? 1

  return (
    <div className={styles.wrap}>
      <div className={styles.toolbar}>
        <span className={styles.count}>
          {data
            ? `${data.total} total claims across ${data.players?.length ?? 0} players`
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
        {data?.players?.length === 0 && <div className={styles.empty}>No claims found</div>}
        {data?.players?.map(p => (
          <div key={p.uuid} className={styles.row}>
            <div className={styles.playerInfo}>
              <span className={styles.playerName}>{p.name}</span>
              <span className={styles.playerUuid}>{p.uuid.substring(0, 8)}...</span>
            </div>
            <div className={styles.barWrap}>
              <div className={styles.bar}>
                <div
                  className={styles.barFill}
                  style={{ width: `${Math.round((p.count / maxCount) * 100)}%` }}
                />
              </div>
              <span className={styles.claimCount}>{p.count} claims</span>
            </div>
            <div className={styles.actions}>
              {confirmUuid === p.uuid ? (
                <>
                  <span className={styles.confirmLabel}>Unclaim all?</span>
                  <button className={styles.btnDanger} onClick={() => unclaimAll(p)}>Confirm</button>
                  <button className={styles.btnGhost} onClick={() => setConfirmUuid(null)}>Cancel</button>
                </>
              ) : (
                <button className={styles.btnDanger} onClick={() => setConfirmUuid(p.uuid)}>
                  Unclaim All
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
