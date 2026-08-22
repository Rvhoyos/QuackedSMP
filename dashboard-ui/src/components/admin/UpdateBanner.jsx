import { useEffect, useState } from 'react'
import { Btn, IconButton } from '../../ui'
import { IconUpgradeArrow } from './MinecraftIcons'
import styles from './UpdateBanner.module.css'

const RELEASES_API = 'https://api.github.com/repos/Rvhoyos/QuackedSMP/releases/latest'

// Dismissal is keyed by the version it was dismissed for, so the banner stays
// gone until a newer release shows up instead of returning on every reload.
const DISMISS_KEY = 'quack_update_dismissed'

// Storefronts the release is mirrored to by the publish-platforms CI job.
const STORES = [
  { label: 'Modrinth',   url: 'https://modrinth.com/mod/quackedsmp' },
  { label: 'CurseForge', url: 'https://www.curseforge.com/minecraft/mc-mods/quackedsmp' },
]

function isNewer(latest, current) {
  const a = latest.split('.').map(Number)
  const b = current.split('.').map(Number)
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const x = a[i] || 0, y = b[i] || 0
    if (x > y) return true
    if (x < y) return false
  }
  return false
}

function open(url) { window.open(url, '_blank', 'noopener,noreferrer') }

// Admin-only update notice. Lives inside the admin shell so the GitHub request
// is never made by a public visitor's browser.
export default function UpdateBanner({ version }) {
  const [update, setUpdate] = useState(null) // { latest, url } or null

  useEffect(() => {
    if (!version || version === 'unknown') return
    let cancelled = false
    fetch(RELEASES_API)
      .then(r => r.ok ? r.json() : null)
      .then(rel => {
        if (cancelled || !rel) return
        const latest = (rel.tag_name || '').replace(/^v/, '')
        if (!latest || !isNewer(latest, version)) return
        if (localStorage.getItem(DISMISS_KEY) === latest) return
        setUpdate({ latest, url: rel.html_url })
      })
      .catch(() => { /* GitHub unreachable, stay silent */ })
    return () => { cancelled = true }
  }, [version])

  if (!update) return null

  function dismiss() {
    localStorage.setItem(DISMISS_KEY, update.latest)
    setUpdate(null)
  }

  return (
    <div className={styles.banner}>
      <span className={styles.icon}><IconUpgradeArrow size={18} /></span>
      <div className={styles.text}>
        <span className={styles.title}>Update available</span>
        <span className={styles.versions}>
          <span className={styles.latest}>v{update.latest}</span> · running v{version}
        </span>
      </div>
      <div className={styles.actions}>
        {STORES.map(s => (
          <Btn key={s.label} size="sm" variant="primary" onClick={() => open(s.url)}>{s.label}</Btn>
        ))}
        <Btn size="sm" onClick={() => open(update.url)}>GitHub</Btn>
      </div>
      <IconButton tip="Dismiss until the next release" onClick={dismiss}>×</IconButton>
    </div>
  )
}
