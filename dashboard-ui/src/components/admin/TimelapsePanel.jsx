import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { streamingDownload } from '../../lib/streamingDownload'
import styles from './TimelapsePanel.module.css'

const SPEEDS = [1, 2, 5, 10, 20]

// Short label for a dimension id: drop the namespace when it is minecraft.
function dimLabel(id) {
  if (!id) return ''
  const [ns, path] = id.split(':')
  return path ? (ns === 'minecraft' ? path : id) : id
}

export default function TimelapsePanel({ token, onExpired }) {
  const [dims,       setDims]       = useState([]) // [{id, sizeBytes, frames(oldest-first)}]
  const [available,  setAvailable]  = useState([]) // all dim ids, the opt-in universe
  const [totalBytes, setTotalBytes] = useState(0)
  const [viewDim,    setViewDim]    = useState('') // dimension currently viewed
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
  const [progress, setProgress] = useState(null) // live capture progress while running

  // Editable capture settings, mirrored from /api/admin/config.
  const [cfg, setCfg] = useState({ dimensions: ['minecraft:overworld'], maxRenderMb: 0, maxSkips: 3, maxFrames: 0 })

  const auth      = { Authorization: `Bearer ${token}` }
  const blobCache = useRef(new Map())   // "dim|name" -> objectURL
  const playRef   = useRef(null)
  const pollRef   = useRef(null)

  // Frames of the dimension being viewed (oldest-first); [] until one is selected.
  const dimEntry = dims.find(d => d.id === viewDim) || null
  const frames   = useMemo(() => dimEntry?.frames || [], [dimEntry])

  function flash(msg) { setStatus(msg); setTimeout(() => setStatus(null), 4000) }

  // silent skips the loading flag, so the 1s capture poll does not flicker the
  // toolbar count and Refresh button; manual refreshes still show "Loading…".
  const loadList = useCallback(async (silent = false) => {
    if (!silent) setLoading(true)
    setError(null)
    try {
      const r = await fetch('/api/admin/timelapse', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      const dimList = Array.isArray(d.dims)
        ? d.dims.map(dm => ({ ...dm, frames: [...(dm.frames || [])].sort((a, b) => a.capturedAt - b.capturedAt) }))
        : []
      setDims(dimList)
      setAvailable(Array.isArray(d.available) ? d.available : [])
      setTotalBytes(Number.isFinite(d.totalSizeBytes) ? d.totalSizeBytes : 0)
      setEnabled(Boolean(d.enabled))
      setRunning(Boolean(d.running))
      setProgress(d.progress || null)
      if (Number.isFinite(d.intervalMinutes)) setInterval_(d.intervalMinutes)
      if (Number.isFinite(d.serverMaxHeap)) setMaxHeap(d.serverMaxHeap)
      // Keep the current view if it still has frames, else fall back to the first.
      setViewDim(v => (v && dimList.some(dm => dm.id === v)) ? v : (dimList[0]?.id || ''))
    } catch { setError('Failed to load frames') }
    if (!silent) setLoading(false)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { loadList() }, [loadList])

  useEffect(() => {
    fetch('/api/admin/config', { headers: auth })
      .then(r => r.status === 401 ? null : r.json())
      .then(d => {
        if (d && !d.error) setCfg({
          dimensions:  Array.isArray(d.timelapse_dimensions) ? d.timelapse_dimensions : ['minecraft:overworld'],
          maxRenderMb: Number.isFinite(d.timelapse_max_render_mb) ? d.timelapse_max_render_mb : 0,
          maxSkips:    Number.isFinite(d.timelapse_max_skips) ? d.timelapse_max_skips : 3,
          maxFrames:   Number.isFinite(d.timelapse_max_frames) ? d.timelapse_max_frames : 0,
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

  // Opt a dimension into (or out of) periodic + manual capture.
  async function toggleCaptureDim(id, on) {
    const next = on ? [...new Set([...cfg.dimensions, id])] : cfg.dimensions.filter(d => d !== id)
    setCfg(c => ({ ...c, dimensions: next }))
    if (await patchConfig({ timelapse_dimensions: next })) flash('Saved')
    else setCfg(c => ({ ...c, dimensions: cfg.dimensions }))
  }

  // Free all cached object URLs on unmount.
  useEffect(() => () => {
    for (const url of blobCache.current.values()) URL.revokeObjectURL(url)
    blobCache.current.clear()
  }, [])

  // Reset playback to the start whenever the viewed dimension changes.
  useEffect(() => { setPlaying(false); setIdx(0) }, [viewDim])

  // Keep the frame index inside the viewed dimension's range.
  useEffect(() => { setIdx(i => Math.min(i, Math.max(0, frames.length - 1))) }, [frames.length])

  // Frames need an auth header, which <img src> cannot send, so fetch each as a
  // blob and hand the <img> an object URL. Cache by dim+name; prefetch the next few.
  const ensureBlob = useCallback(async (name) => {
    if (!name || !viewDim) return null
    const key = `${viewDim}|${name}`
    const cached = blobCache.current.get(key)
    if (cached) return cached
    const r = await fetch(
      `/api/admin/timelapse/frame?dim=${encodeURIComponent(viewDim)}&name=${encodeURIComponent(name)}`,
      { headers: auth })
    if (r.status === 401) { onExpired(); return null }
    if (!r.ok) return null
    const url = URL.createObjectURL(await r.blob())
    blobCache.current.set(key, url)
    return url
  }, [viewDim]) // eslint-disable-line react-hooks/exhaustive-deps

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
    pollRef.current = setInterval(() => loadList(true), 1000)
    return () => { if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null } }
  }, [running, loadList])

  async function captureNow() {
    setError(null)
    try {
      const r = await fetch('/api/admin/timelapse/capture', { method: 'POST', headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { if (r.status === 409) flash('A capture is already running'); else setError(d.error) }
      else { flash('Capturing frames…'); setRunning(true) }
    } catch { setError('Failed to start capture') }
  }

  // Download via authenticated fetch (a plain <a href> sends no Authorization
  // header, so the export route 403s whenever a dashboard password is set).
  async function exportFrames() {
    if (frames.length === 0 || !viewDim) return
    setError(null)
    const result = await streamingDownload({
      url: `/api/admin/timelapse/export?dim=${encodeURIComponent(viewDim)}`,
      filename: `timelapse-${viewDim.replace(':', '_')}.zip`,
      headers: auth,
      allowBlobFallback: true,
    })
    if (!result.ok && !result.userCancelled) setError(result.error || 'Export failed')
  }

  async function deleteFrame(name) {
    if (!viewDim) return
    setError(null)
    try {
      const r = await fetch('/api/admin/timelapse/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ dim: viewDim, name }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else {
        const key = `${viewDim}|${name}`
        const url = blobCache.current.get(key)
        if (url) { URL.revokeObjectURL(url); blobCache.current.delete(key) }
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

  // A forced (players-online) capture is bounded by the RAM cap; 0 = auto, which
  // fits the render to free heap and downsamples only if a capture would not fit.
  const capBytes = cfg.maxRenderMb > 0 ? cfg.maxRenderMb * 1024 ** 2 : 0
  const heapPct  = capBytes > 0 && maxHeap > 0 ? capBytes / maxHeap : 0
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

        <div className={styles.field}>
          <label className={styles.fieldLabel}>Capture these dimensions</label>
          <div className={styles.dimList}>
            {available.length === 0
              ? <span className={styles.dimCheckEmpty}>No dimensions reported by the server yet.</span>
              : available.map(id => (
                  <label key={id} className={styles.dimCheck}>
                    <input type="checkbox" checked={cfg.dimensions.includes(id)}
                           onChange={e => toggleCaptureDim(id, e.target.checked)} />
                    {id}
                  </label>
                ))}
          </div>
        </div>

        <div className={styles.grid} style={{ marginTop: 10 }}>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Interval (minutes)</label>
            <input className={styles.numInput} type="number" min={1} value={interval}
                   onChange={e => setInterval_(Number(e.target.value))}
                   onBlur={e => saveInterval(e.target.value)} />
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Max render RAM (MB, 0 = auto)</label>
            <input className={styles.numInput} type="number" min={0} value={cfg.maxRenderMb}
                   onChange={e => setCfg(c => ({ ...c, maxRenderMb: Number(e.target.value) }))}
                   onBlur={e => saveCfgField('maxRenderMb', 'timelapse_max_render_mb', e.target.value, 0)} />
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Idle-wait skips before forcing (0 = every interval)</label>
            <input className={styles.numInput} type="number" min={0} value={cfg.maxSkips}
                   onChange={e => setCfg(c => ({ ...c, maxSkips: Number(e.target.value) }))}
                   onBlur={e => saveCfgField('maxSkips', 'timelapse_max_skips', e.target.value, 0)} />
          </div>
          <div className={styles.field}>
            <label className={styles.fieldLabel}>Max frames (0 = keep all)</label>
            <input className={styles.numInput} type="number" min={0} value={cfg.maxFrames}
                   onChange={e => setCfg(c => ({ ...c, maxFrames: Number(e.target.value) }))}
                   onBlur={e => saveCfgField('maxFrames', 'timelapse_max_frames', e.target.value, 0)} />
          </div>
        </div>
        <div className={styles.memRow}>
          {capBytes > 0 ? (
            <>
              <span className={styles.memValue}>up to {fmtBytes(capBytes)}</span>
              <span className={styles.memLabel}>
                RAM cap for a forced capture{maxHeap > 0 && ` · ${Math.round(heapPct * 100)}% of server heap (${fmtBytes(maxHeap)})`}
              </span>
            </>
          ) : (
            <span className={styles.memLabel}>
              Auto: fits each capture to free server RAM, downsampling only if it would not fit.
            </span>
          )}
        </div>
        <div className={styles.hint}>
          One pixel per block, per selected dimension. Runs when the server is idle, forced
          after the skip limit. Downsamples only if a frame won't fit RAM (0 = free heap).
        </div>
      </div>

      <div className={styles.toolbar}>
        <span className={styles.count}>
          {loading ? 'Loading…' : `${frames.length} frame${frames.length !== 1 ? 's' : ''}`}
          {totalBytes > 0 && ` · ${fmtBytes(totalBytes)} on disk`}
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

      {running && <CaptureMap progress={progress} />}

      {dims.length === 0 ? (
        <div className={styles.empty}>No frames yet</div>
      ) : (
        <div className={styles.player}>
          <div className={styles.viewRow}>
            <label>
              Dimension
              <select value={viewDim} onChange={e => setViewDim(e.target.value)}>
                {dims.map(d => <option key={d.id} value={d.id}>{dimLabel(d.id)}</option>)}
              </select>
            </label>
            {dimEntry && (
              <span className={styles.viewSize}>
                {fmtBytes(dimEntry.sizeBytes)} this dimension · {fmtBytes(totalBytes)} total
              </span>
            )}
          </div>

          <div className={styles.stage}>
            {srcUrl
              ? <img className={styles.frame} src={srcUrl} alt={`Frame ${idx + 1}`} />
              : <div className={styles.frameLoading}>Loading frame…</div>}
          </div>

          <div className={styles.timestamp}>
            {current && (
              <>
                <span>{fmtDate(current.capturedAt)} · frame {idx + 1} / {frames.length}</span>
                {(() => {
                  const b = resBadge(current.blocksPerPixel)
                  return <span className={b.full ? styles.badgeFull : styles.badgeDown}>{b.text}</span>
                })()}
              </>
            )}
          </div>

          <input
            className={styles.scrub}
            type="range"
            min={0}
            max={Math.max(0, frames.length - 1)}
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

// Resolution badge for a stored frame. bpp 1 is full 1:1; higher downsampled,
// keeping 1/bpp² of the pixels (2x = 25%, 3x = 11%, 4x = 6% of full detail).
function resBadge(bpp) {
  const n = bpp || 1
  if (n <= 1) return { text: '1:1 · full detail', full: true }
  return { text: `${n}× · ${Math.round(100 / (n * n))}% detail`, full: false }
}

const PHASE_LABEL = {
  saving:   'Saving world',
  scanning: 'Scanning region files',
  painting: 'Painting terrain',
  shading:  'Shading relief',
  writing:  'Writing image',
  idle:     'Starting…',
}

// Live "map appearing chunk by chunk" view of a running capture. Mirrors the
// renderer's cx-major sweep: each poll upgrades scanned cells, so columns of the
// world light up left to right with the active scan column glowing.
function CaptureMap({ progress }) {
  const p = progress || {}
  const phase = p.phase || 'idle'
  const done  = p.chunksDone || 0
  const total = p.chunksTotal || 0
  const w = p.w || 0
  const h = p.h || 0
  const cells = p.cells || ''
  const pct = total > 0 ? Math.round((done / total) * 100) : 0
  const base = phase === 'painting' ? `Painting terrain · ${pct}%` : (PHASE_LABEL[phase] || 'Working…')
  // Prefix the dimension (and its position in a multi-dimension batch) when known.
  const dimPrefix = p.dim
    ? (p.dimCount > 1 ? `${dimLabel(p.dim)} ${p.dimIndex}/${p.dimCount} · ` : `${dimLabel(p.dim)} · `)
    : ''
  const label = dimPrefix + base

  // The frontmost scanned column, for the glowing scan edge.
  let leadCol = -1
  for (let i = 0; i < cells.length; i++) {
    if (cells[i] !== '0') { const dx = i % w; if (dx > leadCol) leadCol = dx }
  }

  const hasGrid = w > 0 && h > 0 && cells.length === w * h

  return (
    <div className={styles.capture}>
      <div className={styles.captureHead}>
        <span className={styles.capturePhase}>{label}</span>
        {total > 0 && (
          <span className={styles.captureCount}>
            {done.toLocaleString()} / {total.toLocaleString()} chunks
          </span>
        )}
      </div>

      <div className={styles.mapFrame} style={{ aspectRatio: hasGrid ? `${w} / ${h}` : '16 / 9' }}>
        {hasGrid ? (
          <svg className={styles.mapSvg} viewBox={`0 0 ${w} ${h}`}
               preserveAspectRatio="xMidYMid meet" shapeRendering="crispEdges">
            {Array.from(cells).map((ch, i) => {
              if (ch === '0') return null
              const dx = i % w, dz = Math.floor(i / w)
              const fill = ch === '2' ? '#a6e3a1' : '#20202a'
              return <rect key={i} x={dx + 0.08} y={dz + 0.08} width={0.84} height={0.84} fill={fill} />
            })}
            {leadCol >= 0 && (
              <rect className={styles.mapScanEdge} x={leadCol} y={0} width={1} height={h} />
            )}
          </svg>
        ) : (
          <div className={styles.mapWaiting}>{PHASE_LABEL[phase] || 'Working…'}</div>
        )}
      </div>

      <div className={styles.progressTrack}>
        <div className={styles.progressFill} style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}
