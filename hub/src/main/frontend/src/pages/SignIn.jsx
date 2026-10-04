import { useState } from 'react'
import { signIn } from '../api'
import { ErrorNote, Field, Mark } from '../components'
import { useAction } from '../hooks'

export default function SignIn({ onSignedIn }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const { run, busy, error } = useAction()

  function submit(event) {
    event.preventDefault()
    run(async () => {
      await signIn(username.trim(), password)
      onSignedIn()
      return null
    })
  }

  return (
    <div className="signin">
      <form className="signin-panel" onSubmit={submit}>
        <Mark size={40} />
        <h1>Sign in to WTC Lodestone</h1>
        <p className="muted">Staff console for the WTC Network servers.</p>

        <Field label="Username">
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required autoFocus />
        </Field>
        <Field label="Password">
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </Field>

        <ErrorNote>{error}</ErrorNote>

        <button type="submit" className="button button-primary button-block" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
