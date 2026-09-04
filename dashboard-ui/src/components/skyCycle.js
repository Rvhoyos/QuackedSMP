// Palette for the header's pixel scene at one moment of the Minecraft day.
//
// Every tick in this file is a real boundary from vanilla's Timelines.OVERWORLD_DAY
// on 26.2, so the scene changes when the game changes, not when it looks nice:
//   SKY_LIGHT_LEVEL  holds full daylight 133 to 11867, full night 13670 to 22330
//   STAR_BRIGHTNESS  0 from 627 to 11373, 0.5 from 13228 to 22772
//   SUN_ANGLE        0 at 6000 (noon), MOON_ANGLE is the same track plus 180 degrees
// The colours themselves are hand-drawn pixel art, three authored palettes crossfaded
// across those boundaries. Vanilla eases its tracks, this interpolates linearly.

export const DAY_TICKS = 24000

// Time the scene shows when the server has not reported a day time. Sits mid-sunset,
// which is the fixed dusk the header used before it followed the clock.
export const FALLBACK_TICK = 12800

const DAY = {
  bands: ['#3a6fd0', '#4a7ddb', '#5a8ce4', '#6b9bec', '#7daaf1', '#90b9f5', '#a6c8f8', '#c0d9fb', '#dbe9fd'],
  farHill: '#5f7d92', nearHill: '#3f7a37', grass: '#79c94f', tree: '#24471c',
  cloudA: '#ffffff', cloudB: '#f2f7ff',
}

const TWILIGHT = {
  bands: ['#12122b', '#191834', '#241f47', '#332a55', '#4a3358', '#6b4460', '#98555a', '#c8843a', '#e6ad6a'],
  farHill: '#2f3a4a', nearHill: '#33562f', grass: '#5a9c46', tree: '#1e3019',
  cloudA: '#d8b6c0', cloudB: '#e6c8b0',
}

const NIGHT = {
  bands: ['#05060f', '#070a16', '#0a0e1e', '#0d1226', '#10172f', '#141c39', '#182142', '#1d284d', '#232f59'],
  farHill: '#1a2130', nearHill: '#1b2c1a', grass: '#2c4a2a', tree: '#101c0d',
  cloudA: '#39415c', cloudB: '#3f4763',
}

// Sorted, cyclic. Day holds until vanilla's light track starts falling at 11867 and
// night holds until it starts rising at 22330; the twilight keys sit at the peak of
// each SUNRISE_SUNSET_COLOR ramp.
const SKY_KEYS = [
  [400, DAY], [11867, DAY], [12800, TWILIGHT],
  [13670, NIGHT], [22330, NIGHT], [23300, TWILIGHT],
]

// Vanilla STAR_BRIGHTNESS keyframes, 0 to 0.5.
const STAR_KEYS = [
  [92, 0.037], [627, 0], [11373, 0], [11732, 0.016], [11959, 0.044], [12399, 0.143],
  [12729, 0.258], [13228, 0.5], [22772, 0.5], [23032, 0.364], [23356, 0.225], [23758, 0.101],
]

// Vanilla SKY_LIGHT_LEVEL multiplier, normalised so 1 is full day and 0 is full night.
const LIGHT_KEYS = [[133, 1], [11867, 1], [13670, 0], [22330, 0]]

// Locates tick between two keyframes of a cyclic track. Returns the two values and
// how far between them the tick sits.
function segment(keys, tick) {
  const t = ((tick % DAY_TICKS) + DAY_TICKS) % DAY_TICKS
  let hi = keys.findIndex(k => k[0] > t)
  if (hi === -1) hi = 0
  const lo = (hi - 1 + keys.length) % keys.length
  const span = (keys[hi][0] - keys[lo][0] + DAY_TICKS) % DAY_TICKS || DAY_TICKS
  const into = (t - keys[lo][0] + DAY_TICKS) % DAY_TICKS
  return [keys[lo][1], keys[hi][1], into / span]
}

function numberAt(keys, tick) {
  const [a, b, f] = segment(keys, tick)
  return a + (b - a) * f
}

function mixHex(a, b, f) {
  if (f === 0) return a
  if (f === 1) return b
  const ca = parseInt(a.slice(1), 16), cb = parseInt(b.slice(1), 16)
  const channel = (shift) => {
    const x = (ca >> shift) & 255, y = (cb >> shift) & 255
    return Math.round(x + (y - x) * f)
  }
  return `#${((1 << 24) | (channel(16) << 16) | (channel(8) << 8) | channel(0)).toString(16).slice(1)}`
}

// Crossfades two palettes, so a palette can grow a colour without touching skyAt.
function mixPalette(a, b, f) {
  const out = { bands: a.bands.map((c, i) => mixHex(c, b.bands[i], f)) }
  for (const key of Object.keys(a)) {
    if (key !== 'bands') out[key] = mixHex(a[key], b[key], f)
  }
  return out
}

function round(v, places = 3) {
  return Number(v.toFixed(places))
}

// Everything the scene needs at one tick: the palette, two opacities, and the sun and
// moon as unitless numbers (x is -1 east to 1 west, elev is 1 overhead, -1 underfoot).
export function skyAt(tick) {
  const [a, b, f] = segment(SKY_KEYS, tick)
  const light = numberAt(LIGHT_KEYS, tick)
  const stars = numberAt(STAR_KEYS, tick) / 0.5
  const theta = ((tick - 6000) / DAY_TICKS) * Math.PI * 2
  const sunX = Math.sin(theta), sunElev = Math.cos(theta)
  return {
    ...mixPalette(a, b, f),
    starOpacity: round(0.72 * stars),
    // The lantern reads as lit only once the sky stops competing with it.
    glow: round(0.14 + 0.46 * (1 - light)),
    // The scrim keeps the title readable, so it has to bite hardest at noon.
    scrim: round(0.34 + 0.3 * light),
    sunX: round(sunX, 4),
    sunElev: round(sunElev, 4),
    moonX: round(-sunX, 4),
    moonElev: round(-sunElev, 4),
  }
}
