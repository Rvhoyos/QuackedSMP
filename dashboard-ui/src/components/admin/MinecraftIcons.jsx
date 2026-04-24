// Custom pixel-art Minecraft SVG icons used across the admin panel.
// All icons are 16×16 grid pixel art, rendered at the requested size.

function px(x, y, w = 1, h = 1, fill) {
  return <rect key={`${x}-${y}`} x={x} y={y} width={w} height={h} fill={fill} />
}

// ── Tab bar icons ─────────────────────────────────────────────────────────────

export function IconPlayerHead({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Hair */}
      {px(3, 1, 10, 4, '#4C2A12')}
      {px(3, 2, 3, 1, '#6B3B1A')}
      {px(10, 2, 2, 1, '#6B3B1A')}
      {/* Skin */}
      {px(3, 5, 10, 7, '#C69B6D')}
      {/* Eyes */}
      {px(4, 6, 3, 2, '#3A2418')}
      {px(9, 6, 3, 2, '#3A2418')}
      {/* Eye shine */}
      {px(4, 6, 1, 1, '#FFFFFF')}
      {px(9, 6, 1, 1, '#FFFFFF')}
      {/* Mouth */}
      {px(5, 10, 6, 1, '#3A2418')}
      {px(6, 11, 4, 1, '#C69B6D')}
      {/* Ear hint */}
      {px(2, 5, 1, 5, '#B08050')}
      {px(13, 5, 1, 5, '#B08050')}
    </svg>
  )
}

export function IconCommandBlock({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Body */}
      {px(0, 0, 16, 16, '#C8721E')}
      {/* Grid lines */}
      {px(4, 0, 1, 16, '#8B3A0A')}
      {px(11, 0, 1, 16, '#8B3A0A')}
      {px(0, 4, 16, 1, '#8B3A0A')}
      {px(0, 11, 16, 1, '#8B3A0A')}
      {/* Center eye */}
      {px(5, 5, 6, 6, '#1A0800')}
      {px(6, 6, 4, 4, '#E03800')}
      {px(7, 7, 2, 2, '#FF7030')}
      {/* Top-left highlight */}
      {px(1, 1, 2, 2, '#EE9040')}
    </svg>
  )
}

export function IconRepeatCmdBlock({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Body */}
      {px(0, 0, 16, 16, '#8932B8')}
      {/* Grid lines */}
      {px(4, 0, 1, 16, '#5B1A8A')}
      {px(11, 0, 1, 16, '#5B1A8A')}
      {px(0, 4, 16, 1, '#5B1A8A')}
      {px(0, 11, 16, 1, '#5B1A8A')}
      {/* Center eye */}
      {px(5, 5, 6, 6, '#1A0020')}
      {px(6, 6, 4, 4, '#00C8C8')}
      {px(7, 7, 2, 2, '#40FFFF')}
      {/* Top-left highlight */}
      {px(1, 1, 2, 2, '#A850D8')}
    </svg>
  )
}

export function IconChest({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Lid */}
      {px(1, 1, 14, 6, '#B8773A')}
      {px(2, 2, 12, 1, '#C8883F')}
      {/* Lid hinge line */}
      {px(1, 6, 14, 1, '#5E3010')}
      {/* Body */}
      {px(1, 7, 14, 8, '#9A6028')}
      {/* Lock plate */}
      {px(6, 8, 4, 4, '#5E3010')}
      {/* Lock keyhole */}
      {px(7, 9, 2, 1, '#CDA040')}
      {px(7, 10, 2, 2, '#CDA040')}
      {/* Body highlight */}
      {px(2, 8, 12, 1, '#AB7030')}
    </svg>
  )
}

