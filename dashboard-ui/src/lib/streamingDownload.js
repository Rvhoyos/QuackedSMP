const SPEED_WINDOW_MS = 2000

export function hasFileSystemAccess() {
  return typeof window !== 'undefined' && 'showSaveFilePicker' in window
}

export async function streamingDownload({
  url,
  filename,
  headers = {},
  onProgress = () => {},
  allowBlobFallback = false,
}) {
  if (hasFileSystemAccess()) {
    return streamToDisk({ url, filename, headers, onProgress })
  }
  if (allowBlobFallback) {
    return streamToBlob({ url, filename, headers, onProgress })
  }
  return { ok: false, unsupported: true }
}

async function streamToDisk({ url, filename, headers, onProgress }) {
  let handle
  try {
    handle = await window.showSaveFilePicker({
      suggestedName: filename,
      types: [{ description: 'Zip archive', accept: { 'application/zip': ['.zip'] } }],
    })
  } catch (e) {
    if (e?.name === 'AbortError') return { ok: false, userCancelled: true }
    return { ok: false, error: e?.message || 'Save dialog failed' }
  }

  let writable
  try {
    writable = await handle.createWritable()
    const res = await fetch(url, { headers })
    if (!res.ok) {
      await writable.abort()
      return { ok: false, error: `Download failed (HTTP ${res.status})` }
    }
    const total = Number(res.headers.get('Content-Length')) || 0
    const reader = res.body.getReader()
    const samples = []
    let received = 0

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      await writable.write(value)
      received += value.length
      pushSample(samples, received)
      onProgress({ received, total, ...computeRate(samples, received, total) })
    }
    await writable.close()
    return { ok: true }
  } catch (e) {
    try { await writable?.abort() } catch { /* ignore */ }
    return { ok: false, error: e?.message || 'Download failed' }
  }
}

async function streamToBlob({ url, filename, headers, onProgress }) {
  try {
    const res = await fetch(url, { headers })
    if (!res.ok) return { ok: false, error: `Download failed (HTTP ${res.status})` }
    const total = Number(res.headers.get('Content-Length')) || 0
    const reader = res.body.getReader()
    const chunks = []
    const samples = []
    let received = 0

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      received += value.length
      pushSample(samples, received)
      onProgress({ received, total, ...computeRate(samples, received, total) })
    }

    const blob = new Blob(chunks, { type: 'application/zip' })
    const objectUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(objectUrl)
    return { ok: true }
  } catch (e) {
    return { ok: false, error: e?.message || 'Download failed' }
  }
}

function pushSample(samples, received) {
  const now = Date.now()
  samples.push({ t: now, bytes: received })
  while (samples.length > 1 && now - samples[0].t > SPEED_WINDOW_MS) samples.shift()
}

function computeRate(samples, received, total) {
  if (samples.length < 2) return { speed: 0, eta: null }
  const first = samples[0]
  const last = samples[samples.length - 1]
  const dt = (last.t - first.t) / 1000
  if (dt <= 0) return { speed: 0, eta: null }
  const speed = (last.bytes - first.bytes) / dt
  const eta = total > 0 && speed > 0 ? Math.max(0, (total - received) / speed) : null
  return { speed, eta }
}
