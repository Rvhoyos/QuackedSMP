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
import { IconPlayerHead, IconCommandBlock, IconChest, IconBookshelf, IconPortal, IconSkills, IconFlag, IconChatFilter, IconMod, IconShield, IconSword, IconEmerald, IconRepeatCmdBlock, IconShulkerBox } from './MinecraftIcons'
import styles from './AdminPanel.module.css'

const TABS = [
  { id: 'players',    label: 'Players',     Icon: IconPlayerHead },
  { id: 'commands',   label: 'Commands',    Icon: IconCommandBlock },
  { id: 'dims',       label: 'Dimensions',  Icon: IconPortal },
  { id: 'skills',     label: 'Skills',      Icon: IconSkills,      configKey: 'skills_enabled' },
  { id: 'claims',     label: 'Claims',      Icon: IconFlag,        configKey: 'claims_enabled' },
  { id: 'chatfilter', label: 'Chat Filter', Icon: IconChatFilter,  configKey: 'chatfilter_enabled' },
  { id: 'hardcore',   label: 'Hardcore',    Icon: IconShield,      configKey: 'hardcore_enabled' },
  { id: 'teams',      label: 'Teams',       Icon: IconSword },
  { id: 'shops',      label: 'Shops',       Icon: IconEmerald,     configKey: 'shops_enabled' },
  { id: 'cmdblocks',  label: 'Cmd Blocks',  Icon: IconRepeatCmdBlock, configKey: 'commandblocks_enabled' },
  { id: 'mods',       label: 'Mods',        Icon: IconMod },
  { id: 'backups',    label: 'Backups',     Icon: IconShulkerBox },
  { id: 'config',     label: 'Config',      Icon: IconChest },
  { id: 'features',   label: 'Features',    Icon: IconBookshelf },
]

const TOKEN_KEY = 'quack_admin_token'

export default function AdminPanel({ health }) {
  const [token,      setToken]  = useState(() => sessionStorage.getItem(TOKEN_KEY) || '')
  const [activeTab,  setTab]    = useState('players')
  const [cfg,        setCfg]    = useState(null)

  useEffect(() => {
    if (!token) return
    fetch('/api/admin/config', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => {
        if (r.status === 401 || r.status === 403) return null
        return r.json()
      })
      .then(d => { if (d && !d.error) setCfg(d) })
      .catch(() => {})
  }, [token])

  const visibleTabs = TABS.filter(t => !t.configKey || cfg?.[t.configKey] !== false)

  // If active tab got hidden, fall back to first visible
  const activeVisible = visibleTabs.some(t => t.id === activeTab)
  const resolvedTab = activeVisible ? activeTab : visibleTabs[0]?.id || 'players'

  // If a password is set and we have no token, require login.
  // If no password is set, the server bypasses auth — go straight to the panel.
  const needsAuth = health?.hasPassword && !token

  function onAuth(tok) {
    sessionStorage.setItem(TOKEN_KEY, tok)
    setToken(tok)
  }

  function logout() {
    if (token) {
      fetch('/api/admin/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => {})
    }
    sessionStorage.removeItem(TOKEN_KEY)
    setToken('')
  }

  if (needsAuth) {
    return <AdminGate onAuth={onAuth} />
  }

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.tabs}>
          {visibleTabs.map(({ id, label, Icon }) => (
            <button
              key={id}
              className={`${styles.tab} ${resolvedTab === id ? styles.tabActive : ''}`}
              onClick={() => setTab(id)}
            >
              <span className={styles.tabIcon}><Icon size={16} /></span>
              {label}
            </button>
          ))}
        </div>
        {health?.hasPassword && (
          <button className={styles.logout} onClick={logout} title="Sign out">
            Sign Out
          </button>
        )}
      </div>

      <div className={styles.body}>
        {resolvedTab === 'players'    && <PlayersPanel token={token} onExpired={logout} />}
        {resolvedTab === 'commands'   && <CommandsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'dims'       && <DimsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'skills'     && <SkillsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'claims'     && <ClaimsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'chatfilter' && <ChatFilterPanel token={token} onExpired={logout} />}
        {resolvedTab === 'hardcore'   && <HardcorePanel token={token} onExpired={logout} />}
        {resolvedTab === 'teams'      && <TeamsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'shops'      && <ShopsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'cmdblocks'  && <CommandBlocksPanel token={token} onExpired={logout} />}
        {resolvedTab === 'mods'       && <ModsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'backups'    && <BackupsPanel token={token} onExpired={logout} />}
        {resolvedTab === 'config'     && <ConfigEditor token={token} onExpired={logout} />}
        {resolvedTab === 'features'   && <FeatureShowcase token={token} onExpired={logout} />}
      </div>
    </div>
  )
}
