import { useState } from 'react'
import { SectionCard, Btn, IconButton, Field, Input, Slider, Badge, SwitchField, ConfirmDialog } from '../../ui'
import { IconCollapse, IconExpand } from './MinecraftIcons'
import GuideToggle from './GuideToggle'
import EffectList from './EffectList'
import ItemStackList from './ItemStackList'
import styles from './RtpPanel.module.css'

export default function RtpProfileCard({ profile, registry, features, onNavigate, onChange, onRemove }) {
  const [open, setOpen] = useState(true)
  const [confirm, setConfirm] = useState(false)

  function set(key, value) { onChange({ ...profile, [key]: value }) }

  return (
    <SectionCard
      title={shortDim(profile.dimension)}
      subtitle={profile.dimension}
      collapsed={!open}
      actions={
        <>
          {!profile.enabled && <Badge variant="warn">Off</Badge>}
          <Btn size="sm" variant="danger" onClick={() => setConfirm(true)}>Remove</Btn>
          <IconButton tip={open ? 'Collapse' : 'Expand'} onClick={() => setOpen(o => !o)}>
            {open ? <IconCollapse /> : <IconExpand />}
          </IconButton>
        </>
      }
    >
      {(
        <>
          {/* Switches first, together, so the three booleans read as one group of state
              rather than being scattered between the numbers they have nothing to do with. */}
          <div className={styles.switchRow}>
            <SwitchField
              label="Enabled" hint="/rtp works in this dimension"
              checked={!!profile.enabled} onChange={v => set('enabled', v)} />
            <SwitchField
              label="Land in water" hint="off skips oceans and lakes"
              checked={!!profile.allow_water} onChange={v => set('allow_water', v)} />
            <GuideToggle
              label="Give the guide"
              checked={!!profile.give_welcome_book}
              onChange={v => set('give_welcome_book', v)}
              features={features}
              onNavigate={onNavigate}
            />
          </div>

          {/* numAuto rather than a fixed 96px: each cell sizes to its own hint, so none of them
              wrap onto a second line. */}
          <div className={styles.row}>
            <Field label="Min" hint="Added to the spawn radius" className={styles.numAuto}>
              <Input type="number" min={0} value={profile.min_distance}
                onChange={e => set('min_distance', clampInt(e.target.value, 0))} />
            </Field>
            <Field label="Max" hint="0 uses the world border" className={styles.numAuto}>
              <Input type="number" min={0} value={profile.max_distance}
                onChange={e => set('max_distance', clampInt(e.target.value, 0))} />
            </Field>
            <Field label="Attempts" hint="One candidate per tick" className={styles.numAuto}>
              <Input type="number" min={1} value={profile.max_attempts}
                onChange={e => set('max_attempts', clampInt(e.target.value, 1))} />
            </Field>
            <Field label="Reward cooldown" hint="Seconds, 0 = once ever" className={styles.numAuto}>
              <Input type="number" min={0} value={profile.reward_cooldown_seconds ?? 0}
                onChange={e => set('reward_cooldown_seconds', clampInt(e.target.value, 0))} />
            </Field>
          </div>

          <div className={styles.row}>
            <Field label="Spawn bias" hint={biasHint(profile)} className={styles.grow}
              aside={(profile.spawn_bias ?? 1).toFixed(1)}>
              <Slider hideValue value={profile.spawn_bias ?? 1} min={1} max={4} step={0.1}
                onChange={v => set('spawn_bias', v)} />
            </Field>
          </div>

          <div className={styles.row}>
            <Field label="Arrival message" hint="Supports & colour codes and {distance}" className={styles.wide}>
              <Input value={profile.message || ''} onChange={e => set('message', e.target.value)} />
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
            onChange={items => set('items', items)}
          />

        </>
      )}
      <ConfirmDialog
        open={confirm} onOpenChange={setConfirm}
        title={`Remove the ${shortDim(profile.dimension)} profile?`}
        description="Its distance settings, arrival effects and arrival items go with it. /rtp stops working in that dimension until a profile is added again."
        confirmLabel="Remove profile" tone="danger" onConfirm={onRemove}
      />
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
