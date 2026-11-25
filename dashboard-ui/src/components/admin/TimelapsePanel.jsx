import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { streamingDownload, hasFileSystemAccess } from '../../lib/streamingDownload'
import DownloadProgress from '../DownloadProgress'
import { Toolbar, IconButton, Btn, Toggle, Field, Input, SectionCard, EmptyState, ErrorBanner, ConfirmDialog } from '../../ui'
import { IconMapScroll } from './MinecraftIcons'
import styles from './TimelapsePanel.module.css'

const SPEEDS = [1, 2, 5, 10, 20]

// Short label for a dimension id: drop the namespace when it is minecraft.
function dimLabel(id) {
  if (!id) return ''
  const [ns, path] = id.split(':')
  return path ? (ns === 'minecraft' ? path : id) : id
}

// An expired session is answered with 403; 401 only ever comes from the login route.
function isExpired(res) { return res.status === 401 || res.status === 403 }

// Frames are served cacheable, so <img> can load them straight from the browser
// cache. The signed per-dimension token stands in for the auth header an <img>
// cannot send.
function frameUrl(dim, name, token) {
  return `/api/admin/timelapse/frame?dim=${encodeURIComponent(dim)}`
       + `&name=${encodeURIComponent(name)}&t=${encodeURIComponent(token)}`
}

