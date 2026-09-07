import { useState, useEffect, useRef, useCallback } from 'react'
import Hero from './components/Hero'
import MetricsPanel from './components/MetricsPanel'
import LiveFeed from './components/LiveFeed'
import AdminPanel from './components/admin/AdminPanel'
import SkillsLeaderboard from './components/SkillsLeaderboard'
import HardcoreLeaderboard from './components/HardcoreLeaderboard'
import DownloadProgress from './components/DownloadProgress'
import RestoreInfo from './components/RestoreInfo'
import { streamingDownload, hasFileSystemAccess } from './lib/streamingDownload'
import { fmtSize, fmtDate } from './lib/format'
import styles from './App.module.css'

const PUBLIC_DOWNLOAD_URL = '/api/backups/latest/download'
const PUBLIC_LATEST_URL = '/api/backups/latest'

const MAX_EVENTS = 150
const HIST_MAX = 48

function pushHist(setter, value) {
  if (value == null || Number.isNaN(value)) return
  setter(h => [...h, value].slice(-HIST_MAX))
}

export default function App() {
  const [health,      setHealth]      = useState(null)
  const [tps,         setTps]         = useState(null)
  const [cpu,         setCpu]         = useState(null)
  const [mspt,        setMspt]        = useState(null)
  const [sysMetrics,  setSysMetrics]  = useState(null)
  const [tpsHist,     setTpsHist]     = useState([])
  const [msptHist,    setMsptHist]    = useState([])
  const [leaderboard, setLeaderboard] = useState(null)
  const [hardcore,    setHardcore]    = useState(null)
  const [events,      setEvents]      = useState([])
  const [wsStatus,    setWsStatus]    = useState('connecting')
  const [view,        setView]        = useState('dashboard') // 'dashboard' | 'admin'
  const [dlPrompt,    setDlPrompt]    = useState(false)
  const [dlProgress,  setDlProgress]  = useState(null) // { received, total, speed, eta } | null
  const [dlError,     setDlError]     = useState(null)
  const [latest,      setLatest]      = useState(null) // { name, sizeBytes, createdAt } | null
  const [latestError, setLatestError] = useState(null)

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
      if (!msptData.error) { setMspt(msptData); pushHist(setMsptHist, msptData.mean10s) }
      setSysMetrics(sysData)
    } catch { /* server may be starting */ }
  }, [])

  // sampledAt rides along so the header can tell a running world clock from a
  // stopped one: two polls reporting the same day time mean the clock is not moving.
  const pollHealth = useCallback(async () => {
    const res  = await fetch('/api/health')
    const data = await res.json()
    setHealth({ ...data, sampledAt: Date.now() })
  }, [])

  const pollLeaderboard = useCallback(async () => {
    try {
      const r = await fetch('/api/skills/leaderboard')
      if (r.ok) { const d = await r.json(); if (!d.error) setLeaderboard(d) }
    } catch { /* ignore */ }
    try {
      const r = await fetch('/api/hardcore/leaderboard')
      if (r.ok) { const d = await r.json(); if (!d.error) setHardcore(d) }
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
        if (msg.type === 'tps_update') { setTps(msg.data); pushHist(setTpsHist, msg.data?.tps5s) }
        else pushEvent(msg)
      } catch { /* ignore */ }
    }
  }, [pushEvent]) // eslint-disable-line react-hooks/exhaustive-deps

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const bootstrap = useCallback(async () => {
    try {
      await pollHealth()
      openWebSocket()
      pollMetrics()
      pollLeaderboard()
    } catch {
      retryRef.current = setTimeout(bootstrap, 5000)
    }
  }, [openWebSocket, pollHealth, pollMetrics, pollLeaderboard])

  // The snapshot's name is fetched when the prompt opens, not on the Download click:
  // the save picker needs the click's user activation, which awaiting a fetch can outlive.
  const openDownloadPrompt = useCallback(async () => {
    setDlError(null)
    setDlProgress(null)
    setLatest(null)
    setLatestError(null)
    setDlPrompt(true)
    try {
      const r = await fetch(PUBLIC_LATEST_URL)
      const d = await r.json()
      if (d.error) setLatestError(r.status === 404 ? 'No snapshot has been taken yet' : d.error)
      else setLatest(d)
    } catch {
      setLatestError('Could not reach the server')
    }
  }, [])

  const startPublicDownload = useCallback(async () => {
    if (!latest) return
    setDlError(null)
    setDlPrompt(false)
    setDlProgress({ received: 0, total: 0, speed: 0, eta: null })
    const result = await streamingDownload({
      url: PUBLIC_DOWNLOAD_URL,
      filename: latest.name,
      onProgress: setDlProgress,
      allowBlobFallback: true,
    })
    setDlProgress(null)
    if (!result.ok && !result.userCancelled) setDlError(result.error || 'Download failed')
  }, [latest])

  const lbRef = useRef(null)
  useEffect(() => {
    bootstrap()
    // bootstrap retries when health fails, a periodic poll just waits for the next one.
    pollRef.current = setInterval(() => { pollMetrics(); pollHealth().catch(() => {}) }, 15_000)
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
      {dlError && (
        <div className={styles.dlErrorBanner}>
          <span>Download failed: {dlError}</span>
          <button className={styles.bannerDismiss} onClick={() => setDlError(null)}>×</button>
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
            <p className={styles.dlSnapshot}>
              {latest
                ? <><code>{latest.name}</code> · {fmtDate(latest.createdAt)} · {fmtSize(latest.sizeBytes)}</>
                : latestError || 'Checking the latest snapshot…'}
            </p>
            {!hasFileSystemAccess() && (
              <p className={styles.dlCaveat}>
                Your browser will hold the file in memory while downloading.
                Chrome, Edge, or Brave stream straight to disk and are recommended
                for large downloads.
              </p>
            )}
            {(health?.seed != null || health?.mcVersion) && <RestoreInfo info={health} title="Restore info" />}
            <div className={styles.dlBtns}>
              <button className={styles.btnGhost} onClick={() => setDlPrompt(false)}>
                Cancel
              </button>
              <button className={styles.btnPrimary} onClick={startPublicDownload} disabled={!latest}>
                {latest ? 'Download' : latestError ? 'Unavailable' : 'Checking…'}
              </button>
            </div>
          </div>
        </div>
      )}

      {view === 'admin' ? (
        <main className={styles.mainAdmin}>
          <AdminPanel health={health} wsStatus={wsStatus} onBack={() => setView('dashboard')} />
        </main>
      ) : (
        <main className={styles.main}>
          <Hero
            health={health}
            wsStatus={wsStatus}
            sys={sysMetrics}
            downloading={dlProgress != null}
            onDownload={health?.backupPublicEnabled ? openDownloadPrompt : null}
            onAdmin={health?.adminEnabled ? () => setView('admin') : null}
          />
          <div className={styles.grid}>
            <MetricsPanel tps={tps} cpu={cpu} mspt={mspt} online={health?.online ?? null} sys={sysMetrics} tpsHist={tpsHist} msptHist={msptHist} />
            <LiveFeed events={events} />
            <div className={styles.boards}>
              <SkillsLeaderboard data={leaderboard} />
              <HardcoreLeaderboard data={hardcore} />
            </div>
          </div>
        </main>
      )}
    </div>
  )
}

