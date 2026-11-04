import { formatMemory } from '../../format'
import { ErrorNote } from '../ui/ErrorNote'
import { Note } from '../ui/Note'

const UNDER_WAY = {
  DOWNLOADING: 'Downloading its server files from CurseForge…',
  UPLOADING: 'Uploading its server files to PufferPanel…',
  UNPACKING: 'Unpacking its server files…',
}

const mb = (bytes) => formatMemory(bytes / (1024 * 1024))

/**
 * How putting a CurseForge modpack on a just-created server is going (`server.modpackSetup`). The hub
 * keeps this in memory only, so after a hub restart there is nothing to show and this renders nothing.
 * Downloads and uploads have byte counts; unpacking doesn't (the panel does it in one call), so its
 * bar has no value, which the browser draws as one that's busy without saying how far.
 */
export function ModpackSetupNote({ setup }) {
  if (!setup) return null
  const pack = [setup.modpack, setup.version].filter(Boolean).join(' · ')
  if (setup.stage === 'FAILED') {
    return (
      <ErrorNote>
        Couldn't add {pack}: {setup.error}. Its install ran without it, so upload its server files in PufferPanel.
      </ErrorNote>
    )
  }
  if (setup.stage === 'DONE') {
    return <Note>{pack} is in place, and its install has started in PufferPanel.</Note>
  }
  const known = setup.done != null && setup.total > 0
  return (
    <div className="note setup-progress">
      {/* only the stage is announced; the figures below change every second */}
      <span role="status">
        Adding {pack}. {UNDER_WAY[setup.stage]}
      </span>
      <progress value={known ? setup.done : undefined} max={known ? setup.total : undefined} aria-label={UNDER_WAY[setup.stage]} />
      {setup.done != null && (
        <small className="muted">
          {known ? `${mb(setup.done)} of ${mb(setup.total)} (${Math.floor((setup.done / setup.total) * 100)}%)` : mb(setup.done)}
        </small>
      )}
    </div>
  )
}
