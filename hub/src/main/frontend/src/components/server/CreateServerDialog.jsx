import { useState } from 'react'
import { api } from '../../api'
import { useAction, useApi } from '../../hooks'
import { Dialog } from '../ui/Dialog'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { Icon } from '../ui/Icon'

const STEPS = ['Basics', 'Settings', 'Review']

/**
 * Creates a server on PufferPanel from one of its templates and registers it on the hub in one
 * step. Split into steps so a template with a dozen settings doesn't make one endless form: the
 * step bar and the buttons stay put, and only the middle scrolls. Each step is its own pass of
 * the browser's own validation, since only the current step's fields are on the page.
 */
export function CreateServerDialog({ onClose, onCreated }) {
  const { data: templates, error: templatesError } = useApi('/api/admin/pufferpanel/templates')
  const { data: nodes } = useApi('/api/admin/pufferpanel/nodes')
  const [step, setStep] = useState(0)
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

  const valueOf = (v) => (v.key in values ? values[v.key] : v.value)
  const templateName = templates?.find((t) => t.name === template)?.display ?? template
  const nodeName = nodes?.find((n) => String(n.id) === nodeId)?.name ?? nodes?.[0]?.name

  function pickTemplate(next) {
    setTemplate(next)
    setValues({})
  }

  function submit(event) {
    event.preventDefault()
    if (step < STEPS.length - 1) {
      setStep(step + 1)
      return
    }
    run(async () => {
      const body = { template, id, name: name.trim(), values, nodeId: nodeId === '' ? null : Number(nodeId) }
      onCreated(await api('/api/admin/pufferpanel/servers', { method: 'POST', body }))
      return null
    })
  }

  return (
    <Dialog onClose={onClose} className="dialog-wizard">
      <header className="wizard-head">
        <h2>New server</h2>
        <ol className="wizard-steps">
          {STEPS.map((label, i) => (
            <li key={label} aria-current={i === step ? 'step' : undefined} className={i < step ? 'done' : undefined}>
              <span className="wizard-step-number">{i < step ? <Icon name="check" /> : i + 1}</span>
              {label}
            </li>
          ))}
        </ol>
      </header>

      <form className="wizard-form" onSubmit={submit}>
        <div className="wizard-body">
          {step === 0 && (
            <>
              <p className="muted">
                Creates it on PufferPanel and registers it here, with its mod config and the WTC Lodestone mod already in
                place. You upload the modpack's server files in PufferPanel afterwards.
              </p>
              <div className="form-row">
                <Field label="Display name">
                  <input value={name} onChange={(e) => setName(e.target.value)} placeholder="WTC Genesis" maxLength={40} required autoFocus />
                </Field>
                <Field label="Server ID" hint="Its serverId in the mod config.">
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
            </>
          )}

          {step === 1 &&
            (variables.length > 0 ? (
              <div className="wizard-grid">
                {variables.map((v) => (
                  <TemplateSetting
                    key={v.key}
                    variable={v}
                    value={valueOf(v)}
                    onChange={(value) => setValues((current) => ({ ...current, [v.key]: value }))}
                  />
                ))}
              </div>
            ) : (
              <p className="muted">{stale ? 'Loading the template…' : 'This template has nothing to set.'}</p>
            ))}

          {step === 2 && (
            <dl className="wizard-review">
              <dt>Display name</dt>
              <dd>{name}</dd>
              <dt>Server ID</dt>
              <dd className="mono">{id}</dd>
              <dt>Template</dt>
              <dd>{templateName}</dd>
              {nodes?.length > 1 && (
                <>
                  <dt>Node</dt>
                  <dd>{nodeName}</dd>
                </>
              )}
              {variables.map((v) => (
                <ReviewRow key={v.key} label={v.display} value={valueOf(v)} />
              ))}
            </dl>
          )}

          <ErrorNote>{error || templatesError}</ErrorNote>
        </div>

        <footer className="wizard-foot">
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
          <div className="button-row">
            {step > 0 && (
              <button type="button" className="button" disabled={busy} onClick={() => setStep(step - 1)}>
                Back
              </button>
            )}
            <button type="submit" className="button button-primary" disabled={busy || (step === 0 && !template) || (step > 0 && stale)}>
              {step < STEPS.length - 1 ? 'Next' : busy ? 'Creating…' : 'Create server'}
            </button>
          </div>
        </footer>
      </form>
    </Dialog>
  )
}

/** Template descriptions are written for PufferPanel's own page and can carry HTML; here they're plain hints. */
const plain = (text) => text?.replace(/<[^>]*>/g, '').trim() || undefined

/** One of a template's settings, as the input its type calls for. */
function TemplateSetting({ variable: v, value, onChange }) {
  if (v.type === 'boolean' && !(v.options?.length > 0)) {
    return (
      <div className="field">
        <label className="check-field">
          <input type="checkbox" checked={value === true || value === 'true'} onChange={(e) => onChange(e.target.checked)} />
          <span>{v.display}</span>
        </label>
        {plain(v.desc) && <small>{plain(v.desc)}</small>}
      </div>
    )
  }
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
    <Field label={v.display} hint={plain(v.desc)}>
      {input}
    </Field>
  )
}

function ReviewRow({ label, value }) {
  const shown = value === true || value === 'true' ? 'Yes' : value === false || value === 'false' ? 'No' : value === '' || value == null ? '—' : String(value)
  return (
    <>
      <dt>{label}</dt>
      <dd>{shown}</dd>
    </>
  )
}
