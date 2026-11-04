import { useEffect, useState } from 'react'
import { minecraftVersions } from '../../format'
import { useApi } from '../../hooks'
import { ErrorNote } from '../ui/ErrorNote'
import { Field } from '../ui/Field'

/**
 * The New server dialog's modpack step: search CurseForge, pick a pack, pick one of its versions that
 * comes with server files. `value` is `{ pack, file }` or null for none, which is a fine answer too:
 * the server is then created without one, as before.
 */
export function ModpackPicker({ value, onChange }) {
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  // a request per pause in typing, not per key
  useEffect(() => {
    const timer = setTimeout(() => setSearch(query.trim()), 350)
    return () => clearTimeout(timer)
  }, [query])

  const { data: packs, error, stale } = useApi(value ? null : `/api/admin/curseforge/modpacks?q=${encodeURIComponent(search)}`)
  const filesPath = value ? `/api/admin/curseforge/modpacks/${value.pack.id}/files` : null
  const { data: files, error: filesError, stale: filesStale } = useApi(filesPath)
  const versions = value && !filesStale ? (files ?? []) : null

  // the newest version, until someone picks another
  useEffect(() => {
    if (value && !value.file && !filesStale && files?.length > 0) onChange({ ...value, file: files[0] })
  }, [value, files, filesStale, onChange])

  if (value) {
    return (
      <>
        <div className="modpack-card">
          {value.pack.logoUrl && <img src={value.pack.logoUrl} alt="" />}
          <span>
            <strong>{value.pack.name}</strong>
            <small className="muted">{value.pack.summary}</small>
          </span>
          <button type="button" className="button" onClick={() => onChange(null)}>
            Change
          </button>
        </div>
        <Field label="Version" hint="Only the versions that come with server files.">
          <select
            value={value.file?.fileId ?? ''}
            onChange={(e) => onChange({ ...value, file: versions.find((f) => String(f.fileId) === e.target.value) })}
            disabled={!versions?.length}
            required
          >
            {!versions && <option value="">Loading…</option>}
            {versions?.map((f) => (
              <option key={f.fileId} value={f.fileId}>
                {f.displayName}
                {minecraftVersions(f) && ` (Minecraft ${minecraftVersions(f)})`}
              </option>
            ))}
          </select>
        </Field>
        <ErrorNote>
          {filesError ||
            (versions?.length === 0 && 'No version of this pack comes with server files. Pick another, or go without one.')}
        </ErrorNote>
      </>
    )
  }

  return (
    <>
      <p className="muted">
        Optional. Pick a CurseForge pack and its server files are added for you; skip this to upload them by hand.
      </p>
      <Field label="Search CurseForge">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          // Enter belongs to the search here, not to the dialog's Next
          onKeyDown={(e) => e.key === 'Enter' && e.preventDefault()}
          placeholder="All the Mods 10"
          autoFocus
        />
      </Field>
      <ErrorNote>{error}</ErrorNote>
      {stale && !error && <p className="muted">Searching…</p>}
      {!stale && packs?.length === 0 && <p className="muted">No modpacks match “{search}”.</p>}
      {!stale && packs?.length > 0 && (
        <ul className="modpack-results">
          {packs.map((p) => (
            <li key={p.id}>
              <button type="button" className="modpack-card" disabled={!p.downloadable} onClick={() => onChange({ pack: p, file: null })}>
                {p.logoUrl && <img src={p.logoUrl} alt="" />}
                <span>
                  <strong>{p.name}</strong>
                  <small className="muted">
                    {p.downloadable ? p.summary : 'Its author only lets the CurseForge app download it, so upload this one by hand.'}
                  </small>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
