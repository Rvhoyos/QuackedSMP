import { useState, useEffect } from 'react'
import {
  IconGrassBlock, IconEnderPearl, IconBookQuill,
  IconBallot, IconDiscord, IconMapScroll, IconShield,
  IconPlayerHead, IconChest, IconSign, IconClock,
  IconVoiceChat, IconCrown, IconLoot, IconTierStar,
  IconHelmetGhost, IconChestplateGhost, IconLeggingsGhost, IconBootsGhost,
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

const TABS = [
  {
    id: 'general', label: 'General',
    keys: [
      'max_claims', 'allow_lava_wilderness', 'allow_fire_wilderness',
      'protect_explosions', 'protect_fire_claims', 'protect_enderman', 'protect_farmland',
      'tp_warmup', 'mute_levels_minutes', 'hardcore_death_percent',
      'admin_enabled', 'dashboard_port', 'server_name',
    ],
  },
  {
    id: 'vip', label: 'VIP',
    keys: ['tiers', 'kit_cooldown_seconds', 'kit_definitions'],
  },
  {
    id: 'chat', label: 'Chat',
    keys: ['welcome_message', 'message_interval', 'periodic_messages', 'rules'],
  },
  {
    id: 'integrations', label: 'Integrations',
    keys: [
      'discord_webhook_url', 'discord_join_leave', 'discord_chat', 'voicechat_enable',
      'votifier_enabled', 'votifier_port', 'votifier_token', 'vote_broadcast', 'vote_rewards', 'vote_vip_rewards',
      'bluemap_enabled', 'bluemap_show_homes', 'bluemap_show_claims', 'bluemap_show_worldborder',
      'bluemap_claim_color', 'bluemap_op_claim_color', 'bluemap_vip_claim_color', 'bluemap_worldborder_color',
      'bluemap_show_spawn_protection', 'bluemap_spawn_protection_color',
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
  }, [token]) // eslint-disable-line react-hooks/exhaustive-deps

  function patch(key, val) {
    setDraft(d => ({ ...d, [key]: val }))
  }

  function isTabDirty(tabId) {
    if (!cfg) return false
    const keys = TABS.find(t => t.id === tabId)?.keys ?? []
    return keys.some(k => {
      const d = draft[k], c = cfg[k]
      if (typeof d === 'object' || typeof c === 'object') return JSON.stringify(d) !== JSON.stringify(c)
      return String(d) !== String(c)
    })
  }

  async function saveTab(tabId) {
    const keys    = TABS.find(t => t.id === tabId)?.keys ?? []
    const payload = {}
    for (const k of keys) {
      if (draft[k] === undefined) continue
      const d = draft[k], c = cfg[k]
      const dirty = (Array.isArray(d) || Array.isArray(c))
        ? JSON.stringify(d) !== JSON.stringify(c)
        : String(d) !== String(c)
      if (dirty) payload[k] = draft[k]
    }

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
        {tab === 'vip'          && <VipTab           draft={draft} patch={patch} />}
        {tab === 'chat'         && <ChatTab          draft={draft} patch={patch} />}
        {tab === 'integrations' && <IntegrationsTab  draft={draft} patch={patch} />}
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
        <Row label="Lava in Wilderness" hint="Allow lava source placement outside claimed land">
          <Toggle value={draft.allow_lava_wilderness} onChange={v => patch('allow_lava_wilderness', v)} />
        </Row>
        <Row label="Fire in Wilderness" hint="Allow fire to spread in unclaimed wilderness">
          <Toggle value={draft.allow_fire_wilderness} onChange={v => patch('allow_fire_wilderness', v)} />
        </Row>
        <Row label="Protect: Explosions" hint="Block explosions in claimed chunks">
          <Toggle value={draft.protect_explosions} onChange={v => patch('protect_explosions', v)} />
        </Row>
        <Row label="Protect: Fire in Claims" hint="Block fire spread and burn-out in claimed chunks">
          <Toggle value={draft.protect_fire_claims} onChange={v => patch('protect_fire_claims', v)} />
        </Row>
        <Row label="Protect: Enderman Grief" hint="Prevent endermen from moving blocks in claims and spawn">
          <Toggle value={draft.protect_enderman} onChange={v => patch('protect_enderman', v)} />
        </Row>
        <Row label="Protect: Farmland Trample" hint="Prevent farmland trampling in claims and spawn">
          <Toggle value={draft.protect_farmland} onChange={v => patch('protect_farmland', v)} />
        </Row>
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

      <Group icon={<IconShield />} title="Hardcore Mode" accent="red">
        <Row label="Death Threshold" hint="Percent of peak players that must die to end a session">
          <NumInput value={draft.hardcore_death_percent} onChange={v => patch('hardcore_death_percent', v)} suffix="%" />
        </Row>
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
            dashboard disabled. Re-enable with <code>/smp admin setpassword</code> in-game.
          </div>
          <div className={styles.dangerRow}>
            <button className={styles.dangerBtn} type="button" onClick={onDisable} disabled={disabling}>
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
        <StackedRow label="Welcome Message" hint="{player} = joining player's name, {server} = server name from General tab">
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
        <StackedRow label="Vote Reward Commands" hint="Run on every vote. {player} = voter name. One random command per vote.">
          <ListEditor
            value={draft.vote_rewards}
            onChange={v => patch('vote_rewards', v)}
            placeholder="give {player} diamond 2"
          />
        </StackedRow>
        <VipRewardsEditor
          tiers={draft.tiers || []}
          value={draft.vote_vip_rewards || {}}
          onChange={v => patch('vote_vip_rewards', v)}
        />
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
        <Row label="Show Spawn Protection" hint="Draw the spawn protection radius on the map">
          <Toggle value={draft.bluemap_show_spawn_protection} onChange={v => patch('bluemap_show_spawn_protection', v)} />
        </Row>
        <Row label="Spawn Protection Color" hint="Fill color for the spawn protection area">
          <ColorInput value={draft.bluemap_spawn_protection_color} onChange={v => patch('bluemap_spawn_protection_color', v)} />
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

// Tier-keyed VIP vote reward editor. Shows one command list per defined tier, stacking upward.
function VipRewardsEditor({ tiers = [], value = {}, onChange }) {
  if (!tiers.length) return (
    <StackedRow label="VIP Vote Bonuses" hint="Define tiers first (Server tab) to add VIP vote rewards.">
      <span className={styles.hintText}>No tiers configured</span>
    </StackedRow>
  )
  return tiers.map(t => (
    <StackedRow
      key={t.tier}
      label={`${t.name || 'Tier ' + t.tier} Vote Bonus`}
      hint={`Extra random reward for tier ${t.tier}+ voters. Stacks with all lower tiers.`}
    >
      <ListEditor
        value={value[String(t.tier)] || []}
        onChange={v => onChange({ ...value, [String(t.tier)]: v })}
        placeholder="give {player} netherite_ingot 1"
      />
    </StackedRow>
  ))
}

const TIER_COLORS = ['#cba6f7', '#fab387', '#f9e2af', '#a6e3a1', '#89b4fa', '#f5c2e7', '#94e2d5', '#89dceb']

function TierDefsEditor({ value = [], onChange }) {
  function update(i, field, val) {
    const next = [...value]
    next[i] = { ...next[i], [field]: val }
    onChange(next)
  }
  const nextTier = value.length > 0 ? Math.max(...value.map(v => v.tier)) + 1 : 1
  return (
    <div className={styles.listEditor}>
      {value.map((item, i) => (
        <div key={i} className={styles.tierDefRow}>
          <span className={styles.tierBadgeInline}>
            <span className={styles.tierStarWrap}>
              <IconTierStar size={16} color={TIER_COLORS[i % TIER_COLORS.length]} />
            </span>
            <span className={styles.tierDefNum} style={{ color: TIER_COLORS[i % TIER_COLORS.length] }}>T{item.tier}</span>
          </span>
          <div style={{ flex: 1, minWidth: 60 }}>
            <TextInput value={item.name} onChange={v => update(i, 'name', v)} placeholder="Name" />
          </div>
          <div style={{ flexShrink: 0 }}>
            <NumInput value={item.minPlaytimeHours} onChange={v => update(i, 'minPlaytimeHours', v)} suffix="h" />
          </div>
          <div style={{ flexShrink: 0 }}>
            <NumInput value={item.bonusClaims} onChange={v => update(i, 'bonusClaims', v)} suffix="claims" />
          </div>
          <button className={styles.listRemove} onClick={() => onChange(value.filter((_, j) => j !== i))} type="button">✕</button>
        </div>
      ))}
      <button className={styles.listAdd} onClick={() => onChange([...value, { tier: nextTier, name: '', minPlaytimeHours: 0, bonusClaims: 0 }])} type="button">+ Add Tier</button>
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

// ── Tab: Kits ────────────────────────────────────────────────────────────────

function VipTab({ draft, patch }) {
  return (
    <>
      <Group icon={<IconCrown />} title="Tiers" accent="mauve">
        <StackedRow label="Tier Definitions" hint="Each tier: level number, display name, min playtime to auto-earn, bonus claims granted">
          <TierDefsEditor value={draft.tiers} onChange={v => patch('tiers', v)} />
        </StackedRow>
      </Group>

      <Group icon={<IconClock />} title="Kit Settings" accent="teal">
        <Row label="Cooldown Duration" hint="Hours between kit claims. Default 24 hours.">
          <NumInput value={draft.kit_cooldown_seconds != null ? draft.kit_cooldown_seconds / 3600 : ''} onChange={v => patch('kit_cooldown_seconds', Math.round(v * 3600))} suffix="h" />
        </Row>
      </Group>

      <Group icon={<IconLoot />} title="Kit Definitions" accent="peach">
        <StackedRow label="Kits" hint="Each kit: name (used in /smp kit command), display name (& color codes), min tier, armor, and items.">
          <KitDefsEditor value={draft.kit_definitions} onChange={v => patch('kit_definitions', v)} />
        </StackedRow>
      </Group>
    </>
  )
}

// ── Kit Definitions Editor ───────────────────────────────────────────────────

const MATERIALS = ['leather', 'chainmail', 'iron', 'golden', 'diamond', 'netherite']

const ARMOR_SUGGESTIONS = {
  head:  MATERIALS.map(m => `minecraft:${m}_helmet`),
  chest: MATERIALS.map(m => `minecraft:${m}_chestplate`),
  legs:  MATERIALS.map(m => `minecraft:${m}_leggings`),
  feet:  MATERIALS.map(m => `minecraft:${m}_boots`),
}

const ITEM_SUGGESTIONS = [
  // Weapons
  'minecraft:wooden_sword', 'minecraft:stone_sword', 'minecraft:iron_sword', 'minecraft:golden_sword',
  'minecraft:diamond_sword', 'minecraft:netherite_sword', 'minecraft:bow', 'minecraft:crossbow', 'minecraft:trident',
  // Tools
  'minecraft:wooden_pickaxe', 'minecraft:stone_pickaxe', 'minecraft:iron_pickaxe', 'minecraft:diamond_pickaxe', 'minecraft:netherite_pickaxe',
  'minecraft:wooden_axe', 'minecraft:stone_axe', 'minecraft:iron_axe', 'minecraft:diamond_axe', 'minecraft:netherite_axe',
  'minecraft:wooden_shovel', 'minecraft:stone_shovel', 'minecraft:iron_shovel', 'minecraft:diamond_shovel', 'minecraft:netherite_shovel',
  'minecraft:wooden_hoe', 'minecraft:stone_hoe', 'minecraft:iron_hoe', 'minecraft:diamond_hoe', 'minecraft:netherite_hoe',
  // Food
  'minecraft:bread', 'minecraft:cooked_beef', 'minecraft:cooked_porkchop', 'minecraft:cooked_chicken', 'minecraft:cooked_salmon',
  'minecraft:baked_potato', 'minecraft:golden_apple', 'minecraft:enchanted_golden_apple', 'minecraft:golden_carrot',
  // Resources
  'minecraft:oak_log', 'minecraft:cobblestone', 'minecraft:iron_ingot', 'minecraft:gold_ingot', 'minecraft:diamond',
  'minecraft:emerald', 'minecraft:coal', 'minecraft:oak_planks', 'minecraft:stick',
  // Utility
  'minecraft:torch', 'minecraft:crafting_table', 'minecraft:furnace', 'minecraft:chest', 'minecraft:ender_pearl',
  'minecraft:white_bed', 'minecraft:shield', 'minecraft:bucket', 'minecraft:fishing_rod', 'minecraft:compass',
  'minecraft:map', 'minecraft:spyglass', 'minecraft:flint_and_steel',
  // Farming
  'minecraft:wheat_seeds', 'minecraft:melon_seeds', 'minecraft:pumpkin_seeds', 'minecraft:carrot',
  'minecraft:potato', 'minecraft:beetroot_seeds', 'minecraft:bone_meal',
]

function SuggestInput({ value, onChange, placeholder, listId, suggestions = ITEM_SUGGESTIONS }) {
  return (
    <>
      <input
        className={styles.textInput}
        type="text"
        value={value ?? ''}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        list={listId}
        spellCheck={false}
        autoComplete="off"
      />
      <datalist id={listId}>
        {suggestions.map(s => <option key={s} value={s} />)}
      </datalist>
    </>
  )
}

const ARMOR_GHOST_ICONS = {
  head:  IconHelmetGhost,
  chest: IconChestplateGhost,
  legs:  IconLeggingsGhost,
  feet:  IconBootsGhost,
}

function shortItemName(id) {
  if (!id) return ''
  const name = id.replace(/^minecraft:/, '')
  return name.length > 10 ? name.slice(0, 9) + '\u2026' : name
}

function KitDefsEditor({ value = [], onChange }) {
  const [expanded, setExpanded] = useState({})
  const [editingItem, setEditingItem] = useState({})

  function toggle(i) {
    setExpanded(e => ({ ...e, [i]: !e[i] }))
    setEditingItem(e => ({ ...e, [i]: null }))
  }

  function update(i, field, val) {
    const next = value.map((k, j) => j === i ? { ...k, [field]: val } : k)
    onChange(next)
  }

  function updateArmor(i, slot, val) {
    const next = value.map((k, j) => j === i ? { ...k, armor: { ...k.armor, [slot]: val } } : k)
    onChange(next)
  }

  function updateItem(kitIdx, itemIdx, field, val) {
    const next = value.map((k, j) => {
      if (j !== kitIdx) return k
      const items = k.items.map((it, ii) => ii === itemIdx ? { ...it, [field]: val } : it)
      return { ...k, items }
    })
    onChange(next)
  }

  function addItem(kitIdx) {
    const next = value.map((k, j) => {
      if (j !== kitIdx) return k
      return { ...k, items: [...k.items, { item: '', count: 1 }] }
    })
    onChange(next)
    setEditingItem(e => ({ ...e, [kitIdx]: value[kitIdx].items.length }))
  }

  function removeItem(kitIdx, itemIdx) {
    const next = value.map((k, j) => {
      if (j !== kitIdx) return k
      return { ...k, items: k.items.filter((_, ii) => ii !== itemIdx) }
    })
    onChange(next)
    setEditingItem(e => ({ ...e, [kitIdx]: null }))
  }

  function addKit() {
    onChange([...value, {
      name: '', displayName: '', minTier: 0,
      armor: { head: '', chest: '', legs: '', feet: '' },
      items: [],
    }])
    setExpanded(e => ({ ...e, [value.length]: true }))
  }

  function removeKit(i) {
    onChange(value.filter((_, j) => j !== i))
  }

  return (
    <div className={styles.listEditor}>
      {value.map((kit, i) => (
        <div key={i} className={styles.kitCard}>
          <div className={styles.kitHeader} onClick={() => toggle(i)}>
            <span className={styles.kitCaret}>{expanded[i] ? '▾' : '▸'}</span>
            <span className={styles.kitName}>{kit.name || '(unnamed)'}</span>
            {kit.minTier > 0 && (
              <span className={styles.kitTierBadge}>
                <IconTierStar size={12} color="#cba6f7" />
                Tier {kit.minTier}
              </span>
            )}
            <button className={styles.listRemove} onClick={e => { e.stopPropagation(); removeKit(i) }} type="button">✕</button>
          </div>

          {expanded[i] && (
            <div className={styles.kitBody}>
              <div className={styles.kitFieldRow}>
                <label className={styles.kitFieldLabel}>Name</label>
                <TextInput value={kit.name} onChange={v => update(i, 'name', v)} placeholder="starter" />
              </div>
              <div className={styles.kitFieldRow}>
                <label className={styles.kitFieldLabel}>Display Name</label>
                <TextInput value={kit.displayName} onChange={v => update(i, 'displayName', v)} placeholder="&aStarter Kit" />
              </div>
              <div className={styles.kitFieldRow}>
                <label className={styles.kitFieldLabel}>Min Tier</label>
                <NumInput value={kit.minTier} onChange={v => update(i, 'minTier', v)} />
              </div>

              <div className={styles.kitSectionLabel}>Armor</div>
              <div className={styles.armorColumn}>
                {['head', 'chest', 'legs', 'feet'].map(slot => {
                  const GhostIcon = ARMOR_GHOST_ICONS[slot]
                  const filled = !!(kit.armor?.[slot])
                  return (
                    <div key={slot} className={styles.armorRow}>
                      <div className={`${styles.invSlot} ${filled ? styles.invSlotFilled : ''}`}>
                        <span className={styles.invSlotGhost}>
                          <GhostIcon size={24} />
                        </span>
                      </div>
                      <SuggestInput
                        value={kit.armor?.[slot] ?? ''}
                        onChange={v => updateArmor(i, slot, v)}
                        placeholder={ARMOR_SUGGESTIONS[slot]?.[2] ?? ''}
                        listId={`armor-${i}-${slot}`}
                        suggestions={ARMOR_SUGGESTIONS[slot] ?? []}
                      />
                    </div>
                  )
                })}
              </div>

              <div className={styles.kitSectionLabel}>Items</div>
              <div className={styles.itemGridWrap}>
                <div className={styles.itemGrid}>
                  {kit.items.map((it, j) => (
                    <div
                      key={j}
                      className={`${styles.itemSlot} ${editingItem[i] === j ? styles.itemSlotSelected : ''}`}
                      onClick={() => setEditingItem(e => ({ ...e, [i]: e[i] === j ? null : j }))}
                      title={it.item || 'Empty'}
                    >
                      <span className={styles.itemSlotLabel}>{shortItemName(it.item)}</span>
                      {it.count > 1 && <span className={styles.itemSlotCount}>{it.count}</span>}
                    </div>
                  ))}
                  <div className={styles.itemSlot} onClick={() => addItem(i)} title="Add item">
                    <span className={styles.itemSlotAdd}>+</span>
                  </div>
                </div>
              </div>
              {editingItem[i] != null && kit.items[editingItem[i]] && (
                <div className={styles.itemEditBar}>
                  <SuggestInput
                    value={kit.items[editingItem[i]].item}
                    onChange={v => updateItem(i, editingItem[i], 'item', v)}
                    placeholder="minecraft:diamond"
                    listId={`item-${i}-${editingItem[i]}`}
                  />
                  <NumInput value={kit.items[editingItem[i]].count} onChange={v => updateItem(i, editingItem[i], 'count', v)} />
                  <button className={styles.listRemove} onClick={() => removeItem(i, editingItem[i])} type="button">✕</button>
                </div>
              )}
            </div>
          )}
        </div>
      ))}
      <button className={styles.listAdd} onClick={addKit} type="button">+ Add Kit</button>
    </div>
  )
}
