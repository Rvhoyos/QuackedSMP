import { useState, useEffect, useCallback } from 'react'
import { Toolbar, Btn, IconButton, SectionCard, Field, Input, Toggle, Loading, ErrorBanner, Dialog, DialogButtons, useToast } from '../../ui'
import { IconChorusFruit } from './MinecraftIcons'
import RtpProfileCard from './RtpProfileCard'
import styles from './RtpPanel.module.css'

// Keys arrive snake_cased because the backend serialises the same POJOs that write
// quackedsmp.json, so the panel and the config file share one shape.
const NEW_PROFILE = {
  enabled: true,
  min_distance: 0,
  max_distance: 3000,
  spawn_bias: 1.0,
  allow_water: false,
  max_attempts: 24,
  message: '&aWhoosh! You landed &e{distance}&a blocks from spawn.',
  reward_cooldown_seconds: 0,
  effects: [],
  items: [],
}

export default function RtpPanel({ token, onExpired }) {
  const [enabled, setEnabled] = useState(false)
  const [config,  setConfig]  = useState(null)
  const [dims,    setDims]    = useState([])
  const [registry, setRegistry] = useState({ effects: [], items: [] })
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
      const [rtpRes, dimRes, regRes] = await Promise.all([
        fetch('/api/admin/rtp', { headers: auth }),
        fetch('/api/admin/dims/all', { headers: auth }),
        fetch('/api/admin/registry', { headers: auth }),
      ])
      if (rtpRes.status === 401 || dimRes.status === 401) { onExpired(); return }

      const rtp = await rtpRes.json()
      if (rtp.error) { setError(rtp.error); return }
      setEnabled(!!rtp.enabled)
      setConfig(rtp.config || { warmup_seconds: 5, cooldown_seconds: 300, profiles: [] })

      const dimData = await dimRes.json().catch(() => null)
      setDims(dimensionIds(dimData))

      // The pickers degrade to plain text entry if this fails, so a bad response is not fatal.
      const reg = await regRes.json().catch(() => null)
      if (reg && !reg.error) setRegistry({ effects: reg.effects || [], items: reg.items || [] })

      setDirty(false)
    } catch {
      setError('Failed to load RTP settings')
    } finally {
      setLoading(false)
    }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load() }, [load])

  async function save() {
    setSaving(true)
    try {
      const r = await fetch('/api/admin/rtp/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ enabled, config }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { toast('RTP settings saved'); setDirty(false) }
    } catch {
      setError('Failed to save')
    } finally {
      setSaving(false)
    }
  }

  function edit(mutate) {
    setConfig(prev => { const next = structuredClone(prev); mutate(next); return next })
    setDirty(true)
  }

  function setGlobal(key, value) { edit(next => { next[key] = value }) }
  function setProfile(index, profile) { edit(next => { next.profiles[index] = profile }) }
  function removeProfile(index) { edit(next => { next.profiles.splice(index, 1) }) }

  // A profile IS a dimension, so the dimension is chosen once here and never edited afterwards,
  // and a dimension that already has one is not offered again.
  function addProfile(dimension) {
    edit(next => { next.profiles = [...(next.profiles || []), { ...NEW_PROFILE, dimension }] })
    setPicking(false)
  }

  if (loading) return <div className={styles.wrap}><Loading label="Loading RTP settings…" /></div>

  const profiles = config?.profiles || []
  const used = new Set(profiles.map(p => p.dimension))
  const unusedDims = dims.filter(d => !used.has(d))

  return (
    <div className={styles.wrap}>
      <Toolbar title="Random Teleport" count={`${profiles.length} ${profiles.length === 1 ? 'profile' : 'profiles'}`}>
        <Btn size="sm" variant="primary" disabled={!dirty || saving} onClick={save}>
          {saving ? 'Saving…' : 'Save'}
        </Btn>
        <IconButton tip="Reload" onClick={load}>↻</IconButton>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>

      <div className={styles.body}>
        <SectionCard
          title="Teleport"
          subtitle="Applies to every dimension. Distance and rewards are per profile below."
        >
          <div className={styles.toggleRow}>
            <span className={styles.toggleLabel}>Enable /rtp</span>
            <Toggle checked={enabled} onChange={v => { setEnabled(v); setDirty(true) }} aria-label="Enable RTP" />
          </div>

          <div className={styles.row}>
            <Field label="Warmup" hint="seconds, cancels on move or damage" className={styles.num}>
              <Input type="number" min={0} value={config.warmup_seconds}
                onChange={e => setGlobal('warmup_seconds', clampInt(e.target.value, 0))} />
            </Field>
            <Field label="Cooldown" hint="seconds, per player" className={styles.num}>
              <Input type="number" min={0} value={config.cooldown_seconds}
                onChange={e => setGlobal('cooldown_seconds', clampInt(e.target.value, 0))} />
            </Field>
            <span className={styles.grow} />
          </div>
        </SectionCard>

        {profiles.length === 0 ? (
          <div className={styles.empty}>
            <IconChorusFruit size={30} />
            <span className={styles.emptyLabel}>No dimension profiles yet</span>
            <span className={styles.hint}>/rtp only works in dimensions that have a profile.</span>
          </div>
        ) : profiles.map((profile, i) => (
          <RtpProfileCard
            key={profile.dimension}
            profile={profile}
            registry={registry}
            token={token}
            onExpired={onExpired}
            onChange={next => setProfile(i, next)}
            onRemove={() => removeProfile(i)}
          />
        ))}

        <div className={styles.addRow}>
          <Btn size="sm" disabled={unusedDims.length === 0} onClick={() => setPicking(true)}>
            {unusedDims.length === 0 ? 'Every dimension has a profile' : 'Add dimension profile'}
          </Btn>
        </div>

        <Dialog open={picking} onOpenChange={setPicking} title="Which dimension?"
          description="One profile per dimension. Dimensions that already have one are not listed.">
          <div className={styles.pickList}>
            {unusedDims.map(d => (
              <button key={d} className={styles.pickRow} onClick={() => addProfile(d)}>
                <span className={styles.pickName}>{shortDim(d)}</span>
                <span className={styles.pickId}>{d}</span>
              </button>
            ))}
          </div>
          <DialogButtons>
            <Btn onClick={() => setPicking(false)}>Cancel</Btn>
          </DialogButtons>
        </Dialog>
      </div>
    </div>
  )
}

function clampInt(raw, min) {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) ? Math.max(min, n) : min
}

function shortDim(id) {
  const path = id.includes(':') ? id.split(':')[1] : id
  return path.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

// /api/admin/dims/all answers with a plain array of dimension ids.
function dimensionIds(data) {
  return Array.isArray(data) ? data.filter(d => typeof d === 'string') : []
}
