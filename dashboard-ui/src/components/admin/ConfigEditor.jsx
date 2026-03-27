import { useState, useEffect } from 'react'
import {
  IconGrassBlock, IconEnderPearl, IconBookQuill,
  IconXPOrb, IconBallot, IconDiscord, IconMapScroll, IconShield,
  IconPlayerHead, IconChest, IconSign, IconClock,
  IconVoiceChat, IconFurnace, IconSword,
} from './MinecraftIcons'
import styles from './ConfigEditor.module.css'

// ── Auth ──────────────────────────────────────────────────────────────────────

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// ── Color helpers ─────────────────────────────────────────────────────────────

function toColorInput(hex)   { return hex ? `#${hex.toLowerCase()}` : '#000000' }
function fromColorInput(val) { return val.replace('#', '').toUpperCase() }

// ── Tab definitions ───────────────────────────────────────────────────────────

const SKILL_NAMES = [
  'mining', 'excavation', 'woodcutting',
  'farming', 'fishing', 'agility',
  'melee', 'archery', 'defense',
  'enchanting', 'alchemy', 'trading',
]

const TABS = [
  {
    id: 'general', label: 'General',
    keys: [
      'max_claims', 'vip_bonus_claims', 'allow_lava_wilderness', 'vips',
      'tp_warmup', 'mute_levels_minutes', 'admin_enabled', 'dashboard_port', 'server_name',
    ],
  },
  {
    id: 'chat', label: 'Chat',
    keys: ['welcome_message', 'message_interval', 'periodic_messages', 'rules'],
  },
  {
    id: 'integrations', label: 'Integrations',
    keys: [
      'discord_webhook_url', 'discord_join_leave', 'discord_chat', 'voicechat_enable',
      'votifier_enabled', 'votifier_port', 'votifier_token', 'vote_broadcast', 'vote_rewards',
      'bluemap_enabled', 'bluemap_show_homes', 'bluemap_show_claims', 'bluemap_show_worldborder',
      'bluemap_claim_color', 'bluemap_op_claim_color', 'bluemap_vip_claim_color', 'bluemap_worldborder_color',
    ],
  },
  {
    id: 'skills', label: 'Skills',
    keys: [
      'skill_xp_exponent',
      ...SKILL_NAMES.map(s => `skill_cooldown_${s}`),
      ...SKILL_NAMES.map(s => `skill_unlock_${s}`),
      'cap_industrial_speed', 'cap_nature_health', 'cap_combat_damage', 'cap_knowledge_xp',
      'cap_double_drop', 'cap_defense_armor', 'cap_safe_landing',
    ],
  },
]

// ── Main component ────────────────────────────────────────────────────────────

