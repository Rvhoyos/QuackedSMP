import { useEffect, useMemo, useRef, useState } from 'react'
import { IconTerminalCaret } from './MinecraftIcons'
import styles from './LogTerminal.module.css'

// Level filters. Locally generated lines (a command you sent, a dropped-lines
// marker) always show: they are the record of what you did, not server chatter.
const FILTERS = [
  { id: 'all',   label: 'All',    match: () => true },
  { id: 'warn',  label: 'Warn+',  match: l => l === 'WARN' || l === 'ERROR' || l === 'FATAL' },
  { id: 'error', label: 'Errors', match: l => l === 'ERROR' || l === 'FATAL' },
]

const LOCAL_LEVELS = new Set(['CMD', 'GAP'])

const LEVEL_CLASS = {
  ERROR: styles.lvlError, FATAL: styles.lvlError,
  WARN:  styles.lvlWarn,
  DEBUG: styles.faint, TRACE: styles.faint,
  CMD:   styles.cmd,
  GAP:   styles.gap,
}

// Distance from the bottom, in px, still counted as "watching the tail".
const PIN_SLACK = 24

function clock(ms) {
  const d = new Date(ms)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** Live server log plus the command input. Presentational: the panel owns the data. */
export default function LogTerminal({ lines, live, error, onSubmit, onClear }) {
  const [filter, setFilter] = useState('all')
  const [needle, setNeedle] = useState('')
  const [input,  setInput]  = useState('')
  const [history, setHistory] = useState([])
  const [histIdx, setHistIdx] = useState(-1)

  const scroller = useRef(null)
  const pinned   = useRef(true)

  const shown = useMemo(() => {
    const level = FILTERS.find(f => f.id === filter) ?? FILTERS[0]
    const text  = needle.trim().toLowerCase()
    return lines.filter(l => {
      if (!LOCAL_LEVELS.has(l.level) && !level.match(l.level)) return false
      if (!text) return true
      return l.msg.toLowerCase().includes(text) || (l.logger || '').toLowerCase().includes(text)
    })
  }, [lines, filter, needle])

  // Follow the tail only while the operator is already at the bottom, so
  // scrolling back to read something is not yanked away by the next poll.
  useEffect(() => {
    const el = scroller.current
    if (el && pinned.current) el.scrollTop = el.scrollHeight
  }, [shown])

  function handleScroll(e) {
    const el = e.currentTarget
    pinned.current = el.scrollHeight - el.scrollTop - el.clientHeight < PIN_SLACK
  }

  function submit(e) {
    e.preventDefault()
    const cmd = input.trim()
    if (!cmd) return
    setHistory(h => [cmd, ...h].slice(0, 50))
    setHistIdx(-1)
    setInput('')
    pinned.current = true
    onSubmit(cmd)
  }

  function handleKeyDown(e) {
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      const n = Math.min(histIdx + 1, history.length - 1)
      setHistIdx(n)
      if (history[n]) setInput(history[n])
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      const n = Math.max(histIdx - 1, -1)
      setHistIdx(n)
      setInput(n === -1 ? '' : history[n] ?? '')
    }
  }

  return (
    <section className={styles.terminal}>
      <div className={styles.head}>
        <span className={styles.title}>
          <IconTerminalCaret size={14} />
          Server Log
        </span>
        <div className={styles.tools}>
          <span className={`${styles.status} ${live ? styles.statusLive : styles.statusDown}`}>
            <span className={styles.dot} />
            {live ? 'live' : 'offline'}
          </span>
          <div className={styles.filters}>
            {FILTERS.map(f => (
              <button key={f.id} type="button" title={`Show ${f.label}`}
                className={`${styles.filter} ${filter === f.id ? styles.filterOn : ''}`}
                onClick={() => setFilter(f.id)}>{f.label}</button>
            ))}
          </div>
          <input className={styles.search} type="search" value={needle} placeholder="filter…"
            onChange={e => setNeedle(e.target.value)} spellCheck={false} aria-label="Filter log lines" />
          <button className={styles.clear} type="button" onClick={onClear} title="Clear the view">clear</button>
        </div>
      </div>

      {error && <div className={styles.error}>{error}</div>}

      <div className={styles.log} ref={scroller} onScroll={handleScroll}>
        {shown.length === 0 && (
          <span className={styles.empty}>{lines.length === 0 ? 'Waiting for server output…' : 'No lines match this filter.'}</span>
        )}
        {shown.map(l => (
          <div key={l.seq} className={`${styles.line} ${LEVEL_CLASS[l.level] ?? ''}`}>
            <span className={styles.time}>{clock(l.t)}</span>
            {l.level === 'CMD' || l.level === 'GAP'
              ? <span className={styles.msg}>{l.msg}</span>
              : <>
                  <span className={styles.level}>{l.level}</span>
                  <span className={styles.logger}>{l.logger}</span>
                  <span className={styles.msg}>{l.msg}</span>
                </>}
          </div>
        ))}
      </div>

      <form className={styles.prompt} onSubmit={submit}>
        <span className={styles.caret}>{'>'}</span>
        <input className={styles.field} type="text" value={input} onKeyDown={handleKeyDown}
          onChange={e => { setInput(e.target.value); setHistIdx(-1) }}
          placeholder="give @a diamond 1     (↑↓ for history)" spellCheck={false} autoComplete="off" autoCapitalize="off" />
        <button className={styles.run} type="submit" disabled={!input.trim()}>Run</button>
      </form>
    </section>
  )
}
