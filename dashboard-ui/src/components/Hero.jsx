import { IconDuck } from './admin/MinecraftIcons'
import { Btn } from '../ui'
import WorldScene from './WorldScene'
import styles from './Hero.module.css'

function fmtUptime(ms) {
  if (ms == null) return null
  const s = Math.floor(ms / 1000), d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  return `${m}m`
}

// A little redstone-style status lamp: lit green when the live socket is open,
// dark red and pulsing while reconnecting.
function Lamp({ on }) {
  const core  = on ? '#93e485' : '#5a2230'
  const spark = on ? '#e6ffe0' : '#7a3040'
  return (
    <svg className={on ? styles.lampOn : styles.lampOff} viewBox="0 0 16 16" width={13} height={13} shapeRendering="crispEdges">
      <rect x="1" y="1" width="14" height="14" fill="#241f12" />
      <rect x="2" y="2" width="12" height="12" fill="#463f24" />
      <rect x="3" y="3" width="10" height="10" fill={core} />
      <rect x="4" y="4" width="3" height="3" fill={spark} />
      <rect x="9" y="9" width="2" height="2" fill={spark} opacity="0.7" />
    </svg>
  )
}

// The public dashboard's identity + status block, set inside a live pixel-art
// dusk scene. Owns the server name, the one live indicator, uptime/version,
// and the primary actions. No separate header bar, no duplicate status strip.
export default function Hero({ health, wsStatus, sys, onDownload, onAdmin, downloading }) {
  const live = wsStatus === 'open'
  const uptime = fmtUptime(sys?.uptimeMs)

  return (
    <header className={styles.hero}>
      <WorldScene />
      <div className={styles.scrim} />

      <div className={styles.plate}>
        <div className={styles.left}>
          <a className={styles.mark} href="https://quackedmod.wiki" target="_blank" rel="noopener noreferrer">
            <span className={styles.duck}><IconDuck size={44} /></span>
          </a>
          <div className={styles.titleWrap}>
            <div className={styles.titleRow}>
              <h1 className={styles.name}>{health?.serverName || 'QuackedSMP'}</h1>
              <span className={`${styles.lampTag} ${live ? styles.tagLive : styles.tagDown}`}>
                <Lamp on={live} />
                {live ? 'LIVE' : 'RECONNECTING'}
              </span>
            </div>
            <div className={styles.metaRow}>
              <span className={styles.sub}>made by quackedmod</span>
              {uptime && <span className={styles.chip}>uptime {uptime}</span>}
              {health?.version && <span className={styles.chip}>v{health.version}</span>}
            </div>
          </div>
        </div>

        <div className={styles.actions}>
          {onDownload && (
            <Btn variant="ok" size="lg" onClick={onDownload} disabled={downloading}>
              {downloading ? 'Downloading…' : '⬇ Download World'}
            </Btn>
          )}
          {onAdmin && (
            <Btn variant="primary" size="lg" onClick={onAdmin}>Admin Panel →</Btn>
          )}
        </div>
      </div>
    </header>
  )
}