export function IconBookshelf({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Wood frame */}
      {px(0, 0, 16, 16, '#9A6B2E')}
      {/* Shelf lines */}
      {px(0, 5, 16, 1, '#6B4010')}
      {px(0, 10, 16, 1, '#6B4010')}
      {/* Top row books */}
      {px(1, 1, 2, 4, '#3A7ACC')}
      {px(4, 1, 2, 4, '#CC3A3A')}
      {px(7, 1, 2, 4, '#3ACC6A')}
      {px(10, 1, 2, 4, '#CCA83A')}
      {px(13, 1, 2, 4, '#9A3ACC')}
      {/* Mid row books */}
      {px(1, 6, 3, 4, '#CC3A3A')}
      {px(5, 6, 2, 4, '#3A7ACC')}
      {px(8, 6, 3, 4, '#CCA83A')}
      {px(12, 6, 3, 4, '#3ACC6A')}
      {/* Bottom row books */}
      {px(1, 11, 2, 4, '#9A3ACC')}
      {px(4, 11, 3, 4, '#CC3A3A')}
      {px(8, 11, 2, 4, '#3A7ACC')}
      {px(11, 11, 4, 4, '#CCA83A')}
    </svg>
  )
}

// ── Config section icons ──────────────────────────────────────────────────────

export function IconGrassBlock({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Grass top */}
      {px(0, 0, 16, 5, '#7EC850')}
      {/* Grass texture */}
      {px(2, 1, 1, 1, '#52A030')}
      {px(7, 2, 1, 1, '#52A030')}
      {px(11, 1, 1, 1, '#90D860')}
      {px(14, 2, 1, 1, '#52A030')}
      {/* Transition */}
      {px(0, 4, 16, 2, '#62A83D')}
      {/* Dirt */}
      {px(0, 6, 16, 10, '#976B4C')}
      {/* Dirt dots */}
      {px(2, 8, 2, 2, '#7B5335')}
      {px(8, 9, 2, 2, '#7B5335')}
      {px(13, 10, 2, 2, '#7B5335')}
      {px(5, 13, 2, 1, '#7B5335')}
      {px(11, 13, 2, 1, '#7B5335')}
    </svg>
  )
}

export function IconEnderPearl({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Shadow */}
      {px(4, 13, 8, 2, '#0A1A18')}
      {/* Pearl body */}
      {px(4, 2, 8, 1, '#1A4A40')}
      {px(2, 3, 12, 2, '#1D5548')}
      {px(1, 5, 14, 6, '#206050')}
      {px(2, 11, 12, 2, '#1A4A40')}
      {px(4, 13, 8, 1, '#123830')}
      {/* Shimmer */}
      {px(5, 4, 3, 2, '#2A8070')}
      {px(9, 6, 2, 2, '#2A8070')}
      {/* Highlight */}
      {px(6, 4, 2, 1, '#40B090')}
      {/* Glow dots */}
      {px(3, 7, 1, 1, '#30C0A0')}
      {px(12, 8, 1, 1, '#30C0A0')}
    </svg>
  )
}

export function IconBookQuill({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Book body */}
      {px(1, 3, 10, 12, '#7A4010')}
      {/* Spine */}
      {px(1, 3, 2, 12, '#5A2C08')}
      {/* Pages */}
      {px(3, 4, 7, 10, '#F5ECD0')}
      {/* Text lines */}
      {px(4, 6, 5, 1, '#CCC0A0')}
      {px(4, 8, 5, 1, '#CCC0A0')}
      {px(4, 10, 5, 1, '#CCC0A0')}
      {px(4, 12, 3, 1, '#CCC0A0')}
      {/* Quill feather */}
      {px(9, 1, 1, 1, '#F5F5DC')}
      {px(10, 2, 1, 1, '#E8E8C0')}
      {px(11, 3, 1, 1, '#D8D890')}
      {px(12, 4, 1, 1, '#C8C870')}
      {px(12, 5, 1, 2, '#B8B860')}
      {px(11, 5, 1, 1, '#E8E8C0')}
      {px(13, 3, 1, 1, '#F0F0D0')}
      {px(13, 4, 1, 3, '#E0E0B0')}
      {px(12, 7, 1, 2, '#A0A040')}
      {/* Quill tip */}
      {px(11, 9, 1, 1, '#808000')}
      {px(11, 10, 1, 1, '#404000')}
    </svg>
  )
}

