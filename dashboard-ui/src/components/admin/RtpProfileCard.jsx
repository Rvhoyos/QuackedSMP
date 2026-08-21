import { useState } from 'react'
import { SectionCard, Btn, Field, Input, Toggle, Slider, Badge } from '../../ui'
import EffectList from './EffectList'
import ItemStackList from './ItemStackList'
import styles from './RtpPanel.module.css'

export default function RtpProfileCard({ profile, registry, token, onExpired, onChange, onRemove }) {
  const [open, setOpen] = useState(true)

  function set(key, value) { onChange({ ...profile, [key]: value }) }

  return (
    <SectionCard
      title={shortDim(profile.dimension)}
      subtitle={profile.dimension}
      actions={
        <>
          {!profile.enabled && <Badge variant="warn">Off</Badge>}
          <Btn size="sm" variant="ghost" onClick={() => setOpen(o => !o)}>{open ? 'Collapse' : 'Expand'}</Btn>
          <Btn size="sm" variant="danger" onClick={onRemove}>Remove</Btn>
        </>
      }
    >
      {!open ? null : (
        <>
          <div className={styles.row}>
            <Field label="Min" hint="+ spawn radius" className={styles.num}>
              <Input type="number" min={0} value={profile.min_distance}
                onChange={e => set('min_distance', clampInt(e.target.value, 0))} />
            </Field>
            <Field label="Max" hint="0 = border" className={styles.num}>
              <Input type="number" min={0} value={profile.max_distance}
                onChange={e => set('max_distance', clampInt(e.target.value, 0))} />
            </Field>
            <Field label="Attempts" hint="1 per tick" className={styles.num}>
              <Input type="number" min={1} value={profile.max_attempts}
                onChange={e => set('max_attempts', clampInt(e.target.value, 1))} />
            </Field>
            <label className={styles.inlineCheck}>
              <input type="checkbox" checked={!!profile.allow_water}
                onChange={e => set('allow_water', e.target.checked)} />
              <span>Allow water</span>
            </label>
            <label className={styles.inlineCheck}>
              <input type="checkbox" checked={!!profile.enabled}
                onChange={e => set('enabled', e.target.checked)} />
              <span>Enabled</span>
            </label>
          </div>

          <div className={styles.row}>
            <Field label="Spawn bias" hint={biasHint(profile)} className={styles.grow}>
              <Slider value={profile.spawn_bias ?? 1} min={1} max={4} step={0.1}
                onChange={v => set('spawn_bias', v)} />
            </Field>
          </div>

          <div className={styles.row}>
            <Field label="Arrival message" hint="& colour codes. {distance} = blocks from spawn." className={styles.grow}>
              <Input value={profile.message || ''} onChange={e => set('message', e.target.value)} />
            </Field>
            <Field label="Reward cooldown" hint="seconds, 0 = once ever" className={styles.num}>
              <Input type="number" min={0} value={profile.reward_cooldown_seconds ?? 0}
                onChange={e => set('reward_cooldown_seconds', clampInt(e.target.value, 0))} />
            </Field>
          </div>

          <EffectList
            title="Arrival effects"
            effects={profile.effects || []}
            catalog={registry.effects}
            onChange={effects => set('effects', effects)}
          />

          <ItemStackList
            title="Arrival items"
            items={profile.items || []}
            catalog={registry.items}
            token={token}
            onExpired={onExpired}
            onChange={items => set('items', items)}
          />

        </>
      )}
    </SectionCard>
  )
}

// Radius is drawn as min + (max - min) * u^bias with u uniform, so the median lands at
// 0.5^bias of the way across the band. Saying that in blocks beats describing the curve.
function biasHint(profile) {
  const bias = profile.spawn_bias ?? 1
  const min = profile.min_distance ?? 0
  const max = Math.max(min, profile.max_distance ?? 0)
  if (max <= min) return 'Set a maximum distance to see the effect.'
  const median = Math.round(min + (max - min) * Math.pow(0.5, bias))
  const inner = Math.round(Math.pow(0.5, 1 / bias) * 100)
  return bias <= 1.05
    ? `Even spread. Half of all landings fall inside ${median} blocks.`
    : `Half of all landings fall inside ${median} blocks, and ${inner}% land in the near half of the range.`
}

function clampInt(raw, min) {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) ? Math.max(min, n) : min
}

function shortDim(id) {
  if (!id) return 'New profile'
  const path = id.includes(':') ? id.split(':')[1] : id
  return path.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}
