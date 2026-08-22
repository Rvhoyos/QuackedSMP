import { useState, useRef, useLayoutEffect } from 'react'
import { Btn, Input, Select, Field } from '../../ui'
import { parseBook, buildBook, emptySegment, MC_COLORS, CLICK_ACTIONS, GENERATIONS } from './bookModel'
import styles from './BookEditor.module.css'

/**
 * Book and quill editor. The parchment is the editing surface, one contentEditable span per
 * styled segment, so the DOM mirrors the text-component list instead of fighting the browser's
 * own rich-text handling. The toolbar acts on whichever segment has focus.
 */
const CLICK_FIELD_LABEL = { url: 'Address', command: 'Command', value: 'Text to copy', page: 'Page number' }

export default function BookEditor({ content, onClose, onSave }) {
  const [book, setBook] = useState(() => parseBook(content))
  const [pageIndex, setPageIndex] = useState(0)
  const [activeSeg, setActiveSeg] = useState(0)
  const [showRaw, setShowRaw] = useState(false)
  const [overflowing, setOverflowing] = useState(false)
  const [selection, setSelection] = useState(null)
  const pageRef = useRef(null)

  const page = book.pages[pageIndex] || []
  const segment = page[activeSeg] || page[0]

  function editPage(mutate) {
    setBook(prev => {
      const pages = prev.pages.map(p => p.map(s => ({ ...s, click: { ...s.click } })))
      mutate(pages[pageIndex])
      return { ...prev, pages }
    })
  }

  function setSegmentText(index, text) {
    // Written straight into state without re-rendering that span, see the layout effect below.
    setBook(prev => {
      const pages = prev.pages.map(p => p.map(s => ({ ...s, click: { ...s.click } })))
      if (pages[pageIndex]?.[index]) pages[pageIndex][index].text = text
      return { ...prev, pages }
    })
  }

  /**
   * Applies a change to whatever is highlighted, splitting the run around the selection so one
   * paragraph can hold several colours and several links. With nothing highlighted the whole run
   * changes, which is the old behaviour.
   */
  function applyToRun(patch) {
    const sel = selection
    const seg = page[activeSeg]
    const partial = seg && sel && sel.index === activeSeg
      && sel.end > sel.start && !(sel.start === 0 && sel.end >= seg.text.length)

    if (!partial) {
      editPage(segs => { if (segs[activeSeg]) Object.assign(segs[activeSeg], patch) })
      return
    }

    // The DOM node is about to be reused for different text, so let go of it first and let the
    // layout effect rewrite every run from state.
    document.activeElement?.blur?.()

    const before = seg.text.slice(0, sel.start)
    const middle = seg.text.slice(sel.start, sel.end)
    const after = seg.text.slice(sel.end)

    editPage(segs => {
      const copy = () => ({ ...seg, click: { ...seg.click } })
      const parts = []
      if (before) parts.push({ ...copy(), text: before })
      parts.push(Object.assign(copy(), { text: middle }, patch))
      if (after) parts.push({ ...copy(), text: after })
      segs.splice(activeSeg, 1, ...parts)
    })

    setActiveSeg(before ? activeSeg + 1 : activeSeg)
    setSelection(null)
  }

  function setSegField(key, value) { applyToRun({ [key]: value }) }

  function setClick(key, value) {
    applyToRun({ click: { ...(page[activeSeg]?.click || {}), [key]: value } })
  }


  function removeSegment() {
    if (page.length <= 1) return
    editPage(segs => { segs.splice(activeSeg, 1) })
    setActiveSeg(i => Math.max(0, i - 1))
  }

  function addPage() {
    setBook(prev => {
      const pages = [...prev.pages]
      pages.splice(pageIndex + 1, 0, [emptySegment()])
      return { ...prev, pages }
    })
    setPageIndex(i => i + 1)
    setActiveSeg(0)
  }

  function removePage() {
    if (book.pages.length <= 1) return
    setBook(prev => ({ ...prev, pages: prev.pages.filter((_, i) => i !== pageIndex) }))
    setPageIndex(i => Math.max(0, i - 1))
    setActiveSeg(0)
  }

  function goToPage(next) {
    setPageIndex(Math.max(0, Math.min(book.pages.length - 1, next)))
    setActiveSeg(0)
  }

  // BookEditScreen caps a page at 126 / 9 lines; anything past that is dropped by the game.
  useLayoutEffect(() => {
    const el = pageRef.current
    if (el) setOverflowing(el.scrollHeight > el.clientHeight + 1)
  })

  const clickSpec = CLICK_ACTIONS.find(a => a.id === (segment?.click?.action || ''))

  return (
    <div className={styles.backdrop} role="dialog" aria-label="Book editor">
      <div className={styles.shell}>
        <div className={styles.head}>
          <span className={styles.headTitle}>Book and Quill</span>
          <span className={styles.spacer} />
          <Btn size="sm" variant="ghost" onClick={() => setShowRaw(v => !v)}>
            {showRaw ? 'Hide JSON' : 'Show JSON'}
          </Btn>
          <Btn size="sm" onClick={onClose}>Cancel</Btn>
          <Btn size="sm" variant="primary" onClick={() => onSave(buildBook(book))}>Done</Btn>
        </div>

        <div className={styles.columns}>
          {/* ── The book itself ─────────────────────────────────────────── */}
          <div className={styles.bookSide}>
            <div className={styles.book}>
              {/* Vanilla's own wording, from the book.pageIndicator translation key. */}
              <div className={styles.pageNum}>Page {pageIndex + 1} of {book.pages.length}</div>

              <div className={styles.page} ref={pageRef}>
                {page.map((seg, i) => (
                  <EditableSegment
                    key={`${pageIndex}-${i}`}
                    seg={seg}
                    active={i === activeSeg}
                    onFocus={() => setActiveSeg(i)}
                    onSelect={range => setSelection(range && { index: i, ...range })}
                    onText={text => setSegmentText(i, text)}
                  />
                ))}
              </div>

              {overflowing && (
                <div className={styles.overflow}>Past 14 lines. The game will not show the rest.</div>
              )}

              <button className={`${styles.arrow} ${styles.arrowBack}`} disabled={pageIndex === 0}
                onClick={() => goToPage(pageIndex - 1)} aria-label="Previous page">◀</button>
              <button className={`${styles.arrow} ${styles.arrowForward}`}
                disabled={pageIndex >= book.pages.length - 1}
                onClick={() => goToPage(pageIndex + 1)} aria-label="Next page">▶</button>
            </div>

            <div className={styles.pageActions}>
              <Btn size="sm" onClick={addPage}>Add page</Btn>
              <Btn size="sm" variant="danger" disabled={book.pages.length <= 1} onClick={removePage}>Delete page</Btn>
            </div>
            <span className={styles.tip}>
              Highlight words to colour or link just those. The page matches vanilla's own size,
              so what fits here fits in game.
            </span>
          </div>

          {/* ── Everything the in-game screen does not expose ───────────── */}
          <div className={styles.sideBar}>
            <div className={styles.group}>
              <div className={styles.groupTitle}>Book</div>
              <Field label="Title"><Input value={book.title} maxLength={32}
                onChange={e => setBook(b => ({ ...b, title: e.target.value }))} /></Field>
              <Field label="Author"><Input value={book.author}
                onChange={e => setBook(b => ({ ...b, author: e.target.value }))} /></Field>
              <Field label="Generation">
                <Select value={book.generation}
                  onChange={e => setBook(b => ({ ...b, generation: Number(e.target.value) }))}>
                  {GENERATIONS.map((g, i) => <option key={g} value={i}>{g}</option>)}
                </Select>
              </Field>
            </div>

            <div className={styles.group}>
              <div className={styles.groupTitle}>Selected text</div>
              <span className={styles.groupHint}>Highlight words on the page first.</span>

              <div className={styles.swatches}>
                <button
                  className={`${styles.swatch} ${styles.swatchNone} ${!segment?.color ? styles.swatchOn : ''}`}
                  onClick={() => setSegField('color', undefined)} title="Default">/</button>
                {MC_COLORS.map(c => (
                  <button key={c.id} title={c.id}
                    className={`${styles.swatch} ${segment?.color === c.id ? styles.swatchOn : ''}`}
                    style={{ background: c.hex }}
                    onClick={() => setSegField('color', c.id)} />
                ))}
              </div>

              <div className={styles.formatRow}>
                {[
                  ['bold', 'B'], ['italic', 'I'], ['underlined', 'U'],
                  ['strikethrough', 'S'], ['obfuscated', '?'],
                ].map(([key, label]) => (
                  <button key={key} title={key}
                    className={`${styles.fmt} ${styles[key]} ${segment?.[key] ? styles.fmtOn : ''}`}
                    onClick={() => setSegField(key, !segment?.[key])}>{label}</button>
                ))}
              </div>

              <Field label="When clicked" hint={clickSpec?.what}>
                <Select value={segment?.click?.action || ''} onChange={e => setClick('action', e.target.value)}>
                  {CLICK_ACTIONS.map(a => (
                    <option key={a.id || 'none'} value={a.id}>
                      {a.short ? `${a.label}: ${a.short}` : a.label}
                    </option>
                  ))}
                </Select>
              </Field>

              {clickSpec?.field && (
                <Field
                  label={CLICK_FIELD_LABEL[clickSpec.field]}
                  hint={clickSpec.id === 'open_url' ? 'http or https. mailto is rejected.' : undefined}>
                  <Input
                    type={clickSpec.field === 'page' ? 'number' : 'text'}
                    min={clickSpec.field === 'page' ? 1 : undefined}
                    value={segment?.click?.value || ''}
                    onChange={e => setClick('value', e.target.value)} />
                </Field>
              )}

              <Field label="Tooltip" hint="Shown when the cursor rests on this text">
                <Input value={segment?.hover || ''} onChange={e => setSegField('hover', e.target.value)} />
              </Field>

              <div className={styles.segActions}>
                <Btn size="sm" variant="danger" disabled={page.length <= 1} onClick={removeSegment}>
                  Delete this text
                </Btn>
              </div>
            </div>
          </div>
        </div>

        {showRaw && (
          <pre className={styles.raw}>{JSON.stringify(buildBook(book), null, 2)}</pre>
        )}
      </div>
    </div>
  )
}

