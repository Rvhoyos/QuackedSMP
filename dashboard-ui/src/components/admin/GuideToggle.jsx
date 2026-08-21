import { Toggle } from '../../ui'
import styles from './GuideToggle.module.css'

/**
 * The "hand over the welcome book" switch, shared by kits and random teleport profiles so the
 * same decision looks and behaves the same in both places.
 *
 * The book is stored once, under the Welcome Book feature, so the link beside the switch goes
 * there. When the feature is off the switch would do nothing, and the Welcome Book tab is hidden,
 * so the link points at Features instead, which is where it gets turned back on.
 */
export default function GuideToggle({ checked, onChange, features, onNavigate, label = 'Include guide' }) {
  const featureOn = features?.welcome_book_enabled !== false

  return (
    <div className={styles.cell}>
      <span className={styles.label}>{label}</span>
      <Toggle checked={checked} onChange={onChange} aria-label={label} />
      {featureOn ? (
        <button type="button" className={styles.link} onClick={() => onNavigate?.('guide')}>
          edit in Welcome Book
        </button>
      ) : (
        <button type="button" className={`${styles.link} ${styles.linkWarn}`}
          onClick={() => onNavigate?.('features')}
          title="The welcome book feature is off, so nothing is handed out.">
          turn on in Features
        </button>
      )}
    </div>
  )
}
