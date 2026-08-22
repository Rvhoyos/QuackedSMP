import { useCallback, useEffect, useState } from 'react'
import { Dialog, ConfirmDialog, DialogButtons, Btn, Textarea, Toolbar, SectionCard, apiFetch, apiJson } from '../../ui'
import {
  IconSun, IconMoonStar, IconSunCloud, IconLightning, IconDragonEgg, IconRefreshDust,
  IconMegaphone, IconEndPortal, IconSapling, IconStopButton, IconHazardStripes,
} from './MinecraftIcons'
import LogTerminal from './LogTerminal'
import useLogTail from './useLogTail'
import styles from './CommandsPanel.module.css'

const GROUPS = [
  { label: 'World & Time', actions: [
    { label: 'Set Day',       Icon: IconSun,        cmd: 'time set day' },
    { label: 'Set Night',     Icon: IconMoonStar,   cmd: 'time set night' },
    { label: 'Clear Weather', Icon: IconSunCloud,   cmd: 'weather clear' },
    { label: 'Set Thunder',   Icon: IconLightning,  cmd: 'weather thunder' },
    { label: 'Reset Dragon',  Icon: IconDragonEgg,  cmd: 'smp end reset dragon' },
  ]},
  { label: 'Server', actions: [
    { label: 'Reload Config', Icon: IconRefreshDust, cmd: 'smp reload' },
  ]},
]

export default function CommandsPanel({ token, onExpired }) {
  const [broadcast, setBc]  = useState('')
  const [bcOpen,    setBcOpen]   = useState(false)
  const [stopOpen,  setStopOpen] = useState(false)
  const [endResetOpen, setEndResetOpen] = useState(false)
  const [regenOpen,    setRegenOpen]    = useState(false)
  const [regenPending, setRegenPending] = useState(false)

  const { lines, live, error, echo, clear } = useLogTail({ token, onExpired })

  // Commands are fire-and-forget: the server runs them on the main thread and
  // whatever they print arrives through the log tail like any other output.
  const exec = useCallback(async cmd => {
    const command = cmd.trim()
    if (!command) return false
    echo(`> ${command}`)
    try {
      await apiJson('/api/admin/exec', { token, onExpired, json: { command } })
      return true
    } catch (e) {
      if (!e.expired) echo(`command rejected: ${e.message}`, 'ERROR')
      return false
    }
  }, [token, onExpired, echo])

  const loadRegenStatus = useCallback(async () => {
    try {
      const d = await apiJson('/api/admin/regen', { token, onExpired })
      setRegenPending(d.pending === true)
    } catch { /* status is cosmetic, the button still works */ }
  }, [token, onExpired])
  useEffect(() => { loadRegenStatus() }, [loadRegenStatus])

  async function queueRegen() {
    try {
      await apiFetch('/api/admin/regen', { token, onExpired, method: 'POST' })
      setRegenPending(true)
      setRegenOpen(false)
    } catch { /* expired sessions are handled by apiFetch */ }
  }
  async function cancelRegen() {
    try {
      await apiFetch('/api/admin/regen', { token, onExpired, method: 'DELETE' })
      setRegenPending(false)
    } catch { /* as above */ }
  }

  async function sendBroadcast() {
    if (!broadcast.trim()) return
    await exec(`say ${broadcast.trim()}`)
    setBc('')
    setBcOpen(false)
  }

  return (
    <div className={styles.wrap}>
      <Toolbar title="Commands" count={`${lines.length} log lines`} />

      <div className={styles.body}>
        {GROUPS.map(g => (
          <SectionCard key={g.label} title={g.label}>
            <div className={styles.grid}>
              {g.actions.map(({ label, Icon, cmd }) => (
                <button key={cmd} className={styles.action} onClick={() => exec(cmd)} title={`/${cmd}`}>
                  <Icon size={18} />{label}
                </button>
              ))}
              {g.label === 'Server' && (
                <button className={styles.action} onClick={() => setBcOpen(true)} title="Broadcast a message">
                  <IconMegaphone size={18} />Broadcast
                </button>
              )}
            </div>
          </SectionCard>
        ))}

        <SectionCard
          className={styles.danger}
          title={<span className={styles.dangerTitle}><IconHazardStripes size={13} />Danger Zone</span>}
          subtitle="Stopping takes effect now; the two resets destroy world data on the next shutdown.">
          <div className={styles.grid}>
            <button className={styles.dangerAction} onClick={() => setEndResetOpen(true)}>
              <IconEndPortal size={18} />Queue End Reset
            </button>
            <button className={styles.dangerAction} onClick={() => regenPending ? cancelRegen() : setRegenOpen(true)}>
              <IconSapling size={18} />{regenPending ? 'Cancel Regen' : 'Regen Wilderness'}
            </button>
            <button className={`${styles.dangerAction} ${styles.stop}`} onClick={() => setStopOpen(true)}>
              <IconStopButton size={18} />Stop Server
            </button>
          </div>
        </SectionCard>

        <LogTerminal lines={lines} live={live} error={error} onSubmit={exec} onClear={clear} />
      </div>

      <ConfirmDialog open={stopOpen} onOpenChange={setStopOpen} title="Stop Server"
        description="This runs /stop. The server restarts automatically only if your host is configured for it (systemd, pterodactyl, etc.)."
        confirmLabel="Stop Server" tone="danger" onConfirm={() => { setStopOpen(false); exec('stop') }} />
      <ConfirmDialog open={endResetOpen} onOpenChange={setEndResetOpen} title="Queue End Reset"
        description="Queues the End for a full terrain reset. All builds, chests, and entities in the End are permanently destroyed on the next server shutdown."
        confirmLabel="Queue Reset" tone="warn" onConfirm={() => { setEndResetOpen(false); exec('smp end reset world') }} />
      <ConfirmDialog open={regenOpen} onOpenChange={setRegenOpen} title="Regenerate Wilderness"
        description="Regenerates ALL unclaimed chunks across every dimension (claimed chunks + a 3-chunk buffer are preserved). Unclaimed builds are permanently lost on the next shutdown. Irreversible."
        confirmLabel="Queue Regen" tone="danger" onConfirm={queueRegen} />
      <Dialog open={bcOpen} onOpenChange={setBcOpen} title="Broadcast to All Players">
        <Textarea placeholder="Your message here…" value={broadcast} onChange={e => setBc(e.target.value)} rows={3} autoFocus />
        <DialogButtons>
          <Btn variant="ghost" onClick={() => setBcOpen(false)}>Cancel</Btn>
          <Btn variant="primary" disabled={!broadcast.trim()} onClick={sendBroadcast}>Send</Btn>
        </DialogButtons>
      </Dialog>
    </div>
  )
}
