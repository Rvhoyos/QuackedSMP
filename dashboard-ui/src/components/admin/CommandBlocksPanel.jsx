import { useState, useEffect, useCallback } from 'react'
import styles from './ClaimsPanel.module.css'

const MODE_LABELS = { REDSTONE: 'Impulse', AUTO: 'Repeat', SEQUENCE: 'Chain' }
const MODE_COLORS = { REDSTONE: '#C8721E', AUTO: '#8932B8', SEQUENCE: '#3D8B3D' }
const MODE_OPTIONS = [
  { value: 'REDSTONE', label: 'Impulse' },
  { value: 'AUTO',     label: 'Repeat' },
  { value: 'SEQUENCE', label: 'Chain' },
]

export default function CommandBlocksPanel({ token, onExpired }) {
  const [blocks,     setBlocks]     = useState([])
  const [enabled,    setEnabled]    = useState(true)
  const [loading,    setLoading]    = useState(false)
  const [error,      setError]      = useState(null)
  const [editing,    setEditing]    = useState(null)
  const [editState,  setEditState]  = useState({})
  const [confirmDel, setConfirmDel] = useState(null)
  const [copied,     setCopied]     = useState(null)

  const auth = { Authorization: `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await fetch('/api/admin/commandblocks', { headers: auth })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      if (!r.ok) { setError(`Server error (${r.status})`); setLoading(false); return }
      const d = await r.json()
      if (d.error) { setError(d.error) }
      else { setBlocks(d.blocks || []); setEnabled(d.enabled !== false) }
    } catch { setError('Failed to load command blocks') }
    setLoading(false)
  }, [token])

  useEffect(() => { load() }, [load])

  const blockKey = b => `${b.dim}:${b.x},${b.y},${b.z}`
  const dimName = d => d.split(':').pop()

  function startEdit(b) {
    setEditing(blockKey(b))
    setEditState({
      command: b.command,
      mode: b.mode,
      auto: b.auto,
      conditional: b.conditional,
      trackOutput: b.trackOutput,
    })
  }

  async function saveEdit(b) {
    try {
      const r = await fetch('/api/admin/commandblocks/update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ dim: b.dim, x: b.x, y: b.y, z: b.z, ...editState }),
      })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setEditing(null)
      load()
    } catch { setError('Update failed') }
  }

  async function deleteBlock(b) {
    try {
      const r = await fetch('/api/admin/commandblocks/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ dim: b.dim, x: b.x, y: b.y, z: b.z }),
      })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setConfirmDel(null)
      load()
    } catch { setError('Delete failed') }
  }

  function copyTp(b) {
    const cmd = `/tp @s ${b.x} ${b.y} ${b.z}`
    navigator.clipboard.writeText(cmd).then(() => {
      setCopied(blockKey(b))
      setTimeout(() => setCopied(null), 1500)
    })
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.toolbar}>
        <span className={styles.count}>
          {loading ? 'Scanning...' : `${blocks.length} command block${blocks.length !== 1 ? 's' : ''} in loaded chunks`}
        </span>
        <button className={styles.refresh} onClick={load} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {!enabled && (
        <div className={styles.error} style={{ background: '#2A1A00', borderColor: '#5A3A00', color: '#FFCC55' }}>
          Command blocks are disabled (gamerule commandBlocksEnabled)
        </div>
      )}

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>x</button>
        </div>
      )}

      <div className={styles.list}>
        {blocks.length === 0 && !loading && (
          <div className={styles.empty}>No command blocks found in loaded chunks</div>
        )}
        {blocks.map(b => {
          const key = blockKey(b)
          const isEditing = editing === key
          return (
            <div key={key}>
              <div className={styles.row}>
                <div className={styles.playerInfo}>
                  <span className={styles.playerName}>
                    {dimName(b.dim)} [{b.x}, {b.y}, {b.z}]
                  </span>
                  <span className={styles.playerUuid} style={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {b.command || '(empty)'}
                  </span>
                </div>
                <div className={styles.barWrap}>
                  <span style={{
                    fontFamily: 'var(--font-pixel)', fontSize: '6px', letterSpacing: '0.3px',
                    padding: '3px 8px', background: MODE_COLORS[b.mode] || '#555',
                    color: '#FFF', whiteSpace: 'nowrap',
                  }}>
                    {MODE_LABELS[b.mode] || b.mode}
                  </span>
                  {b.conditional && (
                    <span className={styles.claimCount} style={{ minWidth: 'auto', color: '#FFAA00', fontSize: '10px' }}>
                      Conditional
                    </span>
                  )}
                  {b.auto && (
                    <span className={styles.claimCount} style={{ minWidth: 'auto', color: '#4CAF50', fontSize: '10px' }}>
                      Always Active
                    </span>
                  )}
                  {b.customName && b.customName !== '@' && (
                    <span className={styles.claimCount} style={{ minWidth: 'auto', color: '#888', fontSize: '10px' }}>
                      {b.customName}
                    </span>
                  )}
                </div>
                <div className={styles.actions}>
                  <button className={styles.btnGhost} onClick={() => copyTp(b)}>
                    {copied === key ? 'Copied!' : 'TP'}
                  </button>
                  <button className={styles.btnGhost} onClick={() => isEditing ? setEditing(null) : startEdit(b)}>
                    {isEditing ? 'Cancel' : 'Edit'}
                  </button>
                  {confirmDel === key ? (
                    <>
                      <span className={styles.confirmLabel}>Delete?</span>
                      <button className={styles.btnDanger} onClick={() => deleteBlock(b)}>Confirm</button>
                      <button className={styles.btnGhost} onClick={() => setConfirmDel(null)}>Cancel</button>
                    </>
                  ) : (
                    <button className={styles.btnDanger} onClick={() => setConfirmDel(key)}>Delete</button>
                  )}
                </div>
              </div>

              {isEditing && (
                <div style={{ padding: '10px 14px 14px 20px', borderBottom: '1px solid #0F0F0F', background: '#111' }}>
                  <div style={{ marginBottom: 8 }}>
                    <label style={{ fontFamily: 'var(--font-pixel)', fontSize: '6px', color: '#666', display: 'block', marginBottom: 4 }}>
                      Command
                    </label>
                    <input
                      type="text"
                      value={editState.command}
                      onChange={e => setEditState(s => ({ ...s, command: e.target.value }))}
                      style={{
                        width: '100%', boxSizing: 'border-box',
                        fontFamily: 'var(--font-mono)', fontSize: '12px',
                        background: '#0A0A0A', border: '1px solid #333', color: '#DDD',
                        padding: '6px 8px',
                      }}
                    />
                  </div>
                  <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 10, flexWrap: 'wrap' }}>
                    <label style={{ fontFamily: 'var(--font-pixel)', fontSize: '6px', color: '#666', display: 'flex', alignItems: 'center', gap: 6 }}>
                      Mode
                      <select
                        value={editState.mode}
                        onChange={e => setEditState(s => ({ ...s, mode: e.target.value }))}
                        style={{
                          fontFamily: 'var(--font-mono)', fontSize: '11px',
                          background: '#0A0A0A', border: '1px solid #333', color: '#DDD',
                          padding: '4px 6px',
                        }}
                      >
                        {MODE_OPTIONS.map(o => (
                          <option key={o.value} value={o.value}>{o.label}</option>
                        ))}
                      </select>
                    </label>
                    <label style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: '#888', display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                      <input type="checkbox" checked={editState.auto} onChange={e => setEditState(s => ({ ...s, auto: e.target.checked }))} />
                      Always Active
                    </label>
                    <label style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: '#888', display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                      <input type="checkbox" checked={editState.conditional} onChange={e => setEditState(s => ({ ...s, conditional: e.target.checked }))} />
                      Conditional
                    </label>
                    <label style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: '#888', display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                      <input type="checkbox" checked={editState.trackOutput} onChange={e => setEditState(s => ({ ...s, trackOutput: e.target.checked }))} />
                      Track Output
                    </label>
                  </div>
                  <button
                    className={styles.btnGhost}
                    style={{ borderColor: '#4CAF50', color: '#4CAF50' }}
                    onClick={() => saveEdit(b)}
                  >
                    Save
                  </button>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