export function IconXPOrb({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Glow halo */}
      {px(4, 1, 8, 1, '#2A5020')}
      {px(2, 2, 12, 1, '#2A6020')}
      {px(1, 3, 14, 1, '#306828')}
      {px(1, 12, 14, 1, '#306828')}
      {px(2, 13, 12, 1, '#2A6020')}
      {px(4, 14, 8, 1, '#2A5020')}
      {/* Orb body */}
      {px(3, 3, 10, 10, '#50B840')}
      {px(2, 5, 12, 6, '#58C848')}
      {px(4, 2, 8, 12, '#58C848')}
      {/* Inner bright */}
      {px(5, 5, 6, 6, '#7DFE74')}
      {px(4, 6, 8, 4, '#7DFE74')}
      {/* Core */}
      {px(6, 6, 4, 4, '#AAFFA0')}
      {/* Highlight */}
      {px(6, 5, 2, 2, '#CCFFCC')}
    </svg>
  )
}

export function IconBallot({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Box body */}
      {px(1, 8, 14, 7, '#9A7040')}
      {/* Box lid */}
      {px(0, 6, 16, 3, '#B08040')}
      {/* Slot */}
      {px(5, 7, 6, 1, '#3A2010')}
      {/* Paper going in */}
      {px(5, 3, 6, 6, '#F5F0E0')}
      {px(6, 4, 4, 1, '#E0D8C0')}
      {/* Lines on paper */}
      {px(6, 5, 4, 1, '#D0C8A0')}
      {px(6, 6, 3, 1, '#D0C8A0')}
      {/* Check mark */}
      {px(9, 4, 1, 1, '#3A8030')}
      {px(8, 5, 1, 1, '#3A8030')}
      {px(7, 6, 1, 1, '#3A8030')}
      {/* Box highlight */}
      {px(1, 8, 14, 1, '#C09050')}
      {px(1, 9, 1, 6, '#A07838')}
    </svg>
  )
}

export function IconDiscord({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Body */}
      {px(2, 2, 12, 10, '#5865F2')}
      {px(1, 4, 14, 7, '#5865F2')}
      {px(3, 1, 10, 1, '#5865F2')}
      {px(3, 12, 10, 2, '#5865F2')}
      {/* Tails */}
      {px(2, 13, 3, 3, '#5865F2')}
      {px(11, 13, 3, 3, '#5865F2')}
      {/* Eyes */}
      {px(4, 5, 3, 3, '#FFFFFF')}
      {px(9, 5, 3, 3, '#FFFFFF')}
      {px(5, 6, 1, 1, '#23272A')}
      {px(10, 6, 1, 1, '#23272A')}
      {/* Smile */}
      {px(5, 10, 1, 1, '#FFFFFF')}
      {px(6, 11, 4, 1, '#FFFFFF')}
      {px(10, 10, 1, 1, '#FFFFFF')}
    </svg>
  )
}

export function IconMapScroll({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Map background */}
      {px(2, 1, 12, 14, '#E8D8A0')}
      {/* Rolled edges */}
      {px(0, 1, 2, 14, '#C8B880')}
      {px(14, 1, 2, 14, '#C8B880')}
      {/* Terrain patches */}
      {px(3, 3, 4, 3, '#5A9040')}
      {px(8, 4, 3, 4, '#4A7030')}
      {px(4, 7, 3, 4, '#5A9040')}
      {/* Water */}
      {px(9, 8, 4, 3, '#4080C0')}
      {/* Compass rose */}
      {px(3, 10, 1, 3, '#C03020')}
      {px(2, 11, 3, 1, '#C03020')}
      {/* Road */}
      {px(5, 5, 1, 6, '#D0B860')}
      {/* Map edge curl */}
      {px(1, 2, 1, 12, '#B8A870')}
      {px(14, 2, 1, 12, '#B8A870')}
    </svg>
  )
}

export function IconShield({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Shield outer */}
      {px(3, 1, 10, 1, '#CC3030')}
      {px(2, 2, 12, 1, '#CC3030')}
      {px(1, 3, 14, 7, '#CC3030')}
      {px(2, 10, 12, 2, '#CC3030')}
      {px(3, 12, 10, 1, '#AA2020')}
      {px(5, 13, 6, 1, '#AA2020')}
      {px(6, 14, 4, 1, '#882010')}
      {px(7, 15, 2, 1, '#882010')}
      {/* Shield inner */}
      {px(3, 3, 10, 6, '#E84040')}
      {px(4, 9, 8, 2, '#E84040')}
      {/* Cross */}
      {px(7, 3, 2, 7, '#FFFFFF')}
      {px(4, 5, 8, 2, '#FFFFFF')}
      {/* Highlight */}
      {px(3, 3, 2, 2, '#FF6060')}
    </svg>
  )
}

