import styles from './MetricsPanel.module.css'
import { IconClock, IconSword, IconFurnace, IconPlayerHead } from './admin/MinecraftIcons'

function tpsColor(val) {
  if (val == null) return 'var(--overlay0)'
  if (val >= 18)   return 'var(--green)'
  if (val >= 14)   return 'var(--yellow)'
  return 'var(--red)'
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

function ramColor(used, max) {
  if (used == null || max == null || max === 0) return 'var(--overlay0)'
  const pct = used / max
  if (pct <= 0.6)  return 'var(--green)'
  if (pct <= 0.85) return 'var(--yellow)'
  return 'var(--red)'
}

function diskColor(used, total) {
  if (used == null || total == null || total === 0) return 'var(--overlay0)'
  const pct = used / total
  if (pct <= 0.7)  return 'var(--green)'
  if (pct <= 0.9)  return 'var(--yellow)'
  return 'var(--red)'
}

function fmt(val, decimals = 1) {
  return val != null ? val.toFixed(decimals) : '—'
}

function pct(val) {
  return val != null ? `${(val * 100).toFixed(1)}%` : '—'
}

function fmtBytes(bytes) {
  if (bytes == null) return '—'
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(0)} MB`
  return `${(bytes / 1e3).toFixed(0)} KB`
}

function fmtUptime(ms) {
  if (ms == null) return '—'
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (h > 0) return `${h}h ${m}m`
  return `${m}m`
}

export default function MetricsPanel({ tps, cpu, mspt, online, sys }) {
  const tpsColor5s = tpsColor(tps?.tps5s)

  const heapUsed  = sys?.heapUsed  ?? null
  const heapMax   = sys?.heapMax   ?? null
  const diskTotal = sys?.diskTotal ?? null
  const diskUsable = sys?.diskUsable ?? null
  const diskUsed  = (diskTotal != null && diskUsable != null) ? diskTotal - diskUsable : null
  const heapPct   = (heapUsed != null && heapMax != null && heapMax > 0) ? heapUsed / heapMax : null
  const diskPct   = (diskUsed != null && diskTotal != null && diskTotal > 0) ? diskUsed / diskTotal : null

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
          {sys?.uptimeMs != null && <SubStat label="uptime" value={fmtUptime(sys.uptimeMs)} />}
          {sys?.threads  != null && <SubStat label="threads" value={String(sys.threads)} />}
        </div>
        <div className={styles.cardWindow}>live count</div>
      </div>

      {/* RAM */}
      <div className={styles.card}>
        <div className={styles.cardTop}>
          <span className={styles.cardIcon}>
            <svg viewBox="0 0 22 22" width={22} height={22} shapeRendering="crispEdges">
              <rect x="1" y="6" width="20" height="10" rx="2" fill="#2A4A7A" />
              <rect x="1" y="6" width="20" height="10" rx="2" stroke="#3A6AB0" strokeWidth="1.5" fill="none" />
              <rect x="3"  y="9" width="3" height="4" rx="0.5" fill="#5B8FD4" />
              <rect x="7"  y="9" width="3" height="4" rx="0.5" fill="#5B8FD4" />
              <rect x="11" y="9" width="3" height="4" rx="0.5" fill="#5B8FD4" />
              <rect x="15" y="9" width="3" height="4" rx="0.5" fill="#5B8FD4" />
              <rect x="5"  y="15" width="1" height="2" fill="#3A6AB0" />
              <rect x="9"  y="15" width="1" height="2" fill="#3A6AB0" />
              <rect x="13" y="15" width="1" height="2" fill="#3A6AB0" />
              <rect x="17" y="15" width="1" height="2" fill="#3A6AB0" />
            </svg>
          </span>
          <span className={styles.cardLabel}>RAM</span>
        </div>
        <div className={styles.cardPrimary} style={{ color: ramColor(heapUsed, heapMax) }}>
          {fmtBytes(heapUsed)}
        </div>
        <div className={styles.bar}>
          <div
            className={styles.barFill}
            style={{
              width: heapPct != null ? `${(heapPct * 100).toFixed(1)}%` : '0%',
              background: ramColor(heapUsed, heapMax),
            }}
          />
        </div>
        <div className={styles.cardSub}>
          <SubStat label="max" value={fmtBytes(heapMax)} />
          {heapPct != null && <SubStat label="used" value={`${(heapPct * 100).toFixed(0)}%`} color={ramColor(heapUsed, heapMax)} />}
        </div>
        <div className={styles.cardWindow}>JVM heap</div>
      </div>

      {/* Disk */}
      <div className={styles.card}>
        <div className={styles.cardTop}>
          <span className={styles.cardIcon}>
            <svg viewBox="0 0 22 22" width={22} height={22} shapeRendering="crispEdges">
              <ellipse cx="11" cy="7"  rx="9" ry="4" fill="#3A2A1A" stroke="#7A5A3A" strokeWidth="1.2" />
              <rect x="2" y="7" width="18" height="8" fill="#3A2A1A" />
              <rect x="2" y="7" width="18" height="1" fill="#5A3A1A" />
              <ellipse cx="11" cy="15" rx="9" ry="4" fill="#2A1A0A" stroke="#7A5A3A" strokeWidth="1.2" />
              <ellipse cx="11" cy="7"  rx="5" ry="2" fill="#5A3A1A" />
              <circle cx="15" cy="15" r="1.5" fill="#CC9040" />
              <circle cx="15" cy="15" r="0.7" fill="#FFCC60" />
            </svg>
          </span>
          <span className={styles.cardLabel}>Disk</span>
        </div>
        <div className={styles.cardPrimary} style={{ color: diskColor(diskUsed, diskTotal) }}>
          {fmtBytes(diskUsed)}
        </div>
        <div className={styles.bar}>
          <div
            className={styles.barFill}
            style={{
              width: diskPct != null ? `${(diskPct * 100).toFixed(1)}%` : '0%',
              background: diskColor(diskUsed, diskTotal),
            }}
          />
        </div>
        <div className={styles.cardSub}>
          <SubStat label="free"  value={fmtBytes(diskUsable)} />
          <SubStat label="total" value={fmtBytes(diskTotal)} />
        </div>
        <div className={styles.cardWindow}>disk space</div>
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
