import { useState, useEffect } from 'react'
import { Btn, Meter, SectionCard, Loading, EmptyState, ErrorBanner, Field, Input, Slider, Badge, useToast } from '../../ui'
import { IconSkills } from './MinecraftIcons'
import styles from './SkillsPanel.module.css'

const CATEGORIES = [
  { name: 'Industrial', skills: ['MINING', 'EXCAVATION', 'WOODCUTTING'] },
  { name: 'Nature',     skills: ['FARMING', 'FISHING', 'AGILITY'] },
  { name: 'Combat',     skills: ['MELEE', 'ARCHERY', 'DEFENSE'] },
  { name: 'Knowledge',  skills: ['ENCHANTING', 'ALCHEMY', 'TRADING'] },
]
const SKILL_LABELS = {
  MINING: 'Mining', EXCAVATION: 'Excavation', WOODCUTTING: 'Woodcutting',
  FARMING: 'Farming', FISHING: 'Fishing', AGILITY: 'Agility',
  MELEE: 'Melee', ARCHERY: 'Archery', DEFENSE: 'Defense',
  ENCHANTING: 'Enchanting', ALCHEMY: 'Alchemy', TRADING: 'Trading',
}
const SKILL_NAMES = ['mining', 'excavation', 'woodcutting', 'farming', 'fishing', 'agility', 'melee', 'archery', 'defense', 'enchanting', 'alchemy', 'trading']
const CFG_KEYS = [
  'skill_xp_exponent',
  ...SKILL_NAMES.map(s => `skill_cooldown_${s}`),
  ...SKILL_NAMES.map(s => `skill_unlock_${s}`),
  'cap_industrial_speed', 'cap_nature_health', 'cap_combat_damage', 'cap_knowledge_xp',
  'cap_double_drop', 'cap_defense_armor', 'cap_safe_landing',
]
// Every max below is the point where the value stops working, not a taste call:
// speed   34   - above ~35x sprint the server's "moved too quickly" check kicks the player
// health  502  - MAX_HEALTH attribute clamps at 1024 HP, and the config value is in hearts (x2)
// damage  2047 - ATTACK_DAMAGE clamps at 2048 and a bare-fisted player's base is 1.0
// xp      2.1e9 - the action-bar readout casts the awarded amount to int
// armor   30   - ARMOR attribute clamp, shared with worn gear
// drop/landing 1 - a probability and a damage fraction; both saturate at 1
// Sliders first, then the two whose real range is far too wide to drag. Grouping by control
// type keeps the grid symmetrical instead of alternating slider, box, slider.
const STAT_CAPS = [
  { key: 'cap_double_drop',      label: 'Double Drop Chance', hint: 'Chance per block, 1 = always',                   max: 1,   step: 0.05 },
  { key: 'cap_safe_landing',     label: 'Safe Landing',       hint: 'Fall damage absorbed, 1 = none',                 max: 1,   step: 0.05 },
  { key: 'cap_industrial_speed', label: 'Industrial Speed',   hint: 'Move speed, 0.5 = +50% faster',                  max: 34,  step: 0.05 },
  { key: 'cap_defense_armor',    label: 'Defense Armor',      hint: 'Armor points, 30 max with gear', max: 30,  step: 0.5 },
  { key: 'cap_nature_health',    label: 'Nature Health',      hint: 'Bonus hearts, ~81 hides the hotbar', max: 502, step: 0.5 },
  { key: 'cap_combat_damage',    label: 'Combat Damage',      hint: 'Attack damage, 1 = +100%',                       max: 2047,       step: 0.05, control: 'input' },
  { key: 'cap_knowledge_xp',     label: 'Knowledge XP',       hint: 'Other skills XP, 1 = double',          max: 2147483647, step: 0.05, control: 'input' },
]

export default function SkillsPanel({ token, onExpired }) {
  const [tab, setTab] = useState('settings')
  return (
    <div className={styles.wrap}>
      <div className={styles.subtabs}>
        <button className={`${styles.subtab} ${tab === 'players' ? styles.subtabOn : ''}`} onClick={() => setTab('players')}>Players</button>
        <button className={`${styles.subtab} ${tab === 'settings' ? styles.subtabOn : ''}`} onClick={() => setTab('settings')}>Settings</button>
      </div>
      {tab === 'players' ? <PlayersTab token={token} onExpired={onExpired} /> : <SettingsTab token={token} onExpired={onExpired} />}
    </div>
  )
}