export default function TimelapsePanel({ token, onExpired }) {
  const [confirmFrame, setConfirmFrame] = useState(null)
  const [dims,       setDims]       = useState([]) // [{id, sizeBytes, frameToken, frames(oldest-first)}]
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
  const [maxHeap,  setMaxHeap]  = useState(0)   // server -Xmx in bytes, from JVM
  const [progress, setProgress] = useState(null) // live capture progress while running
  const [exporting,  setExporting]  = useState(false)
  const [dlProgress, setDlProgress] = useState(null)

  const auth    = { Authorization: `Bearer ${token}` }
  const pollRef = useRef(null)
  const playRef = useRef(null)

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
      if (isExpired(r)) { onExpired(); return }
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

  // Reset playback to the start whenever the viewed dimension changes.
  useEffect(() => { setPlaying(false); setIdx(0) }, [viewDim])

  // Keep the stored index inside range. The rendered index is clamped separately,
  // so a dimension with fewer frames never renders against a stale one.
  useEffect(() => { setIdx(i => Math.min(i, Math.max(0, frames.length - 1))) }, [frames.length])

  const safeIdx    = Math.min(idx, Math.max(0, frames.length - 1))
  const current    = frames[safeIdx]
  // The server buckets this so it is byte-identical across polls, which keeps the
  // frame URLs stable and therefore cacheable.
  const frameToken = dimEntry?.frameToken
  const srcUrl     = current && frameToken ? frameUrl(viewDim, current.name, frameToken) : null

  // Warm the next few frames into the browser cache so playback does not stall.
  // The cache dedupes by URL, so this cannot produce a duplicate download.
  useEffect(() => {
    if (!frameToken) return
    for (let k = 1; k <= 3; k++) {
      const next = frames[safeIdx + k]
      if (next) new Image().src = frameUrl(viewDim, next.name, frameToken)
    }
  }, [frames, safeIdx, viewDim, frameToken])

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
      if (isExpired(r)) { onExpired(); return }
      const d = await r.json()
      if (d.error) { if (r.status === 409) flash('A capture is already running'); else setError(d.error) }
      else { flash('Capturing frames…'); setRunning(true) }
    } catch { setError('Failed to start capture') }
  }

  // Download via authenticated fetch (a plain <a href> sends no Authorization
  // header, so the export route 403s whenever a dashboard password is set).
  async function exportFrames() {
    if (frames.length === 0 || !viewDim || exporting) return
    setError(null)
    setExporting(true)
    setDlProgress({ received: 0, total: 0, speed: 0, eta: null })
    const result = await streamingDownload({
      url: `/api/admin/timelapse/export?dim=${encodeURIComponent(viewDim)}`,
      filename: `timelapse-${viewDim.replace(':', '_')}.zip`,
      headers: auth,
      onProgress: setDlProgress,
      allowBlobFallback: true,
    })
    setExporting(false)
    setDlProgress(null)
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
      if (isExpired(r)) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { flash('Frame deleted'); loadList() }
    } catch { setError('Delete failed') }
  }

  function fmtDate(ms) {
    return new Date(ms).toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
  }

  return (
    <div className={styles.wrap}>
      <Toolbar title="Timelapse" count={`${frames.length} frames${totalBytes > 0 ? ` · ${fmtBytes(totalBytes)}` : ''}`}>
        <IconButton tip="Refresh" onClick={() => loadList()} disabled={loading}>↻</IconButton>
        <Btn size="sm" onClick={exportFrames} disabled={frames.length === 0 || exporting}>
          {exporting ? 'Exporting…' : 'Export .zip'}
        </Btn>
        <Btn size="sm" variant="primary" onClick={captureNow} disabled={running}>{running ? 'Capturing…' : 'Capture Now'}</Btn>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>
      {status && <div className={styles.status}>{status}</div>}

      {dlProgress && (
        <div className={styles.exportBar}>
          <span className={styles.exportLabel}>Exporting {dimLabel(viewDim)}…</span>
          <DownloadProgress {...dlProgress} />
          {!hasFileSystemAccess() && (
            <span className={styles.exportCaveat}>
              Your browser holds the archive in memory while downloading. Chrome, Edge or Brave
              stream straight to disk and are better for a large frame set.
            </span>
          )}
        </div>
      )}

      <div className={styles.body}>
        <CaptureSettings
          token={token}
          onExpired={onExpired}
          enabled={enabled}
          interval={interval}
          available={available}
          maxHeap={maxHeap}
          onEnabledChange={setEnabled}
          onIntervalChange={setInterval_}
          onError={setError}
          onFlash={flash}
        />

        {running && <CaptureMap progress={progress} />}

        {dims.length === 0 ? (
          <EmptyState icon={<IconMapScroll size={30} />} label="No frames yet" hint="Top-down frames you can play back here" />
        ) : (
          <SectionCard
            title="Playback"
            actions={
              <label className={styles.viewSel}>
                <span>Dimension</span>
                <select value={viewDim} onChange={e => setViewDim(e.target.value)}>{dims.map(d => <option key={d.id} value={d.id}>{dimLabel(d.id)}</option>)}</select>
              </label>
            }
          >
            <FrameViewer key={viewDim} src={srcUrl} alt={`Frame ${safeIdx + 1}`} />

            <div className={styles.timestamp}>
              {current && (
                <>
                  <span>{fmtDate(current.capturedAt)} · frame {safeIdx + 1} / {frames.length}</span>
                  {(() => { const b = resBadge(current.blocksPerPixel); return <span className={b.full ? styles.badgeFull : styles.badgeDown}>{b.text}</span> })()}
                  {dimEntry && <span className={styles.viewSize}>{fmtBytes(dimEntry.sizeBytes)} this dim</span>}
                </>
              )}
            </div>

            <input className={styles.scrub} type="range" min={0} max={Math.max(0, frames.length - 1)} value={safeIdx} onChange={e => { setPlaying(false); setIdx(Number(e.target.value)) }} />

            <div className={styles.controls}>
              <Btn size="sm" variant="ghost" onClick={() => { setPlaying(false); setIdx(0) }}>⏮</Btn>
              <Btn size="sm" variant="ghost" onClick={() => { setPlaying(false); setIdx(i => Math.max(0, i - 1)) }}>◀</Btn>
              <Btn size="sm" variant="primary" onClick={() => { if (safeIdx >= frames.length - 1) setIdx(0); setPlaying(p => !p) }}>{playing ? '⏸ Pause' : '▶ Play'}</Btn>
              <Btn size="sm" variant="ghost" onClick={() => { setPlaying(false); setIdx(i => Math.min(frames.length - 1, i + 1)) }}>▶</Btn>
              <Btn size="sm" variant="ghost" onClick={() => { setPlaying(false); setIdx(frames.length - 1) }}>⏭</Btn>
              <label className={styles.speed}><span>Speed</span><select value={speed} onChange={e => setSpeed(Number(e.target.value))}>{SPEEDS.map(s => <option key={s} value={s}>{s} fps</option>)}</select></label>
              <span className={styles.ctrlSpacer} />
              {current && <Btn size="sm" variant="danger" onClick={() => setConfirmFrame(current.name)}>Delete frame</Btn>}
            </div>
          </SectionCard>
        )}
      </div>

      <ConfirmDialog
        open={!!confirmFrame} onOpenChange={o => !o && setConfirmFrame(null)}
        title={confirmFrame ? `Delete frame ${confirmFrame}?` : ''}
        description="The render is removed from disk. A frame captures one moment, so this one cannot be made again."
        confirmLabel="Delete frame" tone="danger"
        onConfirm={() => deleteFrame(confirmFrame)}
      />
    </div>
  )
}

