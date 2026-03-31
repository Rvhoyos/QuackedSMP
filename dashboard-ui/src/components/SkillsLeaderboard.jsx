import { useState } from 'react'
import styles from './SkillsLeaderboard.module.css'

const CATEGORIES = [
  { id: 'overall',    label: 'Overall',    skills: null },
  { id: 'INDUSTRIAL', label: 'Industrial', skills: ['MINING', 'EXCAVATION', 'WOODCUTTING'] },
  { id: 'NATURE',     label: 'Nature',     skills: ['FARMING', 'FISHING', 'AGILITY'] },
  { id: 'COMBAT',     label: 'Combat',     skills: ['MELEE', 'ARCHERY', 'DEFENSE'] },
  { id: 'KNOWLEDGE',  label: 'Knowledge',  skills: ['ENCHANTING', 'ALCHEMY', 'TRADING'] },
]

const SKILL_LABELS = {
  MINING: 'Mining', EXCAVATION: 'Excavation', WOODCUTTING: 'Woodcutting',
  FARMING: 'Farming', FISHING: 'Fishing', AGILITY: 'Agility',
  MELEE: 'Melee', ARCHERY: 'Archery', DEFENSE: 'Defense',
  ENCHANTING: 'Enchanting', ALCHEMY: 'Alchemy', TRADING: 'Trading',
}

const RANK_COLORS = ['#FFD700', '#C0C0C0', '#CD7F32']

export default function SkillsLeaderboard({ data }) {
  const [tab, setTab] = useState('overall')

  if (!data) return null

  const active = CATEGORIES.find(c => c.id === tab)

  return (
    <section className={styles.wrap}>
      <div className={styles.header}>
        <span className={styles.title}>Skills Leaderboard</span>
        <div className={styles.tabs}>
          {CATEGORIES.map(c => (
            <button
              key={c.id}
              className={`${styles.tab} ${tab === c.id ? styles.tabActive : ''}`}
              onClick={() => setTab(c.id)}
            >
              {c.label}
            </button>
          ))}
        </div>
      </div>

      <div className={styles.body}>
        {tab === 'overall' ? (
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.thRank}>#</th>
                <th className={styles.thPlayer}>Player</th>
                <th className={styles.thLevel}>Total Level</th>
                <th className={styles.thPlaytime}>Playtime</th>
                <th className={styles.thTier}>Tier</th>
              </tr>
            </thead>
            <tbody>
              {data.overall.map((entry, i) => (
                <tr key={entry.name + i} className={styles.tr}>
                  <td className={styles.rank} style={i < 3 ? { color: RANK_COLORS[i] } : undefined}>
                    {i + 1}
                  </td>
                  <td className={styles.playerName}>{entry.name}</td>
                  <td className={styles.level}>{entry.level}</td>
                  <td className={styles.playtime}>
                    {entry.playtime_ticks != null ? `${Math.floor(entry.playtime_ticks / 72000)}h` : '—'}
                  </td>
                  <td className={styles.tier}>
                    {entry.tier > 0 ? <span className={styles.tierBadge}>T{entry.tier}</span> : '—'}
                  </td>
                </tr>
              ))}
              {data.overall.length === 0 && (
                <tr><td colSpan={5} className={styles.noData}>No data yet</td></tr>
              )}
            </tbody>
          </table>
        ) : (
          <div className={styles.skillsGrid}>
            {(active?.skills ?? []).map(skillId => {
              const entries = data.bySkill[skillId] || []
              return (
                <div key={skillId} className={styles.skillCard}>
                  <div className={styles.skillTitle}>{SKILL_LABELS[skillId]}</div>
                  {entries.length === 0 ? (
                    <div className={styles.noData}>No data</div>
                  ) : (
                    <div className={styles.skillRows}>
                      {entries.map((entry, i) => (
                        <div key={entry.name + i} className={styles.skillRow}>
                          <span
                            className={styles.skillRank}
                            style={i < 3 ? { color: RANK_COLORS[i] } : undefined}
                          >
                            {i + 1}
                          </span>
                          <span className={styles.skillPlayer}>{entry.name}</span>
                          <span className={styles.skillLevel}>Lv {entry.level}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}
