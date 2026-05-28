import { useState, useEffect, useRef } from 'react'
import styles from './BackupsPanel.module.css'

export default function BackupsPanel({ token, onExpired }) {
  const [snapshots,     setSnapshots]     = useState([])
  const [running,       setRunning]       = useState(false)
  const [loading,       setLoading]       = useState(false)
  const [error,         setError]         = useState(null)
  const [status,        setStatus]        = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [downloading,   setDownloading]   = useState(null)
  const pollRef = useRef(null)

  const auth = { Authorization: `Bearer ${token}` }

  function flash(msg) {
    setStatus(msg)
    setTimeout(() => setStatus(null), 4000)
  }

  async function loadList() {
    setLoading(true)
    setError(null)
    try {
      const r = await fetch('/api/admin/backups', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else {
        setSnapshots(Array.isArray(d.snapshots) ? d.snapshots : [])
        setRunning(Boolean(d.running))
      }
    } catch { setError('Failed to load snapshots') }
    setLoading(false)
  }

  useEffect(() => { loadList() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Poll while a snapshot is being generated.
  useEffect(() => {
    if (!running) {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
      return
    }
    pollRef.current = setInterval(loadList, 3000)
    return () => {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
    }
  }, [running]) // eslint-disable-line react-hooks/exhaustive-deps

  async function createSnapshot() {
    setError(null)
    try {
      const r = await fetch('/api/admin/backups/create', { method: 'POST', headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) {
        if (r.status === 409) flash('A snapshot is already running')
        else setError(d.error)
      } else {
        flash('Snapshot started — saving world…')
        setRunning(true)
      }
    } catch { setError('Failed to start snapshot') }
  }

  async function deleteSnapshot(name) {
    setConfirmDelete(null)
    setError(null)
    try {
      const r = await fetch('/api/admin/backups/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ name }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { flash(`Deleted ${name}`); loadList() }
    } catch { setError('Delete failed') }
  }

  async function downloadSnapshot(name) {
    setError(null)
    setDownloading(name)
    try {
      const r = await fetch(`/api/admin/backups/download?name=${encodeURIComponent(name)}`, { headers: auth })
      if (r.status === 401) { onExpired(); return }
      if (!r.ok) {
        try { const d = await r.json(); setError(d.error || 'Download failed') }
        catch { setError('Download failed') }
        return
      }
      const blob = await r.blob()
      const url  = URL.createObjectURL(blob)
      const a    = document.createElement('a')
      a.href     = url
      a.download = name
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch { setError('Download failed') }
    setDownloading(null)
  }

  function fmtSize(bytes) {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
  }

  function fmtDate(ms) {
    const d = new Date(ms)
    return d.toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    })
  }

  return (
    <div className={styles.wrap}>

      <div className={styles.banner}>
        Snapshots zip the world folder. The server pauses autosave during the zip.
        Large downloads share upload bandwidth with players and may cause lag.
      </div>

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>✕</button>
        </div>
      )}
      {status && <div className={styles.statusMsg}>{status}</div>}

      <div className={styles.toolbar}>
        <span className={styles.count}>
          {loading
            ? 'Loading…'
            : `${snapshots.length} snapshot${snapshots.length !== 1 ? 's' : ''}`}
        </span>
        <button className={styles.btnGhost} onClick={loadList} disabled={loading}>
          Refresh
        </button>
        <button className={styles.btn} onClick={createSnapshot} disabled={running}>
          {running ? 'Creating snapshot…' : 'Create Snapshot'}
        </button>
      </div>

      {running && (
        <div className={styles.runningBar}>
          <div className={styles.runningPulse} />
        </div>
      )}

      <div className={styles.list}>
        {!loading && snapshots.length === 0 && (
          <div className={styles.empty}>No snapshots yet</div>
        )}

        {snapshots.map(snap => (
          <div key={snap.name} className={styles.row}>
            <div className={styles.snapInfo}>
              <span className={styles.snapName}>{snap.name}</span>
            </div>
            <span className={styles.snapMeta}>
              {fmtSize(snap.sizeBytes)} · {fmtDate(snap.createdAt)}
            </span>

            {confirmDelete === snap.name ? (
              <div className={styles.confirmRow}>
                <span className={styles.confirmText}>Delete {snap.name}?</span>
                <button className={styles.btnDanger} onClick={() => deleteSnapshot(snap.name)}>
                  Confirm
                </button>
                <button className={styles.btnGhost} onClick={() => setConfirmDelete(null)}>
                  Cancel
                </button>
              </div>
            ) : (
              <div className={styles.actions}>
                <button
                  className={styles.btnGhost}
                  onClick={() => downloadSnapshot(snap.name)}
                  disabled={downloading === snap.name}
                >
                  {downloading === snap.name ? 'Downloading…' : 'Download'}
                </button>
                <button
                  className={styles.btnDanger}
                  onClick={() => setConfirmDelete(snap.name)}
                  disabled={downloading === snap.name}
                >
                  Delete
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
