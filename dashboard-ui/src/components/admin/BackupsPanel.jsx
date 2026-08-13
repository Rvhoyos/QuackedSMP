import { useState, useEffect, useRef } from 'react'
import DownloadProgress from '../DownloadProgress'
import RestoreInfo from '../RestoreInfo'
import { streamingDownload } from '../../lib/streamingDownload'
import styles from './BackupsPanel.module.css'

export default function BackupsPanel({ token, onExpired }) {
  const [snapshots,     setSnapshots]     = useState([])
  const [running,       setRunning]       = useState(false)
  const [loading,       setLoading]       = useState(false)
  const [error,         setError]         = useState(null)
  const [status,        setStatus]        = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [downloading,   setDownloading]   = useState(null)
  const [dlProgress,    setDlProgress]    = useState(null) // { received, total, speed, eta } | null
  const [publicEnabled, setPublicEnabled] = useState(false)
  const [seedDisclosure, setSeedDisclosure] = useState(false)
  const [restoreInfo,    setRestoreInfo]    = useState(null)
  const [panelUrl,        setPanelUrl]        = useState('')
  const [panelUrlSaved,   setPanelUrlSaved]   = useState('')
  const [panelMsgEnabled, setPanelMsgEnabled] = useState(false)
  const [panelInterval,   setPanelInterval]   = useState(1800)
  const [maxCount,        setMaxCount]        = useState(5)
  const [periodicEnabled, setPeriodicEnabled] = useState(false)
  const [intervalHours,   setIntervalHours]   = useState(24)
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

  useEffect(() => {
    fetch('/api/admin/restore-info', { headers: auth })
      .then(r => r.status === 401 ? null : r.json())
      .then(d => { if (d && !d.error) setRestoreInfo(d) })
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    fetch('/api/admin/config', { headers: auth })
      .then(r => r.status === 401 ? null : r.json())
      .then(d => {
        if (d && !d.error) {
          setPublicEnabled(Boolean(d.backup_public_download))
          setSeedDisclosure(Boolean(d.backup_public_seed_disclosure))
          const url = d.panel_url || ''
          setPanelUrl(url)
          setPanelUrlSaved(url)
          setPanelMsgEnabled(Boolean(d.panel_message_enabled))
          if (Number.isFinite(d.panel_message_interval)) setPanelInterval(d.panel_message_interval)
          if (Number.isFinite(d.backup_max_count)) setMaxCount(d.backup_max_count)
          setPeriodicEnabled(Boolean(d.backup_periodic_enabled))
          if (Number.isFinite(d.backup_interval_hours)) setIntervalHours(d.backup_interval_hours)
        }
      })
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Saves an arbitrary config patch, surfacing errors and session expiry.
  async function patchConfig(patch) {
    try {
      const r = await fetch('/api/admin/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify(patch),
      })
      if (r.status === 401) { onExpired(); return false }
      const d = await r.json()
      if (d.error) { setError(d.error); return false }
      return true
    } catch {
      setError('Failed to update setting')
      return false
    }
  }

  // Minecraft only makes http/https links clickable; anything else is dead text in chat.
  // Empty is allowed (it clears the link and hides the feature).
  function panelUrlError(url) {
    const t = url.trim()
    if (t === '') return null
    let parsed
    try { parsed = new URL(t) } catch { return 'Enter a full URL, e.g. https://panel.example.com' }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return 'URL must start with http:// or https://'
    }
    return null
  }

  async function savePanelUrl() {
    const trimmed = panelUrl.trim()
    const err = panelUrlError(trimmed)
    if (err) { setError(err); return }
    if (await patchConfig({ panel_url: trimmed })) {
      setPanelUrl(trimmed)
      setPanelUrlSaved(trimmed)
      flash(trimmed ? 'Panel link saved' : 'Panel link cleared')
    }
  }

  async function togglePanelMsg(next) {
    setPanelMsgEnabled(next)
    if (!(await patchConfig({ panel_message_enabled: next }))) setPanelMsgEnabled(!next)
  }

  async function savePanelInterval(secs) {
    const v = Math.max(60, Number(secs) || 0)
    setPanelInterval(v)
    await patchConfig({ panel_message_interval: v })
  }

  async function saveMaxCount(n) {
    const v = Math.max(1, Number(n) || 0)
    setMaxCount(v)
    await patchConfig({ backup_max_count: v })
  }

  async function togglePeriodic(next) {
    setPeriodicEnabled(next)
    if (!(await patchConfig({ backup_periodic_enabled: next }))) setPeriodicEnabled(!next)
  }

  async function saveIntervalHours(hours) {
    const v = Math.max(1, Number(hours) || 0)
    setIntervalHours(v)
    await patchConfig({ backup_interval_hours: v })
  }

  // Approximate next scheduled run: newest snapshot + interval. Deferred if players are
  // online, so this is a lower bound. No backend clock needed.
  function nextBackupLabel() {
    if (snapshots.length === 0) return `about ${intervalHours}h after the next snapshot`
    const due = snapshots[0].createdAt + intervalHours * 3600 * 1000
    return due <= Date.now() ? 'due now (waiting for an empty server)' : `~${fmtDate(due)}`
  }

  async function togglePublic(next) {
    setPublicEnabled(next)
    try {
      const r = await fetch('/api/admin/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ backup_public_download: next }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); setPublicEnabled(!next) }
    } catch {
      setError('Failed to update setting')
      setPublicEnabled(!next)
    }
  }

  async function toggleSeedDisclosure(next) {
    setSeedDisclosure(next)
    if (!(await patchConfig({ backup_public_seed_disclosure: next }))) setSeedDisclosure(!next)
  }

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
    setDlProgress({ received: 0, total: 0, speed: 0, eta: null })
    const result = await streamingDownload({
      url: `/api/admin/backups/download?name=${encodeURIComponent(name)}`,
      filename: name,
      headers: auth,
      onProgress: setDlProgress,
      allowBlobFallback: true,
    })
    setDownloading(null)
    setDlProgress(null)
    if (!result.ok && !result.userCancelled) {
      setError(result.error || 'Download failed')
    }
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

      <RestoreInfo info={restoreInfo} />

      <div className={styles.panelBox}>
        <div className={styles.panelTitle}>Scheduled backups</div>

        <label className={`${styles.toggleRow} ${styles.panelInner}`}>
          <input
            type="checkbox"
            checked={periodicEnabled}
            onChange={e => togglePeriodic(e.target.checked)}
          />
          <span className={styles.toggleLabel}>Automatically back up on a schedule</span>
        </label>

        {periodicEnabled && (
          <>
            <div className={styles.panelField}>
              <label className={styles.panelLabel}>Interval (hours)</label>
              <input
                className={styles.numInput}
                type="number"
                min={1}
                value={intervalHours}
                onChange={e => setIntervalHours(Number(e.target.value))}
                onBlur={e => saveIntervalHours(e.target.value)}
              />
            </div>
            <div className={styles.panelHint}>
              A due backup waits until no players are online, then runs automatically.
              If it stays overdue for a full extra interval, it runs anyway.
              <br />Next backup: {nextBackupLabel()}
            </div>
          </>
        )}

        <div className={styles.panelField}>
          <label className={styles.panelLabel}>Keep last N snapshots</label>
          <input
            className={styles.numInput}
            type="number"
            min={1}
            value={maxCount}
            onChange={e => setMaxCount(Number(e.target.value))}
            onBlur={e => saveMaxCount(e.target.value)}
          />
          <div className={styles.panelHint}>
            Applies to all snapshots, manual and scheduled. Older ones are pruned after each backup.
          </div>
        </div>
      </div>

      <label className={styles.toggleRow}>
        <input
          type="checkbox"
          checked={publicEnabled}
          onChange={e => togglePublic(e.target.checked)}
        />
        <span className={styles.toggleLabel}>
          Allow public download of latest snapshot
        </span>
        <span className={styles.toggleHint}>
          Shows a "Download World" button on the public dashboard
        </span>
      </label>

      {publicEnabled && (
        <div className={styles.panelBox}>
          <div className={styles.panelTitle}>Public seed disclosure</div>
          <label className={`${styles.toggleRow} ${styles.panelInner}`}>
            <input
              type="checkbox"
              checked={seedDisclosure}
              onChange={e => toggleSeedDisclosure(e.target.checked)}
            />
            <span className={styles.toggleLabel}>Also give the seed to public downloaders</span>
          </label>
          <div className={styles.panelHint}>
            Lets guests restore matching terrain, but exposes the seed to online seed-finder
            tools (structure/loot locating). Off by default. Admins always see the seed above.
          </div>
        </div>
      )}

      {publicEnabled && (
        <div className={styles.panelBox}>
          <div className={styles.panelTitle}>Web panel link</div>
          <div className={styles.panelHint}>
            Players get this link via /smp download. Requires a URL below. Nothing shows if it's empty.
          </div>

          <div className={styles.panelField}>
            <label className={styles.panelLabel}>Panel URL</label>
            <div className={styles.panelRow}>
              <input
                className={styles.textInput}
                type="text"
                placeholder="https://panel.example.com"
                value={panelUrl}
                onChange={e => setPanelUrl(e.target.value)}
              />
              <button
                className={styles.btn}
                onClick={savePanelUrl}
                disabled={panelUrl.trim() === panelUrlSaved || Boolean(panelUrlError(panelUrl))}
              >
                Save
              </button>
            </div>
            {panelUrlError(panelUrl)
              ? <div className={styles.panelError}>{panelUrlError(panelUrl)}</div>
              : <div className={styles.panelHint}>Must include http:// or https:// or it won't be clickable in chat.</div>}
          </div>

          <label className={`${styles.toggleRow} ${styles.panelInner} ${!panelUrlSaved ? styles.disabledRow : ''}`}>
            <input
              type="checkbox"
              checked={panelMsgEnabled}
              disabled={!panelUrlSaved}
              onChange={e => togglePanelMsg(e.target.checked)}
            />
            <span className={styles.toggleLabel}>Periodically announce the link in chat</span>
          </label>

          {panelMsgEnabled && panelUrlSaved && (
            <div className={styles.panelField}>
              <label className={styles.panelLabel}>Interval (seconds)</label>
              <input
                className={styles.numInput}
                type="number"
                min={60}
                value={panelInterval}
                onChange={e => setPanelInterval(Number(e.target.value))}
                onBlur={e => savePanelInterval(e.target.value)}
              />
            </div>
          )}
        </div>
      )}

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

            {downloading === snap.name && dlProgress && (
              <DownloadProgress {...dlProgress} />
            )}

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
                  disabled={downloading != null}
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