function fmtBytes(b) {
  if (b >= 1024 ** 3) return `${(b / 1024 ** 3).toFixed(2)} GB`
  if (b >= 1024 ** 2) return `${Math.round(b / 1024 ** 2)} MB`
  return `${Math.round(b / 1024)} KB`
}

// Everything that writes to /api/admin/config. Kept apart from playback: the two
// share no state, and a new capture setting should not mean editing the viewer.
function CaptureSettings({ token, onExpired, enabled, interval, available, maxHeap,
                           onEnabledChange, onIntervalChange, onError, onFlash }) {
  const [cfg, setCfg] = useState({ dimensions: ['minecraft:overworld'], maxRenderMb: 0, maxSkips: 3, maxFrames: 0 })
  const auth = { Authorization: `Bearer ${token}` }

  useEffect(() => {
    fetch('/api/admin/config', { headers: auth })
      .then(r => isExpired(r) ? null : r.json())
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
      if (isExpired(r)) { onExpired(); return false }
      const d = await r.json()
      if (d.error) { onError(d.error); return false }
      return true
    } catch { onError('Failed to update setting'); return false }
  }

  async function toggleEnabled(next) {
    onEnabledChange(next)
    if (await patchConfig({ timelapse_enabled: next })) onFlash(next ? 'Timelapse capture on' : 'Timelapse capture off')
    else onEnabledChange(!next)
  }

  async function saveInterval(mins) {
    const v = Math.max(1, Number(mins) || 0)
    onIntervalChange(v)
    await patchConfig({ timelapse_interval_minutes: v })
  }

  async function saveCfgField(key, apiKey, value, min) {
    const v = min != null ? Math.max(min, Number(value) || 0) : value
    setCfg(c => ({ ...c, [key]: v }))
    if (await patchConfig({ [apiKey]: v })) onFlash('Saved')
  }

  // Opt a dimension into (or out of) periodic + manual capture.
  async function toggleCaptureDim(id, on) {
    const next = on ? [...new Set([...cfg.dimensions, id])] : cfg.dimensions.filter(d => d !== id)
    setCfg(c => ({ ...c, dimensions: next }))
    if (!await patchConfig({ timelapse_dimensions: next })) setCfg(c => ({ ...c, dimensions: cfg.dimensions }))
    else onFlash('Saved')
  }

  // A forced (players-online) capture is bounded by the RAM cap; 0 = auto, which
  // fits the render to free heap and downsamples only if a capture would not fit.
  const capBytes = cfg.maxRenderMb > 0 ? cfg.maxRenderMb * 1024 ** 2 : 0
  const heapPct  = capBytes > 0 && maxHeap > 0 ? capBytes / maxHeap : 0

  return (
    <SectionCard title="Capture Settings" subtitle="One pixel per block, per selected dimension. Runs when idle, forced after the skip limit.">
      <div className={styles.toggleRow}>
        {/* This is the feature flag: off stops manual captures and /smp timelapse too. */}
        <span className={styles.tLabel}>Timelapse capture{enabled ? `, automatically every ${interval} min` : ', off entirely'}</span>
        <Toggle checked={enabled} onChange={toggleEnabled} aria-label="Timelapse capture" />
      </div>

      <Field label="Capture these dimensions">
        <div className={styles.dimList}>
          {available.length === 0
            ? <span className={styles.dimEmpty}>No dimensions reported by the server yet.</span>
            : available.map(id => (
                <label key={id} className={`${styles.dimCheck} ${cfg.dimensions.includes(id) ? styles.dimOn : ''}`}>
                  <input type="checkbox" checked={cfg.dimensions.includes(id)} onChange={e => toggleCaptureDim(id, e.target.checked)} />
                  {dimLabel(id)}
                </label>
              ))}
        </div>
      </Field>

      <div className={styles.fieldGrid}>
        <Field label="Interval (minutes)"><Input type="number" min={1} value={interval} onChange={e => onIntervalChange(Number(e.target.value))} onBlur={e => saveInterval(e.target.value)} /></Field>
        <Field label="Max render RAM (MB, 0 = auto)"><Input type="number" min={0} value={cfg.maxRenderMb} onChange={e => setCfg(c => ({ ...c, maxRenderMb: Number(e.target.value) }))} onBlur={e => saveCfgField('maxRenderMb', 'timelapse_max_render_mb', e.target.value, 0)} /></Field>
        <Field label="Idle skips before forcing (0 = every interval)"><Input type="number" min={0} value={cfg.maxSkips} onChange={e => setCfg(c => ({ ...c, maxSkips: Number(e.target.value) }))} onBlur={e => saveCfgField('maxSkips', 'timelapse_max_skips', e.target.value, 0)} /></Field>
        <Field label="Max frames (0 = keep all)"><Input type="number" min={0} value={cfg.maxFrames} onChange={e => setCfg(c => ({ ...c, maxFrames: Number(e.target.value) }))} onBlur={e => saveCfgField('maxFrames', 'timelapse_max_frames', e.target.value, 0)} /></Field>
      </div>

      <div className={styles.memRow}>
        {capBytes > 0
          ? <><span className={styles.memValue}>up to {fmtBytes(capBytes)}</span><span className={styles.memLabel}>RAM cap for a forced capture{maxHeap > 0 && ` · ${Math.round(heapPct * 100)}% of server heap (${fmtBytes(maxHeap)})`}</span></>
          : <span className={styles.memLabel}>Auto: fits each capture to free server RAM, downsampling only if it would not fit.</span>}
      </div>
    </SectionCard>
  )
}

