import { useState } from 'react'
import { Btn } from '../../ui'
import StackPicker from './StackPicker'
import StackRow from './StackRow'
import styles from './StackSlot.module.css'

/**
 * Exactly one stack, or nothing. The armor slots of a kit use this; the list editor is the wrong
 * shape there because a head slot holds one helmet, not a list of them.
 *
 * Count is hidden: a worn piece is always a single item.
 */
export default function StackSlot({ value, onChange, catalog, label, icon, suggest = '' }) {
  const [picking, setPicking] = useState(false)

  return (
    <div className={styles.slot}>
      {/* Replace sits on the label line rather than under the slot: it is one short button and
          a whole row of its own for it was the tallest thing in the cell. */}
      <div className={styles.head}>
        {icon && <span className={styles.icon}>{icon}</span>}
        <span className={styles.label}>{label}</span>
        {value && (
          <Btn size="sm" className={styles.replace} onClick={() => setPicking(true)}>Replace</Btn>
        )}
      </div>

      {value
        ? <StackRow stack={value} showCount={false}
            onChange={onChange} onRemove={() => onChange(null)} />
        : (
          <button type="button" className={styles.empty} onClick={() => setPicking(true)}>
            Empty, click to choose
          </button>
        )}

      <StackPicker
        open={picking} onOpenChange={setPicking}
        catalog={catalog} initialSearch={suggest} allowCount={false}
        title={`Choose ${label.toLowerCase()}`}
        onPick={stack => onChange(stack)}
      />
    </div>
  )
}
