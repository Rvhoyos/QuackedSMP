import styles from './EventFeed.module.css'
import { IconSign } from './admin/MinecraftIcons'

const TYPE_META = {
  player_join:  { label: 'joined',  color: 'green' },
  player_leave: { label: 'left',    color: 'red'   },
}

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function PlayerHead({ name, size = 24 }) {
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

export default function EventFeed({ events }) {
  const activity = events.filter(e => e.type !== 'chat')
  return (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <div className={styles.panelTitleRow}>
          <span className={styles.panelHeaderIcon}><IconSign size={18} /></span>
          <span className={styles.panelTitle}>Activity</span>
        </div>
        <span className={styles.panelCount}>{activity.length}</span>
      </div>

      <div className={styles.feed}>
        {activity.length === 0 && (
          <div className={styles.empty}>Waiting for activity...</div>
        )}
        {activity.map((ev) => (
          <EventRow key={ev.id} event={ev} />
        ))}
      </div>
    </div>
  )
}

function EventRow({ event }) {
  const meta = TYPE_META[event.type]
  if (!meta) return null

  return (
    <div className={`${styles.row} ${styles[`row_${meta.color}`]}`}>
      <PlayerHead name={event.player} size={24} />
      <div className={styles.rowBody}>
        <span className={styles.player}>{event.player}</span>
        <span className={`${styles.action} ${styles[`action_${meta.color}`]}`}>{meta.label}</span>
      </div>
      <span className={styles.time}>{formatTime(event.timestamp)}</span>
    </div>
  )
}