export function IconVoiceChat({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Headphone band */}
      {px(4, 1, 8, 2, '#808080')}
      {px(2, 3, 2, 4, '#808080')}
      {px(12, 3, 2, 4, '#808080')}
      {/* Ear cups */}
      {px(1, 6, 4, 5, '#5865F2')}
      {px(11, 6, 4, 5, '#5865F2')}
      {/* Ear cup inner */}
      {px(2, 7, 2, 3, '#7080FF')}
      {px(12, 7, 2, 3, '#7080FF')}
      {/* Sound waves */}
      {px(6, 10, 1, 2, '#A0A0A0')}
      {px(9, 10, 1, 2, '#A0A0A0')}
      {px(7, 9, 2, 4, '#A0A0A0')}
    </svg>
  )
}

// ── Public dashboard icons ─────────────────────────────────────────────────────

export function IconDuck({ size = 32 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* White crest */}
      {px(8, 0, 1, 1, '#FFFDE7')}
      {px(7, 1, 3, 1, '#FFF9C4')}
      {/* Yellow head */}
      {px(4, 2, 7, 1, '#FDD835')}
      {px(3, 3, 9, 1, '#FDD835')}
      {px(3, 4, 8, 1, '#FDD835')}
      {px(3, 5, 7, 1, '#FDD835')}
      {/* Eye */}
      {px(5, 3, 2, 2, '#1A1A1A')}
      {px(5, 3, 1, 1, '#FFFFFF')}
      {/* Beak */}
      {px(11, 3, 3, 1, '#FF8C00')}
      {px(11, 4, 4, 1, '#FF8C00')}
      {px(11, 5, 3, 1, '#E07000')}
      {/* Neck */}
      {px(3, 6, 9, 1, '#FDD835')}
      {/* Body */}
      {px(1, 7, 12, 1, '#FDD835')}
      {px(1, 8, 13, 1, '#FDD835')}
      {px(1, 9, 12, 1, '#FDD835')}
      {px(2, 10, 11, 1, '#ECC820')}
      {px(3, 11, 9, 1, '#ECC820')}
      {px(4, 12, 7, 1, '#D4B000')}
      {/* Wing highlight */}
      {px(3, 8, 6, 2, '#FFE760')}
      {/* Tail */}
      {px(13, 8, 2, 3, '#D4A800')}
      {px(12, 11, 2, 1, '#D4A800')}
      {/* Feet */}
      {px(5, 13, 2, 1, '#FF8C00')}
      {px(9, 13, 2, 1, '#FF8C00')}
      {px(4, 14, 4, 1, '#FF6F00')}
      {px(8, 14, 4, 1, '#FF6F00')}
    </svg>
  )
}

export function IconClock({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Gold frame */}
      {px(4, 0, 8, 1, '#FFB300')}
      {px(2, 1, 12, 1, '#FFC107')}
      {px(1, 2, 1, 12, '#FFB300')}
      {px(14, 2, 1, 12, '#FFB300')}
      {px(2, 14, 12, 1, '#FFC107')}
      {px(4, 15, 8, 1, '#FFB300')}
      {/* Frame highlight */}
      {px(2, 2, 2, 2, '#FFD740')}
      {/* Clock face */}
      {px(3, 2, 10, 12, '#FFFDE7')}
      {px(2, 3, 12, 10, '#FFFDE7')}
      {/* Hour ticks */}
      {px(7, 2, 2, 1, '#BDBDBD')}
      {px(7, 13, 2, 1, '#BDBDBD')}
      {px(2, 7, 1, 2, '#BDBDBD')}
      {px(13, 7, 1, 2, '#BDBDBD')}
      {/* Hour hand (pointing ~2 o'clock) */}
      {px(8, 5, 1, 4, '#212121')}
      {/* Minute hand (pointing ~4 o'clock) */}
      {px(8, 8, 4, 1, '#212121')}
      {/* Center dot */}
      {px(7, 7, 2, 2, '#424242')}
    </svg>
  )
}

