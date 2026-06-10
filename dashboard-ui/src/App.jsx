import { useState, useEffect, useRef, useCallback } from 'react'
import MetricsPanel from './components/MetricsPanel'
import EventFeed from './components/EventFeed'
import ChatPanel from './components/ChatPanel'
import AdminPanel from './components/admin/AdminPanel'
import SkillsLeaderboard from './components/SkillsLeaderboard'
import DownloadProgress from './components/DownloadProgress'
import { IconDuck } from './components/admin/MinecraftIcons'
import { streamingDownload, hasFileSystemAccess } from './lib/streamingDownload'
import styles from './App.module.css'

const PUBLIC_DOWNLOAD_URL = '/api/backups/latest/download'
const PUBLIC_DOWNLOAD_FILENAME = 'world.zip'

const MAX_EVENTS = 150

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

export default function App() {
  const [health,      setHealth]      = useState(null)
  const [tps,         setTps]         = useState(null)
  const [cpu,         setCpu]         = useState(null)
  const [mspt,        setMspt]        = useState(null)
  const [sysMetrics,  setSysMetrics]  = useState(null)
  const [leaderboard, setLeaderboard] = useState(null)
  const [events,      setEvents]      = useState([])
  const [wsStatus,    setWsStatus]    = useState('connecting')
  const [view,        setView]        = useState('dashboard') // 'dashboard' | 'admin'
  const [update,      setUpdate]      = useState(null) // {latest, current, url} or null
  const [dlPrompt,    setDlPrompt]    = useState(false)
  const [dlProgress,  setDlProgress]  = useState(null) // { received, total, speed, eta } | null
  const [dlError,     setDlError]     = useState(null)

  const wsRef         = useRef(null)
  const retryRef      = useRef(null)
  const pollRef       = useRef(null)

  const pushEvent = useCallback((ev) => {
    setEvents(prev => [{ ...ev, id: `${ev.timestamp}-${Math.random()}` }, ...prev].slice(0, MAX_EVENTS))
  }, [])

  const pollMetrics = useCallback(async () => {
    try {
      const [cpuRes, msptRes, sysRes] = await Promise.all([
        fetch('/api/spark/cpu'),
        fetch('/api/spark/tick'),
        fetch('/api/metrics'),
      ])
      const cpuData  = await cpuRes.json()
      const msptData = await msptRes.json()
      const sysData  = await sysRes.json()
      if (!cpuData.error)  setCpu(cpuData)
      if (!msptData.error) setMspt(msptData)
      setSysMetrics(sysData)
    } catch { /* server may be starting */ }
  }, [])

  const pollLeaderboard = useCallback(async () => {
    try {
      const r = await fetch('/api/skills/leaderboard')
      if (r.ok) { const d = await r.json(); if (!d.error) setLeaderboard(d) }
    } catch { /* ignore */ }
  }, [])

  const openWebSocket = useCallback(() => {
    if (wsRef.current) wsRef.current.close()
    const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/events`)
    wsRef.current = ws
    setWsStatus('connecting')

    ws.onopen  = () => setWsStatus('open')
    ws.onclose = () => {
      setWsStatus('closed')
      retryRef.current = setTimeout(bootstrap, 4000)
    }
    ws.onerror = () => ws.close()
    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data)
        if (msg.type === 'tps_update') setTps(msg.data)
        else pushEvent(msg)
      } catch { /* ignore */ }
    }
  }, [pushEvent]) // eslint-disable-line react-hooks/exhaustive-deps

  const checkForUpdate = useCallback(async (currentVersion) => {
    if (!currentVersion || currentVersion === 'unknown') return
    try {
      const r = await fetch('https://api.github.com/repos/Rvhoyos/QuackedSMP/releases/latest')
      if (!r.ok) return
      const rel = await r.json()
      const latest = (rel.tag_name || '').replace(/^v/, '')
      if (latest && isNewer(latest, currentVersion)) {
        setUpdate({ latest, current: currentVersion, url: rel.html_url })
      }
    } catch { /* GitHub unreachable — silent */ }
  }, [])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const bootstrap = useCallback(async () => {
    try {
      const res  = await fetch('/api/health')
      const data = await res.json()
      setHealth(data)
      checkForUpdate(data.version)
      openWebSocket()
      pollMetrics()
      pollLeaderboard()
    } catch {
      retryRef.current = setTimeout(bootstrap, 5000)
    }
  }, [openWebSocket, pollMetrics, pollLeaderboard])

  const startPublicDownload = useCallback(async () => {
    setDlError(null)
    setDlPrompt(false)
    setDlProgress({ received: 0, total: 0, speed: 0, eta: null })
    const result = await streamingDownload({
      url: PUBLIC_DOWNLOAD_URL,
      filename: PUBLIC_DOWNLOAD_FILENAME,
      onProgress: setDlProgress,
      allowBlobFallback: true,
    })
    setDlProgress(null)
    if (!result.ok && !result.userCancelled) setDlError(result.error || 'Download failed')
  }, [])

  const lbRef = useRef(null)
  useEffect(() => {
    bootstrap()
    pollRef.current = setInterval(pollMetrics, 15_000)
    lbRef.current   = setInterval(pollLeaderboard, 60_000)
    return () => {
      clearTimeout(retryRef.current)
      clearInterval(pollRef.current)
      clearInterval(lbRef.current)
      wsRef.current?.close()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <a href="https://quackedmod.wiki" className={styles.brandLink} target="_blank" rel="noopener noreferrer">
          <div className={styles.brand}>
            <span className={styles.brandIcon}><IconDuck size={36} /></span>
            <div className={styles.brandText}>
              <span className={styles.brandName}>QuackedSMP</span>
              <span className={styles.brandSub}>made by quackedmod</span>
            </div>
          </div>
        </a>
        <div className={styles.headerCenter}>
          {health?.serverName && (
            <span className={styles.serverName}>{health.serverName}</span>
          )}
        </div>
        <div className={styles.headerRight}>
          <span className={styles.playerCount}>
            {health != null ? `${health.online} online` : '— online'}
          </span>
          {health?.backupPublicEnabled && view !== 'admin' && (
            <button
              className={styles.navTab}
              onClick={() => { setDlError(null); setDlProgress(null); setDlPrompt(true) }}
              disabled={dlProgress != null}
            >
              {dlProgress != null ? 'Downloading…' : 'Download World'}
            </button>
          )}
          {health?.adminEnabled && (
            <button
              className={`${styles.navTab} ${view === 'admin' ? styles.navTabActive : ''}`}
              onClick={() => setView(v => v === 'admin' ? 'dashboard' : 'admin')}
            >
              {view === 'admin' ? '← Back' : 'Admin'}
            </button>
          )}
          <StatusPill status={wsStatus} />
        </div>
      </header>

      {update && (
        <div className={styles.updateBanner}>
          <span>
            Update available: <strong>v{update.latest}</strong> (running v{update.current})
          </span>
          <a href={update.url} target="_blank" rel="noopener noreferrer" className={styles.updateLink}>
            Download
          </a>
          <button className={styles.updateDismiss} onClick={() => setUpdate(null)}>×</button>
        </div>
      )}

      {dlError && (
        <div className={styles.dlErrorBanner}>
          <span>Download failed: {dlError}</span>
          <button className={styles.updateDismiss} onClick={() => setDlError(null)}>×</button>
        </div>
      )}

      {dlProgress && (
        <div className={styles.dlProgressBanner}>
          <span className={styles.dlProgressLabel}>Downloading world…</span>
          <DownloadProgress {...dlProgress} />
        </div>
      )}

      {dlPrompt && (
        <div className={styles.dlOverlay} onClick={() => setDlPrompt(false)}>
          <div className={styles.dlBox} onClick={e => e.stopPropagation()}>
            <p className={styles.dlTitle}>Download World</p>
            <p className={styles.dlBody}>
              Full Minecraft world as a <code>.zip</code>. Large worlds can take a while
              on slow connections.
            </p>
            {!hasFileSystemAccess() && (
              <p className={styles.dlCaveat}>
                Your browser will hold the file in memory while downloading.
                Chrome, Edge, or Brave stream straight to disk and are recommended
                for large downloads.
              </p>
            )}
            <div className={styles.dlBtns}>
              <button className={styles.btnGhost} onClick={() => setDlPrompt(false)}>
                Cancel
              </button>
              <button className={styles.btnPrimary} onClick={startPublicDownload}>
                Download
              </button>
            </div>
          </div>
        </div>
      )}

      {view === 'admin' ? (
        <main className={styles.main}>
          <AdminPanel health={health} />
        </main>
      ) : (
        <main className={styles.main}>
          <MetricsPanel tps={tps} cpu={cpu} mspt={mspt} online={health?.online ?? null} sys={sysMetrics} />
          <div className={styles.content}>
            <EventFeed events={events} />
            <ChatPanel events={events} />
          </div>
          <SkillsLeaderboard data={leaderboard} />
        </main>
      )}
    </div>
  )
}

function StatusPill({ status }) {
  const labels = { connecting: 'Connecting', open: 'Live', closed: 'Reconnecting' }
  return (
    <span className={`${styles.wsDot} ${styles[status]}`}>
      {labels[status]}
    </span>
  )
}
