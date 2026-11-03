import { useState } from 'react'
import { api } from '../../api'
import { useAction, useApi } from '../../hooks'
import { Dialog } from '../ui/Dialog'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'

/**
 * Creates a server on PufferPanel from one of its templates and registers it on the hub in one
 * step. The settings are whatever the chosen template defines, with its defaults filled in, so a
 * template added to the panel later works here without any change.
 */
export function CreateServerDialog({ onClose, onCreated }) {
  const { data: templates, error: templatesError } = useApi('/api/admin/pufferpanel/templates')
  const { data: nodes } = useApi('/api/admin/pufferpanel/nodes')
  const [template, setTemplate] = useState('')
  const templatePath = template ? `/api/admin/pufferpanel/templates/${encodeURIComponent(template)}` : null
  const { data: detail, stale } = useApi(templatePath)
  const variables = template && !stale ? (detail?.variables ?? []) : []

  const [id, setId] = useState('')
  const [name, setName] = useState('')
  const [nodeId, setNodeId] = useState('')
  // only what was changed; everything else keeps the template's default on the hub side
  const [values, setValues] = useState({})
  const { run, busy, error } = useAction()

  function pickTemplate(next) {
    setTemplate(next)
    setValues({})
  }

  function submit(event) {
    event.preventDefault()
    run(async () => {
      const body = { template, id, name: name.trim(), values, nodeId: nodeId === '' ? null : Number(nodeId) }
      onCreated(await api('/api/admin/pufferpanel/servers', { method: 'POST', body }))
      return null
    })
  }

  return (
    <Dialog onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <h2>New server</h2>
        <p className="muted">
          Creates it on PufferPanel and registers it here, with its mod config and the WTC Lodestone mod already in
          place. Upload the modpack's server files in PufferPanel afterwards.
        </p>

        <div className="form-row">
          <Field label="Display name">
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="WTC Genesis" maxLength={40} required autoFocus />
          </Field>
          <Field label="Server ID" hint="Its serverId in the mod config. Lowercase letters, numbers, _ and -.">
            <input
              className="mono"
              value={id}
              onChange={(e) => setId(e.target.value.toLowerCase())}
              placeholder="genesis"
              pattern="[a-z0-9_\-]{1,64}"
              title="Lowercase letters, numbers, _ and - only"
              maxLength={64}
              required
            />
          </Field>
        </div>

        <div className="form-row">
          <Field label="Template">
            <select value={template} onChange={(e) => pickTemplate(e.target.value)} required>
              <option value="" disabled>
                {templates ? 'Pick a template' : 'Loading…'}
              </option>
              {templates?.map((t) => (
                <option key={t.name} value={t.name}>
                  {t.display}
                </option>
              ))}
            </select>
          </Field>
          {nodes?.length > 1 && (
            <Field label="Node">
              <select value={nodeId} onChange={(e) => setNodeId(e.target.value)}>
                <option value="">{nodes[0].name}</option>
                {nodes.slice(1).map((n) => (
                  <option key={n.id} value={n.id}>
                    {n.name}
                  </option>
                ))}
              </select>
            </Field>
          )}
        </div>

        {variables.length > 0 && (
          <div className="form-row">
            {variables.map((v) => (
              <TemplateSetting
                key={v.key}
                variable={v}
                value={v.key in values ? values[v.key] : v.value}
                onChange={(value) => setValues((current) => ({ ...current, [v.key]: value }))}
              />
            ))}
          </div>
        )}

        <ErrorNote>{error || templatesError}</ErrorNote>
        <div className="form-actions">
          <button type="submit" className="button button-primary" disabled={busy || !template || stale}>
            {busy ? 'Creating…' : 'Create server'}
          </button>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Dialog>
  )
}

/** One of a template's settings, as the input its type calls for. */
function TemplateSetting({ variable: v, value, onChange }) {
  let input
  if (v.options?.length > 0) {
    input = (
      <select value={value ?? ''} onChange={(e) => onChange(e.target.value)} required={v.required}>
        {v.options.map((o) => (
          <option key={String(o.value)} value={o.value}>
            {o.display || String(o.value)}
          </option>
        ))}
      </select>
    )
  } else if (v.type === 'boolean') {
    input = <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
  } else if (v.type === 'integer') {
    input = (
      <input
        type="number"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
        required={v.required}
      />
    )
  } else {
    input = <input value={value ?? ''} onChange={(e) => onChange(e.target.value)} required={v.required} />
  }
  return (
    <Field label={v.display} hint={v.desc}>
      {input}
    </Field>
  )
}
