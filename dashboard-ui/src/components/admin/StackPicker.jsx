import { useState, useMemo, useEffect } from 'react'
import { Btn, Field, Input, Dialog, DialogButtons } from '../../ui'
import BookEditor from './BookEditor'
import { prettyId } from './registryText'
import { BOOK_COMPONENT, BLANK_BOOK, clampCount, newStack } from './stackModel'
import styles from './ItemStackList.module.css'

// Long lists stall the browser and nobody scrolls past the first screenful anyway; the counter
// below tells you when to narrow the search instead.
const MAX_RESULTS = 120

/**
 * Chooses one ItemStack, either from the server's item registry or by writing a book from
 * scratch. Owns the book editor for the write-a-book path, so callers only ever receive a
 * finished stack.
 *
 * Used by every stack editor: the list, the single slots, and anything added later.
 */
export default function StackPicker({
  open, onOpenChange, catalog, onPick,
  title = 'Add an item', initialSearch = '', allowCount = true,
}) {
  const [tab, setTab] = useState('browse')
  const [search, setSearch] = useState(initialSearch)
  const [picked, setPicked] = useState('')
  const [count, setCount] = useState(1)
  const [writing, setWriting] = useState(false)

  // Reopening starts fresh rather than showing the last visit's selection.
  useEffect(() => {
    if (!open) return
    setTab('browse')
    setSearch(initialSearch)
    setPicked('')
    setCount(1)
  }, [open, initialSearch])

  const matches = useMemo(() => {
    const q = search.trim().toLowerCase()
    const hits = q ? catalog.filter(id => id.toLowerCase().includes(q)) : catalog
    return { shown: hits.slice(0, MAX_RESULTS), total: hits.length }
  }, [search, catalog])

  function take(stack) {
    onPick(stack)
    onOpenChange(false)
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange} title={title}>
        <div className={styles.tabs}>
          <button className={`${styles.tab} ${tab === 'browse' ? styles.tabOn : ''}`}
            onClick={() => setTab('browse')}>Pick an item</button>
          <button className={`${styles.tab} ${tab === 'book' ? styles.tabOn : ''}`}
            onClick={() => setTab('book')}>Write a book</button>
        </div>

        {tab === 'browse' ? (
          <>
            <Field label="Search">
              <Input autoFocus value={search} placeholder="bed, torch, golden apple…"
                onChange={e => setSearch(e.target.value)} />
            </Field>

            <div className={styles.pickList}>
              {matches.shown.length === 0
                ? <span className={styles.hint}>Nothing matches that.</span>
                : matches.shown.map(id => (
                  <button key={id}
                    className={`${styles.pickRow} ${picked === id ? styles.pickRowOn : ''}`}
                    onClick={() => setPicked(id)}>
                    <span className={styles.pickName}>{prettyId(id)}</span>
                    <span className={styles.pickId}>{id}</span>
                  </button>
                ))}
            </div>

            <span className={styles.hint}>
              {matches.total > matches.shown.length
                ? `Showing ${matches.shown.length} of ${matches.total}. Keep typing to narrow it down.`
                : `${matches.total} item${matches.total === 1 ? '' : 's'}`}
            </span>

            {allowCount && (
              <Field label="Count">
                <Input type="number" min={1} max={99} value={count}
                  onChange={e => setCount(clampCount(e.target.value))} />
              </Field>
            )}

            <DialogButtons>
              <Btn onClick={() => onOpenChange(false)}>Cancel</Btn>
              <Btn variant="primary" disabled={!picked}
                onClick={() => take(newStack(picked, allowCount ? count : 1))}>
                {picked ? `Add ${prettyId(picked)}` : 'Add item'}
              </Btn>
            </DialogButtons>
          </>
        ) : (
          <>
            <span className={styles.hint}>Opens a blank book in the editor.</span>
            <DialogButtons>
              <Btn onClick={() => onOpenChange(false)}>Cancel</Btn>
              <Btn variant="primary" onClick={() => { onOpenChange(false); setWriting(true) }}>
                Open the book
              </Btn>
            </DialogButtons>
          </>
        )}
      </Dialog>

      {writing && (
        <BookEditor
          content={BLANK_BOOK}
          onClose={() => setWriting(false)}
          onSave={content => {
            setWriting(false)
            onPick({ ...newStack('minecraft:written_book', 1),
              components: { [BOOK_COMPONENT]: content } })
          }}
        />
      )}
    </>
  )
}
