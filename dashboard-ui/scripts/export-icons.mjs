// Exports every pixel icon in src/components/admin/MinecraftIcons.jsx to a standalone
// .svg file under common/src/main/resources/bluemap/icons/, so the BlueMap integration
// and the admin panel share one source of art. Edit the JSX, both update.
//
// Run: npm run export-icons  (wired into the Gradle build, see build.gradle)
//
// Transforms the JSX with esbuild (already present via vite) rather than spinning up a
// dev server, which would try to crawl the whole app just to read one leaf module.

import { transform } from 'esbuild'
import { renderToStaticMarkup } from 'react-dom/server'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const uiDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const srcFile = resolve(uiDir, 'src/components/admin/MinecraftIcons.jsx')
const outDir = resolve(uiDir, '../common/src/main/resources/bluemap/icons')

// The three UI-chrome icons (collapse/expand/close) paint with currentColor, which has no
// meaning in a standalone file. They are not part of the map tag vocabulary, but they are
// exported for completeness, so give them the panel's default text colour.
const FALLBACK_COLOR = '#cdd6f4'

// Rendered pixel size of the exported files. BlueMap takes the display size from the SVG itself
// (the two ints on POIMarker.icon are the anchor, not a size), so exporting at the 16 unit grid
// size would put 16px icons on the map. The viewBox keeps them crisp when scaled.
const EXPORT_SIZE = 64

// Component name to file name: IconShulkerBox -> shulker-box.svg, IconXPOrb -> xp-orb.svg.
// The acronym rule matters: without it XPOrb collapses to "xporb", which then silently fails
// to resolve at runtime.
function fileNameFor(componentName) {
  return componentName
    .replace(/^Icon/, '')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2')
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .toLowerCase() + '.svg'
}

const { code } = await transform(await readFile(srcFile, 'utf8'), {
  loader: 'jsx',
  jsx: 'automatic',
  format: 'esm',
})

// Written next to the source so node resolves react/jsx-runtime from the normal node_modules.
const tmpFile = resolve(uiDir, 'src/components/admin/.icons-export.tmp.mjs')
await writeFile(tmpFile, code, 'utf8')

try {
  const mod = await import(tmpFile)

  await rm(outDir, { recursive: true, force: true })
  await mkdir(outDir, { recursive: true })

  let count = 0
  for (const [name, Component] of Object.entries(mod)) {
    if (!name.startsWith('Icon') || typeof Component !== 'function') continue

    // One size for every icon, regardless of the component's own default (which is tuned for
    // wherever it sits in the panel).
    // renderToStaticMarkup omits xmlns (the browser infers it for inline SVG), but a
    // standalone file loaded through <img> is parsed as a document and needs it.
    const svg = renderToStaticMarkup(Component({ size: EXPORT_SIZE }))
      .replaceAll('currentColor', FALLBACK_COLOR)
      .replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" ')

    await writeFile(resolve(outDir, fileNameFor(name)), svg + '\n', 'utf8')
    count++
  }

  console.log(`[icons] exported ${count} SVGs -> ${outDir}`)
} finally {
  await rm(tmpFile, { force: true })
}