/**
 * One styled run of text. React never rewrites the node while it has focus, which is what keeps
 * the caret from jumping to the start on every keystroke.
 */
function EditableSegment({ seg, active, onFocus, onSelect, onText }) {
  const ref = useRef(null)

  useLayoutEffect(() => {
    const el = ref.current
    if (!el || document.activeElement === el) return
    if (el.textContent !== seg.text) el.textContent = seg.text
  }, [seg.text])

  function handleKeyDown(e) {
    if (e.key !== 'Enter') return
    // Browsers disagree on whether Enter makes a <br> or a <div>; inserting a real newline keeps
    // textContent in step with what the page will actually contain.
    e.preventDefault()
    const selection = window.getSelection()
    if (!selection?.rangeCount) return
    const range = selection.getRangeAt(0)
    range.deleteContents()
    const node = document.createTextNode('\n')
    range.insertNode(node)
    range.setStartAfter(node)
    range.collapse(true)
    selection.removeAllRanges()
    selection.addRange(range)
    onText(e.currentTarget.textContent)
  }

  return (
    <span
      ref={ref}
      contentEditable
      suppressContentEditableWarning
      spellCheck={false}
      className={`${styles.seg} ${active ? styles.segActive : ''} ${seg.obfuscated ? styles.obf : ''}`}
      style={{
        color: MC_COLORS.find(c => c.id === seg.color)?.hex || 'var(--book-ink)',
        fontWeight: seg.bold ? 700 : 400,
        fontStyle: seg.italic ? 'italic' : 'normal',
        textDecoration: [seg.underlined && 'underline', seg.strikethrough && 'line-through']
          .filter(Boolean).join(' ') || 'none',
      }}
      onFocus={onFocus}
      onKeyDown={handleKeyDown}
      onSelect={e => onSelect(offsetsWithin(e.currentTarget))}
      onInput={e => { onSelect(null); onText(e.currentTarget.textContent) }}
    />
  )
}

/**
 * Character offsets of the current highlight inside this run, or null when nothing is selected.
 * Measured with a Range rather than anchorOffset so a run holding hard line breaks, and therefore
 * several text nodes, still counts correctly.
 */
function offsetsWithin(el) {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return null
  const range = sel.getRangeAt(0)
  if (range.collapsed) return null
  if (!el.contains(range.startContainer) || !el.contains(range.endContainer)) return null

  const upToStart = range.cloneRange()
  upToStart.selectNodeContents(el)
  upToStart.setEnd(range.startContainer, range.startOffset)
  const start = upToStart.toString().length
  return { start, end: start + range.toString().length }
}
