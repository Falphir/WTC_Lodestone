import { useState } from 'react'
import { api } from '../../api'
import { minecraftVersions, packRunsOn, slugify } from '../../format'
import { useAction, useApi } from '../../hooks'
import { Dialog } from '../ui/Dialog'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { Icon } from '../ui/Icon'
import { Note } from '../ui/Note'
import { ModpackPicker } from './ModpackPicker'

const STEPS = {
  blank: ['Basics', 'Settings', 'Review'],
  modpack: ['Modpack', 'Basics', 'Settings', 'Review'],
}

/**
 * Creates a server on PufferPanel from one of its templates and registers it on the hub in one
 * step. Split into steps so a template with a dozen settings doesn't make one endless form: the
 * step bar and the buttons stay put, and only the middle scrolls. Each step is its own pass of
 * the browser's own validation, since only the current step's fields are on the page.
 * <p>
 * With a CurseForge API key on the hub it opens on a choice: a blank server, or one from a modpack.
 * From a modpack, the pack comes first, since it decides the rest: leaving that step fills in the
 * name, id, template and the template's versions from the pack (all still editable).
 */
export function CreateServerDialog({ onClose, onCreated }) {
  const { data: templates, error: templatesError } = useApi('/api/admin/pufferpanel/templates')
  const { data: nodes } = useApi('/api/admin/pufferpanel/nodes')
  const { data: curseForge, error: curseForgeError } = useApi('/api/admin/curseforge')
  const canChoose = Boolean(curseForge?.configured)
  // null: still on the opening choice
  const [chosen, setChosen] = useState(null)
  const path = canChoose ? chosen : 'blank'
  const steps = path ? STEPS[path] : []
  const [step, setStep] = useState(0)
  const current = steps[step]
  const last = step === steps.length - 1

  const [modpack, setModpack] = useState(null)
  const setupPath = path === 'modpack' && modpack?.file
    ? `/api/admin/curseforge/modpacks/${modpack.pack.id}/files/${modpack.file.fileId}/setup`
    : null
  const { data: setup, stale: setupStale, error: setupError } = useApi(setupPath)

  const [template, setTemplate] = useState('')
  const templatePath = template ? `/api/admin/pufferpanel/templates/${encodeURIComponent(template)}` : null
  const { data: detail, stale } = useApi(templatePath)
  const variables = template && !stale ? (detail?.variables ?? []) : []

  const [id, setId] = useState('')
  const [name, setName] = useState('')
  // what the pack last filled in, so picking another pack replaces it but never something typed
  const [filled, setFilled] = useState({ id: '', name: '' })
  const [nodeId, setNodeId] = useState('')
  // only what was changed; everything else keeps the template's default on the hub side
  const [values, setValues] = useState({})
  const { run, busy, error } = useAction()

  const valueOf = (v) => (v.key in values ? values[v.key] : v.value)
  const templateName = templates?.find((t) => t.name === template)?.display ?? template
  const nodeName = nodes?.find((n) => String(n.id) === nodeId)?.name ?? nodes?.[0]?.name
  // the pack's suggestion applies to its own template only, and a setting stops being "from the pack" once changed
  const fromPack = (key) => setup?.template === template && key in (setup?.values ?? {}) && values[key] === setup.values[key]

  function pickTemplate(next) {
    setTemplate(next)
    setValues({})
  }

  /** Leaving the Modpack step: the pack decides the template and its versions, and suggests a name. */
  function applyPack() {
    if (setup?.template) {
      setTemplate(setup.template)
      setValues(setup.values)
    }
    const suggested = { name: modpack.pack.name.slice(0, 40), id: slugify(modpack.pack.name) }
    if (!name || name === filled.name) setName(suggested.name)
    if (!id || id === filled.id) setId(suggested.id)
    setFilled(suggested)
  }

  /** Each path starts fresh: what a pack filled in has no business in a blank server, and the other way round. */
  function choose(next) {
    setChosen(next)
    setStep(0)
    setModpack(null)
    setTemplate('')
    setValues({})
    setName('')
    setId('')
    setFilled({ id: '', name: '' })
  }

  function back() {
    if (step > 0) setStep(step - 1)
    else setChosen(null)
  }

  function submit(event) {
    event.preventDefault()
    if (current === 'Modpack') applyPack()
    if (!last) {
      setStep(step + 1)
      return
    }
    run(async () => {
      const body = {
        template,
        id,
        name: name.trim(),
        values,
        nodeId: nodeId === '' ? null : Number(nodeId),
        modpack: path === 'modpack' && modpack?.file ? { modId: modpack.pack.id, fileId: modpack.file.fileId } : null,
      }
      onCreated(await api('/api/admin/pufferpanel/servers', { method: 'POST', body }))
      return null
    })
  }

  // the pack's Minecraft version, for matching a template by hand when none was picked for it
  const packMinecraft = setup?.minecraftVersion || minecraftVersions(modpack?.file)

  return (
    <Dialog onClose={onClose} className="dialog-wizard">
      <header className="wizard-head">
        <h2>New server</h2>
        {path && (
          <ol className="wizard-steps">
            {steps.map((label, i) => (
              <li key={label} aria-current={i === step ? 'step' : undefined} className={i < step ? 'done' : undefined}>
                <span className="wizard-step-number">{i < step ? <Icon name="check" /> : i + 1}</span>
                {label}
              </li>
            ))}
          </ol>
        )}
      </header>

      <form className="wizard-form" onSubmit={submit}>
        <div className="wizard-body">
          {!path && (
            <>
              {!curseForge && !curseForgeError && <p className="muted">Loading…</p>}
              {canChoose && (
                <div className="modpack-results path-choices">
                  <button type="button" className="modpack-card" onClick={() => choose('modpack')}>
                    <span className="path-icon">
                      <Icon name="download" />
                    </span>
                    <span>
                      <strong>From a CurseForge modpack</strong>
                      <small className="muted">
                        Pick a pack and a version. Its server files go on the server, and the template and its versions
                        are chosen to match.
                      </small>
                    </span>
                  </button>
                  <button type="button" className="modpack-card" onClick={() => choose('blank')}>
                    <span className="path-icon">
                      <Icon name="servers" />
                    </span>
                    <span>
                      <strong>Blank server</strong>
                      <small className="muted">Pick a template and its settings. You add the server's files yourself.</small>
                    </span>
                  </button>
                </div>
              )}
            </>
          )}

          {current === 'Modpack' && (
            <>
              <ModpackPicker value={modpack} onChange={setModpack} />
              {modpack?.file && setupStale && <p className="muted">Reading what the pack runs on…</p>}
              {modpack?.file && !setupStale && setup && (
                <p className="muted">
                  {setup.template
                    ? `Runs on ${packRunsOn(setup)}: the template and its versions will be set to match.`
                    : packRunsOn(setup)
                      ? `Runs on ${packRunsOn(setup)}. No template matched, so you'll pick one next.`
                      : "Couldn't tell what this pack runs on, so you'll pick the template next."}
                </p>
              )}
              <ErrorNote>{setupError}</ErrorNote>
            </>
          )}

          {current === 'Basics' && (
            <>
              <p className="muted">
                Creates it on PufferPanel and registers it here, with its mod config and the WTC Lodestone mod already in
                place.
                {path === 'modpack'
                  ? ' The modpack\'s server files are added right after.'
                  : " You upload the modpack's server files in PufferPanel afterwards."}
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
                <Field
                  label="Template"
                  hint={
                    path === 'modpack' && setup?.template === template
                      ? `Picked from the pack: ${packRunsOn(setup)}.`
                      : path === 'modpack' && packMinecraft
                        ? `Pick one that runs ${packRunsOn(setup) || `Minecraft ${packMinecraft}`}.`
                        : undefined
                  }
                >
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

          {current === 'Settings' && path === 'modpack' && setup?.template !== template && packMinecraft && (
            <Note>
              {modpack.pack.name} runs on {packRunsOn(setup) || `Minecraft ${packMinecraft}`}: set the template's versions to
              match.
            </Note>
          )}

          {current === 'Settings' &&
            (variables.length > 0 ? (
              <div className="wizard-grid">
                {variables.map((v) => (
                  <TemplateSetting
                    key={v.key}
                    variable={v}
                    value={valueOf(v)}
                    fromPack={fromPack(v.key)}
                    onChange={(value) => setValues((current) => ({ ...current, [v.key]: value }))}
                  />
                ))}
              </div>
            ) : (
              <p className="muted">{stale ? 'Loading the template…' : 'This template has nothing to set.'}</p>
            ))}

          {current === 'Review' && (
            <dl className="wizard-review">
              <dt>Display name</dt>
              <dd>{name}</dd>
              <dt>Server ID</dt>
              <dd className="mono">{id}</dd>
              {path === 'modpack' && (
                <>
                  <dt>Modpack</dt>
                  <dd>{`${modpack.pack.name} · ${modpack.file.displayName}`}</dd>
                </>
              )}
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
          {path && (
            <div className="button-row">
              {(step > 0 || canChoose) && (
                <button type="button" className="button" disabled={busy} onClick={back}>
                  Back
                </button>
              )}
              <button
                type="submit"
                className="button button-primary"
                disabled={
                  busy ||
                  (current === 'Modpack' && (!modpack?.file || setupStale)) ||
                  (current === 'Basics' && !template) ||
                  (current !== 'Modpack' && stale)
                }
              >
                {!last ? 'Next' : busy ? 'Creating…' : 'Create server'}
              </button>
            </div>
          )}
        </footer>
      </form>
    </Dialog>
  )
}

/** Template descriptions are written for PufferPanel's own page and can carry HTML; here they're plain hints. */
const plain = (text) => text?.replace(/<[^>]*>/g, '').trim() || undefined

/** One of a template's settings, as the input its type calls for. `fromPack`: its value came from the modpack. */
function TemplateSetting({ variable: v, value, fromPack, onChange }) {
  const hint = [fromPack && 'From the pack.', plain(v.desc)].filter(Boolean).join(' ') || undefined
  if (v.type === 'boolean' && !(v.options?.length > 0)) {
    return (
      <div className="field">
        <label className="check-field">
          <input type="checkbox" checked={value === true || value === 'true'} onChange={(e) => onChange(e.target.checked)} />
          <span>{v.display}</span>
        </label>
        {hint && <small>{hint}</small>}
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
    <Field label={v.display} hint={hint}>
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
