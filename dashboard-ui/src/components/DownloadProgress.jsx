import { fmtSize } from '../lib/format'
import styles from './DownloadProgress.module.css'

export default function DownloadProgress({ received, total, speed, eta }) {
  const pct = total > 0 ? Math.min(100, (received / total) * 100) : null
  return (
    <div className={styles.wrap}>
      <div className={styles.text}>
        {pct != null && <span className={styles.pct}>{pct.toFixed(0)}%</span>}
        <span className={styles.bytes}>
          {fmtSize(received)}{total > 0 ? ` / ${fmtSize(total)}` : ''}
        </span>
        {speed > 0 && <span className={styles.speed}>{fmtSize(speed)}/s</span>}
        {eta != null && <span className={styles.eta}>ETA {fmtDuration(eta)}</span>}
      </div>
      <div className={styles.bar}>
        <div
          className={pct != null ? styles.fill : styles.fillIndeterminate}
          style={pct != null ? { width: `${pct}%` } : undefined}
        />
      </div>
    </div>
  )
}

function fmtDuration(seconds) {
  if (seconds < 60) return `${Math.ceil(seconds)}s`
  const m = Math.floor(seconds / 60)
  const s = Math.ceil(seconds % 60)
  if (m < 60) return `${m}m ${s}s`
  const h = Math.floor(m / 60)
  return `${h}h ${m % 60}m`
}
