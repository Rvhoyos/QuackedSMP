import { useState, useEffect, useCallback } from 'react'
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

export default function SkillsPanel({ token, onExpired }) {
  const [allPlayers, setAllPlayers]   = useState([])
  const [query,      setQuery]        = useState('')
  const [showSugg,   setShowSugg]     = useState(false)
  const [selected,   setSelected]     = useState(null)
  const [playerData, setPlayerData]   = useState(null)
  const [editSkill,  setEditSkill]    = useState(null)
  const [editLevel,  setEditLevel]    = useState('')
  const [saving,     setSaving]       = useState(false)
  const [loading,    setLoading]      = useState(false)
  const [error,      setError]        = useState(null)

  const auth = { 'Authorization': `Bearer ${token}` }

  useEffect(() => {
    fetch('/api/admin/skills/players', { headers: auth })
      .then(r => r.status === 401 ? (onExpired(), null) : r.json())
      .then(d => d && Array.isArray(d) && setAllPlayers(d))
      .catch(() => {})
  }, [])

  const filtered = allPlayers
    .filter(p => p.name.toLowerCase().includes(query.toLowerCase()) || p.uuid.startsWith(query))
    .slice(0, 20)

  async function selectPlayer(p) {
    setSelected(p)
    setQuery(p.name)
    setShowSugg(false)
    setPlayerData(null)
    setEditSkill(null)
    setLoading(true)
    try {
      const r = await fetch(`/api/admin/skills?player=${encodeURIComponent(p.uuid)}`, { headers: auth })
      if (r.status === 401) { onExpired(); return }
      const d = await r.json()
      if (d.error) setError(d.error)
      else setPlayerData(d)
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
    <div className={styles.wrap}>
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
                              min="0"
                              max="100"
                              autoFocus
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
    </div>
  )
}
