import { useState, useEffect } from 'react'
import AdminGate from './AdminGate'
import PlayersPanel from './PlayersPanel'
import CommandsPanel from './CommandsPanel'
import ConfigEditor from './ConfigEditor'
import FeatureShowcase from './FeatureShowcase'
import DimsPanel from './DimsPanel'
import SkillsPanel from './SkillsPanel'
import ClaimsPanel from './ClaimsPanel'
import ChatFilterPanel from './ChatFilterPanel'
import ModsPanel from './ModsPanel'
import HardcorePanel from './HardcorePanel'
import TeamsPanel from './TeamsPanel'
import ShopsPanel from './ShopsPanel'
import CommandBlocksPanel from './CommandBlocksPanel'
import BackupsPanel from './BackupsPanel'
import TimelapsePanel from './TimelapsePanel'
import { TooltipProvider, ToastHost } from '../../ui'
import { IconPlayerHead, IconCommandBlock, IconChest, IconBookshelf, IconPortal, IconSkills, IconFlag, IconChatFilter, IconMod, IconShield, IconSword, IconEmerald, IconRepeatCmdBlock, IconShulkerBox, IconMapScroll } from './MinecraftIcons'
import styles from './AdminPanel.module.css'

// Sidebar groups. Each item optionally gates on a config flag (hidden when the
// feature is disabled). Panels are looked up by id in PANELS below.
const GROUPS = [
  { label: 'People', items: [
    { id: 'players', label: 'Players', Icon: IconPlayerHead },
    { id: 'teams',   label: 'Teams',   Icon: IconSword },
  ]},
  { label: 'World', items: [
    { id: 'dims',   label: 'Dimensions', Icon: IconPortal },
    { id: 'claims', label: 'Claims',     Icon: IconFlag,    configKey: 'claims_enabled' },
    { id: 'shops',  label: 'Shops',      Icon: IconEmerald, configKey: 'shops_enabled' },
  ]},
  { label: 'Gameplay', items: [
    { id: 'skills',   label: 'Skills',   Icon: IconSkills, configKey: 'skills_enabled' },
    { id: 'hardcore', label: 'Hardcore', Icon: IconShield, configKey: 'hardcore_enabled' },
  ]},
  { label: 'Moderation', items: [
    { id: 'chatfilter', label: 'Chat Filter', Icon: IconChatFilter, configKey: 'chatfilter_enabled' },
    { id: 'cmdblocks',  label: 'Cmd Blocks',  Icon: IconRepeatCmdBlock, configKey: 'commandblocks_enabled' },
    { id: 'commands',   label: 'Commands',    Icon: IconCommandBlock },
  ]},
  { label: 'Systems', items: [
    { id: 'mods',      label: 'Mods',      Icon: IconMod },
    { id: 'backups',   label: 'Backups',   Icon: IconShulkerBox },
    { id: 'timelapse', label: 'Timelapse', Icon: IconMapScroll },
  ]},
  { label: 'Setup', items: [
    { id: 'config',   label: 'Config',   Icon: IconChest },
    { id: 'features', label: 'Features', Icon: IconBookshelf },
  ]},
]

const TOKEN_KEY = 'quack_admin_token'

export default function AdminPanel({ health }) {
  const [token,     setToken]   = useState(() => sessionStorage.getItem(TOKEN_KEY) || '')
  const [activeTab, setTab]     = useState('players')
  const [cfg,       setCfg]     = useState(null)
  const [navOpen,   setNavOpen] = useState(false)

  useEffect(() => {
    if (!token) return
    fetch('/api/admin/config', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => (r.status === 401 || r.status === 403) ? null : r.json())
      .then(d => { if (d && !d.error) setCfg(d) })
      .catch(() => {})
  }, [token])

  // Filter items by config flags; drop groups that end up empty.
  const visibleGroups = GROUPS
    .map(g => ({ ...g, items: g.items.filter(it => !it.configKey || cfg?.[it.configKey] !== false) }))
    .filter(g => g.items.length > 0)

  const flatItems   = visibleGroups.flatMap(g => g.items)
  const activeItem  = flatItems.find(it => it.id === activeTab)
  const resolvedTab = activeItem ? activeTab : (flatItems[0]?.id || 'players')
  const resolvedLbl = flatItems.find(it => it.id === resolvedTab)?.label || 'Admin'

  const needsAuth = health?.hasPassword && !token

  function onAuth(tok) { sessionStorage.setItem(TOKEN_KEY, tok); setToken(tok) }
  function logout() {
    if (token) {
      fetch('/api/admin/logout', { method: 'POST', headers: { Authorization: `Bearer ${token}` } }).catch(() => {})
    }
    sessionStorage.removeItem(TOKEN_KEY)
    setToken('')
  }
  function pick(id) { setTab(id); setNavOpen(false) }

  if (needsAuth) return <AdminGate onAuth={onAuth} />

  const panelProps = { token, onExpired: logout }

  return (
    <TooltipProvider>
      <ToastHost>
        <div className={styles.shell}>
          {/* Mobile top bar */}
          <div className={styles.mobileBar}>
            <button className={styles.hamburger} onClick={() => setNavOpen(o => !o)} aria-label="Menu">≡</button>
            <span className={styles.mobileTitle}>{resolvedLbl}</span>
          </div>

          {navOpen && <div className={styles.scrim} onClick={() => setNavOpen(false)} />}

          {/* Sidebar rail */}
          <nav className={`${styles.rail} ${navOpen ? styles.railOpen : ''}`}>
            <div className={styles.railScroll}>
              {visibleGroups.map(g => (
                <div key={g.label} className={styles.group}>
                  <div className={styles.groupLabel}>{g.label}</div>
                  {g.items.map(({ id, label, Icon }) => (
                    <button
                      key={id}
                      className={`${styles.item} ${resolvedTab === id ? styles.itemActive : ''}`}
                      onClick={() => pick(id)}
                    >
                      <span className={styles.itemIcon}><Icon size={16} /></span>
                      <span className={styles.itemLabel}>{label}</span>
                    </button>
                  ))}
                </div>
              ))}
            </div>
            {health?.hasPassword && (
              <button className={styles.signout} onClick={logout}>Sign Out</button>
            )}
          </nav>

          {/* Panel body */}
          <div className={styles.body}>
            {resolvedTab === 'players'    && <PlayersPanel {...panelProps} />}
            {resolvedTab === 'commands'   && <CommandsPanel {...panelProps} />}
            {resolvedTab === 'dims'       && <DimsPanel {...panelProps} />}
            {resolvedTab === 'skills'     && <SkillsPanel {...panelProps} />}
            {resolvedTab === 'claims'     && <ClaimsPanel {...panelProps} />}
            {resolvedTab === 'chatfilter' && <ChatFilterPanel {...panelProps} />}
            {resolvedTab === 'hardcore'   && <HardcorePanel {...panelProps} />}
            {resolvedTab === 'teams'      && <TeamsPanel {...panelProps} />}
            {resolvedTab === 'shops'      && <ShopsPanel {...panelProps} />}
            {resolvedTab === 'cmdblocks'  && <CommandBlocksPanel {...panelProps} />}
            {resolvedTab === 'mods'       && <ModsPanel {...panelProps} />}
            {resolvedTab === 'backups'    && <BackupsPanel {...panelProps} />}
            {resolvedTab === 'timelapse'  && <TimelapsePanel {...panelProps} />}
            {resolvedTab === 'config'     && <ConfigEditor {...panelProps} />}
            {resolvedTab === 'features'   && <FeatureShowcase {...panelProps} />}
          </div>
        </div>
      </ToastHost>
    </TooltipProvider>
  )
}
