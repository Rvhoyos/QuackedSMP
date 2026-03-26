import styles from './MetricsPanel.module.css'
import { IconClock, IconSword, IconFurnace, IconPlayerHead } from './admin/MinecraftIcons'

function tpsColor(val) {
  if (val == null) return 'var(--overlay0)'
  if (val >= 18)   return 'var(--green)'
  if (val >= 14)   return 'var(--yellow)'
  return 'var(--red)'
}

function fmt(val, decimals = 1) {
  return val != null ? val.toFixed(decimals) : '—'
}

function pct(val) {
  return val != null ? `${(val * 100).toFixed(1)}%` : '—'
}

export default function MetricsPanel({ tps, cpu, mspt, online }) {
  const tpsColor5s = tpsColor(tps?.tps5s)

  return (
    <div className={styles.grid}>
      {/* TPS */}
      <div className={styles.card}>
        <div className={styles.cardTop}>
          <span className={styles.cardIcon}><IconClock size={22} /></span>
          <span className={styles.cardLabel}>TPS</span>
        </div>
        <div className={styles.cardPrimary} style={{ color: tpsColor5s }}>
          {fmt(tps?.tps5s)}
        </div>
        <div className={styles.cardSub}>
          <SubStat label="10s" value={fmt(tps?.tps10s)} color={tpsColor(tps?.tps10s)} />
          <SubStat label="1m"  value={fmt(tps?.tps1m)}  color={tpsColor(tps?.tps1m)} />
          <SubStat label="5m"  value={fmt(tps?.tps5m)}  color={tpsColor(tps?.tps5m)} />
        </div>
        <div className={styles.cardWindow}>5s window</div>
      </div>

      {/* MSPT */}
      <div className={styles.card}>
        <div className={styles.cardTop}>
          <span className={styles.cardIcon}><IconSword size={22} /></span>
          <span className={styles.cardLabel}>MSPT</span>
        </div>
        <div className={styles.cardPrimary} style={{ color: msptColor(mspt?.mean10s) }}>
          {fmt(mspt?.mean10s)}
          <span className={styles.cardUnit}>ms</span>
        </div>
        <div className={styles.cardSub}>
          <SubStat label="max 10s" value={`${fmt(mspt?.max10s)}ms`} />
          <SubStat label="avg 1m"  value={`${fmt(mspt?.mean1m)}ms`} />
        </div>
        <div className={styles.cardWindow}>mean 10s</div>
      </div>

      {/* CPU */}
      <div className={styles.card}>
        <div className={styles.cardTop}>
          <span className={styles.cardIcon}><IconFurnace size={22} /></span>
          <span className={styles.cardLabel}>CPU</span>
        </div>
        <div className={styles.cardPrimary} style={{ color: cpuColor(cpu?.cpu10s) }}>
          {pct(cpu?.cpu10s)}
        </div>
        <div className={styles.cardSub}>
          <SubStat label="1m"  value={pct(cpu?.cpu1m)} />
          <SubStat label="15m" value={pct(cpu?.cpu15m)} />
        </div>
        <div className={styles.cardWindow}>10s window</div>
      </div>

      {/* Players */}
      <div className={styles.card}>
        <div className={styles.cardTop}>
          <span className={styles.cardIcon}><IconPlayerHead size={22} /></span>
          <span className={styles.cardLabel}>Players</span>
        </div>
        <div className={styles.cardPrimary} style={{ color: 'var(--blue)' }}>
          {online ?? '—'}
        </div>
        <div className={styles.cardSub}>
          <SubStat label="online now" />
        </div>
        <div className={styles.cardWindow}>live count</div>
      </div>
    </div>
  )
}

function SubStat({ label, value, color }) {
  return (
    <div className={styles.subStat}>
      <span className={styles.subLabel}>{label}</span>
      {value != null && (
        <span className={styles.subValue} style={color ? { color } : undefined}>
          {value}
        </span>
      )}
    </div>
  )
}

function msptColor(val) {
  if (val == null) return 'var(--overlay0)'
  if (val <= 40)   return 'var(--green)'
  if (val <= 80)   return 'var(--yellow)'
  return 'var(--red)'
}

function cpuColor(val) {
  if (val == null) return 'var(--overlay0)'
  if (val <= 0.5)  return 'var(--green)'
  if (val <= 0.8)  return 'var(--yellow)'
  return 'var(--red)'
}
