import { useState } from 'react'
import { useResource, apiFetch, Toolbar, IconButton, Btn, Meter, Loading, EmptyState, ErrorBanner, ConfirmDialog, useToast } from '../../ui'
import { IconShield } from './MinecraftIcons'
import styles from './HardcorePanel.module.css'

function dangerColor(frac) {
  return frac >= 0.8 ? 'var(--red)' : frac >= 0.5 ? 'var(--yellow)' : 'var(--green)'
}

export default function HardcorePanel({ token, onExpired }) {
  const { data, loading, error, refresh } = useResource('/api/admin/hardcore', { token, onExpired })
  const [confirm, setConfirm] = useState(null)
  const toast = useToast()

  async function endSession() {
    try {
      await apiFetch('/api/admin/hardcore/end', { token, onExpired, json: { name: confirm } })
      toast(`Ended ${confirm}`)
    } catch (e) { if (!e.expired) toast('Failed to end session', 'danger') }
    setConfirm(null)
    refresh({ silent: true })
  }

  if (loading && !data) return <Loading label="Loading sessions…" />

  const sessions = data?.sessions ?? []

  return (
    <div className={styles.wrap}>
      <Toolbar title="Hardcore" count={`${sessions.length} active`}>
        <IconButton tip="Refresh" onClick={() => refresh()}>↻</IconButton>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>

      {sessions.length === 0 ? (
        <EmptyState icon={<IconShield size={30} />} label="No active hardcore sessions" hint="Player-run hardcore sessions appear here while they're live." />
      ) : (
        <div className={styles.grid}>
          {sessions.map(s => {
            const frac = s.threshold > 0 ? s.deaths / s.threshold : 0
            return (
              <div key={s.name} className={styles.card}>
                <div className={styles.head}>
                  <span className={styles.name}>{s.name}</span>
                  <Btn size="sm" variant="danger" onClick={() => setConfirm(s.name)}>End</Btn>
                </div>
                <span className={styles.sub}>{s.start} · {s.dim}</span>

                <div className={styles.meterRow}>
                  <div className={styles.meterHead}>
                    <span className={styles.meterLabel}>Deaths</span>
                    <span className={styles.meterVal} style={{ color: dangerColor(frac) }}>{s.deaths} / {s.threshold}</span>
                  </div>
                  <Meter value={s.deaths} max={s.threshold} color={dangerColor(frac)} />
                </div>

                <div className={styles.stats}>
                  <span><b className={styles.alive}>{s.alive}</b> alive</span>
                  <span><b className={styles.dead}>{s.dead}</b> dead</span>
                  <span>peak <b>{s.peak}</b></span>
                </div>
              </div>
            )
          })}
        </div>
      )}

      <ConfirmDialog
        open={!!confirm}
        onOpenChange={o => !o && setConfirm(null)}
        title={confirm ? `End the "${confirm}" session?` : ''}
        description="This ends the hardcore session for all its members immediately."
        confirmLabel="End Session"
        tone="danger"
        onConfirm={endSession}
      />
    </div>
  )
}
