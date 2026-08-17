import styles from './MetricsPanel.module.css'
import { IconClock, IconSpeedometer, IconFurnace, IconPlayerHead, IconPulse } from './admin/MinecraftIcons'

// ── Threshold colors ──────────────────────────────────────────────────────────
function tpsColor(v)  { if (v == null) return 'var(--overlay0)'; return v >= 18 ? 'var(--green)' : v >= 14 ? 'var(--yellow)' : 'var(--red)' }
function msptColor(v) { if (v == null) return 'var(--overlay0)'; return v <= 40 ? 'var(--green)' : v <= 80 ? 'var(--yellow)' : 'var(--red)' }
function cpuColor(v)  { if (v == null) return 'var(--overlay0)'; return v <= 0.5 ? 'var(--green)' : v <= 0.8 ? 'var(--yellow)' : 'var(--red)' }
function pctColor(p)  { if (p == null) return 'var(--overlay0)'; return p <= 0.6 ? 'var(--green)' : p <= 0.85 ? 'var(--yellow)' : 'var(--red)' }
function diskColorP(p){ if (p == null) return 'var(--overlay0)'; return p <= 0.7 ? 'var(--green)' : p <= 0.9 ? 'var(--yellow)' : 'var(--red)' }

// ── Formatters ─────────────────────────────────────────────────────────────────
const fmt = (v, d = 1) => (v != null ? v.toFixed(d) : '—')
const pctStr = (v) => (v != null ? `${(v * 100).toFixed(1)}%` : '—')
function fmtBytes(b) {
  if (b == null) return '—'
  if (b >= 1e9) return `${(b / 1e9).toFixed(1)} GB`
  if (b >= 1e6) return `${(b / 1e6).toFixed(0)} MB`
  return `${(b / 1e3).toFixed(0)} KB`
}
function fmtUptime(ms) {
  if (ms == null) return '—'
  const s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}
const clamp01 = (n) => Math.max(0, Math.min(1, n))

// ── 270° ring gauge ────────────────────────────────────────────────────────────
function Gauge({ frac, color, children }) {
  const arc = 75 // 270° of the full circumference (pathLength=100)
  const shown = clamp01(frac ?? 0) * arc
  return (
    <div className={styles.gaugeWrap}>
      <svg viewBox="0 0 100 100" className={styles.gauge}>
        <g transform="rotate(135 50 50)">
          <circle cx="50" cy="50" r="42" pathLength="100" className={styles.gaugeTrack} strokeDasharray={`${arc} 100`} />
          <circle cx="50" cy="50" r="42" pathLength="100" className={styles.gaugeArc} strokeDasharray={`${shown} 100`} style={{ stroke: color }} />
        </g>
      </svg>
      <div className={styles.gaugeCenter}>{children}</div>
    </div>
  )
}

// ── Tiny sparkline ──────────────────────────────────────────────────────────────
function Sparkline({ data, color, min, max }) {
  if (!data || data.length < 2) return <div className={styles.sparkEmpty} />
  const lo = min ?? Math.min(...data)
  const hi = max ?? Math.max(...data)
  const span = hi - lo || 1
  const w = 100, h = 24
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w
    const y = h - ((v - lo) / span) * (h - 4) - 2
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className={styles.spark} preserveAspectRatio="none">
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  )
}

function Stat({ label, value, color }) {
  return (
    <div className={styles.stat}>
      <span className={styles.statLabel}>{label}</span>
      <span className={styles.statValue} style={color ? { color } : undefined}>{value}</span>
    </div>
  )
}

