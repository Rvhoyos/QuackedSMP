import { useState, useEffect } from 'react'
import { Badge, Toggle } from '../../ui'
import { IconFlag, IconSkills, IconPortal, IconChatFilter, IconDiscord, IconVoiceChat, IconBallot, IconMapScroll, IconShield, IconChest, IconEmerald, IconSign } from './MinecraftIcons'
import styles from './FeatureShowcase.module.css'

const FEATURES = [
  { name: 'Land Claims', group: 'Gameplay', Icon: IconFlag, desc: 'Chunk-based land protection. Players claim plots, lock chests, and control access.', tag: 'claims', configKey: 'claims_enabled', toggleable: true },
  { name: 'Skills', group: 'Gameplay', Icon: IconSkills, desc: '12 RPG skills with a configurable XP curve and level-gated active abilities.', tag: 'skills', configKey: 'skills_enabled', toggleable: true },
  { name: 'Teleport', group: 'Gameplay', Icon: IconPortal, desc: 'Homes, /spawn, and /tpa with configurable warmup timers. Movement cancels the warp.', tag: 'teleport' },
  { name: 'Custom Dimensions', group: 'Gameplay', Icon: IconPortal, desc: 'Create and manage custom dimensions in-game, including the Ether sky-islands world.', tag: 'dims' },
  { name: 'Hardcore Mode', group: 'Gameplay', Icon: IconShield, desc: 'Player-run hardcore sessions with shared start, inventory stash, and death threshold.', tag: 'hardcore', configKey: 'hardcore_enabled', toggleable: true },
  { name: 'Kits', group: 'Gameplay', Icon: IconChest, desc: 'Daily kit claims on a cooldown. Tier-gated VIP kits.', tag: 'kits', configKey: 'kits_enabled', toggleable: true },
  { name: 'Shops', group: 'Gameplay', Icon: IconEmerald, desc: 'Chest-based player shops with per-shop currency. Spawn shops have unlimited stock.', tag: 'shops', configKey: 'shops_enabled', toggleable: true },
  { name: 'Chat Filter', group: 'Moderation', Icon: IconChatFilter, desc: 'Keyword blocking with leet-speak detection and escalating auto-mutes.', tag: 'chatfilter', configKey: 'chatfilter_enabled', toggleable: true },
  { name: 'Discord', group: 'Integrations', Icon: IconDiscord, desc: 'Webhook relay of join/leave and chat to a Discord channel.', tag: 'discord', configKey: 'discord_enabled', toggleable: true, disableOnly: true, disablePayload: { discord_webhook_url: '' } },
  { name: 'Simple Voice Chat', group: 'Integrations', Icon: IconVoiceChat, desc: 'Proximity voice chat. Requires the Simple Voice Chat mod on server and clients.', tag: 'voicechat', configKey: 'voicechat_enable', toggleable: true, restartRequired: true },
  { name: 'Votifier', group: 'Integrations', Icon: IconBallot, desc: 'NuVotifier v2 listener with randomized, offline-queued vote rewards.', tag: 'votifier', configKey: 'votifier_enabled', toggleable: true },
  { name: 'BlueMap', group: 'Integrations', Icon: IconMapScroll, desc: 'Live web map with claim regions and player homes as markers.', tag: 'bluemap', configKey: 'bluemap_enabled', toggleable: true, restartRequired: true },
  { name: 'Admin Panel', group: 'System', Icon: IconChest, desc: 'Manage players, run commands, and edit config from the browser. Password-protected.', tag: 'admin', configKey: 'admin_enabled' },
  { name: 'Dashboard', group: 'System', Icon: IconSign, desc: 'Public live metrics, activity feed, chat, and leaderboards.', tag: 'dashboard' },
]

const GROUP_ORDER = ['Gameplay', 'Moderation', 'Integrations', 'System']

function authHeaders(token) { return token ? { Authorization: `Bearer ${token}` } : {} }

export default function FeatureShowcase({ token, onExpired }) {
  const [cfg, setCfg] = useState(null)
  const [overrides, setOverrides] = useState({})
  const [toggling, setToggling] = useState({})

  useEffect(() => {
    if (!token) return
    fetch('/api/admin/config', { headers: authHeaders(token) })
      .then(r => (r.status === 401 || r.status === 403) ? (onExpired?.(), null) : r.json())
      .then(d => { if (d) setCfg(d) })
      .catch(() => {})
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  function resolve(feature) {
    const key = feature.configKey
    if (!key) return true
    if (overrides[key] !== undefined) return overrides[key]
    if (cfg && key in cfg) return cfg[key] !== false
    // Config not loaded yet — show Active so cards don't flicker grey
    return true
  }

  async function toggle(feature) {
    const key = feature.configKey
    const current = resolve(feature)
    const next = !current
    setOverrides(o => ({ ...o, [key]: next }))
    setToggling(t => ({ ...t, [key]: true }))
    const payload = (feature.disablePayload && !next) ? feature.disablePayload : { [key]: next }
    try {
      const res = await fetch('/api/admin/config', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify(payload) })
      if (res.status === 401 || res.status === 403) { onExpired?.(); return }
      if (!res.ok) setOverrides(o => ({ ...o, [key]: current }))
      else setCfg(c => c ? { ...c, [key]: next } : c)
    } catch {
      setOverrides(o => ({ ...o, [key]: current }))
    } finally {
      setToggling(t => ({ ...t, [key]: false }))
    }
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.scroll}>
        {GROUP_ORDER.map(group => {
          const items = FEATURES.filter(f => f.group === group)
          if (!items.length) return null
          return (
            <div key={group} className={styles.group}>
              <div className={styles.groupLabel}>{group}</div>
              <div className={styles.grid}>
                {items.map(f => {
                  const active = resolve(f)
                  const canToggle = !!token && !!f.toggleable && (active || !f.disableOnly)
                  return (
                    <div key={f.tag} className={`${styles.card} ${active ? styles.cardOn : styles.cardOff}`}>
                      <div className={styles.cardTop}>
                        <span className={styles.icon}><f.Icon size={22} /></span>
                        <span className={styles.name}>{f.name}</span>
                        <Badge variant={active ? 'ok' : undefined}>{active ? 'Active' : 'Off'}</Badge>
                      </div>
                      <p className={styles.desc}>{f.desc}</p>
                      <div className={styles.cardFoot}>
                        {f.restartRequired && <span className={styles.restart}>Restart required</span>}
                        {canToggle && <Toggle checked={active} onChange={() => toggle(f)} disabled={!!toggling[f.configKey]} aria-label={`Toggle ${f.name}`} />}
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
