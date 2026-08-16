import { useState } from 'react'
import { useResource, apiFetch, Toolbar, IconButton, Btn, Loading, EmptyState, ErrorBanner, ConfirmDialog, useToast } from '../../ui'
import { IconFlag } from './MinecraftIcons'
import styles from './ClaimsPanel.module.css'

export default function ClaimsPanel({ token, onExpired }) {
  const { data, loading, error, refresh } = useResource('/api/admin/claims', { token, onExpired })
  const [confirm, setConfirm] = useState(null) // { uuid, name }
  const toast = useToast()

  async function unclaimAll() {
    try {
      await apiFetch('/api/admin/claims/unclaim', { token, onExpired, json: { uuid: confirm.uuid } })
      toast(`Unclaimed ${confirm.name}`)
    } catch (e) { if (!e.expired) toast('Failed to unclaim', 'danger') }
    setConfirm(null)
    refresh({ silent: true })
  }

  if (loading && !data) return <Loading label="Loading claims…" />

  const players = data?.players || []
  const maxCount = players[0]?.count ?? 1

  return (
    <div className={styles.wrap}>
      <Toolbar title="Claims" count={data ? `${data.total} across ${players.length}` : ''}>
        <IconButton tip="Refresh" onClick={() => refresh()}>↻</IconButton>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>

      {players.length === 0 ? (
        <EmptyState icon={<IconFlag size={30} />} label="No claims found" hint="Land claims will appear here as players claim chunks." />
      ) : (
        <div className={styles.list}>
          {players.map(p => (
            <div key={p.uuid} className={styles.row}>
              <div className={styles.info}>
                <span className={styles.name}>{p.name}</span>
                <span className={styles.uuid}>{p.uuid.substring(0, 8)}…</span>
              </div>
              <div className={styles.barWrap}>
                <div className={styles.bar}><div className={styles.barFill} style={{ width: `${Math.round((p.count / maxCount) * 100)}%` }} /></div>
                <span className={styles.claimCount}>{p.count} claims</span>
              </div>
              <Btn size="sm" variant="danger" onClick={() => setConfirm({ uuid: p.uuid, name: p.name })}>Unclaim All</Btn>
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!confirm}
        onOpenChange={o => !o && setConfirm(null)}
        title={confirm ? `Unclaim all of ${confirm.name}'s land?` : ''}
        description="This removes every chunk claim this player owns. It cannot be undone."
        confirmLabel="Unclaim All"
        tone="danger"
        onConfirm={unclaimAll}
      />
    </div>
  )
}
