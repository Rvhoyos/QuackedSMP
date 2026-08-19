import { useState, useEffect, useCallback, useRef } from 'react'
import { Toolbar, Btn, IconButton, Textarea, Loading, EmptyState, ErrorBanner, useToast } from '../../ui'
import { IconChatFilter } from './MinecraftIcons'
import styles from './ChatFilterPanel.module.css'

const PAGE_SIZE = 50

export default function ChatFilterPanel({ token, onExpired }) {
  const [tab, setTab] = useState('blocked')
  const [words, setWords] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(new Set())
  const [addText, setAddText] = useState('')
  const [mutes, setMutes] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const toast = useToast()

  const auth = { Authorization: `Bearer ${token}` }
  const debounceRef = useRef(null)

  const loadWords = useCallback(async (p = 0, q = search) => {
    setLoading(true)
    const isWl = tab === 'whitelist'
    const params = new URLSearchParams({ page: p, size: PAGE_SIZE, tab: isWl ? 'whitelist' : 'blocked' })
    if (q) params.set('q', q)
    try {
      const r = await fetch(`/api/admin/chatfilter?${params}`, { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { setWords(d.words || []); setTotal(d.total || 0); setSelected(new Set()) }
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
    setSearch(q); setPage(0)
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => loadWords(0, q), 300)
  }
  async function goPage(p) { setPage(p); await loadWords(p, search) }

  async function addWords() {
    const wordList = addText.split(/[\n,]+/).map(w => w.trim()).filter(Boolean)
    if (!wordList.length) return
    const isWl = tab === 'whitelist'
    try {
      const r = await fetch('/api/admin/chatfilter/add', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ words: wordList, whitelist: isWl }) })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error); else { toast(`Added ${d.added} word${d.added !== 1 ? 's' : ''}`); setAddText(''); loadWords(page, search) }
    } catch { setError('Failed to add words') }
  }

  async function deleteSelected() {
    const wordList = Array.from(selected)
    if (!wordList.length) return
    const isWl = tab === 'whitelist'
    try {
      const r = await fetch('/api/admin/chatfilter/remove', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ words: wordList, whitelist: isWl }) })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error); else { toast(`Removed ${d.removed} word${d.removed !== 1 ? 's' : ''}`); loadWords(page, search) }
    } catch { setError('Failed to remove words') }
  }

  async function unmute(uuid) {
    try {
      const r = await fetch('/api/admin/chatfilter/unmute', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ uuid }) })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error); else { toast('Player unmuted'); loadMutes() }
    } catch { setError('Failed to unmute') }
  }

  async function exportWords() {
    const isWl = tab === 'whitelist'
    try {
      const r = await fetch(`/api/admin/chatfilter?size=99999&tab=${isWl ? 'whitelist' : 'blocked'}`, { headers: auth })
      const d = await r.json()
      const blob = new Blob([(d.words || []).join('\n')], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a'); a.href = url; a.download = `${isWl ? 'whitelist' : 'blocked_words'}.txt`; a.click()
      URL.revokeObjectURL(url)
    } catch { setError('Export failed') }
  }

  function toggleWord(word) { setSelected(prev => { const n = new Set(prev); n.has(word) ? n.delete(word) : n.add(word); return n }) }
  function toggleAll() { (selected.size === words.length && words.length > 0) ? setSelected(new Set()) : setSelected(new Set(words)) }

  const totalPages = Math.ceil(total / PAGE_SIZE)
  const nowMs = Date.now()

  const TABS = [
    { id: 'blocked', label: 'Blocked' },
    { id: 'whitelist', label: 'Whitelist' },
    { id: 'mutes', label: `Mutes${tab === 'mutes' ? ` (${mutes.length})` : ''}` },
  ]

  return (
    <div className={styles.wrap}>
      <Toolbar title="Chat Filter" count={tab !== 'mutes' ? `${total.toLocaleString()} words` : `${mutes.length} muted`}>
        {tab !== 'mutes' && <Btn size="sm" onClick={exportWords}>Export</Btn>}
        <IconButton tip="Refresh" onClick={() => tab === 'mutes' ? loadMutes() : loadWords(page, search)}>↻</IconButton>
      </Toolbar>

      <div className={styles.subtabs}>
        {TABS.map(t => <button key={t.id} className={`${styles.subtab} ${tab === t.id ? styles.subtabOn : ''}`} onClick={() => setTab(t.id)}>{t.label}</button>)}
      </div>

      <ErrorBanner>{error}</ErrorBanner>

      {tab !== 'mutes' ? (
        <div className={styles.body}>
          <div className={styles.controls}>
            <input className={styles.searchInput} placeholder="Search words…" value={search} onChange={e => handleSearch(e.target.value)} />
            <label className={styles.selectAll}>
              <input type="checkbox" checked={selected.size === words.length && words.length > 0} onChange={toggleAll} />
              {selected.size > 0 ? `${selected.size} selected` : 'Select page'}
            </label>
            {selected.size > 0 && <Btn size="sm" variant="danger" onClick={deleteSelected}>Delete ({selected.size})</Btn>}
          </div>

          {loading ? <Loading label="Loading…" />
            : words.length === 0 ? <EmptyState icon={<IconChatFilter size={30} />} label={search ? 'No words match your search' : 'No words in this list'} />
            : (
              <div className={styles.words}>
                {words.map(word => (
                  <button key={word} className={`${styles.word} ${selected.has(word) ? styles.wordOn : ''}`} onClick={() => toggleWord(word)}>
                    <span className={styles.checkbox}>{selected.has(word) ? '✓' : ''}</span>{word}
                  </button>
                ))}
              </div>
            )}

          {totalPages > 1 && (
            <div className={styles.pagination}>
              <Btn size="sm" variant="ghost" disabled={page === 0} onClick={() => goPage(0)}>«</Btn>
              <Btn size="sm" variant="ghost" disabled={page === 0} onClick={() => goPage(page - 1)}>Prev</Btn>
              <span className={styles.pageInfo}>Page {page + 1} / {totalPages}</span>
              <Btn size="sm" variant="ghost" disabled={page >= totalPages - 1} onClick={() => goPage(page + 1)}>Next</Btn>
              <Btn size="sm" variant="ghost" disabled={page >= totalPages - 1} onClick={() => goPage(totalPages - 1)}>»</Btn>
            </div>
          )}

          <div className={styles.addCard}>
            <div className={styles.addLabel}>Add to {tab === 'whitelist' ? 'whitelist' : 'filter'}, comma or newline separated</div>
            <Textarea placeholder={'word1, word2\nword3'} value={addText} onChange={e => setAddText(e.target.value)} rows={3} />
            <div className={styles.addFoot}><Btn variant="primary" disabled={!addText.trim()} onClick={addWords}>Add to {tab === 'whitelist' ? 'Whitelist' : 'Filter'}</Btn></div>
          </div>
        </div>
      ) : (
        <div className={styles.body}>
          {loading ? <Loading label="Loading mutes…" />
            : mutes.length === 0 ? <EmptyState icon={<IconChatFilter size={30} />} label="No active mutes" hint="Players auto-muted by the filter show up here with a countdown." />
            : (
              <div className={styles.muteGrid}>
                {mutes.map(m => {
                  const mins = Math.ceil(Math.max(0, m.muteEnd - nowMs) / 60000)
                  return (
                    <div key={m.uuid} className={styles.muteCard}>
                      <div className={styles.muteTop}>
                        <span className={styles.muteName}>{m.name}</span>
                        <Btn size="sm" variant="warn" onClick={() => unmute(m.uuid)}>Unmute</Btn>
                      </div>
                      <span className={styles.muteUuid}>{m.uuid.substring(0, 8)}…</span>
                      <span className={styles.muteRemaining}>{mins}m remaining · until {new Date(m.muteEnd).toLocaleTimeString()}</span>
                    </div>
                  )
                })}
              </div>
            )}
        </div>
      )}
    </div>
  )
}
