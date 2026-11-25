import { useState, useEffect, useCallback } from 'react'
import { Toolbar, Btn, IconButton, SectionCard, Field, Input, Toggle, Loading, ErrorBanner, Dialog, DialogButtons, useToast } from '../../ui'
import { IconChunkFill } from './MinecraftIcons'
import styles from './PregenPanel.module.css'

function formatBytes(bytes) {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`
  if (bytes >= 1024 ** 2) return `${Math.round(bytes / 1024 ** 2)} MB`
  return `${Math.round(bytes / 1024)} KB`
}

function formatDuration(seconds) {
  if (seconds >= 3600) return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`
  if (seconds >= 60) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
  return `${seconds}s`
}

// Vanilla's largest coordinate. Beyond this no number describes a real area, and letting
// the field hold one only produces a value the server has to turn down.
const MAX_DISTANCE = 29999984
const clampInt = value => Math.min(MAX_DISTANCE, Math.max(0, parseInt(value, 10) || 0))

// Share of the configured area that already exists. The server counts chunks on disk, not only
// the ones a pregen run wrote, so this is meaningful on a world that was played in first and it
// moves as soon as the distance changes.
const percent = d => (d.chunks > 0 ? Math.floor((d.done / d.chunks) * 100) : 0)

export default function PregenPanel({ token, onExpired }) {
  const [data,    setData]    = useState(null)
  const [dims,    setDims]    = useState([])
  const [loading, setLoading] = useState(true)
  const [saving,  setSaving]  = useState(false)
  const [dirty,   setDirty]   = useState(false)
  const [error,   setError]   = useState(null)
  const [picking, setPicking] = useState(false)
  const toast = useToast()

  const auth = { Authorization: `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [pregenRes, dimRes] = await Promise.all([
        fetch('/api/admin/pregen', { headers: auth }),
        fetch('/api/admin/dims/all', { headers: auth }),
      ])
      if (pregenRes.status === 401 || dimRes.status === 401) { onExpired(); return }

      const pregen = await pregenRes.json()
      if (pregen.error) { setError(pregen.error); return }
      setData(pregen)

      // A bare array of ids. The picker degrades to nothing addable if this fails, which is not
      // fatal: the dimensions already configured still show.
      const dimData = await dimRes.json().catch(() => null)
      setDims(Array.isArray(dimData) ? dimData.filter(d => typeof d === 'string') : [])

      setDirty(false)
    } catch {
      setError('Failed to load pre-generation settings')
    } finally {
      setLoading(false)
    }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load() }, [load])

  // The numbers below are the server's, not the panel's, so a pending distance has to be priced by
  // asking. Debounced because it reads region headers, and skipped until the first load lands.
  const dimKey = (data?.dimensions || []).map(d => d.id).join(',')
  const pending = data?.distance
  useEffect(() => {
    if (!dirty || pending === undefined) return
    // Aborted on the next keystroke so a slow reply for an older number can never land on top of
    // the current one and show a price for something nobody typed.
    const abort = new AbortController()
    const id = setTimeout(async () => {
      try {
        const r = await fetch('/api/admin/pregen/preview', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...auth },
          body: JSON.stringify({ distance: pending, dimensions: dimKey ? dimKey.split(',') : [] }),
          signal: abort.signal,
        })
        if (r.status === 401 || r.status === 403) { onExpired?.(); return }
        const d = await r.json()
        if (d.error) return
        // Keep the typed distance; only the priced rows and the verdict come from the server.
        setData(prev => prev ? { ...prev, ...d, distance: prev.distance } : prev)
      } catch { /* leaving the last good numbers up beats blanking them mid-type */ }
    }, 350)
    return () => { clearTimeout(id); abort.abort() }
  }, [pending, dimKey, dirty]) // eslint-disable-line react-hooks/exhaustive-deps

  async function save() {
    setSaving(true)
    try {
      const r = await fetch('/api/admin/pregen/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({
          enabled: data.enabled,
          distance: data.distance,
          dimensions: (data.dimensions || []).map(d => d.id),
        }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { setData(d); setDirty(false); toast('Pre-generation settings saved') }
    } catch {
      setError('Failed to save')
    } finally {
      setSaving(false)
    }
  }

  function edit(mutate) {
    setData(prev => { const next = structuredClone(prev); mutate(next); return next })
    setDirty(true)
  }

  function addDimension(id) {
    edit(next => {
      next.dimensions = [...(next.dimensions || []),
        { id, present: true, radius_blocks: 0, chunks: 0, done: 0, complete: false, bytes: 0, seconds: 0 }]
    })
    setPicking(false)
  }

  if (loading) return <div className={styles.wrap}><Loading label="Loading pre-generation settings…" /></div>
  if (!data)   return <div className={styles.wrap}><ErrorBanner>{error}</ErrorBanner></div>

  const rows = data.dimensions || []
  const used = new Set(rows.map(d => d.id))
  const unusedDims = dims.filter(d => !used.has(d))

  // Totals are for the work still outstanding, which is what a restart would actually spend.
  const totals = rows.filter(d => d.present).reduce(
    (acc, d) => ({
      chunks:  acc.chunks + Math.max(0, d.chunks - d.done),
      bytes:   acc.bytes + d.bytes,
      seconds: acc.seconds + d.seconds,
    }), { chunks: 0, bytes: 0, seconds: 0 })
  // The server decides when the area is close enough to filling the disk to say so, so the
  // panel and the save route never disagree about where that line is.
  const tight = !!data.disk_warning

  return (
    <div className={styles.wrap}>
      <Toolbar title="Chunk Pre-generation" count={`${rows.length} ${rows.length === 1 ? 'dimension' : 'dimensions'}`}>
        <Btn size="sm" variant="primary" disabled={!dirty || saving || !!data.refusal} onClick={save}>
          {saving ? 'Saving…' : 'Save'}
        </Btn>
        <IconButton tip="Reload" onClick={load}>↻</IconButton>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>

      <div className={styles.body}>
        <SectionCard
          title="Area"
          subtitle="Measured out from spawn, on top of the spawn protection radius."
        >
          <div className={styles.row}>
            <div className={styles.toggleCell}>
              <span className={styles.toggleLabel}>Enable pre-generation</span>
              <Toggle checked={!!data.enabled} onChange={v => edit(next => { next.enabled = v })}
                disabled={data.regen_pending} aria-label="Enable pre-generation" />
            </div>

            <div className={styles.numCell}>
              <Field label="Distance" className={styles.numField}>
                <Input type="number" min={1} max={MAX_DISTANCE} value={data.distance}
                  onChange={e => edit(next => { next.distance = clampInt(e.target.value) })} />
              </Field>
              <span className={styles.unit}>blocks past spawn protection, must fit inside the world border</span>
            </div>

            <div className={styles.rowEnd}>
              <Btn size="sm" disabled={unusedDims.length === 0} onClick={() => setPicking(true)}>
                {unusedDims.length === 0 ? 'Every dimension is listed' : 'Add dimension'}
              </Btn>
            </div>
          </div>

          {data.refusal && <p className={styles.blocked}>{data.refusal}</p>}

          {data.regen_pending && (
            <p className={styles.blocked}>
              A wilderness regen is queued. Cancel it in Commands first, since the two undo each
              other.
            </p>
          )}
        </SectionCard>

        <SectionCard title="What a restart will do" subtitle="Estimated from what this world has written per chunk so far.">
          <div className={styles.stats}>
            <div className={styles.stat}>
              <span className={styles.statValue}>{totals.chunks.toLocaleString()}</span>
              <span className={styles.statLabel}>chunks left</span>
            </div>
            <div className={styles.stat}>
              <span className={`${styles.statValue} ${tight ? styles.statBad : ''}`}>{formatBytes(totals.bytes)}</span>
              <span className={styles.statLabel}>disk needed</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statValue}>{formatDuration(totals.seconds)}</span>
              <span className={styles.statLabel}>time</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statValue}>{formatBytes(data.free_bytes)}</span>
              <span className={styles.statLabel}>disk free</span>
            </div>
          </div>

          {tight && (
            <p className={styles.warning}>
              This would use a large share of your free disk. Backups are written to the same
              drive, so leave room for them.
            </p>
          )}

          {data.restart_required ? (
            <p className={styles.notice}>
              Restart to run this. Nobody can join until it finishes.
            </p>
          ) : (
            <p className={styles.hint}>
              {data.enabled ? 'Nothing left to generate. Startups are normal from here.'
                : 'Off, so startups are unaffected.'}
            </p>
          )}
        </SectionCard>

        {rows.length === 0 ? (
          <div className={styles.empty}>
            <IconChunkFill size={30} />
            <span className={styles.emptyLabel}>No dimensions listed</span>
            <span className={styles.hint}>Nothing is pre-generated until a dimension is added here.</span>
          </div>
        ) : (
          <SectionCard title="Dimensions"
            subtitle="Removing one stops pre-generating it. Chunks already on disk are left alone.">
            <div className={styles.dimList}>
              {rows.map(d => (
                <div key={d.id} className={styles.dimRow}>
                  <span className={styles.dimId}>{d.id}</span>
                  {!d.present ? (
                    <span className={styles.dimMissing}>not on this server</span>
                  ) : (
                    <span className={styles.dimStat}>
                      {`${percent(d)}% of ${d.chunks.toLocaleString()} chunks, radius ${d.radius_blocks.toLocaleString()}`}
                    </span>
                  )}
                  <Btn size="sm" variant="ghost"
                    onClick={() => edit(next => { next.dimensions = next.dimensions.filter(x => x.id !== d.id) })}>
                    Remove
                  </Btn>
                </div>
              ))}
            </div>
          </SectionCard>
        )}
      </div>

      <Dialog open={picking} onOpenChange={setPicking} title="Add a dimension">
        <div className={styles.pickList}>
          {unusedDims.map(id => (
            <button key={id} className={styles.pickRow} onClick={() => addDimension(id)}>
              <span className={styles.pickId}>{id}</span>
              {(data.scales?.[id] || 1) !== 1 && (
                <span className={styles.pickNote}>nether scale, uses a smaller radius</span>
              )}
            </button>
          ))}
        </div>
        <DialogButtons>
          <Btn variant="ghost" onClick={() => setPicking(false)}>Cancel</Btn>
        </DialogButtons>
      </Dialog>
    </div>
  )
}
