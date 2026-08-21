import { useState } from 'react'
import { SectionCard, Btn, IconButton, Field, Input, Badge, ConfirmDialog } from '../../ui'
import GuideToggle from './GuideToggle'
import ItemStackList from './ItemStackList'
import StackSlot from './StackSlot'
import {
  IconHelmetGhost, IconChestplateGhost, IconLeggingsGhost, IconBootsGhost, IconTierStar,
  IconCollapse, IconExpand,
} from './MinecraftIcons'
import styles from './KitsPanel.module.css'

// Each worn slot with the ghost icon that hints at what belongs there, and a search term so the
// picker opens already narrowed to that piece rather than the whole item registry.
const ARMOR_SLOTS = [
  { key: 'head',  label: 'Head',  Icon: IconHelmetGhost,     suggest: 'helmet' },
  { key: 'chest', label: 'Chest', Icon: IconChestplateGhost, suggest: 'chestplate' },
  { key: 'legs',  label: 'Legs',  Icon: IconLeggingsGhost,   suggest: 'leggings' },
  { key: 'feet',  label: 'Feet',  Icon: IconBootsGhost,      suggest: 'boots' },
]

export default function KitCard({ kit, registry, features, onNavigate, onChange, onRemove }) {
  const [open, setOpen] = useState(true)
  const [confirm, setConfirm] = useState(false)

  function set(key, value) { onChange({ ...kit, [key]: value }) }
  function setArmor(slot, stack) {
    onChange({ ...kit, armor: { ...(kit.armor || {}), [slot]: stack } })
  }

  return (
    <SectionCard
      title={kit.name || '(unnamed)'}
      subtitle={kit.display_name || 'No display name'}
      collapsed={!open}
      actions={
        <>
          {kit.min_tier > 0 && (
            <Badge variant="info">
              <IconTierStar size={11} /> Tier {kit.min_tier}
            </Badge>
          )}
          {kit.give_welcome_book && <Badge>Guide</Badge>}
          <Btn size="sm" variant="danger" onClick={() => setConfirm(true)}>Remove</Btn>
          <IconButton tip={open ? 'Collapse' : 'Expand'} onClick={() => setOpen(o => !o)}>
            {open ? <IconCollapse /> : <IconExpand />}
          </IconButton>
        </>
      }
    >
      {(
        <>
          <div className={styles.row}>
            <Field label="Name" hint="used in /smp kit" className={styles.grow}>
              <Input value={kit.name || ''} placeholder="starter"
                onChange={e => set('name', e.target.value)} />
            </Field>
            <Field label="Display name" hint="& colour codes" className={styles.grow}>
              <Input value={kit.display_name || ''} placeholder="&aStarter Kit"
                onChange={e => set('display_name', e.target.value)} />
            </Field>
            <Field label="Min tier" hint="0 = everyone" className={styles.numAuto}>
              <Input type="number" min={0} value={kit.min_tier ?? 0}
                onChange={e => set('min_tier', clampInt(e.target.value, 0))} />
            </Field>
            <div className={styles.guideWrap}>
              <GuideToggle
                checked={!!kit.give_welcome_book}
                onChange={v => set('give_welcome_book', v)}
                features={features}
                onNavigate={onNavigate}
              />
            </div>
          </div>

          <div className={styles.subHead}>
            <span className={styles.subTitle}>Armor</span>
          </div>
          <div className={styles.armorGrid}>
            {ARMOR_SLOTS.map(({ key, label, Icon, suggest }) => (
              <StackSlot key={key}
                label={label}
                icon={<Icon size={20} />}
                suggest={suggest}
                catalog={registry.items}
                value={kit.armor?.[key] || null}
                onChange={stack => setArmor(key, stack)}
              />
            ))}
          </div>

          <ItemStackList
            title="Items"
            items={kit.items || []}
            catalog={registry.items}
            onChange={items => set('items', items)}
          />
        </>
      )}
      <ConfirmDialog
        open={confirm} onOpenChange={setConfirm}
        title={`Remove the ${kit.name || 'unnamed'} kit?`}
        description="Its armor and items go with it, and players can no longer claim it with /smp kit."
        confirmLabel="Remove kit" tone="danger" onConfirm={onRemove}
      />
    </SectionCard>
  )
}

function clampInt(raw, min) {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) ? Math.max(min, n) : min
}
