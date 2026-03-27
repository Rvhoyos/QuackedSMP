import { useState, useEffect, useRef } from 'react'
import styles from './ModsPanel.module.css'

export default function ModsPanel({ token, onExpired }) {
  const [mods,          setMods]          = useState([])
  const [loading,       setLoading]       = useState(false)
  const [error,         setError]         = useState(null)
  const [status,        setStatus]        = useState(null)
  const [uploading,     setUploading]     = useState(false)
  const [uploadPct,     setUploadPct]     = useState(0)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const fileRef = useRef(null)

  const auth = { Authorization: `Bearer ${token}` }

  function flash(msg) {
    setStatus(msg)
    setTimeout(() => setStatus(null), 4000)
  }

  async function loadMods() {
    setLoading(true)
    setError(null)
    try {
      const r = await fetch('/api/admin/mods', { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else setMods(Array.isArray(d) ? d : [])
    } catch { setError('Failed to load mod list') }
    setLoading(false)
  }

  useEffect(() => { loadMods() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function deleteMod(filename) {
    setConfirmDelete(null)
    setError(null)
    try {
      const r = await fetch('/api/admin/mods', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ filename }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { flash(`Deleted ${filename}`); loadMods() }
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
    setUploading(true)
    setUploadPct(0)
    setError(null)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/admin/mods/upload')
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.setRequestHeader('X-Filename', file.name)
    xhr.setRequestHeader('Content-Type', 'application/octet-stream')

    xhr.upload.addEventListener('progress', e => {
      if (e.lengthComputable) setUploadPct(Math.round((e.loaded / e.total) * 100))
    })

    xhr.onload = () => {
      setUploading(false)
      if (xhr.status === 401) { onExpired(); return }
      try {
        const d = JSON.parse(xhr.responseText)
        if (d.error) setError(d.error)
        else { flash(`Uploaded ${d.filename}`); loadMods() }
      } catch { setError('Upload failed') }
    }
    xhr.onerror = () => { setUploading(false); setError('Network error during upload') }
    xhr.send(file)
  }

  function fmtSize(bytes) {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  }

  function fmtDate(ms) {
    return new Date(ms).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
  }

  return (
    <div className={styles.wrap}>

      <div className={styles.banner}>
        Changes take effect after server restart
      </div>

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>✕</button>
        </div>
      )}
      {status && <div className={styles.statusMsg}>{status}</div>}

      <div className={styles.toolbar}>
        <span className={styles.count}>
          {loading ? 'Loading…' : `${mods.length} mod${mods.length !== 1 ? 's' : ''} installed`}
        </span>
        <button className={styles.btnGhost} onClick={loadMods} disabled={loading || uploading}>
          Refresh
        </button>
        <button
          className={styles.btn}
          onClick={() => fileRef.current?.click()}
          disabled={uploading}
        >
          {uploading ? `Uploading… ${uploadPct}%` : 'Upload Mod'}
        </button>
        <input ref={fileRef} type="file" accept=".jar" style={{ display: 'none' }} onChange={onFileChange} />
      </div>

      {uploading && (
        <div className={styles.progressTrack}>
          <div className={styles.progressFill} style={{ width: `${uploadPct}%` }} />
        </div>
      )}

      <div className={styles.list}>
        {!loading && mods.length === 0 && (
          <div className={styles.empty}>No mods found in mods/ directory</div>
        )}

        {mods.map(mod => (
          <div key={mod.name} className={`${styles.row} ${mod.active ? styles.rowActive : ''}`}>
            <div className={styles.modInfo}>
              <span className={styles.modName}>{mod.name}</span>
              {mod.active && <span className={styles.badge}>active</span>}
            </div>
            <span className={styles.modMeta}>{fmtSize(mod.size)} · {fmtDate(mod.modified)}</span>

            {mod.active ? (
              <span className={styles.activeHint}>managed automatically</span>
            ) : confirmDelete === mod.name ? (
              <div className={styles.confirmRow}>
                <span className={styles.confirmText}>Delete {mod.name}?</span>
                <button className={styles.btnDanger} onClick={() => deleteMod(mod.name)}>Confirm</button>
                <button className={styles.btnGhost} onClick={() => setConfirmDelete(null)}>Cancel</button>
              </div>
            ) : (
              <button className={styles.btnDanger} onClick={() => setConfirmDelete(mod.name)}>Delete</button>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
