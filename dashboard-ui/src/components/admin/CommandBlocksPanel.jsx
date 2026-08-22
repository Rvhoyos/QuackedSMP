import { useState, useEffect, useCallback, useMemo } from 'react'
import { Toolbar, IconButton, Btn, Badge, Loading, EmptyState, ErrorBanner, ConfirmDialog, Field, Input, Select, Toggle, Menu, MenuItem, useToast } from '../../ui'
import { IconRepeatCmdBlock } from './MinecraftIcons'
import styles from './CommandBlocksPanel.module.css'

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
  const toast = useToast()

  const auth = { Authorization: `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const r = await fetch('/api/admin/commandblocks', { headers: auth })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      if (!r.ok) { setError(`Server error (${r.status})`); setLoading(false); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { setBlocks(d.blocks || []); setEnabled(d.enabled !== false) }
    } catch { setError('Failed to load command blocks') }
    setLoading(false)
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load() }, [load])

  const blockKey = b => `${b.dim}:${b.x},${b.y},${b.z}`
  const dimName = d => d.split(':').pop().replace(/_/g, ' ')

  const byDim = useMemo(() => {
    const m = {}
    for (const b of blocks) { (m[b.dim] ??= []).push(b) }
    return m
  }, [blocks])

  function startEdit(b) {
    setEditing(blockKey(b))
    setEditState({ command: b.command, mode: b.mode, auto: b.auto, conditional: b.conditional, trackOutput: b.trackOutput })
  }
  async function saveEdit(b) {
    try {
      const r = await fetch('/api/admin/commandblocks/update', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ dim: b.dim, x: b.x, y: b.y, z: b.z, ...editState }) })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setEditing(null); toast('Command block updated'); load()
    } catch { setError('Update failed') }
  }
  async function deleteBlock() {
    const b = confirmDel
    try {
      const r = await fetch('/api/admin/commandblocks/delete', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ dim: b.dim, x: b.x, y: b.y, z: b.z }) })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setConfirmDel(null); toast('Command block deleted'); load()
    } catch { setError('Delete failed') }
  }
  function copyTp(b) {
    navigator.clipboard.writeText(`/tp @s ${b.x} ${b.y} ${b.z}`).then(() => { setCopied(blockKey(b)); setTimeout(() => setCopied(null), 1500) })
  }

  if (loading && blocks.length === 0) return <Loading label="Scanning loaded chunks…" />

  return (
    <div className={styles.wrap}>
      <Toolbar title="Command Blocks" count={`${blocks.length} in loaded chunks`}>
        <IconButton tip="Refresh" onClick={load}>↻</IconButton>
      </Toolbar>

      {!enabled && <div className={styles.disabledNote}>Command blocks are disabled (gamerule commandBlocksEnabled)</div>}
      <ErrorBanner>{error}</ErrorBanner>

      {blocks.length === 0 ? (
        <EmptyState icon={<IconRepeatCmdBlock size={30} />} label="No command blocks found" hint="Only command blocks inside currently-loaded chunks are listed." />
      ) : (
        <div className={styles.body}>
          {Object.entries(byDim).map(([dim, list]) => (
            <div key={dim} className={styles.dimGroup}>
              <div className={styles.dimHead}>
                <span className={styles.dimName}>{dimName(dim)}</span>
                <span className={styles.dimCount}>{list.length}</span>
              </div>
              {list.map(b => {
                const key = blockKey(b)
                const isEditing = editing === key
                return (
                  <div key={key} className={styles.block}>
                    <div className={styles.row}>
                      <span className={styles.mode} style={{ background: MODE_COLORS[b.mode] || '#555' }}>{MODE_LABELS[b.mode] || b.mode}</span>
                      <div className={styles.info}>
                        <span className={styles.coords}>[{b.x}, {b.y}, {b.z}]</span>
                        <span className={styles.cmd}>{b.command || '(empty)'}</span>
                      </div>
                      <div className={styles.flags}>
                        {b.conditional && <Badge variant="warn">Cond</Badge>}
                        {b.auto && <Badge variant="ok">Auto</Badge>}
                      </div>
                      <Menu trigger={<Btn size="sm" aria-label="Actions">⋯</Btn>}>
                        <MenuItem onSelect={() => copyTp(b)}>{copied === key ? 'Copied!' : 'Copy TP'}</MenuItem>
                        <MenuItem onSelect={() => isEditing ? setEditing(null) : startEdit(b)}>{isEditing ? 'Close Editor' : 'Edit'}</MenuItem>
                        <MenuItem tone="danger" onSelect={() => setConfirmDel(b)}>Delete</MenuItem>
                      </Menu>
                    </div>
                    {isEditing && (
                      <div className={styles.editForm}>
                        <Field label="Command"><Input value={editState.command} onChange={e => setEditState(s => ({ ...s, command: e.target.value }))} style={{ fontFamily: 'var(--font-mono)' }} /></Field>
                        <div className={styles.editControls}>
                          <Field label="Mode"><Select value={editState.mode} onChange={e => setEditState(s => ({ ...s, mode: e.target.value }))}>{MODE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</Select></Field>
                          <div className={styles.toggleRow}><span className={styles.tLabel}>Always Active</span><Toggle checked={editState.auto} onChange={v => setEditState(s => ({ ...s, auto: v }))} aria-label="Always active" /></div>
                          <div className={styles.toggleRow}><span className={styles.tLabel}>Conditional</span><Toggle checked={editState.conditional} onChange={v => setEditState(s => ({ ...s, conditional: v }))} aria-label="Conditional" /></div>
                          <div className={styles.toggleRow}><span className={styles.tLabel}>Track Output</span><Toggle checked={editState.trackOutput} onChange={v => setEditState(s => ({ ...s, trackOutput: v }))} aria-label="Track output" /></div>
                        </div>
                        <Btn variant="ok" onClick={() => saveEdit(b)}>Save</Btn>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!confirmDel}
        onOpenChange={o => !o && setConfirmDel(null)}
        title={confirmDel ? `Delete command block at [${confirmDel.x}, ${confirmDel.y}, ${confirmDel.z}]?` : ''}
        confirmLabel="Delete"
        tone="danger"
        onConfirm={deleteBlock}
      />
    </div>
  )
}
