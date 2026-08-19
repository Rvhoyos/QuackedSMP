import { useState, useMemo } from 'react'
import { useResource, apiFetch, Toolbar, IconButton, Btn, Badge, Loading, EmptyState, ErrorBanner, Dialog, DialogButtons, Input, StatCard, Menu, MenuItem, MenuSeparator, useToast } from '../../ui'
import { IconPlayerHead } from './MinecraftIcons'
import styles from './PlayersPanel.module.css'

async function exec(command, ctx)            { return apiFetch('/api/admin/exec', { ...ctx, json: { command } }) }
async function setOp(name, uuid, level, ctx)  { return apiFetch('/api/admin/setop', { ...ctx, json: { name, uuid, level } }) }
async function setTier(name, tier, ctx)      { return apiFetch('/api/admin/players/settier', { ...ctx, json: { name, tier } }) }

const formatPlaytime = t => t == null ? null : `${Math.floor(t / 72000)}h`

const OP_LEVELS = [
  { level: 1, label: 'L1 · Spawn bypass',  desc: 'Bypass spawn protection only. Minimal trust.' },
  { level: 2, label: 'L2 · Game commands', desc: '/give, /tp, /gamemode, /time, command blocks. Standard trusted member.' },
  { level: 3, label: 'L3 · Moderation',    desc: '/ban, /kick, /op, /whitelist. Moderator level.' },
  { level: 4, label: 'L4 · Full admin',    desc: '/stop, bypass player limit. Owner level.' },
]

function dimShort(dim) {
  const map = { 'minecraft:overworld': 'Overworld', 'minecraft:the_nether': 'Nether', 'minecraft:the_end': 'The End' }
  return map[dim] ?? dim.replace('minecraft:', '').replace(/_/g, ' ')
}

