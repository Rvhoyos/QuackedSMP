import { useState } from 'react'
import { Badge, Toggle } from '../../ui'
import { IconCamera, IconFlag, IconSkills, IconChatFilter, IconDiscord, IconVoiceChat, IconBallot, IconMapScroll, IconChest, IconEmerald, IconHardcoreHeart, IconGlowstonePortal, IconBed, IconEyeOfEnder, IconSlimeBall, IconBundle, IconLiveMonitor, IconChorusFruit, IconWrittenBook, IconChunkFill, IconOreVeiled, IconLockedSlots, IconSidebarPanel, IconShulkerBox } from './MinecraftIcons'
import styles from './FeatureShowcase.module.css'

// `tab` is where a click on the card lands. Features that own an admin tab name it directly, the
// rest point at their section of the config editor. A card with no tab has nowhere to go: its
// switch is the whole of it.
const FEATURES = [
  { name: 'Land Claims', group: 'Gameplay', Icon: IconFlag, desc: 'Players claim chunks so nobody else can build, break or steal there.', tag: 'claims', configKey: 'claims_enabled', toggleable: true, tab: 'claims' },
  { name: 'Skills', group: 'Gameplay', Icon: IconSkills, desc: '12 skills that level up as players play, granting buffs and unlocking abilities.', tag: 'skills', configKey: 'skills_enabled', toggleable: true, tab: 'skills' },
  { name: 'Teleport', group: 'Gameplay', Icon: IconBed, desc: '/home, /spawn and player-to-player teleport requests, with a warmup.', tag: 'teleport', configKey: 'teleport_enabled', toggleable: true, tab: 'config', section: 'gen-teleport' },
  { name: 'Custom Dimensions', group: 'Gameplay', Icon: IconGlowstonePortal, desc: 'Create new dimensions in game, including the Ether sky islands.', tag: 'dims', configKey: 'dims_enabled', toggleable: true, tab: 'dims' },
  { name: 'Hardcore Mode', group: 'Gameplay', Icon: IconHardcoreHeart, desc: 'One-life runs in the live world. Die and you spectate until the run ends.', tag: 'hardcore', configKey: 'hardcore_enabled', toggleable: true, tab: 'hardcore' },
  { name: 'Random Teleport', group: 'Gameplay', Icon: IconChorusFruit, desc: '/rtp drops a player at a random spot away from spawn.', tag: 'rtp', configKey: 'rtp_enabled', toggleable: true, tab: 'rtp' },
  { name: 'Kits', group: 'Gameplay', Icon: IconBundle, desc: 'Free item kits players claim on a cooldown.', tag: 'kits', configKey: 'kits_enabled', toggleable: true, tab: 'kits' },
  { name: 'Welcome Book', group: 'Gameplay', Icon: IconWrittenBook, desc: 'A guide book for new players. Written in the panel, handed out by /guide.', tag: 'guide', configKey: 'welcome_book_enabled', toggleable: true, tab: 'guide' },
  { name: 'Keep Inventory', group: 'Gameplay', Icon: IconLockedSlots, desc: 'Players keep their items when they die, unless they opt out.', tag: 'keepinv', configKey: 'keep_inv_enabled', toggleable: true },
  { name: 'Welcome Sidebar', group: 'Gameplay', Icon: IconSidebarPanel, desc: 'A short welcome scoreboard shown when a player joins.', tag: 'welcomesidebar', configKey: 'welcome_sidebar_enabled', toggleable: true, tab: 'config', section: 'chat-sidebar' },
  { name: 'Shops', group: 'Gameplay', Icon: IconEmerald, desc: 'Players sell items out of a chest, at prices they set.', tag: 'shops', configKey: 'shops_enabled', toggleable: true, tab: 'shops' },
  { name: 'Slime Chunk Hint', group: 'Gameplay', Icon: IconSlimeBall, desc: 'Slime particles show a player they are standing in a slime chunk.', tag: 'slimehint', configKey: 'slime_hint_enabled', toggleable: true, tab: 'config', section: 'gen-worldhints' },
  { name: 'End Finder', group: 'Gameplay', Icon: IconEyeOfEnder, desc: 'Holding an eye of ender points to the nearest stronghold on the Locator Bar.', tag: 'endfinder', configKey: 'end_finder_enabled', toggleable: true, tab: 'config', section: 'gen-worldhints' },
  { name: 'Chat Filter', group: 'Moderation', Icon: IconChatFilter, desc: 'Censors banned words in chat and mutes repeat offenders.', tag: 'chatfilter', configKey: 'chatfilter_enabled', toggleable: true, tab: 'chatfilter' },
  { name: 'Anti-Xray', group: 'Moderation', Icon: IconOreVeiled, desc: 'Hides underground ore from x-ray cheats. No client mod needed.', tag: 'antixray', configKey: 'antixray_enabled', toggleable: true },
  { name: 'Discord', group: 'Integrations', Icon: IconDiscord, desc: 'Sends joins, leaves and chat to a Discord channel. Add a webhook URL in Config to turn on.', tag: 'discord', configKey: 'discord_enabled', toggleable: true, disableOnly: true, disablePayload: { discord_webhook_url: '' }, tab: 'config', section: 'int-discord' },
  { name: 'Simple Voice Chat', group: 'Integrations', Icon: IconVoiceChat, desc: 'Locks voice chat to players who confirmed 18+ with /verify. Needs the Simple Voice Chat mod.', tag: 'voicechat', configKey: 'voicechat_enable', toggleable: true, restartRequired: true, tab: 'config', section: 'int-voice' },
  { name: 'Votifier', group: 'Integrations', Icon: IconBallot, desc: 'Rewards players for voting on server lists.', tag: 'votifier', configKey: 'votifier_enabled', toggleable: true, tab: 'config', section: 'int-votifier' },
  { name: 'BlueMap', group: 'Integrations', Icon: IconMapScroll, desc: 'Puts claims, homes and shops on the BlueMap web map.', tag: 'bluemap', configKey: 'bluemap_enabled', toggleable: true, restartRequired: true, tab: 'config', section: 'int-bluemap' },
  { name: 'Admin Panel', group: 'System', Icon: IconChest, desc: 'This panel. Run the server from a browser, password protected.', tag: 'admin', configKey: 'admin_enabled', tab: 'config', section: 'gen-admin' },
  { name: 'Chunk Pre-generation', group: 'System', Icon: IconChunkFill, desc: 'Generates the world around spawn at startup so it does not lag later. Players cannot join until it finishes.', tag: 'pregen', configKey: 'pregen_enabled', toggleable: true, restartRequired: true, tab: 'pregen' },
  { name: 'Timelapse', group: 'System', Icon: IconCamera, desc: 'Saves a map image of the world on a timer, replayed as a timelapse.', tag: 'timelapse', configKey: 'timelapse_enabled', toggleable: true, tab: 'timelapse' },
  { name: 'Backups', group: 'System', Icon: IconShulkerBox, desc: 'Zips the whole world, on demand or on a schedule.', tag: 'backups', configKey: 'backup_enabled', toggleable: true, tab: 'backups' },
  { name: 'Dashboard', group: 'System', Icon: IconLiveMonitor, desc: 'The public page: server stats, live chat feed and leaderboards.', tag: 'dashboard' },
]

