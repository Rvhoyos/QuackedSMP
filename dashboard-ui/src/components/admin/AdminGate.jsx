import { useState } from 'react'
import { IconDuck } from './MinecraftIcons'
import styles from './AdminGate.module.css'

export default function AdminGate({ onAuth }) {
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [loading,  setLoading]  = useState(false)

  async function submit(e) {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      const res = await fetch('/api/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      })
      const data = await res.json()
      if (!res.ok) setError(data.error || 'Invalid password.')
      else onAuth(data.token || '')
    } catch {
      setError('Could not reach the server.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.page}>
      <form className={styles.card} onSubmit={submit}>
        <div className={styles.accent} />
        <div className={styles.brand}>
          <span className={styles.duck}><IconDuck size={44} /></span>
          <div className={styles.brandText}>
            <span className={styles.name}>QuackedSMP</span>
            <span className={styles.sub}>Admin Access</span>
          </div>
        </div>

        <label className={styles.label}>Password</label>
        <input
          className={styles.input}
          type="password"
          value={password}
          onChange={e => setPassword(e.target.value)}
          placeholder="Enter admin password"
          autoFocus
          required
        />

        {error && <div className={styles.error}>✗ {error}</div>}

        <button className={styles.connect} type="submit" disabled={loading}>
          {loading ? 'Verifying…' : 'Connect'}
        </button>
        <p className={styles.hint}>Reset in-game with <code>/smp admin setpassword</code></p>
      </form>
    </div>
  )
}
