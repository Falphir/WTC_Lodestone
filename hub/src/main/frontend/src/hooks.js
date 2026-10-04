import { useCallback, useEffect, useState, useSyncExternalStore } from 'react'
import { api } from './api'
import { isConnected, subscribe, subscribeStatus } from './live'

/** The current route, from the URL hash: '#/servers/genesis' -> ['servers', 'genesis']. */
export function useRoute() {
  const hash = useSyncExternalStore(
    (onChange) => {
      window.addEventListener('hashchange', onChange)
      return () => window.removeEventListener('hashchange', onChange)
    },
    () => window.location.hash,
  )
  return hash.replace(/^#\/?/, '').split('/').filter(Boolean).map(decodeURIComponent)
}

/**
 * Loads `path` from the hub API. It reloads when the hub announces a change on one of `topics`
 * (narrowed to `serverId` if given), and every `refreshMs` as a fallback for when the live
 * connection is down. While a new path loads, the previous data stays available (`stale` is true).
 */
export function useApi(path, { refreshMs, topics = [], serverId } = {}) {
  const [state, setState] = useState({ path: null, data: null, error: null })
  const [version, setVersion] = useState(0)
  const topicKey = topics.join(',')

  useEffect(() => {
    let cancelled = false
    const load = () =>
      api(path).then(
        (data) => !cancelled && setState({ path, data, error: null }),
        (error) => !cancelled && setState((s) => ({ ...s, path, error: error.message })),
      )
    load()

    const timer = refreshMs ? setInterval(load, refreshMs) : null
    let debounce = null
    const unsubscribe = topicKey
      ? subscribe((event) => {
          if (!topicKey.split(',').includes(event.topic)) return
          if (serverId && event.serverId && event.serverId !== serverId) return
          // several servers can report at once; reload once for the burst
          clearTimeout(debounce)
          debounce = setTimeout(load, 300)
        })
      : null

    return () => {
      cancelled = true
      clearInterval(timer)
      clearTimeout(debounce)
      unsubscribe?.()
    }
  }, [path, refreshMs, topicKey, serverId, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { data: state.data, error: state.error, stale: state.path !== path, reload }
}

/**
 * The busy/message/error dance every mutating form or action button here does: run `action`,
 * show what it returns as a success message, show a thrown Error's message instead, and track
 * busy throughout. Optionally reloads (e.g. a useApi `reload`) after a successful run.
 */
export function useAction(reload) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)

  const run = useCallback(
    async (action) => {
      setBusy(true)
      setError(null)
      setMessage(null)
      try {
        setMessage(await action())
        reload?.()
        return true
      } catch (err) {
        setError(err.message)
        return false
      } finally {
        setBusy(false)
      }
    },
    [reload],
  )

  return { run, busy, message, error, setError }
}

/** Whether live updates from the hub are currently connected. */
export function useLiveStatus() {
  return useSyncExternalStore(subscribeStatus, isConnected)
}

/** Hub timing (heartbeat interval, offline threshold); fetched once, it only changes when the hub is reconfigured. */
let infoPromise = null
export function useHubInfo() {
  const [info, setInfo] = useState(null)
  useEffect(() => {
    infoPromise ??= api('/api/info').catch((error) => {
      infoPromise = null
      throw error
    })
    let cancelled = false
    infoPromise.then((i) => !cancelled && setInfo(i), () => {})
    return () => {
      cancelled = true
    }
  }, [])
  return info
}

const THEME_KEY = 'lodestone-theme'

/** 'system' follows the OS; 'light' / 'dark' pin it. Remembered per browser. */
export function useTheme() {
  const [theme, setTheme] = useState(() => {
    try {
      return localStorage.getItem(THEME_KEY) || 'system'
    } catch {
      return 'system'
    }
  })

  useEffect(() => {
    if (theme === 'system') document.documentElement.removeAttribute('data-theme')
    else document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch {
      // not remembered, that's fine
    }
  }, [theme])

  return [theme, setTheme]
}