export default function ConfigEditor({ token, onExpired }) {
  const [cfg,         setCfg]        = useState(null)
  const [draft,       setDraft]      = useState({})
  const [tab,         setTab]        = useState('general')
  const [saving,      setSaving]     = useState({})
  const [msgs,        setMsgs]       = useState({})
  const [loadErr,     setLoadErr]    = useState('')
  const [disabling,   setDisabling]  = useState(false)
  const [disableMsg,  setDisableMsg] = useState('')

  useEffect(() => {
    fetch('/api/admin/config', { headers: authHeaders(token) })
      .then(r => {
        if (r.status === 401 || r.status === 403) { onExpired(); throw new Error('session') }
        if (!r.ok) throw new Error('server')
        return r.json()
      })
      .then(d => { setCfg(d); setDraft(d) })
      .catch(e => { if (e.message !== 'session') setLoadErr('Failed to load config.') })
  }, [token, onExpired])

  function patch(key, val) {
    setDraft(d => ({ ...d, [key]: val }))
  }

  function isTabDirty(tabId) {
    if (!cfg) return false
    const keys = TABS.find(t => t.id === tabId)?.keys ?? []
    return keys.some(k => {
      const d = draft[k], c = cfg[k]
      if (Array.isArray(d) || Array.isArray(c)) return JSON.stringify(d) !== JSON.stringify(c)
      return String(d) !== String(c)
    })
  }

  async function saveTab(tabId) {
    const keys    = TABS.find(t => t.id === tabId)?.keys ?? []
    const payload = {}
    for (const k of keys) if (draft[k] !== undefined) payload[k] = draft[k]

    setSaving(s => ({ ...s, [tabId]: true }))
    setMsgs(m => ({ ...m, [tabId]: '' }))
    try {
      const res  = await fetch('/api/admin/config', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
        body:    JSON.stringify(payload),
      })
      if (res.status === 403) { onExpired(); return }
      const data = await res.json()
      if (!res.ok) {
        setMsgs(m => ({ ...m, [tabId]: data.error || 'Save failed.' }))
      } else {
        const votifierKeys = ['votifier_enabled', 'votifier_port', 'votifier_token']
        const votifierChanged = votifierKeys.some(k => k in payload)
        const suffix = votifierChanged ? ' Votifier listener restarted.' : ''
        setMsgs(m => ({ ...m, [tabId]: `Saved ${data.changed} field(s).${suffix}` }))
        setCfg(c => ({ ...c, ...payload }))
      }
    } catch {
      setMsgs(m => ({ ...m, [tabId]: 'Could not reach server.' }))
    } finally {
      setSaving(s => ({ ...s, [tabId]: false }))
    }
  }

  async function disableDashboard() {
    if (!window.confirm('Stop the dashboard server? You will lose access until the Minecraft server is restarted.')) return
    setDisabling(true)
    setDisableMsg('')
    try {
      await fetch('/api/admin/dashboard/disable', {
        method: 'POST',
        headers: authHeaders(token),
      })
      setDisableMsg('Dashboard stopped. This page will stop responding shortly.')
    } catch {
      setDisableMsg('Server stopped.')
    } finally {
      setDisabling(false)
    }
  }

  if (!cfg) return <div className={styles.loading}>{loadErr || 'Loading config…'}</div>

  const tabMsg   = msgs[tab] || ''
  const tabSave  = saving[tab] || false
  const tabDirty = isTabDirty(tab)

  return (
    <div className={styles.wrap}>
      <div className={styles.tabBar}>
        {TABS.map(t => (
          <button
            key={t.id}
            className={`${styles.tabBtn} ${tab === t.id ? styles.tabActive : ''}`}
            onClick={() => setTab(t.id)}
            type="button"
          >
            {t.label}
            {isTabDirty(t.id) && <span className={styles.tabDot} />}
          </button>
        ))}
      </div>

      <div className={styles.scroll}>
        {tab === 'general'      && <GeneralTab      draft={draft} patch={patch} onDisable={disableDashboard} disabling={disabling} disableMsg={disableMsg} />}
        {tab === 'chat'         && <ChatTab          draft={draft} patch={patch} />}
        {tab === 'integrations' && <IntegrationsTab  draft={draft} patch={patch} />}
        {tab === 'skills'       && <SkillsTab        draft={draft} patch={patch} />}
      </div>

      <div className={styles.footer}>
        {tabMsg && (
          <span className={`${styles.msg} ${tabMsg.startsWith('Saved') ? styles.ok : styles.err}`}>
            {tabMsg}
          </span>
        )}
        {tabDirty && <span className={styles.dirty}>Unsaved changes</span>}
        <button className={styles.saveBtn} onClick={() => saveTab(tab)} disabled={tabSave || !tabDirty}>
          {tabSave ? 'Saving…' : 'Save & Reload'}
        </button>
      </div>
    </div>
  )
}

// ── Tab: General ──────────────────────────────────────────────────────────────

