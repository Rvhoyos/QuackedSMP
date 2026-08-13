import { useState } from 'react'
import styles from './RestoreInfo.module.css'

const DEFAULT_NOTE =
  'Restoring on another machine needs the same seed, loader, and mod set for terrain to match at unloaded chunks.'

// Presentational only — parents fetch the data and pass it in. `info` carries
// mcVersion, loader, loaderVersion, modVersion and (optionally) seed.
// The seed row and copy button are omitted when no seed is present.
export default function RestoreInfo({ info, title = 'Restore info', note = DEFAULT_NOTE }) {
  const [copied, setCopied] = useState(false)

  if (!info) return null

  async function copySeed() {
    try {
      await navigator.clipboard.writeText(String(info.seed))
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch { /* clipboard blocked — no-op */ }
  }

  return (
    <div className={styles.box}>
      <div className={styles.title}>{title}</div>

      {info.seed != null && (
        <div className={styles.seedRow}>
          <span className={styles.label}>Seed</span>
          <code className={styles.seed}>{info.seed}</code>
          <button className={styles.copyBtn} onClick={copySeed}>
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      )}

      <div className={styles.grid}>
        <Field label="Minecraft" value={info.mcVersion} />
        <Field label="Loader" value={joinLoader(info.loader, info.loaderVersion)} />
        <Field label="QuackedSMP" value={info.modVersion} />
      </div>

      <div className={styles.note}>{note}</div>
    </div>
  )
}

function joinLoader(loader, version) {
  if (!loader) return version || '—'
  return version ? `${loader} ${version}` : loader
}

function Field({ label, value }) {
  return (
    <div className={styles.field}>
      <span className={styles.label}>{label}</span>
      <span className={styles.value}>{value || '—'}</span>
    </div>
  )
}
