import { useState, useEffect, useRef, useCallback } from 'react'
import { streamingDownload } from '../../lib/streamingDownload'
import styles from './TimelapsePanel.module.css'

const SPEEDS = [1, 2, 5, 10, 20]

export default function TimelapsePanel({ token, onExpired }) {
  const [frames,   setFrames]   = useState([])  // oldest-first
  const [enabled,  setEnabled]  = useState(false)
  const [interval, setInterval_] = useState(60)
  const [running,  setRunning]  = useState(false)
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState(null)
  const [status,   setStatus]   = useState(null)
  const [idx,      setIdx]      = useState(0)
  const [playing,  setPlaying]  = useState(false)
  const [speed,    setSpeed]    = useState(5)
  const [srcUrl,   setSrcUrl]   = useState(null)
  const [maxHeap,  setMaxHeap]  = useState(0)   // server -Xmx in bytes, from JVM

  // Editable capture settings, mirrored from /api/admin/config.
  const [cfg, setCfg] = useState({ dimension: 'minecraft:overworld', maxDimension: 6000, maxFrames: 0 })

  const auth      = { Authorization: `Bearer ${token}` }
  const blobCache = useRef(new Map())   // name -> objectURL
  const playRef   = useRef(null)
  const pollRef   = useRef(null)

  function flash(msg) { setStatus(msg); setTimeout(() => setStatus(null), 4000) }

  const loadList = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await fetch('/api/admin/timelapse', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      const list = Array.isArray(d.frames) ? [...d.frames].sort((a, b) => a.capturedAt - b.capturedAt) : []
      setFrames(list)
      setEnabled(Boolean(d.enabled))
      setRunning(Boolean(d.running))
      if (Number.isFinite(d.intervalMinutes)) setInterval_(d.intervalMinutes)
      if (Number.isFinite(d.serverMaxHeap)) setMaxHeap(d.serverMaxHeap)
      setIdx(i => Math.min(i, Math.max(0, list.length - 1)))
    } catch { setError('Failed to load frames') }
    setLoading(false)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { loadList() }, [loadList])

  useEffect(() => {
    fetch('/api/admin/config', { headers: auth })
      .then(r => r.status === 401 ? null : r.json())
      .then(d => {
        if (d && !d.error) setCfg({
          dimension:    d.timelapse_dimension || 'minecraft:overworld',
          maxDimension: Number.isFinite(d.timelapse_max_dimension) ? d.timelapse_max_dimension : 6000,
          maxFrames:    Number.isFinite(d.timelapse_max_frames) ? d.timelapse_max_frames : 0,
        })
      })
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

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
    } catch { setError('Failed to update setting'); return false }
  }

  async function toggleEnabled(next) {
    setEnabled(next)
    if (await patchConfig({ timelapse_enabled: next })) flash(next ? 'Timelapse capture on' : 'Timelapse capture off')
    else setEnabled(!next)
  }

  async function saveInterval(mins) {
    const v = Math.max(1, Number(mins) || 0)
    setInterval_(v)
    await patchConfig({ timelapse_interval_minutes: v })
  }

  async function saveCfgField(key, apiKey, value, min) {
    const v = min != null ? Math.max(min, Number(value) || 0) : value
    setCfg(c => ({ ...c, [key]: v }))
    if (await patchConfig({ [apiKey]: v })) flash('Saved')
  }

  // Free all cached object URLs on unmount.
  useEffect(() => () => {
    for (const url of blobCache.current.values()) URL.revokeObjectURL(url)
    blobCache.current.clear()
  }, [])

  // Frames need an auth header, which <img src> cannot send, so fetch each as a
  // blob and hand the <img> an object URL. Cache by name; prefetch the next few.
  const ensureBlob = useCallback(async (name) => {
    if (!name) return null
    const cached = blobCache.current.get(name)
    if (cached) return cached
    const r = await fetch(`/api/admin/timelapse/frame?name=${encodeURIComponent(name)}`, { headers: auth })
    if (r.status === 401) { onExpired(); return null }
    if (!r.ok) return null
    const url = URL.createObjectURL(await r.blob())
    blobCache.current.set(name, url)
    return url
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Show the current frame and warm the next few.
  useEffect(() => {
    let alive = true
    const cur = frames[idx]
    if (!cur) { setSrcUrl(null); return }
    ensureBlob(cur.name).then(url => { if (alive) setSrcUrl(url) })
    for (let k = 1; k <= 3; k++) if (frames[idx + k]) ensureBlob(frames[idx + k].name)
    return () => { alive = false }
  }, [frames, idx, ensureBlob])

  // Playback: advance frames on a timer, stop at the end.
  useEffect(() => {
    if (!playing) {
      if (playRef.current) { clearInterval(playRef.current); playRef.current = null }
      return
    }
    playRef.current = setInterval(() => {
      setIdx(i => {
        if (i >= frames.length - 1) { setPlaying(false); return i }
        return i + 1
      })
    }, 1000 / speed)
    return () => { if (playRef.current) { clearInterval(playRef.current); playRef.current = null } }
  }, [playing, speed, frames.length])

  // Poll while a capture is running.
  useEffect(() => {
    if (!running) {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
      return
    }
    pollRef.current = setInterval(loadList, 3000)
    return () => { if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null } }
  }, [running, loadList])

  async function captureNow() {
    setError(null)
    try {
      const r = await fetch('/api/admin/timelapse/capture', { method: 'POST', headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { if (r.status === 409) flash('A capture is already running'); else setError(d.error) }
      else { flash('Capturing frame…'); setRunning(true) }
    } catch { setError('Failed to start capture') }
  }

  // Download via authenticated fetch (a plain <a href> sends no Authorization
  // header, so the export route 403s whenever a dashboard password is set).
  async function exportFrames() {
    if (frames.length === 0) return
    setError(null)
    const result = await streamingDownload({
      url: '/api/admin/timelapse/export',
      filename: 'timelapse-frames.zip',
      headers: auth,
      allowBlobFallback: true,
    })
    if (!result.ok && !result.userCancelled) setError(result.error || 'Export failed')
  }

  async function deleteFrame(name) {
    setError(null)
    try {
      const r = await fetch('/api/admin/timelapse/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ name }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else {
        const url = blobCache.current.get(name)
        if (url) { URL.revokeObjectURL(url); blobCache.current.delete(name) }
        flash('Frame deleted')
        loadList()
      }
    } catch { setError('Delete failed') }
  }

  function fmtDate(ms) {
    return new Date(ms).toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
  }

  const current = frames[idx]

  // Peak heap for one capture: ~12 bytes/px (MapColor refs + height ints + ARGB buffer).
  const peakBytes = cfg.maxDimension * cfg.maxDimension * 12
  const heapPct   = maxHeap > 0 ? peakBytes / maxHeap : 0
  // danger if a single capture would eat a large slice of the server's whole heap.
  const heapLevel = heapPct >= 0.5 ? 'danger' : heapPct >= 0.25 ? 'warn' : 'ok'
  function fmtBytes(b) {
    if (b >= 1024 ** 3) return `${(b / 1024 ** 3).toFixed(2)} GB`
    if (b >= 1024 ** 2) return `${Math.round(b / 1024 ** 2)} MB`
    return `${Math.round(b / 1024)} KB`
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.banner}>
        Timelapse frames are top-down renders of the world, auto-sized to the generated area,
        captured on a schedule and assembled into a timelapse here in your browser.
      </div>

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>✕</button>
        </div>
      )}
      {status && <div className={styles.statusMsg}>{status}</div>}

      <div className={styles.settings}>
        <label className={styles.toggleRow}>
          <input type="checkbox" checked={enabled} onChange={e => toggleEnabled(e.target.checked)} />
          <span className={styles.toggleLabel}>Automatically capture frames on a schedule</span>
        </label>

        <div className={styles.grid}>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Interval (minutes)</label>
            <input className={styles.numInput} type="number" min={1} value={interval}
                   onChange={e => setInterval_(Number(e.target.value))}
                   onBlur={e => saveInterval(e.target.value)} />
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Dimension</label>
            <input className={styles.textInput} type="text" value={cfg.dimension}
                   onChange={e => setCfg(c => ({ ...c, dimension: e.target.value }))}
                   onBlur={e => saveCfgField('dimension', 'timelapse_dimension', e.target.value.trim())} />
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Max resolution (px, longer side)</label>
            <input className={styles.numInput} type="number" min={256} step={256} value={cfg.maxDimension}
                   onChange={e => setCfg(c => ({ ...c, maxDimension: Number(e.target.value) }))}
                   onBlur={e => saveCfgField('maxDimension', 'timelapse_max_dimension', e.target.value, 256)} />
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Max frames (0 = keep all)</label>
            <input className={styles.numInput} type="number" min={0} value={cfg.maxFrames}
                   onChange={e => setCfg(c => ({ ...c, maxFrames: Number(e.target.value) }))}
                   onBlur={e => saveCfgField('maxFrames', 'timelapse_max_frames', e.target.value, 0)} />
          </div>
        </div>
        <div className={styles.memRow} data-level={heapLevel}>
          <span className={styles.memValue}>~{fmtBytes(peakBytes)}</span>
          <span className={styles.memLabel}>
            RAM per capture{maxHeap > 0 && ` · ${Math.round(heapPct * 100)}% of server heap (${fmtBytes(maxHeap)})`}
          </span>
          {heapLevel === 'danger' && <span className={styles.memAlert}>⚠ Too high — likely to crash the server</span>}
          {heapLevel === 'warn'   && <span className={styles.memAlert}>⚠ Heavy spike every interval</span>}
        </div>
        <div className={styles.hint}>
          Snapshots auto-size to the generated world. While the world is smaller than the max
          resolution, every block is one pixel; once it grows past that, pixels cover more
          blocks. Idle periods are skipped.
        </div>
      </div>

      <div className={styles.toolbar}>
        <span className={styles.count}>
          {loading ? 'Loading…' : `${frames.length} frame${frames.length !== 1 ? 's' : ''}`}
          {enabled && ` · every ${interval} min`}
        </span>
        <button className={styles.btnGhost} onClick={loadList} disabled={loading}>Refresh</button>
        <button className={styles.btnGhost} onClick={exportFrames} disabled={frames.length === 0}>
          Export frames (.zip)
        </button>
        <button className={styles.btn} onClick={captureNow} disabled={running}>
          {running ? 'Capturing…' : 'Capture now'}
        </button>
      </div>

      {frames.length === 0 ? (
        <div className={styles.empty}>No frames yet</div>
      ) : (
        <div className={styles.player}>
          <div className={styles.stage}>
            {srcUrl
              ? <img className={styles.frame} src={srcUrl} alt={`Frame ${idx + 1}`} />
              : <div className={styles.frameLoading}>Loading frame…</div>}
          </div>

          <div className={styles.timestamp}>
            {current && `${fmtDate(current.capturedAt)} · frame ${idx + 1} / ${frames.length}`}
          </div>

          <input
            className={styles.scrub}
            type="range"
            min={0}
            max={frames.length - 1}
            value={idx}
            onChange={e => { setPlaying(false); setIdx(Number(e.target.value)) }}
          />

          <div className={styles.controls}>
            <button className={styles.btnGhost} onClick={() => { setPlaying(false); setIdx(0) }}>⏮</button>
            <button className={styles.btnGhost} onClick={() => { setPlaying(false); setIdx(i => Math.max(0, i - 1)) }}>◀</button>
            <button className={styles.btn} onClick={() => {
              if (idx >= frames.length - 1) setIdx(0)
              setPlaying(p => !p)
            }}>
              {playing ? '⏸ Pause' : '▶ Play'}
            </button>
            <button className={styles.btnGhost} onClick={() => { setPlaying(false); setIdx(i => Math.min(frames.length - 1, i + 1)) }}>▶</button>
            <button className={styles.btnGhost} onClick={() => { setPlaying(false); setIdx(frames.length - 1) }}>⏭</button>

            <label className={styles.speed}>
              Speed
              <select value={speed} onChange={e => setSpeed(Number(e.target.value))}>
                {SPEEDS.map(s => <option key={s} value={s}>{s} fps</option>)}
              </select>
            </label>

            {current && (
              <button className={styles.btnDanger} onClick={() => deleteFrame(current.name)}>
                Delete frame
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