export function IconSword({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Blade */}
      {px(10, 0, 2, 1, '#ECEFF1')}
      {px(9, 1, 2, 1, '#E0E0E0')}
      {px(8, 2, 2, 1, '#D6D6D6')}
      {px(7, 3, 2, 1, '#CCCCCC')}
      {px(6, 4, 2, 1, '#C2C2C2')}
      {px(5, 5, 2, 1, '#B8B8B8')}
      {px(4, 6, 2, 1, '#AEAEAE')}
      {px(3, 7, 2, 1, '#A4A4A4')}
      {/* Blade highlight */}
      {px(11, 0, 1, 1, '#FFFFFF')}
      {px(10, 1, 1, 1, '#F5F5F5')}
      {px(9, 2, 1, 1, '#F0F0F0')}
      {/* Guard crosspiece */}
      {px(1, 9, 7, 2, '#B8860B')}
      {px(3, 8, 3, 1, '#D4A017')}
      {px(3, 11, 3, 1, '#9A7000')}
      {/* Guard gem */}
      {px(2, 9, 2, 2, '#E91E63')}
      {px(2, 9, 1, 1, '#FF4081')}
      {/* Handle */}
      {px(1, 11, 2, 4, '#6D4C41')}
      {px(1, 11, 1, 4, '#8D6E63')}
      {/* Pommel */}
      {px(0, 14, 3, 2, '#9E9E9E')}
      {px(0, 14, 1, 1, '#BDBDBD')}
    </svg>
  )
}

export function IconSpeedometer({ size = 20 }) {
  // 24x24, dial centered at (12,14), outer r=10, inner r=7.
  // 240° arc split into three 80° zones (SVG clockwise angles).
  // Green: 150°→230°, Yellow: 230°→310°, Red: 310°→30°.
  return (
    <svg viewBox="0 0 24 24" width={size} height={size}>
      <circle cx="12" cy="14" r="11" fill="#1A1A2A"/>
      {/* Green zone */}
      <path d="M3.34,19 A10,10 0 0,1 5.57,6.34 L7.5,8.64 A7,7 0 0,0 5.94,17.5 Z" fill="#2E7D32"/>
      {/* Yellow zone */}
      <path d="M5.57,6.34 A10,10 0 0,1 18.43,6.34 L16.5,8.64 A7,7 0 0,0 7.5,8.64 Z" fill="#F57F17"/>
      {/* Red zone */}
      <path d="M18.43,6.34 A10,10 0 0,1 20.66,19 L18.06,17.5 A7,7 0 0,0 16.5,8.64 Z" fill="#B71C1C"/>
      {/* Zone dividers */}
      <line x1="5.57"  y1="6.34" x2="7.5"  y2="8.64" stroke="#1A1A2A" strokeWidth="1.5"/>
      <line x1="18.43" y1="6.34" x2="16.5" y2="8.64" stroke="#1A1A2A" strokeWidth="1.5"/>
      {/* Needle — pointing to 12 o'clock (middle of yellow = normal range) */}
      <line x1="12" y1="14" x2="12" y2="5" stroke="#FFFFFF" strokeWidth="1.5" strokeLinecap="round"/>
      {/* Center cap */}
      <circle cx="12" cy="14" r="2.5" fill="#555555"/>
      <circle cx="12" cy="14" r="1"   fill="#AAAAAA"/>
    </svg>
  )
}

export function IconFurnace({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Stone body */}
      {px(0, 0, 16, 16, '#757575')}
      {/* Stone texture blocks */}
      {px(1, 1, 6, 3, '#8A8A8A')}
      {px(9, 1, 6, 3, '#8A8A8A')}
      {px(1, 6, 4, 4, '#8A8A8A')}
      {px(11, 6, 4, 4, '#8A8A8A')}
      {px(1, 12, 7, 3, '#8A8A8A')}
      {px(9, 13, 6, 2, '#8A8A8A')}
      {/* Mortar gaps */}
      {px(7, 1, 2, 3, '#616161')}
      {px(5, 6, 1, 4, '#616161')}
      {px(0, 4, 16, 2, '#616161')}
      {px(0, 10, 16, 2, '#616161')}
      {/* Furnace opening */}
      {px(3, 5, 10, 5, '#1A1A1A')}
      {/* Fire glow */}
      {px(5, 7, 2, 2, '#FF6D00')}
      {px(9, 7, 2, 2, '#FF6D00')}
      {px(6, 6, 4, 3, '#FF8F00')}
      {px(7, 5, 2, 2, '#FFC107')}
      {px(8, 5, 1, 1, '#FFEE58')}
      {/* Opening shadow */}
      {px(3, 5, 10, 1, '#2C2C2C')}
      {px(3, 5, 1, 5, '#2C2C2C')}
    </svg>
  )
}

