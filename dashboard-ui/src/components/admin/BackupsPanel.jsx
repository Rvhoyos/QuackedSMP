import { useState, useEffect, useRef } from 'react'
import DownloadProgress from '../DownloadProgress'
import RestoreInfo from '../RestoreInfo'
import { streamingDownload } from '../../lib/streamingDownload'
import { Toolbar, IconButton, Btn, Toggle, Field, Input, SectionCard, EmptyState, ErrorBanner, ConfirmDialog } from '../../ui'
import { IconShulkerBox } from './MinecraftIcons'
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
  const [tab,             setTab]             = useState('snapshots')
  const pollRef = useRef(null)

  const auth = { Authorization: `Bearer ${token}` }

  function flash(msg) {
    setStatus(msg)
    setTimeout(() => setStatus(null), 4000)
  }

  // silent skips the loading flag so the 3s running-backup poll does not flicker
  // the toolbar/list; manual refreshes still show the loading state.
  async function loadList({ silent = false } = {}) {
    if (!silent) setLoading(true)
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
    if (!silent) setLoading(false)
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
    pollRef.current = setInterval(() => loadList({ silent: true }), 3000)
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
        flash('Snapshot started, saving world…')
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

  const urlErr = panelUrlError(panelUrl)

  return (
    <div className={styles.wrap}>
      <Toolbar title="Backups" count={`${snapshots.length} snapshots`}>
        <div className={styles.tabs}>
          <button className={`${styles.tab} ${tab === 'snapshots' ? styles.tabOn : ''}`} onClick={() => setTab('snapshots')}>Snapshots</button>
          <button className={`${styles.tab} ${tab === 'settings' ? styles.tabOn : ''}`} onClick={() => setTab('settings')}>Settings</button>
        </div>
        <IconButton tip="Refresh" onClick={() => loadList()} disabled={loading}>↻</IconButton>
        <Btn size="sm" variant="primary" onClick={createSnapshot} disabled={running}>{running ? 'Creating…' : 'Create Snapshot'}</Btn>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>
      {status && <div className={styles.status}>{status}</div>}
      {running && <div className={styles.runningBar}><div className={styles.runningPulse} /></div>}

      {tab === 'snapshots' ? (
        <div className={styles.body}>
          <RestoreInfo info={restoreInfo} />
          {!loading && snapshots.length === 0 ? (
            <EmptyState icon={<IconShulkerBox size={30} />} label="No snapshots yet" hint="Create one above, autosave pauses during the zip" />
          ) : (
            <div className={styles.list}>
              {snapshots.map(snap => (
                <div key={snap.name} className={styles.row}>
                  <div className={styles.snapInfo}>
                    <span className={styles.snapName}>{snap.name}</span>
                    <span className={styles.snapMeta}>{fmtSize(snap.sizeBytes)} · {fmtDate(snap.createdAt)}</span>
                  </div>
                  {downloading === snap.name && dlProgress && <DownloadProgress {...dlProgress} />}
                  <div className={styles.actions}>
                    <Btn size="sm" onClick={() => downloadSnapshot(snap.name)} disabled={downloading != null}>{downloading === snap.name ? 'Downloading…' : 'Download'}</Btn>
                    <Btn size="sm" variant="danger" onClick={() => setConfirmDelete(snap.name)} disabled={downloading === snap.name}>Delete</Btn>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <div className={styles.body}>
          <SectionCard title="Schedule">
            <div className={styles.toggleRow}><span className={styles.tLabel}>Automatic scheduled backups</span><Toggle checked={periodicEnabled} onChange={togglePeriodic} aria-label="Scheduled backups" /></div>
            {periodicEnabled && (
              <Field label="Interval (hours)" hint={`A due backup waits for an empty server, then runs. Next: ${nextBackupLabel()}`}>
                <Input type="number" min={1} value={intervalHours} onChange={e => setIntervalHours(Number(e.target.value))} onBlur={e => saveIntervalHours(e.target.value)} />
              </Field>
            )}
            <Field label="Keep last N snapshots" hint="Older snapshots are pruned after each backup">
              <Input type="number" min={1} value={maxCount} onChange={e => setMaxCount(Number(e.target.value))} onBlur={e => saveMaxCount(e.target.value)} />
            </Field>
          </SectionCard>

          <SectionCard title="Public Download" subtitle="Shows a Download World button on the public dashboard.">
            <div className={styles.toggleRow}><span className={styles.tLabel}>Allow public download of latest snapshot</span><Toggle checked={publicEnabled} onChange={togglePublic} aria-label="Public download" /></div>
            {publicEnabled && (
              <div className={styles.toggleRow}><span className={styles.tLabel}>Also disclose the seed to downloaders</span><Toggle checked={seedDisclosure} onChange={toggleSeedDisclosure} aria-label="Seed disclosure" /></div>
            )}
            {publicEnabled && <div className={styles.note}>Disclosing the seed lets guests restore matching terrain but exposes it to seed-finder tools. Admins always see the seed.</div>}
          </SectionCard>

          {publicEnabled && (
            <SectionCard title="Web Panel Link" subtitle="Sent to players via /smp download. Empty hides the feature.">
              <Field label="Panel URL">
                <div className={styles.urlRow}>
                  <Input type="text" placeholder="https://panel.example.com" value={panelUrl} onChange={e => setPanelUrl(e.target.value)} />
                  <Btn onClick={savePanelUrl} disabled={panelUrl.trim() === panelUrlSaved || Boolean(urlErr)}>Save</Btn>
                </div>
              </Field>
              {urlErr && <div className={styles.note} style={{ color: 'var(--red)' }}>{urlErr}</div>}
              <div className={`${styles.toggleRow} ${!panelUrlSaved ? styles.disabled : ''}`}>
                <span className={styles.tLabel}>Periodically announce the link in chat</span>
                <Toggle checked={panelMsgEnabled} disabled={!panelUrlSaved} onChange={togglePanelMsg} aria-label="Announce link" />
              </div>
              {panelMsgEnabled && panelUrlSaved && (
                <Field label="Interval (seconds)"><Input type="number" min={60} value={panelInterval} onChange={e => setPanelInterval(Number(e.target.value))} onBlur={e => savePanelInterval(e.target.value)} /></Field>
              )}
            </SectionCard>
          )}
        </div>
      )}

      <ConfirmDialog open={!!confirmDelete} onOpenChange={o => !o && setConfirmDelete(null)}
        title={confirmDelete ? `Delete ${confirmDelete}?` : ''}
        description="This permanently removes the snapshot zip."
        confirmLabel="Delete" tone="danger" onConfirm={() => deleteSnapshot(confirmDelete)} />
    </div>
  )
}
