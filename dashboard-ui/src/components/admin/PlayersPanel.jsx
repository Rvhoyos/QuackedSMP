import { useState, useEffect, useCallback } from 'react'
import styles from './PlayersPanel.module.css'

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function execCommand(command, token) {
  await fetch('/api/admin/exec', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ command }),
  })
}

async function sendSetOp(name, uuid, level, token) {
  return fetch('/api/admin/setop', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ name, uuid, level }),
  })
}

async function sendSetTier(name, tier, token) {
  return fetch('/api/admin/players/settier', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ name, tier }),
  })
}

function formatPlaytime(ticks) {
  if (ticks == null) return null
  const h = Math.floor(ticks / 72000)
  return `${h}h`
}

const OP_LEVELS = [
  { level: 1, label: 'L1: Spawn protection bypass',     desc: 'Bypass spawn protection only. Minimal trust.' },
  { level: 2, label: 'L2: Game commands',               desc: '/give, /tp, /gamemode, /time, /weather, command blocks. Standard trusted member.' },
  { level: 3, label: 'L3: Moderation',                  desc: '/ban, /kick, /op, /deop, /whitelist. Moderator level.' },
  { level: 4, label: 'L4: Full admin',                  desc: '/stop, bypass player limit. Owner level.' },
]

function dimShort(dim) {
  const map = {
    'minecraft:overworld':    'Overworld',
    'minecraft:the_nether':   'Nether',
    'minecraft:the_end':      'The End',
  }
  return map[dim] ?? dim.replace('minecraft:', '').replace(/_/g, ' ')
}