export function IconPortal({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Obsidian frame */}
      {px(3,  0, 10, 2, '#1A0A2E')}
      {px(3, 14, 10, 2, '#1A0A2E')}
      {px(0,  2,  3, 12, '#1A0A2E')}
      {px(13, 2,  3, 12, '#1A0A2E')}
      {/* Frame edge highlight */}
      {px(3, 0, 10, 1, '#2D1B4E')}
      {px(0, 2,  1, 12, '#2D1B4E')}
      {/* Portal interior */}
      {px(3, 2, 10, 12, '#5B1A8C')}
      {px(4, 3,  8, 10, '#7A2DB5')}
      {px(5, 4,  6,  8, '#8E3FD4')}
      {/* Bright core */}
      {px(6, 5, 4, 6, '#A855F7')}
      {px(7, 6, 2, 4, '#C084FC')}
      {/* Shimmer flecks */}
      {px(4, 5, 1, 1, '#C084FC')}
      {px(10, 9, 1, 1, '#C084FC')}
      {px(5, 11, 1, 1, '#A855F7')}
    </svg>
  )
}

export function IconSkills({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Star body */}
      {px(7, 0, 2, 3, '#A855F7')}
      {px(4, 1, 2, 2, '#7C3AED')}
      {px(10, 1, 2, 2, '#7C3AED')}
      {px(0, 5, 16, 3, '#A855F7')}
      {px(2, 4, 12, 2, '#A855F7')}
      {px(3, 3, 10, 1, '#8B2FE6')}
      {/* Bottom points */}
      {px(4, 9, 3, 6, '#A855F7')}
      {px(9, 9, 3, 6, '#A855F7')}
      {px(3, 10, 2, 4, '#8B2FE6')}
      {px(11, 10, 2, 4, '#8B2FE6')}
      {/* Bright center */}
      {px(5, 5, 6, 4, '#C084FC')}
      {px(6, 4, 4, 1, '#C084FC')}
      {px(6, 4, 2, 1, '#E0AAFF')}
    </svg>
  )
}

export function IconFlag({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Pole */}
      {px(3, 0, 2, 16, '#888')}
      {px(3, 0, 1, 16, '#AAA')}
      {/* Flag */}
      {px(5, 1, 8, 1, '#4CAF50')}
      {px(5, 2, 9, 1, '#66BB6A')}
      {px(5, 3, 9, 1, '#66BB6A')}
      {px(5, 4, 9, 1, '#66BB6A')}
      {px(5, 5, 8, 1, '#66BB6A')}
      {px(5, 6, 7, 1, '#4CAF50')}
      {/* Diagonal tip */}
      {px(13, 2, 1, 1, '#388E3C')}
      {px(14, 3, 1, 2, '#388E3C')}
      {px(13, 5, 1, 1, '#388E3C')}
    </svg>
  )
}

export function IconChatFilter({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Chat bubble */}
      {px(1, 1, 14, 10, '#555')}
      {px(0, 2, 16, 8, '#555')}
      {/* Bubble tail */}
      {px(3, 11, 3, 1, '#555')}
      {px(3, 12, 2, 1, '#555')}
      {px(3, 13, 1, 1, '#555')}
      {/* X mark */}
      {px(4, 3, 2, 2, '#FF5555')}
      {px(8, 3, 2, 2, '#FF5555')}
      {px(6, 5, 2, 2, '#FF5555')}
      {px(4, 7, 2, 2, '#FF5555')}
      {px(8, 7, 2, 2, '#FF5555')}
    </svg>
  )
}

