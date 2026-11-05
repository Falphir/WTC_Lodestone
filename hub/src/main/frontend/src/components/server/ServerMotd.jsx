import { api } from '../../api'
import { motdMissing, renderMotd } from '../../format'
import { useAction, useApi } from '../../hooks'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'
import { MinecraftText } from '../ui/MinecraftText'
import { Panel } from '../ui/Panel'

/**
 * Which network MOTD this server shows, picked from the list on the Configuration page, and how it comes out
 * with this server's listing filled in. Saves as soon as it's picked; running servers show it within a
 * heartbeat.
 */
export function ServerMotd({ server, onSaved }) {
  const { data: motds } = useApi('/api/admin/motds', { topics: ['servers'] })
  const { run, busy, error } = useAction(onSaved)
  const picked = motds?.find((m) => m.id === server.motd?.templateId)
  const listing = { modpack: server.listing.modpack, version: server.listing.modpackVersion, name: server.name }
  const missing = picked ? motdMissing(picked.text, listing) : []
  // rendered here rather than shown as the hub sent it: the hub's alignment is spaces sized for the game's
  // font, which would sit off-centre in the preview's; with the markers kept, the preview lines it up itself
  const preview = picked ? renderMotd(picked.text, listing) : null

  function pick(value) {
    run(async () => {
      await api(`/api/admin/servers/${encodeURIComponent(server.id)}`, { method: 'PATCH', body: { motdTemplateId: Number(value) } })
      return null
    })
  }

  return (
    <Panel pad className="form">
      <Field
        label="MOTD"
        hint={
          <>
            Picked from the network MOTDs on the <a href="#/config">Configuration</a> page. Running servers show a change
            within a minute.
          </>
        }
      >
        <select value={server.motd?.templateId ?? 0} onChange={(e) => pick(e.target.value)} disabled={busy || !motds}>
          <option value={0}>None (keeps its own)</option>
          {motds?.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
            </option>
          ))}
        </select>
      </Field>
      {preview && <MinecraftText text={preview} />}
      {picked && missing.length > 0 && (
        <p className="muted">
          Fill in the listing's {missing.join(' and ')} to use this MOTD. Until then the server keeps its own.
        </p>
      )}
      <ErrorNote>{error}</ErrorNote>
    </Panel>
  )
}
