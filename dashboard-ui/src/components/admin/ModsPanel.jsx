import { useState, useEffect, useRef } from 'react'
import { Toolbar, IconButton, Btn, Badge, Loading, EmptyState, ErrorBanner, ConfirmDialog, useToast } from '../../ui'
import { IconMod } from './MinecraftIcons'
import styles from './ModsPanel.module.css'

export default function ModsPanel({ token, onExpired }) {
  const [mods,       setMods]       = useState([])
  const [loading,    setLoading]    = useState(false)
  const [error,      setError]      = useState(null)
  const [uploading,  setUploading]  = useState(false)
  const [uploadPct,  setUploadPct]  = useState(0)
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
      const r = await fetch('/api/admin/mods', {
        method: 'DELETE', headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ filename }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error); else { toast(`Deleted ${filename}`); loadMods() }
    } catch { setError('Delete failed') }
  }

  function onFileChange(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (!file.name.endsWith('.jar')) { setError('Only .jar files are allowed'); return }
    uploadFile(file)
  }

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
      try {
        const d = JSON.parse(xhr.responseText)
        if (d.error) setError(d.error); else { toast(`Uploaded ${d.filename}`); loadMods() }
      } catch { setError('Upload failed') }
    }
    xhr.onerror = () => { setUploading(false); setError('Network error during upload') }
    xhr.send(file)
  }

  const fmtSize = b => b < 1024 ? `${b} B` : b < 1048576 ? `${(b / 1024).toFixed(1)} KB` : `${(b / 1048576).toFixed(1)} MB`
  const fmtDate = ms => new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })

  if (loading && mods.length === 0) return <Loading label="Loading mods…" />

  return (
    <div className={styles.wrap}>
      <Toolbar title="Mods" count={`${mods.length} installed`}>
        <IconButton tip="Refresh" onClick={loadMods} disabled={loading || uploading}>↻</IconButton>
        <Btn size="sm" variant="primary" disabled={uploading} onClick={() => fileRef.current?.click()}>
          {uploading ? `Uploading… ${uploadPct}%` : 'Upload Mod'}
        </Btn>
        <input ref={fileRef} type="file" accept=".jar" style={{ display: 'none' }} onChange={onFileChange} />
      </Toolbar>

      <div className={styles.banner}>Changes take effect after a server restart.</div>
      <ErrorBanner>{error}</ErrorBanner>

      {uploading && <div className={styles.track}><div className={styles.fill} style={{ width: `${uploadPct}%` }} /></div>}

      {mods.length === 0 ? (
        <EmptyState icon={<IconMod size={30} />} label="No mods found" hint="Upload a .jar to add it to the server's mods/ directory." />
      ) : (
        <div className={styles.list}>
          {mods.map(mod => (
            <div key={mod.name} className={`${styles.row} ${mod.active ? styles.rowActive : ''}`}>
              <div className={styles.info}>
                <span className={styles.name}>{mod.name}</span>
                {mod.active && <Badge variant="ok">active</Badge>}
              </div>
              <span className={styles.meta}>{fmtSize(mod.size)} · {fmtDate(mod.modified)}</span>
              {mod.active
                ? <span className={styles.hint}>managed automatically</span>
                : <Btn size="sm" variant="danger" onClick={() => setConfirmDel(mod.name)}>Delete</Btn>}
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!confirmDel}
        onOpenChange={o => !o && setConfirmDel(null)}
        title={confirmDel ? `Delete ${confirmDel}?` : ''}
        description="The file is removed from the mods/ directory. Takes effect after a restart."
        confirmLabel="Delete"
        tone="danger"
        onConfirm={deleteMod}
      />
    </div>
  )
}
