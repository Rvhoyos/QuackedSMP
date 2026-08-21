import { useState, useEffect, useCallback } from 'react'
import { Toolbar, Btn, IconButton, SectionCard, Toggle, Loading, ErrorBanner, useToast } from '../../ui'
import { IconWrittenBook } from './MinecraftIcons'
import BookEditor from './BookEditor'
import styles from './WelcomeBookPanel.module.css'

/**
 * Edits the one guide the server hands out. Every delivery path reads this same book, so an edit
 * here changes what /guide, /smp help, kit rewards and rtp arrivals all give.
 */
export default function WelcomeBookPanel({ token, onExpired }) {
  const [enabled, setEnabled] = useState(true)
  const [content, setContent] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving,  setSaving]  = useState(false)
  const [dirty,   setDirty]   = useState(false)
  const [error,   setError]   = useState(null)
  const [editing, setEditing] = useState(false)
  const toast = useToast()

  const auth = { Authorization: `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await fetch('/api/admin/welcomebook', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setEnabled(!!d.enabled)
      setContent(d.content || null)
      setDirty(false)
    } catch {
      setError('Failed to load the welcome book')
    } finally {
      setLoading(false)
    }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load() }, [load])

  async function save() {
    setSaving(true)
    try {
      const r = await fetch('/api/admin/welcomebook/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ enabled, content }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { toast('Welcome book saved'); setDirty(false); setError(null) }
    } catch {
      setError('Failed to save')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className={styles.wrap}><Loading label="Loading the welcome book…" /></div>

  const pages = content?.pages?.length ?? 0
  const title = content?.title?.raw ?? content?.title ?? ''

  return (
    <div className={styles.wrap}>
      <Toolbar title="Welcome Book" count={`${pages} ${pages === 1 ? 'page' : 'pages'}`}>
        <Btn size="sm" variant="primary" disabled={!dirty || saving} onClick={save}>
          {saving ? 'Saving…' : 'Save'}
        </Btn>
        <IconButton tip="Reload" onClick={load}>↻</IconButton>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>

      <div className={styles.body}>
        <SectionCard
          title="The guide"
          subtitle="One book, handed out by every path below. Edit it once and they all change."
        >
          <div className={styles.settingsRow}>
            <div className={styles.toggleCell}>
              <span className={styles.toggleLabel}>Enable the guide</span>
              <Toggle checked={enabled} onChange={v => { setEnabled(v); setDirty(true) }}
                aria-label="Enable the welcome book" />
            </div>
            <span className={styles.unit}>
              off means /guide says so and /smp help falls back to a chat list
            </span>
          </div>

          <div className={styles.bookRow}>
            <span className={styles.cover}><IconWrittenBook size={38} /></span>
            <div className={styles.bookMeta}>
              <span className={styles.bookTitle}>{title || 'Untitled'}</span>
              <span className={styles.bookBy}>by {content?.author || 'nobody'}</span>
              <span className={styles.hint}>{pages} {pages === 1 ? 'page' : 'pages'}</span>
            </div>
            <Btn size="sm" onClick={() => setEditing(true)}>Edit the book</Btn>
          </div>
        </SectionCard>

        <SectionCard title="Where it shows up" subtitle="Each of these hands out this same book.">
          <ul className={styles.paths}>
            <li><code>/guide</code> and <code>/smp guide</code>, any time a player asks</li>
            <li><code>/smp help</code>, while the guide is enabled</li>
            <li>Any kit with <em>Include guide</em> switched on, under Kits</li>
            <li>Any random teleport profile with <em>Give guide</em> switched on, under RTP</li>
          </ul>
        </SectionCard>
      </div>

      {editing && (
        <BookEditor
          content={content}
          onClose={() => setEditing(false)}
          onSave={next => { setContent(next); setDirty(true); setEditing(false) }}
        />
      )}
    </div>
  )
}