const GROUP_ORDER = ['Gameplay', 'Moderation', 'Integrations', 'System']

function authHeaders(token) { return token ? { Authorization: `Bearer ${token}` } : {} }

export default function FeatureShowcase({ token, onExpired, onNavigate, features, onFeatureChange }) {
  const [toggling, setToggling] = useState({})
  const [expanded, setExpanded] = useState({})

  // Config not loaded yet reads as Active, so cards don't flicker grey on the way in.
  function isOn(feature) {
    return feature.configKey ? features?.[feature.configKey] !== false : true
  }

  async function toggle(feature) {
    const key = feature.configKey
    const current = isOn(feature)
    const next = !current
    onFeatureChange(key, next)
    setToggling(t => ({ ...t, [key]: true }))
    const payload = (feature.disablePayload && !next) ? feature.disablePayload : { [key]: next }
    try {
      const res = await fetch('/api/admin/config', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders(token) }, body: JSON.stringify(payload) })
      if (res.status === 401 || res.status === 403) { onExpired?.(); return }
      if (!res.ok) onFeatureChange(key, current)
    } catch {
      onFeatureChange(key, current)
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
                  const active = isOn(f)
                  const open = !!expanded[f.tag]
                  // The sidebar hides a feature's tab while it is off, so the name only leads
                  // somewhere once the feature is on.
                  const canOpen = active && !!f.tab
                  const canToggle = !!f.toggleable && (active || !f.disableOnly)
                  const title = (
                    <>
                      <span className={styles.icon}><f.Icon size={22} /></span>
                      <span className={styles.name}>{f.name}</span>
                    </>
                  )
                  return (
                    <div key={f.tag} className={`${styles.card} ${active ? '' : styles.cardOff}`}>
                      {/* Covers the card, so anywhere the name and the switch do not sit expands it. */}
                      <button
                        type="button"
                        className={styles.expand}
                        aria-expanded={open}
                        aria-label={`${open ? 'Collapse' : 'Expand'} ${f.name}`}
                        onClick={() => setExpanded(e => ({ ...e, [f.tag]: !open }))}
                      />
                      <div className={styles.head}>
                        {canOpen ? (
                          <button
                            type="button"
                            className={`${styles.title} ${styles.titleLink}`}
                            aria-label={`Open ${f.name}`}
                            onClick={() => onNavigate(f.tab, f.section)}
                          >{title}</button>
                        ) : (
                          <span className={styles.title}>{title}</span>
                        )}
                        <Badge variant={active ? 'ok' : undefined}>{active ? 'Active' : 'Off'}</Badge>
                        {canToggle && (
                          <span className={styles.switch}>
                            <Toggle checked={active} onChange={() => toggle(f)} disabled={!!toggling[f.configKey]} aria-label={`Toggle ${f.name}`} />
                          </span>
                        )}
                      </div>
                      {open && (
                        <div className={styles.body}>
                          <p className={styles.desc}>{f.desc}</p>
                          {f.restartRequired && <span className={styles.restart}>Restart required</span>}
                        </div>
                      )}
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