const MIN_ZOOM = 1
const MAX_ZOOM = 16

// Pan and zoom viewport for one frame. The transform sits on a wrapper with
// transform-origin 0 0, so a content point maps to screen as t + z * p, and
// holding the point under the cursor while zooming is t' = c - (c - t) * z'/z.
// The image itself is never rescaled, so the framing survives frame changes:
// zoom into one base and playback runs through the frames right there.
function FrameViewer({ src, alt }) {
  const [view, setView]         = useState({ z: MIN_ZOOM, x: 0, y: 0 })
  const [dragging, setDragging] = useState(false)
  // Scale the fitted image sits at before zoom, so the pixels can be kept crisp
  // when magnified and smoothed when shrunk.
  const [fitScale, setFitScale] = useState(1)
  const stageRef = useRef(null)
  const imgRef   = useRef(null)
  const dragRef  = useRef(null)

  // Keep the image covering the viewport while it is bigger than it, and
  // centred once it is not, so panning never reveals dead space.
  const clamp = useCallback(v => {
    const st = stageRef.current, im = imgRef.current
    const z = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, v.z))
    if (!st || !im || !im.naturalWidth) return { z, x: 0, y: 0 }
    const W = st.clientWidth, H = st.clientHeight
    const fit = Math.min(W / im.naturalWidth, H / im.naturalHeight)
    // Size and letterbox offset of the fitted image at zoom 1.
    const dw = im.naturalWidth * fit, dh = im.naturalHeight * fit
    const axis = (t, off, size, extent) => {
      const scaled = size * z
      if (scaled <= extent) return (extent - scaled) / 2 - off * z
      return Math.min(-off * z, Math.max(extent - (off + size) * z, t))
    }
    return { z, x: axis(v.x, (W - dw) / 2, dw, W), y: axis(v.y, (H - dh) / 2, dh, H) }
  }, [])

  const measure = useCallback(() => {
    const st = stageRef.current, im = imgRef.current
    if (!st || !im || !im.naturalWidth) return
    setFitScale(Math.min(st.clientWidth / im.naturalWidth, st.clientHeight / im.naturalHeight))
  }, [])

  // Zoom to z, keeping the point (cx, cy) measured from the stage top-left fixed.
  const zoomTo = useCallback((nextZ, cx, cy) => {
    setView(v => {
      const z = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, nextZ))
      const k = z / v.z
      return clamp({ z, x: cx - (cx - v.x) * k, y: cy - (cy - v.y) * k })
    })
  }, [clamp])

  // Buttons zoom about the middle of the viewport.
  function atCentre(factor) {
    const st = stageRef.current
    if (!st) return
    zoomTo(view.z * factor, st.clientWidth / 2, st.clientHeight / 2)
  }

  // React registers wheel listeners passively, so preventDefault needs a native
  // one, otherwise the panel scrolls away while zooming.
  useEffect(() => {
    const st = stageRef.current
    if (!st) return
    const onWheel = e => {
      e.preventDefault()
      const r = st.getBoundingClientRect()
      // deltaMode 1 is lines, not pixels (Firefox, some mice).
      const dy = e.deltaMode === 1 ? e.deltaY * 16 : e.deltaY
      setView(v => {
        const z = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, v.z * Math.exp(-dy * 0.0015)))
        const k = z / v.z
        const cx = e.clientX - r.left, cy = e.clientY - r.top
        return clamp({ z, x: cx - (cx - v.x) * k, y: cy - (cy - v.y) * k })
      })
    }
    st.addEventListener('wheel', onWheel, { passive: false })
    return () => st.removeEventListener('wheel', onWheel)
  }, [clamp])

  // A narrower stage allows less pan, so re-clamp when it resizes.
  useEffect(() => {
    const onResize = () => { measure(); setView(v => clamp(v)) }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [clamp, measure])

  function onPointerDown(e) {
    if (e.button !== 0 || view.z <= MIN_ZOOM) return
    dragRef.current = { id: e.pointerId, x: e.clientX, y: e.clientY }
    setDragging(true)
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  function onPointerMove(e) {
    const d = dragRef.current
    if (!d || d.id !== e.pointerId) return
    const dx = e.clientX - d.x, dy = e.clientY - d.y
    d.x = e.clientX; d.y = e.clientY
    setView(v => clamp({ ...v, x: v.x + dx, y: v.y + dy }))
  }
  function endDrag(e) {
    if (dragRef.current?.id !== e.pointerId) return
    dragRef.current = null
    setDragging(false)
    if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId)
  }
  function onDoubleClick(e) {
    if (view.z > MIN_ZOOM) { setView({ z: MIN_ZOOM, x: 0, y: 0 }); return }
    const r = stageRef.current.getBoundingClientRect()
    zoomTo(4, e.clientX - r.left, e.clientY - r.top)
  }

  const zoomed = view.z > MIN_ZOOM
  return (
    <div
      ref={stageRef}
      className={`${styles.stage} ${zoomed ? (dragging ? styles.grabbing : styles.grabbable) : ''}`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onDoubleClick={onDoubleClick}
    >
      <div className={styles.panLayer} style={{ transform: `translate(${view.x}px, ${view.y}px) scale(${view.z})` }}>
        {src
          ? <img
              ref={imgRef}
              className={styles.frame}
              src={src}
              alt={alt}
              draggable={false}
              // Nearest-neighbour drops rows when the map is shown smaller than
              // life size, which reads as lost detail on a 1:1 render.
              style={{ imageRendering: fitScale * view.z >= 1 ? 'pixelated' : 'auto' }}
              onLoad={() => { measure(); setView(v => clamp(v)) }}
            />
          : <div className={styles.frameLoading}>Loading frame…</div>}
      </div>

      <div className={styles.zoomBar} onPointerDown={e => e.stopPropagation()} onDoubleClick={e => e.stopPropagation()}>
        <button type="button" className={styles.zoomBtn} onClick={() => atCentre(1 / 1.5)} disabled={!zoomed} title="Zoom out">−</button>
        <span className={styles.zoomLevel}>{view.z < 10 ? view.z.toFixed(1) : Math.round(view.z)}×</span>
        <button type="button" className={styles.zoomBtn} onClick={() => atCentre(1.5)} disabled={view.z >= MAX_ZOOM} title="Zoom in">+</button>
        <button type="button" className={styles.zoomBtn} onClick={() => setView({ z: MIN_ZOOM, x: 0, y: 0 })} disabled={!zoomed} title="Fit to view">⤢</button>
      </div>

      {!zoomed && <div className={styles.zoomHint}>scroll to zoom · drag to pan</div>}
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
// renderer's west-to-east order: each poll lights up the chunks painted since
// the last one, so the world fills in left to right behind a glowing edge.
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

  // The frontmost painted column, for the glowing scan edge.
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
              return <rect key={i} x={dx + 0.08} y={dz + 0.08} width={0.84} height={0.84} fill="#a6e3a1" />
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
