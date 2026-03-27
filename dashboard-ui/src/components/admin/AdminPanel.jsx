import { useState } from 'react'
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
import { IconPlayerHead, IconCommandBlock, IconChest, IconBookshelf, IconPortal, IconSkills, IconFlag, IconChatFilter, IconMod } from './MinecraftIcons'
import styles from './AdminPanel.module.css'

const TABS = [
  { id: 'players',    label: 'Players',     Icon: IconPlayerHead },
  { id: 'commands',   label: 'Commands',    Icon: IconCommandBlock },
  { id: 'dims',       label: 'Dimensions',  Icon: IconPortal },
  { id: 'skills',     label: 'Skills',      Icon: IconSkills },
  { id: 'claims',     label: 'Claims',      Icon: IconFlag },
  { id: 'chatfilter', label: 'Chat Filter', Icon: IconChatFilter },
  { id: 'mods',       label: 'Mods',        Icon: IconMod },
  { id: 'config',     label: 'Config',      Icon: IconChest },
  { id: 'features',   label: 'Showcase',    Icon: IconBookshelf },
]

const TOKEN_KEY = 'quack_admin_token'

export default function AdminPanel({ health }) {
  const [token,      setToken]  = useState(() => sessionStorage.getItem(TOKEN_KEY) || '')
  const [activeTab,  setTab]    = useState('players')

  // If a password is set and we have no token, require login.
  // If no password is set, the server bypasses auth — go straight to the panel.
  const needsAuth = health?.hasPassword && !token

  function onAuth(tok) {
    sessionStorage.setItem(TOKEN_KEY, tok)
    setToken(tok)
  }

  function logout() {
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
          {TABS.map(({ id, label, Icon }) => (
            <button
              key={id}
              className={`${styles.tab} ${activeTab === id ? styles.tabActive : ''}`}
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
        {activeTab === 'players'    && <PlayersPanel token={token} onExpired={logout} />}
        {activeTab === 'commands'   && <CommandsPanel token={token} onExpired={logout} />}
        {activeTab === 'dims'       && <DimsPanel token={token} onExpired={logout} />}
        {activeTab === 'skills'     && <SkillsPanel token={token} onExpired={logout} />}
        {activeTab === 'claims'     && <ClaimsPanel token={token} onExpired={logout} />}
        {activeTab === 'chatfilter' && <ChatFilterPanel token={token} onExpired={logout} />}
        {activeTab === 'mods'       && <ModsPanel token={token} onExpired={logout} />}
        {activeTab === 'config'     && <ConfigEditor token={token} onExpired={logout} />}
        {activeTab === 'features'   && <FeatureShowcase token={token} onExpired={logout} />}
      </div>
    </div>
  )
}
