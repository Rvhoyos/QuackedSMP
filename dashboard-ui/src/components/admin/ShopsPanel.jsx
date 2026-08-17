import { useState, useEffect, useCallback } from 'react'
import { Toolbar, IconButton, Btn, Badge, StatCard, Loading, EmptyState, ErrorBanner, ConfirmDialog, Menu, MenuItem, useToast } from '../../ui'
import { IconEmerald } from './MinecraftIcons'
import styles from './ShopsPanel.module.css'

const RANK_COLORS = ['#FFD700', '#C0C0C0', '#CD7F32']

export default function ShopsPanel({ token, onExpired }) {
  const [shops,      setShops]      = useState([])
  const [economy,    setEconomy]    = useState(null)
  const [econEnabled, setEconEnabled] = useState(false)
  const [loading,    setLoading]    = useState(false)
  const [error,      setError]      = useState(null)
  const [confirmDel, setConfirmDel] = useState(null)
  const [tab,        setTab]        = useState('shops')
  const [toggling,   setToggling]   = useState(false)
  const toast = useToast()

  const auth = { Authorization: `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const cr = await fetch('/api/admin/config', { headers: auth })
      if (cr.ok) { const cd = await cr.json(); if (cd && !cd.error) setEconEnabled(!!cd.economy_enabled) }
    } catch {}
    try {
      const r = await fetch('/api/admin/shops', { headers: auth })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      if (!r.ok) { setError(`Server error (${r.status})`); setLoading(false); return }
      const d = await r.json()
      if (d.error) setError(d.error); else setShops(d)
    } catch { setError('Failed to load shops') }
    try {
      const er = await fetch('/api/admin/economy', { headers: auth })
      if (er.ok) { const ed = await er.json(); setEconomy(!ed.error ? ed : null) }
    } catch {}
    setLoading(false)
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load() }, [load])

  async function toggleEconomy() {
    setToggling(true)
    try {
      const next = !econEnabled
      const r = await fetch('/api/admin/config', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ economy_enabled: next }) })
      if (r.ok) { setEconEnabled(next); if (next) load(); else { setEconomy(null); setTab('shops') } toast(next ? 'Economy enabled' : 'Economy disabled') }
    } catch {}
    setToggling(false)
  }

  async function deleteShop() {
    const shop = confirmDel
    try {
      const r = await fetch('/api/admin/shops/delete', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ dim: shop.dim, x: shop.x, y: shop.y, z: shop.z }) })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setConfirmDel(null); toast('Shop deleted'); load()
    } catch { setError('Failed to delete shop') }
  }

  const dimName = d => d.split(':').pop()
  const itemName = id => id.split(':').pop().replace(/_/g, ' ')
  const spawnCount = shops.filter(s => s.spawnShop).length
  const sortedEcon = economy ? [...economy].sort((a, b) => b.balance - a.balance) : []

  if (loading && shops.length === 0) return <Loading label="Loading shops…" />

  return (
    <div className={styles.wrap}>
      <Toolbar title="Shops" count={`${shops.length}`}>
        <div className={styles.tabs}>
          <button className={`${styles.tab} ${tab === 'shops' ? styles.tabOn : ''}`} onClick={() => setTab('shops')}>Shops</button>
          {econEnabled && <button className={`${styles.tab} ${tab === 'economy' ? styles.tabOn : ''}`} onClick={() => setTab('economy')}>Economy</button>}
        </div>
        <Btn size="sm" variant={econEnabled ? 'ghost' : 'ok'} disabled={toggling} onClick={toggleEconomy}>{toggling ? '…' : econEnabled ? 'Disable Economy' : 'Enable Economy'}</Btn>
        <IconButton tip="Refresh" onClick={load}>↻</IconButton>
      </Toolbar>

      {tab === 'shops' && (
        <div className={styles.stats}>
          <StatCard label="Shops" value={shops.length} tone="info" />
          <StatCard label="Spawn Shops" value={spawnCount} tone="ok" />
        </div>
      )}

      <ErrorBanner>{error}</ErrorBanner>

      {tab === 'shops' && (
        shops.length === 0
          ? <EmptyState icon={<IconEmerald size={30} />} label="No shops registered" hint="Player chest shops appear here once created in-game." />
          : (
            <div className={styles.grid}>
              {shops.map(s => (
                <div key={`${s.dim}:${s.x},${s.y},${s.z}`} className={styles.card}>
                  <div className={styles.cardTop}>
                    <span className={styles.item}>{itemName(s.item)}</span>
                    <Menu trigger={<Btn size="sm" aria-label="Actions">⋯</Btn>}>
                      <MenuItem tone="danger" onSelect={() => setConfirmDel(s)}>Delete Shop</MenuItem>
                    </Menu>
                  </div>
                  <span className={styles.price}>{s.price} {itemName(s.currency || 'minecraft:emerald')}{s.unit > 1 && ` / ${s.unit}`}</span>
                  <div className={styles.meta}>
                    {s.spawnShop ? <Badge variant="ok">unlimited</Badge> : <span className={styles.stock}>{s.stock} in stock</span>}
                    <span className={styles.owner}>{s.ownerName}</span>
                  </div>
                  <span className={styles.loc}>{dimName(s.dim)} [{s.x}, {s.y}, {s.z}]</span>
                </div>
              ))}
            </div>
          )
      )}

      {tab === 'economy' && econEnabled && (
        sortedEcon.length > 0
          ? (
            <div className={styles.list}>
              {sortedEcon.map((e, i) => (
                <div key={e.uuid} className={styles.econRow}>
                  <span className={styles.rank} style={i < 3 ? { color: RANK_COLORS[i] } : undefined}>{i + 1}</span>
                  <span className={styles.econName}>{e.name}</span>
                  <span className={styles.balance}>{e.balance.toLocaleString()} <IconEmerald size={12} /></span>
                </div>
              ))}
            </div>
          )
          : <EmptyState icon={<IconEmerald size={30} />} label="No balances yet" />
      )}

      <ConfirmDialog
        open={!!confirmDel}
        onOpenChange={o => !o && setConfirmDel(null)}
        title={confirmDel ? `Delete this ${itemName(confirmDel.item)} shop?` : ''}
        description={confirmDel ? `${dimName(confirmDel.dim)} [${confirmDel.x}, ${confirmDel.y}, ${confirmDel.z}]` : ''}
        confirmLabel="Delete"
        tone="danger"
        onConfirm={deleteShop}
      />
    </div>
  )
}
