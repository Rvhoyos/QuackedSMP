import { useState, useEffect, useRef } from 'react'
import { IconBookQuill, IconSign } from './admin/MinecraftIcons'
import styles from './LiveFeed.module.css'

const TYPE_META = {
  player_join:  { label: 'joined', tone: 'ok' },
  player_leave: { label: 'left',   tone: 'danger' },
}

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function Head({ name, size = 20 }) {
  return (
    <img
      src={`https://mc-heads.net/avatar/${encodeURIComponent(name)}/${size}`}
      alt={name} width={size} height={size} className={styles.avatar}
      onError={e => { e.target.style.visibility = 'hidden' }}
    />
  )
}

const TABS = [
  { id: 'all',      label: 'All' },
  { id: 'chat',     label: 'Chat' },
  { id: 'activity', label: 'Activity' },
]

export default function LiveFeed({ events }) {
  const [tab, setTab] = useState('all')
  const bottomRef = useRef(null)

  // Oldest-first for a natural chat/log scroll.
  const ordered = events.slice().reverse()
  const shown = ordered.filter(e =>
    tab === 'chat'     ? e.type === 'chat'
  : tab === 'activity' ? e.type !== 'chat'
  : true)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [shown.length])

  return (
    <section className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.titleRow}>
          <span className={styles.headerIcon}><IconBookQuill size={18} /></span>
          <span className={styles.title}>Live Feed</span>
        </div>
        <div className={styles.tabs}>
          {TABS.map(t => (
            <button key={t.id} className={`${styles.tab} ${tab === t.id ? styles.tabActive : ''}`} onClick={() => setTab(t.id)}>
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className={styles.feed}>
        {shown.length === 0 && (
          <div className={styles.empty}>
            <span className={styles.emptyIcon}><IconSign size={26} /></span>
            Waiting for {tab === 'chat' ? 'messages' : tab === 'activity' ? 'activity' : 'events'}…
          </div>
        )}
        {shown.map(ev => ev.type === 'chat'
          ? (
            <div key={ev.id} className={styles.chatRow}>
              <span className={styles.time}>{formatTime(ev.timestamp)}</span>
              <Head name={ev.player} size={18} />
              <span className={styles.player}>{ev.player}</span>
              <span className={styles.message}>{ev.message}</span>
            </div>
          )
          : TYPE_META[ev.type] && (
            <div key={ev.id} className={`${styles.actRow} ${styles[TYPE_META[ev.type].tone]}`}>
              <Head name={ev.player} size={20} />
              <span className={styles.player}>{ev.player}</span>
              <span className={styles.action}>{TYPE_META[ev.type].label}</span>
              <span className={styles.time}>{formatTime(ev.timestamp)}</span>
            </div>
          )
        )}
        <div ref={bottomRef} />
      </div>
    </section>
  )
}
