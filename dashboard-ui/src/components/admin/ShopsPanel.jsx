import { useState, useEffect, useCallback } from 'react'
import styles from './ClaimsPanel.module.css'

export default function ShopsPanel({ token, onExpired }) {
  const [shops,     setShops]     = useState([])
  const [economy,   setEconomy]   = useState(null)
  const [econEnabled, setEconEnabled] = useState(false)
  const [loading,   setLoading]   = useState(false)
  const [error,     setError]     = useState(null)
  const [confirmDel, setConfirmDel] = useState(null)
  const [tab,       setTab]       = useState('shops')
  const [toggling,  setToggling]  = useState(false)

  const auth = { Authorization: `Bearer ${token}` }

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const cr = await fetch('/api/admin/config', { headers: auth })
      if (cr.ok) {
        const cd = await cr.json()
        if (cd && !cd.error) setEconEnabled(!!cd.economy_enabled)
      }
    } catch {}
    try {
      const r = await fetch('/api/admin/shops', { headers: auth })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      if (!r.ok) { setError(`Server error (${r.status})`); setLoading(false); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else setShops(d)
    } catch { setError('Failed to load shops') }
    try {
      const er = await fetch('/api/admin/economy', { headers: auth })
      if (er.ok) {
        const ed = await er.json()
        if (!ed.error) setEconomy(ed)
        else setEconomy(null)
      }
    } catch {}
    setLoading(false)
  }, [token])

  useEffect(() => { load() }, [load])

  async function toggleEconomy() {
    setToggling(true)
    try {
      const next = !econEnabled
      const r = await fetch('/api/admin/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ economy_enabled: next }),
      })
      if (r.ok) {
        setEconEnabled(next)
        if (next) load()
        else { setEconomy(null); setTab('shops') }
      }
    } catch {}
    setToggling(false)
  }

  async function deleteShop(shop) {
    try {
      const r = await fetch('/api/admin/shops/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ dim: shop.dim, x: shop.x, y: shop.y, z: shop.z }),
      })
      if (r.status === 401 || r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error); return }
      setConfirmDel(null)
      load()
    } catch { setError('Failed to delete shop') }
  }

  const shopKey = s => `${s.dim}:${s.x},${s.y},${s.z}`
  const dimName = d => d.split(':').pop()
  const itemName = id => id.split(':').pop().replace(/_/g, ' ')

  return (
    <div className={styles.wrap}>
      <div className={styles.toolbar}>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <button
            className={styles.refresh}
            style={{ color: tab === 'shops' ? '#CCC' : undefined }}
            onClick={() => setTab('shops')}
          >
            Shops ({shops.length})
          </button>
          {econEnabled && (
            <button
              className={styles.refresh}
              style={{ color: tab === 'economy' ? '#CCC' : undefined }}
              onClick={() => setTab('economy')}
            >
              Economy {economy ? `(${economy.length})` : ''}
            </button>
          )}
          <button
            className={styles.refresh}
            style={{ fontSize: '12px', opacity: 0.7 }}
            onClick={toggleEconomy}
            disabled={toggling}
          >
            {toggling ? '...' : econEnabled ? 'Disable Economy' : 'Enable Economy'}
          </button>
        </div>
        <button className={styles.refresh} onClick={load} disabled={loading}>
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>x</button>
        </div>
      )}

      {tab === 'shops' && (
        <div className={styles.list}>
          {shops.length === 0 && !loading && <div className={styles.empty}>No shops registered</div>}
          {shops.map(s => {
            const key = shopKey(s)
            return (
              <div key={key} className={styles.row}>
                <div className={styles.playerInfo}>
                  <span className={styles.playerName}>{itemName(s.item)}</span>
                  <span className={styles.playerUuid}>
                    {dimName(s.dim)} [{s.x}, {s.y}, {s.z}]
                  </span>
                </div>
                <div className={styles.barWrap}>
                  <span className={styles.claimCount} style={{ minWidth: 'auto' }}>
                    {s.price} {itemName(s.currency || 'minecraft:emerald')}
                    {s.unit > 1 && ` per ${s.unit}`}
                  </span>
                  <span className={styles.claimCount} style={{ minWidth: 'auto', color: s.spawnShop ? '#4CAF50' : '#888' }}>
                    {s.spawnShop ? 'unlimited' : `${s.stock} stock`}
                  </span>
                  <span className={styles.playerUuid}>{s.ownerName}</span>
                </div>
                <div className={styles.actions}>
                  {confirmDel === key ? (
                    <>
                      <span className={styles.confirmLabel}>Delete?</span>
                      <button className={styles.btnDanger} onClick={() => deleteShop(s)}>Confirm</button>
                      <button className={styles.btnGhost} onClick={() => setConfirmDel(null)}>Cancel</button>
                    </>
                  ) : (
                    <button className={styles.btnDanger} onClick={() => setConfirmDel(key)}>Delete</button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {tab === 'economy' && econEnabled && economy && (
        <div className={styles.list}>
          {economy.length === 0 && <div className={styles.empty}>No balances</div>}
          {economy.map(e => (
            <div key={e.uuid} className={styles.row}>
              <div className={styles.playerInfo}>
                <span className={styles.playerName}>{e.name}</span>
                <span className={styles.playerUuid}>{e.uuid.substring(0, 8)}...</span>
              </div>
              <div className={styles.barWrap}>
                <span className={styles.claimCount}>
                  {e.balance.toLocaleString()} emeralds
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
