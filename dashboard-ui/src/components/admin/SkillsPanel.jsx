import { useState, useEffect, useCallback } from 'react'
import styles from './SkillsPanel.module.css'

// ── Constants ─────────────────────────────────────────────────────────────────

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

const SKILL_NAMES = [
  'mining', 'excavation', 'woodcutting',
  'farming', 'fishing', 'agility',
  'melee', 'archery', 'defense',
  'enchanting', 'alchemy', 'trading',
]

const CFG_KEYS = [
  'skill_xp_exponent',
  ...SKILL_NAMES.map(s => `skill_cooldown_${s}`),
  ...SKILL_NAMES.map(s => `skill_unlock_${s}`),
  'cap_industrial_speed', 'cap_nature_health', 'cap_combat_damage', 'cap_knowledge_xp',
  'cap_double_drop', 'cap_defense_armor', 'cap_safe_landing',
]

const STAT_CAPS = [
  { key: 'cap_industrial_speed', label: 'Industrial Speed',   hint: 'Max movement speed bonus at level 100 (0.5 = +50%)',          step: 0.05 },
  { key: 'cap_nature_health',    label: 'Nature Health',      hint: 'Max bonus hearts at level 100 (10.0 = +10 hearts)',            step: 0.5  },
  { key: 'cap_combat_damage',    label: 'Combat Damage',      hint: 'Max bonus damage multiplier at level 100 (1.0 = +100%)',       step: 0.05 },
  { key: 'cap_knowledge_xp',     label: 'Knowledge XP',       hint: 'Max bonus XP orb multiplier at level 100 (1.0 = +100%)',      step: 0.05 },
  { key: 'cap_double_drop',      label: 'Double Drop Chance', hint: 'Max double drop chance at Industrial level 100 (0.5 = 50%)',   step: 0.05 },
  { key: 'cap_defense_armor',    label: 'Defense Armor',      hint: 'Max bonus armor points at Defense level 100 (10.0 = +10)',     step: 0.5  },
  { key: 'cap_safe_landing',     label: 'Safe Landing',       hint: 'Max fall damage absorbed at Agility level 100 (1.0 = 100%)',  step: 0.05 },
]

// ── Main component ────────────────────────────────────────────────────────────

export default function SkillsPanel({ token, onExpired }) {
  const [activeTab, setActiveTab] = useState('settings')

  return (
    <div className={styles.wrap}>
      <div className={styles.subtabs}>
        <button
          className={`${styles.subtab} ${activeTab === 'players' ? styles.subtabActive : ''}`}
          onClick={() => setActiveTab('players')}
        >Players</button>
        <button
          className={`${styles.subtab} ${activeTab === 'settings' ? styles.subtabActive : ''}`}
          onClick={() => setActiveTab('settings')}
        >Settings</button>
      </div>

      {activeTab === 'players'  && <PlayersTab  token={token} onExpired={onExpired} />}
      {activeTab === 'settings' && <SettingsTab token={token} onExpired={onExpired} />}
    </div>
  )
}

// ── Players tab ───────────────────────────────────────────────────────────────

