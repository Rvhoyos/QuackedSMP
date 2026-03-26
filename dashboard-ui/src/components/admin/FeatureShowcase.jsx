import { useState, useEffect } from 'react'
import styles from './FeatureShowcase.module.css'

const FEATURES = [
  {
    name: 'Land Claims',
    desc: 'Chunk-based land protection. Players claim plots, lock chests, and control access.',
    tag: 'claims',
    configKey: 'claims_enabled',
    toggleable: true,
  },
  {
    name: 'Skills',
    desc: '12 RPG skills — Mining, Farming, Fishing, Combat and more. XP scales with a configurable exponent. Active abilities unlock at key levels.',
    tag: 'skills',
    configKey: 'skills_enabled',
    toggleable: true,
  },
  {
    name: 'Teleport',
    desc: 'Homes, /spawn, and /tpa with configurable warmup timers. Movement during warmup cancels the teleport.',
    tag: 'teleport',
  },
  {
    name: 'Chat Filter',
    desc: 'Keyword blocking with leet-speak detection. Auto-mutes escalate with repeat violations.',
    tag: 'chatfilter',
    configKey: 'chatfilter_enabled',
    toggleable: true,
  },
  {
    name: 'Custom Dimensions',
    desc: 'Create and manage custom world dimensions in-game. Includes the Ether — a sky-islands dimension.',
    tag: 'dims',
  },
  {
    name: 'Discord',
    desc: 'Webhook integration. Relays in-game join/leave events and chat messages to a Discord channel.',
    tag: 'discord',
    configKey: 'discord_enabled',
  },
  {
    name: 'Simple Voice Chat',
    desc: 'Proximity voice chat integration. Requires the Simple Voice Chat mod on server and clients.',
    tag: 'voicechat',
    configKey: 'voicechat_enable',
    toggleable: true,
    restartRequired: true,
  },
  {
    name: 'Votifier',
    desc: 'NuVotifier v2 listener. Randomized reward commands on vote. Offline vote queuing — rewards delivered on next login.',
    tag: 'votifier',
    configKey: 'votifier_enabled',
    toggleable: true,
  },
  {
    name: 'BlueMap',
    desc: 'Live web map integration. Claim regions and player homes shown as markers.',
    tag: 'bluemap',
    configKey: 'bluemap_enabled',
    toggleable: true,
    restartRequired: true,
  },
  {
    name: 'Admin Panel',
    desc: 'Manage players, run commands, and edit server config from the browser. Password-protected.',
    tag: 'admin',
    configKey: 'admin_enabled',
  },
  {
    name: 'Dashboard',
    desc: 'Public metrics dashboard — live player count, TPS, memory, activity feed, and live chat.',
    tag: 'dashboard',
  },
]

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export default function FeatureShowcase({ token, onExpired }) {
  const [cfg,       setCfg]       = useState(null)
  const [overrides, setOverrides] = useState({})
  const [toggling,  setToggling]  = useState({})

  useEffect(() => {
    if (!token) return
    fetch('/api/admin/config', { headers: authHeaders(token) })
      .then(r => {
        if (r.status === 401 || r.status === 403) { onExpired?.(); return null }
        return r.json()
      })
      .then(d => { if (d) setCfg(d) })
      .catch(() => {})
  }, [token, onExpired])

  function resolve(feature) {
    const key = feature.configKey
    if (!key) return true
    if (overrides[key] !== undefined) return overrides[key]
    if (cfg && key in cfg) return cfg[key] !== false
    // Config not loaded yet — show Active so cards don't flicker grey
    return true
  }

  async function toggle(feature) {
    const key     = feature.configKey
    const current = resolve(feature)
    const next    = !current

    setOverrides(o => ({ ...o, [key]: next }))
    setToggling(t => ({ ...t, [key]: true }))

    try {
      const res = await fetch('/api/admin/config', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body:    JSON.stringify({ [key]: next }),
      })
      if (res.status === 401 || res.status === 403) { onExpired?.(); return }
      if (!res.ok) {
        setOverrides(o => ({ ...o, [key]: current }))
      } else {
        setCfg(c => c ? { ...c, [key]: next } : c)
      }
    } catch {
      setOverrides(o => ({ ...o, [key]: current }))
    } finally {
      setToggling(t => ({ ...t, [key]: false }))
    }
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.grid}>
        {FEATURES.map(f => (
          <FeatureCard
            key={f.tag}
            feature={f}
            active={resolve(f)}
            canToggle={!!token && !!f.toggleable}
            toggling={!!toggling[f.configKey]}
            onToggle={() => toggle(f)}
          />
        ))}
      </div>
    </div>
  )
}

function FeatureCard({ feature, active, canToggle, toggling, onToggle }) {
  return (
    <div className={`${styles.card} ${active ? styles.cardActive : styles.cardOff}`}>
      <div className={styles.cardTop}>
        <div className={styles.name}>{feature.name}</div>
        <span className={`${styles.pill} ${active ? styles.pillOn : styles.pillOff}`}>
          {active ? 'Active' : 'Disabled'}
        </span>
      </div>
      <div className={styles.desc}>{feature.desc}</div>
      {feature.restartRequired && (
        <div className={styles.restartNote}>Restart required to apply changes</div>
      )}
      {canToggle && (
        <button
          className={`${styles.toggle} ${active ? styles.toggleOff : styles.toggleOn}`}
          onClick={onToggle}
          disabled={toggling}
        >
          {toggling ? '···' : active ? 'Disable' : 'Enable'}
        </button>
      )}
    </div>
  )
}