export default function MetricsPanel({ tps, cpu, mspt, online, sys, tpsHist = [], msptHist = [] }) {
  const heapUsed  = sys?.heapUsed ?? null,  heapMax = sys?.heapMax ?? null
  const ramTotal  = sys?.ramTotal ?? null,  ramFree = sys?.ramFree ?? null
  const ramUsed   = (ramTotal != null && ramFree != null) ? ramTotal - ramFree : null
  const diskTotal = sys?.diskTotal ?? null, diskUsable = sys?.diskUsable ?? null
  const diskUsed  = (diskTotal != null && diskUsable != null) ? diskTotal - diskUsable : null
  const worldSize = sys?.worldSize != null && sys.worldSize >= 0 ? sys.worldSize : null
  const ramPct    = (ramUsed != null && ramTotal) ? ramUsed / ramTotal : null
  const diskPct   = (diskUsed != null && diskTotal) ? diskUsed / diskTotal : null

  const tpsV = tps?.tps5s, msptV = mspt?.mean10s, cpuV = cpu?.cpu10s

  return (
    <section className={styles.band}>
      <div className={styles.bandHead}>
        <div className={styles.bandTitleRow}>
          <span className={styles.bandIcon}><IconPulse size={18} /></span>
          <span className={styles.bandTitle}>Server Vitals</span>
        </div>
        <span className={styles.bandSub}>live · 15s refresh</span>
      </div>
      <div className={styles.grid}>
      {/* TPS */}
      <div className={styles.card}>
        <div className={styles.head}><span className={styles.icon}><IconClock size={18} /></span><span className={styles.label}>TPS</span></div>
        <Gauge frac={tpsV != null ? tpsV / 20 : 0} color={tpsColor(tpsV)}>
          <span className={styles.big} style={{ color: tpsColor(tpsV) }}>{fmt(tpsV)}</span>
          <span className={styles.unit}>/20</span>
        </Gauge>
        <Sparkline data={tpsHist} color={tpsColor(tpsV)} min={0} max={20} />
        <div className={styles.stats}>
          <Stat label="1m" value={fmt(tps?.tps1m)} color={tpsColor(tps?.tps1m)} />
          <Stat label="5m" value={fmt(tps?.tps5m)} color={tpsColor(tps?.tps5m)} />
        </div>
      </div>

      {/* MSPT */}
      <div className={styles.card}>
        <div className={styles.head}><span className={styles.icon}><IconSpeedometer size={18} /></span><span className={styles.label}>MSPT</span></div>
        <Gauge frac={msptV != null ? msptV / 50 : 0} color={msptColor(msptV)}>
          <span className={styles.big} style={{ color: msptColor(msptV) }}>{fmt(msptV)}</span>
          <span className={styles.unit}>ms</span>
        </Gauge>
        <Sparkline data={msptHist} color={msptColor(msptV)} min={0} max={50} />
        <div className={styles.stats}>
          <Stat label="max 10s" value={`${fmt(mspt?.max10s)}ms`} />
          <Stat label="avg 1m"  value={`${fmt(mspt?.mean1m)}ms`} />
        </div>
      </div>

      {/* CPU */}
      <div className={styles.card}>
        <div className={styles.head}><span className={styles.icon}><IconFurnace size={18} /></span><span className={styles.label}>CPU</span></div>
        <Gauge frac={cpuV ?? 0} color={cpuColor(cpuV)}>
          <span className={styles.big} style={{ color: cpuColor(cpuV) }}>{pctStr(cpuV)}</span>
        </Gauge>
        <div className={styles.sparkEmpty} />
        <div className={styles.stats}>
          <Stat label="1m"  value={pctStr(cpu?.cpu1m)} />
          <Stat label="15m" value={pctStr(cpu?.cpu15m)} />
        </div>
      </div>

      {/* Players */}
      <div className={styles.card}>
        <div className={styles.head}><span className={styles.icon}><IconPlayerHead size={18} /></span><span className={styles.label}>Players</span></div>
        <Gauge frac={online != null && sys?.maxPlayers ? online / sys.maxPlayers : (online ? 0.5 : 0)} color="var(--blue)">
          <span className={styles.big} style={{ color: 'var(--blue)' }}>{online ?? '—'}</span>
          {sys?.maxPlayers ? <span className={styles.unit}>/{sys.maxPlayers}</span> : <span className={styles.unit}>online</span>}
        </Gauge>
        <div className={styles.sparkEmpty} />
        <div className={styles.stats}>
          <Stat label="uptime" value={fmtUptime(sys?.uptimeMs)} />
        </div>
      </div>

      {/* RAM */}
      <div className={styles.card}>
        <div className={styles.head}><span className={styles.icon}><MemIcon /></span><span className={styles.label}>RAM</span></div>
        <Gauge frac={ramPct} color={pctColor(ramPct)}>
          <span className={styles.bigSm} style={{ color: pctColor(ramPct) }}>{fmtBytes(ramUsed)}</span>
          {ramPct != null && <span className={styles.unit}>{(ramPct * 100).toFixed(0)}%</span>}
        </Gauge>
        <div className={styles.sparkEmpty} />
        <div className={styles.stats}>
          <Stat label="total" value={fmtBytes(ramTotal)} />
          {heapUsed != null && <Stat label="heap" value={fmtBytes(heapUsed)} />}
        </div>
      </div>

      {/* Disk */}
      <div className={styles.card}>
        <div className={styles.head}><span className={styles.icon}><DiskIcon /></span><span className={styles.label}>Disk</span></div>
        <Gauge frac={diskPct} color={diskColorP(diskPct)}>
          <span className={styles.bigSm} style={{ color: diskColorP(diskPct) }}>{fmtBytes(diskUsed)}</span>
          {diskPct != null && <span className={styles.unit}>{(diskPct * 100).toFixed(0)}%</span>}
        </Gauge>
        <div className={styles.sparkEmpty} />
        <div className={styles.stats}>
          <Stat label="free"  value={fmtBytes(diskUsable)} />
          <Stat label="world" value={fmtBytes(worldSize)} />
        </div>
      </div>
      </div>
    </section>
  )
}

function MemIcon() {
  return (
    <svg viewBox="0 0 22 22" width={18} height={18} shapeRendering="crispEdges">
      <rect x="1" y="6" width="20" height="10" rx="2" fill="#2A4A7A" />
      <rect x="1" y="6" width="20" height="10" rx="2" stroke="#3A6AB0" strokeWidth="1.5" fill="none" />
      {[3, 7, 11, 15].map(x => <rect key={x} x={x} y="9" width="3" height="4" rx="0.5" fill="#5B8FD4" />)}
      {[5, 9, 13, 17].map(x => <rect key={x} x={x} y="15" width="1" height="2" fill="#3A6AB0" />)}
    </svg>
  )
}
function DiskIcon() {
  return (
    <svg viewBox="0 0 22 22" width={18} height={18} shapeRendering="crispEdges">
      <ellipse cx="11" cy="7" rx="9" ry="4" fill="#3A2A1A" stroke="#7A5A3A" strokeWidth="1.2" />
      <rect x="2" y="7" width="18" height="8" fill="#3A2A1A" />
      <ellipse cx="11" cy="15" rx="9" ry="4" fill="#2A1A0A" stroke="#7A5A3A" strokeWidth="1.2" />
      <ellipse cx="11" cy="7" rx="5" ry="2" fill="#5A3A1A" />
      <circle cx="15" cy="15" r="1.5" fill="#CC9040" /><circle cx="15" cy="15" r="0.7" fill="#FFCC60" />
    </svg>
  )
}
