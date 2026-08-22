// Registry ids are all the server can give us; display names live in client translation files.
// Turning minecraft:golden_apple into "Golden Apple" matches vanilla for almost everything and
// keeps the namespace visible only when it is not minecraft.
export function prettyId(id) {
  if (!id) return ''
  const [namespace, path] = id.includes(':') ? id.split(':') : ['minecraft', id]
  const name = path.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
  return namespace === 'minecraft' ? name : `${name} (${namespace})`
}
