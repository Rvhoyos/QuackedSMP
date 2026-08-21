import { useState, useEffect, useCallback } from 'react'
import { Toolbar, Btn, IconButton, SectionCard, Field, Input, Toggle, Loading, ErrorBanner, useToast } from '../../ui'
import { IconLoot } from './MinecraftIcons'
import KitCard from './KitCard'
import styles from './KitsPanel.module.css'

// Keys arrive snake_cased because the backend serialises the same POJOs that write
// quackedsmp.json, so the panel and the config file share one shape.
const NEW_KIT = {
  name: '',
  display_name: '',
  min_tier: 0,
  give_welcome_book: false,
  armor: { head: null, chest: null, legs: null, feet: null },
  items: [],
}

export default function KitsPanel({ token, onExpired, onNavigate, features }) {
  const [enabled,  setEnabled]  = useState(false)
  const [cooldown, setCooldown] = useState(86400)
  const [kits,     setKits]     = useState([])
  const [registry, setRegistry] = useState({ effects: [], items: [] })
  const [loading,  setLoading]  = useState(true)
  const [saving,   setSaving]   = useState(false)
  const [dirty,    setDirty]    = useState(false)
  const [error,    setError]    = useState(null)
  const toast = useToast()

  const auth = { Authorization: `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [kitRes, regRes] = await Promise.all([
        fetch('/api/admin/kits', { headers: auth }),
        fetch('/api/admin/registry', { headers: auth }),
      ])
      if (kitRes.status === 401) { onExpired(); return }

      const data = await kitRes.json()
      if (data.error) { setError(data.error); return }
      setEnabled(!!data.enabled)
      setCooldown(data.cooldownSeconds ?? 86400)
      setKits(Array.isArray(data.kits) ? data.kits : [])

      // The picker degrades to nothing selectable if this fails, so a bad response is not fatal.
      const reg = await regRes.json().catch(() => null)
      if (reg && !reg.error) setRegistry({ effects: reg.effects || [], items: reg.items || [] })

      setDirty(false)
    } catch {
      setError('Failed to load kits')
    } finally {
      setLoading(false)
    }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load() }, [load])

  async function save() {
    setSaving(true)
    try {
      const r = await fetch('/api/admin/kits/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ enabled, cooldownSeconds: cooldown, kits }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { toast('Kits saved'); setDirty(false); setError(null) }
    } catch {
      setError('Failed to save')
    } finally {
      setSaving(false)
    }
  }

  function edit(next) { setKits(next); setDirty(true) }

  if (loading) return <div className={styles.wrap}><Loading label="Loading kits…" /></div>

  return (
    <div className={styles.wrap}>
      <Toolbar title="Kits" count={`${kits.length} ${kits.length === 1 ? 'kit' : 'kits'}`}>
        <Btn size="sm" variant="primary" disabled={!dirty || saving} onClick={save}>
          {saving ? 'Saving…' : 'Save'}
        </Btn>
        <IconButton tip="Reload" onClick={load}>↻</IconButton>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>

      <div className={styles.body}>
        <SectionCard title="Kits" subtitle="Applies to every kit. Contents are per kit below."
>
          <div className={styles.settingsRow}>
            <div className={styles.toggleCell}>
              <span className={styles.toggleLabel}>Enable kits</span>
              <Toggle checked={enabled} onChange={v => { setEnabled(v); setDirty(true) }}
                aria-label="Enable kits" />
            </div>
            <div className={styles.numCell}>
              <Field label="Cooldown" className={styles.numField}>
                <Input type="number" min={0} value={Math.round(cooldown / 3600)}
                  onChange={e => { setCooldown(clampInt(e.target.value, 0) * 3600); setDirty(true) }} />
              </Field>
              <span className={styles.unit}>hours between claims, shared by all kits</span>
            </div>

            <div className={styles.rowEnd}>
              <Btn size="sm" onClick={() => edit([...kits, structuredClone(NEW_KIT)])}>Add kit</Btn>
            </div>
          </div>
        </SectionCard>

        {kits.length === 0 ? (
          <div className={styles.empty}>
            <IconLoot size={30} />
            <span className={styles.emptyLabel}>No kits yet</span>
            <span className={styles.hint}>A kit is claimed in game with /smp kit.</span>
          </div>
        ) : kits.map((kit, i) => (
          <KitCard key={i}
            kit={kit}
            registry={registry}
            features={features}
            onNavigate={onNavigate}
            onChange={next => edit(kits.map((k, j) => (j === i ? next : k)))}
            onRemove={() => edit(kits.filter((_, j) => j !== i))}
          />
        ))}
      </div>
    </div>
  )
}

function clampInt(raw, min) {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) ? Math.max(min, n) : min
}
