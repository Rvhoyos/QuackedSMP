import { useState, useEffect, useRef, useCallback } from 'react'
import MetricsPanel from './components/MetricsPanel'
import EventFeed from './components/EventFeed'
import ChatPanel from './components/ChatPanel'
import AdminPanel from './components/admin/AdminPanel'
import { IconDuck } from './components/admin/MinecraftIcons'
import styles from './App.module.css'

const MAX_EVENTS = 150

export default function App() {
  const [health,   setHealth]   = useState(null)
  const [tps,      setTps]      = useState(null)
  const [cpu,      setCpu]      = useState(null)
  const [mspt,     setMspt]     = useState(null)
  const [events,   setEvents]   = useState([])
  const [wsStatus, setWsStatus] = useState('connecting')
  const [view,     setView]     = useState('dashboard') // 'dashboard' | 'admin'

  const wsRef    = useRef(null)
  const retryRef = useRef(null)
  const pollRef  = useRef(null)

  const pushEvent = useCallback((ev) => {
    setEvents(prev => [{ ...ev, id: `${ev.timestamp}-${Math.random()}` }, ...prev].slice(0, MAX_EVENTS))
  }, [])

  const pollMetrics = useCallback(async () => {
    try {
      const [cpuRes, msptRes] = await Promise.all([
        fetch('/api/spark/cpu'),
        fetch('/api/spark/tick'),
      ])
      const cpuData  = await cpuRes.json()
      const msptData = await msptRes.json()
      if (!cpuData.error)  setCpu(cpuData)
      if (!msptData.error) setMspt(msptData)
    } catch { /* server may be starting */ }
  }, [])

  const openWebSocket = useCallback(() => {
    if (wsRef.current) wsRef.current.close()
    const ws = new WebSocket(`ws://${location.host}/ws/events`)
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

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const bootstrap = useCallback(async () => {
    try {
      const res  = await fetch('/api/health')
      const data = await res.json()
      setHealth(data)
      openWebSocket()
      pollMetrics()
    } catch {
      retryRef.current = setTimeout(bootstrap, 5000)
    }
  }, [openWebSocket, pollMetrics])

  useEffect(() => {
    bootstrap()
    pollRef.current = setInterval(pollMetrics, 15_000)
    return () => {
      clearTimeout(retryRef.current)
      clearInterval(pollRef.current)
      wsRef.current?.close()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.brandIcon}><IconDuck size={36} /></span>
          <div className={styles.brandText}>
            <span className={styles.brandName}>QuackedSMP</span>
            <span className={styles.brandSub}>Dashboard</span>
          </div>
        </div>
        <div className={styles.headerRight}>
          <span className={styles.playerCount}>
            {health != null ? `${health.online} online` : '— online'}
          </span>
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

      {view === 'admin' ? (
        <main className={styles.main}>
          <AdminPanel health={health} />
        </main>
      ) : (
        <main className={styles.main}>
          <MetricsPanel tps={tps} cpu={cpu} mspt={mspt} online={health?.online ?? null} />
          <div className={styles.content}>
            <EventFeed events={events} />
            <ChatPanel events={events} />
          </div>
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
