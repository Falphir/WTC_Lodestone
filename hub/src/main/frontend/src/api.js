// The hub's admin JWT is kept in localStorage so a refresh keeps you signed in. It expires on its
// own after 8 hours (or when the hub restarts); the password itself is never stored.
const TOKEN_KEY = 'lodestone-auth'

let onSignedOut = () => {}

/** App registers this so any 401, from any page, drops back to the sign-in screen. */
export function setSignedOutHandler(handler) {
  onSignedOut = handler
}

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

function storeToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // storage blocked: you'll just need to sign in again after a refresh
  }
}

/** Username of the signed-in admin, read from the token's subject. */
export function currentAdmin() {
  const token = getToken()
  if (!token) return null
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(payload)).sub
  } catch {
    return null
  }
}

export async function signIn(username, password) {
  const response = await send('/api/auth/token', {
    method: 'POST',
    headers: { Authorization: `Basic ${btoa(`${username}:${password}`)}` },
  })
  if (response.status === 401) throw new Error('Wrong username or password.')
  if (!response.ok) throw new Error(`Couldn't sign in (HTTP ${response.status}).`)
  const { token } = await response.json()
  storeToken(token)
}

export function signOut() {
  storeToken(null)
  onSignedOut()
}

/** Calls the hub API as the signed-in admin. Throws an Error with a readable message on failure. */
export async function api(path, { method = 'GET', body } = {}) {
  const headers = { Authorization: `Bearer ${getToken()}` }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await send(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (response.status === 401) {
    signOut()
    throw new Error('Your session expired. Sign in again.')
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message || `Request failed (HTTP ${response.status}).`)
  }
  return response.status === 204 ? null : response.json()
}

async function send(path, options) {
  try {
    return await fetch(path, options)
  } catch {
    throw new Error("Can't reach the hub. Check that it's running.")
  }
}
