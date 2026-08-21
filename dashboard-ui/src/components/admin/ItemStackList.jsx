import { useState } from 'react'
import { Btn, useToast } from '../../ui'
import StackPicker from './StackPicker'
import StackRow from './StackRow'
import { prettyId } from './registryText'
import { displayName } from './stackModel'
import styles from './ItemStackList.module.css'

/**
 * Edits a list of ItemStacks for any feature that hands items to a player: random teleport
 * arrivals and kits today, vote rewards later. Knows nothing about the feature holding the list.
 *
 * The pieces it is built from (StackPicker, StackRow) are the same ones a single slot uses, so a
 * stack is chosen, named and edited identically wherever it is stored.
 */
export default function ItemStackList({ items, catalog, onChange, title = 'Items' }) {
  const [adding, setAdding] = useState(false)
  const toast = useToast()

  function setStackAt(index, stack) {
    onChange(items.map((it, i) => (i === index ? { ...it, stack } : it)))
  }

  return (
    <>
      <div className={styles.subHead}>
        <span className={styles.subTitle}>{title}</span>
        <Btn size="sm" onClick={() => setAdding(true)}>Add item</Btn>
      </div>

      {items.length === 0
        ? <span className={styles.hint}>Nothing in this list yet.</span>
        : items.map((item, i) => (
          <StackRow key={i}
            stack={item.stack || {}}
            onChange={stack => setStackAt(i, stack)}
            onRemove={() => onChange(items.filter((_, j) => j !== i))}
          />
        ))}

      <StackPicker
        open={adding} onOpenChange={setAdding}
        catalog={catalog} title="Add an item"
        onPick={stack => {
          onChange([...items, { stack }])
          toast(`Added ${displayName(stack, prettyId)}`)
        }}
      />
    </>
  )
}