export function IconMod({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Crate body */}
      {px(1, 5, 14, 10, '#7B5E2A')}
      {px(0, 6, 16, 8, '#7B5E2A')}
      {/* Crate highlight top */}
      {px(1, 5, 14, 1, '#A07830')}
      {px(1, 5, 1, 9, '#A07830')}
      {/* Crate shadow bottom */}
      {px(1, 14, 14, 1, '#5A4018')}
      {px(14, 6, 1, 8, '#5A4018')}
      {/* Cross straps */}
      {px(7, 5, 2, 10, '#5A3E10')}
      {px(0, 9, 16, 2, '#5A3E10')}
      {/* Download arrow shaft */}
      {px(7, 0, 2, 5, '#7DFE74')}
      {/* Download arrow head */}
      {px(4, 3, 8, 1, '#7DFE74')}
      {px(5, 4, 6, 1, '#7DFE74')}
      {px(6, 5, 4, 1, '#7DFE74')}
      {px(7, 6, 2, 1, '#7DFE74')}
    </svg>
  )
}

// ── VIP tab icons ────────────────────────────────────────────────────────────

export function IconCrown({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Base band */}
      {px(2, 11, 12, 3, '#B8860B')}
      {px(2, 11, 12, 1, '#D4A017')}
      {px(2, 13, 12, 1, '#9A7000')}
      {/* Crown body */}
      {px(2, 6, 12, 5, '#FFD700')}
      {/* Three points */}
      {px(3, 3, 2, 3, '#FFD700')}
      {px(7, 1, 2, 5, '#FFD700')}
      {px(11, 3, 2, 3, '#FFD700')}
      {/* Point tips */}
      {px(3, 2, 2, 1, '#FFEC70')}
      {px(7, 0, 2, 1, '#FFEC70')}
      {px(11, 2, 2, 1, '#FFEC70')}
      {/* Center gem */}
      {px(7, 7, 2, 2, '#E91E63')}
      {px(7, 7, 1, 1, '#FF4081')}
      {/* Side gems */}
      {px(4, 8, 1, 1, '#4FC3F7')}
      {px(11, 8, 1, 1, '#4FC3F7')}
      {/* Highlight */}
      {px(3, 6, 2, 1, '#FFEC70')}
    </svg>
  )
}

export function IconHelmetGhost({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Crown */}
      {px(3, 1, 10, 2, '#666')}
      {px(2, 3, 12, 2, '#5A5A5A')}
      {/* Sides */}
      {px(1, 5, 14, 4, '#555')}
      {/* Brim */}
      {px(0, 9, 16, 2, '#666')}
      {/* Visor opening */}
      {px(3, 7, 10, 3, '#333')}
      {/* Nose guard */}
      {px(7, 7, 2, 2, '#5A5A5A')}
      {/* Top highlight */}
      {px(5, 1, 3, 1, '#777')}
    </svg>
  )
}

export function IconChestplateGhost({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Shoulders */}
      {px(0, 1, 4, 4, '#666')}
      {px(12, 1, 4, 4, '#666')}
      {/* Collar */}
      {px(4, 0, 8, 2, '#5A5A5A')}
      {/* Neck opening */}
      {px(5, 0, 6, 2, '#333')}
      {/* Torso */}
      {px(2, 3, 12, 10, '#555')}
      {/* Side taper */}
      {px(3, 13, 10, 2, '#555')}
      {/* Chest highlight */}
      {px(4, 4, 3, 2, '#666')}
      {/* Arm holes */}
      {px(0, 5, 2, 8, '#333')}
      {px(14, 5, 2, 8, '#333')}
    </svg>
  )
}

export function IconLeggingsGhost({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Waistband */}
      {px(2, 0, 12, 3, '#666')}
      {/* Belt */}
      {px(3, 1, 10, 1, '#777')}
      {/* Left leg */}
      {px(2, 3, 5, 12, '#555')}
      {px(3, 15, 4, 1, '#555')}
      {/* Right leg */}
      {px(9, 3, 5, 12, '#555')}
      {px(9, 15, 4, 1, '#555')}
      {/* Gap between legs */}
      {px(7, 6, 2, 10, '#333')}
      {/* Knee highlights */}
      {px(3, 9, 3, 1, '#666')}
      {px(10, 9, 3, 1, '#666')}
    </svg>
  )
}