function PlayersTab({ token, onExpired }) {
  const [allPlayers, setAllPlayers] = useState([])
  const [query, setQuery] = useState('')
  const [showSugg, setShowSugg] = useState(false)
  const [selected, setSelected] = useState(null)
  const [playerData, setPlayerData] = useState(null)
  const [editSkill, setEditSkill] = useState(null)
  const [editLevel, setEditLevel] = useState('')
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const auth = { Authorization: `Bearer ${token}` }
  const toast = useToast()

  useEffect(() => {
    fetch('/api/admin/skills/players', { headers: auth })
      .then(r => r.status === 401 ? (onExpired(), null) : r.json())
      .then(d => d && Array.isArray(d) && setAllPlayers(d))
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = allPlayers.filter(p => p.name.toLowerCase().includes(query.toLowerCase()) || p.uuid.startsWith(query)).slice(0, 20)

  async function selectPlayer(p) {
    setSelected(p); setQuery(p.name); setShowSugg(false); setPlayerData(null); setEditSkill(null); setLoading(true)
    try {
      const r = await fetch(`/api/admin/skills?player=${encodeURIComponent(p.uuid)}`, { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error); else setPlayerData(d)
    } catch { setError('Failed to load player data') }
    setLoading(false)
  }

  async function saveLevel(skill) {
    const level = parseInt(editLevel, 10)
    if (isNaN(level) || level < 0 || level > 100) { setError('Level must be 0 to 100'); return }
    setSaving(true)
    try {
      const r = await fetch('/api/admin/skills/set', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify({ uuid: selected.uuid, skill, level }) })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else { setPlayerData(prev => ({ ...prev, skills: { ...prev.skills, [skill]: { level: d.level, xp: d.xp } } })); setEditSkill(null); toast(`${SKILL_LABELS[skill]} → ${d.level}`) }
    } catch { setError('Failed to save') }
    setSaving(false)
  }

  return (
    <div className={styles.body}>
      <div className={styles.searchCombo}>
        <input className={styles.searchInput} placeholder="Search player name or UUID…" value={query}
          onChange={e => { setQuery(e.target.value); setSelected(null); setPlayerData(null); setShowSugg(true) }}
          onFocus={() => setShowSugg(true)} onBlur={() => setTimeout(() => setShowSugg(false), 150)} />
        {showSugg && query && !selected && filtered.length > 0 && (
          <div className={styles.suggestions}>
            {filtered.map(p => (
              <button key={p.uuid} className={styles.suggestion} onMouseDown={() => selectPlayer(p)}>
                <span>{p.name}</span><span className={styles.suggLevel}>Total {p.totalLevel}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      <ErrorBanner>{error}</ErrorBanner>

      {loading ? <Loading label="Loading skills…" />
        : !selected ? <EmptyState icon={<IconSkills size={30} />} label="Search for a player" hint="Pick a player to view and edit their 12 skill levels." />
        : playerData && (
          <div className={styles.playerBlock}>
            <div className={styles.playerHead}>
              <span className={styles.playerName}>{playerData.name}</span>
              <span className={styles.playerUuid}>{playerData.uuid}</span>
            </div>
            {CATEGORIES.map(cat => (
              <div key={cat.name} className={styles.catBlock}>
                <div className={styles.catLabel}>{cat.name}</div>
                <div className={styles.skillGrid}>
                  {cat.skills.map(skill => {
                    const s = playerData.skills[skill] || { level: 0, xp: 0 }
                    const isEditing = editSkill === skill
                    return (
                      <div key={skill} className={styles.skillCard}>
                        <div className={styles.skillTop}>
                          <span className={styles.skillName}>{SKILL_LABELS[skill]}</span>
                          <Badge variant={s.level >= 100 ? 'vip' : undefined}>Lv {s.level}</Badge>
                        </div>
                        <Meter value={s.level} max={100} color={s.level >= 100 ? 'var(--mauve)' : 'var(--mc-wood-light)'} />
                        {isEditing ? (
                          <div className={styles.editRow}>
                            <input className={styles.levelInput} type="number" value={editLevel} onChange={e => setEditLevel(e.target.value)} placeholder="0-100" min="0" max="100" autoFocus />
                            <Btn size="sm" variant="ok" disabled={saving} onClick={() => saveLevel(skill)}>{saving ? '…' : 'Save'}</Btn>
                            <Btn size="sm" variant="ghost" onClick={() => setEditSkill(null)}>×</Btn>
                          </div>
                        ) : (
                          <div className={styles.skillFoot}>
                            <span className={styles.xp}>{Math.round(s.xp).toLocaleString()} XP</span>
                            <Btn size="sm" onClick={() => { setEditSkill(skill); setEditLevel(String(s.level)) }}>Edit</Btn>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
    </div>
  )
}

function SettingsTab({ token, onExpired }) {
  const [cfg, setCfg] = useState(null)
  const [draft, setDraft] = useState({})
  const [cat0, setCat0] = useState(CATEGORIES[0].name)
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState('')
  const [loadErr, setLoadErr] = useState('')
  const auth = { Authorization: `Bearer ${token}` }

  useEffect(() => {
    fetch('/api/admin/config', { headers: auth })
      .then(r => { if (r.status === 401 || r.status === 403) { onExpired(); throw new Error('session') } if (!r.ok) throw new Error('server'); return r.json() })
      .then(d => { setCfg(d); setDraft(d) })
      .catch(e => { if (e.message !== 'session') setLoadErr('Failed to load config.') })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function patch(key, val) { setDraft(d => ({ ...d, [key]: val })) }
  const isDirty = cfg && CFG_KEYS.some(k => String(draft[k]) !== String(cfg[k]))

  async function save() {
    const payload = {}
    for (const k of CFG_KEYS) { if (draft[k] === undefined) continue; if (String(draft[k]) !== String(cfg[k])) payload[k] = draft[k] }
    setSaving(true); setMsg('')
    try {
      const r = await fetch('/api/admin/config', { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth }, body: JSON.stringify(payload) })
      if (r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (!r.ok) setMsg(d.error || 'Save failed.')
      else { setMsg(`Saved ${d.changed} field(s).`); setCfg(c => ({ ...c, ...payload })) }
    } catch { setMsg('Could not reach server.') }
    setSaving(false)
  }

  if (!cfg) return loadErr ? <ErrorBanner>{loadErr}</ErrorBanner> : <Loading label="Loading settings…" />

  const expo = draft.skill_xp_exponent ?? 1.5
  const cat = CATEGORIES.find(c => c.name === cat0) ?? CATEGORIES[0]

  return (
    <div className={styles.settingsWrap}>
      <div className={styles.settingsScroll}>
        <SectionCard title="Progression">
          <p className={styles.note}>
            Each cap is the value at level 100, scaling linearly from 0. Set <b>0</b> to switch a perk off.
          </p>
          {/* Sliders here put their value beside the label rather than to the right of the
              track. These cells are narrow, and a value on the track's right was taking a
              third of the width from the control itself. */}
          <div className={styles.capGrid}>
            <Field label="XP Exponent" hint="Higher is slower past level 10" aside={expo}>
              <Slider hideValue value={expo} min={1} max={Math.max(7.9, expo)} step={0.05}
                onChange={v => patch('skill_xp_exponent', v)} />
            </Field>
            {STAT_CAPS.map(({ key, label, hint, max, step, control }) => {
              const v = draft[key] ?? 0
              const isInput = control === 'input'
              return (
                <Field key={key} label={label} hint={hint} aside={isInput ? undefined : v}>
                  {isInput
                    ? <Input type="number" min="0" max={max} step={step} value={draft[key] ?? ''} onChange={e => patch(key, parseFloat(e.target.value))} />
                    : <Slider hideValue value={v} min={0} max={Math.max(max, v)} step={step} onChange={n => patch(key, n)} />}
                </Field>
              )
            })}
          </div>
        </SectionCard>

        <SectionCard title="Abilities" actions={
          <div className={styles.catTabs}>
            {CATEGORIES.map(c => (
              <button key={c.name} className={`${styles.catTab} ${c.name === cat.name ? styles.catTabOn : ''}`}
                onClick={() => setCat0(c.name)}>{c.name}</button>
            ))}
          </div>
        }>
          <p className={styles.note}>
            Cooldown shortens as the skill levels, to 25% by level 100.
            <b>0</b> means no cooldown, or usable from the start.
          </p>
          <div className={styles.skillTableHead}><span /><span>Cooldown (s)</span><span>Unlock Lvl</span></div>
          {cat.skills.map(skill => {
            const s = skill.toLowerCase()
            const unlock = draft[`skill_unlock_${s}`] ?? 0
            return (
              <div key={skill} className={styles.skillTableRow}>
                <span className={styles.rowLabel}>{SKILL_LABELS[skill]}</span>
                <Input type="number" min="0" max="3600" step="1" value={draft[`skill_cooldown_${s}`] ?? ''} onChange={e => patch(`skill_cooldown_${s}`, parseInt(e.target.value))} />
                <Slider value={unlock} min={0} max={100} step={1} onChange={v => patch(`skill_unlock_${s}`, v)} />
              </div>
            )
          })}
        </SectionCard>
      </div>

      <div className={styles.saveBar}>
        {msg && <span className={msg.startsWith('Saved') ? styles.msgOk : styles.msgErr}>{msg}</span>}
        {isDirty && <span className={styles.dirty}>Unsaved changes</span>}
        <Btn variant="primary" onClick={save} disabled={saving || !isDirty}>{saving ? 'Saving…' : 'Save & Reload'}</Btn>
      </div>
    </div>
  )
}
