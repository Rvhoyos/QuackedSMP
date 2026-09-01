import { useEffect, useRef, useState } from 'react'
import { DAY_TICKS } from './skyCycle'

const STEP_MS      = 1000
const TICKS_PER_MS = 0.02  // 20 ticks a second
const FROZEN_TICKS = 20    // a running clock moves ~300 ticks between two 15s polls

/*
 * Smooths the day time the server reports every 15 seconds into a clock the scene
 * can animate against.
 *
 * Between polls the tick is derived from the sample and its timestamp rather than
 * accumulated, so a throttled background tab catches up in one step instead of
 * falling behind, and every poll resyncs whatever the server actually says.
 *
 * sampledAt is required and has to change on every poll even when the tick does
 * not: two polls reporting the same tick are how we know the server clock is
 * standing still (advance_time off, or /time pause), and the scene then holds
 * instead of drifting away and snapping back once a poll.
 */
export default function useServerDayTime(sample, sampledAt) {
  const [tick, setTick] = useState(null)
  const anchor = useRef(null)

  useEffect(() => {
    if (sample == null || sample < 0) {
      anchor.current = null
      setTick(null)
      return
    }
    const prev = anchor.current
    // The same poll seen twice (StrictMode remounts the effect) is not a second
    // reading, and reading it as one would look exactly like a stopped clock.
    if (prev && prev.at === sampledAt) return
    const advanced = (sample - (prev ? prev.tick : sample) + DAY_TICKS) % DAY_TICKS
    anchor.current = { tick: sample, at: sampledAt, running: !prev || advanced >= FROZEN_TICKS }
    setTick(sample)
  }, [sample, sampledAt])

  useEffect(() => {
    const id = setInterval(() => {
      const a = anchor.current
      if (!a || !a.running) return
      const elapsed = (Date.now() - a.at) * TICKS_PER_MS
      setTick((a.tick + elapsed) % DAY_TICKS)
    }, STEP_MS)
    return () => clearInterval(id)
  }, [])

  return tick
}
