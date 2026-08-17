import styles from './WorldScene.module.css'

// A hand-drawn pixel-art dusk scene behind the World Header. Decoupled layers
// so nothing crops at any header width:
//   - sky   : a banded (hard-stop) dusk gradient filling the whole header
//   - moon / stars / clouds : positioned in the upper sky, always visible
//   - terrain : a wide, short SVG anchored to the bottom (hills never crop)

// Terrain is a very wide, short viewBox so slice-to-fill never cuts the hills.
const TW = 320
const TH = 26

function ridge(x, base, a1, f1, a2, f2, phase) {
  const v = base - a1 * Math.sin(x / f1 + phase) - a2 * Math.sin(x / f2 + phase * 1.7)
  return Math.round(v)
}

const COL = 4
const cols = Array.from({ length: Math.ceil(TW / COL) }, (_, i) => i * COL)
const farHill  = cols.map(x => ({ x, top: ridge(x, 11, 3, 80, 2, 26, 1.4) }))
const nearHill = cols.map(x => ({ x, top: ridge(x, 16, 4, 58, 3, 19, 0.5) }))

function groundAt(x) {
  return nearHill.reduce((b, c) => Math.abs(c.x - x) < Math.abs(b.x - x) ? c : b, nearHill[0]).top
}

// A blocky spruce rooted at (bx, groundY), drawn upward.
function spruce(bx, groundY, s = 1) {
  const p = []
  const r = (x, y, w, h) => p.push({ x: bx + x * s, y: groundY - y * s, w: w * s, h: h * s })
  r(-1, 3, 2, 3)   // trunk
  r(-5, 5, 10, 3)
  r(-4, 8, 8, 3)
  r(-3, 11, 6, 3)
  r(-2, 14, 4, 3)
  return p
}

const trees = [
  ...spruce(72, groundAt(72), 0.9),
  ...spruce(232, groundAt(232), 1.0),
]
const lanternX = 82
const lanternY = groundAt(lanternX) - 2

const stars = [
  ['12%', '22%', 2], ['20%', '46%', 1], ['33%', '16%', 1], ['44%', '38%', 2],
  ['58%', '20%', 1], ['66%', '52%', 1], ['74%', '30%', 1], ['88%', '18%', 2],
  ['95%', '44%', 1], ['7%', '58%', 1], ['50%', '62%', 1],
]

// A blocky pixel cloud.
function Cloud({ className }) {
  return (
    <svg className={className} viewBox="0 0 32 14" width={72} height={31} shapeRendering="crispEdges" aria-hidden="true">
      <rect x="4" y="4" width="20" height="4" fill="#d8b6c0" opacity="0.5" />
      <rect x="0" y="8" width="30" height="4" fill="#d8b6c0" opacity="0.5" />
      <rect x="8" y="0" width="12" height="4" fill="#e6c8b0" opacity="0.5" />
    </svg>
  )
}

export default function WorldScene() {
  return (
    <div className={styles.scene} aria-hidden="true">
      <div className={styles.sky} />

      {/* Moon */}
      <svg className={styles.moon} viewBox="0 0 16 16" width={26} height={26} shapeRendering="crispEdges">
        <rect x="4" y="1" width="8" height="14" fill="#e4e0f0" />
        <rect x="2" y="3" width="12" height="10" fill="#e4e0f0" />
        <rect x="1" y="5" width="14" height="6" fill="#e4e0f0" />
        <rect x="6" y="5" width="3" height="3" fill="#c7c2da" />
        <rect x="10" y="9" width="2" height="2" fill="#c7c2da" />
      </svg>

      {/* Stars */}
      {stars.map(([l, t, s], i) => (
        <span key={i} className={styles.star} style={{ left: l, top: t, width: s, height: s }} />
      ))}

      {/* Clouds */}
      <Cloud className={styles.cloudA} />
      <Cloud className={styles.cloudB} />

      {/* Terrain */}
      <svg className={styles.terrain} viewBox={`0 0 ${TW} ${TH}`} preserveAspectRatio="xMidYMax slice" shapeRendering="crispEdges">
        {farHill.map((c, i) => (
          <rect key={`f${i}`} x={c.x} y={c.top} width={COL} height={TH - c.top} fill="#2f3a4a" />
        ))}
        {nearHill.map((c, i) => (
          <g key={`n${i}`}>
            <rect x={c.x} y={c.top} width={COL} height={TH - c.top} fill="#33562f" />
            <rect x={c.x} y={c.top} width={COL} height={1.5} fill="#5a9c46" />
          </g>
        ))}
        {trees.map((t, i) => <rect key={`t${i}`} {...t} fill="#1e3019" />)}
        <rect x={lanternX - 2} y={lanternY - 2} width={5} height={5} fill="#ffcf6a" opacity={0.22} />
        <rect x={lanternX} y={lanternY} width={2} height={2} fill="#ffd77a" />
      </svg>
    </div>
  )
}
