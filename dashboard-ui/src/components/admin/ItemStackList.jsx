import { useState, useMemo } from 'react'
import { Btn, Field, Input, Textarea, Dialog, DialogButtons, useToast } from '../../ui'
import BookEditor from './BookEditor'
import { prettyId } from './registryText'
import styles from './ItemStackList.module.css'

const BOOK_COMPONENT = 'minecraft:written_book_content'
// Long lists stall the browser and nobody scrolls past the first screenful anyway; the counter
// below tells you when to narrow the search instead.
const MAX_RESULTS = 120

const BLANK_BOOK = {
  title: { raw: '' },
  author: '',
  generation: 0,
  pages: [{ raw: { text: '', extra: [] } }],
  resolved: true,
}

/**
 * Edits a list of ItemStacks for any feature that hands items to a player: random teleport
 * arrivals today, kits and vote rewards later. Knows nothing about the feature holding the list.
 *
 * Items are picked from the server's item registry, written from scratch in the book editor, or
 * imported from a /give command when they carry components a bare id cannot express.
 */
export default function ItemStackList({ items, catalog, token, onExpired, onChange, title = 'Items' }) {
  const [adding, setAdding] = useState(false)
  const [tab, setTab] = useState('browse')
  const [search, setSearch] = useState('')
  const [picked, setPicked] = useState('')
  const [count, setCount] = useState(1)
  const [give, setGive] = useState('')
  const [busy, setBusy] = useState(false)
  const [addError, setAddError] = useState(null)
  const [bookIndex, setBookIndex] = useState(-1)
  const [writingNew, setWritingNew] = useState(false)
  const toast = useToast()

  const matches = useMemo(() => {
    const q = search.trim().toLowerCase()
    const hits = q ? catalog.filter(id => id.toLowerCase().includes(q)) : catalog
    return { shown: hits.slice(0, MAX_RESULTS), total: hits.length }
  }, [search, catalog])

  function openAdd() {
    setAdding(true)
    setAddError(null)
    setSearch('')
    setPicked('')
    setCount(1)
    setGive('')
  }

  function addPicked() {
    if (!picked) return
    onChange([...items, { stack: { id: picked, count } }])
    toast(`Added ${prettyId(picked)}`)
    setAdding(false)
  }

  async function addFromGive() {
    setBusy(true)
    setAddError(null)
    try {
      const r = await fetch('/api/admin/items/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ give }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setAddError(d.error); return }
      onChange([...items, { stack: d.stack }])
      toast('Item added')
      setAdding(false)
    } catch {
      setAddError('Import failed')
    } finally {
      setBusy(false)
    }
  }

  function setCountAt(index, next) {
    onChange(items.map((it, i) => (i === index ? { ...it, stack: { ...it.stack, count: next } } : it)))
  }

  function setBook(index, content) {
    onChange(items.map((it, i) => {
      if (i !== index) return it
      const components = { ...(it.stack.components || {}), [BOOK_COMPONENT]: content }
      return { ...it, stack: { ...it.stack, components } }
    }))
  }

  return (
    <>
      <div className={styles.subHead}>
        <span className={styles.subTitle}>{title}</span>
        <Btn size="sm" onClick={openAdd}>Add item</Btn>
      </div>

      {items.length === 0 ? (
        <span className={styles.hint}>Nothing in this list yet.</span>
      ) : items.map((item, i) => {
        const stack = item.stack || {}
        const book = stack.components?.[BOOK_COMPONENT]
        const extras = Object.keys(stack.components || {}).length
        return (
          <div key={i} className={styles.itemRow}>
            <span className={styles.itemName}>
              {prettyId(stack.id) || 'Unknown item'}
              <span className={styles.itemId}>{stack.id}</span>
            </span>
            {extras > 0 && (
              <span className={styles.itemTag}>{book ? 'written book' : `${extras} component${extras > 1 ? 's' : ''}`}</span>
            )}
            <label className={styles.inlineField}>
              <span>Count</span>
              {/* ItemStack's own codec clamps count to 1..99, so the input does too. */}
              <Input type="number" min={1} max={99} value={stack.count ?? 1}
                onChange={e => setCountAt(i, clampCount(e.target.value))} />
            </label>
            {book && <Btn size="sm" onClick={() => setBookIndex(i)}>Edit book</Btn>}
            <Btn size="sm" variant="danger"
              onClick={() => onChange(items.filter((_, j) => j !== i))}>Remove</Btn>
          </div>
        )
      })}

      <Dialog open={adding} onOpenChange={setAdding} title="Add an arrival item">
        <div className={styles.tabs}>
          <button className={`${styles.tab} ${tab === 'browse' ? styles.tabOn : ''}`}
            onClick={() => setTab('browse')}>Pick an item</button>
          <button className={`${styles.tab} ${tab === 'book' ? styles.tabOn : ''}`}
            onClick={() => setTab('book')}>Write a book</button>
          <button className={`${styles.tab} ${tab === 'give' ? styles.tabOn : ''}`}
            onClick={() => setTab('give')}>Paste from a command</button>
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

            <Field label="Count">
              <Input type="number" min={1} max={99} value={count}
                onChange={e => setCount(clampCount(e.target.value))} />
            </Field>

            <DialogButtons>
              <Btn onClick={() => setAdding(false)}>Cancel</Btn>
              <Btn variant="primary" disabled={!picked} onClick={addPicked}>
                {picked ? `Add ${prettyId(picked)}` : 'Add item'}
              </Btn>
            </DialogButtons>
          </>
        ) : tab === 'book' ? (
          <>
            <span className={styles.hint}>Opens a blank book in the editor.</span>
            <DialogButtons>
              <Btn onClick={() => setAdding(false)}>Cancel</Btn>
              <Btn variant="primary" onClick={() => { setAdding(false); setWritingNew(true) }}>
                Open the book
              </Btn>
            </DialogButtons>
          </>
        ) : (
          <>
            <span className={styles.hint}>Paste any command that names an item. Nothing is run, only read.</span>
            <Field label="Command or item id">
              <Textarea rows={4} value={give}
                placeholder={'minecraft:diamond_sword\n/give @p minecraft:red_bed 3'}
                onChange={e => setGive(e.target.value)} />
            </Field>
            {addError && <span className={styles.importError}>{addError}</span>}
            <DialogButtons>
              <Btn onClick={() => setAdding(false)}>Cancel</Btn>
              <Btn variant="primary" disabled={!give.trim() || busy} onClick={addFromGive}>
                {busy ? 'Reading…' : 'Add item'}
              </Btn>
            </DialogButtons>
          </>
        )}
      </Dialog>

      {writingNew && (
        <BookEditor
          content={BLANK_BOOK}
          onClose={() => setWritingNew(false)}
          onSave={content => {
            onChange([...items, { stack: { id: 'minecraft:written_book', count: 1,
              components: { [BOOK_COMPONENT]: content } } }])
            toast('Book added')
            setWritingNew(false)
          }}
        />
      )}

      {bookIndex >= 0 && (
        <BookEditor
          content={items[bookIndex]?.stack?.components?.[BOOK_COMPONENT]}
          onClose={() => setBookIndex(-1)}
          onSave={content => { setBook(bookIndex, content); setBookIndex(-1) }}
        />
      )}
    </>
  )
}

function clampCount(raw) {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) ? Math.max(1, Math.min(99, n)) : 1
}
