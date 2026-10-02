import { getToken } from './api'

// One WebSocket for the whole dashboard. The hub only says *what* changed ({ topic, serverId });
// each page then refetches its own data through the normal API.

const listeners = new Set()
const statusListeners = new Set()
let socket = null
let retryTimer = null
let idleTimer = null
let attempts = 0
let connected = false

function setConnected(value) {
  connected = value
  statusListeners.forEach((l) => l())
}

function connect() {
  retryTimer = null
  const token = getToken()
  if (!token || listeners.size === 0) return
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  // browsers can't send an Authorization header on a WebSocket, so the hub accepts the token here instead
  socket = new WebSocket(`${protocol}://${window.location.host}/api/admin/live?access_token=${encodeURIComponent(token)}`)
  socket.onopen = () => {
    attempts = 0
    setConnected(true)
  }
  socket.onmessage = (message) => {
    let event
    try {
      event = JSON.parse(message.data)
    } catch {
      return
    }
    listeners.forEach((l) => l(event))
  }
  socket.onclose = () => {
    socket = null
    setConnected(false)
    // hub restarted or network blip: back off 1s, 2s, 4s… up to 30s. Pages keep polling meanwhile.
    if (listeners.size > 0) retryTimer = setTimeout(connect, Math.min(30_000, 1000 * 2 ** attempts++))
  }
}

/** Calls `listener(event)` for every change the hub announces. Returns the unsubscribe function. */
export function subscribe(listener) {
  listeners.add(listener)
  clearTimeout(idleTimer)
  if (!socket && !retryTimer) connect()
  return () => {
    listeners.delete(listener)
    // switching pages unsubscribes the old page just before the new one subscribes; keep the socket for that
    if (listeners.size === 0) idleTimer = setTimeout(disconnect, 2000)
  }
}

function disconnect() {
  clearTimeout(retryTimer)
  retryTimer = null
  if (socket) {
    socket.onclose = null
    socket.close()
    socket = null
    setConnected(false)
  }
}

export function subscribeStatus(listener) {
  statusListeners.add(listener)
  return () => statusListeners.delete(listener)
}

export const isConnected = () => connected