function GeneralTab({ draft, patch, onDisable, disabling, disableMsg }) {
  return (
    <>
      <Group icon={<IconGrassBlock />} title="Claims & Land Protection" accent="green">
        <Row label="Max Claims" hint="Per player base limit">
          <NumInput value={draft.max_claims} onChange={v => patch('max_claims', v)} />
        </Row>
        <Row label="VIP Bonus Claims" hint="Extra claims granted to VIP-tagged players">
          <NumInput value={draft.vip_bonus_claims} onChange={v => patch('vip_bonus_claims', v)} />
        </Row>
        <Row label="Lava in Wilderness" hint="Allow lava source placement outside claimed land">
          <Toggle value={draft.allow_lava_wilderness} onChange={v => patch('allow_lava_wilderness', v)} />
        </Row>
      </Group>

      <Group icon={<IconPlayerHead />} title="VIP Players" accent="yellow">
        <StackedRow label="VIP List" hint="Players with VIP bonus claims, one name per entry">
          <ListEditor value={draft.vips} onChange={v => patch('vips', v)} placeholder="PlayerName" />
        </StackedRow>
      </Group>

      <Group icon={<IconEnderPearl />} title="Teleport" accent="teal">
        <Row label="Warmup Duration" hint="Seconds before teleport fires. Moving cancels it.">
          <NumInput value={draft.tp_warmup} onChange={v => patch('tp_warmup', v)} suffix="s" />
        </Row>
      </Group>

      <Group icon={<IconShield />} title="Chat Moderation" accent="red">
        <StackedRow label="Mute Escalation Levels" hint="Duration in minutes for each repeated offence">
          <MuteLevels value={draft.mute_levels_minutes} onChange={v => patch('mute_levels_minutes', v)} />
        </StackedRow>
      </Group>

      <Group icon={<IconChest />} title="Admin Panel" accent="red">
        <Row label="Server Name" hint="Shown in the dashboard header. Leave blank to hide.">
          <TextInput value={draft.server_name} onChange={v => patch('server_name', v)} placeholder="My SMP" />
        </Row>
        <Row label="Panel Enabled" hint="Disabling locks you out. Re-enable with /smp admin setpassword in-game.">
          <Toggle value={draft.admin_enabled} onChange={v => patch('admin_enabled', v)} />
        </Row>
        <Row label="Dashboard Port" hint="Port the dashboard listens on. Requires restart to change.">
          <NumInput value={draft.dashboard_port} onChange={v => patch('dashboard_port', v)} />
        </Row>
        <div className={styles.note}>Password changes: <code>/smp admin setpassword</code> in-game</div>
      </Group>

      <DangerZone onDisable={onDisable} disabling={disabling} disableMsg={disableMsg} />
    </>
  )
}

// ── Danger Zone ───────────────────────────────────────────────────────────────

function DangerZone({ onDisable, disabling, disableMsg }) {
  const [open, setOpen] = useState(false)
  return (
    <div className={styles.dangerSection}>
      <button className={styles.dangerToggle} onClick={() => setOpen(o => !o)} type="button">
        {open ? '[ - ] Danger Zone' : '[ + ] Danger Zone'}
      </button>
      {open && (
        <div className={styles.dangerBody}>
          <div className={styles.dangerWarn}>
            Stops the dashboard HTTP server and frees the port. The config is saved with
            dashboard disabled. Re-enable by restarting the Minecraft server.
          </div>
          <div className={styles.dangerRow}>
            <button className={styles.dangerBtn} onClick={onDisable} disabled={disabling}>
              {disabling ? 'Stopping…' : 'Disable Dashboard'}
            </button>
            {disableMsg && <span className={styles.disableMsg}>{disableMsg}</span>}
          </div>
        </div>
      )}
    </div>
  )
}

// ── Color code legend data ────────────────────────────────────────────────────

const COLOR_CODES = [
  { code: '&0', label: 'Black',        hex: '#000000' },
  { code: '&1', label: 'Dark Blue',    hex: '#0000AA' },
  { code: '&2', label: 'Dark Green',   hex: '#00AA00' },
  { code: '&3', label: 'Dark Aqua',    hex: '#00AAAA' },
  { code: '&4', label: 'Dark Red',     hex: '#AA0000' },
  { code: '&5', label: 'Dark Purple',  hex: '#AA00AA' },
  { code: '&6', label: 'Gold',         hex: '#FFAA00' },
  { code: '&7', label: 'Gray',         hex: '#AAAAAA' },
  { code: '&8', label: 'Dark Gray',    hex: '#555555' },
  { code: '&9', label: 'Blue',         hex: '#5555FF' },
  { code: '&a', label: 'Green',        hex: '#55FF55' },
  { code: '&b', label: 'Aqua',         hex: '#55FFFF' },
  { code: '&c', label: 'Red',          hex: '#FF5555' },
  { code: '&d', label: 'Light Purple', hex: '#FF55FF' },
  { code: '&e', label: 'Yellow',       hex: '#FFFF55' },
  { code: '&f', label: 'White',        hex: '#FFFFFF' },
]

const FORMAT_CODES = [
  { code: '&l', label: 'Bold' },
  { code: '&o', label: 'Italic' },
  { code: '&n', label: 'Underline' },
  { code: '&m', label: 'Strikethrough' },
  { code: '&k', label: 'Obfuscated' },
  { code: '&r', label: 'Reset' },
]