export default function PlayersPanel({ token, onExpired }) {
  const [players,  setPlayers]  = useState(null)
  const [error,    setError]    = useState('')
  const [confirm,  setConfirm]  = useState(null) // {name, action}
  const [reason,   setReason]   = useState('')
  const [opLevel,  setOpLevel]  = useState(4)
  const [tierInput, setTierInput] = useState(0)

  const fetchPlayers = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/players', { headers: authHeaders(token) })
      if (res.status === 403) { onExpired(); return }
      if (!res.ok) { setError('Admin panel disabled'); return }
      setPlayers(await res.json())
      setError('')
    } catch {
      setError('Could not load player list.')
    }
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    fetchPlayers()
    const id = setInterval(fetchPlayers, 10_000)
    return () => clearInterval(id)
  }, [fetchPlayers])

  async function doAction(name, uuid, action) {
    try {
      if (action === 'op') {
        const res = await sendSetOp(name, uuid, opLevel, token)
        if (res.status === 403) { onExpired(); return }
      } else if (action === 'settier') {
        const res = await sendSetTier(name, tierInput, token)
        if (res.status === 403) { onExpired(); return }
      } else {
        const cmd = action === 'kick'  ? `kick ${name} ${reason || 'Kicked by admin'}`
                  : action === 'ban'   ? `ban ${name} ${reason || 'Banned by admin'}`
                  : action === 'mute'  ? `mute ${name} 60`
                  : action === 'deop'  ? `deop ${name}`
                  : `teleport ${name} 0 64 0`
        await execCommand(cmd, token)
      }
    } catch { /* network error — still close dialog */ }
    setConfirm(null)
    setReason('')
    setOpLevel(4)
    setTierInput(0)
    setTimeout(fetchPlayers, 500)
  }

  if (error) return <div className={styles.empty}>{error}</div>
  if (!players) return <div className={styles.empty}>Loading…</div>
  if (players.length === 0) return (
    <div className={styles.empty}>
      <span className={styles.emptyIcon}>⬡</span>
      No players online
    </div>
  )

  return (
    <div className={styles.wrap}>
      <div className={styles.toolbar}>
        <span className={styles.count}>{players.length} online</span>
        <button className={styles.refresh} onClick={fetchPlayers}>↻ Refresh</button>
      </div>

      <div className={styles.list}>
        {players.map(p => (
          <div key={p.uuid} className={styles.row}>
            <img
              className={styles.head}
              src={`https://minotar.net/avatar/${p.name}/32`}
              alt={p.name}
              width={32} height={32}
              onError={e => { e.target.style.display = 'none' }}
            />
            <div className={styles.info}>
              <span className={styles.name}>{p.name}</span>
              <span className={styles.dim}>{dimShort(p.dimension)}</span>
              <span className={styles.meta}>
                {formatPlaytime(p.playtime_ticks) && <span>{formatPlaytime(p.playtime_ticks)}</span>}
                {p.tier > 0 && <span className={styles.tierBadge}>T{p.tier}</span>}
              </span>
            </div>
            {p.isOp && <span className={styles.opBadge}>OP</span>}
            <div className={styles.actions}>
              <ActionBtn label="Kick"  color="yellow" onClick={() => { setConfirm({ name: p.name, uuid: p.uuid, action: 'kick' }); setReason('') }} />
              <ActionBtn label="Mute"  color="peach"  onClick={() => doAction(p.name, p.uuid, 'mute')} />
              <ActionBtn label="Ban"   color="red"    onClick={() => { setConfirm({ name: p.name, uuid: p.uuid, action: 'ban'  }); setReason('') }} />
              <ActionBtn label="Op"    color="teal"   onClick={() => { setConfirm({ name: p.name, uuid: p.uuid, action: 'op' }); setOpLevel(4); setReason('') }} />
              <div className={styles.tierControl}>
                <button
                  className={`${styles.tierStep} ${styles.tierMinus}`}
                  disabled={(p.tier ?? 0) <= (p.earned_tier ?? 0)}
                  onClick={async () => {
                    await sendSetTier(p.name, Math.max(0, (p.tier ?? 0) - 1), token)
                    setTimeout(fetchPlayers, 300)
                  }}
                >−</button>
                <span className={`${styles.tierVal} ${p.tier > 0 ? styles.tierValActive : ''}`}>
                  {p.tier > 0 ? `T${p.tier}` : '—'}
                </span>
                <button
                  className={`${styles.tierStep} ${styles.tierPlus}`}
                  onClick={async () => {
                    await sendSetTier(p.name, (p.tier ?? 0) + 1, token)
                    setTimeout(fetchPlayers, 300)
                  }}
                >+</button>
              </div>
              {p.isOp && <ActionBtn label="Deop" color="mauve" onClick={() => { setConfirm({ name: p.name, uuid: p.uuid, action: 'deop' }); setReason('') }} />}
            </div>
          </div>
        ))}
      </div>

      {confirm && (
        <div className={styles.confirmOverlay} onClick={() => setConfirm(null)}>
          <div className={styles.confirmBox} onClick={e => e.stopPropagation()}>
            <p className={styles.confirmTitle}>
              {confirm.action === 'ban' ? 'Ban' : confirm.action === 'op' ? 'Op' : confirm.action === 'deop' ? 'Deop' : 'Kick'}{' '}
              <strong>{confirm.name}</strong>?
            </p>
            {(confirm.action === 'kick' || confirm.action === 'ban') && (
              <input
                className={styles.reasonInput}
                type="text"
                placeholder="Reason (optional)"
                value={reason}
                onChange={e => setReason(e.target.value)}
                autoFocus
              />
            )}
            {confirm.action === 'op' && (
              <div className={styles.opLevelPicker}>
                {OP_LEVELS.map(({ level, label, desc }) => (
                  <label key={level} className={`${styles.opLevelOption} ${opLevel === level ? styles.opLevelSelected : ''}`}>
                    <input
                      type="radio"
                      name="opLevel"
                      value={level}
                      checked={opLevel === level}
                      onChange={() => setOpLevel(level)}
                    />
                    <span className={styles.opLevelLabel}>{label}</span>
                    <span className={styles.opLevelDesc}>{desc}</span>
                  </label>
                ))}
              </div>
            )}
            <div className={styles.confirmBtns}>
              <button className={styles.confirmCancel} onClick={() => setConfirm(null)}>Cancel</button>
              <button
                className={`${styles.confirmDo} ${
                  confirm.action === 'ban'  ? styles.confirmBan  :
                  confirm.action === 'op'   ? styles.confirmOp   :
                  confirm.action === 'deop' ? styles.confirmDeop :
                  styles.confirmKick
                }`}
                onClick={() => doAction(confirm.name, confirm.uuid, confirm.action)}
              >
                {confirm.action === 'op' ? `Set OP L${opLevel}` : confirm.action.charAt(0).toUpperCase() + confirm.action.slice(1)}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function ActionBtn({ label, color, onClick }) {
  return (
    <button
      className={`${styles.actionBtn} ${styles[`action_${color}`]}`}
      onClick={onClick}
    >
      {label}
    </button>
  )
}
