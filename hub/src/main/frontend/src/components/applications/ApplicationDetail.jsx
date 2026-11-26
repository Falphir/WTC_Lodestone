import { useState } from 'react'
import { Panel } from '../ui/Panel'
import { Badge } from '../ui/Badge'
import { Field } from '../ui/Field'
import { RelativeTime } from '../ui/RelativeTime'
import { APPLICATION_ANSWERS, applicationAnswer, applicationStatus } from '../../format'

const iso = (ms) => new Date(ms).toISOString()

/**
 * One application read in full: its answers, its decision, and while it's pending the approve and
 * deny controls. A decision only takes effect in Discord once the bot picks it up, which it says.
 */
export function ApplicationDetail({ application: a, busy, onApprove, onDeny }) {
  const [reason, setReason] = useState('')
  const status = applicationStatus(a.status)

  function deny(event) {
    event.preventDefault()
    onDeny(reason.trim())
  }

  return (
    <Panel as="section" pad className="application" aria-labelledby="application-name">
      <header className="application-head">
        <div>
          <h2 id="application-name">{a.discordName || a.discordId}</h2>
          {a.discordName && <span className="cell-sub mono">{a.discordId}</span>}
        </div>
        <Badge tone={status.tone}>{status.label}</Badge>
      </header>

      <p className="muted">
        Applied <RelativeTime value={iso(a.createdAt)} />
        {a.decidedAt && (
          <>
            {', '}
            {a.status} <RelativeTime value={iso(a.decidedAt)} />
          </>
        )}
      </p>

      {a.botPending && (
        <p className="note">
          Waiting for the bot to {a.status === 'approved' ? 'give them the member role' : 'send them the news'} in
          Discord. It checks every 30 seconds.
        </p>
      )}

      {a.answers ? (
        <dl className="answers">
          {APPLICATION_ANSWERS.map(([key, label]) => (
            <div key={key}>
              <dt>{label}</dt>
              <dd>{applicationAnswer(key, a.answers[key]) ?? <span className="muted">Not answered</span>}</dd>
            </div>
          ))}
          {a.denyReason && (
            <div>
              <dt>Reason for denying</dt>
              <dd>{a.denyReason}</dd>
            </div>
          )}
        </dl>
      ) : (
        <p className="muted">
          This application was sent before the hub kept answers. They're in its post in the Discord applications
          channel.
        </p>
      )}

      {a.status === 'pending' && (
        <form className="application-decide" onSubmit={deny}>
          <Field label="Reason for denying" hint="Optional. The bot sends it to them in a DM.">
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} maxLength={500} />
          </Field>
          <div className="form-actions">
            <button type="button" className="button button-primary" disabled={busy} onClick={onApprove}>
              Approve
            </button>
            <button type="submit" className="button button-danger" disabled={busy}>
              Deny
            </button>
          </div>
        </form>
      )}
    </Panel>
  )
}
