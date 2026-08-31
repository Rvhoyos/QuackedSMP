import { useState, useEffect } from 'react'
import { Badge, Toggle } from '../../ui'
import { IconCamera, IconFlag, IconSkills, IconChatFilter, IconDiscord, IconVoiceChat, IconBallot, IconMapScroll, IconChest, IconEmerald, IconHardcoreHeart, IconGlowstonePortal, IconBed, IconEyeOfEnder, IconSlimeBall, IconBundle, IconLiveMonitor, IconChorusFruit, IconWrittenBook, IconChunkFill, IconOreVeiled, IconLockedSlots, IconSidebarPanel, IconShulkerBox } from './MinecraftIcons'
import styles from './FeatureShowcase.module.css'

const FEATURES = [
  { name: 'Land Claims', group: 'Gameplay', Icon: IconFlag, desc: 'Chunk-based land protection. Players claim plots, lock chests, and control access.', tag: 'claims', configKey: 'claims_enabled', toggleable: true },
  { name: 'Skills', group: 'Gameplay', Icon: IconSkills, desc: '12 RPG skills with a configurable XP curve and level-gated active abilities.', tag: 'skills', configKey: 'skills_enabled', toggleable: true },
  { name: 'Teleport', group: 'Gameplay', Icon: IconBed, desc: 'Homes, /spawn, and /tpa with configurable warmup timers. Movement cancels the warp.', tag: 'teleport', configKey: 'teleport_enabled', toggleable: true },
  { name: 'Custom Dimensions', group: 'Gameplay', Icon: IconGlowstonePortal, desc: 'Create and manage custom dimensions in-game, including the Ether sky-islands world. Switching off blocks new dimensions; existing ones keep working.', tag: 'dims', configKey: 'dims_enabled', toggleable: true },
  { name: 'Hardcore Mode', group: 'Gameplay', Icon: IconHardcoreHeart, desc: 'Player-run hardcore sessions with shared start, inventory stash, and death threshold.', tag: 'hardcore', configKey: 'hardcore_enabled', toggleable: true },
  { name: 'Random Teleport', group: 'Gameplay', Icon: IconChorusFruit, desc: 'On-demand /rtp with a per-dimension profile: distance band, spawn bias, arrival effects and a one-time item package.', tag: 'rtp', configKey: 'rtp_enabled', toggleable: true },
  { name: 'Kits', group: 'Gameplay', Icon: IconBundle, desc: 'Daily kit claims on a cooldown. Tier-gated VIP kits.', tag: 'kits', configKey: 'kits_enabled', toggleable: true },
  { name: 'Welcome Book', group: 'Gameplay', Icon: IconWrittenBook, desc: 'One in-game guide listing every player command, handed out by /guide, /smp help, kits and rtp arrivals. Edited in the panel.', tag: 'guide', configKey: 'welcome_book_enabled', toggleable: true },
  { name: 'Keep Inventory', group: 'Gameplay', Icon: IconLockedSlots, desc: 'Players keep their items on death unless they opt out with /smp keepinv off. Off leaves the server\u2019s own keep_inventory gamerule alone.', tag: 'keepinv', configKey: 'keep_inv_enabled', toggleable: true },
  { name: 'Welcome Sidebar', group: 'Gameplay', Icon: IconSidebarPanel, desc: 'A short scoreboard sidebar shown when a player joins normal survival. Title and lines are edited under Config > Chat.', tag: 'welcomesidebar', configKey: 'welcome_sidebar_enabled', toggleable: true },
  { name: 'Shops', group: 'Gameplay', Icon: IconEmerald, desc: 'Chest-based player shops with per-shop currency. Spawn shops have unlimited stock.', tag: 'shops', configKey: 'shops_enabled', toggleable: true },
  { name: 'Slime Chunk Hint', group: 'Gameplay', Icon: IconSlimeBall, desc: 'Slime particles at a player\u2019s feet while they stand in a slime chunk below Y40, plus a rare quiet squish.', tag: 'slimehint', configKey: 'slime_hint_enabled', toggleable: true },
  { name: 'End Finder', group: 'Gameplay', Icon: IconEyeOfEnder, desc: 'Holding an eye of ender marks the nearest stronghold on the vanilla Locator Bar, with the distance on the action bar. No client mod.', tag: 'endfinder', configKey: 'end_finder_enabled', toggleable: true },
  { name: 'Chat Filter', group: 'Moderation', Icon: IconChatFilter, desc: 'Keyword blocking with leet-speak detection and escalating auto-mutes.', tag: 'chatfilter', configKey: 'chatfilter_enabled', toggleable: true },
  { name: 'Anti-Xray', group: 'Moderation', Icon: IconOreVeiled, desc: 'Server-side ore obfuscation. Hidden ores are replaced with random blocks in chunk packets and revealed the moment a real block is broken. No client mod.', tag: 'antixray', configKey: 'antixray_enabled', toggleable: true },
  { name: 'Discord', group: 'Integrations', Icon: IconDiscord, desc: 'Webhook relay of join/leave and chat to a Discord channel.', tag: 'discord', configKey: 'discord_enabled', toggleable: true, disableOnly: true, disablePayload: { discord_webhook_url: '' } },
  { name: 'Simple Voice Chat', group: 'Integrations', Icon: IconVoiceChat, desc: 'Proximity voice chat. Requires the Simple Voice Chat mod on server and clients.', tag: 'voicechat', configKey: 'voicechat_enable', toggleable: true, restartRequired: true },
  { name: 'Votifier', group: 'Integrations', Icon: IconBallot, desc: 'NuVotifier v2 listener with randomized, offline-queued vote rewards.', tag: 'votifier', configKey: 'votifier_enabled', toggleable: true },
  { name: 'BlueMap', group: 'Integrations', Icon: IconMapScroll, desc: 'Live web map with claim regions and player homes as markers.', tag: 'bluemap', configKey: 'bluemap_enabled', toggleable: true, restartRequired: true },
  { name: 'Admin Panel', group: 'System', Icon: IconChest, desc: 'Manage players, run commands, and edit config from the browser. Password-protected.', tag: 'admin', configKey: 'admin_enabled' },
  { name: 'Chunk Pre-generation', group: 'System', Icon: IconChunkFill, desc: 'Generates the area around spawn on the next restart, so nobody walks into terrain the server has to invent on the spot. The server does not accept joins until it finishes.', tag: 'pregen', configKey: 'pregen_enabled', toggleable: true, restartRequired: true },
  { name: 'Timelapse', group: 'System', Icon: IconCamera, desc: 'Periodic top-down world snapshots rendered straight from region files, played back in the panel with pan and zoom.', tag: 'timelapse', configKey: 'timelapse_enabled', toggleable: true },
  { name: 'Backups', group: 'System', Icon: IconShulkerBox, desc: 'World snapshots zipped from a paused save, on demand or on a schedule, with optional public download of the newest one.', tag: 'backups', configKey: 'backup_enabled', toggleable: true },
  { name: 'Dashboard', group: 'System', Icon: IconLiveMonitor, desc: 'Public live metrics, activity feed, chat, and leaderboards.', tag: 'dashboard' },
]

const GROUP_ORDER = ['Gameplay', 'Moderation', 'Integrations', 'System']

function authHeaders(token) { return token ? { Authorization: `Bearer ${token}` } : {} }

export default function FeatureShowcase({ token, onExpired }) {
  const [cfg, setCfg] = useState(null)
  const [overrides, setOverrides] = useState({})
  const [toggling, setToggling] = useState({})

  useEffect(() => {
    // No token guard: when no admin password is set the routes serve unauthenticated calls, and
    // skipping the fetch here left every card falling back to "Active" with no toggle.
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
    // Config not loaded yet, show Active so cards don't flicker grey
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
                  const canToggle = !!f.toggleable && (active || !f.disableOnly)
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
