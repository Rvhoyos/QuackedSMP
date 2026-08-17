import { useState, useEffect, useRef } from 'react'
import { Toolbar, IconButton, Badge, StatCard, Loading, EmptyState, ErrorBanner, ConfirmDialog, Menu, MenuItem, Btn, useToast } from '../../ui'
import { IconMod } from './MinecraftIcons'
import styles from './ModsPanel.module.css'

export default function ModsPanel({ token, onExpired }) {
  const [mods,       setMods]       = useState([])
  const [loading,    setLoading]    = useState(false)
  const [error,      setError]      = useState(null)
  const [uploading,  setUploading]  = useState(false)
  const [uploadPct,  setUploadPct]  = useState(0)
  const [dragging,   setDragging]   = useState(false)
  const [confirmDel, setConfirmDel] = useState(null)
  const fileRef = useRef(null)
  const toast = useToast()

  const auth = { Authorization: `Bearer ${token}` }

  async function loadMods() {
    setLoading(true); setError(null)
    try {
      const r = await fetch('/api/admin/mods', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error); else setMods(Array.isArray(d) ? d : [])
    } catch { setError('Failed to load mod list') }
    setLoading(false)
  }
  useEffect(() => { loadMods() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function deleteMod() {
    const filename = confirmDel
    setConfirmDel(null); setError(null)
    try {
      const r = await fetch('/api/admin/mods', { method: 'DELETE', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ filename }) })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error); else { toast(`Deleted ${filename}`); loadMods() }
    } catch { setError('Delete failed') }
  }

  function pickFile(file) {
    if (!file) return
    if (!file.name.endsWith('.jar')) { setError('Only .jar files are allowed'); return }
    uploadFile(file)
  }
  function onFileChange(e) { const f = e.target.files?.[0]; e.target.value = ''; pickFile(f) }
  function onDrop(e) { e.preventDefault(); setDragging(false); pickFile(e.dataTransfer.files?.[0]) }

  function uploadFile(file) {
    setUploading(true); setUploadPct(0); setError(null)
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/admin/mods/upload')
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.setRequestHeader('X-Filename', file.name)
    xhr.setRequestHeader('Content-Type', 'application/octet-stream')
    xhr.upload.addEventListener('progress', e => { if (e.lengthComputable) setUploadPct(Math.round((e.loaded / e.total) * 100)) })
    xhr.onload = () => {
      setUploading(false)
      if (xhr.status === 401) { onExpired(); return }
      try { const d = JSON.parse(xhr.responseText); if (d.error) setError(d.error); else { toast(`Uploaded ${d.filename}`); loadMods() } }
      catch { setError('Upload failed') }
    }
    xhr.onerror = () => { setUploading(false); setError('Network error during upload') }
    xhr.send(file)
  }

  const fmtSize = b => b < 1024 ? `${b} B` : b < 1048576 ? `${(b / 1024).toFixed(1)} KB` : `${(b / 1048576).toFixed(1)} MB`
  const fmtDate = ms => new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })

  const active = mods.filter(m => m.active)
  const others = mods.filter(m => !m.active)

  return (
    <div className={styles.wrap}>
      <Toolbar title="Mods" count={`${mods.length} installed`}>
        <IconButton tip="Refresh" onClick={loadMods} disabled={loading || uploading}>↻</IconButton>
      </Toolbar>

      <div className={styles.body}>
        <div
          className={`${styles.dropzone} ${dragging ? styles.dropActive : ''}`}
          onDragOver={e => { e.preventDefault(); setDragging(true) }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          onClick={() => !uploading && fileRef.current?.click()}
        >
          <IconMod size={28} />
          {uploading
            ? <span className={styles.dropText}>Uploading… {uploadPct}%</span>
            : <span className={styles.dropText}>Drop a <b>.jar</b> here or click to upload</span>}
          <span className={styles.dropHint}>Changes take effect after a server restart.</span>
          {uploading && <div className={styles.track}><div className={styles.fill} style={{ width: `${uploadPct}%` }} /></div>}
          <input ref={fileRef} type="file" accept=".jar" style={{ display: 'none' }} onChange={onFileChange} />
        </div>

        <ErrorBanner>{error}</ErrorBanner>

        {loading && mods.length === 0 ? <Loading label="Loading mods…" />
          : mods.length === 0 ? <EmptyState icon={<IconMod size={30} />} label="No mods found" hint="Drop a .jar above to add it to the server." />
          : (
            <>
              {others.length > 0 && (
                <div className={styles.section}>
                  <div className={styles.sectionLabel}>Installed</div>
                  <div className={styles.grid}>
                    {others.map(m => (
                      <div key={m.name} className={styles.card}>
                        <div className={styles.cardTop}>
                          <span className={styles.name}>{m.name}</span>
                          <Menu trigger={<Btn size="sm" aria-label="Actions">⋯</Btn>}>
                            <MenuItem tone="danger" onSelect={() => setConfirmDel(m.name)}>Delete</MenuItem>
                          </Menu>
                        </div>
                        <span className={styles.meta}>{fmtSize(m.size)} · {fmtDate(m.modified)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {active.length > 0 && (
                <div className={styles.section}>
                  <div className={styles.sectionLabel}>Managed (active)</div>
                  <div className={styles.grid}>
                    {active.map(m => (
                      <div key={m.name} className={`${styles.card} ${styles.cardActive}`}>
                        <div className={styles.cardTop}>
                          <span className={styles.name}>{m.name}</span>
                          <Badge variant="ok">active</Badge>
                        </div>
                        <span className={styles.meta}>{fmtSize(m.size)} · managed automatically</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
      </div>

      <ConfirmDialog
        open={!!confirmDel}
        onOpenChange={o => !o && setConfirmDel(null)}
        title={confirmDel ? `Delete ${confirmDel}?` : ''}
        description="Removes the file from mods/. Takes effect after a restart."
        confirmLabel="Delete"
        tone="danger"
        onConfirm={deleteMod}
      />
    </div>
  )
}
