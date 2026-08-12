import { useState } from 'react'
import styles from './HardcoreLeaderboard.module.css'
import {
  IconDragonHead, IconSkull, IconClock, IconPlayerHead,
  IconHeartCracked, IconWolf, IconMedal, IconCrown, IconTombstone,
} from './admin/MinecraftIcons'

const RANK_COLORS = ['#FFD700', '#C0C0C0', '#CD7F32']

const TABS = [
  { id: 'fastestWins', label: 'Fastest Wins' },
  { id: 'longestRuns', label: 'Longest Runs' },
  { id: 'topPlayers',  label: 'Players' },
]

function fmtDur(ms) {
  const s = Math.max(0, Math.floor((ms ?? 0) / 1000))
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${sec}s`
  return `${sec}s`
}

export default function HardcoreLeaderboard({ data }) {
  const [tab, setTab] = useState('fastestWins')
  if (!data) return null

  const rec    = data.records || {}
  const active = data.active  || []
  const rows   = data[tab]    || []

  return (
    <section className={styles.wrap}>
      <div className={styles.header}>
        <span className={styles.title}>Hardcore Leaderboard</span>
        <div className={styles.tabs}>
          {TABS.map(t => (
            <button
              key={t.id}
              className={`${styles.tab} ${tab === t.id ? styles.tabActive : ''}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* Fun records + counters (everyone sees the same). Kept on top so the art is always visible. */}
      <div className={styles.recordsHeading}>Records</div>
      <div className={styles.records}>
        <Counter icon={<IconDragonHead size={16} />} label="Dragons slain" value={rec.dragonsSlain ?? 0} />
        <Counter icon={<IconTombstone size={16} />}  label="Body count"    value={rec.bodyCount ?? 0} />
        <Counter icon={<IconClock size={16} />}      label="Active runs"   value={active.length} />
        <Record icon={<IconSkull size={16} />}       label="Bloodiest run" entry={rec.bloodiestRun} sub={e => `${e.deaths} deaths`} />
        <Record icon={<IconHeartCracked size={16} />} label="Closest call" entry={rec.closestCall}  sub={e => `won at ${e.deaths} deaths`} />
        <Record icon={<IconPlayerHead size={16} />}  label="Biggest party" entry={rec.biggestParty} sub={e => `${e.peakPlayers} players`} />
        <Record icon={<IconWolf size={16} />}        label="Lone wolf"     entry={rec.loneWolf}     sub={e => `${fmtDur(e.durationMs)} solo`} />
        <PlayerRecord icon={<IconMedal size={16} />} label="Veteran"  stat={rec.veteran}  sub={s => `${fmtDur(s.totalRunMs)} survived`} />
        <PlayerRecord icon={<IconCrown size={16} />} label="Champion" stat={rec.champion} sub={s => `${s.wins} wins`} />
      </div>

      {/* Ranking table the tabs control. Bounded scroll box so it never pushes the records off. */}
      <div className={styles.rankingHeading}>Rankings</div>
      <div className={styles.body}>
        {tab === 'topPlayers' ? (
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.thRank}>#</th>
                <th className={styles.thName}>Player</th>
                <th className={styles.thNum}>Runs</th>
                <th className={styles.thNum}>W/L</th>
                <th className={styles.thNum}>Win%</th>
                <th className={styles.thNum}>Survived</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((e, i) => (
                <tr key={e.name + i} className={styles.tr}>
                  <td className={styles.rank} style={i < 3 ? { color: RANK_COLORS[i] } : undefined}>{i + 1}</td>
                  <td className={styles.name}>{e.name}</td>
                  <td className={styles.num}>{e.runs}</td>
                  <td className={styles.num}>{e.wins}/{e.losses}</td>
                  <td className={styles.num}>{e.winRatePct}%</td>
                  <td className={styles.num}>{fmtDur(e.totalRunMs)}</td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={6} className={styles.noData}>No data yet</td></tr>}
            </tbody>
          </table>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.thRank}>#</th>
                <th className={styles.thName}>Session</th>
                <th className={styles.thName}>By</th>
                <th className={styles.thNum}>Time</th>
                <th className={styles.thNum}>Deaths</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((e, i) => (
                <tr key={e.name + i} className={styles.tr}>
                  <td className={styles.rank} style={i < 3 ? { color: RANK_COLORS[i] } : undefined}>{i + 1}</td>
                  <td className={styles.name}>{e.name}</td>
                  <td className={styles.by}>{e.creator}</td>
                  <td className={styles.num}>{fmtDur(e.durationMs)}</td>
                  <td className={styles.num}>{e.deaths}</td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={5} className={styles.noData}>No finished runs yet</td></tr>}
            </tbody>
          </table>
        )}
      </div>

      {active.length > 0 && (
        <div className={styles.active}>
          <span className={styles.activeTitle}>Active</span>
          {active.map((a, i) => (
            <span key={a.name + i} className={styles.activePill}>
              {a.name} · {fmtDur(a.runMs)} · {a.alive} alive · {a.deaths}/{a.threshold}
            </span>
          ))}
        </div>
      )}
    </section>
  )
}

function Counter({ icon, label, value }) {
  return (
    <div className={styles.record}>
      <span className={styles.recordLabel}><span className={styles.recordIcon}>{icon}</span>{label}</span>
      <span className={styles.recordVal}>{value}</span>
    </div>
  )
}

function Record({ icon, label, entry, sub }) {
  return (
    <div className={styles.record}>
      <span className={styles.recordLabel}><span className={styles.recordIcon}>{icon}</span>{label}</span>
      <span className={styles.recordRight}>
        <span className={styles.recordName}>{entry ? entry.name : '-'}</span>
        <span className={styles.recordSub}>{entry ? sub(entry) : ''}</span>
      </span>
    </div>
  )
}

function PlayerRecord({ icon, label, stat, sub }) {
  return (
    <div className={styles.record}>
      <span className={styles.recordLabel}><span className={styles.recordIcon}>{icon}</span>{label}</span>
      <span className={styles.recordRight}>
        <span className={styles.recordName}>{stat ? stat.name : '-'}</span>
        <span className={styles.recordSub}>{stat ? sub(stat) : ''}</span>
      </span>
    </div>
  )
}