export default function PlayersPanel({ token, onExpired }) {
  const ctx = { token, onExpired }
  const { data: players, loading, error, refresh } = useResource('/api/admin/players', { token, onExpired, interval: 10_000 })
  const toast = useToast()

  const [confirm, setConfirm] = useState(null) // { name, uuid, action }
  const [reason,  setReason]  = useState('')
  const [opLevel, setOpLevel] = useState(4)
  const [filter,  setFilter]  = useState('')

  const list = players || []
  const shown = useMemo(
    () => filter ? list.filter(p => p.name.toLowerCase().includes(filter.toLowerCase())) : list,
    [list, filter],
  )
  const opCount  = list.filter(p => p.isOp).length
  const vipCount = list.filter(p => p.tier > 0).length

  function open(action, p) { setConfirm({ name: p.name, uuid: p.uuid, action }); setReason(''); setOpLevel(4) }

  async function doAction() {
    const { name, uuid, action } = confirm
    try {
      if (action === 'op') await setOp(name, uuid, opLevel, ctx)
      else await exec(action === 'kick' ? `kick ${name} ${reason || 'Kicked by admin'}`
                    : action === 'ban'  ? `ban ${name} ${reason || 'Banned by admin'}`
                    : `deop ${name}`, ctx)
      toast(`${action} → ${name}`)
    } catch (e) { if (!e.expired) toast('Action failed', 'danger') }
    setConfirm(null); setReason(''); setOpLevel(4)
    setTimeout(() => refresh({ silent: true }), 400)
  }

  async function quickMute(p) {
    try { await exec(`mute ${p.name} 60`, ctx); toast(`muted ${p.name}`) }
    catch (e) { if (!e.expired) toast('Mute failed', 'danger') }
  }
  async function bumpTier(p, delta) {
    try { await setTier(p.name, Math.max(0, (p.tier ?? 0) + delta), ctx) }
    catch (e) { if (!e.expired) toast('Tier change failed', 'danger') }
    setTimeout(() => refresh({ silent: true }), 300)
  }

  if (loading && !players) return <Loading label="Loading players…" />
  if (error && !players)   return <EmptyState icon={<IconPlayerHead size={30} />} label="Could not load players" hint={error} />

  return (
    <div className={styles.wrap}>
      <Toolbar title="Players" count={`${list.length} online`}>
        <input className={styles.filter} placeholder="Filter…" value={filter} onChange={e => setFilter(e.target.value)} />
        <IconButton tip="Refresh" onClick={() => refresh()}>↻</IconButton>
      </Toolbar>

      <div className={styles.stats}>
        <StatCard label="Online" value={list.length} tone="info" />
        <StatCard label="Operators" value={opCount} tone="warn" />
        <StatCard label="VIP" value={vipCount} tone="vip" />
      </div>

      <ErrorBanner>{error && players ? error : null}</ErrorBanner>

      {list.length === 0 ? (
        <EmptyState icon={<IconPlayerHead size={30} />} label="No players online" hint="Players appear here as they join." />
      ) : shown.length === 0 ? (
        <EmptyState label="No players match your filter" />
      ) : (
        <div className={styles.grid}>
          {shown.map(p => (
            <div key={p.uuid} className={styles.card}>
              <img className={styles.head} src={`https://mc-heads.net/avatar/${encodeURIComponent(p.name)}/40`} alt={p.name} width={40} height={40} onError={e => { e.target.style.visibility = 'hidden' }} />
              <div className={styles.info}>
                <span className={styles.name}>{p.name}</span>
                <span className={styles.dim}>{dimShort(p.dimension)}{formatPlaytime(p.playtime_ticks) ? ` · ${formatPlaytime(p.playtime_ticks)}` : ''}</span>
                <div className={styles.badges}>
                  {p.isOp && <Badge variant="warn">OP</Badge>}
                  {p.tier > 0 && <Badge variant="vip">T{p.tier}</Badge>}
                </div>
              </div>
              <div className={styles.cardActions}>
                <div className={styles.tier}>
                  <button className={styles.tierStep} disabled={(p.tier ?? 0) <= (p.earned_tier ?? 0)} onClick={() => bumpTier(p, -1)} aria-label="Lower tier">−</button>
                  <span className={`${styles.tierVal} ${p.tier > 0 ? styles.tierOn : ''}`}>{p.tier > 0 ? `T${p.tier}` : ', '}</span>
                  <button className={styles.tierStep} onClick={() => bumpTier(p, +1)} aria-label="Raise tier">+</button>
                </div>
                <Menu trigger={<Btn size="sm" aria-label="Actions">⋯</Btn>}>
                  <MenuItem tone="warn"   onSelect={() => open('kick', p)}>Kick</MenuItem>
                  <MenuItem              onSelect={() => quickMute(p)}>Mute 60m</MenuItem>
                  <MenuItem tone="danger" onSelect={() => open('ban', p)}>Ban</MenuItem>
                  <MenuSeparator />
                  {p.isOp
                    ? <MenuItem onSelect={() => open('deop', p)}>Deop</MenuItem>
                    : <MenuItem onSelect={() => open('op', p)}>Make Operator</MenuItem>}
                </Menu>
              </div>
            </div>
          ))}
        </div>
      )}

      <Dialog
        open={!!confirm}
        onOpenChange={o => !o && setConfirm(null)}
        title={confirm ? `${confirm.action[0].toUpperCase()}${confirm.action.slice(1)} ${confirm.name}?` : ''}
        description={confirm?.action === 'op' ? 'Choose an operator level.' : undefined}
      >
        {(confirm?.action === 'kick' || confirm?.action === 'ban') && (
          <Input placeholder="Reason (optional)" value={reason} onChange={e => setReason(e.target.value)} autoFocus />
        )}
        {confirm?.action === 'op' && (
          <div className={styles.opPicker}>
            {OP_LEVELS.map(({ level, label, desc }) => (
              <label key={level} className={`${styles.opOption} ${opLevel === level ? styles.opSelected : ''}`}>
                <input type="radio" name="opLevel" checked={opLevel === level} onChange={() => setOpLevel(level)} />
                <span className={styles.opLabel}>{label}</span>
                <span className={styles.opDesc}>{desc}</span>
              </label>
            ))}
          </div>
        )}
        <DialogButtons>
          <Btn variant="ghost" onClick={() => setConfirm(null)}>Cancel</Btn>
          <Btn variant={confirm?.action === 'ban' ? 'danger' : confirm?.action === 'op' ? 'ok' : 'warn'} onClick={doAction}>
            {confirm?.action === 'op' ? `Set OP L${opLevel}` : confirm ? `${confirm.action[0].toUpperCase()}${confirm.action.slice(1)}` : ''}
          </Btn>
        </DialogButtons>
      </Dialog>
    </div>
  )
}
