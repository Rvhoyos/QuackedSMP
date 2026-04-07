import { useState, useEffect, useCallback } from 'react'
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
  const [endResetOpen, setEndResetOpen] = useState(false)
  const [regenOpen,    setRegenOpen]    = useState(false)
  const [regenPending, setRegenPending] = useState(false)
  const [loading,   setLoading] = useState(false)

  const loadRegenStatus = useCallback(async () => {
    try {
      const r = await fetch('/api/admin/regen', { headers: authHeaders(token) })
      if (r.ok) {
        const d = await r.json()
        setRegenPending(d.pending === true)
      }
    } catch {}
  }, [token])

  useEffect(() => { loadRegenStatus() }, [loadRegenStatus])

  async function queueRegen() {
    try {
      const r = await fetch('/api/admin/regen', {
        method: 'POST',
        headers: authHeaders(token),
      })
      if (r.status === 403) { onExpired(); return }
      setRegenPending(true)
      setRegenOpen(false)
    } catch {}
  }

  async function cancelRegen() {
    try {
      const r = await fetch('/api/admin/regen', {
        method: 'DELETE',
        headers: authHeaders(token),
      })
      if (r.status === 403) { onExpired(); return }
      setRegenPending(false)
    } catch {}
  }

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
              className={`${styles.quickBtn} ${styles.quickWarn}`}
              onClick={() => setEndResetOpen(true)}
            >
              <span className={styles.quickIcon}>🌍</span>
              Queue End Reset
            </button>
            <button
              className={`${styles.quickBtn} ${styles.quickWarn}`}
              onClick={() => regenPending ? cancelRegen() : setRegenOpen(true)}
            >
              <span className={styles.quickIcon}>🌿</span>
              {regenPending ? 'Cancel Regen' : 'Regen Wilderness'}
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

      {/* End Reset confirmation */}
      {endResetOpen && (
        <div className={styles.bcOverlay} onClick={() => setEndResetOpen(false)}>
          <div className={styles.bcBox} onClick={e => e.stopPropagation()}>
            <h3 className={`${styles.bcTitle} ${styles.warnTitle}`}>🌍 Queue End Reset</h3>
            <p className={styles.warnText}>
              This will queue the End dimension for a full terrain reset.
              All builds, chests, and entities in the End will be permanently destroyed.
            </p>
            <p className={styles.warnNote}>
              The reset will execute on the next server shutdown.
            </p>
            <div className={styles.bcBtns}>
              <button className={styles.bcCancel} onClick={() => setEndResetOpen(false)}>Cancel</button>
              <button
                className={styles.stopConfirm}
                onClick={() => { setEndResetOpen(false); exec('smp end reset world') }}
              >
                Queue Reset
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Wilderness Regen confirmation */}
      {regenOpen && (
        <div className={styles.bcOverlay} onClick={() => setRegenOpen(false)}>
          <div className={styles.bcBox} onClick={e => e.stopPropagation()}>
            <h3 className={`${styles.bcTitle} ${styles.warnTitle}`}>🌿 Regenerate Wilderness</h3>
            <p className={styles.warnText}>
              This will regenerate ALL unclaimed chunks across all dimensions.
              Claimed chunks and a 3-chunk buffer around them will be preserved.
            </p>
            <p className={styles.warnDanger}>
              Unclaimed builds, chests, and entities will be permanently lost. This is irreversible.
            </p>
            <p className={styles.warnNote}>
              The regen will execute on the next server shutdown.
            </p>
            <div className={styles.bcBtns}>
              <button className={styles.bcCancel} onClick={() => setRegenOpen(false)}>Cancel</button>
              <button className={styles.stopConfirm} onClick={queueRegen}>
                Queue Regen
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
