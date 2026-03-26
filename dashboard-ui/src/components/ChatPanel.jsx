import { useEffect, useRef } from 'react'
import { IconBookQuill } from './admin/MinecraftIcons'
import styles from './ChatPanel.module.css'

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function PlayerHead({ name, size = 20 }) {
  return (
    <img
      src={`https://mc-heads.net/avatar/${encodeURIComponent(name)}/${size}`}
      alt={name}
      width={size}
      height={size}
      className={styles.avatar}
      onError={e => { e.target.style.visibility = 'hidden' }}
    />
  )
}

export default function ChatPanel({ events }) {
  const bottomRef = useRef(null)
  // events are newest-first; reverse for oldest-first display (like a real chat)
  const messages = events.filter(e => e.type === 'chat').toReversed()

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.headerIcon}><IconBookQuill size={18} /></span>
          <span className={styles.title}>Chat</span>
        </div>
        <span className={styles.count}>{messages.length}</span>
      </div>

      <div className={styles.feed}>
        {messages.length === 0 && (
          <div className={styles.empty}>No messages yet</div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={styles.row}>
            <span className={styles.time}>{formatTime(msg.timestamp)}</span>
            <PlayerHead name={msg.player} size={18} />
            <span className={styles.player}>{msg.player}</span>
            <span className={styles.message}>{msg.message}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
