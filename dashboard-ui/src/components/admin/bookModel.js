// Written book JSON <-> a flat, editable model.
//
// On the wire a page is a text component tree where children inherit the parent's style. The
// editor works on a flat list of segments instead, each carrying its own fully resolved style,
// which is what makes a per-segment toolbar possible. Writing back always produces an empty root
// with every segment spelled out in full, so nothing depends on inheritance and a round trip
// through the editor renders identically to what came in.

export const STYLE_KEYS = ['color', 'bold', 'italic', 'underlined', 'strikethrough', 'obfuscated']

export const MC_COLORS = [
  { id: 'black',        hex: '#000000' },
  { id: 'dark_blue',    hex: '#0000AA' },
  { id: 'dark_green',   hex: '#00AA00' },
  { id: 'dark_aqua',    hex: '#00AAAA' },
  { id: 'dark_red',     hex: '#AA0000' },
  { id: 'dark_purple',  hex: '#AA00AA' },
  { id: 'gold',         hex: '#FFAA00' },
  { id: 'gray',         hex: '#AAAAAA' },
  { id: 'dark_gray',    hex: '#555555' },
  { id: 'blue',         hex: '#5555FF' },
  { id: 'green',        hex: '#55FF55' },
  { id: 'aqua',         hex: '#55FFFF' },
  { id: 'red',          hex: '#FF5555' },
  { id: 'light_purple', hex: '#FF55FF' },
  { id: 'yellow',       hex: '#FFFF55' },
  { id: 'white',        hex: '#FFFFFF' },
]

// What a click actually does inside a book, traced through BookViewScreen.handleClickEvent and
// Screen.defaultHandleGameClickEvent rather than assumed:
//   change_page        -> the book turns the page itself
//   run_command        -> closes the book, then sendUnattendedCommand as the clicking player
//   open_url           -> a confirmation screen, then the system browser
//   copy_to_clipboard  -> keyboardHandler.setClipboard
//   suggest_command    -> calls activeScreen.insertText, which only ChatScreen implements.
//                         BookViewScreen does not, so in a book it does nothing. Not offered.
//   open_file          -> client only, refused when sent from a server. Not offered.
export const CLICK_ACTIONS = [
  { id: '', label: 'Nothing', short: 'not clickable', field: null,
    what: 'Clicking does nothing.' },
  { id: 'open_url', label: 'Open a link', short: 'opens a web page', field: 'url',
    what: 'The player is asked to confirm, then the page opens in their browser.' },
  { id: 'run_command', label: 'Run a command', short: 'the player runs it', field: 'command',
    what: 'Closes the book and runs the command as the clicking player, with their permissions. '
        + 'A player without permission gets nothing.' },
  { id: 'copy_to_clipboard', label: 'Copy text', short: 'copies to clipboard', field: 'value',
    what: 'Copies this text to the player\'s clipboard.' },
  { id: 'change_page', label: 'Turn to a page', short: 'jumps to a page', field: 'page',
    what: 'Turns to another page of this book.' },
]

export const GENERATIONS = ['Original', 'Copy of original', 'Copy of a copy', 'Tattered']

export function emptySegment(style = {}) {
  return { text: '', click: { action: '', value: '' }, hover: '', ...style }
}

export function parseBook(content) {
  const raw = content || {}
  const pages = (raw.pages || []).map(page => parsePage(unwrapFilterable(page)))
  return {
    title: String(unwrapFilterable(raw.title) ?? ''),
    author: String(raw.author ?? ''),
    generation: clampGeneration(raw.generation),
    pages: pages.length ? pages : [[emptySegment()]],
  }
}

export function buildBook(model) {
  return {
    title: { raw: model.title },
    author: model.author,
    generation: clampGeneration(model.generation),
    pages: model.pages.map(segments => ({ raw: buildPage(segments) })),
    resolved: true,
  }
}

// Filterable writes {raw, filtered?} and reads either that or the bare value.
function unwrapFilterable(value) {
  if (value && typeof value === 'object' && !Array.isArray(value) && 'raw' in value) return value.raw
  return value
}

function clampGeneration(value) {
  const n = Number(value)
  return Number.isFinite(n) ? Math.max(0, Math.min(GENERATIONS.length - 1, Math.trunc(n))) : 0
}

function parsePage(component) {
  const segments = []
  collect(component, {}, segments)
  return segments.length ? segments : [emptySegment()]
}

function collect(node, inherited, out) {
  if (node == null) return
  if (typeof node === 'string') {
    if (node !== '') out.push({ ...emptySegment(), ...inherited, text: node })
    return
  }
  if (Array.isArray(node)) {
    node.forEach(child => collect(child, inherited, out))
    return
  }

  // Children see the parent's style, so resolve this node's style against what it inherited.
  const style = { ...inherited }
  for (const key of STYLE_KEYS) {
    if (node[key] !== undefined && node[key] !== null) style[key] = normalise(key, node[key])
  }

  if (typeof node.text === 'string' && node.text !== '') {
    out.push({
      ...emptySegment(),
      ...style,
      text: node.text,
      click: parseClick(node.click_event),
      hover: parseHover(node.hover_event),
    })
  }

  ;(node.extra || []).forEach(child => collect(child, style, out))
}

// SNBT writes flags as 1b/0b, which arrive as booleans once vanilla has re-encoded them, but
// accept numbers too rather than silently reading 0 as truthy.
function normalise(key, value) {
  if (key === 'color') return String(value)
  if (typeof value === 'number') return value !== 0
  return Boolean(value)
}

function parseClick(event) {
  if (!event || !event.action) return { action: '', value: '' }
  const spec = CLICK_ACTIONS.find(a => a.id === event.action)
  if (!spec || !spec.field) return { action: '', value: '' }
  return { action: event.action, value: String(event[spec.field] ?? '') }
}

function parseHover(event) {
  if (!event || event.action !== 'show_text') return ''
  return plainText(event.value)
}

function plainText(node) {
  if (node == null) return ''
  if (typeof node === 'string') return node
  if (Array.isArray(node)) return node.map(plainText).join('')
  return (node.text ?? '') + (node.extra || []).map(plainText).join('')
}

function buildPage(segments) {
  const used = segments.filter(seg => seg.text !== '')
  return { text: '', extra: used.map(buildSegment) }
}

function buildSegment(seg) {
  const node = { text: seg.text }
  for (const key of STYLE_KEYS) {
    if (seg[key] !== undefined && seg[key] !== null && seg[key] !== false) node[key] = seg[key]
  }

  const spec = CLICK_ACTIONS.find(a => a.id === seg.click?.action)
  if (spec && spec.field && String(seg.click.value).trim() !== '') {
    // change_page takes a positive int; everything else takes a string.
    node.click_event = {
      action: spec.id,
      [spec.field]: spec.field === 'page'
        ? Math.max(1, parseInt(seg.click.value, 10) || 1)
        : String(seg.click.value),
    }
  }

  if (seg.hover && seg.hover.trim() !== '') {
    node.hover_event = { action: 'show_text', value: { text: seg.hover } }
  }
  return node
}
