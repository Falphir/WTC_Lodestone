import { CopyButton } from './CopyButton'
import { Panel } from './Panel'

/** Shown once right after a token is issued or reset -- the hub only ever stores its hash. */
export function TokenNotice({ title, server, onDone }) {
  const config = `serverId = "${server.id}"\ntoken = "${server.token}"`
  return (
    <Panel as="section" pad className="token-notice" aria-live="polite">
      <h2>{title}</h2>
      <p>
        Copy its token now. The hub only stores a hash of it, so it can't be shown again. Put these lines in{' '}
        <code>config/wtc_lodestone-common.toml</code> on that server and restart it.
      </p>
      <pre className="codeblock">{config}</pre>
      <div className="form-actions">
        <CopyButton text={config} label="Copy config" />
        <button type="button" className="button" onClick={onDone}>
          Done
        </button>
      </div>
    </Panel>
  )
}
