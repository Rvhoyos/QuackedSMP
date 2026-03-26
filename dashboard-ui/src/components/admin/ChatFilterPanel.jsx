import { useState, useEffect, useCallback, useRef } from 'react'
import styles from './ChatFilterPanel.module.css'

const PAGE_SIZE = 50

export default function ChatFilterPanel({ token, onExpired }) {
  const [tab,       setTab]      = useState('blocked') // 'blocked' | 'whitelist' | 'mutes'
  const [words,     setWords]    = useState([])
  const [total,     setTotal]    = useState(0)
  const [page,      setPage]     = useState(0)
  const [search,    setSearch]   = useState('')
  const [selected,  setSelected] = useState(new Set())
  const [addText,   setAddText]  = useState('')
  const [mutes,     setMutes]    = useState([])
  const [loading,   setLoading]  = useState(false)
  const [error,     setError]    = useState(null)
  const [status,    setStatus]   = useState(null)

  const auth = { 'Authorization': `Bearer ${token}` }
  const debounceRef = useRef(null)

  function flash(msg) {
    setStatus(msg)
    setTimeout(() => setStatus(null), 3000)
  }

  const loadWords = useCallback(async (p = 0, q = search) => {
    setLoading(true)
    const isWl = tab === 'whitelist'
    const params = new URLSearchParams({ page: p, size: PAGE_SIZE, tab: isWl ? 'whitelist' : 'blocked' })
    if (q) params.set('q', q)
    try {
      const r = await fetch(`/api/admin/chatfilter?${params}`, { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error) } else {
        setWords(d.words || [])
        setTotal(d.total || 0)
        setSelected(new Set())
      }
    } catch { setError('Failed to load') }
    setLoading(false)
  }, [token, tab]) // eslint-disable-line react-hooks/exhaustive-deps

  const loadMutes = useCallback(async () => {
    setLoading(true)
    try {
      const r = await fetch('/api/admin/chatfilter/mutes', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (!d.error) setMutes(Array.isArray(d) ? d : [])
    } catch { setError('Failed to load mutes') }
    setLoading(false)
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (tab === 'mutes') loadMutes()
    else { setPage(0); setSearch(''); loadWords(0, '') }
  }, [tab]) // eslint-disable-line react-hooks/exhaustive-deps

  function handleSearch(q) {
    setSearch(q)
    setPage(0)
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => loadWords(0, q), 300)
  }

  async function goPage(p) {
    setPage(p)
    await loadWords(p, search)
  }

  async function addWords() {
    const wordList = addText.split(/[\n,]+/).map(w => w.trim()).filter(Boolean)
    if (!wordList.length) return
    const isWl = tab === 'whitelist'
    try {
      const r = await fetch('/api/admin/chatfilter/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ words: wordList, whitelist: isWl }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { flash(`Added ${d.added} word${d.added !== 1 ? 's' : ''}`); setAddText(''); loadWords(page, search) }
    } catch { setError('Failed to add words') }
  }

  async function deleteSelected() {
    const wordList = Array.from(selected)
    if (!wordList.length) return
    const isWl = tab === 'whitelist'
    try {
      const r = await fetch('/api/admin/chatfilter/remove', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ words: wordList, whitelist: isWl }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { flash(`Removed ${d.removed} word${d.removed !== 1 ? 's' : ''}`); loadWords(page, search) }
    } catch { setError('Failed to remove words') }
  }

  async function unmute(uuid) {
    try {
      const r = await fetch('/api/admin/chatfilter/unmute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ uuid }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { flash('Player unmuted'); loadMutes() }
    } catch { setError('Failed to unmute') }
  }

  async function exportWords() {
    const isWl = tab === 'whitelist'
    try {
      const r = await fetch(`/api/admin/chatfilter?size=99999&tab=${isWl ? 'whitelist' : 'blocked'}`, { headers: auth })
      const d = await r.json()
      const content = (d.words || []).join('\n')
      const blob = new Blob([content], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${isWl ? 'whitelist' : 'blocked_words'}.txt`
      a.click()
      URL.revokeObjectURL(url)
    } catch { setError('Export failed') }
  }

  function toggleWord(word) {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(word)) next.delete(word)
      else next.add(word)
      return next
    })
  }

  function toggleAll() {
    if (selected.size === words.length && words.length > 0) setSelected(new Set())
    else setSelected(new Set(words))
  }

  const totalPages = Math.ceil(total / PAGE_SIZE)
  const nowMs = Date.now()

  return (
    <div className={styles.wrap}>
      <div className={styles.subtabs}>
        <button
          className={`${styles.subtab} ${tab === 'blocked' ? styles.subtabActive : ''}`}
          onClick={() => setTab('blocked')}
        >
          Blocked Words{tab === 'blocked' && total > 0 ? ` (${total.toLocaleString()})` : ''}
        </button>
        <button
          className={`${styles.subtab} ${tab === 'whitelist' ? styles.subtabActive : ''}`}
          onClick={() => setTab('whitelist')}
        >
          Whitelist{tab === 'whitelist' && total > 0 ? ` (${total.toLocaleString()})` : ''}
        </button>
        <button
          className={`${styles.subtab} ${tab === 'mutes' ? styles.subtabActive : ''}`}
          onClick={() => setTab('mutes')}
        >
          Mutes{tab === 'mutes' ? ` (${mutes.length})` : ''}
        </button>
      </div>

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>x</button>
        </div>
      )}
      {status && <div className={styles.statusMsg}>{status}</div>}

      {tab !== 'mutes' ? (
        <>
          <div className={styles.toolbar}>
            <input
              className={styles.searchInput}
              placeholder="Search words..."
              value={search}
              onChange={e => handleSearch(e.target.value)}
            />
            <span className={styles.totalCount}>{total.toLocaleString()} words</span>
            <button className={styles.btn} onClick={exportWords}>Export</button>
          </div>

          <div className={styles.bulkBar}>
            <label className={styles.selectAllLabel}>
              <input
                type="checkbox"
                checked={selected.size === words.length && words.length > 0}
                onChange={toggleAll}
              />
              {selected.size > 0
                ? <span className={styles.selectedCount}>{selected.size} selected</span>
                : <span className={styles.selectHint}>Select all on page</span>
              }
            </label>
            {selected.size > 0 && (
              <button className={styles.btnDanger} onClick={deleteSelected}>
                Delete selected ({selected.size})
              </button>
            )}
          </div>

          <div className={styles.wordList}>
            {loading && <div className={styles.empty}>Loading...</div>}
            {!loading && words.length === 0 && (
              <div className={styles.empty}>{search ? 'No words match your search.' : 'No words in list.'}</div>
            )}
            {words.map(word => (
              <label key={word} className={`${styles.wordRow} ${selected.has(word) ? styles.wordSelected : ''}`}>
                <input type="checkbox" checked={selected.has(word)} onChange={() => toggleWord(word)} />
                <span className={styles.word}>{word}</span>
              </label>
            ))}
          </div>

          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button className={styles.btnGhost} disabled={page === 0} onClick={() => goPage(0)}>
                First
              </button>
              <button className={styles.btnGhost} disabled={page === 0} onClick={() => goPage(page - 1)}>
                Prev
              </button>
              <span className={styles.pageInfo}>Page {page + 1} / {totalPages}</span>
              <button className={styles.btnGhost} disabled={page >= totalPages - 1} onClick={() => goPage(page + 1)}>
                Next
              </button>
              <button className={styles.btnGhost} disabled={page >= totalPages - 1} onClick={() => goPage(totalPages - 1)}>
                Last
              </button>
            </div>
          )}

          <div className={styles.addSection}>
            <div className={styles.addTitle}>
              Add words to {tab === 'whitelist' ? 'whitelist' : 'filter'}, comma or newline separated
            </div>
            <textarea
              className={styles.textarea}
              placeholder={'word1, word2\nword3'}
              value={addText}
              onChange={e => setAddText(e.target.value)}
              rows={3}
            />
            <button className={styles.btn} disabled={!addText.trim()} onClick={addWords}>
              Add to {tab === 'whitelist' ? 'Whitelist' : 'Filter'}
            </button>
          </div>
        </>
      ) : (
        <div className={styles.muteList}>
          <div className={styles.muteToolbar}>
            <span className={styles.count}>{mutes.length} active mute{mutes.length !== 1 ? 's' : ''}</span>
            <button className={styles.refresh} onClick={loadMutes} disabled={loading}>
              {loading ? 'Refreshing...' : 'Refresh'}
            </button>
          </div>
          {!loading && mutes.length === 0 && (
            <div className={styles.empty}>No active mutes.</div>
          )}
          {mutes.map(m => {
            const remaining = Math.max(0, m.muteEnd - nowMs)
            const mins = Math.ceil(remaining / 60000)
            return (
              <div key={m.uuid} className={styles.muteRow}>
                <div className={styles.muteInfo}>
                  <span className={styles.muteName}>{m.name}</span>
                  <span className={styles.muteUuid}>{m.uuid.substring(0, 8)}...</span>
                </div>
                <div className={styles.muteTime}>
                  <span className={styles.muteUntil}>Until {new Date(m.muteEnd).toLocaleString()}</span>
                  <span className={styles.muteRemaining}>{mins}m remaining</span>
                </div>
                <button className={styles.btnDanger} onClick={() => unmute(m.uuid)}>Unmute</button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
