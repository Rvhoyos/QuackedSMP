import { Btn, IconButton, Select, Input, SwitchField } from '../../ui'
import { prettyId } from './registryText'
import { IconX } from './MinecraftIcons'
import styles from './ItemStackList.module.css'

const CATEGORIES = [
  { id: 'BENEFICIAL', label: 'Beneficial' },
  { id: 'NEUTRAL',    label: 'Neutral' },
  { id: 'HARMFUL',    label: 'Harmful' },
]

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X']

/**
 * Edits a list of potion effects for any feature that applies them. The picker is fed by the
 * server's live mob effect registry, so modded effects appear and nobody memorises an id.
 */
export default function EffectList({ effects, catalog, onChange, title = 'Effects' }) {
  const known = new Map(catalog.map(e => [e.id, e]))

  function add() {
    const first = catalog.find(e => e.category === 'BENEFICIAL')?.id || catalog[0]?.id || 'minecraft:resistance'
    onChange([...effects, { effect: first, seconds: 30, amplifier: 0, show_particles: true }])
  }

  function set(index, key, value) {
    onChange(effects.map((e, i) => (i === index ? { ...e, [key]: value } : e)))
  }

  return (
    <>
      <div className={styles.subHead}>
        <span className={styles.subTitle}>{title}</span>
        <Btn size="sm" onClick={add}>Add effect</Btn>
      </div>

      {effects.length === 0 ? (
        <span className={styles.hint}>No effects. Players arrive with nothing applied.</span>
      ) : effects.map((effect, i) => {
        const meta = known.get(effect.effect)
        return (
          <div key={i} className={styles.effectRow}>
            <div className={styles.effectPick}>
              <span className={styles.effectDot} style={{ background: meta?.color || 'var(--txt-faint)' }} />
              <Select value={effect.effect} onChange={e => set(i, 'effect', e.target.value)}>
                {/* An id saved before a mod was removed would vanish from the list, so keep it. */}
                {!meta && <option value={effect.effect}>{effect.effect} (not installed)</option>}
                {CATEGORIES.map(cat => {
                  const rows = catalog.filter(e => e.category === cat.id)
                  if (rows.length === 0) return null
                  return (
                    <optgroup key={cat.id} label={cat.label}>
                      {rows.map(e => <option key={e.id} value={e.id}>{prettyId(e.id)}</option>)}
                    </optgroup>
                  )
                })}
              </Select>
            </div>

            <label className={styles.inlineField}>
              <span>Seconds</span>
              <Input type="number" min={1} value={effect.seconds} disabled={meta?.instant}
                onChange={e => set(i, 'seconds', clampInt(e.target.value, 1))} />
            </label>

            <label className={styles.inlineField}>
              <span>Level {ROMAN[Math.min(ROMAN.length - 1, effect.amplifier ?? 0)]}</span>
              <Input type="number" min={1} max={256} value={(effect.amplifier ?? 0) + 1}
                onChange={e => set(i, 'amplifier', clampInt(e.target.value, 1) - 1)} />
            </label>

            <SwitchField label="Particles"
              checked={effect.show_particles !== false}
              onChange={v => set(i, 'show_particles', v)} />

            <IconButton tip="Remove" className={styles.rowRemove}
              onClick={() => onChange(effects.filter((_, j) => j !== i))}>
              <IconX />
            </IconButton>

            {meta?.instant && (
              <span className={styles.rowNote}>Applies once on arrival. Duration is ignored.</span>
            )}
          </div>
        )
      })}
    </>
  )
}

function clampInt(raw, min) {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) ? Math.max(min, n) : min
}
