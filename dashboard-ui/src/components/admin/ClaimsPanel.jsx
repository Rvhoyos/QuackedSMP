import { useState } from 'react'
import { useResource, apiFetch, Toolbar, IconButton, Btn, Meter, StatCard, Loading, EmptyState, ErrorBanner, ConfirmDialog, Menu, MenuItem, useToast } from '../../ui'
import { IconFlag } from './MinecraftIcons'
import styles from './ClaimsPanel.module.css'

const RANK_COLORS = ['#FFD700', '#C0C0C0', '#CD7F32']

export default function ClaimsPanel({ token, onExpired }) {
  const { data, loading, error, refresh } = useResource('/api/admin/claims', { token, onExpired })
  const [confirm, setConfirm] = useState(null)
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
      <Toolbar title="Claims" count={data ? `${data.total} total` : ''}>
        <IconButton tip="Refresh" onClick={() => refresh()}>↻</IconButton>
      </Toolbar>

      <div className={styles.stats}>
        <StatCard label="Total Claims" value={data?.total ?? '—'} tone="info" />
        <StatCard label="Claimers" value={players.length} />
      </div>

      <ErrorBanner>{error}</ErrorBanner>

      {players.length === 0 ? (
        <EmptyState icon={<IconFlag size={30} />} label="No claims found" hint="Land claims appear here as players claim chunks." />
      ) : (
        <div className={styles.list}>
          {players.map((p, i) => (
            <div key={p.uuid} className={styles.row}>
              <span className={styles.rank} style={i < 3 ? { color: RANK_COLORS[i] } : undefined}>{i + 1}</span>
              <div className={styles.info}>
                <span className={styles.name}>{p.name}</span>
                <span className={styles.uuid}>{p.uuid.substring(0, 8)}…</span>
              </div>
              <div className={styles.barWrap}>
                <Meter value={p.count} max={maxCount} color={i < 3 ? RANK_COLORS[i] : 'var(--mc-wood-light)'} />
              </div>
              <span className={styles.count}>{p.count}</span>
              <Menu trigger={<Btn size="sm" aria-label="Actions">⋯</Btn>}>
                <MenuItem tone="danger" onSelect={() => setConfirm({ uuid: p.uuid, name: p.name })}>Unclaim All</MenuItem>
              </Menu>
            </div>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!confirm}
        onOpenChange={o => !o && setConfirm(null)}
        title={confirm ? `Unclaim all of ${confirm.name}'s land?` : ''}
        description="Removes every chunk claim this player owns. Cannot be undone."
        confirmLabel="Unclaim All"
        tone="danger"
        onConfirm={unclaimAll}
      />
    </div>
  )
}
