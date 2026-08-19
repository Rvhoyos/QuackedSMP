import { useState, useEffect, useRef, useCallback } from 'react'

// Attaches the admin bearer token when present.
export function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// Central admin fetch. Attaches auth + JSON content-type, and maps an expired
// session (401/403) to onExpired() + a throw tagged { expired: true } so callers
// can ignore it without showing a spurious error.
export async function apiFetch(url, { token, onExpired, json, method, headers, ...opts } = {}) {
  const res = await fetch(url, {
    method: method || (json !== undefined ? 'POST' : 'GET'),
    headers: {
      ...(json !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...authHeaders(token),
      ...(headers || {}),
    },
    ...(json !== undefined ? { body: JSON.stringify(json) } : {}),
    ...opts,
  })
  if (res.status === 401 || res.status === 403) {
    onExpired?.()
    const e = new Error('session expired')
    e.expired = true
    throw e
  }
  return res
}

// Convenience: apiFetch + parse JSON. Throws on { error } payloads.
export async function apiJson(url, opts = {}) {
  const res = await apiFetch(url, opts)
  const d = await res.json().catch(() => ({}))
  if (d && d.error) { const e = new Error(d.error); e.payload = d; throw e }
  return d
}

/*
 * Polling resource loader.
 *
 * refresh({ silent }), a silent refresh does NOT toggle the loading flag, so a
 * background poll never flickers toolbars/buttons. Manual refreshes (silent
 * omitted) show the loading state. This is the flicker-free behavior that used
 * to be hand-rolled per panel; here it is the default for every panel.
 *
 * A 401/403 on any load calls onExpired and clears without surfacing an error.
 */
export function useResource(url, { token, onExpired, interval = 0, enabled = true } = {}) {
  const [data, setData]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState(null)

  const urlRef = useRef(url)
  urlRef.current = url

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true)
    try {
      const d = await apiJson(urlRef.current, { token, onExpired })
      setData(d)
      setError(null)
    } catch (e) {
      if (!e.expired) setError(e.message || 'Failed to load')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [token, onExpired])

  useEffect(() => {
    if (!enabled) return undefined
    load()
    if (interval > 0) {
      const id = setInterval(() => load({ silent: true }), interval)
      return () => clearInterval(id)
    }
    return undefined
  }, [load, interval, enabled])

  return { data, loading, error, setData, setError, refresh: load }
}
