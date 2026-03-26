import { useState } from 'react'
import { IconDuck } from './MinecraftIcons'
import styles from './AdminGate.module.css'

export default function AdminGate({ onAuth }) {
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState('')
  const [loading,  setLoading]  = useState(false)

  async function submit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await fetch('/api/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      })
      const data = await res.json()
      if (!res.ok) {
        setError(data.error || 'Invalid password.')
      } else {
        onAuth(data.token || '')
      }
    } catch {
      setError('Could not reach the server.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.container}>
        {/* Wood header — server entry */}
        <div className={styles.woodHeader}>
          <div className={styles.serverIcon}>
            <IconDuck size={40} />
          </div>
          <div className={styles.serverInfo}>
            <span className={styles.serverName}>QuackedSMP</span>
            <span className={styles.serverSub}>Admin Panel</span>
          </div>
        </div>

        {/* Dark body */}
        <div className={styles.body}>
          <form className={styles.form} onSubmit={submit}>
            <label className={styles.label}>Password</label>
            <input
              className={styles.input}
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="enter password..."
              autoFocus
              required
            />

            {error && (
              <div className={styles.error}>
                <span className={styles.errorX}>✗</span> {error}
              </div>
            )}

            <button className={styles.connectBtn} type="submit" disabled={loading}>
              {loading ? 'Verifying...' : 'Connect'}
            </button>
          </form>

          <p className={styles.hint}>/smp admin setpassword to reset</p>
        </div>
      </div>
    </div>
  )
}
