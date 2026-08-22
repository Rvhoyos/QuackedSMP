// Shared model for every editor that stores an ItemStack: random teleport arrivals, kits, and
// whatever stores one next. Nothing here knows which feature is holding the stack.
//
// A stack travels as ItemStack.CODEC JSON, which vanilla defines as {id, count, components}
// with count constrained to intRange(1, 99) (ItemStack.MAP_CODEC). Components are plain JSON,
// so a written book or a custom name is directly editable in the browser.

export const BOOK_COMPONENT = 'minecraft:written_book_content'
const NAME_COMPONENT = 'minecraft:custom_name'

// The count bounds are the codec's own, not a taste call.
const MIN_COUNT = 1
const MAX_COUNT = 99

export const BLANK_BOOK = {
  title: { raw: '' },
  author: '',
  generation: 0,
  pages: [{ raw: { text: '', extra: [] } }],
  resolved: true,
}

export function clampCount(raw) {
  const n = parseInt(raw, 10)
  return Number.isFinite(n) ? Math.max(MIN_COUNT, Math.min(MAX_COUNT, n)) : MIN_COUNT
}

export function newStack(id, count = 1) {
  return { id, count: clampCount(count) }
}

export function getComponent(stack, key) {
  return stack?.components?.[key]
}

/** Returns a copy of the stack with one component set, or removed when value is undefined. */
export function withComponent(stack, key, value) {
  const components = { ...(stack?.components || {}) }
  if (value === undefined) delete components[key]
  else components[key] = value

  const next = { ...stack }
  if (Object.keys(components).length === 0) delete next.components
  else next.components = components
  return next
}

/** How many components a stack carries beyond its id and count, for the row's badge. */
export function componentCount(stack) {
  return Object.keys(stack?.components || {}).length
}

// ── Custom name ─────────────────────────────────────────────────────────────
//
// minecraft:custom_name is a full text component (DataComponents.CUSTOM_NAME persists with
// ComponentSerialization.CODEC), so a name can carry colour and formatting.
//
// The catch, from ItemStack.getStyledHoverName(): vanilla builds the displayed name as an empty
// parent with the rarity colour, then adds ChatFormatting.ITALIC to that parent whenever a
// custom name is present. Style is inherited, so a name renders italic unless it says otherwise.
// writeName therefore always states italic outright rather than leaving it off, which is the
// opposite of what bookModel does for book text, where nothing forces a style from above.

export function readName(stack) {
  const raw = getComponent(stack, NAME_COMPONENT)
  if (raw == null) return null
  if (typeof raw === 'string') return { text: raw, color: '', bold: false, italic: false, underlined: false }
  return {
    text: String(raw.text ?? ''),
    color: typeof raw.color === 'string' ? raw.color : '',
    bold: !!raw.bold,
    italic: !!raw.italic,
    underlined: !!raw.underlined,
  }
}

export function writeName(stack, name) {
  if (!name || name.text.trim() === '') return withComponent(stack, NAME_COMPONENT, undefined)

  const component = { text: name.text, italic: !!name.italic }
  if (name.color) component.color = name.color
  if (name.bold) component.bold = true
  if (name.underlined) component.underlined = true
  return withComponent(stack, NAME_COMPONENT, component)
}

/** The name to show in the panel: the custom name's text when there is one, else the item id. */
export function displayName(stack, prettyId) {
  const name = readName(stack)
  if (name && name.text.trim() !== '') return name.text
  return prettyId(stack?.id) || 'Unknown item'
}