function PlayersTab({ token, onExpired }) {
  const [allPlayers, setAllPlayers] = useState([])
  const [query,      setQuery]      = useState('')
  const [showSugg,   setShowSugg]   = useState(false)
  const [selected,   setSelected]   = useState(null)
  const [playerData, setPlayerData] = useState(null)
  const [editSkill,  setEditSkill]  = useState(null)
  const [editLevel,  setEditLevel]  = useState('')
  const [saving,     setSaving]     = useState(false)
  const [loading,    setLoading]    = useState(false)
  const [error,      setError]      = useState(null)

  const auth = { Authorization: `Bearer ${token}` }

  useEffect(() => {
    fetch('/api/admin/skills/players', { headers: auth })
      .then(r => r.status === 401 ? (onExpired(), null) : r.json())
      .then(d => d && Array.isArray(d) && setAllPlayers(d))
      .catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = allPlayers
    .filter(p => p.name.toLowerCase().includes(query.toLowerCase()) || p.uuid.startsWith(query))
    .slice(0, 20)

  async function selectPlayer(p) {
    setSelected(p); setQuery(p.name); setShowSugg(false)
    setPlayerData(null); setEditSkill(null); setLoading(true)
    try {
      const r = await fetch(`/api/admin/skills?player=${encodeURIComponent(p.uuid)}`, { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) { setError(d.error) } else { setPlayerData(d) }
    } catch { setError('Failed to load player data') }
    setLoading(false)
  }

  async function saveLevel(skill) {
    const level = parseInt(editLevel, 10)
    if (isNaN(level) || level < 0 || level > 100) { setError('Level must be 0–100'); return }
    setSaving(true)
    try {
      const r = await fetch('/api/admin/skills/set', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify({ uuid: selected.uuid, skill, level }),
      })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else {
        setPlayerData(prev => ({ ...prev, skills: { ...prev.skills, [skill]: { level: d.level, xp: d.xp } } }))
        setEditSkill(null)
      }
    } catch { setError('Failed to save') }
    setSaving(false)
  }

  return (
    <>
      <div className={styles.searchBox}>
        <div className={styles.sectionTitle}>Skills Editor</div>
        <div className={styles.searchRow}>
          <div className={styles.searchCombo}>
            <input
              className={styles.input}
              placeholder="Search player name or UUID..."
              value={query}
              onChange={e => { setQuery(e.target.value); setSelected(null); setPlayerData(null); setShowSugg(true) }}
              onFocus={() => setShowSugg(true)}
              onBlur={() => setTimeout(() => setShowSugg(false), 150)}
            />
            {showSugg && query && !selected && filtered.length > 0 && (
              <div className={styles.suggestions}>
                {filtered.map(p => (
                  <button key={p.uuid} className={styles.suggestion} onMouseDown={() => selectPlayer(p)}>
                    <span className={styles.suggName}>{p.name}</span>
                    <span className={styles.suggLevel}>Total Lv {p.totalLevel}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {error && (
        <div className={styles.error}>
          {error}
          <button className={styles.dismiss} onClick={() => setError(null)}>x</button>
        </div>
      )}

      {!selected && !loading && (
        <div className={styles.empty}>Search for a player to view and edit their skills.</div>
      )}
      {loading && <div className={styles.empty}>Loading...</div>}

      {playerData && !loading && (
        <div className={styles.playerSkills}>
          <div className={styles.playerHeader}>
            <span className={styles.playerName}>{playerData.name}</span>
            <span className={styles.playerUuid}>{playerData.uuid}</span>
          </div>
          <div className={styles.categories}>
            {CATEGORIES.map(cat => (
              <div key={cat.name} className={styles.category}>
                <div className={styles.catTitle}>{cat.name}</div>
                <div className={styles.skillCards}>
                  {cat.skills.map(skill => {
                    const s = playerData.skills[skill] || { level: 0, xp: 0 }
                    const isEditing = editSkill === skill
                    return (
                      <div key={skill} className={styles.skillCard}>
                        <div className={styles.skillName}>{SKILL_LABELS[skill]}</div>
                        <div className={styles.skillLevel}>Lv {s.level}</div>
                        {isEditing ? (
                          <div className={styles.editRow}>
                            <input
                              className={styles.xpInput}
                              type="number"
                              value={editLevel}
                              onChange={e => setEditLevel(e.target.value)}
                              placeholder="Level (0–100)"
                              min="0" max="100" autoFocus
                            />
                            <button className={styles.btn} disabled={saving} onClick={() => saveLevel(skill)}>
                              {saving ? '...' : 'Save'}
                            </button>
                            <button className={styles.btnGhost} onClick={() => setEditSkill(null)}>Cancel</button>
                          </div>
                        ) : (
                          <div className={styles.skillXpRow}>
                            <span className={styles.skillXp}>{Math.round(s.xp).toLocaleString()} XP</span>
                            <button
                              className={styles.btnGhost}
                              onClick={() => { setEditSkill(skill); setEditLevel(String(s.level)) }}
                            >Edit</button>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  )
}

// ── Settings tab ──────────────────────────────────────────────────────────────

function SettingsTab({ token, onExpired }) {
  const [cfg,     setCfg]     = useState(null)
  const [draft,   setDraft]   = useState({})
  const [saving,  setSaving]  = useState(false)
  const [msg,     setMsg]     = useState('')
  const [loadErr, setLoadErr] = useState('')

  const auth = { Authorization: `Bearer ${token}` }

  useEffect(() => {
    fetch('/api/admin/config', { headers: auth })
      .then(r => {
        if (r.status === 401 || r.status === 403) { onExpired(); throw new Error('session') }
        if (!r.ok) throw new Error('server')
        return r.json()
      })
      .then(d => { setCfg(d); setDraft(d) })
      .catch(e => { if (e.message !== 'session') setLoadErr('Failed to load config.') })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function patch(key, val) { setDraft(d => ({ ...d, [key]: val })) }

  const isDirty = cfg && CFG_KEYS.some(k => String(draft[k]) !== String(cfg[k]))

  async function save() {
    const payload = {}
    for (const k of CFG_KEYS) {
      if (draft[k] === undefined) continue
      if (String(draft[k]) !== String(cfg[k])) payload[k] = draft[k]
    }
    setSaving(true); setMsg('')
    try {
      const r = await fetch('/api/admin/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...auth },
        body: JSON.stringify(payload),
      })
      if (r.status === 403) { onExpired(); return }
      const d = await r.json()
      if (!r.ok) setMsg(d.error || 'Save failed.')
      else { setMsg(`Saved ${d.changed} field(s).`); setCfg(c => ({ ...c, ...payload })) }
    } catch { setMsg('Could not reach server.') }
    setSaving(false)
  }

  if (!cfg) return <div className={styles.empty}>{loadErr || 'Loading…'}</div>

  return (
    <div className={styles.cfgWrap}>
      <div className={styles.cfgScroll}>

        {/* XP Scaling */}
        <div className={styles.cfgSection}>XP Scaling</div>
        <div className={styles.cfgRow}>
          <div className={styles.cfgLeft}>
            <span className={styles.cfgLabel}>XP Exponent</span>
            <span className={styles.cfgHint}>Higher = slower level scaling. Default 1.5, change carefully.</span>
          </div>
          <input className={styles.numInput} type="number" step="0.05"
            value={draft.skill_xp_exponent ?? ''}
            onChange={e => patch('skill_xp_exponent', parseFloat(e.target.value))} />
        </div>

        {/* Per-skill cooldowns and unlock levels */}
        {CATEGORIES.map(cat => (
          <div key={cat.name}>
            <div className={styles.cfgSection}>{cat.name} Skills</div>
            <div className={styles.skillTableHead}>
              <span />
              <span className={styles.skillColLabel}>Cooldown</span>
              <span className={styles.skillColLabel}>Unlock Lvl</span>
            </div>
            {cat.skills.map(skill => {
              const s = skill.toLowerCase()
              return (
                <div key={skill} className={styles.skillTableRow}>
                  <span className={styles.cfgLabel}>{SKILL_LABELS[skill]}</span>
                  <div className={styles.numWrap}>
                    <input className={styles.numInput} type="number" step="1"
                      value={draft[`skill_cooldown_${s}`] ?? ''}
                      onChange={e => patch(`skill_cooldown_${s}`, parseInt(e.target.value))} />
                    <span className={styles.suffix}>s</span>
                  </div>
                  <input className={styles.numInput} type="number" step="1"
                    value={draft[`skill_unlock_${s}`] ?? ''}
                    onChange={e => patch(`skill_unlock_${s}`, parseInt(e.target.value))} />
                </div>
              )
            })}
          </div>
        ))}

        {/* Passive stat caps */}
        <div className={styles.cfgSection}>Passive Stat Caps</div>
        {STAT_CAPS.map(({ key, label, hint, step }) => (
          <div key={key} className={styles.cfgRow}>
            <div className={styles.cfgLeft}>
              <span className={styles.cfgLabel}>{label}</span>
              <span className={styles.cfgHint}>{hint}</span>
            </div>
            <input className={styles.numInput} type="number" step={step}
              value={draft[key] ?? ''}
              onChange={e => patch(key, parseFloat(e.target.value))} />
          </div>
        ))}

      </div>

      <div className={styles.cfgFooter}>
        {msg && (
          <span className={`${styles.cfgMsg} ${msg.startsWith('Saved') ? styles.cfgOk : styles.cfgErr}`}>
            {msg}
          </span>
        )}
        {isDirty && <span className={styles.cfgDirty}>Unsaved changes</span>}
        <button className={styles.btn} onClick={save} disabled={saving || !isDirty}>
          {saving ? 'Saving…' : 'Save & Reload'}
        </button>
      </div>
    </div>
  )
}