function ColorLegend() {
  const [open, setOpen] = useState(true)
  return (
    <div className={styles.legend}>
      <button className={styles.legendToggle} onClick={() => setOpen(o => !o)} type="button">
        <span className={styles.legendCaret}>{open ? '▾' : '▸'}</span>
        <span className={styles.legendToggleTitle}>Color & Formatting Reference</span>
        <span className={styles.legendSwatchStrip}>
          {COLOR_CODES.map(({ code, hex }) => (
            <span key={code} className={styles.legendStripDot} style={{ background: hex }} />
          ))}
        </span>
      </button>
      {open && (
        <div className={styles.legendBody}>
          <div className={styles.legendSection}>
            <span className={styles.legendSectionLabel}>Colors</span>
            <div className={styles.legendGrid}>
              {COLOR_CODES.map(({ code, label, hex }) => (
                <div key={code} className={styles.legendItem} title={label}>
                  <span className={styles.legendSwatch} style={{ background: hex, boxShadow: hex === '#000000' ? 'inset 0 0 0 1px #333' : 'none' }} />
                  <code className={styles.legendCode}>{code}</code>
                  <span className={styles.legendLabel}>{label}</span>
                </div>
              ))}
            </div>
          </div>
          <div className={styles.legendSection}>
            <span className={styles.legendSectionLabel}>Formatting</span>
            <div className={styles.legendGrid}>
              {FORMAT_CODES.map(({ code, label }) => (
                <div key={code} className={styles.legendItem}>
                  <code className={styles.legendCode}>{code}</code>
                  <span className={styles.legendLabel}>{label}</span>
                </div>
              ))}
            </div>
          </div>
          <div className={styles.legendExamples}>
            <span className={styles.legendSectionLabel}>Examples</span>
            <div className={styles.legendExample}>
              <code className={styles.exampleInput}>&amp;6Welcome to &amp;aQuackedSMP&amp;6!</code>
              <span className={styles.exampleArrow}>→</span>
              <span className={styles.exampleOutput}>
                <span style={{ color: '#FFAA00' }}>Welcome to </span>
                <span style={{ color: '#55FF55' }}>QuackedSMP</span>
                <span style={{ color: '#FFAA00' }}>!</span>
              </span>
            </div>
            <div className={styles.legendExample}>
              <code className={styles.exampleInput}>&amp;e1. &amp;fBe respectful.</code>
              <span className={styles.exampleArrow}>→</span>
              <span className={styles.exampleOutput}>
                <span style={{ color: '#FFFF55' }}>1. </span>
                <span style={{ color: '#FFFFFF' }}>Be respectful.</span>
              </span>
            </div>
            <div className={styles.legendExample}>
              <code className={styles.exampleInput}>&amp;b[Tip] &amp;fUse &amp;a/claim&amp;f!</code>
              <span className={styles.exampleArrow}>→</span>
              <span className={styles.exampleOutput}>
                <span style={{ color: '#55FFFF' }}>[Tip] </span>
                <span style={{ color: '#FFFFFF' }}>Use </span>
                <span style={{ color: '#55FF55' }}>/claim</span>
                <span style={{ color: '#FFFFFF' }}>!</span>
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Tab: Chat ─────────────────────────────────────────────────────────────────

function ChatTab({ draft, patch }) {
  return (
    <>
      <ColorLegend />
      <Group icon={<IconSign />} title="Announcements" accent="yellow">
        <StackedRow label="Welcome Message" hint="{player} is replaced with the joining player's name">
          <TextInput value={draft.welcome_message} onChange={v => patch('welcome_message', v)} />
        </StackedRow>
        <Row label="Periodic Tip Interval" hint="Seconds between automatic tip broadcasts">
          <NumInput value={draft.message_interval} onChange={v => patch('message_interval', v)} suffix="s" />
        </Row>
      </Group>

      <Group icon={<IconClock />} title="Periodic Tips" accent="yellow">
        <StackedRow label="Tip Messages" hint="Broadcast in rotation. Supports & color codes.">
          <ListEditor
            value={draft.periodic_messages}
            onChange={v => patch('periodic_messages', v)}
            placeholder="&b[Tip] &fYour tip here…"
          />
        </StackedRow>
      </Group>

      <Group icon={<IconBookQuill />} title="Server Rules" accent="peach">
        <StackedRow label="Rules" hint="Shown with /rules. Supports & color codes.">
          <ListEditor
            value={draft.rules}
            onChange={v => patch('rules', v)}
            placeholder="&e1. Your rule here…"
          />
        </StackedRow>
      </Group>
    </>
  )
}

// ── Tab: Integrations ─────────────────────────────────────────────────────────

function IntegrationsTab({ draft, patch }) {
  return (
    <>
      <Group icon={<IconDiscord />} title="Discord Webhook" accent="blue">
        <StackedRow label="Webhook URL" hint="Paste the webhook URL from your Discord channel settings">
          <SecretInput
            value={draft.discord_webhook_url}
            onChange={v => patch('discord_webhook_url', v)}
            placeholder="https://discord.com/api/webhooks/…"
          />
        </StackedRow>
        <Row label="Join / Leave Events" hint="Post a message when players join or leave">
          <Toggle value={draft.discord_join_leave} onChange={v => patch('discord_join_leave', v)} />
        </Row>
        <Row label="Chat Relay" hint="Mirror in-game chat messages to Discord">
          <Toggle value={draft.discord_chat} onChange={v => patch('discord_chat', v)} />
        </Row>
      </Group>

      <Group icon={<IconVoiceChat />} title="Simple Voice Chat" accent="teal">
        <Row label="Enabled" hint="Requires Simple Voice Chat mod on the server.">
          <Toggle value={draft.voicechat_enable} onChange={v => patch('voicechat_enable', v)} />
        </Row>
      </Group>

      <Group icon={<IconBallot />} title="Votifier" accent="peach">
        <Row label="Enabled" hint="Receives votes from listing sites via NuVotifier v2.">
          <Toggle value={draft.votifier_enabled} onChange={v => patch('votifier_enabled', v)} />
        </Row>
        <Row label="Port" hint="Votifier listen port. Restarts automatically on save.">
          <NumInput value={draft.votifier_port} onChange={v => patch('votifier_port', v)} />
        </Row>
        <StackedRow label="Token" hint="Auto-generated on first start. Copy into your voting site when registering.">
          <TextInput
            value={draft.votifier_token}
            onChange={v => patch('votifier_token', v)}
            placeholder="auto-generated on first start"
          />
        </StackedRow>
        <StackedRow label="Vote Broadcast" hint="Sent on vote. {player} = voter name. Supports & color codes.">
          <TextInput value={draft.vote_broadcast} onChange={v => patch('vote_broadcast', v)} />
        </StackedRow>
        <StackedRow label="Vote Reward Commands" hint="Run on every vote. {player} = voter name. One command per entry.">
          <ListEditor
            value={draft.vote_rewards}
            onChange={v => patch('vote_rewards', v)}
            placeholder="give {player} diamond 2"
          />
        </StackedRow>
      </Group>

      <Group icon={<IconMapScroll />} title="BlueMap" accent="sky">
        <Row label="Enabled" hint="Live 3D web map integration">
          <Toggle value={draft.bluemap_enabled} onChange={v => patch('bluemap_enabled', v)} />
        </Row>
        <Row label="Show Homes" hint="Display player home markers on the map">
          <Toggle value={draft.bluemap_show_homes} onChange={v => patch('bluemap_show_homes', v)} />
        </Row>
        <Row label="Show Claims" hint="Draw claim region outlines on the map">
          <Toggle value={draft.bluemap_show_claims} onChange={v => patch('bluemap_show_claims', v)} />
        </Row>
        <Row label="Show World Border" hint="Draw the world border on the map">
          <Toggle value={draft.bluemap_show_worldborder} onChange={v => patch('bluemap_show_worldborder', v)} />
        </Row>
        <Row label="Claim Color" hint="Default fill color for player claims">
          <ColorInput value={draft.bluemap_claim_color} onChange={v => patch('bluemap_claim_color', v)} />
        </Row>
        <Row label="OP Claim Color" hint="Fill color for operator claims">
          <ColorInput value={draft.bluemap_op_claim_color} onChange={v => patch('bluemap_op_claim_color', v)} />
        </Row>
        <Row label="VIP Claim Color" hint="Fill color for VIP player claims">
          <ColorInput value={draft.bluemap_vip_claim_color} onChange={v => patch('bluemap_vip_claim_color', v)} />
        </Row>
        <Row label="World Border Color" hint="Color of the world border line">
          <ColorInput value={draft.bluemap_worldborder_color} onChange={v => patch('bluemap_worldborder_color', v)} />
        </Row>
      </Group>
    </>
  )
}

// ── Tab: Skills ───────────────────────────────────────────────────────────────

const SKILL_GROUPS = [
  { name: 'Industrial', accent: 'peach',  icon: <IconFurnace />,    skills: ['mining', 'excavation', 'woodcutting'] },
  { name: 'Nature',     accent: 'green',  icon: <IconGrassBlock />, skills: ['farming', 'fishing', 'agility'] },
  { name: 'Combat',     accent: 'red',    icon: <IconSword />,      skills: ['melee', 'archery', 'defense'] },
  { name: 'Knowledge',  accent: 'mauve',  icon: <IconBookQuill />,  skills: ['enchanting', 'alchemy', 'trading'] },
]

function SkillsTab({ draft, patch }) {
  return (
    <>
      <Group icon={<IconXPOrb />} title="XP Scaling" accent="mauve">
        <Row label="XP Exponent" hint="Higher = slower level scaling. Default 1.5, change carefully.">
          <NumInput value={draft.skill_xp_exponent} step={0.05} onChange={v => patch('skill_xp_exponent', v)} />
        </Row>
      </Group>

      {SKILL_GROUPS.map(grp => (
        <Group key={grp.name} icon={grp.icon} title={`${grp.name} Skills`} accent={grp.accent}>
          <div className={styles.skillTableHeader}>
            <span />
            <span className={styles.skillColLabel}>Cooldown</span>
            <span className={styles.skillColLabel}>Unlock Lvl</span>
          </div>
          {grp.skills.map(skill => (
            <div key={skill} className={styles.skillRow}>
              <span className={styles.skillName}>
                {skill.charAt(0).toUpperCase() + skill.slice(1)}
              </span>
              <NumInput
                value={draft[`skill_cooldown_${skill}`]}
                onChange={v => patch(`skill_cooldown_${skill}`, v)}
                suffix="s"
              />
              <NumInput
                value={draft[`skill_unlock_${skill}`]}
                onChange={v => patch(`skill_unlock_${skill}`, v)}
              />
            </div>
          ))}
        </Group>
      ))}

      <Group icon={<IconShield />} title="Passive Stat Caps" accent="mauve">
        <Row label="Industrial Speed" hint="Max movement speed bonus at level 100 (0.5 = +50%)">
          <NumInput value={draft.cap_industrial_speed} step={0.05} onChange={v => patch('cap_industrial_speed', v)} />
        </Row>
        <Row label="Nature Health" hint="Max bonus hearts at level 100 (10.0 = +10 hearts)">
          <NumInput value={draft.cap_nature_health} step={0.5} onChange={v => patch('cap_nature_health', v)} />
        </Row>
        <Row label="Combat Damage" hint="Max bonus damage multiplier at level 100 (1.0 = +100%)">
          <NumInput value={draft.cap_combat_damage} step={0.05} onChange={v => patch('cap_combat_damage', v)} />
        </Row>
        <Row label="Knowledge XP" hint="Max bonus XP orb multiplier at level 100 (1.0 = +100%)">
          <NumInput value={draft.cap_knowledge_xp} step={0.05} onChange={v => patch('cap_knowledge_xp', v)} />
        </Row>
        <Row label="Double Drop Chance" hint="Max double drop chance at Industrial level 100 (0.5 = 50%)">
          <NumInput value={draft.cap_double_drop} step={0.05} onChange={v => patch('cap_double_drop', v)} />
        </Row>
        <Row label="Defense Armor" hint="Max bonus armor points at Defense level 100 (10.0 = +10)">
          <NumInput value={draft.cap_defense_armor} step={0.5} onChange={v => patch('cap_defense_armor', v)} />
        </Row>
        <Row label="Safe Landing" hint="Max fall damage absorbed at Agility level 100 (1.0 = 100%)">
          <NumInput value={draft.cap_safe_landing} step={0.05} onChange={v => patch('cap_safe_landing', v)} />
        </Row>
      </Group>
    </>
  )
}

// ── Section group ─────────────────────────────────────────────────────────────

function Group({ icon, title, accent = 'blue', children }) {
  return (
    <div className={`${styles.group} ${styles[`accent_${accent}`]}`}>
      <div className={styles.groupHeader}>
        <span className={styles.groupIcon}>{icon}</span>
        <span className={styles.groupTitle}>{title}</span>
      </div>
      <div className={styles.groupBody}>{children}</div>
    </div>
  )
}

// ── Row layouts ───────────────────────────────────────────────────────────────

function Row({ label, hint, children }) {
  return (
    <div className={styles.row}>
      <div className={styles.rowLeft}>
        <span className={styles.rowLabel}>{label}</span>
        {hint && <span className={styles.rowHint}>{hint}</span>}
      </div>
      <div className={styles.rowRight}>{children}</div>
    </div>
  )
}

function StackedRow({ label, hint, children }) {
  return (
    <div className={styles.rowStacked}>
      <span className={styles.rowLabel}>{label}</span>
      {hint && <span className={styles.rowHint}>{hint}</span>}
      {children}
    </div>
  )
}

// ── Controls ──────────────────────────────────────────────────────────────────

function Toggle({ value, onChange }) {
  return (
    <button
      className={`${styles.toggle} ${value ? styles.toggleOn : ''}`}
      onClick={() => onChange(!value)}
      type="button"
    >
      <span className={styles.toggleKnob} />
    </button>
  )
}

function NumInput({ value, step = 1, onChange, suffix }) {
  return (
    <div className={styles.numWrap}>
      <input
        className={styles.numInput}
        type="number"
        value={value ?? ''}
        step={step}
        onChange={e => onChange(step < 1 ? parseFloat(e.target.value) : parseInt(e.target.value))}
      />
      {suffix && <span className={styles.suffix}>{suffix}</span>}
    </div>
  )
}

function TextInput({ value, onChange, placeholder }) {
  return (
    <input
      className={styles.textInput}
      type="text"
      value={value ?? ''}
      onChange={e => onChange(e.target.value)}
      placeholder={placeholder}
      spellCheck={false}
    />
  )
}

function SecretInput({ value, onChange, placeholder }) {
  return (
    <input
      className={styles.textInput}
      type="password"
      value={value ?? ''}
      onChange={e => onChange(e.target.value)}
      placeholder={placeholder}
      autoComplete="off"
      spellCheck={false}
    />
  )
}

function ColorInput({ value, onChange }) {
  const raw    = (value ?? '').replace(/^#/, '')
  const isArgb = raw.length === 8
  const rgb    = isArgb ? raw.slice(2) : (raw || '000000')
  return (
    <label className={styles.colorWrap}>
      <input
        className={styles.colorInput}
        type="color"
        value={`#${rgb.toLowerCase()}`}
        onChange={e => {
          const newRgb = e.target.value.replace('#', '').toUpperCase()
          onChange(isArgb ? raw.slice(0, 2) + newRgb : newRgb)
        }}
      />
      <span className={styles.colorHex}>#{raw.toUpperCase()}</span>
    </label>
  )
}

function ListEditor({ value = [], onChange, placeholder }) {
  return (
    <div className={styles.listEditor}>
      {value.map((item, i) => (
        <div key={i} className={styles.listRow}>
          <input
            className={`${styles.textInput} ${styles.listInput}`}
            type="text"
            value={item}
            onChange={e => {
              const next = [...value]
              next[i] = e.target.value
              onChange(next)
            }}
            placeholder={placeholder}
            spellCheck={false}
          />
          <button
            className={styles.listRemove}
            onClick={() => onChange(value.filter((_, j) => j !== i))}
            type="button"
            title="Remove"
          >✕</button>
        </div>
      ))}
      <button
        className={styles.listAdd}
        onClick={() => onChange([...value, ''])}
        type="button"
      >+ Add</button>
    </div>
  )
}

function MuteLevels({ value = [], onChange }) {
  const levels = value.length > 0 ? value : [60, 120, 240, 480, 1440]
  return (
    <div className={styles.muteLevels}>
      {levels.map((v, i) => (
        <div key={i} className={styles.muteLevel}>
          <span className={styles.muteLevelLabel}>Level {i + 1}</span>
          <NumInput
            value={v}
            onChange={n => {
              const next = [...levels]
              next[i] = n
              onChange(next)
            }}
            suffix="min"
          />
        </div>
      ))}
    </div>
  )
}
