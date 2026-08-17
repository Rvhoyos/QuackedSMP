import { useState, useEffect, useCallback } from 'react'
import { Dialog, ConfirmDialog, DialogButtons, Btn, Textarea } from '../../ui'
import styles from './CommandsPanel.module.css'

function authHeaders(token) { return token ? { Authorization: `Bearer ${token}` } : {} }

async function run(command, token) {
  try {
    const res = await fetch('/api/admin/exec', {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
      body: JSON.stringify({ command }),
    })
    if (res.status === 403) return 'auth'
    return res.ok ? 'ok' : 'err'
  } catch { return 'net' }
}

const GROUPS = [
  { label: 'World & Time', actions: [
    { label: 'Set Day', icon: '☀', cmd: 'time set day' },
    { label: 'Set Night', icon: '🌙', cmd: 'time set night' },
    { label: 'Clear Weather', icon: '🌤', cmd: 'weather clear' },
    { label: 'Set Thunder', icon: '⛈', cmd: 'weather thunder' },
    { label: 'Reset Dragon', icon: '🐉', cmd: 'smp end reset dragon' },
  ]},
  { label: 'Server', actions: [
    { label: 'Reload Config', icon: '↻', cmd: 'smp reload' },
  ]},
]

export default function CommandsPanel({ token, onExpired }) {
  const [history,  setHistory]  = useState([])
  const [input,    setInput]    = useState('')
  const [histIdx,  setHistIdx]  = useState(-1)
  const [broadcast, setBc]      = useState('')
  const [bcOpen,    setBcOpen]  = useState(false)
  const [stopOpen,  setStopOpen] = useState(false)
  const [endResetOpen, setEndResetOpen] = useState(false)
  const [regenOpen,    setRegenOpen]    = useState(false)
  const [regenPending, setRegenPending] = useState(false)
  const [loading,   setLoading] = useState(false)

  const loadRegenStatus = useCallback(async () => {
    try { const r = await fetch('/api/admin/regen', { headers: authHeaders(token) }); if (r.ok) { const d = await r.json(); setRegenPending(d.pending === true) } } catch {}
  }, [token])
  useEffect(() => { loadRegenStatus() }, [loadRegenStatus])

  async function queueRegen() {
    try { const r = await fetch('/api/admin/regen', { method: 'POST', headers: authHeaders(token) }); if (r.status === 403) { onExpired(); return } setRegenPending(true); setRegenOpen(false) } catch {}
  }
  async function cancelRegen() {
    try { const r = await fetch('/api/admin/regen', { method: 'DELETE', headers: authHeaders(token) }); if (r.status === 403) { onExpired(); return } setRegenPending(false) } catch {}
  }

  async function exec(cmd) {
    if (!cmd.trim()) return false
    setLoading(true)
    const result = await run(cmd, token)
    if (result === 'auth') { onExpired(); return false }
    const ok = result === 'ok'
    setHistory(h => [{ cmd: result === 'net' ? `${cmd}  ← network error` : cmd, ok, ts: Date.now() }, ...h].slice(0, 50))
    setLoading(false)
    return ok
  }

  async function submitInput(e) { e.preventDefault(); if (!input.trim()) return; await exec(input.trim()); setInput(''); setHistIdx(-1) }
  function handleKeyDown(e) {
    if (e.key === 'ArrowUp') { e.preventDefault(); const n = Math.min(histIdx + 1, history.length - 1); setHistIdx(n); if (history[n]) setInput(history[n].cmd) }
    else if (e.key === 'ArrowDown') { e.preventDefault(); const n = Math.max(histIdx - 1, -1); setHistIdx(n); setInput(n === -1 ? '' : history[n]?.cmd ?? '') }
  }
  async function sendBroadcast() { if (!broadcast.trim()) return; await exec(`say ${broadcast.trim()}`); setBc(''); setBcOpen(false) }

  return (
    <div className={styles.wrap}>
      <div className={styles.scroll}>
        {GROUPS.map(g => (
          <div key={g.label} className={styles.group}>
            <div className={styles.groupLabel}>{g.label}</div>
            <div className={styles.grid}>
              {g.actions.map(a => (
                <button key={a.cmd} className={styles.quickBtn} onClick={() => exec(a.cmd)} title={a.cmd}>
                  <span className={styles.quickIcon}>{a.icon}</span>{a.label}
                </button>
              ))}
              {g.label === 'Server' && (
                <button className={styles.quickBtn} onClick={() => setBcOpen(true)}><span className={styles.quickIcon}>📢</span>Broadcast</button>
              )}
            </div>
          </div>
        ))}

        {/* Danger zone */}
        <div className={styles.dangerZone}>
          <div className={styles.dangerLabel}>⚠ Danger Zone</div>
          <div className={styles.grid}>
            <button className={styles.dangerBtn} onClick={() => setEndResetOpen(true)}><span className={styles.quickIcon}>🌍</span>Queue End Reset</button>
            <button className={styles.dangerBtn} onClick={() => regenPending ? cancelRegen() : setRegenOpen(true)}><span className={styles.quickIcon}>🌿</span>{regenPending ? 'Cancel Regen' : 'Regen Wilderness'}</button>
            <button className={`${styles.dangerBtn} ${styles.stopBtn}`} onClick={() => setStopOpen(true)}><span className={styles.quickIcon}>⏹</span>Stop Server</button>
          </div>
        </div>

        {/* Terminal */}
        <div className={styles.terminal}>
          <div className={styles.termHeader}>
            <span className={styles.termTitle}>Terminal</span>
            <span className={styles.termHint}>↑↓ history</span>
          </div>
          <div className={styles.termLog}>
            {history.length === 0 && <span className={styles.termEmpty}>No commands run yet.</span>}
            {history.map((h, i) => (
              <div key={i} className={`${styles.termLine} ${h.ok ? styles.termOk : styles.termFail}`}>
                <span className={styles.termArrow}>{h.ok ? '↳' : '✗'}</span>
                <span className={styles.termCmd}>{h.cmd}</span>
              </div>
            ))}
          </div>
          <form className={styles.termInput} onSubmit={submitInput}>
            <span className={styles.termPrompt}>{'>'}</span>
            <input className={styles.termField} type="text" value={input}
              onChange={e => { setInput(e.target.value); setHistIdx(-1) }}
              onKeyDown={handleKeyDown} placeholder="say Hello, World!" spellCheck={false} autoComplete="off" autoCapitalize="off" />
            <button className={styles.termRun} type="submit" disabled={loading || !input.trim()}>{loading ? '…' : '▶'}</button>
          </form>
        </div>
      </div>

      <ConfirmDialog open={stopOpen} onOpenChange={setStopOpen} title="⏹ Stop Server"
        description="This runs /stop. The server restarts automatically only if your host is configured for it (systemd, pterodactyl, etc.)."
        confirmLabel="Stop Server" tone="danger" onConfirm={() => { setStopOpen(false); exec('stop') }} />
      <ConfirmDialog open={endResetOpen} onOpenChange={setEndResetOpen} title="🌍 Queue End Reset"
        description="Queues the End for a full terrain reset. All builds, chests, and entities in the End are permanently destroyed on the next server shutdown."
        confirmLabel="Queue Reset" tone="warn" onConfirm={() => { setEndResetOpen(false); exec('smp end reset world') }} />
      <ConfirmDialog open={regenOpen} onOpenChange={setRegenOpen} title="🌿 Regenerate Wilderness"
        description="Regenerates ALL unclaimed chunks across every dimension (claimed chunks + a 3-chunk buffer are preserved). Unclaimed builds are permanently lost on the next shutdown. Irreversible."
        confirmLabel="Queue Regen" tone="danger" onConfirm={queueRegen} />
      <Dialog open={bcOpen} onOpenChange={setBcOpen} title="📢 Broadcast to All Players">
        <Textarea placeholder="Your message here…" value={broadcast} onChange={e => setBc(e.target.value)} rows={3} autoFocus />
        <DialogButtons>
          <Btn variant="ghost" onClick={() => setBcOpen(false)}>Cancel</Btn>
          <Btn variant="primary" disabled={!broadcast.trim()} onClick={sendBroadcast}>Send</Btn>
        </DialogButtons>
      </Dialog>
    </div>
  )
}
