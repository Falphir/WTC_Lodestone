import { useState } from 'react'
import { signIn } from '../api'
import { ErrorNote, Mark } from '../components'

export default function SignIn({ onSignedIn }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await signIn(username.trim(), password)
      onSignedIn()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <div className="signin">
      <form className="signin-panel" onSubmit={submit}>
        <Mark size={40} />
        <h1>Sign in to WTC Lodestone</h1>
        <p className="muted">Staff console for the WTC Network servers.</p>

        <label className="field">
          <span>Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required autoFocus />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        <ErrorNote>{error}</ErrorNote>

        <button type="submit" className="button button-primary button-block" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