export function IconBootsGhost({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Left boot shaft */}
      {px(1, 2, 5, 8, '#555')}
      {/* Left boot foot */}
      {px(0, 10, 7, 4, '#666')}
      {px(0, 14, 7, 2, '#5A5A5A')}
      {/* Left boot top */}
      {px(1, 2, 5, 1, '#777')}
      {/* Right boot shaft */}
      {px(10, 2, 5, 8, '#555')}
      {/* Right boot foot */}
      {px(9, 10, 7, 4, '#666')}
      {px(9, 14, 7, 2, '#5A5A5A')}
      {/* Right boot top */}
      {px(10, 2, 5, 1, '#777')}
    </svg>
  )
}

export function IconLoot({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Chest body */}
      {px(1, 7, 14, 8, '#9A6028')}
      {/* Chest body highlight */}
      {px(2, 8, 12, 1, '#AB7030')}
      {/* Hinge line */}
      {px(1, 7, 14, 1, '#5E3010')}
      {/* Lock plate */}
      {px(6, 9, 4, 3, '#5E3010')}
      {px(7, 10, 2, 1, '#CDA040')}
      {/* Open lid (angled up-left) */}
      {px(0, 2, 12, 5, '#B8773A')}
      {px(1, 3, 10, 1, '#C8883F')}
      {px(0, 6, 12, 1, '#5E3010')}
      {/* Loot: gold ingot poking out */}
      {px(3, 4, 3, 3, '#FFD700')}
      {px(3, 4, 3, 1, '#FFEC70')}
      {/* Loot: emerald */}
      {px(10, 3, 3, 4, '#50C878')}
      {px(10, 3, 3, 1, '#70E898')}
      {/* Loot: diamond peeking */}
      {px(7, 5, 2, 2, '#4FC3F7')}
    </svg>
  )
}

export function IconTierStar({ size = 16, color = '#cba6f7' }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Star body */}
      {px(6, 0, 4, 4, color)}
      {px(0, 5, 16, 5, color)}
      {px(2, 4, 12, 2, color)}
      {px(2, 10, 12, 2, color)}
      {/* Bottom points */}
      {px(3, 12, 4, 3, color)}
      {px(9, 12, 4, 3, color)}
      {px(4, 15, 2, 1, color)}
      {px(10, 15, 2, 1, color)}
      {/* Bright center highlight */}
      <rect key="hl1" x="5" y="5" width="6" height="4" fill="#FFFFFF" opacity="0.3" />
      <rect key="hl2" x="6" y="3" width="4" height="2" fill="#FFFFFF" opacity="0.2" />
    </svg>
  )
}

export function IconSign({ size = 20 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Sign board */}
      {px(1, 1, 14, 9, '#C8973A')}
      {/* Wood grain highlight */}
      {px(1, 1, 14, 1, '#D4A840')}
      {px(1, 5, 14, 1, '#B88030')}
      {/* Text lines */}
      {px(3, 3, 10, 1, '#3A2010')}
      {px(3, 6, 7, 1, '#3A2010')}
      {px(3, 8, 5, 1, '#3A2010')}
      {/* Shadow bottom */}
      {px(1, 9, 14, 1, '#9A7020')}
      {/* Post */}
      {px(7, 10, 2, 6, '#9A6B2E')}
      {px(7, 10, 1, 6, '#B07830')}
    </svg>
  )
}

export function IconEmerald({ size = 18 }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges">
      {/* Emerald gem shape */}
      {px(6, 1, 4, 1, '#2D9E2D')}
      {px(5, 2, 6, 1, '#3DBF3D')}
      {px(4, 3, 8, 2, '#50D050')}
      {px(3, 5, 10, 2, '#3DBF3D')}
      {px(3, 7, 10, 2, '#2D9E2D')}
      {px(4, 9, 8, 2, '#208020')}
      {px(5, 11, 6, 1, '#186018')}
      {px(6, 12, 4, 1, '#104010')}
      {/* Highlight */}
      {px(6, 3, 2, 1, '#80FF80')}
      {px(5, 5, 2, 1, '#60E060')}
    </svg>
  )
}
