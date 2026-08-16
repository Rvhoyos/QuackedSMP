import { useState, useEffect, useCallback } from 'react'
import { Toolbar, IconButton, Btn, Badge, Loading, EmptyState, ErrorBanner, ConfirmDialog, useToast } from '../../ui'
import { IconEmerald } from './MinecraftIcons'
import styles from './ShopsPanel.module.css'

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
    setLoading(true)
    setError(null)
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
      const r = await fetch('/api/admin/config', {
        method: 'POST', headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ economy_enabled: next }),
      })
      if (r.ok) {
        setEconEnabled(next)
        if (next) load(); else { setEconomy(null); setTab('shops') }
        toast(next ? 'Economy enabled' : 'Economy disabled')
      }
    } catch {}
    setToggling(false)
  }

  async function deleteShop() {
    const shop = confirmDel
    try {
      const r = await fetch('/api/admin/shops/delete', {
        method: 'POST', headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ dim: shop.dim, x: shop.x, y: shop.y, z: shop.z }),
      })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setConfirmDel(null); toast('Shop deleted'); load()
    } catch { setError('Failed to delete shop') }
  }

  const dimName = d => d.split(':').pop()
  const itemName = id => id.split(':').pop().replace(/_/g, ' ')

  if (loading && shops.length === 0) return <Loading label="Loading shops…" />

  return (
    <div className={styles.wrap}>
      <Toolbar title="Shops" count={`${shops.length}`}>
        <div className={styles.tabs}>
          <button className={`${styles.tab} ${tab === 'shops' ? styles.tabOn : ''}`} onClick={() => setTab('shops')}>Shops</button>
          {econEnabled && <button className={`${styles.tab} ${tab === 'economy' ? styles.tabOn : ''}`} onClick={() => setTab('economy')}>Economy{economy ? ` (${economy.length})` : ''}</button>}
        </div>
        <Btn size="sm" variant={econEnabled ? 'ghost' : 'ok'} disabled={toggling} onClick={toggleEconomy}>
          {toggling ? '…' : econEnabled ? 'Disable Economy' : 'Enable Economy'}
        </Btn>
        <IconButton tip="Refresh" onClick={load}>↻</IconButton>
      </Toolbar>

      <ErrorBanner>{error}</ErrorBanner>

      {tab === 'shops' && (
        shops.length === 0
          ? <EmptyState icon={<IconEmerald size={30} />} label="No shops registered" hint="Player chest shops appear here once created in-game." />
          : (
            <div className={styles.list}>
              {shops.map(s => (
                <div key={`${s.dim}:${s.x},${s.y},${s.z}`} className={styles.row}>
                  <div className={styles.info}>
                    <span className={styles.name}>{itemName(s.item)}</span>
                    <span className={styles.loc}>{dimName(s.dim)} [{s.x}, {s.y}, {s.z}]</span>
                  </div>
                  <div className={styles.detail}>
                    <span className={styles.price}>{s.price} {itemName(s.currency || 'minecraft:emerald')}{s.unit > 1 && ` / ${s.unit}`}</span>
                    {s.spawnShop ? <Badge variant="ok">unlimited</Badge> : <span className={styles.stock}>{s.stock} stock</span>}
                    <span className={styles.owner}>{s.ownerName}</span>
                  </div>
                  <Btn size="sm" variant="danger" onClick={() => setConfirmDel(s)}>Delete</Btn>
                </div>
              ))}
            </div>
          )
      )}

      {tab === 'economy' && econEnabled && (
        (economy && economy.length > 0)
          ? (
            <div className={styles.list}>
              {economy.map(e => (
                <div key={e.uuid} className={styles.row}>
                  <div className={styles.info}>
                    <span className={styles.name}>{e.name}</span>
                    <span className={styles.loc}>{e.uuid.substring(0, 8)}…</span>
                  </div>
                  <span className={styles.balance}>{e.balance.toLocaleString()} emeralds</span>
                </div>
              ))}
            </div>
          )
          : <EmptyState icon={<IconEmerald size={30} />} label="No balances" />
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
