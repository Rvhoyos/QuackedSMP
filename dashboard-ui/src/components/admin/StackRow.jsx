import { useState } from 'react'
import { Btn, IconButton, Input } from '../../ui'
import BookEditor from './BookEditor'
import NameEditor from './NameEditor'
import { IconX } from './MinecraftIcons'
import { prettyId } from './registryText'
import {
  BOOK_COMPONENT, clampCount, componentCount, displayName, getComponent, withComponent,
} from './stackModel'
import styles from './ItemStackList.module.css'

/**
 * One stack in an editor: what it is, how many, and the buttons that open the book and name
 * editors. Shared by the list and the single slots so a stack looks and behaves the same
 * wherever it is stored.
 */
export default function StackRow({ stack, onChange, onRemove, showCount = true }) {
  const [editingBook, setEditingBook] = useState(false)
  const [editingName, setEditingName] = useState(false)

  const book = getComponent(stack, BOOK_COMPONENT)
  const extras = componentCount(stack)
  const named = displayName(stack, prettyId)

  return (
    <>
      <div className={styles.itemRow}>
        <span className={styles.itemName}>
          {named}
          <span className={styles.itemId}>{stack.id}</span>
        </span>

        {extras > 0 && (
          <span className={styles.itemTag}>
            {book ? 'written book' : `${extras} component${extras > 1 ? 's' : ''}`}
          </span>
        )}

        {showCount && (
          <label className={styles.inlineField}>
            <span>Count</span>
            {/* ItemStack's own codec constrains count to 1..99, so the input does too. */}
            <Input type="number" min={1} max={99} value={stack.count ?? 1}
              onChange={e => onChange({ ...stack, count: clampCount(e.target.value) })} />
          </label>
        )}

        <Btn size="sm" onClick={() => setEditingName(true)}>Rename</Btn>
        {book && <Btn size="sm" onClick={() => setEditingBook(true)}>Edit book</Btn>}
        <IconButton tip="Remove" className={styles.rowRemove} onClick={onRemove}>
          <IconX />
        </IconButton>
      </div>

      {editingBook && (
        <BookEditor
          content={book}
          onClose={() => setEditingBook(false)}
          onSave={content => {
            onChange(withComponent(stack, BOOK_COMPONENT, content))
            setEditingBook(false)
          }}
        />
      )}

      <NameEditor open={editingName} onOpenChange={setEditingName}
        stack={stack} onSave={onChange} />
    </>
  )
}
