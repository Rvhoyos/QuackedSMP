import { useState } from 'react'
import styles from './CommandsPanel.module.css'

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function run(command, token) {
  try {
    const res = await fetch('/api/admin/exec', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
      body: JSON.stringify({ command }),
    })
    if (res.status === 403) return 'auth'
    return res.ok ? 'ok' : 'err'
  } catch {
    return 'net'
  }
}

const QUICK_ACTIONS = [
  {
    group: 'Server',
    actions: [
      { label: 'Reload Config',    icon: '↻',  cmd: 'smp reload' },
      { label: 'Reset Dragon',     icon: '🐉', cmd: 'smp end reset dragon' },
      { label: 'Queue End Reset',  icon: '🌍', cmd: 'smp end reset world' },
      { label: 'Set Day',          icon: '☀',  cmd: 'time set day' },
      { label: 'Set Night',        icon: '🌙', cmd: 'time set night' },
      { label: 'Clear Weather',    icon: '🌤', cmd: 'weather clear' },
      { label: 'Set Thunder',      icon: '⛈',  cmd: 'weather thunder' },
    ],
  },
]

export default function CommandsPanel({ token, onExpired }) {
  const [history,  setHistory]  = useState([])  // [{cmd, ok, ts}]
  const [input,    setInput]    = useState('')
  const [histIdx,  setHistIdx]  = useState(-1)
  const [broadcast, setBc]      = useState('')
  const [bcOpen,    setBcOpen]  = useState(false)
  const [stopOpen,  setStopOpen] = useState(false)
  const [loading,   setLoading] = useState(false)

  async function exec(cmd) {
    if (!cmd.trim()) return
    setLoading(true)
    const result = await run(cmd, token)
    if (result === 'auth') { onExpired(); return false }
    const ok = result === 'ok'
    const label = result === 'net' ? `${cmd}  ← network error` : cmd
    setHistory(h => [{ cmd: label, ok, ts: Date.now() }, ...h].slice(0, 50))
    setLoading(false)
    return ok
  }

  async function submitInput(e) {
    e.preventDefault()
    if (!input.trim()) return
    await exec(input.trim())
    setInput('')
    setHistIdx(-1)
  }

  function handleKeyDown(e) {
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      const next = Math.min(histIdx + 1, history.length - 1)
      setHistIdx(next)
      if (history[next]) setInput(history[next].cmd)
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      const next = Math.max(histIdx - 1, -1)
      setHistIdx(next)
      setInput(next === -1 ? '' : history[next]?.cmd ?? '')
    }
  }

  async function sendBroadcast() {
    if (!broadcast.trim()) return
    await exec(`say ${broadcast.trim()}`)
    setBc('')
    setBcOpen(false)
  }

  return (
    <div className={styles.wrap}>
      {/* Quick Actions */}
      {QUICK_ACTIONS.map(group => (
        <div key={group.group} className={styles.group}>
          <h3 className={styles.groupLabel}>{group.group}</h3>
          <div className={styles.grid}>
            {group.actions.map(a => (
              <button
                key={a.cmd}
                className={styles.quickBtn}
                onClick={() => exec(a.cmd)}
                title={a.cmd}
              >
                <span className={styles.quickIcon}>{a.icon}</span>
                {a.label}
              </button>
            ))}
            <button
              className={`${styles.quickBtn} ${styles.quickBroadcast}`}
              onClick={() => setBcOpen(true)}
            >
              <span className={styles.quickIcon}>📢</span>
              Broadcast
            </button>
            <button
              className={`${styles.quickBtn} ${styles.quickStop}`}
              onClick={() => setStopOpen(true)}
            >
              <span className={styles.quickIcon}>⏹</span>
              Stop Server
            </button>
          </div>
        </div>
      ))}

      <div className={styles.divider} />

      {/* Terminal */}
      <div className={styles.terminal}>
        <div className={styles.termHeader}>
          <span className={styles.termTitle}>Terminal</span>
          <span className={styles.termHint}>↑↓ history</span>
        </div>

        <div className={styles.termLog}>
          {history.length === 0 && (
            <span className={styles.termEmpty}>No commands run yet.</span>
          )}
          {history.map((h, i) => (
            <div key={i} className={`${styles.termLine} ${h.ok ? styles.termOk : styles.termFail}`}>
              <span className={styles.termArrow}>{h.ok ? '↳' : '✗'}</span>
              <span className={styles.termCmd}>{h.cmd}</span>
            </div>
          ))}
        </div>

        <form className={styles.termInput} onSubmit={submitInput}>
          <span className={styles.termPrompt}>{'>'}</span>
          <input
            className={styles.termField}
            type="text"
            value={input}
            onChange={e => { setInput(e.target.value); setHistIdx(-1) }}
            onKeyDown={handleKeyDown}
            placeholder="say Hello, World!"
            spellCheck={false}
            autoComplete="off"
            autoCapitalize="off"
          />
          <button className={styles.termRun} type="submit" disabled={loading || !input.trim()}>
            {loading ? '…' : '▶'}
          </button>
        </form>
      </div>

      {/* Stop server confirmation */}
      {stopOpen && (
        <div className={styles.bcOverlay} onClick={() => setStopOpen(false)}>
          <div className={styles.bcBox} onClick={e => e.stopPropagation()}>
            <h3 className={`${styles.bcTitle} ${styles.stopTitle}`}>⏹ Stop Server</h3>
            <p className={styles.stopWarning}>
              This will run <code>/stop</code>. The server will restart automatically
              only if your hosting environment is configured to do so (systemd, pterodactyl, etc.).
            </p>
            <div className={styles.bcBtns}>
              <button className={styles.bcCancel} onClick={() => setStopOpen(false)}>Cancel</button>
              <button
                className={styles.stopConfirm}
                onClick={() => { setStopOpen(false); exec('stop') }}
              >
                Stop Server
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Broadcast modal */}
      {bcOpen && (
        <div className={styles.bcOverlay} onClick={() => setBcOpen(false)}>
          <div className={styles.bcBox} onClick={e => e.stopPropagation()}>
            <h3 className={styles.bcTitle}>📢 Broadcast to All Players</h3>
            <textarea
              className={styles.bcInput}
              placeholder="Your message here…"
              value={broadcast}
              onChange={e => setBc(e.target.value)}
              rows={3}
              autoFocus
            />
            <div className={styles.bcBtns}>
              <button className={styles.bcCancel} onClick={() => setBcOpen(false)}>Cancel</button>
              <button className={styles.bcSend} onClick={sendBroadcast} disabled={!broadcast.trim()}>
                Send
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
