import { useState } from 'react'
import { api } from '../../api'
import { useAction, useApi } from '../../hooks'
import { Badge } from '../ui/Badge'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { Icon } from '../ui/Icon'

// Stop and kill throw everyone off the server, so they ask first; kill doesn't even save the world.
const CONFIRM = {
  stop: { title: 'Stop server?', label: 'Stop', text: (name) => `Stop ${name}? Everyone on it is disconnected.` },
  kill: {
    title: 'Kill server?',
    label: 'Kill',
    danger: true,
    text: (name) => `Kill ${name}? It's ended immediately, without saving. Use Stop unless it's stuck.`,
  },
}

/**
 * Start / stop / restart / kill for a server linked to PufferPanel, sitting in the server page's
 * header the way PufferPanel's own does. The hub makes the calls, so the browser never holds the
 * panel's credentials; the state is polled from the panel itself.
 */
export function ServerPower({ server }) {
  const path = `/api/admin/servers/${encodeURIComponent(server.id)}/power`
  const { data: power, error: statusError, reload } = useApi(path, { refreshMs: 5000, topics: ['servers'] })
  const { run, busy, message, error } = useAction(reload)
  const [confirming, setConfirming] = useState(null)

  async function act(action) {
    const ok = await run(async () => {
      await api(`${path}/${action}`, { method: 'POST' })
      return action === 'restart' ? 'Restarting: it starts again once it has stopped.' : null
    })
    if (ok) setConfirming(null)
  }

  const running = power?.running
  const restarting = power?.restarting
  const locked = busy || !power || restarting
  // one line under the buttons, so a failure or a restart in progress is never silent
  const note = (!confirming && error) || (!power && statusError) || message

  return (
    <div className="power">
      <div className="power-buttons">
        {restarting ? (
          <Badge tone="warn">Restarting…</Badge>
        ) : power ? (
          <Badge tone={running ? 'good' : 'none'} title="PufferPanel process">
            {running ? 'Running' : 'Stopped'}
          </Badge>
        ) : (
          statusError && <Badge tone="crit" title={statusError}>Unknown</Badge>
        )}
        <button type="button" className="button" disabled={locked || running} onClick={() => act('start')}>
          <Icon name="play" />
          Start
        </button>
        <button type="button" className="button" disabled={locked || !running} onClick={() => act('restart')}>
          <Icon name="restart" />
          Restart
        </button>
        <button type="button" className="button" disabled={locked || !running} onClick={() => setConfirming('stop')}>
          <Icon name="stop" />
          Stop
        </button>
        <button type="button" className="button button-danger" disabled={locked || !running} onClick={() => setConfirming('kill')}>
          <Icon name="kill" />
          Kill
        </button>
      </div>
      {note && <small className={error || statusError ? 'power-note power-note-error' : 'power-note muted'}>{note}</small>}

      {confirming && (
        <ConfirmDialog
          title={CONFIRM[confirming].title}
          confirmLabel={CONFIRM[confirming].label}
          danger={CONFIRM[confirming].danger}
          busy={busy}
          error={error}
          onCancel={() => setConfirming(null)}
          onConfirm={() => act(confirming)}
        >
          {CONFIRM[confirming].text(server.name)}
        </ConfirmDialog>
      )}
    </div>
  )
}
